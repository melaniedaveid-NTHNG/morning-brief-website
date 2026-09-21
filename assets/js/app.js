(() => {
  "use strict";

  const NEWS_ORDER = ["verge", "mit_tech_review", "wired"];
  const dayCache = new Map();

  const $ = (sel, root = document) => root.querySelector(sel);

  const dayTemplate = $("#day-template");
  const sourceTemplate = $("#news-source-template");
  const itemTemplate = $("#news-item-template");

  function formatDateLabel(isoDate) {
    const d = new Date(`${isoDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric", year: "numeric" });
  }

  function formatPublished(pubDateStr) {
    if (!pubDateStr) return "";
    const d = new Date(pubDateStr);
    if (Number.isNaN(d.getTime())) return "";
    return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
  }

  async function fetchJSON(path) {
    const res = await fetch(path, { cache: "no-store" });
    if (!res.ok) throw new Error(`Failed to load ${path} (${res.status})`);
    return res.json();
  }

  async function getIndex() {
    return fetchJSON("data/index.json");
  }

  async function getDay(isoDate) {
    if (dayCache.has(isoDate)) return dayCache.get(isoDate);
    const data = await fetchJSON(`data/${isoDate}.json`);
    dayCache.set(isoDate, data);
    return data;
  }

  function renderBrief(container, brief) {
    container.innerHTML = "";
    if (!brief) {
      const p = document.createElement("p");
      p.className = "brief-empty";
      p.textContent = "No personal brief for this day yet — calendar and inbox highlights will appear here once automation is connected.";
      container.appendChild(p);
      return;
    }

    const sections = [
      ["Calendar", brief.calendar],
      ["Needs attention", brief.attention],
      ["Unread", brief.unread],
    ];

    let any = false;
    for (const [label, list] of sections) {
      if (!Array.isArray(list) || list.length === 0) continue;
      any = true;
      const h = document.createElement("h3");
      h.className = "brief-section-title";
      h.textContent = label;
      container.appendChild(h);

      const ul = document.createElement("ul");
      ul.className = "brief-list";
      for (const entry of list) {
        const li = document.createElement("li");
        li.textContent = entry;
        ul.appendChild(li);
      }
      container.appendChild(ul);
    }

    if (brief.summary) {
      any = true;
      const p = document.createElement("p");
      p.className = "brief-empty";
      p.style.marginTop = "0";
      p.textContent = brief.summary;
      container.prepend(p);
    }

    if (!any) {
      const p = document.createElement("p");
      p.className = "brief-empty";
      p.textContent = "Nothing flagged for today.";
      container.appendChild(p);
    }
  }

  function renderNews(container, news) {
    container.innerHTML = "";
    if (!news) return;

    for (const key of NEWS_ORDER) {
      const entry = news[key];
      if (!entry) continue;

      const node = sourceTemplate.content.cloneNode(true);
      node.querySelector(".news-source-title").textContent = entry.source || key;
      const ul = node.querySelector(".news-items");

      const items = Array.isArray(entry.items) ? entry.items.slice(0, 3) : [];
      if (items.length === 0) {
        const li = document.createElement("li");
        li.className = "news-source-empty";
        li.textContent = "No items fetched.";
        ul.appendChild(li);
      } else {
        for (const item of items) {
          const itemNode = itemTemplate.content.cloneNode(true);
          const a = itemNode.querySelector(".news-item-title");
          a.textContent = item.title || "Untitled";
          a.href = item.url || "#";
          const summary = itemNode.querySelector(".news-item-summary");
          if (item.summary) {
            summary.textContent = item.summary;
          } else {
            summary.remove();
          }
          const meta = itemNode.querySelector(".news-item-meta");
          const when = formatPublished(item.published);
          if (when) {
            meta.textContent = when;
          } else {
            meta.remove();
          }
          ul.appendChild(itemNode);
        }
      }
      container.appendChild(node);
    }
  }

  function buildDayView(day, isoDate) {
    const node = dayTemplate.content.cloneNode(true);
    const wrapper = node.querySelector(".day");
    wrapper.querySelector('[data-slot="date"]').textContent = formatDateLabel(day.date || isoDate);
    renderBrief(wrapper.querySelector('[data-slot="brief"]'), day.brief);
    renderNews(wrapper.querySelector('[data-slot="news"]'), day.news);
    return wrapper;
  }

  async function renderToday() {
    const container = $("#today-content");
    try {
      const index = await getIndex();
      if (!index.dates || index.dates.length === 0) {
        container.innerHTML = '<p class="empty-state">No briefings yet. Run the fetch script to generate today’s data.</p>';
        return;
      }
      const latest = index.dates[0];
      const day = await getDay(latest);
      container.innerHTML = "";
      container.appendChild(buildDayView(day, latest));
    } catch (err) {
      container.innerHTML = `<p class="error-state">Couldn’t load today’s brief: ${err.message}</p>`;
    }
  }

  let libraryLoaded = false;

  async function renderLibrary() {
    if (libraryLoaded) return;
    const feedEl = $("#library-feed");
    try {
      const index = await getIndex();
      const dates = index.dates || [];
      if (dates.length === 0) {
        feedEl.innerHTML = '<p class="empty-state">No briefings saved yet.</p>';
        return;
      }
      const days = await Promise.all(dates.map((isoDate) => getDay(isoDate)));
      feedEl.innerHTML = "";
      dates.forEach((isoDate, i) => {
        feedEl.appendChild(buildDayView(days[i], isoDate));
      });
      libraryLoaded = true;
    } catch (err) {
      feedEl.innerHTML = `<p class="error-state">Couldn’t load library: ${err.message}</p>`;
    }
  }

  function setView(view) {
    document.querySelectorAll(".tab").forEach((tab) => {
      const active = tab.dataset.view === view;
      tab.classList.toggle("active", active);
      tab.setAttribute("aria-selected", String(active));
    });
    document.querySelectorAll(".view").forEach((panel) => {
      panel.hidden = panel.dataset.viewPanel !== view;
    });
    if (view === "library") {
      renderLibrary();
    }
  }

  function currentTheme() {
    return document.documentElement.getAttribute("data-theme") ||
      (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  }

  function updateThemeLabel() {
    const label = $("#theme-toggle-label");
    if (label) label.textContent = currentTheme() === "dark" ? "Light" : "Dark";
  }

  function initTheme() {
    const stored = (() => {
      try { return localStorage.getItem("mb-theme"); } catch { return null; }
    })();
    if (stored === "light" || stored === "dark") {
      document.documentElement.setAttribute("data-theme", stored);
    }
    updateThemeLabel();
    $("#theme-toggle").addEventListener("click", () => {
      const next = currentTheme() === "dark" ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", next);
      try { localStorage.setItem("mb-theme", next); } catch { /* ignore */ }
      updateThemeLabel();
    });
  }

  function initTabs() {
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.addEventListener("click", () => setView(tab.dataset.view));
    });
  }

  const WEATHER_CODES = {
    0: "Clear", 1: "Mostly clear", 2: "Partly cloudy", 3: "Overcast",
    45: "Fog", 48: "Fog",
    51: "Light drizzle", 53: "Drizzle", 55: "Heavy drizzle",
    61: "Light rain", 63: "Rain", 65: "Heavy rain",
    66: "Freezing rain", 67: "Freezing rain",
    71: "Light snow", 73: "Snow", 75: "Heavy snow", 77: "Snow grains",
    80: "Rain showers", 81: "Rain showers", 82: "Heavy showers",
    85: "Snow showers", 86: "Snow showers",
    95: "Thunderstorm", 96: "Thunderstorm", 99: "Thunderstorm",
  };

  async function renderWeather() {
    const el = $("#weather-chip");
    if (!el) return;
    try {
      const res = await fetch(
        "https://api.open-meteo.com/v1/forecast?latitude=51.5074&longitude=-0.1278&current=temperature_2m,weather_code&timezone=Europe%2FLondon",
        { cache: "no-store" }
      );
      if (!res.ok) throw new Error(`weather ${res.status}`);
      const data = await res.json();
      const temp = Math.round(data.current?.temperature_2m);
      const desc = WEATHER_CODES[data.current?.weather_code] || "";
      if (Number.isFinite(temp)) {
        el.textContent = `London ${temp}°C${desc ? " · " + desc : ""}`;
      }
    } catch (err) {
      el.remove();
    }
  }

  function init() {
    initTheme();
    initTabs();
    renderWeather();
    renderToday();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
