(ns com.jimrthy.blog.web.web-sockets
  "Bottom-level web-socket implementation"
  (:require [clojure.spec.alpha :as s]
            [com.jimrthy.blog.lamport :as lamport]
            [com.jimrthy.blog.web.ws-handler :as handler]
            [crypto.random]
            [integrant.core :as ig]
            [immutant.web :as web]
            [immutant.web.async :as web-async]
            ;; TODO: Just use sente
            [io.pedestal.http.immutant.websockets :as ws]
            [io.pedestal.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;; This is really the websocket
;; Q: How do (and should) I spec it?
(s/def ::sock any?)

(s/def ::failure-handler (s/fspec :args []
                                  :ret any?))
(s/def ::success-handler (s/fspec :args []
                                  :ret any?))

(comment
  ;; Specifying it this way is tempting.
  ;; But the basic API they provide is the function write-body-to-stream.
  ;; Which is really about writing content to an output stream, like HTTP.
  ;; This doesn't really fly when we're talking about a websocket interface
  ;; that just goes through its own function call that forwards along a string
  ;; (or, presumably, raw bytes)
  (s/def ::writeable-body #(extends?
                            io.pedestal.http.impl.servlet-interceptor
                            (class %))))
(s/def ::writeable-body string?)
(s/def ::message-writer (s/fspec
                         :args (s/cat :socket ::sock
                                      ::msg ::writeable-body
                                      :on-success ::success-handler
                                      :on-failure ::failure-handler)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

;; This next construct seems wrong, even though it
;; compiles.
(s/def write-msg ::message-writer)
(defn write-msg
  "Building block for the write function associated with ::state"
  ([socket msg on-success on-failure]
   (web-async/send! socket
                    msg
                    :on-success on-success
                    :on-error on-failure))
  ([socket msg]
   (write-msg socket
              msg
              (fn []
                (log/debug ::wrote msg))
              (fn []
                (log/error ::write-failed msg)))))

(defn new-ws-client
  "Builds what the python version calls a Master Connection"
  [{ws-clients ::sessions
    :as component}
   {{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   send-ch]
  (log/info ::msg "Fresh websocket connection"
            ::headers headers
            ::key sec-websocket-key
            ::time (System/nanoTime))
  (log/debug ::request request)
  ;; Doing a clock tick here seems odd.
  ;; We have to remember that this is a 1-time connection
  ;; piece that's totally distinct from the bulk of the
  ;; messaging.
  (let [request (assoc request ::lamport/clock (lamport/tick!))
        session-state (handler/start request (partial write-msg send-ch))]
    ;; Q: Do I really want to do this here?
    ;; It seems like it would make more sense to wait until
    ;; the ping has been successfully sent.
    ;; That approach could set us up with a race
    ;; condition where we don't know about the handlers
    ;; for incoming messages until after the success handler
    ;; for that ping has been sent.
    ;; This approach has similar issues, but at least the
    ;; problematic time window is shorter.
    ;; TODO: Ensure client will not send any messages until
    ;; after its first PONG
    (swap! ws-clients assoc sec-websocket-key (assoc session-state
                                                     ::lamport/clock
                                                     (lamport/tick!)))))

(defn ws-on-text
  [this
   {{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   msg]
  (log/info ::key sec-websocket-key
            ;; Q: Is there anything in here?
            ::session-id (get-in request [:session :lgm-1.backend.routes/id])
            ::msg msg
            ::lamport/clock (lamport/tick!))
  ;; Q: How can I tell where msg came from?
  ;; A: For now, trust the sec-websocket-key
  (log/warn :exception (RuntimeException. "This was a terrible idea")
            ;; Honestly, we need to verify the SESSION in the request
            ;; header.
            ;; And then the client needs to resubmit credentials over
            ;; the websocket as evidence that that SESSION token
            ;; wasn't hijacked.
            ::problem "sec-websocket-key is just a nonce")
  (if-let [handler (-> this ::sessions deref (get sec-websocket-key))]
    (handler/recv! handler msg)
    (throw (ex-info "Incoming on dead socket"
                    {::lamport/clock (lamport/tick!)
                     ::message msg
                     ::socket-key sec-websocket-key
                     ::this this}))))

(defn ws-on-bin
  [this
   {{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   payload
   offset
   length]
  ;; Q: How can I tell where this came from?
  ;; c.f. ws-on-text, which has the same problem
  (let [request (assoc request ::lamport/clock (lamport/tick!))]
    (println (str "Incoming binary: " length " bytes starting at offset " offset
                  ":\n"
                  (String. payload)))
    (throw (RuntimeException. "Handle this also"))))

(defn start-ws-connection
  "Low-level web-socket on-connect event handler"
  ;; Originally copy/pasted from pedestal.http.immutant.websockets
  ;; Q: Does it serve any purpose?
  ([on-connect-fn]
   (start-ws-connection on-connect-fn 10))
  ([on-connect-fn send-buffer-or-n]
   ;; Note that the send-buffer-or-n argument just gets
   ;; thrown away.
   (fn [send-ch]
     (on-connect-fn send-ch))))

(def ws-paths
  {"/api/v1/ws" (fn [this
                     ;; TODO: Given our current implementation,
                     ;; we need the session middleware to check/
                     ;; extract the user ID so we know who's on
                     ;; the other side.
                     ;; Then again, the current implementation's
                     ;; broken.
                     ;; If anything can get that session cookie,
                     ;; then they can hijack the session.
                     ;; Realistically, the user needs to log in
                     ;; to be able to be able to open a web socket.
                     ;; But then credentials need to be sent over
                     ;; the websocket so we can have some
                     ;; confidence that nothing malicious has stolen
                     ;; the cookie.
                     ;; That concern is mostly about malware hacking
                     ;; into the browser, getting the cookie, and then
                     ;; pretending to be a legitimate client.
                     ;; This concern may be overkill, but the person
                     ;; who found the original cross-site origin
                     ;; vulnerability recommended it.
                     ;; Speaking of which, we really have to verify
                     ;; that also.
                     {{sec-websocket-key "sec-websocket-key"
                       :as headers} :headers
                      :as request}]
                  {:on-open (start-ws-connection (partial new-ws-client
                                                          this
                                                          request))
                   :on-message (fn [sock msg]
                                 ;; The ch is a detail the pedestal ws jetty sample
                                 ;; (which is the roots of this implementation)
                                 ;; hides:
                                 ;; It's built around the idea that we're
                                 ;; still doing request/response, so it just
                                 ;; takes whatever the handler returns and
                                 ;; sends it back to the socket.
                                 ;; Or maybe this is just an example of the
                                 ;; way Pedestal is designed: if you want
                                 ;; to send back multiple responses, just
                                 ;; call the interceptor-chain's :leave stack
                                 ;; again.
                                 ;; Q: How thread-safe *are* these
                                 ;; sockets?
                                 ;; It would be very interesting to
                                 ;; try forwarding this along directly to
                                 ;; any transformer that needs to use it.
                                 ;; It seems like it could cut out some
                                 ;; possibly pointless intermediate pieces.
                                 ;; Then again, it also feels like a very
                                 ;; dangerous piece of premature optimization.
                                 ;; Since it's basically spreading the mechanism
                                 ;; for serious external side-effects deeper into
                                 ;; the system.
                                 ;; This deserves some serious thought.
                                 (if (string? msg)
                                   (ws-on-text this request msg)
                                   (ws-on-bin this request msg 0 (count msg))))
                   :on-error (fn [throwable]
                               ;; Q: Does this mean we should remove
                               ;; the reference?
                               ;; A: Nope. That will be a separate call
                               ;; to :on-close
                               (log/error :exception throwable
                                          :msg "WS-Error"))
                   :on-close (fn [num-code reason-text]
                               ;; TODO: This needs to
                               ;; disconnect any/all transformers
                               ;; to which this socket happened to
                               ;; be a/the sink.
                               (log/info ::msg "WS-Close"
                                         ::status num-code
                                         ::lamport/clock (lamport/tick!)
                                         ::reason reason-text)
                               (if-let [session (get this sec-websocket-key)]
                                 (let [{:keys [::handler/sock]} session]
                                   (log/info ::disconnect sec-websocket-key
                                             ::session session)
                                   (web-async/close sock)
                                   (swap! (::sessions this) dissoc sec-websocket-key))
                                 (log/warn ::problem "Closing a non-session websocket")))
                   ;; Set this is milliseconds.
                   ;; If the socket is idle for longer than this (in milliseconds),
                   ;; it will close.
                   ;; It would be nice to be able to adjust this at runtime for
                   ;; the sake of things like long debugging sessions
                   ;; Note that ultimately, without this, we're going to accumulate
                   ;; lots of garbage sockets.
                   ;; Then again, that may just be at dev time
                   :timeout 0})})

(defn add-ws-endpoints
  "Copy/pasted from io.pedestal.http.immutant.websockets

  Trying to sort out how/where to get a session ID"
  [this srv-cfg ws-paths]
  (loop [ws-maps (seq ws-paths)
         request srv-cfg]
    ;; Convert the entries in ws-maps to
    ;; ???
    (if (empty? ws-maps)
      request
      (let [[path handler-creator] (first ws-maps)
            ;;listener (ws/make-ws-listener ws-map)

            ;; This needs to be something that can be cast to
            ;; io.undertow.server.HttpHandler.
            ;; A map that defines an Interceptor (like the one directly
            ;; above) doesn't cut it.
            ;; TODO: Figure out how to get session information/cookies
            ;; to the real endpoint.
            ;; Absolutely vital piece of this: need access to the Origin
            ;; header to be sure this request came from me
            ;; (to prevent Cross-Site WebSocket Hijacking)
            ;; Of course, Pedestal really should handle that for me
            ;; (assuming there's a reasonable way to do so...what *is* my
            ;; identity?)
            outer-handler (fn [request]
                            ;; This gets called when the websocket connects.
                            (log/info :add-ws-endpoints request
                                      :keys (keys request))
                            (web-async/as-channel (assoc request :websocket? true)
                                                  (handler-creator this request)))]
        ;; web/run takes a handler function and a bunch of options
        ;; (:path is the most interesting) and returns an updated
        ;; web server configuration that's ready to call that handler
        ;; when it receives a request for that :path
        (recur (rest ws-maps) (-> request
                                  (assoc :path path)
                                  (->> (web/run outer-handler))
                                  ;; Nothing outside of this cares about that path
                                  (dissoc :path)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defmethod ig/init-key ::websocket
  [_ opts]
  ;; Q: Which real dependencies are involved here?
  ;; This is really where the outer layer starts interacting
  ;; with the rest of the system.
  ;; So we need a way to dispatch to pretty much everything else
  ;; But we have to start somewhere.
  ;; Q: Is there any way to preserve websocket connections
  ;; across restarts?
  ;; Just to make things nastier, note that the existing implementation
  ;; supplies an empty dictionary, which really doesn't make a lot
  ;; of sense, since there's no good way to ever change it.
  ;; (it doesn't seem likely, but it would be nice)
  ;; TODO: This needs to be handled through sessions interceptor
  ;; instead.
  ;; That (and probably several other similar pieces) would probably
  ;; make sense as its own Component.
  (update opts ::sessions #(or % (atom {}))))

(defn configurator
  "For a pedestal immutant service's :context-configurator

  Returns something that pedestal can convert to an Undertow HttpHandler.

  I think the main point is to bypass the interceptors. I
  know that, so far, it's only used for creating web sockets."
  [this]
  (fn [x]
    ;; This gets called once during startup
    (log/info ::ctx-cfg x
              ::type (class x))
    (add-ws-endpoints this x ws-paths)))
