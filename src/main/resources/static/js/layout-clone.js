// layout-clone.js
// Split out of layout.js (originally lines 2496-3545) for modularity.
// Clone workflow: clone page open/close, OTT service metadata and plan card rendering/search, and the clone hierarchy tree.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function openClone() {
  const page = document.getElementById("clonePage");
  const workBody = document.getElementById("leftPane")?.parentElement; // .workspace-body
  const headerPill = document.querySelector(".header-pill-bar");
  const stepRail = document.getElementById("stepRail");
  const sidebar = document.getElementById("sidebar");

  if (!page) return;

  // Force-close ATP Rules page if it's the one currently showing —
  // otherwise it stays stacked on top (same z-index, later in DOM)
  // and the clone page opens invisibly underneath it.
  const atpPage = document.getElementById("atpRulesPage");
  if (atpPage) {
    atpPage.classList.remove("visible");
    atpPage.style.display = "none";
  }

  // Same deal for the ATP Rate page.
  const atpRatePage = document.getElementById("atpRatePage");
  if (atpRatePage) {
    atpRatePage.classList.remove("visible");
    atpRatePage.style.display = "none";
  }

  // 1. Hide the normal workspace content (like admin mode does)
  if (workBody) workBody.style.display = "none";
  if (headerPill) headerPill.style.display = "none";

  // 2. Collapse step-rail & sidebar (clone page doesn't need them)
  //    setModuleUI handles nav highlight + rail/sidebar state
  setModuleUI("clone");

  // 3. Show the clone page container (flex, then animate in)
  _tpSelected.clear();
  _tpfClearAll(); // reset filters on every open
  page.style.display = "flex";

  // Hide bottom rail nodes — clone page has no context for them
  ["mn-approved", "mn-rejected", "mn-saved", "mn-drafts"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.style.display = "none";
  });

  // Clear search input
  const si = document.getElementById("cloneSearchInput");
  if (si) si.value = "";

  // Trigger CSS transition on next paint
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      page.classList.add("visible");
    });
  });

  // 4. Fetch real data from API and render cards
  _loadAndRenderTpCards();
}

// ── Close ─────────────────────────────────────────────────
function closeClonePage() {
  const page = document.getElementById("clonePage");
  const workBody = document.getElementById("leftPane")?.parentElement;
  const headerPill = document.querySelector(".header-pill-bar");
  const stepRail = document.getElementById("stepRail");
  const sidebar = document.getElementById("sidebar");

  if (!page) return;

  // Animate out
  page.classList.remove("visible");

  // After transition ends, restore workspace
  page.addEventListener(
    "transitionend",
    function _restore(e) {
      if (e.propertyName !== "opacity") return;
      page.removeEventListener("transitionend", _restore);
      page.style.display = "none";

      // Restore workspace body + header
      if (workBody) workBody.style.display = "";
      if (headerPill) headerPill.style.display = "";

      // Restore bottom rail nodes — only show ones not hidden by Thymeleaf (activeStep)
      ["mn-approved", "mn-rejected", "mn-saved", "mn-drafts"].forEach((id) => {
        const el = document.getElementById(id);
        if (el && !el.classList.contains("hidden")) el.style.display = "";
      });

      // Restore rails + active nav node based on current module/step
      const step = getActiveStep();
      if (step > 0) {
        setModuleUI("builder");
      } else if (window.location.pathname.startsWith("/builder/admin")) {
        setModuleUI("approver");
      }

      _tpSelected.clear();
    },
    { once: false },
  ); // we manually remove, so once:false is fine
}

