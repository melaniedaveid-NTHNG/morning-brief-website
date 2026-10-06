# Morning Brief

A small mobile-first site that shows today's brief plus the latest 3 articles from
**The Verge**, **MIT Technology Review**, and **Wired** — and keeps every past day
around as a browsable library. The header shows a small live London weather
reading (via the free [Open-Meteo](https://open-meteo.com/) API, no key needed).

## Structure

- `index.html`, `assets/` — the static site (no build step, no frameworks)
- `data/YYYY-MM-DD.json` — one file per day: personal brief + news
- `data/index.json` — list of available dates, newest first (rebuilt automatically)
- `scripts/fetch_news.py` — pulls the latest 3 items from each outlet's RSS/Atom
  feed and writes/updates today's `data/YYYY-MM-DD.json`

## Running locally

```bash
python3 scripts/fetch_news.py   # fetch today's news into data/
python3 -m http.server 8934     # serve the site
```

Then open `http://localhost:8934`.

## Day JSON shape

```json
{
  "date": "2026-09-21",
  "brief": {
    "summary": "optional one-liner",
    "calendar": ["9am standup", "2pm 1:1 with Sam"],
    "attention": ["Reply to invoice from Acme"],
    "unread": ["3 unread in #design"]
  },
  "news": {
    "verge": { "source": "The Verge", "items": [{ "title": "...", "url": "...", "summary": "...", "published": "..." }] },
    "mit_tech_review": { "source": "MIT Technology Review", "items": [...] },
    "wired": { "source": "Wired", "items": [...] }
  }
}
```

`brief` can be `null` — the site shows a placeholder until it's populated. Any
missing news source or empty `items` list renders gracefully too.

## Adding a new day

Run `scripts/fetch_news.py` any day you want a news snapshot — it merges into
that day's file and refreshes `data/index.json`. To add personal brief content
(calendar/attention/unread), edit the day's JSON directly, or wire it up via a
scheduled task later.

## Automating it

Not set up yet. When ready: a daily scheduled Claude task can gather the
morning brief + fetch news (via this script or by fetching the feeds directly),
write the day's JSON into `data/`, and commit/push so GitHub Pages picks it up.

## Agent OS (Nothing Phone 3)

`nothing-os/` contains a separate project: a voice-first home-screen app for the Nothing Phone (3)
plus a browser prototype of it. See [`nothing-os/README.md`](nothing-os/README.md).
