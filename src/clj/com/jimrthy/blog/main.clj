(ns com.jimrthy.blog.main
  (:require [com.jimrthy.blog.system :as sys]
            [integrant.core :as ig]))

(defn init
  [opts]
  (let [dscr (sys/init opts)]
    (ig/init dscr)))

(defn -main
  [& args]
  ;; TODO: Probably need to parse args
  ;; Boot does weird things with that aspect
  ;; that causes (boot (run)) to behave differently
  ;; than just running the jar.
  ;; That probably doesn't matter here, since the
  ;; main point should stem from adding a .war
  ;; file to wildfly.
  ;; But it's worth keeping in mind.
  (let [system (init args)
        completion-flag (::sys/done system)]
    @completion-flag
    (try
      (finally
        (ig/halt! system)))))