// ── Render cards ──────────────────────────────────────────
// ── OTT name → asset lookup ──
// srcs[] = multiple icons for combo bundles (e.g. Netflix Prime shows both icons).
// Combo entries MUST come before their component keywords so exact match wins.
// NOTE: only entries with `allowed: true` are ever matched/rendered. This keeps
// the rendered icon set in lock-step with the _OTT_SERVICES master list below —
// anything not defined there (Facebook, Instagram, Google, etc.) is looked up
// here for reference only and will never produce an icon or a fallback badge.
const _OTT_META = [
  // ── Combo bundles (before individual keywords) ──────────────────────────
  {
    keywords: ["netflix prime"],
    title: "Netflix + Prime",
    allowed: true,
    srcs: [
      { src: "/images/ott/Netflix.avif", title: "Netflix" },
      { src: "/images/ott/Prime.svg", title: "Prime Video" },
    ],
    src: "/images/ott/Netflix.avif",
    desc: "Netflix + Prime Video bundle",
  },
  // ── Individual OTT platforms (must match _OTT_SERVICES below) ───────────
  {
    keywords: ["netflix"],
    title: "Netflix",
    allowed: true,
    src: "/images/ott/Netflix.avif",
    desc: "Award-winning series | Movies | Documentaries",
  },
  {
    keywords: ["prime", "amazon"],
    title: "Prime Video",
    allowed: true,
    src: "/images/ott/Prime.svg",
    desc: "Amazon Originals | Movies | Live Sports",
  },
  {
    keywords: ["hotstar", "jiohotstar", "disney"],
    title: "JioHotstar",
    allowed: true,
    src: "/images/ott/Jiohotstar.svg",
    desc: "TV Shows | Movies | Originals | Live Sports",
  },
  {
    keywords: ["zee5", "zee"],
    title: "ZEE5",
    allowed: true,
    src: "/images/ott/Zee5.svg",
    desc: "Web Series | Movies | Originals in 18 languages",
  },
  {
    keywords: ["sony", "sonyliv"],
    title: "SonyLIV",
    allowed: true,
    src: "/images/ott/SonyLiv.svg",
    desc: "Popular TV Shows | New Series | Movies",
  },
  {
    keywords: ["mxplayer", "mx player", "mx"],
    title: "MX Player",
    allowed: true,
    src: "/images/ott/MX_Player.webp",
    desc: "Free Movies | Web Series | Music Videos",
  },
  {
    keywords: ["saavn", "jiosaavn", "jio saavn"],
    title: "JioSaavn",
    allowed: true,
    src: "/images/ott/jiosaavn.png",
    desc: "Music | Podcasts | Radio | 80M+ Songs",
  },
  {
    keywords: ["fancode", "fan code"],
    title: "FanCode",
    allowed: true,
    src: "/images/ott/FanCode.svg",
    desc: "Live Cricket | Football | Sports Streaming",
  },
  // ── NOT part of _OTT_SERVICES — kept for reference only, never rendered ──
  {
    keywords: ["facebook"],
    title: "Facebook",
    src: "/images/ott/facebook.png",
    desc: "Social media | Videos | Marketplace",
  },
  {
    keywords: ["instagram"],
    title: "Instagram",
    src: "/images/ott/instagram.png",
    desc: "Reels | Stories | Photos",
  },
  {
    keywords: ["google"],
    title: "Google",
    src: "/images/ott/google.png",
    desc: "Search | Maps | YouTube & more",
  },
  {
    keywords: ["twitter", "x.com"],
    title: "Twitter / X",
    src: "/images/ott/twitter.png",
    desc: "News | Trends | Live conversations",
  },
  {
    keywords: ["chatgpt", "openai", "chat gpt"],
    title: "ChatGPT",
    src: "/images/ott/chat-gpt.png",
    desc: "AI assistant | Chat | Code | Writing",
  },
  {
    keywords: ["youtube"],
    title: "YouTube",
    src: "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/YouTube_full-color_icon_%282017%29.svg/240px-YouTube_full-color_icon_%282017%29.svg.png",
    desc: "Videos | Live | Shorts",
  },
  {
    keywords: ["whatsapp"],
    title: "WhatsApp",
    src: "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/WhatsApp.svg/240px-WhatsApp.svg.png",
    desc: "Messaging | Calls | Status",
  },
];
_OTT_META.forEach((m) => {
  if (!m.initial) m.initial = m.title.charAt(0);
});

// Resolves a raw rate-group name to a known OTT entry, or null if it isn't
// one of the explicitly allowed services. Returning null (instead of a
// generic fallback badge) is what keeps unknown/undefined services from
// being rendered at all — no icon, no letter badge, nothing.
function _ottLookup(name) {
  const lower = (name || "").toLowerCase().trim();
  // 1. Exact keyword match among allowed entries only
  const exact = _OTT_META.find(
    (m) => m.allowed && m.keywords.some((k) => k === lower),
  );
  if (exact) return exact;
  // 2. Longest keyword substring match among allowed entries so specific beats general
  const found = _OTT_META
    .filter((m) => m.allowed && m.keywords.some((k) => lower.includes(k)))
    .sort(
      (a, b) =>
        Math.max(...b.keywords.map((k) => k.length)) -
        Math.max(...a.keywords.map((k) => k.length)),
    )[0];
  if (found) return found;
  // Not a recognized/allowed OTT service — render nothing for it.
  return null;
}

