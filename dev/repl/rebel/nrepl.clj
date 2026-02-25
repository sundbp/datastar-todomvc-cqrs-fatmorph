(ns ^{:clojure.tools.namespace.repl/load false} cosmic.rebel.nrepl
  (:require
   [clojure.java.io :as io]
   clojure.string
   cider.nrepl
   [clj-commons.ansi :as ansi]
   nrepl.server
   [refactor-nrepl.middleware :as refactor.nrepl]))


(defn start-nrepl
  [opts]
  (let [middlewares (conj (map #'cider.nrepl/resolve-or-fail cider.nrepl/cider-middleware)
                          #'refactor.nrepl/wrap-refactor)
        middlewares (try
                      (require 'vlaaad.reveal.nrepl)
                      (conj middlewares (ns-resolve *ns* 'vlaaad.reveal.nrepl/middleware))
                      (catch Exception _
                        middlewares))
        server
        (nrepl.server/start-server
         :port (:port opts)
         :handler (apply nrepl.server/default-handler middlewares))]
    (spit ".nrepl-port" (:port server))
    (println (ansi/compose [:yellow (str "[cosmic] nREPL client can be connected to port " (:port server))]))
    server))

(defn nrepl-port-from-file
  []
  (let [default-port-file (io/file (System/getProperty "user.dir") ".default-nrepl-port")]
    (when (.exists default-port-file)
      (some-> (slurp default-port-file) clojure.string/trim-newline Integer/parseInt))))

(def port (or (some-> (System/getenv "NREPL_PORT") Integer/parseInt)
              (nrepl-port-from-file)
              5600))

(println "[cosmic] Starting nREPL server on port" port)

(def server (start-nrepl {:port port}))
