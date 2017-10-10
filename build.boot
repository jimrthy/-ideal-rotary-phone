;;; This is based very heavily on
;;; https://github.com/Deraen/saapas

(def project 'com.jimrthy.blog)
(def version "0.1.0-SNAPSHOT")

(set-env! :dependencies
          '[[adzerk/boot-cljs "2.1.4" :scope "test"]
            [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
            [adzerk/boot-reload "0.5.2" :scope "test"]
            #_[adzerk/boot-test "RELEASE" :scope "test"]
            [binaryage/devtools "0.9.7" :scope "test"]
            [boot-immutant "0.6.0" :scope "test"]
            [cheshire "5.8.0" :exclusions [org.clojure/clojure]]
            [com.cemerick/piggieback "0.2.2" :scope "test" :exclusions [args4j
                                                                        org.clojure/clojure
                                                                        org.clojure/clojurescript
                                                                        org.clojure/tools.reader]]
            [crisptrutski/boot-cljs-test "0.3.4" :scope "test"]
            [crypto-random "1.2.0" :exclusions [commons-codec
                                                org.clojure/clojure]]
            [doo "0.1.8" :scope "test" :exclusions [org.clojure/clojure
                                                    org.clojure/clojurescript]]
            [environ "1.1.0" :exclusions [org.clojure/clojure]]
            [integrant "0.6.1" :exclusions [org.clojure/clojure]]
            ;; This really should be scoped to "test" or "dev",
            ;; but the current configuration places it squarely in
            ;; the middle of main
            [integrant/repl "0.2.0" :exclusions [integrant
                                                 org.clojure/clojure
                                                 org.clojure/tools.namespace]]
            [io.pedestal/pedestal.service "0.5.3" :exclusions [cheshire
                                                               commons-codec
                                                               com.cognitect/transit-clj
                                                               com.cognitect/transit-java
                                                               com.fasterxml.jackson.core/jackson-core
                                                               com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                               com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                                                               org.clojure/clojure
                                                               org.clojure/core.async
                                                               org.clojure/tools.analyzer.jvm
                                                               org.clojure/tools.reader
                                                               org.msgpack/msgpack
                                                               org.slf4j/log4j-over-slf4j
                                                               org.slf4j/slf4j-api
                                                               ring/ring-core]]
            [io.pedestal/pedestal.immutant "0.5.3" :exclusions [org.clojure/clojure
                                                                org.immutant/web]]
            [io.pedestal/pedestal.service-tools "0.5.3"
             :scope "test"
             :exclusions [cheshire
                          commons-codec
                          com.cognitect/transit-clj
                          com.cognitect/transit-java
                          com.fasterxml.jackson.core/jackson-core
                          com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                          com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                          org.clojure/clojure
                          org.clojure/core.async
                          org.clojure/java.classpath
                          org.clojure/tools.analyzer.jvm
                          org.clojure/tools.namespace
                          org.clojure/tools.reader
                          org.msgpack/msgpack
                          org.slf4j/log4j-over-slf4j
                          org.slf4j/slf4j-api
                          ring/ring-core]]
            [metosin/boot-alt-test "0.3.2" :scope "test"]
            [org.clojure/clojure "1.9.0-beta2"]
            [org.clojure/clojurescript "1.9.946" :scope "test" :exclusions [org.clojure/clojure]]
            [org.clojure/core.async "0.3.443" :exclusions [org.clojure/clojure
                                                           org.clojure/tools.reader]]
            [org.clojure/java.classpath "0.2.3" :scope "test" :exclusions [org.clojure/clojure]]
            [org.clojure/spec.alpha "0.1.134" :exclusions [org.clojure/clojure]]
            [org.clojure/test.check "0.10.0-alpha2" :scope "test" :exclusions [org.clojure/clojure]]
            [org.clojure/tools.namespace "0.3.0-alpha4" :exclusions [org.clojure/clojure
                                                                     org.clojure/tools.reader]]
            [org.clojure/tools.nrepl "0.2.13" :scope "test" :exclusions [org.clojure/clojure]]
            [org.immutant/messaging "2.1.9" :exclusions [ch.qos.logback/logback-classic
                                                         ch.qos.logback/logback-core
                                                         org.clojure/clojure
                                                         org.clojure/tools.reader
                                                         org.slf4j/slf4j-api]]
            ;; Pedestal uses 2.1.4. There have been lots of bug
            ;; fixes since then. So go with the latest/greatest
            [org.immutant/web "2.1.9" :exclusions [ch.qos.logback/logback-classic
                                                   ch.qos.logback/logback-core
                                                   commons-codec
                                                   org.clojure/clojure
                                                   org.clojure/tools.reader
                                                   org.jboss.logging/jboss-logging
                                                   org.slf4j/slf4j-api]]
            [org.martinklepsch/boot-garden "1.3.2-0" :scope "test"]
            [org.omcljs/om "1.0.0-beta1" :exclusions [com.fasterxml.jackson.core/jackson-core
                                                      org.clojure/clojure]]
            [org.slf4j/slf4j-api "1.8.0-alpha2"]
            [org.slf4j/slf4j-log4j12 "1.8.0-alpha2" ]
            ;; Q: Does this make any sense?
            [ring-middleware-format/ring-middleware-format "0.7.2" :exclusions [cheshire
                                                                                commons-codec
                                                                                com.cognitect/transit-clj
                                                                                com.cognitect/transit-java
                                                                                com.fasterxml.jackson.core/jackson-core
                                                                                com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                                                com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                                                                                org.clojure/clojure
                                                                                org.clojure/tools.reader
                                                                                ring/ring-core]]
            [samestep/boot-refresh "0.1.0" :scope "test" :exclusions [org.clojure/tools.namespace]]
            ;; None of the linters currently work with
            ;; clojure 1.9. But maybe someday...
            [tolitius/boot-check "0.1.5" :scope "test"]
            [weasel "0.7.0" :scope "test" :exclusions [org.clojure/clojure
                                                       org.clojure/clojurescript]]]
          :resource-paths #{"resources" "src/clj" "src/cljc" "src/cljs"}
          ;; It's OK for test path to be included here, since JAR doesn't include source files
          :source-paths   #{"dev" "dev-resources"
                            "src/cljs" "test/clj"
                            "test/cljs"})

