(ns jepsen.hstream.legacy.common
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer :all]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [generator :as gen] [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.hstream.legacy.client :refer :all]
            [jepsen.hstream.common.mvar :refer :all]
            [jepsen.hstream.legacy.nemesis :as local-nemesis]
            [slingshot.slingshot :refer [throw+ try+]]
            [jepsen.hstream.common.utils :refer :all]))

(defn db-with-streams-initialized
  "HStream DB for a particular version. Here we use the FIRST
   node to create streams for the whole test."
  [version opts streams]
  (reify
    db/DB
      (setup! [_ test node]
        (info node ">>> Setting up DB: HStream" version)
        (when (= node "n1")
          (let [service-url (str "hstream://" node ":6570")
                this-client (get-client-until-ok service-url
                                                 (* 1000 (:grpc-timeout opts)))]
            (dosync (dorun (map #(try+ (create-stream this-client
                                                      %
                                                      (:max-partitions opts))
                                       (catch Object _ nil))
                             streams))))))
      (teardown! [_ _ node]
        (info node ">>> Tearing down DB: HStream" version))))

(defn db-empty
  "HStream DB for a particular version. No extra action is executed after the DB is ready."
  [version]
  (reify
    db/DB
      (setup! [_ _ node] (info node ">>> Setting up DB: HStream" version))
      (teardown! [_ _ node]
        (info node ">>> Tearing down DB: HStream" version))))

(defrecord Default-Client [opts subscription-results subscription-ack-timeout]
  client/Client
    (open! [this test node]
      (let [target-node (if (is-hserver-node? node)
                          node
                          (let [alive-nodes
                                  (local-nemesis/find-hserver-alive-nodes test)]
                            (if (empty? alive-nodes)
                              (throw+ (Exception. "No available node now!"))
                              (rand-nth alive-nodes))))
            service-url (str "hstream://" target-node ":6570")]
        (info "+++++++++ open with" node "(actual" target-node ") +++++++++")
        (let [[got-node got-client] (get-client-start-from-url
                                      service-url
                                      (* 1000 (:grpc-timeout opts)))]
          (when (nil? got-client) (throw+ (Exception. "I got a nil client!")))
          (-> this
              (assoc :client got-client
                     :target-node got-node)))))
    (setup! [_ _] (info "-------- SETTING UP DONE ---------"))
    (invoke! [this _ op]
      (try+
        (case (:f op)
          :add (let [is-done (agent nil)
                     test-data {:key (:value op)}
                     producer (create-producer (:client this)
                                               (:stream op)
                                               (* 1000 (:grpc-timeout opts)))]
                 (send-off
                   is-done
                   (fn [_]
                     (try+ (let [write-future
                                  (if (zero? (:max-partitions opts))
                                    (write-data producer test-data)
                                    (write-data
                                      producer
                                      test-data
                                      ;; partitionKey
                                      (str (mod (+ (:value op)
                                                   (rand-int (:max-partitions
                                                               opts)))
                                                (:max-partitions opts)))))]
                            (.join write-future)
                            {:status :done, :details nil})
                           (catch Object _ {:status :error, :details &throw-context}))))
                 (if (await-for (* 1000 (:write-timeout opts)) is-done)
                   (let [done-result @is-done]
                     (case (:status done-result)
                       :done (assoc op
                               :type :ok
                               :target-node (:target-node this))
                       :error (assoc op
                                :type :fail
                                :error (pprint (:details done-result))
                                :target-node (:target-node this)
                                :extra "happened in send-off")))
                   (assoc op
                     :type :fail
                     :error :unknown-timeout
                     :target-node (:target-node this))))
          :sub (let [test-subscription-id (str "subscription_" (:stream op))]
                 (subscribe (:client this)
                            test-subscription-id
                            (:stream op)
                            subscription-ack-timeout)
                 (assoc op
                   :type :ok
                   :sub-id test-subscription-id
                   :target-node (:target-node this)))
          :create (do (create-stream (:client this)
                                     (:stream op)
                                     (:max-partitions opts))
                      (Thread/sleep (* 1000 5)) ;; Very important: wait for the
                      ;; stream to be ready. Or
                      ;; creating subs will be very
                      ;; slow!
                      (assoc op
                        :type :ok
                        :target-node (:target-node this)))
          :read (let [is-done (agent false)
                      subscription-result (get subscription-results
                                               (:consumer-id op))
                      test-subscription-id (str "subscription_" (:stream op))
                      consumer (consume (:client this)
                                        test-subscription-id
                                        (gen-collect-value-callback
                                          subscription-result))]
                  (send-off is-done
                            (fn [_]
                              (Thread/sleep (* 1000 (:fetch-wait-time opts)))
                              true))
                  (await is-done)
                  (.awaitTerminated (.stopAsync consumer))
                  (assoc op
                    :type :ok
                    :value @subscription-result
                    :target-node (:target-node this))))
        (catch Object _
          (assoc op
            :type :fail
            :error (pprint &throw-context)
            :target-node (:target-node this)
            :extra "happened in op"))))
    (teardown! [_ _] (info "++++++++++++++++ teardown! ++++++++++++++++"))
    (close! [this _]
      (dosync (println ">>> Closing client...") (.close (:client this)))))

(def cli-opts
  "Additional command line options."
  [[nil "--grpc-timeout SECOND" "The timeout of gRPC client." :default 5
    :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-f" "--fetch-wait-time SECOND"
    "The time between starting fetching from the stream and shutting down it."
    :default 15 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--dummy BOOL" "Whether to use dummy ssh connection for local test."
    :default false :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean"]]
   [nil "--write-timeout SECOND" "The max time for a single write operation."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--max-partitions INT"
    "The maximum number of partitions(ordering keys). 0 means use default key only"
    :default 0 :parse-fn read-string :validate
    [#(and (number? %) (>= % 0)) "Must be a non-negative number"]]
   [nil "--nemesis-on [true|false]" "Whether to turn on the nemesis" :default
    true :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean value"]]
   [nil "--nemesis-interval SECOND"
    "The interval between two nemesis operations." :default 15 :parse-fn
    read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]])