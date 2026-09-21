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

  function formatDateShort(isoDate) {
    const d = new Date(`${isoDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
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

  function buildDayView(day) {
    const node = dayTemplate.content.cloneNode(true);
    const wrapper = node.querySelector(".day");
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
      const label = document.createElement("p");
      label.className = "brief-date";
      label.textContent = formatDateLabel(day.date || latest);
      container.appendChild(label);
      container.appendChild(buildDayView(day));
    } catch (err) {
      container.innerHTML = `<p class="error-state">Couldn’t load today’s brief: ${err.message}</p>`;
    }
  }

  async function renderLibrary() {
    const listEl = $("#library-list");
    const detailEl = $("#library-detail");
    try {
      const index = await getIndex();
      const dates = index.dates || [];
      if (dates.length === 0) {
        listEl.innerHTML = '<p class="empty-state">No briefings saved yet.</p>';
        return;
      }
      listEl.innerHTML = "";
      dates.forEach((isoDate, i) => {
        const btn = document.createElement("button");
        btn.className = "library-item" + (i === 0 ? " active" : "");
        btn.type = "button";
        btn.dataset.date = isoDate;
        btn.innerHTML = `${formatDateShort(isoDate)}<span class="sub">${isoDate}</span>`;
        btn.addEventListener("click", () => selectLibraryDay(isoDate));
        listEl.appendChild(btn);
      });
      await selectLibraryDay(dates[0]);
    } catch (err) {
      listEl.innerHTML = `<p class="error-state">Couldn’t load library: ${err.message}</p>`;
    }
  }

  async function selectLibraryDay(isoDate) {
    const listEl = $("#library-list");
    const detailEl = $("#library-detail");
    listEl.querySelectorAll(".library-item").forEach((el) => {
      el.classList.toggle("active", el.dataset.date === isoDate);
    });
    detailEl.innerHTML = '<p class="loading">Loading…</p>';
    try {
      const day = await getDay(isoDate);
      detailEl.innerHTML = "";
      const label = document.createElement("p");
      label.className = "brief-date";
      label.textContent = formatDateLabel(day.date || isoDate);
      detailEl.appendChild(label);
      detailEl.appendChild(buildDayView(day));
    } catch (err) {
      detailEl.innerHTML = `<p class="error-state">Couldn’t load ${isoDate}: ${err.message}</p>`;
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

  function init() {
    initTheme();
    initTabs();
    renderToday();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
