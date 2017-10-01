(ns com.jimrthy.blog.web.ws-handler
  "Higher-level websocket request dispatcher

  Abstractions on top of the raw ws functionality to minimize change impact

  At least in theory, this layer really should be transport-neutral."
  (:require [clojure.spec.alpha :as s]
            [com.jimrthy.blog.lamport :as lamport]
            [immutant.web.async :as web-async]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            ;; Undocumented hidden implementation details.
            ;; Scary stuff!
            [io.pedestal.http.impl.servlet-interceptor :as servlet-interceptor]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as route-table]
            [io.pedestal.interceptor.chain :as i-c]
            [io.pedestal.log :as log])
  (:import clojure.lang.ExceptionInfo))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;; This is the initial request that caused the web socket
;; creation in the first place
;; TODO: Spec this out. It hasn't gone through a ring converter,
;; so should probably be pretty raw.
;; Although putting it through one would probably be a great idea
(s/def ::initial-request map?)

;; How does this identify itself?
(s/def ::key string?)

;; The part here that dispatches to appropriate ClientRequest
;; (using terminology from the python implementation) handlers.
(s/def ::server any?)

;; Pedestal has something like an IWriteableBody protocol.
;; msg needs to implement that.
;; TODO: match up that detail
(s/def ::writer (s/fspec :args (s/cat :msg any?)))

