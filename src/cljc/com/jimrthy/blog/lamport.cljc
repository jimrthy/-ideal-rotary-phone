(ns com.jimrthy.blog.lamport
  (:require [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [io.pedestal.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::clock nat-int?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

(defn tick!
  ([clock]
   (swap! clock inc))
  ([clock remote-time]
   (swap! clock
          (fn [my-time]
            (if (and remote-time (> remote-time my-time))
              ;; Remote clocks might be ahead of ours
              (inc remote-time)
              (inc my-time))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(def ticker
  ;; This is really a pedestal interceptor.
  ;; Including it in the cljc seems dubious
  "Make sure clock ticks when messages arrive and depart"
  {:name ::ticker
   :enter (fn [{:keys [::clock]
                :as ctx}]
            (log/info ::entering @clock)
            (update ctx ::clock tick!))
   :leave (fn [{:keys [::clock]
                :as ctx}]
            (log/info ::leaving @clock)
            (update ctx ::clock tick!))})

(defmethod ig/init-key ::clock
  [_ opts]
  (atom 0))