// Build OTT icon strip: show `max` icons then '...'. Unknown/undefined
// services (anything _ottLookup can't resolve) are silently dropped —
// they don't get an icon, a fallback letter badge, or count toward the
// visible slots / "+N more" total.
function _buildOttStripHtml(rateGroupNames, max) {
  if (!rateGroupNames || !rateGroupNames.length) return "";

  // Resolve every name to its known icon(s); drop anything unresolved.
  const resolved = [];
  for (const name of rateGroupNames) {
    const svc = _ottLookup(name);
    if (!svc) continue; // not one of our defined OTT services — skip entirely
    if (svc.srcs && svc.srcs.length) {
      svc.srcs.forEach((sub) => resolved.push(sub));
    } else if (svc.src) {
      resolved.push(svc);
    }
  }

  if (!resolved.length) return "";

  let html = resolved
    .slice(0, max)
    .map(
      (item) =>
        `<img class="tp-ott-icon-img" src="${item.src}" alt="${item.title}" title="${item.title}"
             onerror="this.onerror=null;this.style.display='none';">`,
    )
    .join("");

  if (resolved.length > max)
    html += `<span class="tp-ott-more">+${resolved.length - max}</span>`;

  return html;
}

async function _loadAndRenderTpCards() {
  const grid = document.getElementById("clonePlanGrid");
  const countBadge = document.getElementById("clonePlanCount");
  if (!grid) return;

  grid.innerHTML =
    '<p style="padding:24px;color:var(--text-muted,#888)">Loading plans...</p>';

  try {
    const networkId =
      typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
    if (!networkId) {
      grid.innerHTML =
        '<p style="padding:24px;color:var(--text-muted,#888)">Network ID not found in session.</p>';
      return;
    }

    const res = await fetch("/tariff-package-details?networkId=" + networkId);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const plans = await res.json();

    if (!plans || !plans.length) {
      grid.innerHTML =
        '<p style="padding:24px;color:var(--text-muted,#888)">No tariff plans found for this network.</p>';
      if (countBadge) countBadge.textContent = "0 plans";
      return;
    }

    _renderTpCards(plans);
  } catch (err) {
    console.error("Failed to load tariff plans:", err);
    grid.innerHTML =
      '<p style="padding:24px;color:#e63946">Failed to load plans. Please try again.</p>';
  }
}

// ── OTT service master list (cards + modal share this) ────
const _OTT_SERVICES = [
  {
    id: "netflix",
    title: "Netflix",
    src: "/images/ott/Netflix.avif",
    // fallback: 'https://play-lh.googleusercontent.com/TBRwjS_qfJCSj1m7zZB93FnpJM5fSpMA_wUlFDLxWAb45T9RmwBvQd5cWR5viJJOhkI=s96',
    bg: "#000000",
    desc: "Award-winning series | Movies | Documentaries",
  },
  {
    id: "prime",
    title: "Prime Video",
    src: "/images/ott/Prime.svg",
    fallback:
      "https://play-lh.googleusercontent.com/7GeHvHSS4mPpgXgZbEcBnXPuqstCJSnXxN3HkJ1UXlW_cDiQ6wUnrMPP9UX3Lc5s-A=s96",
    bg: "#00a8e1",
    desc: "Amazon Originals | Movies | Live Sports",
  },
  {
    id: "hotstar",
    title: "JioHotstar",
    src: "/images/ott/Jiohotstar.svg",
    fallback:
      "https://play-lh.googleusercontent.com/N8wdJc9fXHWNFSHjFNBmMLBIsHTMVLvQWm0wAAOOVLvPz6jPE0O3hgGiHCBUaGnETQ=s96",
    bg: "#1f80e0",
    desc: "TV Shows | Movies | Originals | Live Sports",
  },
  {
    id: "zee5",
    title: "ZEE5",
    src: "/images/ott/Zee5.svg",
    fallback:
      "https://play-lh.googleusercontent.com/K2YZMc-arGqQrPjBT_BBORfTCNMvkVYi6hk1UHm7nzAE3-pjBYMBvZlRmFAZsXKlg7Y=s96",
    bg: "#8b1fa9",
    desc: "Web Series | Movies | Originals in 18 languages",
  },
  {
    id: "sonyliv",
    title: "SonyLIV",
    src: "/images/ott/SonyLiv.svg",
    fallback:
      "https://play-lh.googleusercontent.com/5kFbAj5LrFKKb42jDAfZ-rSR7nZ5kZSgd3xyRRn2OJUyFCxXU9V9pCvMWyGKWi2xSGM=s96",
    bg: "#003087",
    desc: "Popular TV Shows | New Series | Movies",
  },
  {
    id: "mxplayer",
    title: "MX Player",
    src: "/images/ott/MX_Player.webp",
    fallback:
      "https://play-lh.googleusercontent.com/qJ3jUspGE6OBkBEi1sWTBYggELSMCYLKZpLKB4FbHzQJJZBLWaZ0jL-nefcNfBzGXQ=s96",
    bg: "#ff6c00",
    desc: "Free Movies | Web Series | Music Videos",
  },
  {
    id: "jiosaavn",
    title: "JioSaavn",
    src: "/images/ott/JioSaavn.png",
    fallback:
      "https://play-lh.googleusercontent.com/YXF5WxFIGaE89K0K5C8fX2cV7RBBLxhI7HLlWv4rTVe1P0nIlTjy4eHT9iJqOKNitFoC=s96",
    bg: "#1db954",
    desc: "Music | Podcasts | Radio | 80M+ Songs",
  },
  {
    id: "fancode",
    title: "FanCode",
    src: "/images/ott/FanCode.svg",
    fallback:
      "https://play-lh.googleusercontent.com/8vFMcbQ9IuRPcJKz6lHt0W_FWu_pY4HUMqz-t7k-E1I4-GHUWbPXrVvjSSvF2EbIoQ=s96",
    bg: "#e63946",
    desc: "Live Cricket | Football | Sports Streaming",
  },
];