(s/def ::state (s/keys :req [::initial-request
                             ::key
                             ::server
                             ::writer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(def raw-logger
  {:name ::raw-logger
   :enter (fn [ctx]
            (log/info ::incoming-keys (keys ctx)
                      ::incoming ctx)
            ctx)
   :leave (fn [ctx]
            (log/info ::outgoing-keys (keys ctx)
                      ::outgoing #_ctx "too big for value")
            ctx)
   :error (fn [context-map ex]
            ;; This is bad enough that it seems broadcasting
            ;; every way we can.
            (println "Failure escaped to outer Interceptor. See logs.")
            (log/error :exception ex)
            context-map)})

(def response-writer
  "Main point: allow transformer instance to repeatedly invoke :leave"
  {:name ::responder
   :leave (fn [{:keys [:response ::writer]
                :as ctx}]
            (let [{:keys [:body]} response]
              (log/debug :body body :writer writer :context-keys (keys ctx))
              ;; Doing this check feels dubious, at best.
              ;; I'm not sure what alternative would be cleaner.
              ;; Pong, at least, is a "request" with no real
              ;; response.
              ;; Q: Would it make sense to just take basically-
              ;; undocumented steps there and pop this off the
              ;; :leave stack before that terminates so we
              ;; just never bother trying to send back anything?
              ;; That seems very brittle.
              (when body
                (writer body)))
            ctx)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Hacking Pedestal
;;; These are undocumented internal functions that
;;; I needed (?) to override to get web sockets
;;; working in a way that lets me tell what goes where.

(defn ws-interceptor-service-fn
  "This would be analogous to
io.pedestal.http.impl.servlet-interceptor/http-interceptor-service-fn

Or maybe just the plain interceptor-service-fn, since I really
don't want to add any Ring handling.

Although I *do* need a Terminator interceptor."
  [interceptors default-context]
  (throw (RuntimeException. "How deep do I need to go?")))

(defn chain-provider
  [service-map]
  ;; This is really just the default version.
  ;; Except that that wraps things up and ties you to
  ;; the specific implementations that it cares about.
  (-> service-map
      http/service-fn
      http/servlet))

(defn build-server
  "The return value never seems to be used, but this *does* get called"
  [service-map server-map]
  (println "Building server-fn" service-map server-map)
  ;; Default implementations return something like:
  (let [server "what is this?"]
    ;; Note that none of this is getting called.
    {:server (fn [& args]
               ;; This is assuming we're actually
               ;; expected to *have* an ICallable
               ;; here. I think this is really expected
               ;; to return a Servlet.
               ;; It doesn't matter, since it never seems to
               ;; be used.
               (println "Server function called:" args)
               (throw (RuntimeException. "Oops, I *did* get called")))
     :start-fn #(.start server)
     :stop-fn #(.stop server)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Routes

(defn routing-table
  "It seems as though this definitely needs the database and luna connections.

  That 'seems' is wrong. But we do need access to a registrar that knows how
  to communicate with whichever components do have those connections."
  []
  ;; TODO: Add more end-points here
  #{})

;;; Note that, at the very least, this should be part
;;; of a Component definition rather than a top-level
;;; global.

;;; And that verb customization hasn't been released yet
(let [verbs {:verbs #{:any :delete :get :head
                      :options :patch :post :put}}
      routes (route-table/table-routes verbs (routing-table))
      ;; This still doesn't work
      #_(route/expand-routes (concat [verbs] (routing-table)))
      ;; I really want dynamic routes at dev time.
      ;; Really should be able to just supply a function.
      ;; TODO: Figure out how to make that happen
      ;; Q: Is it worth setting it up this way, when we know
      ;; exactly which 2 of the default interceptors we want?
      ;; TODO: Switch to specifying
      ;; ::http/request-logger raw-logger
      ;; here. That would take us back to caring about 3 of them.
      ;; (another nifty 0.5.3 feature! See? Bleeding edge *is*
      ;; good)
      default-interceptors (http/default-interceptors {::http/routes routes})
      ;; Fun detail: We really need to inject something like body-params
      ;; Which is what chicken4's parse-incoming is really doing.
      wanted-interceptors (filter #(#{::http/not-found
                                      ::route/router}
                                    (:name %))
                                  (::http/interceptors default-interceptors))
      actual-interceptors (concat [raw-logger
                                   ;; Q: Replace this with its actual functionality?
                                   ;; It isn't complex, and it seems like it would
                                   ;; be better to just be explicit about it.
                                   ;; Besides, this is a totally undocumented
                                   ;; namespace.
                                   servlet-interceptor/terminator-injector
                                   ;; Q: Do I want to put this is front
                                   ;; of the terminator-injector?
                                   ;; It seems like it would be more accurate
                                   lamport/ticker
                                   response-writer
                                   #_proto/translator
                                   #_(sid-mapper/build)
                                   #_(session/start)
                                   ;; Should be able to move
                                   ;; this just after the proto/translator.
                                   ;; There's no reason for these messages
                                   ;; to bother either sid-mapper or SESSION
                                   ;; TODO: Make that so.
                                   #_(ping-pong/build-interceptor)
                                   #_(transform-manager/build-interceptor)]
                                  wanted-interceptors)]
  (def service-map {::http/chain-provider chain-provider
                    ::http/interceptors actual-interceptors
                    ::http/routes routes
                    ;; If we go with a standard ::type (defaults to :jetty),
                    ;; then it basically looks up
                    ;; io.pedestal.http.???
                    ;; and uses its server function.
                    ::http/type build-server
                    ::http/secure-headers nil}))
(comment
  ;; Q: Which interceptors get set up by default?
  (->> (routing-table)
       route/expand-routes
       (hash-map ::http/routes)
       http/default-interceptors
       ::http/interceptors
       (map :name))
  )

(defn build-server
  []
  (http/create-server service-map))
(comment
  ;; Verify that it's limited to the subset
  ;; of interceptors that we actually want.
  (-> (build-server) ::http/interceptors)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef recv!
        :args (:this ::state)
        :msg string?)
(defn recv!
  "Message arriving from client

  This is really the equivalent of the existing ClientRequest handler
  subchannel dispatcher.

  Or maybe the Transformers' end_points.

  It needs to handle the client-side (start/alter/stop) portion
  of the dataflow life cycles."
  [{:keys [::initial-request ::protocol ::server ::writer]
    :as this}
   msg]
  (try
    (let [{:keys [::http/interceptors
                  ::http/routes]} server]
      (let [result
            (i-c/execute {:initial-request initial-request
                          :protocol protocol
                          :raw-request msg
                          :routes routes
                          :server server
                          ::writer writer}
                         interceptors)]
        ;; Really should supply feedback to the client.
        ;; Disabling that in the python version was a costly mistake.
        ;; However:
        ;; i-c/execute seems to have been called for its side-effects.
        ;; result is nil.
        (log/debug ::finished-interceptor-chain-for msg
                   ::lamport/clock (lamport/tick!)
                   ::headers (:headers initial-request))
        result))
    (catch Exception ex
      ;; It's theoretically possible that an exception escaped
      ;; i-c/execute.
      ;; But not likely enough to be worth handling,
      ;; since there's an interceptor precisely for that.
      (log/error :exception ex
                 ::where "Setting up for interceptor chaining"))))

(defmethod ig/init-key ::server
  [_ {
      :as dependencies}]
  ;; TODO: Switch to this instead of doing it all manually in start, the way we're
  ;; doing directly below
  (throw (RuntimeException. "All signs point to converting to this sooner rather than later")))

(s/fdef start
        :args (s/cat :request ::request
                     :writer ::writer)
        :ret ::state)
(defn start
  "Create a new 'Master' websocket connection

  It's tempting to believe that I'll want/need an entirely separate Integrant
  System for this.

  It's even more tempting to think that something like a Servlet
  makes sense, to try to take advantage of all the existing infrastructure."
  [{{sec-websocket-key "sec-websocket-key"
     :as headers} :headers
    :as request}
   writer]
  (let [most
        {::initial-request request
         ::key sec-websocket-key
         ::server (build-server)
         ::writer writer}]
    most))

(comment
  (def server (http/create-server service-map))
  (http/create-provider service-map)
  (#'http/service-map->server-options service-map)
  (#'http/server-map->service-map service-map)
  )
