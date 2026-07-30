// ═══════════════════════════════════════════════════════════
// ATP RULES — inline page (list + create form), same take-over
// pattern as Clone TPs. No dual-listbox mapping widget — the
// "Additional Tariff Plans" pickers are a searchable tag-select,
// and the prerequisite mapping is a repeatable condition-row list.
// ═══════════════════════════════════════════════════════════

let _arOptions = [];      // [{id, name}] available ATPs — static for now, no API yet
let _arRules = [];        // cached rule list — static for now, no API yet
let _arConditions = [];   // working condition rows for the form in progress
let _arAdd = [];          // ATPs to add — working selection
let _arRemove = [];       // ATPs to remove — working selection
let _arIdSeq = 1000;      // local id generator while there's no backend

// ── STATIC MOCK DATA — swap these out once the real API exists ──
const _AR_STATIC_OPTIONS = [
  { id: 1, name: "SMS DAILY 10" },
  { id: 2, name: "DATA DAILY 1GB" },
  { id: 3, name: "F1 VC UL" },
  { id: 4, name: "DATA 40GB 4G5G" },
  { id: 5, name: "VOICE UL LOCAL" },
  { id: 6, name: "SMS WEEKLY 50" },
  { id: 7, name: "DATA DAILY 2GB" },
  { id: 8, name: "ROAMING PACK 1DAY" },
  { id: 9, name: "AdditionalIVS" },
  { id: 10, name: "OTT COMBO PACK" },
];

const _AR_STATIC_RULES = [
  {
    id: 501,
    name: "AdditionalIVS",
    extendAccountValidity: "no",
    conditions: [
      { atpId: 1, atpName: "SMS DAILY 10", prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" },
      { atpId: 2, atpName: "DATA DAILY 1GB", prerequisiteFlag: "Should Not Contain", allOrAnyFlag: "All" },
    ],
    atpsToAdd: [{ id: 1, name: "SMS DAILY 10" }],
    atpsToRemove: [{ id: 3, name: "F1 VC UL" }],
    createdBy: "system",
    createdAt: "2026-07-20 10:15:00",
  },
  {
    id: 502,
    name: "DATA 40GB 4G5G",
    extendAccountValidity: "yes",
    conditions: [{ atpId: 4, atpName: "DATA 40GB 4G5G", prerequisiteFlag: "Should Contain", allOrAnyFlag: "Any" }],
    atpsToAdd: [],
    atpsToRemove: [{ id: 2, name: "DATA DAILY 1GB" }],
    createdBy: "system",
    createdAt: "2026-07-18 09:02:00",
  },
];

// ── Open / close the page ──────────────────────────────────
function openAtpRules() {
  const page = document.getElementById("atpRulesPage");
  const workBody = document.getElementById("leftPane")?.parentElement;
  const headerPill = document.querySelector(".header-pill-bar");
  if (!page) return;

  // Force-close Clone TPs page if it's the one currently showing —
  // otherwise it stays stacked on top (same z-index, earlier in DOM
  // order doesn't matter once it's already visible) and the ATP
  // Rules page opens invisibly underneath it.
  const clonePage = document.getElementById("clonePage");
  if (clonePage) {
    clonePage.classList.remove("visible");
    clonePage.style.display = "none";
  }

  if (workBody) workBody.style.display = "none";
  if (headerPill) headerPill.style.display = "none";

  setModuleUI("atprules");

  ["mn-approved", "mn-rejected", "mn-saved", "mn-drafts"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.style.display = "none";
  });

  _arShowList();
  page.style.display = "flex";

  requestAnimationFrame(() => {
    requestAnimationFrame(() => page.classList.add("visible"));
  });

  _arLoadOptions();
  _arLoadRules();
}

