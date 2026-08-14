# Self-Healing Integration Monitoring

[`workflows/self-healing-monitor.json`](../workflows/self-healing-monitor.json)

## The problem

Automation fails silently. That is what makes it dangerous.

A workflow that crashes loudly gets fixed in ten minutes. A messaging
integration that quietly drops its connection looks exactly like a quiet
afternoon — no errors, no alerts, no traffic. The business finds out when a
customer mentions that nobody replied to them.

This happened. A connection dropped and stayed down for roughly fifty minutes
before anyone noticed.

## What it does

A scheduled job polls the integration's connection state every ten minutes.

**One bad check is ignored.** Transient failures are normal and paging a human
for them trains them to ignore alerts.

**Two consecutive bad checks trigger an automatic restart.** The monitor tries to
fix it before telling anyone. Most dropped connections come back from a restart.

**Three consecutive bad checks escalate to a human** — but usefully. The alert
carries a link to a page that initiates a fresh connection and renders the
resulting QR code, refreshing itself every 25 seconds. The operator scans it and
the system is back. No SSH, no terminal, no reading logs at 11pm.

**Recovery is announced.** When the connection returns, the monitor says so.
Silence after an alert is indistinguishable from a monitor that also died.

## Why the escalation ladder matters

Most monitoring is a binary: working, or wake someone up. That produces alert
fatigue, which produces ignored alerts, which produces the fifty-minute outage
the monitoring was supposed to prevent.

Three tiers — tolerate, self-heal, escalate with a one-click fix — means a human
is only involved in the cases where a human is genuinely required, and when they
are, the fix takes fifteen seconds.

## The reconnection page

The escalation link points at a webhook that, when opened, requests a new
connection and renders the QR code as HTML with an auto-refresh. It exists
because the alternative — talking a non-technical owner through a terminal —
does not work at 11pm.

## Stack

n8n (self-hosted) · scheduled polling · REST API state checks ·
Telegram alerting · dynamically rendered HTML endpoint
