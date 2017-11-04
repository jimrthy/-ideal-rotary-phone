(ns ideal-rotary-phone.hello
  (:require [cheshire.core :as json]
            [immutant.web.async :as web-async]
            [io.pedestal.http :as http]
            [io.pedestal.http.immutant.websockets :as ws]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]
            [ring.middleware.session.cookie :as cookie]))

(defn recv!
  "This is really your interesting part"
  [this msg]
  ;; Important details that must be in the Context we're putting together here:
  ;; 1. Something that's basically a Ring request map
  ;; 2. Routes
  ;; 3. The websocket's writer callback function
  ;;    This part is less than ideal. That should come from an interceptor/component
  ;;    that got created along with the handler behind "this"
  ;;    i.e. Some client connected. There's a unique, meaningless, high-entropy
  ;;    id for that connection somewhere.
  ;;    And then there are sub-channel messages that have been sent
  ;;    over that connection.
  ;;    We really need an interceptor that copes with those.
  (throw (RuntimeException. "This gets really interesting if you call interceptor-chain/execute")))

(defn ws-on-text
  [this
   {{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   msg]
  (log/info ::key sec-websocket-key
            ::session-id (get-in request [:session :I-dropped-this-ball])
            ::msg msg)
  (if-let [handler (-> this ::sessions deref (get sec-websocket-key))]
    (recv! handler msg)
    (throw (ex-info "Incoming on dead socket"
                    {::message msg
                     ::socket-key sec-websocket-key
                     ::this this}))))

(defn ws-on-bin
  [this request payload offset length]
  (throw (RuntimeException. "Handle this, too")))

(defn garbage
  "Just a placeholder to demo the point"
  [req]
  {:status 500
   :body "You'll never really call this, will you?"})

(defn build-nested-server
  "This short-circuits a lot of code to build an interceptor-chain.

  And it never seems to get called.

  So whatever seemed to be going on here was a dead-end that
  needs to go back away.

  I think."
  [service-map server-map]
  (let [server "what is this?"]
    {:server (fn [& args]
               (println "Server function called:" args))
     ;; These next two would fail hard, if they were ever called
     :start-fn #(.start server)
     :stop-fn #(.stop server)}))

(defn write-msg
  "Send msg back to client.

  This is really the termination point for the push
  notifications associated with a specific on-data 'request'"
  [sock msg]
  (web-async/send! sock
                   ;; Pedestal has a protocol for converting
                   ;; most source formats to something that can
                   ;; be written.
                   ;; I think it's named IWriteableBody.
                   ;; TODO: make sure this gets converted to that.
                   msg
                   :on-success (fn []
                                 (log/debug ::wrote msg))
                   :on-error (fn []
                               (log/error ::write-failed msg))))

(defn start-handler
  [{{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as initial-request}
   socket]
  {::initial-request initial-request
   ::client-nonce sec-websocket-key
   ;; It would be nice to configure things so we pass
   ;; everything we really and truly need in here and
   ;; get to ignore the rest of this.
   ;; That "really and truly need" qualifier keeps
   ;; evolving.
   ::server (build-nested-server)
   ::sock socket
   ::writer (partial write-msg socket)})

(def cookie-key
  "This needs to be something crypto-random"
  (byte-array [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15]))

(def ws-configuration
  "I'm not actually using this, so it should probably go away"
  (atom {::sessions {}}))

(defn new-ws-client
  "Lots of interesting configuration should probably happen here.

  If you're using some sort of Component library, this is a great
  candidate for breaking the general rules and adding an extra
  bunch of branches to this particular part of the tree."
  [{:keys [::sessions]
    :as component}
   {{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   sock]
  (let [ws-state (start-handler request sock)]
    ;; Using the sec-websocket-key here is a terrible idea.
    ;; It's just a nonce, supplied by the client.
    ;; If another client happens to pick the same nonce, we're
    ;; destroying session integrity.
    ;; Using an actual SESSION cookie as the key here is less-awful.
    ;; But it still leaves us wide open to trivial DDoS attacks.
    ;; Our best bet seems to be:
    ;; 1. Add xsrf protection to configurator, below
    ;;    (spoiler alert: this seems like it will be best-served
    ;;    by setting up its own interceptor-chain)
    ;; 2. Verify the session token associated with the request here
    ;;    Assuming the principal submitted authentication credentials
    ;;    through a "normal" AJAX request
    ;; 3. Set up the actual on-message caller/handler to submit
    ;;    authentication credentials a second time through this channel.
    (swap! component assoc sec-websocket-key (assoc ws-state {::something ::interesting}))))

(def ws-paths
  "Really should be able to specify multiple URI paths here for
  a variety of implementation/behavior.

  I remember having issues with that, but this might have been
  imaginary."
  {"/api/v1/ws" (fn [this
                     {{sec-websocket-key "sec-websocket-key"
                       :as headers} :headers
                      :as request}]
                  ;; Default implementation of start-ws-connection returns a
                  ;; function that calls a callback that looks like it probably
                  ;; has something to do with a buffer.
                  ;; The buffer parameter just gets thrown away.
                  ;; Q: Is there any point to this indirection?
                  {:on-open (ws/start-ws-connection (partial new-ws-client this request))
                   :on-message (fn [sock msg]
                                 ;; The current sample implementation hides the socket
                                 ;; from handlers.
                                 ;; It seems to be built around the idea that we're doing
                                 ;; request/response.
                                 ;; A message arrives from the client, we process it, and
                                 ;; then we send the result of that processing back.
                                 ;; That's *a* viable approach for some scenarios.
                                 ;; But there are others that are very likely to show up
                                 ;; with websockets where they don't really fit all that
                                 ;; well.
                                 ;; Subscribing to push notifications is a really obvious
                                 ;; one:
                                 ;; Client sends us a message that sets up a subscription.
                                 ;; One obvious approach to handle that is for this message
                                 ;; handler to set up an interceptor-chain that terminates
                                 ;; back at web-async/send!
                                 (if (string? msg)
                                   (ws-on-text this request msg)
                                   ;; TODO: Verify this works
                                   (ws-on-bin this request msg 0 (count msg))))
                   :on-close (fn [num-code reason-text]
                               (if-let [session (get this sec-websocket-key)]
                                 (let [{:keys [::sock]} session]
                                   (log/info ::disconnect sec-websocket-key
                                             ::session session)
                                   (web-async/close sock)
                                   (swap! (::sessions this) dissoc sec-websocket-key))
                                 (log/warn ::problem "Closing a non-session websocket"
                                           ::key sec-websocket-key
                                           ::connected (-> this ::sessions deref))))
                   ;; Close after this many idle milliseconds
                   ;; Really needs to be tunable at runtime for debugging
                   :timeout (* 60 60 1000)})})

(defn configurator
  "For bypassing everything else to create web sockets.

  Returns something that Pedestal can convert to an Undertow HttpHandler.

  Gets called once during startup"
  [ws-configuration]
  (fn [x]
    (ws/add-ws-endpoints ws-configuration x ws-paths)))

(def routes
  #{["/does/not/matter" :get garbage :route-name ::pointless-place-holder]})

(def service {::http/container-options {:context-configurator configurator}
              ::http/enable-session {:store (cookie/cookie-store {:key cookie-key})}
              ::http/join? false
              ::http/port 8010
              ::http/routes (route/expand-routes routes)
              ::http/type :immutant})

(defn respond-hello
       [request]
       {:status 200 :body "Hello, World"})


(defn init
  []
  (println "Hello!"))