function closeAtpRulesPage() {
  const page = document.getElementById("atpRulesPage");
  const workBody = document.getElementById("leftPane")?.parentElement;
  const headerPill = document.querySelector(".header-pill-bar");
  if (!page) return;

  page.classList.remove("visible");

  page.addEventListener(
    "transitionend",
    function _restore(e) {
      if (e.propertyName !== "opacity") return;
      page.removeEventListener("transitionend", _restore);
      page.style.display = "none";

      if (workBody) workBody.style.display = "";
      if (headerPill) headerPill.style.display = "";

      ["mn-approved", "mn-rejected", "mn-saved", "mn-drafts"].forEach((id) => {
        const el = document.getElementById(id);
        if (el && !el.classList.contains("hidden")) el.style.display = "";
      });

      const step = getActiveStep();
      if (step > 0) {
        setModuleUI("builder");
      } else if (window.location.pathname.startsWith("/builder/admin")) {
        setModuleUI("approver");
      }
    },
    { once: false },
  );
}

// ── View switching ─────────────────────────────────────────
function _arShowList() {
  document.getElementById("arListView").style.display = "flex";
  document.getElementById("arFormView").style.display = "none";
}

function _arShowForm() {
  _arResetForm();
  document.getElementById("arListView").style.display = "none";
  document.getElementById("arFormView").style.display = "flex";
  if (!_arOptions.length) _arLoadOptions();
}

function _arResetForm() {
  _arConditions = [];
  _arAdd = [];
  _arRemove = [];
  document.getElementById("arRuleName").value = "";
  const seg = document.getElementById("arExtendValidity");
  seg.dataset.value = "no";
  seg.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b.dataset.val === "no"));
  document.getElementById("arConditionList").innerHTML = "";
  _arToggleConditionEmpty();
  _arRenderTagSelect("arAddSelect", "add");
  _arRenderTagSelect("arRemoveSelect", "remove");
}

// ── Segmented toggle (Extend Account Validity) ─────────────
document.addEventListener("click", (e) => {
  const btn = e.target.closest("#arExtendValidity button");
  if (!btn) return;
  const seg = document.getElementById("arExtendValidity");
  seg.dataset.value = btn.dataset.val;
  seg.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b === btn));
});

// ── Load data — STATIC for now, no API yet ──────────────────
// Swap the body of these two functions for real fetch() calls
// once /atp-rules/options and /atp-rules/list exist.
async function _arLoadOptions() {
  _arOptions = _AR_STATIC_OPTIONS;
}

async function _arLoadRules() {
  const listEl = document.getElementById("arRuleList");
  listEl.innerHTML = `<div class="ar-empty-state"><span>Loading rules…</span></div>`;
  // simulate a tiny network delay so the loading state is visible
  await new Promise((r) => setTimeout(r, 150));
  _arRules = _arRules.length ? _arRules : _AR_STATIC_RULES.slice();
  _arRenderList(_arRules);
}

// ── List rendering + search ─────────────────────────────────
function _arRenderList(rules) {
  const listEl = document.getElementById("arRuleList");
  const emptyEl = document.getElementById("arEmptyState");
  const countEl = document.getElementById("arRuleCount");

  countEl.textContent = `${_arRules.length} rule${_arRules.length === 1 ? "" : "s"}`;

  if (!rules.length) {
    listEl.innerHTML = "";
    emptyEl.style.display = _arRules.length ? "none" : "flex";
    if (_arRules.length) {
      listEl.innerHTML = `<div class="ar-empty-state"><span class="material-icons">search_off</span><p>No matching rules</p></div>`;
    }
    return;
  }
  emptyEl.style.display = "none";

  listEl.innerHTML = rules
    .map((r, i) => {
      const conds = (r.conditions || []).length;
      const adds = (r.atpsToAdd || []).length;
      const removes = (r.atpsToRemove || []).length;
      return `
        <div class="ar-rule-row" style="--card-i:${i}">
          <span class="ar-rule-id">#${r.id}</span>
          <div class="ar-rule-main">
            <div class="ar-rule-name">${_arEscape(r.name)}</div>
            <div class="ar-rule-meta">
              ${conds ? `<span class="ar-rule-tag ar-rule-tag--cond">${conds} condition${conds === 1 ? "" : "s"}</span>` : ""}
              ${adds ? `<span class="ar-rule-tag ar-rule-tag--add">+${adds} to add</span>` : ""}
              ${removes ? `<span class="ar-rule-tag ar-rule-tag--remove">-${removes} to remove</span>` : ""}
              ${!conds && !adds && !removes ? `<span class="ar-rule-tag ar-rule-tag--cond">no actions configured</span>` : ""}
            </div>
          </div>
          <span class="ar-rule-date">${r.createdAt || ""}</span>
        </div>`;
    })
    .join("");
}

