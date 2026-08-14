# Lead Discovery & Enrichment Pipeline

[`workflows/lead-prospecting-pipeline.json`](../workflows/lead-prospecting-pipeline.json)
[`workflows/lead-capture-google-maps.json`](../workflows/lead-capture-google-maps.json)
[`workflows/lead-source-openstreetmap.json`](../workflows/lead-source-openstreetmap.json)
[`workflows/support-dashboard-ui.json`](../workflows/support-dashboard-ui.json)

## The problem

Buying lead lists gets you the same stale contacts everyone else bought.
Scraping directories gets you banned or sued. Paid enrichment APIs charge per
lookup and the free tiers run out in an afternoon.

What actually works is boring: pull from sources that are genuinely public,
normalise everything into one schema, enrich from the company's own website, and
never contact the same organisation twice.

## Sources

**OpenStreetMap (Overpass API)** — free, no key, no per-query cost, no terms
problem. Covers any business category that appears on the map, which is most
physical, local businesses. This is the default source.

**Google Places** — better coverage and richer records for categories OSM misses,
but metered. Used selectively rather than as the primary source.

Both normalise into one shared record shape, so downstream steps do not care
where a lead came from.

## Enrichment

For each organisation:

- fetch the public website
- extract sector, size signals and contact surface with an LLM
- infer likely contact addresses from observed patterns
- validate before anything is written down

The honest number here is 40–60% verified contact rate, not 95%. Any pipeline
claiming better than that from free sources is either paying for data or
guessing. Building around the real figure means the downstream steps handle
misses gracefully instead of pretending they do not happen.

## Deduplication

Every organisation contacted is recorded permanently. A lead that has been
touched before never re-enters the pipeline, regardless of which source
surfaced it. This is unglamorous and it is the single thing that keeps an
outbound system from embarrassing its owner.

## Dashboard

A webhook-rendered dashboard shows collection volume, source breakdown and
pipeline state. Three small workflows: one serves the UI, one serves the data,
one handles actions taken from it. Splitting them keeps the render path fast and
the action path auditable.

## Stack

n8n (self-hosted) · PostgreSQL · Overpass / OpenStreetMap API · Google Places
API · LLM extraction · webhook-rendered HTML
