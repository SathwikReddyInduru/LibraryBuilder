// ═══════════════════════════════════════════════════════════
// ATP CREATE — standalone page (/builder/atpcreate).
// Same list-left / view-or-form-right pattern as the ATP Rules
// page, but as its own page (own nav node, own path in
// BuilderPageController) rather than an in-place overlay —
// this feature is expected to be temporary, so it's kept in its
// own small set of files (atpcreate.html, Atpcreate.js,
// Atpcreate.css) instead of touching layout.html/builder.css/etc.
//
// Everything on this page lives in this one file: the left-pane
// ATP library list (load/render/search) below, and the right-pane
// create/modify form (payload building, sim-range rows, checklist
// search/select-all, balance-category show/hide, submit) in the
// IIFE further down.
// ═══════════════════════════════════════════════════════════

let _acAtps = [];          // cached ATP list — from /builder/added-packages
let _acSelectedId = null;  // id of the ATP currently shown in the right pane
let _acDetailCache = {};   // servicePackageId -> detail object (once the real view API exists)

// ── Shared static mapping tables ────────────────────────────────
// Single source of truth for both the create/modify form (which
// turns a human choice into the id/code the API expects) and the
// read-only ATP detail view further down (which turns that same
// id/code back into a human label). Kept at file scope — not inside
// the form IIFE below — so both sides read from the exact same data
// and can never drift out of sync with each other.

const BUCKET_TYPE_MAP = {
  promotional: "P",
  bonus: "B",
  traffic: "T",
};
const BUCKET_TYPE_LABEL_MAP = { P: "Promotional", B: "Bonus", T: "Traffic" };

// bucketUnitType is sent exactly as the Unit Type <select>'s
// value — Sec/Amt/Calls/Msg/Byt, matching the legacy dropdown's
// option values verbatim — not uppercased into a made-up code.

// Values are the legacy usageTypeMap bitmask ids (from the legacy
// usageTypeMap <select>, name="usageTypeMap"). Voice/SMS/MMS/Global
// all share the same curated 6-item subset of that catalog (the same
// set appears in all four of those legacy screens), rather than the
// full ~37-value list. Data has its own separate, smaller legacy
// list (non/roaming/national-roaming data) — its 3 options are used
// as-is.
const SHARED_USAGE_TYPE_IDS = {
  local_onnet_mo_m2m: 1,
  local_offnet_mo_m2m: 16,
  isd_mo_m2m: 262144,
  premium_mo: 67108864,
  roaming_mo: 1073741824,
  national_roaming_mo: 68719476736,
};

const USAGE_TYPE_ID_MAP = {
  voice: SHARED_USAGE_TYPE_IDS,
  sms: SHARED_USAGE_TYPE_IDS,
  mms: SHARED_USAGE_TYPE_IDS,
  global: SHARED_USAGE_TYPE_IDS,
  data: {
    non_roaming_data: 65536,
    roaming_data: 131072,
    national_roaming_data: 274877906944,
  },
};

// Display labels for the SHARED_USAGE_TYPE_IDS / data usage-type ids
// above, matching the checklist copy in atpcreate.html.
const USAGE_TYPE_LABEL_MAP = {
  local_onnet_mo_m2m: "Local On-Net MO (M2M)",
  local_offnet_mo_m2m: "Local Off-Net MO (M2M)",
  isd_mo_m2m: "ISD MO (M2M)",
  premium_mo: "Premium MO",
  roaming_mo: "Roaming MO",
  national_roaming_mo: "National Roaming MO",
  non_roaming_data: "Non-Roaming Data",
  roaming_data: "Roaming Data",
  national_roaming_data: "National Roaming Data",
};
// id -> label, built once below (ids don't collide across categories).
const USAGE_TYPE_ID_TO_LABEL = {};
Object.values(USAGE_TYPE_ID_MAP).forEach((idMap) => {
  Object.entries(idMap).forEach(([slug, id]) => {
    USAGE_TYPE_ID_TO_LABEL[id] = USAGE_TYPE_LABEL_MAP[slug] || slug;
  });
});

// Static stand-in for the Zone Group / Data Zone Group GET APIs
// (not implemented yet). Each option carries the id + ratingFlag
// the backend will eventually return ("Y" = rating zone group,
// "N" = restricted zone group) so the payload shape (see
// buildRatingFlagPairs) never has to change once real data lands —
// only these two arrays get more entries. renderRatingChecklist
// groups them under "Rating"/"Restricted" headers regardless of
// how many there are.
const ZONE_GROUP_OPTIONS = [
  { id: 1, name: "LOCAL_VOICE_ZONE", ratingFlag: "Y" },
  { id: 2, name: "LOCAL_SMS_ZONE", ratingFlag: "Y" },
  { id: 3, name: "FT_LOCAL_ONNET", ratingFlag: "N" },
  { id: 4, name: "FT_LOCAL_OFNET", ratingFlag: "N" },
];
const DATA_ZONE_GROUP_OPTIONS = [
  { id: 2, name: "DNS_DATA_ZONE", ratingFlag: "Y" },
  { id: 11, name: "DATAROAMING", ratingFlag: "Y" },
  { id: 22, name: "TEST_2580", ratingFlag: "N" },
  { id: 43, name: "INSTA", ratingFlag: "N" },
];

// Values match the legacy admin screen's derivedService1..4 option
// lists (category prefix ~ legacy numeric id): Voice gets the full
// 1-14 set, SMS gets 1-11 + 14 (no Roaming Call back Service/Call
// Forwarding), Data is just Local MO/National ROAMING/InterNational
// ROAMING (1, 5, 7), and MMS is 1-8 + 14 (no roaming-callback/CUG/
// friends-family/homzone/call-forwarding options).
const DERIVED_SERVICE_ID_MAP = {
  voice: {
    local_mo: "1~1",
    local_mt: "1~2",
    std: "1~3",
    isd: "1~4",
    national_roaming: "1~5",
    national_roaming_mt: "1~6",
    international_roaming: "1~7",
    international_roaming_mt: "1~8",
    closed_user_group: "1~9",
    friends_family: "1~10",
    homzone: "1~11",
    roaming_callback_service: "1~12",
    call_forwarding: "1~13",
    community_account: "1~14",
  },
  sms: {
    local_mo: "2~1",
    local_mt: "2~2",
    std: "2~3",
    isd: "2~4",
    national_roaming: "2~5",
    national_roaming_mt: "2~6",
    international_roaming: "2~7",
    international_roaming_mt: "2~8",
    closed_user_group: "2~9",
    friends_family: "2~10",
    homzone: "2~11",
    community_account: "2~14",
  },
  data: {
    local_mo: "3~1",
    national_roaming: "3~5",
    international_roaming: "3~7",
  },
  mms: {
    local_mo: "4~1",
    local_mt: "4~2",
    std: "4~3",
    isd: "4~4",
    national_roaming: "4~5",
    national_roaming_mt: "4~6",
    international_roaming: "4~7",
    international_roaming_mt: "4~8",
    community_account: "4~14",
  },
  global: {
    international_roaming: "5~1",
    balance_sharing: "5~2",
    multi_currency: "5~3",
  },
};

const DERIVED_SERVICE_CATEGORY_LABELS = {
  voice: "Voice",
  sms: "SMS",
  data: "Data",
  mms: "MMS",
  global: "Global",
};

// Display labels for the DERIVED_SERVICE_ID_MAP slugs above.
const DERIVED_SERVICE_LABEL_MAP = {
  local_mo: "Local MO",
  local_mt: "Local MT",
  std: "STD",
  isd: "ISD",
  national_roaming: "National Roaming",
  national_roaming_mt: "National Roaming MT",
  international_roaming: "International Roaming",
  international_roaming_mt: "International Roaming MT",
  closed_user_group: "Closed User Group",
  friends_family: "Friends Family",
  homzone: "Homzone",
  roaming_callback_service: "Roaming Call back Service",
  call_forwarding: "Call Forwarding",
  community_account: "Community Account",
  balance_sharing: "Global Balance Sharing",
  multi_currency: "Multi-Currency Billing",
};
// "1~1" -> { category: "voice", label: "Local MO" }, built once below.
const DERIVED_SERVICE_CODE_TO_INFO = {};
Object.entries(DERIVED_SERVICE_ID_MAP).forEach(([category, slugs]) => {
  Object.entries(slugs).forEach(([slug, code]) => {
    DERIVED_SERVICE_CODE_TO_INFO[code] = {
      category,
      label: DERIVED_SERVICE_LABEL_MAP[slug] || slug,
    };
  });
});

// Roaming networks — FTL / India send their integer network ID;
// "All Networks" is a form-only convenience (checks the other two),
// it never has an id of its own.
const ROAMING_NETWORK_ID_MAP = { ftl: 1, india: 2 };
const ROAMING_NETWORK_LABEL_MAP = { 1: "FTL", 2: "India" };

// Matches the Billing Service Type <select> options in atpcreate.html.
const BILLING_SERVICE_TYPE_LABEL_MAP = {
  101: "Call Alert",
  102: "Flash Message",
  103: "Call Waiting",
  104: "Call Conference",
  108: "Barring GPRS in Roaming",
  113: "Missed Call Alerts",
  114: "CLIP",
  115: "CLIR",
  116: "CRBT",
  117: "Missed Call Alerts Plus",
  118: "VOICEMAIL",
  119: "CALLFILTER",
  120: "Black Berry",
  121: "CallerId",
  122: "IPTV",
  123: "Call Barring",
  124: "Selective Call Enabling",
  125: "Concessional Location",
  126: "Selective Location Enabling",
  127: "Semi Local Calls",
  128: "LowBalAlerts",
  129: "Contents",
  130: "Extra Service",
  131: "Home internet service",
  132: "TV and video services",
  133: "Eligibility management",
  134: "Digital service entitlements",
  135: "Member-level activation",
  136: "Cloud storage",
};

// Matches the Category Offer Code <select> options in atpcreate.html.
const CATEGORY_OFFER_CODE_LABEL_MAP = {
  HLRS: "HLRS",
  B: "Bonus",
  TB: "Traffic Bundle",
  TM: "Traffic Module",
  P: "Promotional",
};

// Matches the Calendar Config <select> options in atpcreate.html.
const CALENDAR_CONFIG_LABEL_MAP = {
  5: "NATIONAL_CALENDAR",
  4: "DNS_DATA_CAL",
  3: "LOCAL_DATA_CAL",
  2: "LOCAL_SMS_CAL",
  1: "LOCAL_VOICE_CAL",
  66: "MT_CALENDAR_ROAMING",
};

function _acEscape(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[c]);
}

// ── Left pane: list load / render / search ───────────────────
async function _acLoadList() {
  const listEl = document.getElementById("acList");
  listEl.innerHTML = `<div class="ar-empty-state"><span>Loading ATPs…</span></div>`;

  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";

  if (!networkId) {
    console.error("ATP Create: NETWORK_ID not found in session.");
    _acAtps = [];
    _acRenderList(_acAtps);
    return;
  }

  try {
    const res = await fetch("/builder/added-packages?networkId=" + networkId);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();

    _acAtps = (data || []).map((item) => ({
      id: item.servicePackageId,
      name: item.servicePackageDesc,
    }));
  } catch (err) {
    console.error("Failed to load ATP library:", err);
    _acAtps = [];
  }

  _acRenderList(_acAtps);
}

function _acRenderList(atps) {
  const listEl = document.getElementById("acList");
  const emptyEl = document.getElementById("acEmptyState");
  const countEl = document.getElementById("acCount");

  countEl.textContent = `${_acAtps.length} ATP${_acAtps.length === 1 ? "" : "s"}`;

  if (!atps.length) {
    listEl.innerHTML = "";
    emptyEl.style.display = "flex";
    if (_acAtps.length) {
      emptyEl.innerHTML = `<span class="material-icons">search_off</span><p>No matching ATPs</p>`;
    } else {
      emptyEl.innerHTML = `<span class="material-icons">inbox</span><p>No ATPs yet</p><span>Create your first Additional Tariff Profile.</span>`;
    }
    return;
  }
  emptyEl.style.display = "none";

  listEl.innerHTML = atps
    .map(
      (a) => `
        <div class="ar-rule-row ${String(a.id) === String(_acSelectedId) ? "selected" : ""}"
             data-id="${a.id}" onclick="_acSelectAtp('${a.id}')">
          <div class="ar-rule-row-top">
            <div class="ar-rule-row-text">
              <span class="ar-rule-name">${_acEscape(a.name || "(Untitled ATP)")}</span>
              <span class="ar-rule-id">#${_acEscape(a.id)}</span>
            </div>
            <span class="material-icons ar-rule-chevron">chevron_right</span>
          </div>
        </div>`,
    )
    .join("");
}

function _acHighlightSelectedRow() {
  document.querySelectorAll("#acList .ar-rule-row").forEach((row) => {
    row.classList.toggle("selected", row.dataset.id === String(_acSelectedId));
  });
}

function _acApplySearch(term) {
  const q = (term || "").trim().toLowerCase();
  if (!q) return _acRenderList(_acAtps);
  const filtered = _acAtps.filter(
    (a) =>
      String(a.name || "").toLowerCase().includes(q) ||
      String(a.id).includes(q),
  );
  _acRenderList(filtered);
}

