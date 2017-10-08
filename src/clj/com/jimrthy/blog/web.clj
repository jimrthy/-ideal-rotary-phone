(ns com.jimrthy.blog.web
  (:require [com.jimrthy.blog.authcz :as authcz]
            [com.jimrthy.blog.web.routes :as routes]
            [com.jimrthy.blog.web.web-sockets :as ws]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.log :as log]
            [ring.middleware.session.cookie :as cookie]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defmethod ig/init-key ::authcz
  [_ this]
  this)

(defmethod ig/init-key ::routes
  [_ {:keys [::authcz]
      :as this}]
  (log/info ::where "Initializing routes")
  (comment
    ;; According to the docs, you can supply a
    ;; function here to update your routes dynamically.
    ;; This is supposed to be a nice way to update your
    ;; routes on the fly, without needing to restart
    ;; the server.
    ;; TODO: Get that working.
    (if production?
      (routes/wrapper)
      #'routes/wrapper))
  (assoc this ::routes (routes/wrapper this)))

(defmethod ig/init-key ::server
  [_
   {:keys [::routes]
    {:keys [:env]
     :as service} ::service
    :as this}]
  {:pre [env
         routes
         service]}
  (assoc this
         ::actual
         (-> service
             (merge
              {::http/routes (::routes routes)
               ;; Don't block the thread that starts the server
               ::http/join? false
               ;; For dev mode, allow any origin.
               ;; TODO: Totally change the rules for production
               ::http/allowed-origins {:creds true
                                       :allowed-origins (constantly true)}})
             ;; Wire up interceptor chains
             http/default-interceptors
             http/dev-interceptors
             http/create-server
             http/start)))

(defmethod ig/init-key ::service
  [_
   {:keys [::authcz
           ::port
           ::production?
           ::routes
           ::ws]
    :as this
    :or {port 8080}}]
  (let [{:keys [::authcz/cookie-key]} authcz
        baseline
        {:env (if production?
                :prod
                :dev)
         ::http/allowed-origins (fn [x]
                                  (log/info ::todo "TODO: Verify the origin of"
                                            ::what? x)
                                  true)
         ;; Inject the interceptor for websocket connections
         ::http/container-options {:context-configurator (ws/configurator ws)}
         ;; This needs to be "A settings map to include the csrf-protection
         ;; interceptor. This implies sessions are enabled."
         ;; So...what does that actually mean?
         ;; And how do we use this?
         ;; ::http/enable-csrf {:cookie-token true}
         ;; TODO: Use a real database for the cookie store
         ::http/enable-session {:store (cookie/cookie-store {:key cookie-key})}
         ;; do not block thread that starts web server
         ::http/join? false
         ::http/port port
         ::http/resource-path "/public"
         ;; According to the official docs:
         ;; this can be "a function that returns routes when called"
         ;; Trying to use routes/route-wrapper hasn't panned out so far
         ::http/routes (::routes/routes routes)
         ::http/type :immutant}]
    (if production?
      baseline
      (merge baseline {                  ;; all origins are allowed in dev mode
                       ::http/allowed-origins {:creds true
                                               :allowed-origins (constantly true)}}))))

(defmethod ig/init-key ::socket
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