// ── Icons shown in plan cards (first 5 + "+N more" badge) ─
const _OTT_ICONS = _OTT_SERVICES;

// ── All loaded plans (for search filtering) ───────────────
let _allTpPlans = [];

// ── Category display order & icons ───────────────────────
const _CAT_ORDER = ["VOICE", "SMS", "DATA", "VOICE_SMS"];
const _CAT_ICON = {
  VOICE: "📞",
  SMS: "💬",
  DATA: "📶",
  VOICE_SMS: "📱",
};

// ── Group flat plan array by tariffPackageDesc ────────────
// New query returns one row per tariffPackageId with separate dataBenefit /
// smsBenefit / voiceBenefit columns, so grouping is now a simple normalisation
// pass that builds the same {buckets, rateGroupNames, _raw} shape the rest of
// the rendering code expects.
function _groupPlansByDesc(plans) {
  const map = new Map();
  plans.forEach((p) => {
    const key = p.tariffPackageDesc || "";
    if (!map.has(key)) {
      // Build ordered buckets from the three flat benefit columns
      const buckets = [];
      if (p.voiceBenefit)
        buckets.push({
          balanceCategory: "VOICE",
          bucketUnitValue: p.voiceBenefit,
        });
      if (p.smsBenefit)
        buckets.push({ balanceCategory: "SMS", bucketUnitValue: p.smsBenefit });
      if (p.dataBenefit)
        buckets.push({
          balanceCategory: "DATA",
          bucketUnitValue: p.dataBenefit,
        });

      map.set(key, {
        tariffPackageDesc: key,
        tariff_package_id: p.tariff_package_id,
        activationFee: p.activationFee,
        rentalType: p.rentalType,
        rentalPeriod: p.rentalPeriod,
        isCorporateYn: p.isCorporateYn,
        packageType: p.packageType,
        buckets,
        rateGroupNames: Array.isArray(p.rateGroupNames)
          ? [...p.rateGroupNames]
          : [],
        // Per-DATP benefit breakdown (voice/sms/data attached to individual DATPs),
        // shown separately in the details modal underneath the main benefits.
        datpBenefits: Array.isArray(p.datpBenefits) ? [...p.datpBenefits] : [],
        _raw: [p],
      });
    } else {
      // Duplicate desc (shouldn't happen with the new query, but handle safely)
      const group = map.get(key);
      if (Number(p.activationFee) > Number(group.activationFee)) {
        group.activationFee = p.activationFee;
      }
      if (Array.isArray(p.rateGroupNames)) {
        p.rateGroupNames.forEach(function (name) {
          if (name && !group.rateGroupNames.includes(name))
            group.rateGroupNames.push(name);
        });
      }
      if (Array.isArray(p.datpBenefits)) {
        p.datpBenefits.forEach(function (datp) {
          if (datp && !group.datpBenefits.some((d) => d.datpId === datp.datpId))
            group.datpBenefits.push(datp);
        });
      }
      group._raw.push(p);
    }
  });

  return Array.from(map.values());
}

function _renderTpCards(plans) {
  _allTpPlans = plans;
  _applyTpSearch("");
}

