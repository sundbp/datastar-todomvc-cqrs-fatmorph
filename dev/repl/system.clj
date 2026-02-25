(ns repl.system
  (:require
   [clojure.core.async :as async]
   [demo.http-server :as d.http-server]
   [demo.routes :as d.routes]
   [repl.system-helpers :as system-helpers]))

(defn system-wrapper
  [config]
  (fn [do-with-state]
    (system-helpers/with-open-state [todo-db (system-helpers/closeable (atom []))
                                     event-bus (system-helpers/closeable (async/chan 1024)
                                                                         async/close!)
                                     by-type-publication (system-helpers/closeable (async/pub @event-bus :event-type))
                                     routes (system-helpers/closeable (d.routes/routes {:todo-db @todo-db
                                                                                        :event-bus @event-bus
                                                                                        :by-type-publication @by-type-publication}))
                                     http-server (system-helpers/closeable (d.http-server/start (assoc (select-keys config [:http-server :profile])
                                                                                                       :routes @routes
                                                                                                       :not-found-body "NOT FOUND!"))
                                                                           d.http-server/stop)]
      (do-with-state {:config      config
                      :http-server @http-server
                      :todo-db     @todo-db
                      :event-bus   @event-bus}))))
