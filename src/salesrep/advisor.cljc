(ns salesrep.advisor
  "Sales Advisor — the advisor named in this repository's README,
  proposing an order submission or account acceptance from a customer,
  order details and policy terms.
  Swappable mock/llm; the advisor ONLY proposes — `salesrep.governor`
  checks the pricing authority ceiling and credit check
  independently and always escalates submit-order and accept-account
  decisions. Modeled on cloud-itonami-isco-3315's valuation.advisor.

  A proposal: {:op :submit-order|:accept-account
               :effect :propose :order-id str :order-amount number
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake order-id order-amount] :as request}]
  {:op op
   :effect :propose
   :order-id order-id
   :order-amount (or order-amount 0)
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a sales representative advisor. Given a request, propose an
   :op, the :order-id, :order-amount, an honest :confidence and a :stake.
   Never propose an order above the client's registered pricing-authority
   ceiling — the governor checks it against the registered client record.
   Submit-order and accept-account always require human sign-off
   regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "order request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