function _applyTpSearch(query) {
  const grid = document.getElementById("clonePlanGrid");
  const countBadge = document.getElementById("clonePlanCount");
  if (!grid) return;

  const q = query.trim().toLowerCase();

  // 1. Text search filter — include benefit columns + package type/corp in scope
  let flatFiltered = q
    ? _allTpPlans.filter((p) => {
        const fee = String(p.activationFee ?? "");
        const desc = (p.tariffPackageDesc || "").toLowerCase();
        const data = (p.dataBenefit || "").toLowerCase();
        const sms = (p.smsBenefit || "").toLowerCase();
        const voice = (p.voiceBenefit || "").toLowerCase();
        const packageType = (p.packageType || "").toLowerCase();
        const corpText =
          (p.isCorporateYn || "").toUpperCase() === "Y"
            ? "corp corporate"
            : "";
        return (
          fee.includes(q) ||
          desc.includes(q) ||
          data.includes(q) ||
          sms.includes(q) ||
          voice.includes(q) ||
          packageType.includes(q) ||
          corpText.includes(q)
        );
      })
    : _allTpPlans;

  // 2. Category filter — check the flat benefit columns from the new query
  if (_tpFilter.category && _tpFilter.category !== "ALL") {
    const cat = _tpFilter.category.toUpperCase();
    flatFiltered = flatFiltered.filter((p) => {
      if (cat === "DATA") return !!p.dataBenefit;
      if (cat === "SMS") return !!p.smsBenefit;
      if (cat === "VOICE") return !!p.voiceBenefit;
      return true;
    });
  }

  // 3. Group
  let groups = _groupPlansByDesc(flatFiltered);

  // 4. Price sort
  if (_tpFilter.price === "asc") {
    groups.sort((a, b) => Number(a.activationFee) - Number(b.activationFee));
  } else if (_tpFilter.price === "desc") {
    groups.sort((a, b) => Number(b.activationFee) - Number(a.activationFee));
  }

  // (validity filter: API doesn't return validity; stub for future use)

  if (countBadge)
    countBadge.textContent =
      groups.length + " plan" + (groups.length !== 1 ? "s" : "");

  grid.innerHTML = "";

  if (!groups.length) {
    grid.innerHTML =
      '<p style="padding:24px;color:var(--text-muted,#888)">No plans match your search.</p>';
    return;
  }

  // OTT icons strip — built per-card from group.rateGroupNames
  // (the static strip is computed inside the forEach below)

  groups.forEach((group, i) => {
    const planId = "tp-grp-" + encodeURIComponent(group.tariffPackageDesc);
    const selected = _tpSelected.has(planId);

    const feeNum = Number(group.activationFee);
    const priceHtml = `
            <span class="tp-price-main">
                <sup>₸</sup>${feeNum.toLocaleString("en-IN")}
            </span>
            <span class="tp-price-period">/m+GST</span>
        `;

    // Build benefit chips: one per non-null bucket (VOICE | SMS | DATA)
    const bucketsHtml = group.buckets
      .map((b) => {
        const icon = _CAT_ICON[b.balanceCategory] || "📦";
        const val = b.bucketUnitValue || "-";
        const cat = b.balanceCategory || "";
        const mod = cat.toLowerCase(); // 'voice' | 'sms' | 'data'
        return `
                <div class="tp-meta-col tp-meta-col--${mod}">
                    <span class="tp-meta-val">${val}</span>
                    <span class="tp-meta-key">${icon} ${cat}</span>
                </div>`;
      })
      .join('<div class="tp-meta-sep"></div>');

    // Package type tag: always shown (Prepaid/Postpaid/whatever the API sends)
    const pkgTypeRaw = (group.packageType || "").trim();
    const pkgTypeLabel = pkgTypeRaw
      ? pkgTypeRaw.charAt(0).toUpperCase() + pkgTypeRaw.slice(1).toLowerCase()
      : "";

    // Corp tag: only shown when isCorporateYn === "Y"
    const isCorp = (group.isCorporateYn || "").toUpperCase() === "Y";

    const card = document.createElement("div");
    card.className = "tp-plan-card" + (selected ? " selected" : "");
    card.dataset.planId = planId;
    card.style.setProperty("--card-i", i);

    card.innerHTML = `
            <div class="tp-check-badge"><span class="material-icons">check</span></div>

            <div class="tp-tag-row">
                <div class="tp-tag-group-left">
                    <div class="tp-tag">${
                      (group.rentalType || "").toLowerCase() === "others"
                        ? group.rentalPeriod != null
                          ? group.rentalPeriod +
                            " Day" +
                            (group.rentalPeriod !== 1 ? "s" : "")
                          : "Others"
                        : group.rentalType || "Individual plan"
                    }</div>
                    ${pkgTypeLabel ? `<div class="tp-tag tp-tag--type">${pkgTypeLabel}</div>` : ""}
                </div>
                ${isCorp ? `<div class="tp-tag tp-tag--corp">Corp</div>` : ""}
            </div>

            <div class="tp-price-only">
                ${priceHtml}
            </div>

            <div class="tp-buckets-row tp-buckets-row--multi">
                ${bucketsHtml}
            </div>

            <div class="tp-ott-strip">${_buildOttStripHtml(group.rateGroupNames, 2)}</div>

            <div class="tp-card-actions">
                <button
                    class="tp-btn-details"
                    onclick='event.stopPropagation();openTpDetails(${JSON.stringify(JSON.stringify(group))})'
                >
                    View Details
                </button>

                <button
                    class="tp-btn-select"
                    onclick="event.stopPropagation();openCloneTree('${encodeURIComponent(group.tariffPackageDesc)}', ${group.tariff_package_id || group._raw[0]?.tariff_package_id || "null"}, 'clone')"
                >
                    Select
                </button>
            </div>
        `;

    grid.appendChild(card);
  });
}

