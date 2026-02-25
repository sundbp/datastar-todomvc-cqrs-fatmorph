(ns demo.http-server
  (:require
   [taoensso.telemere :as log]
   [clj-uuid :as generate-uuid]
   [reitit.ring :as r.ring]
   [reitit.swagger-ui :as r.swagger-ui]
   [reitit.ring.coercion :as r.coercion]
   [reitit.coercion.malli :as r.malli]
   [reitit.ring.middleware.muuntaja :as r.muuntaja]
   [reitit.ring.middleware.exception :as r.exception]
   [reitit.ring.middleware.parameters :as r.parameters]
   reitit.ring.middleware.dev
   [reitit.dev.pretty :as r.pretty]
   ring.middleware.keyword-params
   ring.middleware.cookies
   ring.middleware.session
   ring.middleware.session.memory
   malli.util
   [muuntaja.core :as formatting]
   muuntaja.format.core
   [org.httpkit.server :as http-server]))

;;; state

(def ^:private formatting-instance
  (formatting/create formatting/default-options))

(defn wrap-debug
  [app]
  (fn [req]
    (prn "debug req" (:headers req))
    (let [r (app req)]
      (prn "debug response" r)
      r)))

(defn handler [message exception request]
  {:status 500
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(defn wrap-session-id
  [app]
  (fn [req]
    (if (get-in req [:session :uid])
      (app req)
      (app (assoc-in req [:session :uid] (generate-uuid/v1))))))

(defn create-router
  [{:keys [routes not-found-body http-server]}]
  (assert routes "No routes passed to create-router!")
  (r.ring/ring-handler
   (r.ring/router
    routes
    { ;;:validate r.ring.spec/validate
     :exception r.pretty/exception
     ;; note: useful for debugging
     ;;:reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
     :data {:coercion (r.malli/create
                       {;; set of keys to include in error messages
                        :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
                        ;; schema identity function (default: close all map schemas)
                        :compile malli.util/closed-schema
                        ;; strip-extra-keys (effects only predefined transformers)
                        :strip-extra-keys true
                        ;; add/set default values
                        :default-values true
                        ;; malli options
                        :options nil})
            :muuntaja formatting-instance
            :middleware [;; note: for debugging uncomment this middleware
                         ;;wrap-debug
                         ring.middleware.cookies/wrap-cookies
                         [ring.middleware.session/wrap-session
                          {:cookie-attrs {:secure false
                                          :same-site :strict}
                           :store (ring.middleware.session.memory/memory-store)}]
                         wrap-session-id
                         ;; query-params & form-params
                         r.parameters/parameters-middleware
                         ring.middleware.keyword-params/wrap-keyword-params
                         ;; content-negotiation
                         r.muuntaja/format-negotiate-middleware
                         ;; encoding response body
                         r.muuntaja/format-response-middleware
                         ;; exception handling
                         r.exception/exception-middleware
                         ;; decoding request body
                         r.muuntaja/format-request-middleware
                         ;; coercing response bodys
                         r.coercion/coerce-response-middleware
                         ;; coercing request parameters
                         r.coercion/coerce-request-middleware]}})
   ;; the default handler
   (r.ring/routes
    (when (:openapi http-server)
      (r.swagger-ui/create-swagger-ui-handler
       {:path (get-in http-server [:openapi :path])
        :config (get-in http-server [:openapi :config])}))
    (r.ring/create-default-handler
     {:not-found (constantly
                  {:status  404
                   :headers {"Content-Type" "text/html"}
                   :body    not-found-body})}))))

;;; state

(defn start
  [{{:keys [server-options]} :http-server :as ctx}]
  (log/log! {:level :info :id ::start :data {:profile (:profile ctx)
                                             :http-server-options server-options}})

  {:server (http-server/run-server (create-router ctx)
                                   (assoc server-options :legacy-return-value? false))})

(defn stop
  [{:keys [server]}]
  (log/log! {:level :info :id ::stop})
  (http-server/server-stop! server {:timeout 200}))
