(ns demo.ui.todo
  (:require
   [charred.api :as json]
   [clojure.core.async :as async]
   clojure.string
   [demo.ui.layout :as layout]
   [dev.onionpancakes.chassis.core :as c]
   [dev.onionpancakes.chassis.compiler :as cc]
   [starfederation.datastar.clojure.adapter.http-kit2 :as hk-adapter]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.brotli :as brotli]
   [taoensso.telemere :as log]))

(defn- list-item
  [{:keys [id title done?]}]
  (let [text-styling (if done?
                       "flex-1 text-base line-through opacity-40"
                       "flex-1 text-base")]
    [:li {:class "flex items-center gap-3 px-4 py-3 group hover:bg-base-200/50 transition-colors"
          :id (str "todo-item-" id)}
     [:input {:type "checkbox"
              :class "checkbox checkbox-sm"
              :checked done?
              :data-on:change (format "@patch('/api/toggle-todos?id=%s')" id)}]
     [:span {:class text-styling} title]
     [:button {:class "btn btn-ghost btn-xs btn-square opacity-0 group-hover:opacity-100 transition-opacity"
               :data-on:click (format "@delete('/api/remove-todo?id=%d')" id)}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 text-error" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M6 18L18 6M6 6l12 12"}]]]]))

(defn- todo-list
  [todo-db]
  [:ul {:class "divide-y divide-base-300"
        :id "todo-list"}
   (for [{:keys [done?] :as item} todo-db]
     (list-item item)
     ;; (if (= :all @filter-mode)
     ;;   (list-item item)
     ;;   (when (and (= :completed @filter-mode) done?)
     ;;     (list-item todo-db item)))
     )])

(defn- num-left
  [todo-db]
  (->> todo-db
       (map #(not (:done? %)))
       count))

(defn render-main
  [todo-db]
  (cc/compile
   [:div#main {:data-init "@get('/sse/todo')"
               :class "flex justify-center min-h-screen bg-base-200 p-10"}
    [:div {:class "w-full max-w-2xl"}
     ;; Header
     [:h1 {:class "text-4xl font-bold text-center mb-4 opacity-30"} "TODOs"]
     ;; Card container
     [:div {:class "card bg-base-100 shadow-xl"}
      [:div {:class "card-body p-0"}
       ;; New todo input
       [:div {:class "flex items-center border-b border-base-300 px-4"}
        [:button {:class "btn btn-ghost btn-sm btn-square"
                  :data-on:click "@patch('/api/toggle-todos?id=all')"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 opacity-30" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 9l-7 7-7-7"}]]]
        [:input {:class "input input-ghost w-full focus:outline-none focus:bg-transparent text-lg"
                 :type "text"
                 :placeholder "What needs to be done?"
                 :data-signals "{'newTodo': ''}"
                 :data-bind "newTodo"
                 :data-on:keydown "evt.key === 'Enter' && $newTodo.trim() && @post('/api/add-todo')"}]]
       (todo-list todo-db)
       ;; Footer
       [:div {:class "flex items-center justify-between px-4 py-3 border-t border-base-300 text-sm"}

        ;; Item count
        [:span {:class "opacity-60"
                :id "todo-count"} (str (num-left todo-db) " items left")]

        ;; Filters
        [:div {:class "join"
               :data-signals "{'filter': 'all'}"}
         [:button {:class "btn btn-ghost btn-xs join-item"
                   :data-class "{'btn-active': $filter == 'all'}"
                   :data-on:click "$filter = 'all'; @patch('/api/set-todos-filter')"}
          "All"]
         [:button {:class "btn btn-ghost btn-xs join-item"
                   :data-class "{'btn-active': $filter === 'active'}"
                   :data-on:click "$filter = 'active'; @patch('/api/set-todos-filter')"}
          "Active"]
         [:button {:class "btn btn-ghost btn-xs join-item"
                   :data-class "{'btn-active': $filter === 'completed'}"
                   :data-on:click "$filter = 'completed'; @patch('/api/set-todos-filter')"}
          "Completed"]]

        ;; Clear completed
        [:button {:class "btn btn-ghost btn-xs opacity-60 hover:opacity-100"
                  :data-on:click "@delete('/api/remove-completed-todos')"}
         "Clear completed"]]]]

     ;; Subtle footer info
     [:p {:class "text-center text-xs opacity-30 mt-4"} "datastar, CQRS, fat morph style"]]]))

(defn select-write-profile
  [{:keys [headers]}]
  (let [accepts (get headers "accept-encoding")]
    (cond
      (clojure.string/includes? accepts "br")   (brotli/->brotli-profile)
      (clojure.string/includes? accepts "gzip") hk-adapter/gzip-profile
      :else                                     hk-adapter/basic-profile)))

(defn- filter-todos
  [mode todos]
  (filter (fn [{:keys [done?]}]
            (case mode
              "completed" done?
              "active"    (not done?)
              true))
          todos))

(defn sse
  [todo-db by-type-publication]
  (fn [request]
    (let [todo-events-chan (async/chan 10)
          session-id (get-in request [:session :uid])]
      (hk-adapter/->sse-response
       request
       {hk-adapter/write-profile (select-write-profile request)

        hk-adapter/on-open
        (fn [sse-gen]
          (log/with-ctx [:demo/session-id session-id]
            ;; Send initial render immediately so client doesn't wait - check if this makes sense?
            (d*/patch-elements! sse-gen (c/html
                                         (render-main @todo-db)))
            ;; subscribe to todo events
            (async/sub by-type-publication :todo todo-events-chan)

            ;; async listen for events and re-render as needed
            (async/go-loop []
              (when-let [{:keys [action filter-mode] :as todo-event} (async/<! todo-events-chan)]
                (log/log! {:level :info :id ::send-sse-event :data {:todo-event todo-event}})
              ;; Execute the Datastar patch operation utilizing the Fat Morph strategy
                (d*/patch-elements! sse-gen (c/html (render-main (filter-todos (or filter-mode "all")
                                                                               @todo-db))))
                (when (= :add action)
                  (d*/patch-signals! sse-gen (json/write-json-str {:newTodo ""})))
                (recur)))))

        hk-adapter/on-close
        (fn [_sse-gen status]
          (log/with-ctx [:cosmic/session-id session-id]
            (log/log! {:level :info :id ::sse-closing :data {:status status}})
            (async/unsub by-type-publication :todo todo-events-chan)
            (async/close! todo-events-chan)))}))))

(defn page
  [todo-db]
  (fn [_request]
    {:status 200
     :headers {"content-type" "text/html"}
     :body
     (c/html
      (layout/document-layout
       "Demo of datastar, CQRS and fat morph flow"
       (render-main @todo-db)))}))
