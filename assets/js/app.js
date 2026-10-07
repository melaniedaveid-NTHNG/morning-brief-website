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

  function formatDateListItem(isoDate) {
    const d = new Date(`${isoDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric", year: "numeric" });
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
    const listEl = $("#library-days");
    try {
      const index = await getIndex();
      const dates = index.dates || [];
      if (dates.length === 0) {
        listEl.innerHTML = '<li class="empty-state">No briefings saved yet.</li>';
        return;
      }
      listEl.innerHTML = "";
      dates.forEach((isoDate, i) => {
        const li = document.createElement("li");
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "library-day" + (i === 0 ? " active" : "");
        btn.dataset.date = isoDate;
        btn.textContent = formatDateListItem(isoDate);
        btn.addEventListener("click", () => selectLibraryDay(isoDate));
        li.appendChild(btn);
        listEl.appendChild(li);
      });
      libraryLoaded = true;
    } catch (err) {
      listEl.innerHTML = `<li class="error-state">Couldn’t load library: ${err.message}</li>`;
    }
  }

  async function selectLibraryDay(isoDate) {
    const listEl = $("#library-days");
    const detailEl = $("#library-detail");
    listEl.hidden = true;
    $("#actions-label").textContent = formatDateListItem(isoDate);
    listEl.querySelectorAll(".library-day").forEach((el) => {
      el.classList.toggle("active", el.dataset.date === isoDate);
    });
    detailEl.innerHTML = '<p class="loading">Loading…</p>';
    try {
      const day = await getDay(isoDate);
      detailEl.innerHTML = "";
      detailEl.appendChild(buildDayView(day, isoDate));
    } catch (err) {
      detailEl.innerHTML = `<p class="error-state">Couldn’t load ${isoDate}: ${err.message}</p>`;
    }
  }

  const VIEW_LABELS = { today: "Today\u2019s brief", library: "Library" };

  function setView(view) {
    document.querySelectorAll(".actions [data-view]").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.view === view);
    });
    document.querySelectorAll(".view").forEach((panel) => {
      panel.hidden = panel.dataset.viewPanel !== view;
    });
    $("#actions-label").textContent = VIEW_LABELS[view] || "";
    $("#main").scrollTop = 0;
    if (view === "library") {
      // Always land on the list of days; picking one swaps it for that day.
      $("#library-days").hidden = false;
      $("#library-detail").innerHTML = "";
      renderLibrary();
    }
  }

  function initActions() {
    document.querySelectorAll(".actions [data-view]").forEach((btn) => {
      btn.addEventListener("click", () => setView(btn.dataset.view));
    });
  }

  function updateClock() {
    const el = $("#clock");
    const now = new Date();
    const hh = String(now.getHours()).padStart(2, "0");
    const mm = String(now.getMinutes()).padStart(2, "0");
    el.textContent = `${hh}:${mm}`;
    el.dateTime = now.toISOString();
  }

  function initClock() {
    updateClock();
    setInterval(updateClock, 1000);
  }

  // Voice commands: "today", "library", "radio" / "stop".
  function initVoice() {
    const btn = $("#voice-toggle");
    const hint = $("#voice-hint");
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) {
      btn.disabled = true;
      btn.title = "Voice not supported in this browser";
      return;
    }

    const showHint = (text) => {
      hint.textContent = text;
      hint.hidden = !text;
    };

    const rec = new Recognition();
    rec.lang = "en-GB";
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    let listening = false;

    rec.addEventListener("start", () => {
      listening = true;
      btn.setAttribute("aria-pressed", "true");
      showHint("Listening\u2026 try \u201ctoday\u201d, \u201clibrary\u201d or \u201cradio\u201d");
    });
    rec.addEventListener("end", () => {
      listening = false;
      btn.setAttribute("aria-pressed", "false");
      setTimeout(() => { if (!listening) showHint(""); }, 1800);
    });
    rec.addEventListener("error", () => showHint("Didn\u2019t catch that."));
    rec.addEventListener("result", (event) => {
      const said = (event.results[0]?.[0]?.transcript || "").toLowerCase();
      showHint(`\u201c${said}\u201d`);
      const audio = $("#radio-audio");
      if (said.includes("library") || said.includes("past")) {
        setView("library");
      } else if (said.includes("today") || said.includes("brief") || said.includes("news")) {
        setView("today");
      } else if (said.includes("stop") || said.includes("pause")) {
        if (!audio.paused) $("#radio-toggle").click();
      } else if (said.includes("radio") || said.includes("music")) {
        if (audio.paused) $("#radio-toggle").click();
      }
    });

    btn.addEventListener("click", () => {
      if (listening) rec.stop();
      else rec.start();
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

  const RADIO_STREAM_URL = "https://dispatcher.rndfnk.com/rbb/radioeins/live/mp3/mid";

  function initRadio() {
    const btn = $("#radio-toggle");
    const audio = $("#radio-audio");
    if (!btn || !audio) return;

    const setState = (playing, label) => {
      btn.setAttribute("aria-pressed", String(playing));
      btn.setAttribute("aria-label", label);
      btn.title = label;
    };

    const fail = () => {
      setState(false, "radioeins unavailable");
      setTimeout(() => setState(false, "Play radioeins livestream"), 3000);
    };

    audio.addEventListener("waiting", () => btn.setAttribute("aria-label", "Loading radioeins\u2026"));
    audio.addEventListener("playing", () => setState(true, "Stop radioeins"));
    audio.addEventListener("pause", () => setState(false, "Play radioeins livestream"));
    audio.addEventListener("error", fail);

    btn.addEventListener("click", () => {
      if (audio.paused) {
        if (!audio.src) audio.src = RADIO_STREAM_URL;
        audio.play().catch(fail);
      } else {
        audio.pause();
      }
    });
  }

  const LEAVE_HOUR = 8;
  const LEAVE_MINUTE = 50;
  const LEAVE_GRACE_MS = 10 * 60 * 1000;

  function nextLeaveTarget(now) {
    const target = new Date(now);
    target.setHours(LEAVE_HOUR, LEAVE_MINUTE, 0, 0);
    if (now.getTime() > target.getTime() + LEAVE_GRACE_MS) {
      target.setDate(target.getDate() + 1);
    }
    return target;
  }

  function updateCountdown() {
    const el = $("#countdown-chip");
    if (!el) return;
    const now = new Date();
    const target = nextLeaveTarget(now);
    const diffMs = target.getTime() - now.getTime();

    if (diffMs <= 0) {
      el.textContent = "Leave now — 8:50 departure";
      return;
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    const s = totalSeconds % 60;

    let text;
    if (h > 0) {
      text = `Leave in ${h}h ${m}m`;
    } else if (m > 0) {
      text = `Leave in ${m}m ${s}s`;
    } else {
      text = `Leave in ${s}s`;
    }
    el.textContent = `${text} · 8:50`;
  }

  function initCountdown() {
    if (!$("#countdown-chip")) return;
    updateCountdown();
    setInterval(updateCountdown, 1000);
  }

  function init() {
    initClock();
    initActions();
    initRadio();
    initVoice();
    initCountdown();
    renderWeather();
    renderToday();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
