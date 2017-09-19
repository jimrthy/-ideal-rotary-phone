(ns com.jimrthy.blog.system
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::done
  [_ _]
  ;; Fulfill this to let a "standard" run complete
  (promise nil))

(defn init
  [opts]
  {::done {}})
