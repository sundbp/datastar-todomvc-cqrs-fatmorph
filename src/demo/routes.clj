(ns demo.routes
  (:require
   [demo.ui.todo :as todo-view]
   [demo.api.todo :as todo-api]
   [reitit.openapi :as openapi]
   [ring.util.response :as r.response]
   [starfederation.datastar.clojure.adapter.http-kit2 :as hk-adapter]))

(defn routes
  [{:keys [todo-db event-bus by-type-publication]}]
  [["/" {:no-doc true
         :get (constantly (r.response/redirect "/ui/todo"))}]

   ["/ui" {:no-doc true}
    ["/todo" {:get (todo-view/page todo-db)}]]

   ["/sse" {:no-doc true
            :middleware [hk-adapter/start-responding-middleware]}
    ["/todo" {:get (todo-view/sse todo-db by-type-publication)}]]

   ["/api"
    ["/add-todo" {:post (todo-api/add-todo todo-db event-bus)}]

    ["/toggle-todos" {:patch (todo-api/toggle-todos todo-db event-bus)}]

    ["/remove-completed-todos" {:delete (todo-api/remove-completed todo-db event-bus)}]

    ["/remove-todo" {:delete (todo-api/remove-todo todo-db event-bus)}]

    ["/set-todos-filter" {:patch (todo-api/set-todos-filter event-bus)}]]

   ["/openapi" {:no-doc true}
    ["/openapi.json"
     {:get {:handler (openapi/create-openapi-handler)
            :openapi {:info {:title "demo" :version "0.0.1"}}}}]]])
