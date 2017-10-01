(ns com.jimrthy.blog.web.response-wrappers
  "Try to standardize what responses look like")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn respond
  [status body]
  {:status status :body body})

(defn bad-request
  [body]
  (respond 400 body))

(defn forbidden
  [body]
  (respond 403 body))

(defn internal-error
  [body]
  (respond 500 body))

(defn ok
  [body]
  (respond 200 body))

(defn unauthenticated
  [body]
  ;; This isn't semantically correct.
  ;; According to wikipedia: "must include a WWW-Authenticate header
  ;; containing a challenge applicable to the requested resource."
  ;; But this is the basic idea
  (respond 401 body))
