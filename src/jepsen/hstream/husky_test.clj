(ns jepsen.hstream.husky-test
  (:require [clojure.tools.logging :refer :all]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [control :as c] [generator :as gen]
             [independent :as independent] [nemesis :as nemesis]
             [tests :as tests] [net :as net]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.hstream.legacy.checker :as local-checker]
            [jepsen.hstream.legacy.client :refer :all]
            [jepsen.hstream.legacy.common :as common]
            [jepsen.hstream.legacy.husky :as husky]
            [jepsen.hstream.common.mvar :refer :all]
            [jepsen.hstream.common.utils :refer :all]
            [jepsen.hstream.legacy.nemesis :as local-nemesis])
  (:import [jepsen.hstream.legacy.common Default-Client]))

(defn hstream-test
  "Given an options map from the command-line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [subscription-results
          (into [] (repeatedly (:fetching-number opts) #(ref [])))
        subscription-ack-timeout 5]
    (merge
      tests/noop-test
      opts
      {:pure-generators true,
       :name "legacy-husky",
       :net net/iptables,
       :plot local-nemesis/plot-spec,
       :db (common/db-empty "0.19.0"),
       :client (common/Default-Client. opts
                                       subscription-results
                                       subscription-ack-timeout),
       :nemesis (local-nemesis/nemesis+),
       :ssh {:dummy? (:dummy opts),
             :private-key-path "/root/.ssh/id_rsa",
             :strict-host-key-checking false},
       :checker (checker/compose {:set (local-checker/set+),
                                  :stat (checker/stats),
                                  :latency (checker/latency-graph),
                                  :rate (checker/rate-graph),
                                  :clock (checker/clock-plot),
                                  :exceptions (checker/unhandled-exceptions),
                                  :timeline (timeline/html)}),
       :generator
         (let [client-gen (husky/husky-generate
                            {:rate (:rate opts),
                             :max-streams (:max-streams opts),
                             :max-write-number (:write-number opts),
                             :max-read-number (:fetching-number opts),
                             :read-wait-time (:fetch-wait-time opts)})]
           (if (:nemesis-on opts)
             (let [nemesis-gen
                     (->> (gen/phases
                            (gen/sleep 15)
                            (gen/mix [(repeat {:type :info, :f :start-slow})
                                      (repeat {:type :info, :f :stop-slow})
                                      (repeat {:type :info, :f :kill-node})
                                      (repeat {:type :info, :f :resume-node})]))
                          (gen/stagger (:nemesis-interval opts))
                          (gen/time-limit (+ (* 10 (:max-streams opts))
                                             (quot (:write-number opts)
                                                   (:rate opts))
                                             (* 2 (:fetch-wait-time opts)))))]
               (gen/clients client-gen nemesis-gen))
             (gen/clients client-gen)))})))

(def cli-opts
  "Additional command line options."
  (concat
    common/cli-opts
    [["-s" "--max-streams INT"
      "The number of HStream streams to be written to in the test." :default 1
      :parse-fn read-string :validate
      [#(and (number? %) (pos? %)) "Must be a positive number"]]
     [nil "--fetching-number INT"
      "The number of fetching operations in total.
      WARNING: its value must be greater than `--max-streams`"
      :default 10 :parse-fn read-string :validate
      [#(and (number? %) (pos? %)) "Must be a positive number"]]
     [nil "--write-number INT" "The number of write operations in total."
      :default 10000 :parse-fn read-string :validate
      [#(and (number? %) (pos? %)) "Must be a positive number"]]]))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn hstream-test,
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
