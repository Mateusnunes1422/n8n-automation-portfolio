# Multi-Tenant Messaging CRM (Reseller)

[`workflows/disparo-crm-revenda.json`](../workflows/disparo-crm-revenda.json)

## The problem

Selling a WhatsApp campaign service is a different system from running one.
[`whatsapp-cloud-compliance`](../workflows/whatsapp-cloud-compliance.json) sends
for *one* business. A reseller acquires paying clients through paid ads and then
has to keep several of them isolated from each other, on one deployment, while
billing each for what they actually sent.

That adds four things the single-tenant dispatcher never had to solve:

- a **funnel** — ad clicks become leads, leads become paying clients
- **isolation** — one client's contacts, campaigns and opt-outs must never leak
  into another's
- **billing** — a credit balance that debits per delivered message and survives
  a send that fails halfway
- **shared reputation** — every client sends through infrastructure you own, so
  one client's bad list is everyone's problem

The last one is the whole game, and it's the one that kills these businesses.

## What it does

Seven blocks in one workflow, so a reseller can run the whole operation from a
single import.

| Block | Trigger | Purpose |
|---|---|---|
| SETUP | manual, once | Creates the multi-tenant schema |
| FUNIL | `POST /crm-disparo/lead` | Ad landing page posts leads, scored and deduplicated |
| ONBOARDING | `POST /crm-disparo/cliente/ativar` | Lead becomes a client, plan credits posted to the ledger |
| LISTAS | `POST /crm-disparo/lista/importar` | Contact import, **rejected without proof of consent** |
| DISPATCHER | every 10 min | Per-client sending with rate limits and credit debit |
| INBOUND | `POST /crm-disparo/inbound` | Replies, opt-out, complaint escalation |
| PAINEL | `GET /crm-disparo/painel` | Balance and campaign metrics per client |

## Four decisions worth the code

### 1. A list without proof of consent is refused, not flagged

The import endpoint requires four fields alongside the contacts: where consent
was collected, the exact wording the person accepted, the collection date, and
who is signing the declaration. Miss any one and the request returns `422` with
nothing written.

This is not a checkbox. It is the difference between a business and a burn
cycle. Unofficial gateways — the cheap reseller API everyone starts with — get
numbers banned in days when fed scraped lists, and the ban follows the
infrastructure, not the client who uploaded the list. Refusing the list at the
door is cheaper than losing the number.

Contacts already opted out are dropped **at import time**, not at send time, so
a suppressed number never sits in a client's list looking sendable.

### 2. A complaint suppresses the number platform-wide

Normal opt-out (`sair`, `parar`, `cancelar`) is scoped to the client who sent
the message. But when the reply contains `spam`, `denunciar`, `procon` or
`advogado`, the suppression is written with a null `cliente_id` — which the
dispatcher and the importer both read as *global*.

That number stops receiving from every client on the platform, including clients
who have never messaged it and never will. One angry recipient is worth less
than the sending reputation all your clients share.

This is tested: a number escalated to global suppression is blocked from a
brand-new client's import, one that did not exist when the complaint arrived.

### 3. The send record and the credit debit are one statement

```sql
with novo as (insert into disparos ... returning id, cliente_id, status),
     debito as (update clientes set creditos_saldo = creditos_saldo - 1
                  from novo where status = 'sent' returning ...)
insert into creditos_movimentos ...
```

Two separate queries would eventually deliver a message without charging, or
charge without delivering — and with credits as the unit you sell, either one is
a refund conversation. The debit is also gated on `status = 'sent'`: a failed
provider call writes the `disparos` row for the audit trail and takes no credit.

Postgres has a trap here that cost a debugging session. Data-modifying CTEs
cannot see each other's effects **on the same table** — an earlier version of
the list importer did `insert into listas ...` and then `update listas set
total_importado = ...` in the same statement, and the update silently matched
zero rows. Totals are now computed before the insert.

### 4. The provider is per-client, not global

Each client row carries `provider` (`cloud_api` or `gateway`) and a
`provider_config` JSONB. One code node builds the URL, headers and body for
whichever the client is on; the HTTP node just executes what it is handed. A
client can start on a cheap gateway and move to the official Cloud API without a
workflow change — only a row update.

The opt-out footer is injected only on the gateway path. Cloud API templates
carry opt-out in the approved template itself, so appending it there would
duplicate it.

## What is deliberately conservative

- 45 seconds between sends, business-hours window per client, hourly and daily
  caps from the plan
- Campaign auto-pauses at >8% failure, <85% delivery or >1.5% opt-out, measured
  only after 50 sends so early noise doesn't trip it
- Clients start `trial` — activation credits the plan but you gate `ativo` on
  your own payment confirmation
- Every client-facing endpoint checks `x-api-key`, and all client-supplied data
  goes through `$1` query parameters rather than string interpolation

## Verification

The schema and all 14 SQL nodes were run against PostgreSQL 16, and the 9 code
nodes executed in isolation. Covered: lead deduplication, plan crediting,
consent enforcement (each of the four fields rejected independently), global
suppression reaching an unrelated client, credit debit on success, **no** debit
on failure, opt-out removing a contact from the dispatcher queue, both provider
payload shapes, and inbound parsing for Cloud API and gateway including
`fromMe` and group filtering.

## Stack

n8n (self-hosted) · PostgreSQL 16 · WhatsApp Cloud API and gateway providers ·
Meta / Google Ads landing pages

## Notes on this export

Credentials, tokens, phone numbers and hostnames are redacted or read from
environment variables: `CRM_API_KEY`, `GRAPH_API_VERSION`, `WA_PHONE_NUMBER_ID`,
`META_ACCESS_TOKEN`, `GATEWAY_BASE_URL`, `GATEWAY_API_KEY`. Import the workflow
disabled, run SETUP once, and test against your own numbers before activating
the dispatcher.
