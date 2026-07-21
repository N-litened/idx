(ns com.wotbrew.idx-test-runner
  "Entry points for running the test suite with a meaningful exit code.

  JVM:         clojure -M:dev:test-clj
  CLJS (node): clojure -M:dev:test-cljs"
  (:require [clojure.test :as t]
            [com.wotbrew.idx-test]))

#?(:cljs
   ;; the cljs.main node repl-env ignores js/process.exitCode (verified: the
   ;; driver process exits 0 regardless), but it does exit 1 when an evaluated
   ;; form throws — so throwing from the :end-run-tests report is the only
   ;; reliable way to make `clojure -M:dev:test-cljs` fail on a failing suite.
   ;; The summary is already printed by the time this report fires.
   (defmethod t/report [:cljs.test/default :end-run-tests] [m]
     (when-not (t/successful? m)
       (throw (ex-info "test suite failed" m)))))

(defn -main [& _]
  #?(:clj  (let [result (t/run-tests 'com.wotbrew.idx-test)]
             (System/exit (if (t/successful? result) 0 1)))
     :cljs (t/run-tests 'com.wotbrew.idx-test)))
