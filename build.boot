(def project 'ideal-rotary-phone)
(def version "0.1.0-SNAPSHOT")

(set-env! :dependencies '[[adzerk/boot-reload "0.5.2" :scope "test"]
                          [boot-immutant "0.6.0" :scope "test"]
                          [io.pedestal/pedestal.immutant "0.5.3" :exclusions [org.immutant/web]]
                          [io.pedestal/pedestal.service "0.5.3" :exclusions [org.slf4j/log4j-over-slf4j]]
                          [io.pedestal/pedestal.service-tools "0.5.3"
                           :scope "test"
                           :exclusions [org.slf4j/log4j-over-slf4j]]
                          [org.clojure/clojure "1.9.0-beta4"]
                          [org.clojure/core.async "0.3.443" :exclusions [org.clojure/clojure
                                                                         org.clojure/tools.reader]]
                          [org.clojure/spec.alpha "0.1.143"]
                          ;; Pedestal uses 2.1.4. There have been lots of bug
                          ;; fixes since then. So go with the latest/greatest
                          [org.immutant/web "2.1.9" :exclusions [org.jboss.logging/jboss-logging]]
                          [org.slf4j/slf4j-api "1.8.0-alpha2"]]
          :source-paths #{"src" "test"})

(require '[boot.immutant :refer (gird)])

(deftask build-immutant-dev-war
  "For deploying to a local wildfly development instance"
  []
  (comp
   (gird :dev true :init-fn 'com.jimrthy.blog.main/init)
   (war)
   (target)))