function _toggleTpSelect(planId) {
  if (_tpSelected.has(planId)) {
    _tpSelected.delete(planId);
  } else {
    _tpSelected.add(planId);
  }

  const card = document.querySelector(
    `.tp-plan-card[data-plan-id="${planId}"]`,
  );
  if (card) {
    const isSelected = _tpSelected.has(planId);
    card.classList.toggle("selected", isSelected);
    const btn = card.querySelector(".tp-btn-select");
    if (btn) btn.textContent = isSelected ? "Selected" : "Select";
  }
}

// ── Clone action stub ─────────────────────────────────────
function handleCloneAction() {
  const ids = Array.from(_tpSelected);
  if (!ids.length) return;
  showToast(
    `Cloning ${ids.length} plan(s).\n(Wire to your POST /api/clone endpoint)`,
  );
}

// ── Clone Tree Modal ──────────────────────────────────────
async function openCloneTree(encodedDesc, tariffPackageId, context) {
  const tpDesc = decodeURIComponent(encodedDesc);
  const modal = document.getElementById("cloneTreeModal");
  const body = document.getElementById("cloneTreeBody");

  // Store for action buttons
  modal.dataset.tpDesc = tpDesc;
  modal.dataset.tpId = tariffPackageId || "";
  // "clone" (default) = opened from the clone page's Select button.
  // "approved" = opened from the Approved TPs overlay — Modify must save
  // back into the existing approved package instead of creating a clone.
  modal.dataset.context = context || "clone";

  // Approved overlay only needs the Modify action — same behavior as the
  // old direct-to-builder click, just shown inside this modal first.
  // Clone page keeps all three buttons (Cancel / Modify / Clone).
  const isApproved = modal.dataset.context === "approved";
  const cancelBtn = modal.querySelector(".ctm-btn--cancel");
  const cloneBtn = modal.querySelector(".ctm-btn--clone");
  if (cancelBtn) cancelBtn.style.display = isApproved ? "none" : "";
  if (cloneBtn) cloneBtn.style.display = isApproved ? "none" : "";

  // Store full plan object so Clone button can POST it directly

  // Show modal with loading state
  body.innerHTML = `<div class="ctm-loading">
        <span class="material-icons ctm-spin">refresh</span>
        Loading plan structure…
    </div>`;
  modal.classList.add("active");

  // Fetch
  try {
    const networkId =
      typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
    const res = await fetch(
      `/details?networkId=${networkId}&tariffPackageId=${tariffPackageId}`,
    );
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();
    _currentClonePayload = data;
    _renderCloneTree(body, tpDesc, data);
  } catch (err) {
    console.error("Clone tree fetch error:", err);
    body.innerHTML = `<div class="ctm-error">
            <span class="material-icons">error_outline</span>
            Failed to load plan details. Please try again.
        </div>`;
  }
}

