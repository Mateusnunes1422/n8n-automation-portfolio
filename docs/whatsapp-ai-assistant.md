# WhatsApp AI Assistant with Human Handoff

[`workflows/clinic-ai-assistant.json`](../workflows/clinic-ai-assistant.json)

## The problem

Small service businesses lose enquiries to response lag. Someone messages at 8pm
or during a busy Saturday, nobody replies for hours, and the lead books with
whoever answered first.

An AI assistant fixes the lag. It also creates a new problem that most builds
ignore: what happens when the bot should stop talking.

## What it does

An inbound WhatsApp message arrives on a webhook. The assistant then:

1. **Filters noise** — ignores its own messages (`fromMe`), group chats
   (`@g.us`), status broadcasts, and anything without text. Without this the
   assistant answers itself and loops.
2. **Loads conversation memory** — the last N messages for that contact, keyed
   by their JID. This is what makes the third message in a thread coherent with
   the first.
3. **Injects current date and time** in the business's timezone. Without an
   explicit timestamp the model confidently proposes appointments in the past.
4. **Classifies and responds** — answers routine questions, captures booking
   details, and emits structured markers the workflow parses out before the
   patient ever sees them.
5. **Escalates when it should.**

## The handoff, which is the interesting part

When the model decides a conversation needs a person — a complaint, real
urgency, an explicit request for a human — it emits a marker at the end of its
reply. The workflow:

- strips the marker so the customer never sees it
- sends a short holding reply
- posts an alert to the team's channel
- **records a pause timestamp for that specific contact**

For the next six hours, that conversation is silent. Messages still arrive, they
still get logged, but the assistant does not reply.

That pause is three lines of code and it is the whole difference between a demo
and something a business can put in front of customers. Without it, the staff
member takes over and the bot keeps answering on top of them — mid-sentence,
contradicting them, in front of the customer.

The pause expires on its own. The team can also release it manually, or release
every paused conversation at once, with a command sent from their own group.

## Reliability

Things that were added because they broke in production, not because they were
designed up front:

- **Model fallback with retry and backoff.** The primary model hit rate limits
  under burst load and returned empty responses. Now: three retries on the
  primary, then two on a fallback model.
- **Send throttling.** Replies go out with a randomised delay and a "typing"
  presence indicator. Instant replies at machine speed are a ban signature on
  unofficial WhatsApp transports.
- **Quiet hours and a daily cap** on outbound reminders.
- **A separate monitor workflow** watching the connection — see
  [self-healing-monitor](self-healing-monitor.md).

## Reminders

A scheduled job runs every five minutes over stored appointments and sends a
reminder 24 hours out and again 3 hours out, each flagged once so it never
double-sends. Past-due entries are discarded with a grace window.

## Stack

n8n (self-hosted, Docker) · LLM API with fallback · WhatsApp API · PostgreSQL ·
Telegram alerting

## Notes on this export

Credentials, tokens, phone numbers, group identifiers and hostnames are
redacted. Conversation state is not included — it never leaves the server.
