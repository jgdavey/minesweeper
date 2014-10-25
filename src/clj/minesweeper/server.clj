(ns minesweeper.server
  (:require [clojure.java.io :as io]
            [minesweeper.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel start-cljx]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [compojure.response :as response]
            [ring.util.mime-type :refer [ext-mime-type]]
            [ring.util.response :as r]
            [ring.middleware.head :refer [wrap-head]]
            [ring.middleware.reload :as reload]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]])
  (:import [java.net URL]
           [java.io FileNotFoundException]))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn- request-path
  "Return the path + query of the request."
  [request]
  (str (:uri request)
       (if-let [query (:query-string request)]
         (str "?" query))))

(defn- content-type-response
  [resp req & [opts]]
  (if (r/get-header resp "Content-Type")
    resp
    (if-let [mime-type (ext-mime-type (:uri req) (:mime-types opts))]
      (r/content-type resp mime-type)
      resp)))

(defn wrap-content-type [handler & [opts]]
  (fn [req]
    (if-let [resp (handler req)]
      (content-type-response resp req opts))))

(defn proxy-response
  "A route that returns a 404 not found response, with its argument as the
  response body."
  [url-root]
  (->
    (fn [request]
      (try
        (let [url (URL. (str url-root (request-path request)))]
          (response/render url request))
        (catch FileNotFoundException e
          (println e))))
    wrap-content-type
    wrap-head))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/" req (page))
  (GET "/index.html" req (page))
  (proxy-response "http://localhost:4568"))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'routes))
    (api routes)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (when is-dev?
        (start-figwheel)
        (start-cljx))
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port port :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