// ── Right-pane view switching ─────────────────────────────────
function _acShowPlaceholder() {
  document.getElementById("acPlaceholder").classList.remove("hidden");
  document.getElementById("acViewPanel").classList.add("hidden");
  document.getElementById("acFormPanel").classList.add("hidden");
}

function _acShowForm() {
  _acSelectedId = null;
  _acHighlightSelectedRow();

  const panel = document.getElementById("acFormPanel");
  panel.dataset.mode = "create";
  panel.dataset.servicePackageId = "";

  const title = panel.querySelector(".ar-form-header h3");
  const sub = panel.querySelector(".ar-form-header-sub");
  const publishLabel = document.getElementById("atpCreatePublishLabel");
  const publishBtn = document.getElementById("atpCreatePublish");
  const notice = document.getElementById("atpFormModifyNotice");
  if (title) title.textContent = "New ATP";
  if (sub) sub.textContent = "Define the tariff, validity and usage rules for this ATP";
  if (publishLabel) publishLabel.textContent = "Publish ATP";
  if (publishBtn) {
    publishBtn.disabled = false;
    publishBtn.title = "";
  }
  if (notice) notice.style.display = "none";

  if (typeof window._acResetForm === "function") window._acResetForm();

  document.getElementById("acPlaceholder").classList.add("hidden");
  document.getElementById("acViewPanel").classList.add("hidden");
  panel.classList.remove("hidden");
}

function _acCancelForm() {
  if (_acSelectedId != null) {
    const id = _acSelectedId;
    _acSelectedId = null; // clear so _acSelectAtp doesn't treat this as a deselect toggle
    _acSelectAtp(id);
  } else {
    _acShowPlaceholder();
  }
}

// ── Right pane: read-only view of a selected ATP ──────────────
// Fetches from GET /atp/{atpId} (com.xius.Lb.controller.AtpController),
// which reconstructs the full create payload for a given ATP straight
// from the DB (id here is the same servicePackageId the list rows
// carry — see AtpDetailsRepository.findAtpCore). Falls back to the
// name/id already known from the list if that call fails, instead of
// pretending to have data it doesn't.
async function _acSelectAtp(id) {
  const listed = _acAtps.find((a) => String(a.id) === String(id));
  if (!listed) return;

  if (String(_acSelectedId) === String(id)) {
    _acSelectedId = null;
    _acHighlightSelectedRow();
    _acShowPlaceholder();
    return;
  }

  _acSelectedId = id;
  _acHighlightSelectedRow();

  document.getElementById("acPlaceholder").classList.add("hidden");
  document.getElementById("acFormPanel").classList.add("hidden");
  const viewPanel = document.getElementById("acViewPanel");
  viewPanel.classList.remove("hidden");
  viewPanel.innerHTML = `<div class="ar-empty-state"><span>Loading ATP…</span></div>`;

  if (_acDetailCache[id]) {
    _acRenderView(id, listed.name, _acDetailCache[id]);
    return;
  }

  try {
    const res = await fetch(`/atp/${id}`);
    if (res.ok) {
      const detail = await res.json();
      if (String(_acSelectedId) !== String(id)) return;
      _acDetailCache[id] = detail;
      _acRenderView(id, listed.name, detail);
      return;
    }
    console.error(`GET /atp/${id} failed with status ${res.status}`);
  } catch (err) {
    console.error("Failed to load ATP details:", err);
  }

  if (String(_acSelectedId) !== String(id)) return;
  _acRenderView(id, listed.name, null);
}

// Icon + label for each detail section — looked up by _acCardTitle
// to put a small icon badge next to each card's title.
const AC_DETAIL_SECTIONS = [
  { id: "overview", label: "Overview", icon: "description" },
  { id: "validity", label: "Validity & Scheduling", icon: "event_available" },
  { id: "sim", label: "SIM / IMSI Ranges", icon: "sim_card" },
  { id: "roaming", label: "Roaming", icon: "public" },
  { id: "balance", label: "Balance Categories", icon: "account_balance_wallet" },
  { id: "derived", label: "Derived Services", icon: "hub" },
  { id: "calendar", label: "Zone & Calendar", icon: "calendar_month" },
];

function _acRenderView(id, name, detail) {
  const panel = document.getElementById("acViewPanel");
  const displayName = detail?.servicePlanDesc || detail?.atpName || name || `ATP #${id}`;

  // Quick-fact chips under the title — the handful of things you'd
  // otherwise have to scroll to Overview/Validity to see. Only shown
  // once `detail` has actually loaded.
  // Rating/Billing + validity-days chips disabled per feedback —
  // commented out rather than removed in case they're wanted back.
  // const typeOfServiceLabel = { 1: "Rating", 2: "Billing" }[detail?.typeOfService] ?? null;
  const heroFacts = detail
    ? [
        // typeOfServiceLabel && { icon: "sell", text: typeOfServiceLabel },
        // detail.validityPeriodType && {
        //   icon: "event_available",
        //   text:
        //     detail.validityPeriodType === "LIMITED"
        //       ? `${detail.validityPeriodDays ?? "?"} day validity`
        //       : "Unlimited validity",
        // },
        String(detail.vipPlanFlagYn ?? "").toUpperCase() === "Y" && {
          icon: "star",
          text: "VIP Plan",
        },
      ].filter(Boolean)
    : [];

  panel.innerHTML = `
    <div class="ar-view-header">
      <div class="ar-view-header-icon"><span class="material-icons">receipt_long</span></div>
      <div class="ar-view-header-main">
        <h2>${_acEscape(displayName)}</h2>
        <div class="ar-view-header-meta">
          <span class="ar-rule-date">Service Package ID: <strong>${_acEscape(id)}</strong></span>
          ${
            heroFacts.length
              ? `<span class="ar-view-hero-facts">${heroFacts
                  .map(
                    (f) =>
                      `<span class="ar-view-hero-fact"><span class="material-icons">${f.icon}</span>${_acEscape(f.text)}</span>`,
                  )
                  .join("")}</span>`
              : ""
          }
        </div>
      </div>
      <div class="ar-view-header-actions">
        <button type="button" class="ar-icon-btn ar-icon-btn--primary" onclick="_acModifyAtp('${id}')">
          <span class="material-icons">edit</span> Modify
        </button>
      </div>
    </div>
    <div class="ar-view-scroller">
      <div class="ar-view-body">
        ${
          detail
            ? _acRenderAtpDetail(detail)
            : `<div class="ar-card">
                <div class="ar-card-title">Summary</div>
                <div class="atpc-grid-2">
                  <div class="ar-field"><label>ATP Name</label><div class="ar-input ar-input-readonly">${_acEscape(displayName)}</div></div>
                  <div class="ar-field"><label>Service Package ID</label><div class="ar-input ar-input-readonly">${_acEscape(id)}</div></div>
                </div>
                <div class="ar-field" style="margin-top:14px;">
                  <div class="ar-cancel-btn" style="cursor:default;display:inline-flex;align-items:center;gap:8px;background:#fffbeb;border-color:#fde68a;color:#92400e;">
                    <span class="material-icons" style="font-size:16px;">info</span>
                    Full ATP details aren't available from the backend yet — showing what's known from the list.
                  </div>
                </div>
              </div>`
        }
      </div>
    </div>`;
}

// ── Read-only ATP detail view ──────────────────────────────────
// Renders the GET /atp/{id} response as plain label/value text
// inside .ar-detail-* — deliberately NOT .ar-input/.ar-input-
// readonly, so nothing in this view looks like a form field. This is
// purely for looking at an ATP's config; "Modify" (see _acModifyAtp
// below) is the only path that ever opens the actual create/edit
// form, which it then fills in from this same GET /atp/{id} data.

// value -> "Yes"/"No" badge for the API's various *Yn / Y|N fields.
function _acYn(value) {
  const isYes = String(value ?? "").toUpperCase() === "Y";
  return `<span class="ar-badge ar-badge--${isYes ? "yes" : "no"}">${isYes ? "Yes" : "No"}</span>`;
}

// A single label/value pair. `mono` renders the value in the
// monospace "code" treatment used for identifiers (names, ids,
// calendar/zone-group codes) rather than plain prose.
function _acDetailItem(label, value, { mono = false, full = false } = {}) {
  const valueHtml =
    value === undefined || value === null || value === ""
      ? `<span class="ar-detail-value ar-detail-value--muted">Not set</span>`
      : `<span class="ar-detail-value${mono ? " ar-detail-value--code" : ""}">${_acEscape(value)}</span>`;
  return `<div class="ar-detail-item${full ? " ar-detail-item--full" : ""}">
    <div class="ar-detail-label">${_acEscape(label)}</div>
    ${valueHtml}
  </div>`;
}

// A row of plain .ar-detail-chip pills for list values (roaming
// networks, usage types, derived services) — falls back to "Not set"
// rather than rendering an empty row.
function _acDetailChips(label, items) {
  const body = (items || []).length
    ? `<div class="ar-detail-chip-row">${items.map((t) => `<span class="ar-detail-chip">${_acEscape(t)}</span>`).join("")}</div>`
    : `<span class="ar-detail-value ar-detail-value--muted">Not set</span>`;
  return `<div class="ar-detail-item ar-detail-item--full">
    <div class="ar-detail-label">${_acEscape(label)}</div>
    ${body}
  </div>`;
}

// Card header used by every section below: icon (looked up from
// AC_DETAIL_SECTIONS by id, defined next to _acRenderView) + title,
// consistent across the whole view. `data-ar-section` on the outer
// .ar-card is what the sticky nav rail scrolls to and scroll-spies.
function _acCardTitle(sectionId, title) {
  const icon = AC_DETAIL_SECTIONS.find((s) => s.id === sectionId)?.icon || "info";
  return `<div class="ar-card-title"><span class="material-icons ar-card-title-icon">${icon}</span>${_acEscape(title)}</div>`;
}