function _renderCloneTree(container, tpDesc, response) {
  // ── Unwrap: response is { tpName, username, networkId, data: {...} }
  const d = response.data || response;

  const tpName = d.tariffPlanName || d.tariffPackageDesc || "—";
  const datpRows = d.defaultAtps || [];
  const caAtpRows = d.caAtps || [];
  // CA-category AATPs live only in caAtps now (see saveConfiguration()), so
  // merge them back in here as their own AATP rows for display. They don't
  // carry a `category` field in caAtps itself, so tag it on here.
  const aatpRows = [
    ...(d.allowedAtps || []),
    ...caAtpRows.map((c) => ({ ...c, category: "CA" })),
  ];

  function attrPill(label, value) {
    if (value === null || value === undefined || value === "") return "";
    return `<span class="pd-attr">
                    <span class="pd-attr-label">${label}</span>
                    <span class="pd-attr-value">${value}</span>
                </span>`;
  }

  function componentRow(r, index, type) {
    const name = r.packageName || r.chargeDesc || r.chargeId || type;
    const ca =
      type === "AATP" && r.category === "CA"
        ? caAtpRows.find(
            (c) => String(c.servicePackageId) === String(r.servicePackageId),
          )
        : null;
    const attrs = [
      attrPill(
        "Validity",
        r.validity
          ? {
              M: "Monthly",
              O: "Others",
              D: "Daily",
              W: "Weekly",
              FM: "Fixed Month",
              CW: "Calendar Week",
              CM: "Calendar Month",
              U: "Unlimited",
              Y: "Yearly",
            }[r.validity] || r.validity
          : "—",
      ),
      r.validity === "O" && r.rentalPeriod
        ? attrPill("Validity Days", r.rentalPeriod)
        : "",
      attrPill("Mid. Expiry", r.midnightExpiry || "—"),
      attrPill("Renewal", r.renewal || "—"),
      attrPill("Rental", r.rental ?? "0"),
      attrPill("Max Count", r.maxCount ?? "0"),
      attrPill("Free Cycles", r.freeCycles ?? "0"),
      attrPill("MRP", r.mrp ?? "0"),
      attrPill("Service Code", r.serviceCode || ""),
      r.category !== "CA"
        ? attrPill("VIP Plan", r.vipPlan === "Y" ? "Yes" : r.vipPlan === "N" ? "No" : "—")
        : "",
      ca ? attrPill("Default Lines", ca.defaultLinesAllowed ?? "0") : "",
      ca ? attrPill("Charge/Line", ca.additionalChargePerLine ?? "0") : "",
      ca ? attrPill("Rollover", ca.packageRolloverYn || "—") : "",
      ca ? attrPill("CA Start", ca.packageStartDate || "") : "",
      ca ? attrPill("CA End", ca.packageEndDate || "") : "",
      ...(ca
        ? (ca.serviceMappings || []).map((m) =>
            attrPill(
              m.serviceUnitType || "Mapping",
              `${m.units ?? 0} units, topup ${m.topupCharge ?? 0}, max xfer ${m.maxTransferLimit ?? 0}%`,
            ),
          )
        : []),
    ].join("");
    const colorClass = type === "DATP" ? "pd-row--datp" : "pd-row--aatp";
    const badge = type === "DATP" ? "pd-badge--datp" : "pd-badge--aatp";
    return `
        <div class="pd-component-row ${colorClass}">
            <div class="pd-row-top">
                <span class="pd-row-badge ${badge}">${type}</span>
                <span class="pd-row-name">${name}</span>
                <span class="pd-row-index">#${index + 1}</span>
            </div>
            <div class="pd-row-attrs">${attrs || '<span class="pd-no-attrs">No attributes</span>'}</div>
        </div>`;
  }

  const datpHtml = datpRows.length
    ? datpRows.map((r, i) => componentRow(r, i, "DATP")).join("")
    : '<div class="pd-empty-section">No DATP components</div>';

  const aatpHtml = aatpRows.length
    ? aatpRows.map((r, i) => componentRow(r, i, "AATP")).join("")
    : '<div class="pd-empty-section">No AATP components</div>';

  container.innerHTML = `
        <div class="pd-sheet">
            <div class="pd-plan-band">
                <div class="pd-plan-band-left">
                    <span class="pd-plan-label">SERVICE PLAN</span>
                    <span class="pd-plan-name">${tpName}</span>
                </div>
                <div class="pd-plan-band-right">
                    <span class="pd-plan-label">PACKAGE</span>
                    <span class="pd-plan-pkg">${tpDesc}</span>
                </div>
            </div>
            <div class="pd-sections">
                <div class="pd-section">
                    <div class="pd-section-header pd-section-header--datp">
                        <span class="material-icons pd-section-icon">add_circle_outline</span>
                        <span class="pd-section-title">Default ATP</span>
                        <span class="pd-section-count">${datpRows.length}</span>
                    </div>
                    <div class="pd-section-body">${datpHtml}</div>
                </div>
                <div class="pd-section">
                    <div class="pd-section-header pd-section-header--aatp">
                        <span class="material-icons pd-section-icon">shopping_cart</span>
                        <span class="pd-section-title">Allowed ATP</span>
                        <span class="pd-section-count">${aatpRows.length}</span>
                    </div>
                    <div class="pd-section-body">${aatpHtml}</div>
                </div>
            </div>
        </div>`;
}

function closeCloneTree() {
  document.getElementById("cloneTreeModal").classList.remove("active");
}

function _cloneTreeOverlayClick(e) {
  if (e.target === document.getElementById("cloneTreeModal")) closeCloneTree();
}

document.addEventListener("click", function (e) {
  if (e.target.id === "tpDetailsModal") closeTpDetails();
});

// console.log("CLONE PAYLOAD:", JSON.stringify(payload, null, 2));

