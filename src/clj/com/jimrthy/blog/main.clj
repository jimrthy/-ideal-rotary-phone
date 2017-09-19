(ns com.jimrthy.blog.main
  (:require [com.jimrthy.blog.system :as sys
             integrant.core :as ig]))

(defn init
  [opts]
  (let [dscr (sys/init opts)]
    (ig/init dscr)))

(defn -main
  [& args]
  ;; TODO: Probably need to parse args
  (let [system (init opts)
        completion-flag (::sys/done system)]
    @completion-flag
    (try
      (finally
        (ig/halt! system)))))