function _acRenderAtpDetail(detail) {
  const d = detail || {};

  // ---- Overview ----
  const typeOfServiceLabel =
    { 1: "Rating", 2: "Billing" }[d.typeOfService] ?? d.typeOfService;
  const billingServiceTypeLabel = BILLING_SERVICE_TYPE_LABEL_MAP[d.billingServiceType]
    ?? d.billingServiceType;
  const categoryOfferCodeLabel = CATEGORY_OFFER_CODE_LABEL_MAP[d.categoryOfferCode]
    ?? d.categoryOfferCode;
  const ratingTypeLabel =
    { S: "Slab Based", N: "Non Slab Based" }[d.ratingType] ?? d.ratingType;

  const overviewCard = `
    <div class="ar-card" data-ar-section="overview">
      ${_acCardTitle("overview", "Overview")}
      <div class="ar-detail-grid">
        ${_acDetailItem("ATP Name", d.atpName, { mono: true })}
        ${_acDetailItem("Publicity ID", d.publicityId, { mono: true })}
        ${_acDetailItem("Rating Type", ratingTypeLabel)}
        ${_acDetailItem("Type of Service", typeOfServiceLabel)}
        ${d.typeOfService === 2 ? _acDetailItem("Billing Service Type", billingServiceTypeLabel) : ""}
        ${_acDetailItem("Category Offer Code", categoryOfferCodeLabel)}
        <div class="ar-detail-item"><div class="ar-detail-label">VIP Plan</div>${_acYn(d.vipPlanFlagYn)}</div>
      </div>
      ${d.description ? `<div class="ar-detail-grid"><div class="ar-detail-item ar-detail-item--full"><div class="ar-detail-label">Description</div><span class="ar-detail-value">${_acEscape(d.description)}</span></div></div>` : ""}
    </div>`;

  // ---- Validity & Scheduling ----
  const validityCard = `
    <div class="ar-card" data-ar-section="validity">
      ${_acCardTitle("validity", "Validity & Scheduling")}
      <div class="ar-detail-grid">
        ${_acDetailItem("Valid From", d.validFrom, { mono: true })}
        ${_acDetailItem("Valid To", d.validTo, { mono: true })}
        ${_acDetailItem("Validity Period Type", d.validityPeriodType === "LIMITED" ? "Limited" : "Unlimited")}
        ${d.validityPeriodType === "LIMITED" ? _acDetailItem("Validity Period Days", d.validityPeriodDays) : ""}
        ${_acDetailItem("Applicable From (Hr)", d.applicableFromHrs)}
        ${_acDetailItem("Applicable To (Hr)", d.applicableToHrs)}
      </div>
      <div class="ar-detail-grid">
        <div class="ar-detail-item"><div class="ar-detail-label">Roll Over</div>${_acYn(d.rollOverYn)}</div>
        <div class="ar-detail-item"><div class="ar-detail-label">Extend Validity</div>${_acYn(d.extendValidityYn)}</div>
      </div>
    </div>`;

  // ---- Roaming ----
  const roamingNetworkLabels = (d.roamingNetworks || []).map(
    (id) => ROAMING_NETWORK_LABEL_MAP[id] ?? id,
  );
  // Allow MTC/MOC/NLD MO/ILD MO only show up on the create form once
  // VOICE/SMS/MMS is picked, so only render them here if the API
  // actually returned a value for at least one — otherwise every ATP
  // would show a misleading "No" for fields that were never set.
  const hasVoiceRoamingFields =
    d.allowMtc != null || d.allowMoc != null || d.allowNldMo != null || d.allowIldMo != null;
  const roamingCard = `
    <div class="ar-card" data-ar-section="roaming">
      ${_acCardTitle("roaming", "Roaming")}
      <div class="ar-detail-grid">
        ${_acDetailChips("Roaming Networks", roamingNetworkLabels)}
        <div class="ar-detail-item"><div class="ar-detail-label">Allow National Roaming Data</div>${_acYn(d.allowNationalRoamingData)}</div>
        <div class="ar-detail-item"><div class="ar-detail-label">Allow International Roaming Data</div>${_acYn(d.allowInternationalRoamingData)}</div>
        ${hasVoiceRoamingFields ? `<div class="ar-detail-item"><div class="ar-detail-label">Allow MTC</div>${_acYn(d.allowMtc)}</div>` : ""}
        ${hasVoiceRoamingFields ? `<div class="ar-detail-item"><div class="ar-detail-label">Allow MOC</div>${_acYn(d.allowMoc)}</div>` : ""}
        ${hasVoiceRoamingFields ? `<div class="ar-detail-item"><div class="ar-detail-label">Allow NLD MO</div>${_acYn(d.allowNldMo)}</div>` : ""}
        ${hasVoiceRoamingFields ? `<div class="ar-detail-item"><div class="ar-detail-label">Allow ILD MO</div>${_acYn(d.allowIldMo)}</div>` : ""}
      </div>
    </div>`;

  // ---- SIM / IMSI Range Details ----
  // Wire format (see AtpDetailsService#buildSimRangeDetails on the
  // backend) is "<S|I>-<I|E>-<start>-<end>": SIM/IMSI flag first,
  // then Include/Exclude flag, then the range. Older records saved
  // before the per-row SIM/IMSI flag existed only have 3 parts
  // ("<I|E>-<start>-<end>") — those fall back to SIM rather than
  // losing the End value. Shown as compact "start → end" rows rather
  // than a field grid, since each pair reads as one unit.
  const simRangeRows = (d.simRangeDetails || [])
    .map((entry) => {
      const parts = String(entry).split("-");
      let mode, simType, start, end;
      if (parts.length >= 4) {
        [simType, mode, start, end] = parts;
      } else {
        [mode, start, end] = parts;
        simType = "S";
      }
      const isExclude = mode === "E";
      const isImsi = simType === "I";
      return `<div class="ar-detail-range-row">
        <span class="ar-detail-range-icon material-icons">${isImsi ? "sim_card" : "credit_card"}</span>
        <span class="ar-badge ar-badge--${isExclude ? "exclude" : "include"}">${isExclude ? "Exclude" : "Include"}</span>
        <span class="ar-badge">${isImsi ? "IMSI" : "SIM"}</span>
        <span class="ar-detail-value--code">${_acEscape(start)}</span>
        <span class="ar-detail-range-arrow material-icons">arrow_forward</span>
        <span class="ar-detail-value--code">${_acEscape(end)}</span>
      </div>`;
    })
    .join("");
  const simCard = `
    <div class="ar-card" data-ar-section="sim">
      ${_acCardTitle("sim", "SIM / IMSI Range Details")}
      ${simRangeRows || `<span class="ar-detail-value ar-detail-value--muted">No ranges</span>`}
    </div>`;

  // ---- Balance Categories ----
  const balanceCard = (d.balanceCategories || []).length
    ? `<div class="ar-card" data-ar-section="balance">
        ${_acCardTitle("balance", "Balance Categories")}
        ${(d.balanceCategories || [])
          .map((bc, i) => {
            const usageLabels = (bc.usageType || []).map(
              (id) => USAGE_TYPE_ID_TO_LABEL[id] ?? id,
            );
            return `
            <div class="ar-detail-subhead">${_acEscape(bc.balanceCategory)}</div>
            <div class="ar-detail-grid">
              ${_acDetailItem("Bucket Type", BUCKET_TYPE_LABEL_MAP[bc.bucketType] ?? bc.bucketType)}
              ${_acDetailItem("Unit Type", bc.bucketUnitType)}
              <div class="ar-detail-item"><div class="ar-detail-label">Unlimited Usage</div>${_acYn(bc.unlimitedUsageYn)}</div>
              ${bc.unlimitedUsageYn !== "Y" ? _acDetailItem("Unit Value", bc.bucketUnitValue, { mono: true }) : ""}
              ${_acDetailChips("Usage Types", usageLabels)}
            </div>`;
          })
          .join("")}
      </div>`
    : "";

  // ---- Derived Services ----
  // Group the flat "1~1" style codes back by category (voice/sms/
  // data/mms/global) purely for display, same grouping the create
  // form uses.
  const derivedByCategory = {};
  (d.derivedServiceSelections || []).forEach((code) => {
    const info = DERIVED_SERVICE_CODE_TO_INFO[code];
    if (!info) return;
    (derivedByCategory[info.category] ||= []).push(info.label);
  });
  const derivedCard = Object.keys(derivedByCategory).length
    ? `<div class="ar-card" data-ar-section="derived">
        ${_acCardTitle("derived", "Derived Services")}
        <div class="ar-detail-grid">
          ${Object.entries(derivedByCategory)
            .map(
              ([cat, labels]) =>
                _acDetailChips(DERIVED_SERVICE_CATEGORY_LABELS[cat] || cat, labels),
            )
            .join("")}
        </div>
      </div>`
    : "";

  // ---- Calendar Config & Zone Groups ----
  const zoneGroupChips = (d.zoneGroup || []).map((z) => {
    const opt = ZONE_GROUP_OPTIONS.find((o) => o.id === z.id);
    return { name: opt?.name ?? `#${z.id}`, restricted: z.ratingFlag === "N" };
  });
  const dataZoneGroupChips = (d.dataZoneGroupId || []).map((z) => {
    const opt = DATA_ZONE_GROUP_OPTIONS.find((o) => o.id === z.id);
    return { name: opt?.name ?? `#${z.id}`, restricted: z.ratingFlag === "N" };
  });
  const zoneChipRow = (chips) =>
    chips.length
      ? `<div class="ar-detail-chip-row">${chips
          .map(
            (c) =>
              `<span class="ar-badge ar-badge--${c.restricted ? "restricted" : "rating"}">${_acEscape(c.name)}</span>`,
          )
          .join("")}</div>`
      : `<span class="ar-detail-value ar-detail-value--muted">Not set</span>`;
  const calendarCard = `
    <div class="ar-card" data-ar-section="calendar">
      ${_acCardTitle("calendar", "Zone & Calendar Configuration")}
      <div class="ar-detail-grid">
        ${_acDetailItem("Calendar Config", CALENDAR_CONFIG_LABEL_MAP[d.calendarConfig] ?? d.calendarConfig)}
      </div>
      <div class="ar-detail-item ar-detail-item--full">
        <div class="ar-detail-label">Zone Group</div>
        ${zoneChipRow(zoneGroupChips)}
      </div>
      <div class="ar-detail-item ar-detail-item--full" style="margin-top:12px;">
        <div class="ar-detail-label">Data Zone Group</div>
        ${zoneChipRow(dataZoneGroupChips)}
      </div>
    </div>`;

  return [
    overviewCard,
    validityCard,
    simCard,
    roamingCard,
    balanceCard,
    derivedCard,
    calendarCard,
  ]
    .filter(Boolean)
    .join("");
}

// Opens the create/edit form in Modify mode and fills it in from
// GET /atp/{atpId} (same detail object the read-only view renders —
// reused from _acDetailCache when we already fetched it for that
// view, otherwise fetched fresh here). The save-side API still
// doesn't exist (see AtpDetailsController), so applyEditModeUI()
// (called via window._acApplyEditModeUI) keeps Publish disabled —
// this just gets the fields populated for reference/copy-editing.
async function _acModifyAtp(id) {
  const listed = _acAtps.find((a) => String(a.id) === String(id));

  function openForm(detail) {
    _acSelectedId = null;
    _acHighlightSelectedRow();

    const panel = document.getElementById("acFormPanel");
    panel.dataset.mode = "edit";
    panel.dataset.servicePackageId = id;

    const displayName = detail?.atpName || listed?.name || `ATP #${id}`;
    const title = panel.querySelector(".ar-form-header h3");
    const sub = panel.querySelector(".ar-form-header-sub");
    if (title) title.textContent = `Modify ATP — ${displayName}`;
    if (sub) {
      sub.textContent =
        "Update the tariff, validity and usage rules for this ATP";
    }

    document.getElementById("acPlaceholder").classList.add("hidden");
    document.getElementById("acViewPanel").classList.add("hidden");
    panel.classList.remove("hidden");

    if (typeof window._acPopulateForm === "function") {
      window._acPopulateForm(detail);
    }
    if (typeof window._acApplyEditModeUI === "function") {
      window._acApplyEditModeUI();
    }
  }

  if (_acDetailCache[id]) {
    openForm(_acDetailCache[id]);
    return;
  }

  try {
    const res = await fetch(`/atp/${id}`);
    if (res.ok) {
      const detail = await res.json();
      _acDetailCache[id] = detail;
      openForm(detail);
      return;
    }
    console.error(`GET /atp/${id} failed with status ${res.status}`);
  } catch (err) {
    console.error("Failed to load ATP details:", err);
  }

  if (typeof showToast === "function") {
    showToast("Couldn't load this ATP's details — opening a blank form.", "error");
  }
  openForm(null);
}

// ── Page init ──────────────────────────────────────────────────
document.addEventListener("DOMContentLoaded", () => {
  // This is its own standalone page (own nav node, own route), not an
  // in-workspace step — same reasoning as atp-rules.js / atp-rate.js,
  // which hide it the moment their overlay opens.
  const headerPill = document.querySelector(".header-pill-bar");
  if (headerPill) headerPill.style.display = "none";

  _acShowPlaceholder();
  _acLoadList();
});