(require '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
         '[adzerk.boot-reload :refer [reload]]
         '[boot.immutant :refer (gird)]
         '[crisptrutski.boot-cljs-test :refer [test-cljs]]
         '[metosin.boot-alt-test :refer [alt-test]]
         '[org.martinklepsch.boot-garden :refer [garden]]
         '[samestep.boot-refresh :refer [refresh]]
         '[tolitius.boot-check :as check])

(task-options!
 aot {:namespace   #{'com.jimrthy.blog.main}}
 jar {:main        'com.jimrthy.blog.main
      :file        (str "jimrthy-blog-" version "-standalone.jar")}
 pom {:project     project
      :version     version
      :description "Because everyone has to write their own blog, sooner or later"
      :url         "http://blog.jimrthy.com"
      :scm         {:url "Not Publicly Available"}
      :license     {"Copyright 2017, James Gatannah, All Rights Reserved"
                    "Totally proprietary, for now"}})

(deftask add-version-text
  "Add a version.txt resource

Access in code via (some-> \"version.txt\" clojure.java.io/resource slurp clojure.string/trim)"
  []
  (with-pre-wrap fs
    (let [t (tmp-dir!)]
      (spit (clojure.java.io/file t "version.txt") version)
      (-> fs (add-resource t) commit!))))

(deftask build-war
  "For production"
  []
  (comp
   (uber :as-jars true)
   (aot :all true)
   (gird :init-fn 'com.jimrthy.blog.main/init)
   (war)
   (target)))

(deftask build-dev-war
  "For deploying to a local wildfly development instance"
  []
  (comp
   (boot.immutant/gird :dev true :init-fn 'com.jimrthy.blog.main/init)
   (war)
   (target)))

(comment
  (deftask check-conflicts
    "Verify that there are no dependency conflicts."
    []
    (with-pass-thru fs
      (require '[boot.pedantic :as pedant])
      (let [dep-conflicts (resolve 'pedant/dep-conflicts)]
        ;; I think this next line is broken.
        ;; TODO: Verify that and fix it
        (if-let [conflicts (not-empty (dep-conflicts pod/env))]
          (throw (ex-info (str "Unresolved dependency conflicts. "
                               "Use :exclusions to resolve them!")
                          conflicts))
          (println "\nVerified there are no dependency conflicts"))))))

(deftask check-sources
  "Run static analysis"
  []
  (comp (check/with-bikeshed)
        (check/with-eastwood)
        (check/with-kibit)
        (check/with-yagni)))

;; I think I'd like to call this on a cljs REPL reload,
;; but that just doesn't make any sense.
;; This is something known at build time.
;; That needs to be something available on the browser.
(defn my-reload
  []
  ;; Don't particularly want to know about this here
  #_(require 'com.jimrthy.blog.core)
  ;; Actually, I can't.
  ;; This is a cljs piece
  #_(com.jimrthy.blog.core/reload)
  )

(deftask dev
  "Start the dev environment."
  [s speak bool "Notify when build is done"
   p port PORT int "Port for web server"
   a use-sass bool "Use Scss instead of less"
   t test-cljs bool "Compile and run cljs tests"]
  (comp
   (watch)
   ;; Q: What does the open-file option really do?
   ;; A: shells out to whichever command is specified w/ 3 args
   ;; I've lost the reference for what each of those args actually
   ;; means.
   ;; But it's something like:
   ;; file-name line-number column
   ;; Note that this really needs to happen between cljs-repl and
   ;; cljs.
   ;; It modifies files that impact the cljs task, but relies
   ;; on symbols that get loaded by cljs-repl
   ;; It's annoying for it to be sensitive to that sort of thing,
   ;; but, really, how else could it work?
   (reload :open-file "emacsclient -n +%s:%s %s"
           :ids #{"js/main"}
           ;; I really don't want to know anything about this here
           ;; But this seems to be the key to a figwheel-style workflow.
           ;; The first time, anyway.
           ;; If I include this, then I get feedback from core.cljs.
           ;; Then on subsequent reloads I get HUD errors that
           ;; it isn't declared.
           :on-jsload 'com.jimrthy.blog.core/reload!)
   ;; This starts a repl server with piggieback middleware
   ;; Note that you still need to create your own connection to
   ;; that REPL
   ;; adzerk.boot-cljs-repl includes a (start-repl) task(?)
   ;; that you should be able to call from a regular clojure
   ;; REPL to make that happen.
   ;; Except that I'm getting errors that look like they could
   ;; possibly be conflicts between http-kit and aleph
   (cljs-repl :ids #{"js/main"})
   ;; This sets up the cljs compiler
   (cljs :ids #{"js/main"} :source-map true :optimizations :none)
   (sift :move {#"^js/(.*)" "public/js/$1"})
   (target :dir #{"target"})
   ;; This next form was probably something I inherited from
   ;; https://github.com/Deraen/saapas in the template
   ;; from which I copied this.
   ;; It may or may not be a useful idea.
   #_(start-app :port port)
   (if speak
     (boot.task.built-in/speak)
     identity)))

(deftask run
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  (require '[com.jimrthy.blog.main :as app])
  (apply (resolve 'app/-main) args))

(deftask interact
  "Main point is serving up compiled cljs"
  []
  (set-env! :resource-paths (fn [cur]
                              (println "Getting ready to swap target/ for resources/ inside"
                                       cur
                                       "a"
                                       (class cur))
                              (-> cur
                                  (conj "target")
                                  (disj "resources"))))
  (comp
   (aot)
   (add-version-text)
   (pom)
   (target)
   (repl)))

(ns-unmap *ns* 'test)

(deftask test
  []
  (comp
   (alt-test)
   ;; FIXME: This is not a good place to define which namespaces to test
   (test-cljs :namespaces #{"frontend.core-test"})))

(deftask autotest []
  (comp
   (watch)
   (test)))

(deftask package
  "Build the package"
  []
  (comp
   (cljs :optimizations :advanced
         :compiler-options {:preloads nil})
   (garden :styles-var 'cljs-comparison.styles/screen
           :output-to "css/garden.css")
   (aot)
   (add-version-text)
   (pom)
   (uber)
   (jar :file "jimrthy-blog.jar")
   (sift :include #{#".*\.jar"})
   (target)))
