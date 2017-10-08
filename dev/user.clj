(ns user
  "Pieces that are vital for interactive development

  Well, they will be, if I can figure out how to make
  this work."
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.pprint :refer (pprint)]
            [clojure.reflect :as reflect]
            [clojure.repl :refer (doc pst source)]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.jimrthy.blog.system :as sys]
            [com.jimrthy.blog.web :as web]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            ;; try-routing-for is especially useful
            [io.pedestal.http.route :as route]
            [integrant.repl :refer [clear go halt
                                    init prep
                                    ;; reset isn't working
                                    ;; it seems like an issue with tools.namespace
                                    reset reset-all]]
            [integrant.repl.state :as ig-state]))

;; FIXME: Pull these from the environment
(let [opts {}]
  (integrant.repl/set-prep! (partial sys/init opts)))

(println "user ns loaded")

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
