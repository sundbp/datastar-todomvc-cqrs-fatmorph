(ns dev
  (:require
   [clj-reload.core :as reload]
   [repl.system :as system]
   [repl.system-helpers :as system-helpers]
   [hyperfiddle.rcf :as rcf]
   [taoensso.telemere :as log]))

#_{:clj-kondo/ignore [:redefined-var]}
(def ^:clj-reload/keep state (atom nil))
#_{:clj-kondo/ignore [:redefined-var]}
(def ^:clj-reload/keep instance (atom :not-started))
#_{:clj-kondo/ignore [:redefined-var]}
(def ^:clj-reload/keep system-stopped (promise))

#_{:clj-kondo/ignore [:redefined-var]}
(defn start
  []
  (swap! instance #(if (or (= :not-started %)
                           (and (future? %) (realized? %)))
                     (future-call (system-helpers/start-system-and-wait-forever
                                   state
                                   system-stopped
                                   (system/system-wrapper (system-helpers/env-config :development "demo-config.edn"))))
                     (throw (ex-info "already running" {}))))
  (rcf/enable!))

#_{:clj-kondo/ignore [:redefined-var]}
(defn stop
  []
  (rcf/enable! false)
  (let [instance-future @instance]
    (when (future? instance-future)
      (when-not (realized? instance-future)
        (future-cancel instance-future))
      (try
        @instance-future
        (catch java.util.concurrent.CancellationException _
          (when (= ::timeout (deref system-stopped (* 60 1000) ::timeout))
            (log/log! {:level :info :msg "Timed out stopping system, exiting anyways.."}))
          ::stopped)))))

(reload/init {:output :verbose})

#_{:clj-kondo/ignore [:redefined-var]}
(defn recompile
  []
  (reload/reload))

#_{:clj-kondo/ignore [:redefined-var]}
(defn reset
  []
  (stop)
  ;; added a short sleep just for better log output (the stopping happens on a different thread)
  (Thread/sleep 300)
  (recompile)
  ;; ensure we dynamically lookup start here as the recompile may have rebound the var
  (let [start-fn (resolve 'dev/start)]
    (start-fn)))

(comment
  (reset))