// ============================================================
// The form-pane logic below (payload building, sim-range rows,
// checklist search/select-all, balance-category show/hide, submit)
// used to live in its own Atpcreate.js, loaded right after this
// file — merged here since the two always load together on this
// one page. Still wrapped in its own IIFE so its declarations
// don't collide with the list-pane globals above or with other
// page-global scripts loaded in layout.html.
// ============================================================
// ============================================================
// ATP Create / Modify form — /builder/atpcreate/new and
// /builder/atpcreate/edit/{servicePackageId}
// Wrapped in an IIFE so none of these declarations collide with
// other page-global scripts loaded in layout.html (payload-builder.js,
// atp-rate.js, etc. may declare similarly-named constants).
// ============================================================
(function () {
  const ATP_API_URL = "/atp/create";

  /* ---------- Small helpers ---------- */

  function toApiDate(isoDate) {
    if (!isoDate) return null;
    const [y, m, d] = isoDate.split("-");
    return `${m}/${d}/${y}`;
  }

  // Inverse of toApiDate() — "MM/DD/YYYY" (or "M/D/YYYY") from the
  // GET /atp/{id} response back to the "YYYY-MM-DD" a <input
  // type="date"> needs.
  function fromApiDate(apiDate) {
    if (!apiDate) return "";
    const [m, d, y] = apiDate.split("/");
    if (!m || !d || !y) return "";
    return `${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`;
  }

  function toYN(value) {
    const v = (value || "").toString().trim().toLowerCase();
    return v === "y" || v === "yes" ? "Y" : "N";
  }

  function capitalize(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  function num(value, fallback = 0) {
    const n = Number(value);
    return isNaN(n) ? fallback : n;
  }

  /* ---------- Section: Balance Category show/hide ---------- */

  function syncBalanceSections() {
    const checkboxes = document.querySelectorAll(
      '#balanceCategoryGroup input[type="checkbox"]',
    );
    let anyChecked = false;

    checkboxes.forEach((cb) => {
      const target = cb.dataset.unitTarget;
      const unitRow = document.querySelector(
        `.unit-row[data-unit-row="${target}"]`,
      );
      const derivedGroups = document.querySelectorAll(
        `.derived-group[data-derived-group="${target}"]`,
      );

      if (cb.checked) anyChecked = true;

      if (unitRow) unitRow.classList.toggle("is-hidden", !cb.checked);
      derivedGroups.forEach((g) =>
        g.classList.toggle("is-hidden", !cb.checked),
      );

      if (!cb.checked) {
        unitRow?.querySelectorAll("input, select").forEach((f) => {
          if (f.tagName === "SELECT") f.selectedIndex = 0;
          else f.value = "";
        });
        derivedGroups.forEach((g) =>
          g
            .querySelectorAll('input[type="checkbox"]')
            .forEach((i) => (i.checked = false)),
        );
      }
    });

    // The Unit Types & Values / Derived Services & Usage Types cards
    // shouldn't render at all — not even with an empty title — until
    // at least one balance category is picked.
    document
      .getElementById("unitTypesCard")
      ?.classList.toggle("is-hidden", !anyChecked);
    document
      .getElementById("derivedServicesCard")
      ?.classList.toggle("is-hidden", !anyChecked);

    // Zone Group (voice/sms) and Data Zone Group (data) live inside
    // the always-visible Calendar Config & Zone Groups card and
    // toggle independently of each other.
    const voiceOrSms = Array.from(checkboxes).some(
      (cb) =>
        (cb.dataset.unitTarget === "voice" ||
          cb.dataset.unitTarget === "sms") &&
        cb.checked,
    );
    const dataChecked = Array.from(checkboxes).some(
      (cb) => cb.dataset.unitTarget === "data" && cb.checked,
    );

    const zoneGroupField = document.getElementById("zoneGroupField");
    const dataZoneGroupField = document.getElementById("dataZoneGroupField");

    zoneGroupField?.classList.toggle("is-hidden", !voiceOrSms);
    dataZoneGroupField?.classList.toggle("is-hidden", !dataChecked);

    syncCalendarZoneCardVisibility();

    // Clear the checklist rather than leaving a stale selection once
    // a field is hidden again.
    if (!voiceOrSms) {
      document
        .querySelectorAll('#zoneGroupField input[name="zoneGroup"]')
        .forEach((cb) => (cb.checked = false));
    }
    if (!dataChecked) {
      document
        .querySelectorAll('#dataZoneGroupField input[name="dataZoneGroup"]')
        .forEach((cb) => (cb.checked = false));
    }

    // Roaming & Network Access: Allow National/International Roaming
    // Data only make sense for DATA; Allow MTC/MOC/NLD MO/ILD MO only
    // make sense for VOICE/SMS/MMS. Both groups show together when
    // both kinds of category are checked (e.g. Data + Voice).
    const voiceSmsMms = Array.from(checkboxes).some(
      (cb) =>
        ["voice", "sms", "mms"].includes(cb.dataset.unitTarget) && cb.checked,
    );
    document
      .querySelectorAll('[data-roaming-field="data"]')
      .forEach((field) => field.classList.toggle("is-hidden", !dataChecked));
    document
      .querySelectorAll('[data-roaming-field="voice"]')
      .forEach((field) =>
        field.classList.toggle("is-hidden", !voiceSmsMms),
      );

    // Layout of the roaming-fields grid depends on which combination
    // of groups is visible: 4-across when only Voice/SMS/MMS fields
    // are showing, 3-across when Data's fields join them, and a
    // centered 2-up layout when Data is the only group showing.
    const roamingGrid = document.getElementById("roamingAccessGrid");
    if (roamingGrid) {
      let layout = "";
      if (dataChecked && voiceSmsMms) layout = "both";
      else if (voiceSmsMms) layout = "voice-only";
      else if (dataChecked) layout = "data-only";
      roamingGrid.dataset.layout = layout;
    }

    if (!dataChecked) {
      document
        .querySelectorAll('[data-roaming-field="data"] select')
        .forEach((sel) => (sel.selectedIndex = 0));
    }
    if (!voiceSmsMms) {
      document
        .querySelectorAll('[data-roaming-field="voice"] select')
        .forEach((sel) => (sel.selectedIndex = 0));
    }

    syncMsDropdownLabels();
  }

  /* ---------- Section: Billing Service Type show/hide ---------- */

  function syncBillingServiceType() {
    const typeOfService = document.getElementById("typeOfService");
    const field = document.getElementById("billingServiceTypeField");
    const isBilling = typeOfService?.value === "2";

    field?.classList.toggle("is-hidden", !isBilling);

    if (!isBilling) {
      const billingServiceType = document.getElementById("billingServiceType");
      if (billingServiceType) billingServiceType.selectedIndex = 0;
    }

    // Calendar Config only applies to Rating ATPs — hidden for
    // Billing *and* while Type of Service hasn't been picked yet
    // (excluded from the payload too, see buildAtpPayload).
    const isRating = typeOfService?.value === "1";
    document
      .getElementById("calendarConfigField")
      ?.classList.toggle("is-hidden", !isRating);

    // Billing ATPs also only ever roam under a Restricted zone group
    // — hide the Rating (Y) group in both Zone Group and Data Zone
    // Group checklists and clear any Rating selection so a stale
    // pick doesn't get validated or submitted.
    document
      .querySelectorAll('[data-rating-checklist] [data-rating-flag="Y"]')
      .forEach((el) => el.classList.toggle("is-hidden", isBilling));
    if (isBilling) {
      document
        .querySelectorAll(
          '[data-rating-checklist] input[data-rating-flag="Y"]:checked',
        )
        .forEach((cb) => (cb.checked = false));
    }

    syncCalendarZoneCardVisibility();
  }

  // The Zone & Calendar Configuration card should only render once at
  // least one field inside it would actually show something: Calendar
  // Config (Rating ATPs only) or either zone-group checklist (needs a
  // balance category checked). Called from both syncBillingServiceType
  // (Type of Service changes) and syncBalanceSections (balance category
  // checkboxes change) since either can flip the answer.
  function syncCalendarZoneCardVisibility() {
    const isRating = document.getElementById("typeOfService")?.value === "1";
    const anyBalanceCategoryChecked = document.querySelector(
      '#balanceCategoryGroup input[type="checkbox"]:checked',
    );
    document
      .getElementById("calendarZoneCard")
      ?.classList.toggle("is-hidden", !(isRating || anyBalanceCategoryChecked));
  }

  /* ---------- Section: Validity Period Days show/hide ----------
     Only relevant — and only sent in the payload — when Validity
     Period Type is Limited. */

  function syncValidityPeriodDaysField() {
    const typeSelect = document.getElementById("validityPeriodType");
    const field = document.getElementById("validityPeriodDaysField");
    const isLimited = typeSelect?.value === "LIMITED";

    field?.classList.toggle("is-hidden", !isLimited);

    if (!isLimited) {
      const daysInput = document.getElementById("validityPeriodDays");
      if (daysInput) daysInput.value = "";
    }
  }

  /* ---------- Section: Unlimited Usage — lock unit type/value ----------
     Each balance category (voice/sms/data/mms) has its own Unlimited
     Usage flag now, living in that category's Unit Type & Value row.
     When a category's flag is Yes, that category's Unit Type / Unit
     Value fields are forced to fixed values and made non-editable.
     When it's No, they're reset back to their default (blank/first
     option) and made editable again — independent of every other
     category's flag. */

  const UNLIMITED_FIXED_UNITS = {
    voice: { unitType: "Sec", unitValue: "999999999" },
    sms: { unitType: "Msg", unitValue: "999999999" },
    data: { unitType: "Byt", unitValue: "999999999" },
    mms: { unitType: "Msg", unitValue: "999999999" },
  };

  function syncUnlimitedUsageFields() {
    Object.keys(UNLIMITED_FIXED_UNITS).forEach((cat) => {
      const flagField = document.getElementById(
        `unlimitedUsage${capitalize(cat)}`,
      );
      const typeField = document.getElementById(
        `unitType${capitalize(cat)}`,
      );
      const valueField = document.getElementById(
        `unitValue${capitalize(cat)}`,
      );
      if (!flagField || !typeField || !valueField) return;

      const isUnlimited = flagField.value === "Y";

      if (isUnlimited) {
        typeField.value = UNLIMITED_FIXED_UNITS[cat].unitType;
        valueField.value = UNLIMITED_FIXED_UNITS[cat].unitValue;
        typeField.disabled = true;
        valueField.disabled = true;
        typeField.classList.add("is-locked");
        valueField.classList.add("is-locked");
      } else if (typeField.classList.contains("is-locked")) {
        // Only clear/unlock when this category is actually coming out
        // of the locked (Unlimited=Y) state — checked via the
        // "is-locked" marker this function itself sets below, not the
        // generic .disabled flag. Edit-mode field locking (see
        // applyEditModeFieldLocks) also disables this same field for
        // an unrelated reason and must NOT be touched here, or
        // checking a different category would wrongly reset it.
        typeField.selectedIndex = 0;
        valueField.value = "";
        typeField.disabled = false;
        valueField.disabled = false;
        typeField.classList.remove("is-locked");
        valueField.classList.remove("is-locked");
      }
    });
  }

  function initSegmented() {
    document.querySelectorAll(".ar-segmented").forEach((group) => {
      const hiddenInputId = group.dataset.hiddenInput;
      const hiddenInput = hiddenInputId
        ? document.getElementById(hiddenInputId)
        : null;
      group.querySelectorAll("button").forEach((btn) => {
        btn.addEventListener("click", () => {
          group
            .querySelectorAll("button")
            .forEach((b) => b.classList.remove("active"));
          btn.classList.add("active");
          if (hiddenInput) hiddenInput.value = btn.dataset.value;
        });
      });
    });
  }

  /* ---------- Section: SIM Range Details repeater ---------- */

  function updateSimRangeRemoveState() {
    const rows = document.querySelectorAll("#simRangeList .sim-range-row");
    const soleRow = rows.length === 1 ? rows[0] : null;
    rows.forEach((row) => {
      const btn = row.querySelector(".ar-condition-remove");
      if (!btn) return;
      const isSole = row === soleRow;
      btn.classList.toggle("ar-condition-remove--disabled", isSole);
      btn.title = isSole
        ? "At least one range is required"
        : "Remove this range";
    });
  }

  function addSimRangeRow(prefill) {
    const list = document.getElementById("simRangeList");
    const row = document.createElement("div");
    row.className = "ar-condition-row sim-range-row";
    row.innerHTML = `
    <select class="sim-range-type">
      <option value="sim" ${prefill?.simType !== "imsi" ? "selected" : ""}>SIM</option>
      <option value="imsi" ${prefill?.simType === "imsi" ? "selected" : ""}>IMSI</option>
    </select>
    <select class="sim-range-mode">
      <option value="include" ${prefill?.mode !== "exclude" ? "selected" : ""}>Include</option>
      <option value="exclude" ${prefill?.mode === "exclude" ? "selected" : ""}>Exclude</option>
    </select>
    <input type="text" class="sim-range-start" placeholder="Range start e.g. 111111111111111" maxlength="20" inputmode="numeric" pattern="[0-9]*" minlength="15" title="15 to 20 digits" value="${prefill?.start || ""}">
    <input type="text" class="sim-range-end" placeholder="Range end e.g. 222222222222222" maxlength="20" inputmode="numeric" pattern="[0-9]*" minlength="15" title="15 to 20 digits" value="${prefill?.end || ""}">
    <button type="button" class="ar-condition-remove"><span class="material-icons">close</span></button>
  `;
    row.querySelector(".ar-condition-remove").addEventListener("click", () => {
      // The last remaining range can't be removed — there must always
      // be at least one row to edit.
      if (document.querySelectorAll("#simRangeList .sim-range-row").length <= 1) {
        return;
      }
      row.remove();
      updateSimRangeRemoveState();
    });
    list.appendChild(row);

    // Belt-and-braces on top of maxlength: strips anything non-numeric
    // and hard-caps at 20 digits as the user types or pastes, so a
    // paste of e.g. 30 digits (or non-digit junk) can't sneak past the
    // maxlength attribute.
    row
      .querySelectorAll(".sim-range-start, .sim-range-end")
      .forEach((input) => {
        input.addEventListener("input", () => {
          input.value = input.value.replace(/\D/g, "").slice(0, 20);
        });
      });

    updateSimRangeRemoveState();
  }

  // Format is "<S|I>-<I|E>-<start>-<end>" — S/I for SIM/IMSI first,
  // then I/E for Include/Exclude (matches AtpDetailsService#buildSimRangeDetails
  // on the backend). Both are chosen per row now (not once for the
  // whole list), so each row's own .sim-range-type carries its own
  // flag.
  function getSimRangeDetails() {
    return Array.from(document.querySelectorAll("#simRangeList .sim-range-row"))
      .map((row) => {
        const mode = row.querySelector(".sim-range-mode").value;
        const simType = row.querySelector(".sim-range-type").value;
        const start = row.querySelector(".sim-range-start").value.trim();
        const end = row.querySelector(".sim-range-end").value.trim();
        if (!start || !end) return null;
        return `${simType === "imsi" ? "I" : "S"}-${mode === "exclude" ? "E" : "I"}-${start}-${end}`;
      })
      .filter(Boolean);
  }

  /* ---------- Section: SIM Range Details validation ----------
     Mirrors the legacy IMSI/SIM range mapping validation
     (createRangeMapping() in rat_Bundle.js): Range Start/End are
     required, digits only, at least 15 digits, Range End must be >=
     Range Start, and the same Start-End pair can't be mapped twice —
     just adapted to this page's inline rows instead of the legacy
     "fill a staging row, click the arrow to add it to the list"
     pattern. (The legacy network-IMSI-series-prefix check isn't
     reproduced here — this page doesn't have that network config
     available client-side.) */
  function validateSimRanges() {
    const rows = Array.from(
      document.querySelectorAll("#simRangeList .sim-range-row"),
    );
    const seenRanges = new Set();
    let populatedCount = 0;

    for (const row of rows) {
      const startField = row.querySelector(".sim-range-start");
      const endField = row.querySelector(".sim-range-end");
      const start = startField.value.trim();
      const end = endField.value.trim();

      // Untouched row (Add Range was clicked but nothing entered yet)
      // — ignored, same as getSimRangeDetails() drops it.
      if (!start && !end) continue;

      if (!start) {
        flashInvalid(startField);
        flagError("Enter Range Start");
        startField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      if (!/^[0-9]+$/.test(start)) {
        flashInvalid(startField);
        flagError("Enter a valid Range Start");
        startField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      if (start.length < 15) {
        flashInvalid(startField);
        flagError("Range Start must be at least 15 digits");
        startField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }

      if (!end) {
        flashInvalid(endField);
        flagError("Enter Range End");
        endField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      if (!/^[0-9]+$/.test(end)) {
        flashInvalid(endField);
        flagError("Enter a valid Range End");
        endField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      if (end.length < 15) {
        flashInvalid(endField);
        flagError("Range End must be at least 15 digits");
        endField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }

      // Digits-only + 15-20 length is already guaranteed above, so a
      // plain BigInt comparison is safe even past Number's precision
      // limit (ranges can be up to 20 digits).
      if (BigInt(start) > BigInt(end)) {
        flashInvalid(endField);
        flagError("Range End must be greater than or equal to Range Start");
        endField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }

      const key = `${start}-${end}`;
      if (seenRanges.has(key)) {
        flashInvalid(startField);
        flashInvalid(endField);
        flagError("This range is already mapped");
        startField.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      seenRanges.add(key);
      populatedCount++;
    }

    if (populatedCount === 0) {
      flagError("Add at least one SIM / IMSI range");
      document
        .getElementById("simRangeList")
        ?.scrollIntoView({ behavior: "smooth", block: "center" });
      return false;
    }

    return true;
  }

  /* ---------- Section: checklist multi-select (roaming / derived / usage) ---------- */

  function initChecklistSearch() {
    document.querySelectorAll(".ar-checklist-search input").forEach((input) => {
      input.addEventListener("input", () => {
        const query = input.value.trim().toLowerCase();
        const body = input
          .closest(".ar-tagselect")
          .querySelector(".ar-checklist-body");
        body.querySelectorAll(".ar-check-row").forEach((row) => {
          const label = row.textContent.toLowerCase();
          row.style.display = label.includes(query) ? "" : "none";
        });
      });
    });

    document.querySelectorAll("[data-select-all]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const body = btn
          .closest(".ar-tagselect")
          .querySelector(".ar-checklist-body");
        body
          .querySelectorAll('.ar-check-row:not([style*="display: none"]) input')
          .forEach((i) => (i.checked = true));
      });
    });

    document.querySelectorAll("[data-deselect]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const body = btn
          .closest(".ar-tagselect")
          .querySelector(".ar-checklist-body");
        body.querySelectorAll("input").forEach((i) => (i.checked = false));
      });
    });
  }

  /* ---------- Section: Zone Group / Data Zone Group rating checklists ----------
     Both dropdowns share the same shape: static options with an id +
     ratingFlag (Y = rating zone group, N = restricted zone group),
     grouped under "Rating"/"Restricted" headers so the grouping still
     makes sense once the GET APIs return more than one of each.
     Rules (radio-like, enforced live below): only one restricted (N)
     option may be checked at a time, in both checklists; only one
     rating (Y) option may be checked in Zone Group, while Data Zone
     Group's rating (Y) options stay multi-select. */

  function renderRatingChecklist(fieldName, options) {
    const body = document.querySelector(
      `[data-rating-checklist="${fieldName}"]`,
    );
    if (!body) return;

    const groups = [
      { flag: "Y", heading: "Rating" },
      { flag: "N", heading: "Restricted" },
    ];

    body.innerHTML = "";
    groups.forEach(({ flag, heading }) => {
      const rows = options.filter((opt) => opt.ratingFlag === flag);
      if (rows.length === 0) return;

      const headingEl = document.createElement("div");
      headingEl.className = "ar-checklist-group-heading";
      headingEl.dataset.ratingFlag = flag;
      headingEl.textContent = heading;
      body.appendChild(headingEl);

      rows.forEach((opt) => {
        const label = document.createElement("label");
        label.className = "ar-check-row";
        label.dataset.ratingFlag = flag;
        label.innerHTML = `<input type="checkbox" name="${fieldName}" value="${opt.id}" data-rating-flag="${flag}" /> ${opt.name}`;
        body.appendChild(label);
      });
    });
  }

  // Enforces selection limits per checklist:
  // - Restricted (N): only one box can stay checked, in BOTH the
  //   Zone Group and Data Zone Group checklists.
  // - Rating (Y): only one box can stay checked in the Zone Group
  //   checklist; Data Zone Group's Rating boxes are unrestricted
  //   (multiple can stay checked).
  function initRatingZoneGroupExclusivity() {
    document
      .querySelectorAll("[data-rating-checklist]")
      .forEach((body) => {
        const fieldName = body.dataset.ratingChecklist;
        body.addEventListener("change", (e) => {
          const cb = e.target;
          if (!cb.matches('input[type="checkbox"]') || !cb.checked) return;

          const flag = cb.dataset.ratingFlag;
          const singleSelect =
            flag === "N" || (flag === "Y" && fieldName === "zoneGroup");
          if (!singleSelect) return;

          body
            .querySelectorAll(
              `input[type="checkbox"][data-rating-flag="${flag}"]`,
            )
            .forEach((other) => {
              if (other !== cb) other.checked = false;
            });
        });
      });
  }

  // Reads a rating checklist's checked boxes into the {id, ratingFlag}
  // pair shape the payload sends for both Zone Group and Data Zone
  // Group.
  function buildRatingFlagPairs(fieldName) {
    return Array.from(
      document.querySelectorAll(
        `[data-rating-checklist="${fieldName}"] input[type="checkbox"]:checked`,
      ),
    ).map((cb) => ({
      id: Number(cb.value),
      ratingFlag: cb.dataset.ratingFlag,
    }));
  }

  // Only enforced while the field is visible (see syncZoneGroupVisibility
  // etc. — hidden fields don't apply to the selected balance categories).
  // This only checks that at least one Rating and at least one
  // Restricted box are checked — it doesn't care how many, so it holds
  // whether the checklist enforces single-select or multi-select for
  // Rating (see initRatingZoneGroupExclusivity).
  function validateRatingChecklist(fieldName, fieldEl, label) {
    if (!fieldEl || fieldEl.classList.contains("is-hidden")) return true;

    const isBilling =
      document.getElementById("typeOfService")?.value === "2";
    const pairs = buildRatingFlagPairs(fieldName);
    const hasRating = pairs.some((p) => p.ratingFlag === "Y");
    const hasRestricted = pairs.some((p) => p.ratingFlag === "N");

    // Billing ATPs never show the Rating (Y) group (see
    // syncBillingServiceType), so only a Restricted pick is required.
    if (isBilling) {
      if (!hasRestricted) {
        flagError(`Select a Restricted ${label} option`);
        fieldEl.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
      return true;
    }

    if (!hasRating || !hasRestricted) {
      flagError(`Select one Rating and one Restricted ${label} option`);
      fieldEl.scrollIntoView({ behavior: "smooth", block: "center" });
      return false;
    }
    return true;
  }

  function validateZoneGroups() {
    if (
      !validateRatingChecklist(
        "zoneGroup",
        document.getElementById("zoneGroupField"),
        "Zone Group",
      )
    ) {
      return false;
    }
    if (
      !validateRatingChecklist(
        "dataZoneGroup",
        document.getElementById("dataZoneGroupField"),
        "Data Zone Group",
      )
    ) {
      return false;
    }
    return true;
  }

  /* ---------- Section: full-form required-field validation ----------
     Every field on this form is mandatory. On submit we walk it once,
     skipping anything currently hidden or disabled — a field that
     doesn't apply yet (e.g. Billing Service Type before "Billing" is
     picked, or the SMS unit row before "SMS" is checked under Balance
     Category) isn't demanded until it's actually shown. The first
     empty field found stops submission: it's flashed, focused, and
     both the status line and a toast explain what's missing. Zone
     Group / Data Zone Group and the SIM/IMSI ranges have their own
     richer rules (Rating+Restricted pairing, digit-length checks) and
     stay validated separately by validateZoneGroups() /
     validateSimRanges() rather than being re-checked here. */

  function fieldGroupLabel(container) {
    const label = container?.querySelector("label");
    if (!label) return "This field";
    return label.textContent.replace("*", "").trim() || "This field";
  }

  function validateAllRequiredFields() {
    const form = document.getElementById("atpCreateForm");
    if (!form) return true;

    // 1. Plain single-value fields — text/number/date inputs, selects,
    // and the description textarea. Requiring a `name` attribute
    // naturally excludes the little checklist search boxes, which
    // aren't real form fields.
    const singleValueFields = form.querySelectorAll(
      [
        '.ar-field input[name][type="text"]',
        '.ar-field input[name][type="number"]',
        '.ar-field input[name][type="date"]',
        ".ar-field select[name]",
        ".ar-field textarea[name]",
      ].join(", "),
    );
    for (const field of singleValueFields) {
      if (field.closest(".is-hidden") || field.disabled) continue;
      if (!field.value || !field.value.toString().trim()) {
        const fieldEl = field.closest(".ar-field");
        flagError(`${fieldGroupLabel(fieldEl)} is required`);
        flashInvalid(field);
        field.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
    }

    // 2. Balance Category — at least one pill checked.
    const balanceGroup = document.getElementById("balanceCategoryGroup");
    if (balanceGroup && !balanceGroup.closest(".is-hidden")) {
      if (!balanceGroup.querySelector('input[type="checkbox"]:checked')) {
        flagError("Select at least one Balance Category");
        balanceGroup.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
    }

    // 3. Every other checkbox checklist — derived services, usage
    // types, roaming networks. Zone Group / Data Zone Group carry
    // [data-rating-checklist] and are excluded here since
    // validateZoneGroups() already covers them with the
    // Rating+Restricted rule.
    const checklistBodies = form.querySelectorAll(
      ".ar-checklist-body:not([data-rating-checklist])",
    );
    for (const body of checklistBodies) {
      if (body.closest(".is-hidden")) continue;
      const boxes = body.querySelectorAll('input[type="checkbox"]');
      if (boxes.length === 0) continue;
      if (!body.querySelector('input[type="checkbox"]:checked')) {
        const heading = body.closest(".ar-tagselect")?.querySelector("label");
        const label = heading ? heading.textContent.trim() : "This checklist";
        flagError(`Select at least one option under ${label}`);
        body.scrollIntoView({ behavior: "smooth", block: "center" });
        return false;
      }
    }

    return true;
  }

  /* ---------- Section: multi-select dropdown (closed toggle that
     opens to reveal the checkbox checklist, e.g. Data Zone Group) --------- */

  function initMsDropdowns() {
    document.querySelectorAll(".ar-msdropdown").forEach((dropdown) => {
      const toggle = dropdown.querySelector(".ar-msdropdown-toggle");
      const panel = dropdown.querySelector(".ar-msdropdown-panel");
      const label = dropdown.querySelector(".ar-msdropdown-label");
      if (!toggle || !panel || !label) return;

      function updateLabel() {
        const checked = Array.from(
          panel.querySelectorAll('input[type="checkbox"]:checked'),
        );
        if (checked.length === 0) {
          label.textContent = "Select";
        } else if (checked.length === 1) {
          label.textContent = checked[0]
            .closest(".ar-check-row")
            .textContent.trim();
        } else {
          label.textContent = `${checked.length} selected`;
        }
      }

      function closeDropdown() {
        dropdown.classList.remove("is-open");
        panel.classList.add("is-hidden");
      }

      toggle.addEventListener("click", () => {
        const wasOpen = dropdown.classList.contains("is-open");
        document
          .querySelectorAll(".ar-msdropdown.is-open")
          .forEach((d) => d !== dropdown && d._msClose?.());
        if (wasOpen) {
          closeDropdown();
        } else {
          dropdown.classList.add("is-open");
          panel.classList.remove("is-hidden");
        }
      });

      // Select-all / deselect-all set .checked directly rather than
      // dispatching a "change" event, so the label needs its own
      // listener on those buttons too (deferred a frame so it runs
      // after the checklist's own click handler has updated .checked).
      panel.addEventListener("change", (e) => {
        if (e.target.matches('input[type="checkbox"]')) updateLabel();
      });
      panel
        .querySelectorAll("[data-select-all], [data-deselect]")
        .forEach((btn) => {
          btn.addEventListener("click", () =>
            requestAnimationFrame(updateLabel),
          );
        });

      dropdown._msClose = closeDropdown;
      dropdown._msUpdateLabel = updateLabel;
      updateLabel();
    });

    // Click outside any open dropdown closes it.
    document.addEventListener("click", (e) => {
      document.querySelectorAll(".ar-msdropdown.is-open").forEach((d) => {
        if (!d.contains(e.target)) d._msClose?.();
      });
    });
  }

  function syncMsDropdownLabels() {
    document
      .querySelectorAll(".ar-msdropdown")
      .forEach((d) => d._msUpdateLabel?.());
  }

  /* ---------- Section: Roaming Networks — "All Networks" is exclusive
     of the individual networks: picking it clears any specific network,
     and picking a specific network clears "All Networks". ---------- */

  function initRoamingNetworksExclusivity() {
    const body = document.getElementById("roamingNetworksBody");
    if (!body) return;

    const allBox = body.querySelector('input[value="all"]');
    const otherBoxes = Array.from(
      body.querySelectorAll('input[name="roamingNetworks"]:not([value="all"])'),
    );
    if (!allBox) return;

    allBox.addEventListener("change", () => {
      if (allBox.checked) {
        otherBoxes.forEach((box) => (box.checked = false));
      }
    });

    otherBoxes.forEach((box) => {
      box.addEventListener("change", () => {
        if (box.checked) allBox.checked = false;
      });
    });
  }

  /* ---------- Payload builder — matches the agreed API contract exactly ---------- */

  function buildAtpPayload() {
    const form = document.getElementById("atpCreateForm");
    const fd = new FormData(form);

    // Fields locked (disabled) in edit mode — see applyEditModeFieldLocks()
    // — drop out of FormData entirely, so their real value has to be read
    // straight off the DOM element instead. This works identically for
    // fields that aren't disabled too, so it's safe to use everywhere
    // instead of fd.get() for anything that might be locked.
    function fieldVal(name) {
      const el = form.querySelector(`[name="${name}"]`);
      return el ? el.value : fd.get(name);
    }

    const validFromIso = fd.get("validFrom");
    const validToIso = fd.get("validTo");

    /* Balance categories */
    const balanceCategories = [];
    document
      .querySelectorAll('#balanceCategoryGroup input[type="checkbox"]:checked')
      .forEach((cb) => {
        const cat = cb.dataset.unitTarget;
        const capsCat = cat.toUpperCase();
        const unlimitedUsageYn = toYN(
          fieldVal(`unlimitedUsage${capitalize(cat)}`),
        );

        // Unit Type / Unit Value are disabled (and so excluded from
        // FormData) when this category's own Unlimited Usage flag is
        // Yes — read the fixed values directly in that case instead
        // of from the form. (Also true — for a different reason — when
        // this row is locked in edit mode; fieldVal() below covers
        // that case by reading the DOM value directly regardless of
        // disabled state.)
        const unitTypeRaw =
          unlimitedUsageYn === "Y" && UNLIMITED_FIXED_UNITS[cat]
            ? UNLIMITED_FIXED_UNITS[cat].unitType
            : fieldVal(`unitType${capitalize(cat)}`);
        const unitValueRaw =
          unlimitedUsageYn === "Y" && UNLIMITED_FIXED_UNITS[cat]
            ? UNLIMITED_FIXED_UNITS[cat].unitValue
            : fd.get(`unitValue${capitalize(cat)}`);
        const usageTypeSlugs = fd.getAll(`usageType${capitalize(cat)}`);
        const usageTypeIds = usageTypeSlugs
          .map((slug) => USAGE_TYPE_ID_MAP[cat]?.[slug])
          .filter((id) => id !== undefined);

        balanceCategories.push({
          balanceCategory: capsCat,
          // Only present in edit mode, stashed on the pill checkbox
          // by _acPopulateForm() from the GET-by-id response's
          // bucketId — a fresh create has nothing to send here, so
          // this stays null and the backend assigns a new bucket.
          bucketId: cb.dataset.bucketId || null,
          bucketType: BUCKET_TYPE_MAP[fieldVal("bucketType")] || null,
          usageType: usageTypeIds,
          bucketUnitType:
            unitTypeRaw && unitTypeRaw !== "-1" ? unitTypeRaw : null,
          bucketUnitValue: unitValueRaw ? Number(unitValueRaw) : null,
          unlimitedUsageYn,
        });
      });

    /* Derived service selections — flattened across categories */
    const derivedServiceSelections = [];
    ["voice", "sms", "data", "mms", "global"].forEach((cat) => {
      fd.getAll(`derived${capitalize(cat)}`).forEach((slug) => {
        const id = DERIVED_SERVICE_ID_MAP[cat]?.[slug];
        if (id) derivedServiceSelections.push(id);
      });
    });

    /* Roaming networks — FTL / India send their integer network ID;
       "All Networks" (exclusive of the individual ones — see
       initRoamingNetworksExclusivity) has no numeric ID and stays as
       the uppercase string it always was. */
    /* Roaming & Network Access flags — only meaningful, and only
       sent, when their respective balance category is selected
       (see syncBalanceSections, which hides+resets these fields
       otherwise). Checked straight off the checkboxes rather than
       FormData, since FormData still reports the hidden select's
       default option value even while the field itself is hidden. */
    const dataCategoryChecked = document.querySelector(
      '#balanceCategoryGroup input[data-unit-target="data"]:checked',
    );
    const voiceSmsMmsCategoryChecked = document.querySelector(
      '#balanceCategoryGroup input[data-unit-target="voice"]:checked, ' +
        '#balanceCategoryGroup input[data-unit-target="sms"]:checked, ' +
        '#balanceCategoryGroup input[data-unit-target="mms"]:checked',
    );

    const roamingNetworks = fd
      .getAll("roamingNetworks")
      .map((v) => ROAMING_NETWORK_ID_MAP[v] ?? v.toUpperCase());

    /* SIM / IMSI range details */
    const simRangeDetails = getSimRangeDetails();

    const networkId =
      typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : null;
    const createdBy =
      typeof USERNAME !== "undefined" && USERNAME ? USERNAME : "ADMIN";

    return {
      networkId: num(networkId, 1),
      createdBy: createdBy,

      atpName: fieldVal("atpName"),
      typeOfService: fieldVal("typeOfService")
        ? num(fieldVal("typeOfService"))
        : null,
      billingServiceType:
        fieldVal("typeOfService") === "2"
          ? num(fd.get("billingServiceType"))
          : null,
      categoryOfferCode: fd.get("categoryOfferCode") || null,
      // vipPlan's own option values are already "Y"/"N" (unlike the
      // yes/no selects toYN() is for) — wrapping it in toYN() was
      // turning a selected "Yes" into "N".
      vipPlanFlagYn: fd.get("vipPlan") || "N",
      ratingType: document.getElementById("ratingType").value || null,
      description: fd.get("description"),
      publicityId: fieldVal("publicityId"),

      validFrom: toApiDate(validFromIso),
      validTo: toApiDate(validToIso),
      validityPeriodType:
        (fd.get("validityPeriodType") || "").toUpperCase() || null,
      // Only meaningful — and only sent — when validityPeriodType is
      // LIMITED; the input itself is hidden otherwise (see
      // syncValidityPeriodDaysField).
      validityPeriodDays:
        (fd.get("validityPeriodType") || "").toUpperCase() === "LIMITED" &&
        fd.get("validityPeriodDays")
          ? Number(fd.get("validityPeriodDays"))
          : null,
      // effectiveEndDate field removed
      // effectiveEndDate: toApiDate(fd.get("effectiveEndDate")),
      applicableFromHrs:
        fd.get("hoursFrom") !== "" ? Number(fd.get("hoursFrom")) : null,
      applicableToHrs:
        fd.get("hoursTo") !== "" ? Number(fd.get("hoursTo")) : null,

      rollOverYn: toYN(fd.get("rollOver")),
      extendValidityYn: toYN(fd.get("extendValidity")),

      roamingNetworks: roamingNetworks,
      // These four are only shown/collected when the relevant balance
      // category is selected (see syncBalanceSections) — sent as null,
      // not "N", when that category isn't checked, so the backend can
      // tell "explicitly No" apart from "not applicable to this ATP".
      allowNationalRoamingData: dataCategoryChecked
        ? toYN(fd.get("allowNationalRoamingData"))
        : null,
      allowInternationalRoamingData: dataCategoryChecked
        ? toYN(fd.get("allowInternationalRoamingData"))
        : null,
      allowMtc: voiceSmsMmsCategoryChecked ? toYN(fd.get("allowMtc")) : null,
      allowMoc: voiceSmsMmsCategoryChecked ? toYN(fd.get("allowMoc")) : null,
      allowNldMo: voiceSmsMmsCategoryChecked
        ? toYN(fd.get("allowNldMo"))
        : null,
      allowIldMo: voiceSmsMmsCategoryChecked
        ? toYN(fd.get("allowIldMo"))
        : null,
      // SIM vs IMSI is chosen per row now — see getSimRangeDetails(),
      // each entry carries its own "S"/"I" flag.
      simRangeDetails: simRangeDetails,

      balanceCategories: balanceCategories,
      derivedServiceSelections: derivedServiceSelections,

      // billingServiceType/calendarConfig/zoneGroup/dataZoneGroupId
      // aren't part of the agreed sample payload, but the form has
      // real inputs for all of them, so they're still sent. Fields
      // that ARE in the sample but have no corresponding input on
      // this form (zoneGroupId, rentalAmount, activationCharge,
      // serviceDuration, rentalPeriod) are deliberately left out
      // rather than sent as guessed/hardcoded values — add them here
      // once this form grows inputs for them.
      // calendarConfig only applies to Rating ATPs — hidden (and
      // excluded here) for anything else via syncBillingServiceType,
      // so it's sent as null rather than whatever stale value the
      // hidden select still holds.
      // NOTE: must use fieldVal(), not fd.get(), here — typeOfService is
      // disabled in edit mode (see applyEditModeFieldLocks), so fd.get()
      // returns null for it during modify even when it's actually "1",
      // which was causing calendarConfig to always be sent as null on
      // modify. fieldVal() reads straight off the DOM and works in both
      // create and edit mode.
      calendarConfig:
        fieldVal("typeOfService") === "1"
          ? fd.get("calendarConfig") || null
          : null,
      // Each entry is {id, ratingFlag} — see buildRatingFlagPairs.
      zoneGroup: buildRatingFlagPairs("zoneGroup"),
      dataZoneGroupId: buildRatingFlagPairs("dataZoneGroup"),
    };
  }

  // Dev helper: the Publish button is disabled in edit mode (see
  // applyEditModeUI), so it never fires a "submit" event to log
  // through — a disabled button can't trigger form submission at
  // all, no matter what the submit handler does. This bypasses the
  // button entirely: run `_acDebugAtpPayload()` in the browser
  // console at any time (create or edit mode) to build the payload
  // off the form's current values and log it, without touching the
  // network. Not called from anywhere in this file automatically.
  window._acDebugAtpPayload = function () {
    const payload = buildAtpPayload();
    console.log("🔍 [debug] Current form payload:", payload);
    return payload;
  };

  /* ---------- API call ---------- */

  // Pulls a human-readable message out of an error response from the
  // Java backend (com.xius.Lb / com.xius.TariffBuilder). Two shapes show
  // up depending on which exception handler fired (see
  // GlobalExceptionHandler):
  //   - ErrorResponse (IllegalArgumentException/IllegalStateException,
  //     non-2xx): { timestamp, status, error, message, path }
  //   - the generic map (TariffInsertException, bad request body, NPE,
  //     etc. — returned with HTTP 200): { status: "error", message, reason }
  function getApiErrorMessage(result) {
    return (
      result?.message ||
      result?.reason ||
      result?.error ||
      "Something went wrong while saving the ATP."
    );
  }

  // Pulls the IDs worth surfacing to the user out of a successful
  // AtpResponse — { atpId, bundleId, bucketIds, servicePlanIds,
  // tariffPlanId, message } — so the success toast can show something
  // useful instead of the raw JSON blob. Returns null if nothing worth
  // showing was found.
  function getApiSuccessSummary(result) {
    const parts = [];

    if (result?.atpId != null) {
      parts.push(`ATP ID ${result.atpId}`);
    }
    if (result?.tariffPlanId != null) {
      parts.push(`Tariff Plan ID ${result.tariffPlanId}`);
    }
    if (Array.isArray(result?.servicePlanIds) && result.servicePlanIds.length) {
      parts.push(
        `Service Plan${result.servicePlanIds.length > 1 ? "s" : ""} ${result.servicePlanIds.join(", ")}`,
      );
    }
    if (result?.bundleId != null) {
      parts.push(`Bundle ID ${result.bundleId}`);
    }
    if (Array.isArray(result?.bucketIds) && result.bucketIds.length) {
      parts.push(
        `Bucket${result.bucketIds.length > 1 ? "s" : ""} ${result.bucketIds.join(", ")}`,
      );
    }

    return parts.length ? parts.join(" · ") : null;
  }

  // Same idea as getApiSuccessSummary, but for the shape PUT
  // /atp/modify/{atpId} returns (AtpModifyResponse) — added/updated/
  // removed counts per bucket, service plan, and derived-service
  // selection, instead of the flat ID lists a create response has.
  function getModifySuccessSummary(result) {
    const parts = [];

    const bucketBits = [];
    if (result?.addedBucketIds?.length) bucketBits.push(`${result.addedBucketIds.length} added`);
    if (result?.updatedBucketIds?.length) bucketBits.push(`${result.updatedBucketIds.length} updated`);
    if (result?.removedBucketIds?.length) bucketBits.push(`${result.removedBucketIds.length} removed`);
    if (bucketBits.length) parts.push(`Buckets: ${bucketBits.join(", ")}`);

    const planBits = [];
    if (result?.addedServicePlanIds?.length) planBits.push(`${result.addedServicePlanIds.length} added`);
    if (result?.updatedServicePlanIds?.length) planBits.push(`${result.updatedServicePlanIds.length} updated`);
    if (result?.removedServicePlanIds?.length) planBits.push(`${result.removedServicePlanIds.length} removed`);
    if (planBits.length) parts.push(`Service Plans: ${planBits.join(", ")}`);

    const derivedBits = [];
    if (result?.addedDerivedServiceSelections?.length)
      derivedBits.push(`${result.addedDerivedServiceSelections.length} added`);
    if (result?.removedDerivedServiceSelections?.length)
      derivedBits.push(`${result.removedDerivedServiceSelections.length} removed`);
    if (derivedBits.length) parts.push(`Derived Services: ${derivedBits.join(", ")}`);

    return parts.length ? parts.join(" · ") : null;
  }

  async function callAtpApi() {
    const payload = buildAtpPayload();
    // Edit mode hits PUT /atp/modify/{atpId} (AtpController#modifyAtp)
    // with the same payload shape the create form already builds;
    // create mode keeps hitting POST /atp/create. The service-package
    // ID is read off #acFormPanel's dataset, set by _acModifyAtp()
    // when it opens the form in edit mode.
    const editId = isEditMode()
      ? document.querySelector(".atpc-fullscreen")?.dataset.servicePackageId
      : null;
    const url = editId ? `/atp/modify/${editId}` : ATP_API_URL;
    const method = editId ? "PUT" : "POST";
    console.log(`📤 [${method}] ${url}`, payload);

    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errBody = await response.text().catch(() => "");
      // GlobalExceptionHandler can send its error shape on a non-2xx
      // status too (IllegalArgumentException -> 400, IllegalStateException
      // -> 500), so try to parse it and reuse the same extraction —
      // otherwise this branch was dumping the raw JSON body straight
      // into the toast.
      let parsedErrBody = null;
      try {
        parsedErrBody = errBody ? JSON.parse(errBody) : null;
      } catch {
        parsedErrBody = null;
      }
      throw new Error(
        parsedErrBody
          ? getApiErrorMessage(parsedErrBody)
          : `API error ${response.status}. Please try again.`,
      );
    }

    const result = await response.json().catch(() => ({}));
    // Some backend exception handlers (TariffInsertException, bad
    // request body, NPE, generic RuntimeException/Exception) return
    // HTTP 200 with { status: "error", message, reason } instead of a
    // non-2xx status — catch that here too.
    if (result.status === "error") {
      throw new Error(getApiErrorMessage(result));
    }
    return result;
  }

  /* ---------- Status + button loading helpers ---------- */

  function markStatus(state, label) {
    const dot = document.getElementById("statusDot");
    const text = document.getElementById("statusText");
    if (dot) dot.className = "status-dot" + (state ? ` is-${state}` : "");
    if (text) text.textContent = label;
  }

  // Every validation failure on this form should be impossible to
  // miss — not just the small status-line text, but a toast too.
  // Shared by the required-field pass below and the existing SIM
  // range / zone group validators so all of it looks consistent.
  function flagError(message) {
    markStatus(null, message);
    if (typeof showToast === "function") {
      showToast(message, "error");
    }
  }

  function setBtnLoading(btn, isLoading, loadingLabel) {
    if (!btn) return;
    if (isLoading) {
      btn.dataset.originalText = btn.textContent;
      btn.disabled = true;
      btn.textContent = loadingLabel;
    } else {
      btn.disabled = false;
      if (btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
    }
  }

  function flashInvalid(field) {
    field.style.borderColor = "#dc2626";
    field.addEventListener(
      "input",
      function clear() {
        field.style.borderColor = "";
        field.removeEventListener("input", clear);
      },
      { once: true },
    );
    field.focus();
  }

  /* ---------- Modify mode ----------
     Opened via the "Modify" button on the read-only ATP view
     (_acModifyAtp in the list-pane script above), which sets
     data-mode="edit" + data-service-package-id on #acFormPanel
     (.atpc-fullscreen) and calls window._acPopulateForm(detail) with
     the GET /atp/{id} response to fill this form in. Publish now
     hits PUT /atp/modify/{atpId} (see callAtpApi) instead of being
     disabled — AtpController#modifyAtp is implemented. */
  function isEditMode() {
    return (
      document.querySelector(".atpc-fullscreen")?.dataset.mode === "edit"
    );
  }

  /* ---------- Edit-mode field locking ----------
     A handful of fields can't be changed once an ATP exists, since the
     backend treats them as identity/creation-time-only:
       - atpName, publicityId, typeOfService: ATP-level, immutable.
       - bucketType: one shared field across every category on the
         ATP (see populateAtpCreateForm) — no per-row concept of "new"
         applies to it, so it's locked outright in edit mode.
       - Unlimited Usage / Unit Type: locked per balance-category row,
         but ONLY for a row that already has a bucket on this ATP
         (data-bucket-id, stashed on the pill checkbox by
         populateAtpCreateForm). A category checked *after* opening
         the modify form has no bucketId yet — it's a brand-new bucket
         being created alongside the modify, not an existing one being
         edited in place — so it stays fully editable.
     Unit Value is deliberately NOT locked; only the type and the
     unlimited flag are ATP-modify-time-immutable.

     Locked <select> elements are set `disabled` (readonly isn't
     supported on <select>). buildAtpPayload()'s fieldVal() helper
     reads locked fields straight off the DOM instead of FormData, so
     their real value still makes it into the payload despite being
     disabled — see the comment there. */
  function applyEditModeFieldLocks() {
    if (!isEditMode()) return;

    const atpNameField = document.getElementById("atpName");
    if (atpNameField) atpNameField.readOnly = true;

    const publicityIdField = document.querySelector('[name="publicityId"]');
    if (publicityIdField) publicityIdField.readOnly = true;

    const typeOfServiceField = document.getElementById("typeOfService");
    if (typeOfServiceField) typeOfServiceField.disabled = true;

    const bucketTypeField = document.querySelector('[name="bucketType"]');
    if (bucketTypeField) bucketTypeField.disabled = true;

    document
      .querySelectorAll("#balanceCategoryGroup input[data-unit-target]")
      .forEach((cb) => {
        const capCat = capitalize(cb.dataset.unitTarget);
        const hasExistingBucket = !!cb.dataset.bucketId;

        const unlimitedField = document.getElementById(
          `unlimitedUsage${capCat}`,
        );
        const typeField = document.getElementById(`unitType${capCat}`);

        if (unlimitedField) {
          unlimitedField.disabled = hasExistingBucket;
        }

        // Don't fight the separate "Unlimited=Y locks the unit type to
        // a fixed value" mechanism (syncUnlimitedUsageFields /
        // populateAtpCreateForm's is-locked branch, which "is-locked"
        // is reserved for exclusively) — a row that's already locked
        // that way stays locked regardless, and this never touches
        // its "is-locked" class.
        if (typeField && !typeField.classList.contains("is-locked")) {
          typeField.disabled = hasExistingBucket;
        }
      });
  }

  /* Reverses applyEditModeFieldLocks() — called from
     resetAtpCreateForm() so a fresh "New ATP" doesn't inherit locks
     left over from whatever ATP was last opened for edit. */
  function clearEditModeFieldLocks() {
    const atpNameField = document.getElementById("atpName");
    if (atpNameField) atpNameField.readOnly = false;

    const publicityIdField = document.querySelector('[name="publicityId"]');
    if (publicityIdField) publicityIdField.readOnly = false;

    const typeOfServiceField = document.getElementById("typeOfService");
    if (typeOfServiceField) typeOfServiceField.disabled = false;

    const bucketTypeField = document.querySelector('[name="bucketType"]');
    if (bucketTypeField) bucketTypeField.disabled = false;

    document
      .querySelectorAll("#balanceCategoryGroup input[data-unit-target]")
      .forEach((cb) => {
        const capCat = capitalize(cb.dataset.unitTarget);
        const unlimitedField = document.getElementById(
          `unlimitedUsage${capCat}`,
        );
        const typeField = document.getElementById(`unitType${capCat}`);

        if (unlimitedField) {
          unlimitedField.disabled = false;
        }
        // Leave "is-locked" rows alone here too — that's the
        // Unlimited=Y auto-lock's field to own, not this function's.
        if (typeField && !typeField.classList.contains("is-locked")) {
          typeField.disabled = false;
        }
      });
  }

  function applyEditModeUI() {
    if (!isEditMode()) return;

    const publishLabel = document.getElementById("atpCreatePublishLabel");
    if (publishLabel) publishLabel.textContent = "Save Changes";

    applyEditModeFieldLocks();
  }
  // Exposed so _acModifyAtp() (list-pane script above) can re-apply
  // this after switching #acFormPanel into edit mode — the
  // DOMContentLoaded call below only covers the initial page load.
  window._acApplyEditModeUI = applyEditModeUI;

  /* ---------- Reset for reuse ----------
     This page keeps the create form mounted for the whole visit — the
     "New ATP" button in atpcreate.html just switches which panel is
     visible rather than reloading the page — so each click needs to
     explicitly put the form back to a blank slate. Exposed on window
     so _acShowForm() above can call it. */
  function resetAtpCreateForm() {
    const form = document.getElementById("atpCreateForm");
    if (!form) return;
    form.reset();

    // Clear any edit-mode field locks (disabled/readonly) left over
    // from whatever ATP was last opened for modify — see
    // applyEditModeFieldLocks() / _acApplyEditModeUI.
    clearEditModeFieldLocks();

    // Clear any bucketId stashed on the pill checkboxes by a previous
    // edit-mode populate (see _acPopulateForm) — form.reset() doesn't
    // touch dataset attributes, so without this a stale bucketId from
    // whatever ATP was last opened for edit would silently leak into
    // the next fresh create's payload.
    document
      .querySelectorAll("#balanceCategoryGroup input[data-bucket-id]")
      .forEach((cb) => delete cb.dataset.bucketId);

    // Segmented toggles (Rating Type, Rental Type): form.reset() already
    // restored the hidden input to its default value attribute — just
    // re-sync the visible active button to match.
    document.querySelectorAll(".ar-segmented[data-hidden-input]").forEach((group) => {
      const hiddenInput = document.getElementById(group.dataset.hiddenInput);
      const current = hiddenInput ? hiddenInput.value : null;
      group.querySelectorAll("button").forEach((b) => {
        b.classList.toggle("active", b.dataset.value === current);
      });
    });

    // Dynamically-added SIM/IMSI range rows don't reset on their own.
    const simList = document.getElementById("simRangeList");
    if (simList) simList.innerHTML = "";
    addSimRangeRow();

    // Hide every unit-row / derived-group again now that all balance
    // category checkboxes are back to unchecked.
    syncBalanceSections();
    syncBillingServiceType();
    syncValidityPeriodDaysField();

    // Close any open multi-select dropdown and reset its label back
    // to "Select" now that form.reset() cleared its checkboxes.
    document
      .querySelectorAll(".ar-msdropdown.is-open")
      .forEach((d) => d._msClose?.());
    syncMsDropdownLabels();

    markStatus(null, "");
  }
  window._acResetForm = resetAtpCreateForm;

  /* ---------- Populate from GET /atp/{id} (Modify mode) ----------
     Inverse of buildAtpPayload() above — takes the AtpDetailsResponse
     fetched by _acModifyAtp() (list-pane script) and fills every form
     field from it, using the same *_MAP tables buildAtpPayload uses
     to go the other way, so the two stay in sync. Exposed on window
     so _acModifyAtp() can call it once the form panel is visible.
     `detail` may be null (detail fetch failed) — in that case this
     just resets the form to blank and stops. */
  function populateAtpCreateForm(detail) {
    const form = document.getElementById("atpCreateForm");
    if (!form) return;

    resetAtpCreateForm();
    if (!detail) return;

    function setField(name, value) {
      const el = form.elements.namedItem(name);
      if (!el) return;
      el.value = value === null || value === undefined ? "" : String(value);
    }

    /* ---- ATP Details ---- */
    setField("atpName", detail.atpName);
    setField("publicityId", detail.publicityId);
    setField("description", detail.description);
    setField("categoryOfferCode", detail.categoryOfferCode);
    setField("vipPlan", detail.vipPlanFlagYn || "N");

    setField(
      "typeOfService",
      detail.typeOfService != null ? detail.typeOfService : "",
    );
    syncBillingServiceType();
    if (detail.typeOfService === 2) {
      setField(
        "billingServiceType",
        detail.billingServiceType != null ? detail.billingServiceType : "",
      );
    }

    // Rating Type segmented toggle
    const ratingTypeHidden = document.getElementById("ratingType");
    if (ratingTypeHidden) {
      ratingTypeHidden.value = detail.ratingType || "N";
      document
        .querySelectorAll('.ar-segmented[data-hidden-input="ratingType"] button')
        .forEach((b) => {
          b.classList.toggle("active", b.dataset.value === ratingTypeHidden.value);
        });
    }

    /* ---- Validity & Scheduling ---- */
    setField("validFrom", fromApiDate(detail.validFrom));
    setField("validTo", fromApiDate(detail.validTo));
    setField("validityPeriodType", detail.validityPeriodType || "LIMITED");
    syncValidityPeriodDaysField();
    if ((detail.validityPeriodType || "").toUpperCase() === "LIMITED") {
      setField(
        "validityPeriodDays",
        detail.validityPeriodDays != null ? detail.validityPeriodDays : "",
      );
    }
    setField(
      "hoursFrom",
      detail.applicableFromHrs != null ? detail.applicableFromHrs : "",
    );
    setField(
      "hoursTo",
      detail.applicableToHrs != null ? detail.applicableToHrs : "",
    );

    setField("rollOver", detail.rollOverYn || "N");
    setField("extendValidity", detail.extendValidityYn || "N");

    /* ---- Balance & Usage Configuration / Unit Types & Values /
       Derived Services — driven off detail.balanceCategories. ---- */
    (detail.balanceCategories || []).forEach((entry) => {
      const catUpper = (entry.balanceCategory || "").toUpperCase();
      const cat = catUpper.toLowerCase();
      if (!cat) return;

      const pillBox = document.querySelector(
        `#balanceCategoryGroup input[data-unit-target="${cat}"]`,
      );
      if (pillBox) {
        pillBox.checked = true;
        // bucketId has no form input of its own — it's backend-assigned,
        // never user-entered — so stash it straight on the pill checkbox
        // here and read it back off the same element in buildAtpPayload().
        // Only the GET-by-id response (edit mode) carries it; a fresh
        // create has nothing to stash, so this stays unset there and
        // buildAtpPayload() sends bucketId: null as before.
        if (entry.bucketId != null) {
          pillBox.dataset.bucketId = entry.bucketId;
        } else {
          delete pillBox.dataset.bucketId;
        }
      }

      // Bucket Type is one shared field across every category in
      // this form — take it from whichever entry actually has one.
      if (entry.bucketType) {
        const slug = Object.entries(BUCKET_TYPE_MAP).find(
          ([, code]) => code === entry.bucketType,
        )?.[0];
        if (slug) setField("bucketType", slug);
      }

      const capCat = capitalize(cat);
      const unlimitedYn = entry.unlimitedUsageYn === "Y" ? "Y" : "N";
      setField(`unlimitedUsage${capCat}`, unlimitedYn);

      const typeField = document.getElementById(`unitType${capCat}`);
      const valueField = document.getElementById(`unitValue${capCat}`);
      const isUnlimited = unlimitedYn === "Y" && UNLIMITED_FIXED_UNITS[cat];
      if (isUnlimited) {
        // Mirrors syncUnlimitedUsageFields()'s locked-field branch.
        if (typeField) {
          typeField.value = UNLIMITED_FIXED_UNITS[cat].unitType;
          typeField.disabled = true;
          typeField.classList.add("is-locked");
        }
        if (valueField) {
          valueField.value = UNLIMITED_FIXED_UNITS[cat].unitValue;
          valueField.disabled = true;
          valueField.classList.add("is-locked");
        }
      } else {
        if (typeField) {
          typeField.value = entry.bucketUnitType || "";
          typeField.disabled = false;
          typeField.classList.remove("is-locked");
        }
        if (valueField) {
          valueField.value =
            entry.bucketUnitValue != null ? entry.bucketUnitValue : "";
          valueField.disabled = false;
          valueField.classList.remove("is-locked");
        }
      }

      // Usage type checklist — response carries the numeric ids,
      // checkboxes are keyed by slug, so reverse USAGE_TYPE_ID_MAP.
      const idToSlug = {};
      Object.entries(USAGE_TYPE_ID_MAP[cat] || {}).forEach(([slug, id]) => {
        idToSlug[id] = slug;
      });
      (entry.usageType || []).forEach((id) => {
        const slug = idToSlug[id];
        if (!slug) return;
        const cb = form.querySelector(
          `input[name="usageType${capCat}"][value="${slug}"]`,
        );
        if (cb) cb.checked = true;
      });
    });

    // Reveal the Unit Types & Values / Derived Services cards and
    // the Zone Group / Data Zone Group fields for whichever
    // categories just got checked above.
    syncBalanceSections();

    // Derived service selections — flattened codes like "1~1" back
    // to their {category, slug} checkbox.
    (detail.derivedServiceSelections || []).forEach((code) => {
      for (const [category, slugs] of Object.entries(DERIVED_SERVICE_ID_MAP)) {
        const slug = Object.keys(slugs).find((s) => slugs[s] === code);
        if (!slug) continue;
        const cb = form.querySelector(
          `input[name="derived${capitalize(category)}"][value="${slug}"]`,
        );
        if (cb) cb.checked = true;
        break;
      }
    });

    /* ---- Roaming & Network Access ---- */
    const roamingIdToSlug = {};
    Object.entries(ROAMING_NETWORK_ID_MAP).forEach(([slug, id]) => {
      roamingIdToSlug[id] = slug;
    });
    (detail.roamingNetworks || []).forEach((entry) => {
      const box = form.querySelector(
        String(entry).toUpperCase() === "ALL"
          ? 'input[name="roamingNetworks"][value="all"]'
          : `input[name="roamingNetworks"][value="${roamingIdToSlug[Number(entry)] || ""}"]`,
      );
      if (box) box.checked = true;
    });

    // These four fields are shown/hidden by the syncBalanceSections()
    // call above based on which categories are checked — set the
    // values regardless, they just won't be visible if the relevant
    // category isn't part of this ATP.
    setField(
      "allowNationalRoamingData",
      detail.allowNationalRoamingData === "Y" ? "Y" : "N",
    );
    setField(
      "allowInternationalRoamingData",
      detail.allowInternationalRoamingData === "Y" ? "Y" : "N",
    );
    if (detail.allowMtc != null) {
      setField("allowMtc", detail.allowMtc === "Y" ? "Y" : "N");
    }
    if (detail.allowMoc != null) {
      setField("allowMoc", detail.allowMoc === "Y" ? "Y" : "N");
    }
    if (detail.allowNldMo != null) {
      setField("allowNldMo", detail.allowNldMo === "Y" ? "Y" : "N");
    }
    if (detail.allowIldMo != null) {
      setField("allowIldMo", detail.allowIldMo === "Y" ? "Y" : "N");
    }

    // SIM / IMSI Range Details — "<S|I>-<I|E>-<start>-<end>" strings
    // back into repeater rows, each with its own SIM/IMSI selector.
    // (Matches AtpDetailsService#buildSimRangeDetails on the backend:
    // SIM/IMSI flag first, then Include/Exclude flag.) Older records
    // saved before the per-row SIM/IMSI flag existed only have 3 parts
    // ("<I|E>-<start>-<end>") — those are matched separately and
    // default to SIM.
    const simList = document.getElementById("simRangeList");
    if (simList) simList.innerHTML = "";
    const ranges = detail.simRangeDetails || [];
    if (ranges.length === 0) {
      addSimRangeRow();
    } else {
      ranges.forEach((entry) => {
        const match4 = /^([SI])-([IE])-(\d+)-(\d+)$/.exec(entry);
        const match3 = match4 ? null : /^([IE])-(\d+)-(\d+)$/.exec(entry);
        if (match4) {
          addSimRangeRow({
            mode: match4[2] === "E" ? "exclude" : "include",
            simType: match4[1] === "I" ? "imsi" : "sim",
            start: match4[3],
            end: match4[4],
          });
        } else if (match3) {
          addSimRangeRow({
            mode: match3[1] === "E" ? "exclude" : "include",
            simType: "sim",
            start: match3[2],
            end: match3[3],
          });
        }
      });
      if (!simList.querySelector(".sim-range-row")) addSimRangeRow();
    }

    /* ---- Calendar Config & Zone Groups ---- */
    setField(
      "calendarConfig",
      detail.calendarConfig != null ? detail.calendarConfig : "",
    );
    (detail.zoneGroup || []).forEach((entry) => {
      const cb = document.querySelector(
        `[data-rating-checklist="zoneGroup"] input[value="${entry.id}"]`,
      );
      if (cb) cb.checked = true;
    });
    (detail.dataZoneGroupId || []).forEach((entry) => {
      const cb = document.querySelector(
        `[data-rating-checklist="dataZoneGroup"] input[value="${entry.id}"]`,
      );
      if (cb) cb.checked = true;
    });
    syncMsDropdownLabels();

    markStatus(null, "");
  }
  window._acPopulateForm = populateAtpCreateForm;

  /* ---------- Wire up on load ---------- */

  document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("atpCreateForm");
    const atpNameField = document.getElementById("atpName");
    const publishBtn = document.getElementById("atpCreatePublish");
    const saveDraftBtn = document.getElementById("atpCreateSaveDraft");

    applyEditModeUI();
    document
      .querySelectorAll('#balanceCategoryGroup input[type="checkbox"]')
      .forEach((cb) => {
        cb.addEventListener("change", () => {
          syncBalanceSections();
          syncUnlimitedUsageFields();
          // Re-evaluate edit-mode locks: a category checked just now
          // has no bucketId yet, so it must stay editable even though
          // other, pre-existing categories on this ATP are locked.
          applyEditModeFieldLocks();
        });
      });
    syncBalanceSections();
    initSegmented();
    // Render before initChecklistSearch/initMsDropdowns so both pick
    // up the checkboxes that just got created.
    renderRatingChecklist("zoneGroup", ZONE_GROUP_OPTIONS);
    renderRatingChecklist("dataZoneGroup", DATA_ZONE_GROUP_OPTIONS);
    initChecklistSearch();
    initMsDropdowns();
    initRoamingNetworksExclusivity();
    initRatingZoneGroupExclusivity();

    document
      .getElementById("typeOfService")
      ?.addEventListener("change", syncBillingServiceType);
    syncBillingServiceType();

    document
      .getElementById("validityPeriodType")
      ?.addEventListener("change", syncValidityPeriodDaysField);
    syncValidityPeriodDaysField();

    Object.keys(UNLIMITED_FIXED_UNITS).forEach((cat) => {
      document
        .getElementById(`unlimitedUsage${capitalize(cat)}`)
        ?.addEventListener("change", syncUnlimitedUsageFields);
    });
    syncUnlimitedUsageFields();

    document
      .getElementById("addSimRangeBtn")
      ?.addEventListener("click", () => addSimRangeRow());

    // Start with one range row filled in by default instead of an
    // empty list, so there's always at least one to edit or remove.
    if (
      document.getElementById("simRangeList") &&
      !document.querySelector("#simRangeList .sim-range-row")
    ) {
      addSimRangeRow();
    }

    // validityPeriodDays is a plain manual input — no auto-fill from
    // Valid From/To. The field's own visibility (Limited only) is
    // handled by syncValidityPeriodDaysField().

    saveDraftBtn?.addEventListener("click", async () => {
      if (isEditMode()) {
        if (typeof showToast === "function") {
          showToast("Modify API not implemented yet.", "info");
        }
        return;
      }
      if (!validateAllRequiredFields()) return;
      if (!validateSimRanges()) return;
      if (!validateZoneGroups()) return;
      markStatus(null, "Saving...");
      setBtnLoading(saveDraftBtn, true, "Saving...");
      try {
        const result = await callAtpApi();
        markStatus(
          "saved",
          `Draft saved · ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`,
        );
        if (typeof showToast === "function") {
          const summary = getApiSuccessSummary(result);
          showToast(
            summary ? `Draft saved — ${summary}` : "Draft saved.",
            "success",
            summary ? 6000 : undefined,
          );
        }
        resetAtpCreateForm();
      } catch (err) {
        console.error("Save draft failed:", err);
        markStatus(null, "Failed to save draft");
        if (typeof showToast === "function") {
          showToast(err.message, "error");
        }
      } finally {
        setBtnLoading(saveDraftBtn, false);
      }
    });

    form?.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!validateAllRequiredFields()) return;
      if (!validateSimRanges()) return;
      if (!validateZoneGroups()) return;

      const editing = isEditMode();
      const editId = editing
        ? document.querySelector(".atpc-fullscreen")?.dataset.servicePackageId
        : null;

      markStatus(null, editing ? "Saving changes..." : "Publishing...");
      setBtnLoading(publishBtn, true, editing ? "Saving..." : "Publishing...");
      try {
        const result = await callAtpApi();
        markStatus(
          editing ? "saved" : "published",
          `${editing ? "Changes saved" : "Published"} · ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`,
        );
        if (typeof showToast === "function") {
          if (editing) {
            const summary = getModifySuccessSummary(result);
            const baseMsg = result?.message || "ATP updated successfully.";
            showToast(
              summary ? `${baseMsg} — ${summary}` : baseMsg,
              "success",
              summary ? 6000 : undefined,
            );
          } else {
            const summary = getApiSuccessSummary(result);
            showToast(
              summary
                ? `ATP published successfully — ${summary}`
                : "ATP published successfully.",
              "success",
              summary ? 6000 : undefined,
            );
          }
        }
        if (editing && editId) {
          // Stay on this ATP instead of resetting into a blank Create
          // form: drop the cached GET /atp/{id} response (it's stale
          // now) and reload both the list row and the read-only view
          // so Modify -> Save -> view shows what was actually saved.
          delete _acDetailCache[editId];
          await _acLoadList();
          _acSelectAtp(editId);
        } else {
          resetAtpCreateForm();
        }
      } catch (err) {
        console.error(editing ? "Save changes failed:" : "Publish failed:", err);
        markStatus(null, editing ? "Failed to save changes" : "Failed to publish");
        if (typeof showToast === "function") {
          showToast(err.message, "error");
        }
      } finally {
        setBtnLoading(publishBtn, false);
      }
    });
  });
})();