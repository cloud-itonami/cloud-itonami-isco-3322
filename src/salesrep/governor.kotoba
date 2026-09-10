(ns salesrep.governor
  "SalesRepresentationGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every order submission and account acceptance an advisor may
  propose for a customer. The governor never dispatches orders itself
  and never submits an order above the client's registered
  pricing-authority ceiling. Modeled on
  cloud-itonami-isco-3315's valuation.governor.
  Task twist: an order amount is an arithmetic ceiling
  against the client's registered pricing-authority ceiling, and an
  order cannot be submitted until the client has passed credit check.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance      — the customer/manufacturer/wholesaler
                                must be registered.
    2. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches orders and
                                never submits an order above the
                                registered pricing-authority ceiling;
                                it only gates what the advisor may
                                publish).
    3. order basis            — an order proposal must cite a
                                REGISTERED order belonging to this
                                client.
    4. order-wrong-client     — an order must belong to the request's
                                client.
    5. pricing-authority-exceeded — the order amount must not
                                exceed the client's registered
                                pricing-authority ceiling (submitting
                                beyond the client's registered ceiling
                                is unauthorized submission, not routine
                                order service).
    6. credit-check-required  — the client must have passed credit
                                check before any order can be submitted
                                or account can be accepted (submitting
                                without customer credit evidence is
                                incomplete customer due diligence, not
                                proper account management).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    7. :op :submit-order (order submission always requires human
                         sign-off per Trust Control 1).
    8. :op :accept-account (account acceptance always requires human
                           sign-off per Trust Control 2).
    9. low confidence (< `confidence-floor`)."
  (:require [salesrep.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:submit-order :accept-account})
(def ^:private normal-ops #{:create-quote})

(defn- hard-violations [{:keys [request proposal]} client-record order]
  (let [{:keys [op order-amount]} proposal]
    (cond-> []
            (nil? client-record)
            (conj {:rule :no-client :detail "未登録 client"})

            (not= :propose (:effect proposal))
            (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録権限上限超過の発行を直接実行しない）"})

            (nil? order)
            (conj {:rule :unknown-order :detail "未登録 order への提案は不可"})

            (and order (not= (:client-id order) (:client-id request)))
            (conj {:rule :order-wrong-client :detail "order が別 client のもの"})

            (and client-record order (number? order-amount)
                 (> order-amount (:pricing-authority-ceiling client-record)))
            (conj {:rule :pricing-authority-exceeded
                   :detail (str "注文金額 " order-amount " > 登録済み上限 "
                                (:pricing-authority-ceiling client-record) "（登録上限を超える提出は無許可提出であって通常の注文業務ではない）")})

            (and client-record (not (:credit-status client-record)))
            (conj {:rule :credit-check-required
                   :detail "信用調査未完了の顧客への注文提出は不完全な顧客デューデリジェンスであって適切なアカウント管理ではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `salesrep.store/Store`. Pure — never
  mutates the store, never submits an order above the registered
  pricing-authority ceiling."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        order (some->> (:order-id proposal) (store/order store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record order)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
