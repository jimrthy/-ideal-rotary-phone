(ns com.jimrthy.blog.web
  (:require [com.jimrthy.blog.authcz :as authcz]
            [com.jimrthy.blog.web.routes :as routes]
            [com.jimrthy.blog.web.web-sockets :as ws]
            [crypto.random]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.secure-headers :as sec-hdr]
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
  [_ {:keys [::authcz
             ::production?]
      :as this}]
  (log/info ::where "Initializing routes")
  ;; According to the docs, you can supply a
  ;; function here to update your routes dynamically.
  ;; This is supposed to be a nice way to update your
  ;; routes on the fly, without needing to restart
  ;; the server.
  ;; TODO: Get that working.
  (if production?
    (routes/wrapper)
    #'routes/wrapper)
  (assoc this ::routes (routes/wrapper this)))

(defmethod ig/halt-key! ::server
  [_ {:keys [::actual]
      :as this}]
  (http/stop actual))

(defn more-secure-headers
  ;; TODO: File a github issue. How am I the first person to notice this?
  "Because the default CSP implementation doesn't actually work"
  [original
   {:keys [:report-url
           ;; Let caller specify their own nonce creation function
           :nonce-generator
           :script-src]
    :as options}]
  {:pre [report-url]}
  (log/debug ::where "Building a more secure header handler")
  (let [default-leave (:leave original)]
    {:name ::more-secure-headers
     :enter (fn [ctx]
              (let [nonce (if nonce-generator
                            (nonce-generator)
                            (crypto.random/base64 16))]
                (assoc-in ctx [:request :csp-nonce] nonce)))
     :leave (fn [ctx]
              (let [csp-nonce (get-in ctx [:request :csp-nonce])
                    constant-settings (merge options {:base-uri "'none'"
                                                      :object-src "'none'"})
                    script-src-with-nonce (str "'nonce-" csp-nonce "' "
                                               (or script-src
                                                   "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"))
                    sec-headers (sec-hdr/csp-map->str (assoc constant-settings
                                                             :script-src script-src-with-nonce))]
                (assoc-in ctx
                          [:response :headers "Content-Security-Policy"]
                          sec-headers)))}))

(defmethod ig/init-key ::server
  [_
   {:keys [::routes]
    {:keys [:env]
     :as service} ::service
    :as this}]
  {:pre [env
         routes
         service]}
  (let [routed-service (merge service {::http/routes (::routes routes)
                                       ;; Don't block the thread that starts the server
                                       ::http/join? false})
        default-interceptors (http/default-interceptors routed-service)
        less-secure-interceptors (update default-interceptors
                                         ::http/interceptors
                                         (fn [starting]
                                           (log/info ::where "Trying to override secure-headers"
                                                     ::working-with (map :name starting))
                                           (map (fn [intc]
                                                  (if (= ::sec-hdr/secure-headers (:name intc))
                                                    (more-secure-headers intc {:report-url "https://my-site.com/admin/csp-violations"})
                                                    intc))
                                                starting)))]
    (assoc this
           ::actual
           ;; Wire up interceptor chains
           (-> less-secure-interceptors
               http/dev-interceptors
               http/create-server
               http/start))))
(comment
  (-> (http/default-interceptors {::http/routes []}) keys)
  )

(defmethod ig/init-key ::service
  [_ {:keys [::authcz
             ::host
             ::port
             ::production?
             ::routes
             ::ws]
      :or {port 8080
           host "localhost"}}]
  (let [{:keys [::authcz/cookie-key]} authcz
        default-headers (sec-hdr/create-headers)
        baseline {:env (if production?
                         :prod
                         :dev)
                  ;; For dev mode, allow any origin.
                  ;; TODO: Totally change the rules for production
                  ::http/allowed-origins (fn [x]
                                           (log/info "TODO: Verify the origin of" x)
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
                  ::http/host host
                  ::http/join? false
                  ::http/port port
                  ;; TODO: This next part has to go away for CSP issues
                  ;; Or, at least, I need to wrap it to add the nonce to
                  ;; any script tags. Once I figure out why I'm getting
                  ;; errors from scripts that mine try to reference.
                  ;; Paul deGrandis' advice was to just disable CSP
                  ;; for an SPA, which seems nuts at first glance
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
(comment
  (->  (sec-hdr/create-headers) (get "Content-Security-Policy"))
  "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"
  (->  (sec-hdr/create-headers)
       (assoc "Content-Security-Policy"
              (str "object-src 'none';\n"
                   "script-src 'replaceMe!' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;\n"
                   "base-uri 'none';\n"
                   "report-uri https://my-origin/")))
  (sec-hdr/csp-map->str {:base-uri "'none'"
                         :object-src "'none'"
                         :report-uri "https://my-origin/csp-violations"
                         :script-src "'replaceMe!' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"})
  (sec-hdr/secure-headers)
  )

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
