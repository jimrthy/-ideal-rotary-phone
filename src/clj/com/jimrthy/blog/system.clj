(ns com.jimrthy.blog.system
  (:require  [com.jimrthy.blog.lamport :as lamport]
             [com.jimrthy.blog.web :as web]
            [integrant.core :as ig]
            [io.pedestal.log :as log]))

(defmethod ig/init-key ::done
  [_ _]
  ;; Fulfill this to let a "standard" run complete
  (promise))

(defmethod ig/init-key ::production?
  [_
   {:keys [::prod?]
    :or {prod? false}}]
  prod?)

(defn init
  [opts]
  (log/info ::where "Top of system/init")
  ;; TODO: Let opts override defaults
  {::done {}
   ::lamport/clock {}
   ::production? {::prod? false}
   ::web/authcz {}
   ::web/routes {:com.jimrthy.blog.web.routes.authcz (ig/ref ::web/authcz)}
   ::web/server {::web/routes (ig/ref ::web/routes)
                 ::web/service (ig/ref ::web/service)}
   ::web/service {::web/authcz (ig/ref ::web/authcz)
                  ::web/port 8002
                  ::web/production? (ig/ref ::production?)
                  ::web/web-socket {}
                  ::web/ws (ig/ref ::web/socket)}
   ::web/socket {}})
