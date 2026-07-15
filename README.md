# cloud-itonami-isco-3322

Open Occupation Blueprint for **ISCO-08 3322**: Commercial Sales Representatives.

This repository designs a forkable OSS business for an independent sales representation practice: a sample-handling and order-documentation robot manages client orders under a governor-gated actor, so the practice keeps its own order records instead of renting a closed sales-CRM SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a sample-handling and order-documentation robot performs sample staging, order-form printing and physical archival under an actor that proposes
actions and an independent **Sales Representation Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
order submission above the client's registered pricing-authority ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
client account + product catalog + pricing authority
        |
        v
Sales Advisor -> Sales Representation Governor -> submit order/quote, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3322`). Required capabilities:

- :robotics
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
