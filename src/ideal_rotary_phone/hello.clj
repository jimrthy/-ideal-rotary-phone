(ns ideal-rotary-phone.hello
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

(fedfn respond-hello
       [request]
       {:status 200 :body "Hello, World"})
