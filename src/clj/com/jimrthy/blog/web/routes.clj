(ns com.jimrthy.blog.web.routes
  (:require [cheshire.core :as json]
            [clojure.pprint :refer (pprint)]
            [com.jimrthy.blog.web.response-wrappers :as respond]
            [io.pedestal.http.content-negotiation :as con-neg]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handlers

(def echo
  {:name ::echo
   :enter (fn
            [context]
            (let [result
                  (->> context
                       :request
                       respond/ok
                       (assoc context :response))]
              (log/info ::where "Echoing back"
                        ::response (with-out-str (pprint result)))
              result))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Magic constants

(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

;; TODO: need to add an interceptor that coerces the body
(def content-neg-intc (con-neg/negotiate-content supported-types))

(defn accepted-type
  [ctx]
  (get-in ctx [:request :accept :field] "text/plain"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/generate-string body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body {:name ::coerce-body
                  :leave (fn [ctx]
                           (if (get-in ctx [:response :headers "Content-Type"])
                             ctx
                             (update ctx :response coerce-to (accepted-type ctx))))})

(defn greet
  [request]
  (let [nm (get-in request [:query-params :name])]
    {:status 200 :body (str "Hello, " nm "\n")}))

(defn table
  "Return the HTTP routes"
  [{auth-manager ::authcz
    :as this}]
  ;; Q: Where is this log message going?
  (log/info ::message (str "Building route table around\n"
                           (with-out-str (pprint auth-manager))
                           "\nfor\n"
                           (keys this)
                           "\nin\n"
                           this))
  ;; Realistically, none of these belong here.
  ;; echo was just for testing.
  ;; login really needs to go over the websocket.
  ;; But this is the basic idea
  #{["/api/v1/echo" :get echo :route-name ::get-echo]
    ["/api/v1/echo" :post echo :route-name ::post-echo]
    ["/api/v1/greet" :get [coerce-body content-neg-intc greet] :route-name ::hello-world]
    #_["/api/v1/login" :get (conj (intrcptr/default-interceptor-chain auth-manager)
                                list-logged-in-users)]
    #_["/api/v1/login" :post (conj (vec (remove #(or (= intrcptr/authc %)
                                                   (= intrcptr/authz %))
                                              (intrcptr/default-interceptor-chain auth-manager)))
                                 (body-params) log-in)]
    #_["/api/v1/login" :delete (conj (intrcptr/default-interceptor-chain auth-manager) log-out)]})
(comment
  (table {})
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn wrapper
  "Routes can be a function that resolves routes,
  we can use this to set the routes to be reloadable

  At least that's the theory in the docs. So far, I
  haven't had any success with it.

  TODO: Figure out how to make this work."
  [this]
  ;; I think that I want to do something like this,
  ;; at least in debug mode.
  ;; It probably involves too much overhead for production,
  ;; but that "probably" is very debatable.
  ;; If we're talking to something like a database or
  ;; message queue, or external services, any time spent
  ;; recalculating this is going to be almost negligible.
  ;; c.f. the "Developing at the REPL" section of the
  ;; pedestal.io guides
  (comment (fn [x]
             (log/debug ::param x)
             (route/expand-routes (table this))))
  (route/expand-routes (table this)))
