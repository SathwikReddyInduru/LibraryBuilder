// layout-misc-ui.js
// Split out of layout.js (originally lines 2266-2495) for modularity.
// Plan hover tooltip, nav clock, and the tariff-package filter modal.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

(function initPlanHoverTooltip() {
  if (window.location.pathname.includes("/builder/step2")) return;
  // ── Create a single shared tooltip element ──
  const tooltip = document.createElement("div");
  tooltip.id = "planHoverTooltip";
  tooltip.style.cssText = `
        position: fixed;
        z-index: 9999;
        background: #1e293b;
        color: #f1f5f9;
        border: 1px solid #334155;
        border-radius: 8px;
        padding: 8px 14px;
        font-size: 10px;
        line-height: 1.5;
        max-width: 280px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.35);
        pointer-events: none;
        opacity: 0;
        transition: opacity 0.15s ease;
        white-space: pre-wrap;
        word-break: break-word;
    `;
  document.body.appendChild(tooltip);

  // Cache: packageId → response string (avoids duplicate API calls)
  const _tooltipCache = {};
  // Track in-flight requests to avoid duplicates
  const _inFlight = {};

  function showTooltip(text, x, y) {
    tooltip.textContent = text;
    positionTooltip(x, y);
    tooltip.style.opacity = "1";
  }

  function hideTooltip() {
    tooltip.style.opacity = "0";
  }

  function positionTooltip(x, y) {
    const GAP = 12;
    const tw = tooltip.offsetWidth || 280;
    const th = tooltip.offsetHeight || 60;
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    let left = x + GAP;
    let top = y + GAP;

    if (left + tw > vw - 8) left = x - tw - GAP;
    if (top + th > vh - 8) top = y - th - GAP;

    tooltip.style.left = left + "px";
    tooltip.style.top = top + "px";
  }

  // ── Delegate hover on #comp-list plan cards ──
  document.addEventListener("mouseover", function (e) {
    const card = e.target.closest(
      "[data-network-id][data-package-id], [data-networkid][data-packageid]",
    );
    if (!card) return;

    const networkId = card.dataset.networkId || card.dataset.networkid;
    const servicePackageId = card.dataset.packageId || card.dataset.packageid;
    if (!networkId || !servicePackageId) return;

    const cacheKey = networkId + ":" + servicePackageId;

    // If cached, show immediately
    if (_tooltipCache[cacheKey]) {
      showTooltip(_tooltipCache[cacheKey], e.clientX, e.clientY);
      return;
    }

    // Show loading state while fetching
    showTooltip("Loading...", e.clientX, e.clientY);

    // Skip if already fetching
    if (_inFlight[cacheKey]) return;
    _inFlight[cacheKey] = true;

    fetch("/description", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ networkId, servicePackageId }),
    })
      .then((res) => res.json()) // parse JSON first
      .then((data) => {
        console.log("API RESPONSE:", data);
        const description = data.description; // adjust based on backend

        _tooltipCache[cacheKey] = description || "—";

        showTooltip(_tooltipCache[cacheKey], e.clientX, e.clientY);
      })
      .catch(() => {
        _tooltipCache[cacheKey] = "Description unavailable";
      })
      .finally(() => {
        delete _inFlight[cacheKey];
      });
  });

  // ── Follow mouse while inside the card ──
  document.addEventListener("mousemove", function (e) {
    if (tooltip.style.opacity === "1") {
      positionTooltip(e.clientX, e.clientY);
    }
  });

  // ── Hide when leaving the card ──
  document.addEventListener("mouseout", function (e) {
    const card = e.target.closest(
      "[data-network-id][data-package-id], [data-networkid][data-packageid]",
    );
    if (!card) return;
    if (!card.contains(e.relatedTarget)) {
      hideTooltip();
    }
  });
  document.addEventListener("mouseover", function (e) {
    if (
      !e.target.closest(
        "[data-network-id][data-package-id], [data-networkid][data-packageid]",
      )
    ) {
      hideTooltip();
    }
  });
})();

(function initClock() {
  const clockEl = document.getElementById("navClock");
  if (!clockEl) return;

  function tick() {
    clockEl.textContent = new Date().toLocaleString("en-GB", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  }

  tick();
  setInterval(tick, 1000);
})();

/* ═══════════════════════════════════════════════════════════
   CLONE TPS PAGE  —  append to bottom of layout.js
   No overlay, no backdrop — replaces workspace content like admin mode
════════════════════════════════════════════════════════════ */

// ── Filter state ──────────────────────────────────────────
const _tpFilter = { category: null, validity: null, price: null };

function openFilterModal() {
  document.getElementById("tpFilterModal").classList.add("active");
}

function _filterOverlayClick(e) {
  if (e.target === document.getElementById("tpFilterModal")) {
    document.getElementById("tpFilterModal").classList.remove("active");
  }
}

function _tpfChip(btn, group) {
  // toggle within group (single-select per group)
  const row = document.getElementById("tpf-" + group);
  row
    .querySelectorAll(".tpf-chip")
    .forEach((c) => c.classList.remove("selected"));
  const alreadySelected = _tpFilter[group] === btn.dataset.val;
  if (alreadySelected) {
    _tpFilter[group] = null;
  } else {
    btn.classList.add("selected");
    _tpFilter[group] = btn.dataset.val;
  }
  _tpfUpdateFooter();
}

function _tpfUpdateFooter() {
  const anyActive = _tpFilter.category || _tpFilter.validity || _tpFilter.price;
  const showBtn = document.getElementById("tpfShowBtn");
  showBtn.disabled = !anyActive;

  // update badge on Filter button
  const count = [
    _tpFilter.category,
    _tpFilter.validity,
    _tpFilter.price,
  ].filter(Boolean).length;
  const badge = document.getElementById("cloneFilterBadge");
  const filterBtn = document.getElementById("cloneFilterBtn");
  if (count > 0) {
    badge.textContent = count;
    badge.style.display = "inline-flex";
    filterBtn.classList.add("active");
  } else {
    badge.style.display = "none";
    filterBtn.classList.remove("active");
  }
}

function _tpfClearAll() {
  _tpFilter.category = null;
  _tpFilter.validity = null;
  _tpFilter.price = null;
  document
    .querySelectorAll(".tpf-chip")
    .forEach((c) => c.classList.remove("selected"));
  _tpfUpdateFooter();
  _applyTpSearch(document.getElementById("cloneSearchInput")?.value || "");
  document.getElementById("tpFilterModal").classList.remove("active");
}

function _tpfApply() {
  document.getElementById("tpFilterModal").classList.remove("active");
  _applyTpSearch(document.getElementById("cloneSearchInput")?.value || "");
}

// ── Selection state ───────────────────────────────────────
const _tpSelected = new Set();

// ── Open ──────────────────────────────────────────────────
