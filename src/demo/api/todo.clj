(ns demo.api.todo
  (:require
   [clojure.core.async :as async]
   clojure.string
   [clojure.core :as c]))

(defn set-todos-filter
  [event-bus]
  (fn [request]
    (async/put! event-bus {:event-type :todo :action :set-filter :filter-mode (get-in request [:body-params :filter])})
    {:status 200
     :session (assoc (:session request) :filter (get-in request [:body-params :filter]))
     :headers {"content-type" "application/json"}}))

(defn toggle-todos
  [todo-db event-bus]
  (fn [request]
    (let [id (-> request (get-in [:query-params "id"]))]
      (if (= "all" id)
        (swap! todo-db (fn [items]
                         (let [all-done? (every? #(:done? %) items)]
                           (prn "all-done" all-done?)
                           (map #(update % :done? (c/constantly (not all-done?))) items))))
        (let [id-int (Integer/parseInt id)]
          (swap! todo-db (fn [items]
                           (map (fn [i]
                                  (if (= id-int (:id i))
                                    (update i :done? not)
                                    i))
                                items)))))
      (async/put! event-bus {:event-type :todo :action :toggle-todos})
      {:status 200
       :headers {"content-type" "application/json"}})))

(defn remove-todo
  [todo-db event-bus]
  (fn [request]
    (let [id (-> request (get-in [:query-params "id"]) Integer/parseInt)]
      (swap! todo-db (fn [items] (remove #(= id (:id %)) items)))
      (async/put! event-bus {:event-type :todo :action :remove-todo})
      {:status 200
       :headers {"content-type" "application/json"}})))

(defn remove-completed
  [todo-db event-bus]
  (fn [_request]
    (swap! todo-db (fn [items] (remove #(:done? %) items)))
    (async/put! event-bus {:event-type :todo :action :remove-completed})
    {:status 200
     :headers {"content-type" "application/json"}}))

(defn add-todo
  [todo-db event-bus]
  (fn [request]
    (let [title (get-in request [:body-params :newTodo])]
      (when (not (clojure.string/blank? title))
        (swap! todo-db (fn [items]
                         (let [last-id (or (-> items last :id) 0)]
                           (conj items {:id (inc last-id) :title title :done? false})))))
      (async/put! event-bus {:event-type :todo :action :add})
      {:status 200
       :headers {"content-type" "application/json"}})))
