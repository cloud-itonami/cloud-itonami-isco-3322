(ns salesrep.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [salesrep.store :as store]
            [salesrep.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Manufacturer A"
                                :pricing-authority-ceiling 100000
                                :credit-status true})
    (store/register-order! st {:order-id "O-1" :client-id "client-1"
                               :items 5
                               :total-amount 50000})
    st))

(defn- order-op [op confidence order-amount]
  {:op op :effect :propose :order-id "O-1"
   :order-amount order-amount
   :confidence confidence :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-ceiling-and-credited
  (let [st (fresh-store)
        v (governor/check req {} (order-op :create-quote 0.9 50000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceiling-boundary
  (testing "the pricing-authority-ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (order-op :create-quote 0.9 100000) st)]
      (is (:ok? v)))))

(deftest hard-on-order-exceeds-ceiling
  (testing "submitting an order above the client's registered pricing-authority ceiling is unauthorized submission, not routine order"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (order-op :create-quote 0.99 150000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :pricing-authority-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-credit-not-checked
  (testing "submitting an order from an uncredited client is incomplete customer due diligence, not proper account management"
    (let [st (fresh-store)
          _ (store/register-client! st {:client-id "client-uncredited" :name "New Customer"
                                        :pricing-authority-ceiling 50000
                                        :credit-status false})
          v (governor/check {:client-id "client-uncredited"} {} (assoc (order-op :create-quote 0.99 25000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :credit-check-required (:rule %)) (:violations v))))))

(deftest hard-on-unknown-order
  (let [st (fresh-store)
        v (governor/check req {} (assoc (order-op :create-quote 0.9 50000) :order-id "O-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-order (:rule %)) (:violations v)))))

(deftest hard-on-foreign-order
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Manufacturer B"
                                :pricing-authority-ceiling 75000
                                :credit-status true})
    (store/register-order! st {:order-id "O-2" :client-id "client-2"
                               :items 3
                               :total-amount 40000})
    (let [v (governor/check {:client-id "client-2"} {} (assoc (order-op :create-quote 0.9 50000) :order-id "O-1") st)]
      (is (:hard? v))
      (is (some #(= :order-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (order-op :create-quote 0.9 50000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (order-op :create-quote 0.9 50000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-submit-order-even-at-high-confidence
  (testing "order submission always requires human sign-off per Trust Control 1"
    (let [st (fresh-store)
          v (governor/check req {} {:op :submit-order :effect :propose
                                    :order-id "O-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-accept-account-even-at-high-confidence
  (testing "account acceptance always requires human sign-off per Trust Control 2"
    (let [st (fresh-store)
          v (governor/check req {} {:op :accept-account :effect :propose
                                    :order-id "O-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (order-op :accept-account 0.3 50000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
