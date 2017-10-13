(ns user
  "Pieces that are vital for interactive development

  Well, they will be, if I can figure out how to make
  this work."
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer (pprint)]
            [clojure.reflect :as reflect]
            [clojure.repl :refer (doc pst source)]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.jimrthy.blog.web.routes :as blog-routes]
            [com.jimrthy.blog.system :as sys]
            [com.jimrthy.blog.web :as web]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            ;; try-routing-for is especially useful
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :refer (table-routes)]
            [io.pedestal.test :as ped-test]
            [integrant.repl :refer [clear go halt
                                    init prep
                                    ;; reset isn't working
                                    ;; it seems like an issue with tools.namespace
                                    reset reset-all]]
            [integrant.repl.state :as ig-state]))

;; FIXME: Pull these from the environment
(let [opts {::web/service {::web/host "localhost"
                           ::web/port 8040}}]
  (integrant.repl/set-prep! (partial sys/init opts)))

(defn check
  []
  (println "Just verifying that something works"))

(defn class-path
  "Returns the items on the CLASSPATH"
  []
  (-> (java.lang.ClassLoader/getSystemClassLoader)
      .getURLs
      seq))

(defn prepped
  "Examine the pre-started system definition"
  []
  ig-state/config)

(defn system
  []
  ig-state/system)

;;;; REPL conveniences

(defn get-table-routes
  ([system]
   (-> system
       ::web/routes
       blog-routes/table
       table-routes))
  ([]
   (get-table-routes (system))))

(defn check-response
  [verb url]
  (let [svc-fn (-> (system) ::web/server ::web/actual :io.pedestal.http/service-fn)]
    (ped-test/response-for svc-fn verb url)))

(defn print-routes
  "Print application's routes"
  ([system]
   (route/print-routes (get-table-routes system)))
  ([]
   (route/print-routes (get-table-routes))))

(defn named-route
  "Return details re: route-name, based on routing-component"
  ([route-name]
   (named-route (system) route-name))
  ([system route-name]
   (->> system
        get-table-routes
        (filter #(= route-name (:route-name %)))
        first)))

(defn print-route
  "Prints a route and its interceptors"
  [rname]
  (letfn [(joined-by
            [s coll]
            (apply str (interpose s coll)))
          (repeat-str
            [s n]
            (apply str (repeat n s)))
          (interceptor-info
            [i]
            (let [iname (or (:name i) "<handler>")
                  stages (joined-by ","
                                    (keys (filter
                                           (comp (complement nil?) val)
                                           (dissoc i :name))))]
              (str iname " (" stages ")")))]
    (when-let [rte (named-route rname)]
      (let [{:keys [path method route-name interceptors]} rte
            name-line (str "[" method " " path " " route-name "]")]
        (joined-by "\n"
                   (into [name-line (repeat-str "-" (count name-line))]
                         (map interceptor-info interceptors)))))))

(defn response-for
  "Get the response based on system"
  ([system verb url]
   (let [svc-fn (get-in system [::web/server ::web/actual ::http/service-fn])]
     (ped-test/response-for svc-fn verb url)))
  ([verb url]
   (response-for (system) verb url)))
(comment (response-for :get "/index.html"))

(defn recognize-route
  "Verifies the requested HTTP verb and path, via router"
  ([system verb path]
   (-> system
       get-table-routes
       (route/try-routing-for :prefix-tree path verb)))
  ([verb path]
   (recognize-route (system) verb path)))
(comment
  (recognize-route (system) :get "/api/v1/echo"))

(defn dev-url-for
  "Returns a URL string for the named route"
  [route-name & opts]
  (let [f (route/url-for-routes (get-table-routes))
        defaults {:host "localhost" :scheme :http :port 8000}
        route-opts (flatten (seq (merge defaults (apply hash-map opts))))]
    (apply f route-name route-opts)))
(comment
  (route/url-for-routes (get-table-routes))
  (dev-url-for ::blog-routes/get-echo))
