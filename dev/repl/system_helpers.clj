(ns repl.system-helpers
  (:require
   [aero.core :as aero]
   [exoscale.cloak :as cloak]
   [clojure.java.io :as io]
   [clojure.string]
   [taoensso.telemere :as log]))

(defmethod aero/reader 'secret
  [_ _ value]
  (exoscale.cloak/mask value))

(defn get-system-version
  []
  (or (System/getenv "KAMAL_VERSION") "REPL"))

(defn env-config
  [profile config-path]
  (-> (aero/read-config (or (System/getenv "APP_CONFIG_PATH")
                            (io/resource config-path))
                        {:profile profile})
      (assoc-in [:system :version] (get-system-version))
      (assoc-in [:system :profile] profile)))

(defn log-progress
  [action component-id]
  (log/log! {:level :info :data {:component component-id :action action} :msg (format "%s %s" action component-id)}))

(defmacro ^{:private true} assert-args
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                 (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
       ~(let [more (nnext pairs)]
          (when more
            (list* `assert-args more)))))

(defmacro with-open-state
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (.close name) on each
  name in reverse order."
  [bindings & body]
  (assert-args
   (vector? bindings) "a vector for its binding"
   (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (= (count bindings) 0) `(do
                              (repl.system-helpers/log-progress "starting" "body-using-system")
                              ~@body
                              (repl.system-helpers/log-progress "stopped" "body-using-system"))
    (symbol? (bindings 0)) `(do
                              (repl.system-helpers/log-progress "starting" ~(name (bindings 0)))
                              (let ~(subvec bindings 0 2)
                                (repl.system-helpers/log-progress "started" ~(name (bindings 0)))
                                (try
                                  (with-open-state ~(subvec bindings 2) ~@body)
                                  (finally
                                    (repl.system-helpers/log-progress "stopping" ~(name (bindings 0)))
                                    (. ^java.lang.AutoCloseable ~(bindings 0) ~'close)
                                    (repl.system-helpers/log-progress "stopped" ~(name (bindings 0)))))))
    :else (throw (IllegalArgumentException.
                  "with-open-state only allows Symbols in bindings"))))

(defn closeable
  "Helper fn to wrap anything into a Closable."
  (^java.io.Closeable [value] (closeable value identity))
  (^java.io.Closeable [value close-fn]
   (reify
     clojure.lang.IDeref
     (deref [_] value)
     java.io.Closeable
     (close [_] (close-fn value)))))

(defn with-published-state
  "Apply the fn do-with-state with the system state, and also publish the
  system state to the target-atom."
  [target-atom do-with-state]
  (fn [state]
    (reset! target-atom state)
    (try
      (do-with-state state)
      (finally
        (reset! target-atom nil)))))

(defn wait-for-exit
  [& _]
  (.join (Thread/currentThread)))

(defn start-system-and-wait-forever
  [state-atom system-stopped run-with-system]
  (fn []
    (try
      (run-with-system (with-published-state state-atom wait-for-exit))
      (catch InterruptedException _
        ::done)
      (catch Exception e
        (log/error! {:error e :id ::start-system-and-wait-forever :msg "Caught exception running system!"}))
      (finally
        (deliver system-stopped ::stopped)))))

(defn ensure-dir-exists
  [file-path]
  (-> (io/file file-path)
      (.getParentFile)
      (.mkdirs)))
