(ns salesrep.store
  "SSoT for the ISCO-08 3322 independent sales representation practice
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section; README's 'Robotics premise' — a sample-handling and
  order-documentation robot manages client orders under this
  advisor/governor pair, which never dispatches orders itself and never
  submits an order above the client's registered pricing-authority
  ceiling). Modeled on cloud-itonami-isco-3315's valuation.store.

  Domain:

    client  — a registered customer/manufacturer/wholesaler
              (:client-id, :name, :pricing-authority-ceiling, :credit-status)
    order   — a registered order proposal {:order-id :client-id :items
              :total-amount}. `:credit-status` is whether the client has
              passed credit check — submitting an order from an uncredited
              client is incomplete customer due diligence, not proper
              account management.
    record  — a committed operating record (a submitted order or finalized
              account) — written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (order [s order-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-order! [s o])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (order [_ order-id] (get-in @a [:orders order-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-order! [s order]
    (swap! a assoc-in [:orders (:order-id order)] order) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :orders {} :records [] :ledger []}
                                   seed)))))
