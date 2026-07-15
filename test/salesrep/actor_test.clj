(ns salesrep.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [salesrep.actor :as actor]
            [salesrep.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Manufacturer A"
                                :pricing-authority-ceiling 100000
                                :credit-status true})
    (store/register-order! st {:order-id "O-1" :client-id "client-1"
                               :items 5
                               :total-amount 50000})
    st))

(deftest commits-a-within-ceiling-credited-quote
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :create-quote :stake :low
                 :order-id "O-1" :order-amount 50000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-above-ceiling-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :create-quote :stake :low
                 :order-id "O-1" :order-amount 150000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-submit-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :submit-order :stake :low
                 :order-id "O-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
