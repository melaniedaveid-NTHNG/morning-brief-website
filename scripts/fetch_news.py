#!/usr/bin/env python3
"""Fetch the latest 3 articles from The Verge, MIT Technology Review, and Wired
and write/merge them into data/<YYYY-MM-DD>.json, then refresh data/index.json.

Standard library only (urllib + xml.etree) so it runs anywhere without pip installs.
"""
import html
import json
import re
import sys
import urllib.request
from datetime import date, datetime
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"

FEEDS = {
    "verge": {"name": "The Verge", "url": "https://www.theverge.com/rss/index.xml"},
    "mit_tech_review": {"name": "MIT Technology Review", "url": "https://www.technologyreview.com/feed/"},
    "wired": {"name": "Wired", "url": "https://www.wired.com/feed/rss"},
}

USER_AGENT = "Mozilla/5.0 (compatible; morning-brief-website/1.0)"


def strip_html(text):
    if not text:
        return ""
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def fetch_feed(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read()


ATOM_NS = {"a": "http://www.w3.org/2005/Atom"}


def _truncate(desc, length=220):
    if len(desc) > length:
        return desc[: length - 3].rsplit(" ", 1)[0] + "..."
    return desc


def parse_items(xml_bytes, limit=3):
    root = ElementTree.fromstring(xml_bytes)

    if root.tag.endswith("}feed") or root.tag == "feed":
        # Atom feed (e.g. The Verge)
        items = []
        for entry in root.findall("a:entry", ATOM_NS)[:limit]:
            title = strip_html(entry.findtext("a:title", default="", namespaces=ATOM_NS))
            link_el = entry.find("a:link[@rel='alternate']", ATOM_NS) or entry.find("a:link", ATOM_NS)
            link = link_el.get("href", "").strip() if link_el is not None else ""
            pub_date = entry.findtext("a:published", default="", namespaces=ATOM_NS) or entry.findtext(
                "a:updated", default="", namespaces=ATOM_NS
            )
            desc = strip_html(
                entry.findtext("a:summary", default="", namespaces=ATOM_NS)
                or entry.findtext("a:content", default="", namespaces=ATOM_NS)
            )
            items.append({
                "title": title,
                "url": link,
                "summary": _truncate(desc),
                "published": pub_date,
            })
        return items

    # RSS 2.0
    items = []
    for item in root.findall("./channel/item")[:limit]:
        title = strip_html(item.findtext("title", default=""))
        link = (item.findtext("link", default="") or "").strip()
        pub_date = item.findtext("pubDate", default="")
        desc = strip_html(item.findtext("description", default=""))
        items.append({
            "title": title,
            "url": link,
            "summary": _truncate(desc),
            "published": pub_date,
        })
    return items


def fetch_source(key, meta):
    try:
        xml_bytes = fetch_feed(meta["url"])
        items = parse_items(xml_bytes, limit=3)
        return items
    except Exception as exc:
        print(f"  warning: failed to fetch {meta['name']}: {exc}", file=sys.stderr)
        return []


def load_day(day_path):
    if day_path.exists():
        return json.loads(day_path.read_text())
    return {}


def main():
    today = date.today().isoformat()
    day_path = DATA_DIR / f"{today}.json"
    day = load_day(day_path)
    day.setdefault("date", today)
    day.setdefault("brief", None)
    news = day.setdefault("news", {})

    for key, meta in FEEDS.items():
        print(f"Fetching {meta['name']}...")
        items = fetch_source(key, meta)
        news[key] = {"source": meta["name"], "items": items}

    day["generated_at"] = datetime.now().astimezone().isoformat(timespec="seconds")

    DATA_DIR.mkdir(exist_ok=True)
    day_path.write_text(json.dumps(day, indent=2) + "\n")
    print(f"Wrote {day_path}")

    rebuild_index()


def rebuild_index():
    dates = sorted(
        (p.stem for p in DATA_DIR.glob("*.json") if re.match(r"^\d{4}-\d{2}-\d{2}$", p.stem)),
        reverse=True,
    )
    index_path = DATA_DIR / "index.json"
    index_path.write_text(json.dumps({"dates": dates}, indent=2) + "\n")
    print(f"Wrote {index_path} ({len(dates)} day(s))")


if __name__ == "__main__":
    main()
