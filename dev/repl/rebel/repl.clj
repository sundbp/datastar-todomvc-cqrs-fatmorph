(ns repl.rebel.repl
  (:require
   rebel-readline.clojure.main
   rebel-readline.core
   [clj-commons.ansi :as ansi]))

(defn start
  [_]
  (rebel-readline.core/ensure-terminal
   (rebel-readline.clojure.main/repl
    :init (fn []
            (try
              (println "[demo] Loading Clojure code, please wait...")
              (require 'dev)
              (in-ns 'dev)
              (require 'repl.rebel.nrepl)
              (println (ansi/compose [:yellow "[demo] Now enter " [:bold.yellow "(reset)"] " to start the dev system."]))

              (catch Exception e
                (if (= (.getMessage e)
                       "Could not locate dev__init.class, dev.clj or dev.cljc on classpath.")
                  (do
                    (println (ansi/compose [:red "[demo] Failed to require dev. Falling back to `user`. "]))
                    (println (ansi/compose [:bold.red "[demo] Make sure to supply any extra required aliases when starting your REPL!"])))

                  (do
                    (.printStackTrace e)
                    (println "[demo] Failed to require dev, this usually means there was a syntax error. See exception above.")
                    (println "[demo] Please correct it, and enter (fixed!) to resume development.")))))))
   ;; When the REPL stops, stop:
   (System/exit 0)))
