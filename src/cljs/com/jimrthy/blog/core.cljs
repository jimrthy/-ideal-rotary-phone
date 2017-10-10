(ns com.jimrthy.blog.core
  (:require [com.jimrthy.blog.lamport :as lamport]
            [integrant.core :as ig]))

(enable-console-print!)

(defonce state {::lamport (ig/init-key ::clock {})})

(defn reload!
  []
  (println "Reloading2")
  (swap! state
         #(update % ::lamport inc)))

(defn main
  [& args]
  (println "(main) called with:")
  (doseq [arg args]
    (println arg)))
