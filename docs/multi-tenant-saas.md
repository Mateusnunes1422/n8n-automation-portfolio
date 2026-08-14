# Multi-Tenant Scheduling SaaS with Automated Provisioning

[`workflows/multi-tenant-scheduling-saas.json`](../workflows/multi-tenant-scheduling-saas.json) — 235 nodes
[`workflows/salon-tenant-provisioning-e2e.json`](../workflows/salon-tenant-provisioning-e2e.json) — 241 nodes

## The problem

Selling an automation to one business is a project. Selling it to fifty is a
product — and the difference is entirely in what happens at signup.

Manual setup per client does not scale past about five clients. Somebody has to
clone workflows, wire up a messaging instance, configure a calendar, seed the
service catalogue, and not make a mistake. It takes hours and it breaks quietly.

## What it does

A signup provisions a complete, isolated tenant with no human involvement:

1. Clones a template workflow
2. Strips nodes that belong to the platform rather than the tenant
3. Applies a vertical preset — the service catalogue, hours and copy differ for a
   barbershop, a salon and a clinic
4. Injects a configured AI agent with the tenant's own credentials wired in
5. Registers the tenant's messaging instance
6. Publishes the tenant's endpoints

The template is 235 nodes. A provisioned tenant is 231 — the difference being
the platform-level nodes that get removed on the way through.

## Three interfaces, three audiences

- **Business owner** — bookings, staff, services, pricing, financials
- **Staff member** — their own schedule only
- **Platform admin** — tenant management, infrastructure, provisioning status

Same deployment, different surface. Attempting to serve all three from one
interface is the mistake that makes small-business software unusable.

## Things learned the hard way

**Node references break inside sub-nodes.** Expressions that resolve a value from
a named node work in a normal node and silently fail inside a tool attached to an
AI agent. The fix is to plumb the value into the agent's input instead of
resolving it from the tool.

**Tool parameter syntax is version-dependent.** The HTTP request tool exposed to
an agent uses placeholder definitions, not the newer inline-argument syntax.
Reading the actual node schema rather than the documentation for the current
version was what unblocked live booking through conversation.

**Webhook registration differs by method.** Cloning a workflow through the API
registered POST webhooks and skipped GET ones. Worth knowing before you debug the
wrong layer for an afternoon.

## No-show module

Appointment no-shows cost appointment-driven businesses 15–30% of capacity. A
separate module keeps its own appointment store, decoupled from any external
calendar, and fires confirmation prompts at 48 hours and 2 hours out over
WhatsApp. Decoupling it from the calendar was deliberate: it removed the single
hardest part of provisioning a new tenant.

## Stack

n8n (self-hosted, Docker) · PostgreSQL · REST APIs · WhatsApp API ·
Google Calendar · LLM agent with tool calling
