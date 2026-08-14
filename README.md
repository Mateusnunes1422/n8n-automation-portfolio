# n8n Automation Portfolio

Production automation systems built with self-hosted n8n — conversational AI
agents, lead pipelines, multi-tenant provisioning, and the monitoring that keeps
them running.

Every workflow here is exported from a system that ran in production, then
sanitized: credentials, tokens, personal data, hostnames and client identifiers
are stripped and replaced with `<REDACTED_*>` markers. Nothing in this repository
is a live secret.

## Workflows

| Workflow | Nodes | What it does |
|---|---|---|
| [multi-tenant-scheduling-saas](workflows/multi-tenant-scheduling-saas.json) | 235 | Booking and management platform serving multiple independent businesses from one deployment. Separate owner, staff and admin interfaces, recurring billing logic, calendar sync. |
| [salon-tenant-provisioning-e2e](workflows/salon-tenant-provisioning-e2e.json) | 241 | End-to-end tenant provisioning — signup clones a template workflow, strips global nodes, applies a vertical preset and injects a configured AI agent. No manual setup. |
| [whatsapp-cloud-compliance](workflows/whatsapp-cloud-compliance.json) | 47 | WhatsApp Cloud API integration with opt-out handling, delivery status tracking and message templating. |
| [lead-prospecting-pipeline](workflows/lead-prospecting-pipeline.json) | 11 | Lead discovery and enrichment from public sources, with deduplication. |
| [lead-capture-google-maps](workflows/lead-capture-google-maps.json) | 18 | Business lead capture via the Places API, normalized into a shared schema. |
| [lead-source-openstreetmap](workflows/lead-source-openstreetmap.json) | 7 | Free lead source built on OpenStreetMap — no per-query API cost. |
| [support-dashboard-ui](workflows/support-dashboard-ui.json) | 5 | Webhook-rendered operations dashboard. |
| [support-dashboard-data](workflows/support-dashboard-data.json) | 6 | Data layer feeding the dashboard. |
| [support-dashboard-actions](workflows/support-dashboard-actions.json) | 7 | Action endpoints the dashboard calls. |
| [self-healing-monitor](workflows/self-healing-monitor.json) | 5 | Polls a live integration, attempts automatic recovery when the connection drops, and escalates to an operator with a self-refreshing reconnection page only when it can't self-heal. |
| [clinic-ai-assistant](workflows/clinic-ai-assistant.json) | 4 | WhatsApp AI assistant: intent classification, per-contact conversation memory, qualification, booking capture, and automatic handoff to a human. |

## Write-ups

Each of these covers the problem, the approach, and the design decisions that
only became obvious after something broke in production.

- **[WhatsApp AI Assistant with Human Handoff](docs/whatsapp-ai-assistant.md)** —
  why the bot has to go *silent* when it escalates, and what happens when it
  doesn't.
- **[Multi-Tenant Scheduling SaaS](docs/multi-tenant-saas.md)** — provisioning a
  complete isolated tenant from a signup, and three things that only fail inside
  AI agent sub-nodes.
- **[Lead Discovery & Enrichment Pipeline](docs/lead-pipeline.md)** — free
  sources that don't violate anyone's terms, and the honest verification rate.
- **[Self-Healing Integration Monitoring](docs/self-healing-monitor.md)** —
  tolerate, self-heal, then escalate with a one-click fix.

## Two design decisions worth reading the code for

**Human handoff with a silence window** — in `clinic-ai-assistant`, when the
assistant detects it should escalate, it alerts the team and then goes quiet on
that specific conversation for a fixed period. Without that, the bot keeps
replying over the staff member who just took over. It is three lines of code and
it is the difference between a demo and something a business can actually put in
front of customers.

**Self-healing before alerting** — in `self-healing-monitor`, a dropped
connection triggers an automatic restart attempt first. A human is only paged
when the automatic recovery fails. Most monitoring wakes someone up for problems
that resolve themselves.

## Stack

n8n (self-hosted, Docker, reverse proxy with automatic HTTPS) · PostgreSQL ·
JavaScript · Python · REST APIs and webhooks · OpenAI / Claude / Gemini ·
WhatsApp (Cloud API and Evolution) · Telegram · Google Calendar

## Importing

These are standard n8n workflow exports. Import via **Workflows → Import from
File**. You will need to supply your own credentials and replace every
`<REDACTED_*>` marker with your own values before anything will run.