async function _cloneTreeAction(action) {
  const modal = document.getElementById("cloneTreeModal");
  const tpDesc = modal.dataset.tpDesc;
  const tpId = modal.dataset.tpId;

  if (action === "clone") {
    const payload = _currentClonePayload;

    if (payload == null) {
      showToast("Plan data not available. Please close and try again.");
      return;
    }

    // Inject username from sessionStorage (handles old DB records where createdBy is null)
    payload.username =
      sessionStorage.getItem(STORAGE_KEYS.USERNAME) ||
      (typeof USERNAME !== "undefined" ? USERNAME : "");
    if (payload.data) {
      payload.data.username = payload.username;
    }

    // Disable button to prevent double-submit
    const cloneBtn = modal.querySelector('[onclick*="clone"]');
    if (cloneBtn) {
      cloneBtn.disabled = true;
      cloneBtn.textContent = "Cloning…";
    }

    try {
      const res = await fetch("/clone", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      const result = await res.json();

      if (!res.ok || result.status === "error") {
        const reason =
          result.message || result.error || "Clone failed. Please try again.";
        const detail = result.failedTable
          ? "\nFailed at: " + result.failedStep + " -> " + result.failedTable
          : "";
        showToast(" Clone failed:\n" + reason + detail);
        return;
      }

      if (!result.clonedTpName) {
        showToast(" Clone failed: server did not return a cloned plan name.");
        return;
      }

      showToast("Cloned successfully! New plan: " + result.clonedTpName, "success");
      closeCloneTree();
      await _loadAndRenderTpCards();
    } catch (err) {
      console.error("Clone error:", err);
      showToast("Server error during clone. Please try again.");
    } finally {
      if (cloneBtn) {
        cloneBtn.disabled = false;
        cloneBtn.textContent = "Clone";
      }
    }
  } else if (action === "modify") {
    const payload = _currentClonePayload;
    if (!payload) {
      showToast("Plan data not available. Please close and try again.");
      return;
    }

    const context = modal.dataset.context || "clone";
    const isApproved = context === "approved";
    const d = payload.data || payload;

    // Build builder state from the plan data (same shape as loadSavedPackage).
    // Approved TPs additionally carry chargeId so step4 can filter dropdowns
    // correctly for already-approved packages (see currentChargeId backend
    // overload) — clone-page plans never had this field.
    const state = rebuildStateFromPackage(d, { includeChargeId: isApproved });

    // Helper: JSON.stringify array or fallback to '[]'
    const svcsToJson = (val) => {
      if (Array.isArray(val)) return JSON.stringify(val);
      if (typeof val === "string") return val || "[]";
      return "[]";
    };

    sessionStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(state));
    sessionStorage.setItem(
      STORAGE_KEYS.CONFIG_NAME,
      payload.tpName || d.tariffPackageDesc || "",
    );
    sessionStorage.setItem(STORAGE_KEYS.PKG_TYPE, d.packageType || "");
    sessionStorage.setItem(STORAGE_KEYS.PKG_SUB_TYPE, d.tariffPackCategory || "");
    sessionStorage.setItem(STORAGE_KEYS.PERIODIC_CHARGE_ID, d.periodicChargeID || "");
    sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S2, svcsToJson(d.selectedSvcs_s2));
    sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S3, svcsToJson(d.selectedSvcs_s3));
    sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S4, svcsToJson(d.selectedSvcs_s4));
    sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S5, svcsToJson(d.selectedSvcs_s5));

    if (isApproved) {
      // Edit the existing approved package in place.
      sessionStorage.setItem(STORAGE_KEYS.IS_UPDATE, "true");
      sessionStorage.setItem(STORAGE_KEYS.APPROVED_MODE, "true");
      sessionStorage.setItem(
        STORAGE_KEYS.APPROVED_TP_NAME,
        payload.tpName || d.tariffPackageDesc || "",
      );
      sessionStorage.setItem(STORAGE_KEYS.APPROVED_TARIFF_PACKAGE_ID, String(tpId || ""));
    } else {
      // Flag: step6 will show "Clone Package" instead of "Save Config"
      sessionStorage.setItem(STORAGE_KEYS.CLONE_MODE, "true");
      // Store original tpName and networkId for the clone POST
      sessionStorage.setItem(
        STORAGE_KEYS.CLONE_TP_NAME,
        payload.tpName || d.tariffPackageDesc || "",
      );
      sessionStorage.setItem(STORAGE_KEYS.CLONE_NETWORK_ID, String(payload.networkId || ""));
      // username is read from sessionStorage directly in step6 — no need to re-store it
    }

    closeCloneTree();
    window.isInternalNavigation = true;
    window.location.href = "/builder/step1";
  } else {
    closeCloneTree();
  }
}

