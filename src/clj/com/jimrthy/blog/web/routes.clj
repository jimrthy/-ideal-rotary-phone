(ns com.jimrthy.blog.web.routes
  (:require [cheshire.core :as json]
            [clojure.pprint :refer (pprint)]
            [com.jimrthy.blog.web.response-wrappers :as respond]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.content-negotiation :as con-neg]
            [io.pedestal.http.params :as params]
            [io.pedestal.http.ring-middlewares :as mw]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handlers
;;; These don't belong in here.
;;; They're really just proof-of-concept

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

(defn view-article
  [{{:keys [:article-id]
     :as path-params} :path-params
    :as request}]
  (respond/ok (str "Returning article #" article-id)))

(def insert-article
  {:name ::article-insert
   :enter (fn [{:keys [:request]
                :as ctx}]
            ;; TODO: Add an interceptor for decoding body params
            (let [{:keys [:body :body-params :edn-params :json-params :headers]} request]
              (log/debug ::what "PUT a new article"
                         ::request-keys (keys request)
                         ::headers headers
                         ::body (slurp body)
                         ;; This is arriving as nil
                         ::body-params body-params
                         ;; If I submit the request as EDN, the
                         ;; params wind up here.
                         ;; This is annoying.
                         ;; TODO: See whether they have any default
                         ;; interceptors that just merge all possible/
                         ;; likely parameter maps.
                         ;; If not, go ahead and write one.
                         ::edn-params edn-params
                         ;; When I specify that the request is
                         ;; content-type=application/json in
                         ;; the headers, I get this back.
                         ::json-params json-params))
            (assoc ctx :response (respond/ok "Article inserted")))})

(defn upload-file
  [request]
  ;; FIXME: Get this written
  ;; Use -f and @ w/ httpie for the client portions.
  (respond/internal-error "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual

(defn item-manager
  [{:keys [json-params]
    :as request}]
  (log/info ::incoming (keys request) ::json-params json-params)
  (let [{:keys [:result]} json-params
        {:keys [:action :parameters]} result]
    (respond/ok (json/generate-string {"speech" (pr-str {"action" action
                                                         "parameters" parameters})}))))

;; Sample:
;; ":com.jimrthy.blog.web.routes/json-params
;;  {:id "002cf7c5-df6c-4473-9f73-ca721fe598ab", :timestamp "2017-10-13T15:03:40.016Z", :lang "en", :result {:source "agent", :actionIncomplete false, :fulfillment {:speech "", :messages [{:type 0, :id "9023267b-9a47-4bd5-ae93-e050bd226c88", :speech ""}]}, :score 0.9100000262260437, :resolvedQuery "add coconut", :action "item.add", :contexts [], :parameters {:item "coconut"}, :metadata {:intentId "8d550f05-a694-4d57-b925-38daea8da8bc", :webhookUsed "true", :webhookForSlotFillingUsed "false", :intentName "Add item"}}, :status {:code 206, :errorType "partial_content", :errorDetails "Webhook call failed. Error: 404 Not Found"}"


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Magic constants

(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

;; TODO: Probably want to use the :mime-types key in the service
;; map returned by init-key in service instead
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
    (respond/ok (str "Hello, " nm "\n"))))

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
  (comment
    #{["/api/v1/article/:article-id" :get view-article :route-name ::view-article]
      ["/api/v1/article/:article-id" :put [(body-params/body-params) params/keyword-body-params insert-article] :route-name ::add-article]
      ["/api/v1/echo" :get echo :route-name ::get-echo]
      ["/api/v1/echo" :post echo :route-name ::post-echo]
      ["/api/v1/file/:file-id" :put [(mw/multipart-params) upload-file] :route-name ::add-file]
      ["/api/v1/greet" :get [coerce-body content-neg-intc greet] :route-name ::hello-world]
      #_["/api/v1/login" :get (conj (intrcptr/default-interceptor-chain auth-manager)
                                    list-logged-in-users)]
      #_["/api/v1/login" :post (conj (vec (remove #(or (= intrcptr/authc %)
                                                       (= intrcptr/authz %))
                                                  (intrcptr/default-interceptor-chain auth-manager)))
                                     (body-params) log-in)]
      #_["/api/v1/login" :delete (conj (intrcptr/default-interceptor-chain auth-manager) log-out)]})
  #{["/webhook" :post [(body-params/body-params) params/keyword-body-params item-manager] :route-name ::my-list-webhook]})
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
  (comment
    (route/expand-routes (table this)))
  (fn []
    (log/debug ::where "Expanding routes")
    (route/expand-routes (table this))))