function _arApplySearch(term) {
  const q = (term || "").trim().toLowerCase();
  if (!q) return _arRenderList(_arRules);
  const filtered = _arRules.filter((r) => {
    if ((r.name || "").toLowerCase().includes(q)) return true;
    return (r.conditions || []).some((c) => (c.atpName || "").toLowerCase().includes(q));
  });
  _arRenderList(filtered);
}

// ── Prerequisite condition rows ─────────────────────────────
function _arAddConditionRow() {
  const row = { atpId: "", atpName: "", prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" };
  _arConditions.push(row);
  _arRenderConditionRows();
}

function _arRemoveConditionRow(idx) {
  _arConditions.splice(idx, 1);
  _arRenderConditionRows();
}

function _arRenderConditionRows() {
  const list = document.getElementById("arConditionList");
  list.innerHTML = _arConditions
    .map((row, idx) => {
      const options = _arOptions
        .map((o) => `<option value="${o.id}" ${String(o.id) === String(row.atpId) ? "selected" : ""}>${_arEscape(o.name)}</option>`)
        .join("");
      return `
        <div class="ar-condition-row">
          <select onchange="_arUpdateCondition(${idx}, 'atpId', this.value, this)">
            <option value="">Select ATP…</option>
            ${options}
          </select>
          <select onchange="_arUpdateCondition(${idx}, 'prerequisiteFlag', this.value)">
            <option ${row.prerequisiteFlag === "Should Contain" ? "selected" : ""}>Should Contain</option>
            <option ${row.prerequisiteFlag === "Should Not Contain" ? "selected" : ""}>Should Not Contain</option>
          </select>
          <select onchange="_arUpdateCondition(${idx}, 'allOrAnyFlag', this.value)">
            <option ${row.allOrAnyFlag === "All" ? "selected" : ""}>All</option>
            <option ${row.allOrAnyFlag === "Any" ? "selected" : ""}>Any</option>
          </select>
          <button type="button" class="ar-condition-remove" onclick="_arRemoveConditionRow(${idx})">
            <span class="material-icons">close</span>
          </button>
        </div>`;
    })
    .join("");
  _arToggleConditionEmpty();
}

function _arUpdateCondition(idx, field, value, selectEl) {
  const row = _arConditions[idx];
  if (!row) return;
  row[field] = value;
  if (field === "atpId" && selectEl) {
    const opt = _arOptions.find((o) => String(o.id) === String(value));
    row.atpName = opt ? opt.name : "";
  }
}

function _arToggleConditionEmpty() {
  document.getElementById("arConditionEmpty").style.display = _arConditions.length ? "none" : "block";
}

// ── Tag-select component (search + chips), used for Add/Remove lists ─
function _arRenderTagSelect(containerId, target) {
  const container = document.getElementById(containerId);
  const selected = target === "add" ? _arAdd : _arRemove;

  container.innerHTML = `
    <div class="ar-tagselect-input-wrap">
      <input type="text" placeholder="Search ATPs to add…" autocomplete="off"
             oninput="_arFilterTagOptions('${containerId}', '${target}', this.value)"
             onfocus="_arFilterTagOptions('${containerId}', '${target}', this.value)"
             onblur="setTimeout(() => _arCloseTagDropdown('${containerId}'), 150)">
      <div class="ar-tagselect-dropdown" id="${containerId}-dropdown"></div>
    </div>
    <div class="ar-chip-row" id="${containerId}-chips"></div>
  `;
  if (target === "remove") {
    container.querySelector("input").placeholder = "Search ATPs to remove…";
  }
  _arRenderChips(containerId, target);
}

function _arFilterTagOptions(containerId, target, term) {
  const dropdown = document.getElementById(`${containerId}-dropdown`);
  const selected = target === "add" ? _arAdd : _arRemove;
  const q = (term || "").trim().toLowerCase();

  const matches = _arOptions.filter(
    (o) => !selected.some((s) => String(s.id) === String(o.id)) && (!q || o.name.toLowerCase().includes(q)),
  );

  dropdown.innerHTML = matches.length
    ? matches
        .slice(0, 30)
        .map((o) => `<div class="ar-tagselect-option" onmousedown="_arSelectTagOption('${containerId}', '${target}', ${o.id})">${_arEscape(o.name)}</div>`)
        .join("")
    : `<div class="ar-tagselect-option-empty">${_arOptions.length ? "No matching ATPs" : "Loading ATPs…"}</div>`;

  dropdown.classList.add("open");
}

function _arCloseTagDropdown(containerId) {
  const dropdown = document.getElementById(`${containerId}-dropdown`);
  if (dropdown) dropdown.classList.remove("open");
}

function _arSelectTagOption(containerId, target, id) {
  const opt = _arOptions.find((o) => String(o.id) === String(id));
  if (!opt) return;
  const arr = target === "add" ? _arAdd : _arRemove;
  if (!arr.some((s) => String(s.id) === String(id))) arr.push(opt);

  const input = document.querySelector(`#${containerId} .ar-tagselect-input-wrap input`);
  if (input) input.value = "";
  _arRenderChips(containerId, target);
  _arCloseTagDropdown(containerId);
}

function _arRemoveTagOption(containerId, target, id) {
  if (target === "add") {
    _arAdd = _arAdd.filter((s) => String(s.id) !== String(id));
  } else {
    _arRemove = _arRemove.filter((s) => String(s.id) !== String(id));
  }
  _arRenderChips(containerId, target);
}

function _arRenderChips(containerId, target) {
  const chipsEl = document.getElementById(`${containerId}-chips`);
  const arr = target === "add" ? _arAdd : _arRemove;
  chipsEl.innerHTML = arr
    .map(
      (o) => `
      <span class="ar-chip">
        ${_arEscape(o.name)}
        <span class="material-icons" onclick="_arRemoveTagOption('${containerId}', '${target}', ${o.id})">close</span>
      </span>`,
    )
    .join("");
}

// ── Submit — STATIC for now, no API yet ─────────────────────
// Swap this for a real fetch("/atp-rules/create", ...) POST once
// the backend endpoint exists.
async function _arSubmit() {
  const name = document.getElementById("arRuleName").value.trim();
  if (!name) {
    alert("Please enter the Additional Tariff Plan name.");
    document.getElementById("arRuleName").focus();
    return;
  }

  const rule = {
    id: ++_arIdSeq,
    name,
    extendAccountValidity: document.getElementById("arExtendValidity").dataset.value,
    conditions: _arConditions
      .filter((c) => c.atpId)
      .map((c) => ({
        atpId: c.atpId,
        atpName: c.atpName,
        prerequisiteFlag: c.prerequisiteFlag,
        allOrAnyFlag: c.allOrAnyFlag,
      })),
    atpsToAdd: _arAdd.slice(),
    atpsToRemove: _arRemove.slice(),
    createdBy: (typeof USERNAME !== "undefined" && USERNAME) || "system",
    createdAt: new Date().toISOString().slice(0, 19).replace("T", " "),
  };

  const btn = document.getElementById("arSubmitBtn");
  btn.disabled = true;
  btn.textContent = "Saving…";

  // simulate a tiny save delay
  await new Promise((r) => setTimeout(r, 200));

  if (!_arRules.length) _arRules = _AR_STATIC_RULES.slice();
  _arRules.unshift(rule);

  btn.disabled = false;
  btn.textContent = "Submit";

  _arShowList();
  _arRenderList(_arRules);
}

// ── util ──────────────────────────────────────────────────
function _arEscape(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}