let _arOptions = [];      // [{id, name}] available ATPs — static for now, no API yet
let _arNameOptions = [];  // [{id, name}] Additional Tariff Plan Name choices — from /builder/added-packages
let _arRules = [];        // cached rule list — loaded from /service-package-plan-mapping
let _arConditions = [];   // working condition rows for the form in progress
let _arAdd = [];          // ATPs to add — working selection
let _arRemove = [];       // ATPs to remove — working selection
let _arSelectedId = null; // id of the rule currently shown in the right pane
let _arDetailCache = {};  // planId -> full detail object from /planSubscriptionRules/view
let _arEditingRuleId = null; // planId currently being modified (form panel is in edit mode) or null for "new"

// ── Open / close the page ──────────────────────────────────
function openAtpRules() {
  const page = document.getElementById("atpRulesPage");
  const workBody = document.getElementById("leftPane")?.parentElement;
  const headerPill = document.querySelector(".header-pill-bar");
  if (!page) return;

  const clonePage = document.getElementById("clonePage");
  if (clonePage) {
    clonePage.classList.remove("visible");
    clonePage.style.display = "none";
  }

  // Force-close the ATP Rate overlay — same z-index, later in DOM
  // would otherwise stack on top and hide this one.
  const atpRatePage = document.getElementById("atpRatePage");
  if (atpRatePage) {
    atpRatePage.classList.remove("visible");
    atpRatePage.style.display = "none";
  }

  if (workBody) workBody.style.display = "none";
  if (headerPill) headerPill.style.display = "none"; // no header pill on this page

  setModuleUI("atprules");

  ["mn-approved", "mn-rejected", "mn-saved", "mn-drafts"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.style.display = "none";
  });

  _arSelectedId = null;
  _arShowPlaceholder();
  page.style.display = "flex";

  requestAnimationFrame(() => {
    requestAnimationFrame(() => page.classList.add("visible"));
  });

  _arLoadOptions();
  _arLoadRuleNameOptions();
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

// ── Right-pane view switching ───────────────────────────────
function _arShowPlaceholder() {
  document.getElementById("arPlaceholder").classList.remove("hidden");
  document.getElementById("arViewPanel").classList.add("hidden");
  document.getElementById("arFormPanel").classList.add("hidden");
}

function _arShowForm() {
  _arSelectedId = null;
  _arEditingRuleId = null;
  _arSetFormMode(false);
  _arHighlightSelectedRow();
  _arResetForm();
  document.getElementById("arPlaceholder").classList.add("hidden");
  document.getElementById("arViewPanel").classList.add("hidden");
  document.getElementById("arFormPanel").classList.remove("hidden");
  if (!_arOptions.length) _arLoadOptions();
  if (!_arNameOptions.length) _arLoadRuleNameOptions();
}

// Swaps the form header/submit-button copy between "New Rule" and "Modify Rule",
// and — while editing — swaps the plan-name picker for read-only text (the plan
// being modified is fixed and can't be changed) shown both in the header and
// in the Basics card.
function _arSetFormMode(isEdit, ruleName) {
  const title = document.querySelector("#arFormPanel .ar-form-header h3");
  const sub = document.querySelector("#arFormPanel .ar-form-header-sub");
  const submitBtn = document.getElementById("arSubmitBtn");
  const headerName = document.getElementById("arFormHeaderName");
  const nameField = document.getElementById("arRuleNameField");
  const nameReadonlyField = document.getElementById("arRuleNameReadonlyField");
  const nameReadonly = document.getElementById("arRuleNameReadonly");

  if (title) title.textContent = isEdit ? "Modify ATP Rule" : "New ATP Rule";
  if (sub) sub.textContent = isEdit
    ? "Update the details and conditions for this rule"
    : "Define the basic details and conditions for this rule";
  if (submitBtn) submitBtn.textContent = isEdit ? "Update" : "Submit";

  if (headerName) {
    headerName.classList.toggle("hidden", !isEdit);
    headerName.textContent = isEdit ? ruleName || "" : "";
  }
  if (nameField) nameField.classList.toggle("hidden", isEdit);
  if (nameReadonlyField) nameReadonlyField.classList.toggle("hidden", !isEdit);
  if (nameReadonly) nameReadonly.textContent = isEdit ? ruleName || "—" : "";
}

function _arCancelForm() {
  const wasEditing = _arEditingRuleId != null;
  _arEditingRuleId = null;
  _arSetFormMode(false);

  if (wasEditing && _arDetailCache[_arSelectedId]) {
    // back out of edit mode into the (already-fetched) view, no refetch needed
    document.getElementById("arFormPanel").classList.add("hidden");
    document.getElementById("arViewPanel").classList.remove("hidden");
    _arRenderView(_arDetailCache[_arSelectedId]);
  } else if (_arSelectedId != null) {
    const id = _arSelectedId;
    _arSelectedId = null; // clear so _arSelectRule doesn't treat this as a deselect toggle
    _arSelectRule(id);
  } else {
    _arShowPlaceholder();
  }
}

function _arResetForm() {
  _arConditions = [{ atpId: "", atpName: "", prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" }];
  _arAdd = [];
  _arRemove = [];
  const nameSelect = document.getElementById("arRuleName");
  if (nameSelect) nameSelect.value = "";
  const seg = document.getElementById("arExtendValidity");
  seg.dataset.value = "no";
  seg.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b.dataset.val === "no"));
  _arRenderConditionRows();
  _arRenderTagSelect("arAddSelect", "add");
  _arRenderTagSelect("arRemoveSelect", "remove");
}

// Extend Account Validity — server sends/expects Y | N | C ("validity addon"),
// the segmented control works off no | yes | addon.
function _arExtendValidityToUi(serverVal) {
  const v = String(serverVal || "").trim().toUpperCase();
  if (v === "Y") return "yes";
  if (v === "C") return "addon";
  return "no";
}

function _arExtendValidityToServer(uiVal) {
  if (uiVal === "yes") return "Y";
  if (uiVal === "addon") return "C";
  return "N";
}

function _arExtendValidityLabel(serverVal) {
  const v = String(serverVal || "").trim().toUpperCase();
  if (v === "Y") return "Yes";
  if (v === "C") return "Validity Addon";
  if (v === "N") return "No";
  return "—";
}

// ── Segmented toggle (Extend Account Validity) ─────────────
document.addEventListener("click", (e) => {
  const btn = e.target.closest("#arExtendValidity button");
  if (!btn) return;
  const seg = document.getElementById("arExtendValidity");
  seg.dataset.value = btn.dataset.val;
  seg.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b === btn));
});

// ── Load data ────────────────────────────────────────────────
// ATP options (used by the Prerequisite Condition, ATPs to be Added,
// and ATPs to be Removed pickers) come from GET /service-packages/{networkId}.
async function _arLoadOptions() {
  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";

  if (!networkId) {
    console.error("ATP Rules: NETWORK_ID not found in session.");
    _arOptions = [];
  } else {
    try {
      const res = await fetch("/service-packages/" + networkId);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const body = await res.json();
      const rows = body.data || [];

      _arOptions = rows.map((row) => ({
        id: row.servicePackageId,
        name: row.servicePackageName,
      }));
    } catch (err) {
      console.error("Failed to load service packages for ATP Rules:", err);
      _arOptions = [];
    }
  }

  // Refresh any pickers already on screen so the loaded options show up
  // even if the user opened the form before the fetch resolved.
  if (document.getElementById("arConditionList")) _arRenderConditionRows();
  if (document.getElementById("arAddSelect-list")) {
    const addTerm = document.querySelector("#arAddSelect .ar-checklist-search input")?.value || "";
    _arRenderCheckOptions("arAddSelect", "add", addTerm);
  }
  if (document.getElementById("arRemoveSelect-list")) {
    const removeTerm = document.querySelector("#arRemoveSelect .ar-checklist-search input")?.value || "";
    _arRenderCheckOptions("arRemoveSelect", "remove", removeTerm);
  }
}

// "Additional Tariff Plan Name" dropdown — backed by the real
// /builder/added-packages API (AtpRulesController.getAddOnPackages).
async function _arLoadRuleNameOptions() {
  const select = document.getElementById("arRuleName");
  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";

  if (select) {
    select.disabled = true;
    select.innerHTML = `<option value="">Loading…</option>`;
  }

  if (!networkId) {
    console.error("ATP Rules: NETWORK_ID not found in session.");
    _arNameOptions = [];
    _arRenderRuleNameOptions();
    return;
  }

  try {
    const res = await fetch("/builder/added-packages?networkId=" + networkId);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();

    _arNameOptions = (data || []).map((item) => ({
      id: item.servicePackageId,
      name: item.servicePackageDesc,
    }));
  } catch (err) {
    console.error("Failed to load Additional Tariff Plan names:", err);
    _arNameOptions = [];
  }

  _arRenderRuleNameOptions();
}

function _arRenderRuleNameOptions(selectedId) {
  const select = document.getElementById("arRuleName");
  if (!select) return;

  const options = _arNameOptions
    .map(
      (o) =>
        `<option value="${o.id}" ${String(o.id) === String(selectedId) ? "selected" : ""}>${_arEscape(o.name)}</option>`,
    )
    .join("");

  select.innerHTML = `<option value="">Select Additional Tariff Plan…</option>${options}`;
  select.disabled = false;
}

// Rule list now comes from the real /service-package-plan-mapping API.
async function _arLoadRules() {
  const listEl = document.getElementById("arRuleList");
  listEl.innerHTML = `<div class="ar-empty-state"><span>Loading rules…</span></div>`;

  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";

  if (!networkId) {
    console.error("ATP Rules: NETWORK_ID not found in session.");
    _arRules = [];
    _arRenderList(_arRules);
    return;
  }

  try {
    const res = await fetch("/service-package-plan-mapping?networkId=" + networkId);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();

    _arRules = (data || []).map((item) => ({
      id: item.planId,
      name: item.servicePackageDesc,
      extendAccountValidity: "no",
      conditions: [],
      atpsToAdd: [],
      atpsToRemove: [],
      createdBy: "",
      createdAt: "",
    }));
  } catch (err) {
    console.error("Failed to load rules:", err);
    _arRules = [];
  }

  _arRenderList(_arRules);
}

// ── Left pane: list rendering + search ──────────────────────
function _arRenderList(rules) {
  const listEl = document.getElementById("arRuleList");
  const emptyEl = document.getElementById("arEmptyState");
  const countEl = document.getElementById("arRuleCount");

  countEl.textContent = `${_arRules.length} rule${_arRules.length === 1 ? "" : "s"}`;

  if (!rules.length) {
    listEl.innerHTML = "";
    emptyEl.style.display = "flex";
    if (_arRules.length) {
      emptyEl.innerHTML = `<span class="material-icons">search_off</span><p>No matching rules</p>`;
    }
    return;
  }
  emptyEl.style.display = "none";

  listEl.innerHTML = rules
    .map((r) => {
      const conds = (r.conditions || []).length;
      const adds = (r.atpsToAdd || []).length;
      const removes = (r.atpsToRemove || []).length;
      const hasMeta = conds || adds || removes || r.createdAt;
      return `
        <div class="ar-rule-row ${String(r.id) === String(_arSelectedId) ? "selected" : ""}"
             data-id="${r.id}" onclick="_arSelectRule(${r.id})">
          <div class="ar-rule-row-top">
            <div class="ar-rule-row-text">
              <span class="ar-rule-name">${_arEscape(r.name)}</span>
              <span class="ar-rule-id">#${r.id}</span>
            </div>
            <span class="material-icons ar-rule-chevron">chevron_right</span>
          </div>
          ${hasMeta ? `
          <div class="ar-rule-meta">
            ${conds ? `<span class="ar-rule-tag ar-rule-tag--cond">${conds} cond.</span>` : ""}
            ${adds ? `<span class="ar-rule-tag ar-rule-tag--add">+${adds}</span>` : ""}
            ${removes ? `<span class="ar-rule-tag ar-rule-tag--remove">-${removes}</span>` : ""}
          </div>
          ${r.createdAt ? `<span class="ar-rule-date">${r.createdAt}</span>` : ""}` : ""}
        </div>`;
    })
    .join("");
}

function _arHighlightSelectedRow() {
  document.querySelectorAll(".ar-rule-row").forEach((row) => {
    row.classList.toggle("selected", row.dataset.id === String(_arSelectedId));
  });
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

// ── Right pane: read-only view of a selected rule ───────────
// Fetches full rule detail from the real /planSubscriptionRules/view API
// (teammate's endpoint — networkId + planId) and renders it.
async function _arSelectRule(id) {
  const listedRule = _arRules.find((r) => String(r.id) === String(id));
  if (!listedRule) return;

  if (String(_arSelectedId) === String(id)) {
    // clicking the already-selected rule again deselects it
    _arSelectedId = null;
    _arHighlightSelectedRow();
    _arShowPlaceholder();
    return;
  }

  _arSelectedId = id;
  _arHighlightSelectedRow();

  document.getElementById("arPlaceholder").classList.add("hidden");
  document.getElementById("arFormPanel").classList.add("hidden");
  const viewPanel = document.getElementById("arViewPanel");
  viewPanel.classList.remove("hidden");
  viewPanel.innerHTML = `<div class="ar-empty-state"><span>Loading rule…</span></div>`;

  try {
    const detail = await _arFetchRuleDetail(id);
    if (String(_arSelectedId) !== String(id)) return; // selection changed while the fetch was in flight
    _arDetailCache[id] = detail;
    _arRenderView(detail);
  } catch (err) {
    console.error("Failed to load rule detail:", err);
    if (String(_arSelectedId) !== String(id)) return;
    viewPanel.innerHTML = `
      <div class="ar-empty-state">
        <span class="material-icons">error_outline</span>
        <p>Couldn't load this rule.</p>
        <span>${_arEscape(err.message || "Please try again.")}</span>
      </div>`;
  }
}

// GET /planSubscriptionRules/view?networkId=..&planId=..
async function _arFetchRuleDetail(planId) {
  const networkId = typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  if (!networkId) throw new Error("NETWORK_ID not found in session.");

  const res = await fetch(`/planSubscriptionRules/view?networkId=${networkId}&planId=${planId}`);
  if (!res.ok) throw new Error("HTTP " + res.status);
  const data = await res.json();
  if (data.errorCode !== 0) throw new Error(data.errorDesc || "Request failed");

  return {
    id: planId,
    name: data.planName || "",
    extendAccountValidity: data.extendAccountValidity || "",
    conditions: (data.atpIncludeExcludeList || []).map(_arParseIncludeExclude),
    tpConditions: (data.tpIncludeExcludeList || []).map(_arParseIncludeExclude),
    atpsToAdd: (data.atpToBeAddedList || []).map(_arParseAddRemove),
    atpsToRemove: (data.atpToBeRemovedList || []).map(_arParseAddRemove),
    createdBy: "",
    createdAt: "",
  };
}

// "F1 VC UL_ATP122~I#200~ALL" -> { atpId: 200, atpName: "F1 VC UL_ATP122",
//   prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" }
// I = Should Contain (include), E = Should Not Contain (exclude)
function _arParseIncludeExclude(str) {
  const parts = String(str).split("~");
  const atpName = parts[0] || "";
  const flagPart = parts[1] || "";
  const allOrAny = parts[2] || "";
  const hashIdx = flagPart.indexOf("#");
  const flag = hashIdx === -1 ? flagPart : flagPart.slice(0, hashIdx);
  const atpId = hashIdx === -1 ? "" : flagPart.slice(hashIdx + 1);
  return {
    atpId,
    atpName,
    prerequisiteFlag: flag === "E" ? "Should Not Contain" : "Should Contain",
    allOrAnyFlag: allOrAny === "ANY" ? "Any" : "All",
  };
}

// "F1MOBILE S 5G UL_ATP170#275" -> { id: 275, name: "F1MOBILE S 5G UL_ATP170" }
function _arParseAddRemove(str) {
  const s = String(str);
  const hashIdx = s.lastIndexOf("#");
  if (hashIdx === -1) return { id: "", name: s };
  return { id: s.slice(hashIdx + 1), name: s.slice(0, hashIdx) };
}

// Renders the read-only "Prerequisite Conditions" / "TP-Level Conditions"
// table body: # | ATP Name | Condition | Match Type
function _arConditionRowsHtml(conditions) {
  if (!conditions.length) {
    return `<tr><td colspan="4" class="ar-table-empty">No conditions — this rule always applies.</td></tr>`;
  }
  return conditions
    .map((c, i) => {
      const isExclude = c.prerequisiteFlag === "Should Not Contain";
      return `
      <tr>
        <td class="ar-table-idx">${i + 1}</td>
        <td class="ar-table-primary">${_arEscape(c.atpName)}</td>
        <td><span class="ar-table-tag ${isExclude ? "ar-table-tag--exclude" : "ar-table-tag--include"}">${_arEscape(c.prerequisiteFlag)}</span></td>
        <td><span class="ar-table-tag ar-table-tag--match">${_arEscape(c.allOrAnyFlag)}</span></td>
      </tr>`;
    })
    .join("");
}

// Renders the read-only "ATPs to Add" / "ATPs to Remove" table body: # | ATP Name
function _arAtpRowsHtml(atps, emptyLabel) {
  if (!atps.length) {
    return `<tr><td colspan="2" class="ar-table-empty">${_arEscape(emptyLabel)}</td></tr>`;
  }
  return atps
    .map(
      (o, i) => `
      <tr>
        <td class="ar-table-idx">${i + 1}</td>
        <td class="ar-table-primary">${_arEscape(o.name)}</td>
      </tr>`,
    )
    .join("");
}

function _arRenderView(rule) {
  const panel = document.getElementById("arViewPanel");
  const conditions = rule.conditions || [];
  const tpConditions = rule.tpConditions || [];
  const atpsToAdd = rule.atpsToAdd || [];
  const atpsToRemove = rule.atpsToRemove || [];

  const metaLine = rule.createdAt || rule.createdBy
    ? `<span class="ar-rule-date">Created ${_arEscape(rule.createdAt || "")} by ${_arEscape(rule.createdBy || "")}</span>`
    : `<span class="ar-rule-date">Rule #${rule.id}</span>`;

  panel.innerHTML = `
    <div class="ar-view-header">
      <div>
        <h2>${_arEscape(rule.name)}</h2>
        ${metaLine}
      </div>
      <div class="ar-view-header-actions">
        <button type="button" class="ar-icon-btn" title="Modify rule" onclick="_arModifyRule(${rule.id})">
          <span class="material-icons">edit</span>
          Modify
        </button>
        <button type="button" class="ar-icon-btn ar-icon-btn--danger" title="Delete rule" onclick="_arDeleteRule(${rule.id})">
          <span class="material-icons">delete</span>
          Delete
        </button>
      </div>
    </div>

    <div class="ar-view-body">
    <div class="ar-card">
      <div class="ar-card-header-row">
        <div class="ar-card-header-left">
          <span class="ar-card-icon ar-card-icon--basics"><span class="material-icons">description</span></span>
          <div class="ar-card-header-text">
            <div class="ar-card-title">Basics</div>
          </div>
        </div>
      </div>
      <div class="ar-info-row">
        <div class="ar-info-item">
          <label>Additional Tariff Plan Name</label>
          <span>${_arEscape(rule.name)}</span>
        </div>
        <div class="ar-info-item">
          <label>Extend Account Validity</label>
          <span>${_arEscape(_arExtendValidityLabel(rule.extendAccountValidity))}</span>
        </div>
      </div>
    </div>

    <div class="ar-card">
      <div class="ar-card-header-row">
        <div class="ar-card-header-left">
          <span class="ar-card-icon ar-card-icon--filter"><span class="material-icons">filter_alt</span></span>
          <div class="ar-card-header-text">
            <div class="ar-card-title">Prerequisite Conditions</div>
            <p class="ar-card-sub-inline">This rule applies only when every condition below is met.</p>
          </div>
        </div>
      </div>
      <div class="ar-table-wrap">
        <div class="ar-table-scroll">
          <table class="ar-table">
            <thead>
              <tr>
                <th>#</th>
                <th>ATP Name</th>
                <th>Condition</th>
                <th>Match Type</th>
              </tr>
            </thead>
            <tbody>${_arConditionRowsHtml(conditions)}</tbody>
          </table>
        </div>
      </div>
    </div>

    ${tpConditions.length ? `
    <div class="ar-card">
      <div class="ar-card-header-row">
        <div class="ar-card-header-left">
          <span class="ar-card-icon ar-card-icon--filter"><span class="material-icons">rule</span></span>
          <div class="ar-card-header-text">
            <div class="ar-card-title">TP-Level Conditions</div>
          </div>
        </div>
      </div>
      <div class="ar-table-wrap">
        <div class="ar-table-scroll">
          <table class="ar-table">
            <thead>
              <tr>
                <th>#</th>
                <th>ATP Name</th>
                <th>Condition</th>
                <th>Match Type</th>
              </tr>
            </thead>
            <tbody>${_arConditionRowsHtml(tpConditions)}</tbody>
          </table>
        </div>
      </div>
    </div>` : ""}

    <div class="ar-view-cards-row">
      <div class="ar-card ar-card-half">
        <div class="ar-card-header-row">
          <div class="ar-card-header-left">
            <span class="ar-card-icon ar-card-icon--add"><span class="material-icons">add_circle</span></span>
            <div class="ar-card-header-text">
              <div class="ar-card-title">ATPs to Add</div>
            </div>
          </div>
          <span class="ar-count-badge">${atpsToAdd.length} ATP${atpsToAdd.length === 1 ? "" : "s"}</span>
        </div>
        <p class="ar-card-sub-inline" style="margin-bottom:10px;">Granted automatically when this rule fires.</p>
        <div class="ar-table-wrap">
          <div class="ar-table-scroll">
            <table class="ar-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>ATP Name</th>
                </tr>
              </thead>
              <tbody>${_arAtpRowsHtml(atpsToAdd, "None")}</tbody>
            </table>
          </div>
        </div>
      </div>
      <div class="ar-card ar-card-half">
        <div class="ar-card-header-row">
          <div class="ar-card-header-left">
            <span class="ar-card-icon ar-card-icon--remove"><span class="material-icons">remove_circle</span></span>
            <div class="ar-card-header-text">
              <div class="ar-card-title">ATPs to Remove</div>
            </div>
          </div>
          <span class="ar-count-badge">${atpsToRemove.length} ATP${atpsToRemove.length === 1 ? "" : "s"}</span>
        </div>
        <p class="ar-card-sub-inline" style="margin-bottom:10px;">Revoked automatically when this rule fires.</p>
        <div class="ar-table-wrap">
          <div class="ar-table-scroll">
            <table class="ar-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>ATP Name</th>
                </tr>
              </thead>
              <tbody>${_arAtpRowsHtml(atpsToRemove, "None")}</tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
    </div>
  `;
}

// ── Modify — opens the same form used for "New Rule", prefilled with the
// fetched detail. The plan being modified is fixed, so the plan-name picker
// is swapped for read-only text (see _arSetFormMode); on submit this goes
// out via PUT /planSubscriptionRules/modify (see _arSubmit).
function _arModifyRule(id) {
  const rule = _arDetailCache[id];
  if (!rule) return;

  _arEditingRuleId = id;
  _arSelectedId = id;

  _arConditions = rule.conditions.length
    ? rule.conditions.map((c) => ({ ...c }))
    : [{ atpId: "", atpName: "", prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" }];
  _arAdd = rule.atpsToAdd.map((o) => ({ ...o }));
  _arRemove = rule.atpsToRemove.map((o) => ({ ...o }));

  document.getElementById("arPlaceholder").classList.add("hidden");
  document.getElementById("arViewPanel").classList.add("hidden");
  document.getElementById("arFormPanel").classList.remove("hidden");
  _arSetFormMode(true, rule.name);

  const nameSelect = document.getElementById("arRuleName");
  if (nameSelect) {
    // The form field is keyed by servicePackageId, but the view API only
    // gives us the plan name back — match on name as a best effort.
    // (Not that it matters for submission: the select is hidden while
    // editing and _arSubmit() always uses _arEditingRuleId as planId.)
    const match = _arNameOptions.find((o) => o.name === rule.name);
    nameSelect.value = match ? match.id : "";
  }

  const seg = document.getElementById("arExtendValidity");
  if (seg) {
    const evVal = _arExtendValidityToUi(rule.extendAccountValidity);
    seg.dataset.value = evVal;
    seg.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b.dataset.val === evVal));
  }

  _arRenderConditionRows();
  _arRenderTagSelect("arAddSelect", "add");
  _arRenderTagSelect("arRemoveSelect", "remove");
}

// ── Delete — confirms, calls DELETE /planSubscriptionRules/delete
// (networkId + planId as query params, same controller as add/modify),
// then on success removes the rule locally and goes back to the placeholder.
async function _arDeleteRule(id) {
  const rule = _arDetailCache[id] || _arRules.find((r) => String(r.id) === String(id));
  if (!rule) return;

  const ok = confirm(`Delete rule "${rule.name}"? This can't be undone.`);
  if (!ok) return;

  const networkId = typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  if (!networkId) {
    alert("Network not found in session. Please reload the page and try again.");
    return;
  }

  const btn = document.querySelector(`#arViewPanel .ar-icon-btn--danger`);
  const actionButtons = document.querySelectorAll("#arViewPanel .ar-view-header-actions button");
  actionButtons.forEach((b) => (b.disabled = true));
  if (btn) btn.innerHTML = `<span class="material-icons">hourglass_empty</span> Deleting…`;

  try {
    const res = await fetch(
      `/planSubscriptionRules/delete?networkId=${encodeURIComponent(networkId)}&planId=${encodeURIComponent(id)}`,
      { method: "DELETE" },
    );
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();
    if (data.errorCode !== 0) throw new Error(data.errorDesc || "Request failed");

    _arRules = _arRules.filter((r) => String(r.id) !== String(id));
    delete _arDetailCache[id];

    _arSelectedId = null;
    _arRenderList(_arRules);
    _arShowPlaceholder(); // go back
  } catch (err) {
    console.error("Failed to delete ATP rule:", err);
    alert("Couldn't delete the rule: " + (err.message || "Please try again."));
    actionButtons.forEach((b) => (b.disabled = false));
    if (btn) btn.innerHTML = `<span class="material-icons">delete</span> Delete`;
  }
}

// ── Prerequisite condition rows (create form) ────────────────
function _arAddConditionRow() {
  const row = { atpId: "", atpName: "", prerequisiteFlag: "Should Contain", allOrAnyFlag: "All" };
  _arConditions.unshift(row);
  _arRenderConditionRows();
}

function _arRemoveConditionRow(idx) {
  // At least one condition is mandatory — refuse to remove the last one.
  if (_arConditions.length <= 1) return;
  _arConditions.splice(idx, 1);
  _arRenderConditionRows();
}

function _arRenderConditionRows() {
  const list = document.getElementById("arConditionList");
  const onlyOne = _arConditions.length <= 1;
  list.innerHTML = _arConditions
    .map((row, idx) => {
      // an ATP already mapped in another condition row can't be picked again here
      const usedElsewhere = new Set(
        _arConditions.filter((r, i) => i !== idx && r.atpId).map((r) => String(r.atpId)),
      );
      const options = _arOptions
        .filter((o) => !usedElsewhere.has(String(o.id)))
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
          ${
            onlyOne
              ? `<button type="button" class="ar-condition-remove ar-condition-remove--disabled" title="At least one condition is required">
                  <span class="material-icons">close</span>
                </button>`
              : `<button type="button" class="ar-condition-remove" onclick="_arRemoveConditionRow(${idx})">
                  <span class="material-icons">close</span>
                </button>`
          }
        </div>`;
    })
    .join("");
  _arToggleConditionEmpty();
}

function _arUpdateCondition(idx, field, value, selectEl) {
  const row = _arConditions[idx];
  if (!row) return;
  row[field] = value;
  if (field === "atpId") {
    if (selectEl) {
      const opt = _arOptions.find((o) => String(o.id) === String(value));
      row.atpName = opt ? opt.name : "";
    }
    // re-render so the other rows' dropdowns pick up the new exclusion
    _arRenderConditionRows();
  }
}

function _arToggleConditionEmpty() {
  document.getElementById("arConditionEmpty").style.display = _arConditions.length ? "none" : "block";
}

// ── Checklist component (search + select all/deselect all + checkboxes),
// used for the ATPs to Add / ATPs to Remove pickers ───────────
function _arRenderTagSelect(containerId, target) {
  const container = document.getElementById(containerId);
  const addLabel = target === "add" ? "Search ATPs to add…" : "Search ATPs to remove…";

  container.innerHTML = `
    <div class="ar-checklist-toolbar">
      <div class="ar-checklist-search">
        <span class="material-icons">search</span>
        <input type="text" placeholder="${addLabel}" autocomplete="off"
               oninput="_arFilterTagOptions('${containerId}', '${target}', this.value)">
      </div>
      <div class="ar-checklist-actions">
        <button type="button" class="ar-checklist-icon-btn" title="Select all"
                onclick="_arSelectAllTagOptions('${containerId}', '${target}')">
          <span class="material-icons">done_all</span>
        </button>
        <button type="button" class="ar-checklist-icon-btn" title="Deselect all"
                onclick="_arDeselectAllTagOptions('${containerId}', '${target}')">
          <span class="material-icons">remove_done</span>
        </button>
      </div>
    </div>
    <div class="ar-checklist-count" id="${containerId}-count"></div>
    <div class="ar-checklist-body" id="${containerId}-list"></div>
  `;

  _arRenderCheckOptions(containerId, target, "");
}

function _arFilterTagOptions(containerId, target, term) {
  _arRenderCheckOptions(containerId, target, term);
}

function _arRenderCheckOptions(containerId, target, term) {
  const listEl = document.getElementById(`${containerId}-list`);
  const countEl = document.getElementById(`${containerId}-count`);
  const selected = target === "add" ? _arAdd : _arRemove;
  const otherSelected = target === "add" ? _arRemove : _arAdd; // picked in the opposite list — off-limits here
  const q = (term || "").trim().toLowerCase();

  const opts = _arOptions.filter(
    (o) => !otherSelected.some((s) => String(s.id) === String(o.id)) && (!q || o.name.toLowerCase().includes(q)),
  );

  // Selected ATPs float to the top so users can see what's already picked
  // without hunting through the full list.
  const isChecked = (o) => selected.some((s) => String(s.id) === String(o.id));
  opts.sort((a, b) => Number(isChecked(b)) - Number(isChecked(a)));

  listEl.innerHTML = opts.length
    ? opts
        .map((o) => {
          const checked = isChecked(o);
          return `
        <label class="ar-check-row${checked ? " ar-check-row--selected" : ""}">
          <input type="checkbox" ${checked ? "checked" : ""}
                 onchange="_arToggleTagOption('${containerId}', '${target}', ${o.id}, this.checked)">
          <span>${_arEscape(o.name)}</span>
        </label>`;
        })
        .join("")
    : `<div class="ar-checklist-empty">${_arOptions.length ? "No matching ATPs" : "Loading ATPs…"}</div>`;

  countEl.textContent = selected.length ? `${selected.length} selected` : "None selected";
}

// Re-renders whichever of the two checklists is currently on screen,
// each preserving its own search term — used so picking an ATP in one
// list immediately removes it as an option in the other.
function _arRefreshChecklistIfPresent(containerId, target) {
  if (!document.getElementById(`${containerId}-list`)) return;
  const term = document.querySelector(`#${containerId} .ar-checklist-search input`)?.value || "";
  _arRenderCheckOptions(containerId, target, term);
}

function _arRefreshBothChecklists() {
  _arRefreshChecklistIfPresent("arAddSelect", "add");
  _arRefreshChecklistIfPresent("arRemoveSelect", "remove");
}

function _arToggleTagOption(containerId, target, id, checked) {
  const opt = _arOptions.find((o) => String(o.id) === String(id));
  if (!opt) return;

  if (target === "add") {
    _arAdd = checked
      ? _arAdd.some((s) => String(s.id) === String(id)) ? _arAdd : [..._arAdd, opt]
      : _arAdd.filter((s) => String(s.id) !== String(id));
  } else {
    _arRemove = checked
      ? _arRemove.some((s) => String(s.id) === String(id)) ? _arRemove : [..._arRemove, opt]
      : _arRemove.filter((s) => String(s.id) !== String(id));
  }

  _arRefreshBothChecklists();
}

function _arSelectAllTagOptions(containerId, target) {
  const searchInput = document.querySelector(`#${containerId} .ar-checklist-search input`);
  const term = searchInput ? searchInput.value : "";
  const q = (term || "").trim().toLowerCase();
  const otherSelected = target === "add" ? _arRemove : _arAdd;
  const visible = _arOptions.filter(
    (o) => !otherSelected.some((s) => String(s.id) === String(o.id)) && (!q || o.name.toLowerCase().includes(q)),
  );

  if (target === "add") {
    visible.forEach((o) => {
      if (!_arAdd.some((s) => String(s.id) === String(o.id))) _arAdd.push(o);
    });
  } else {
    visible.forEach((o) => {
      if (!_arRemove.some((s) => String(s.id) === String(o.id))) _arRemove.push(o);
    });
  }

  _arRefreshBothChecklists();
}

function _arDeselectAllTagOptions(containerId, target) {
  const searchInput = document.querySelector(`#${containerId} .ar-checklist-search input`);
  const term = searchInput ? searchInput.value : "";
  const q = (term || "").trim().toLowerCase();
  const visibleIds = new Set(
    _arOptions.filter((o) => !q || o.name.toLowerCase().includes(q)).map((o) => String(o.id)),
  );

  if (target === "add") {
    _arAdd = _arAdd.filter((s) => !visibleIds.has(String(s.id)));
  } else {
    _arRemove = _arRemove.filter((s) => !visibleIds.has(String(s.id)));
  }

  _arRefreshBothChecklists();
}

// ── Submit — POST /planSubscriptionRules/add for a new rule, or
// PUT /planSubscriptionRules/modify when editing (same request/response
// shape, both confirmed backend endpoints — see PlanSubscriptionRuleController).
// Request: { networkId, planId, atpIncludeExcludeList, atpToBeAddedList,
//            atpToBeRemovedList, tpIncludeExcludeList, extendAccountValidity }
// Response: { errorCode, errorDesc } — errorCode 0 = success.
async function _arSubmit() {
  const isEdit = _arEditingRuleId != null;
  const nameSelect = document.getElementById("arRuleName");
  const servicePackageId = nameSelect.value;

  // The plan-name picker only matters (and is only shown) when creating —
  // while editing, the plan is fixed and _arEditingRuleId is the source of truth.
  if (!isEdit && !servicePackageId) {
    alert("Please select the Additional Tariff Plan name.");
    nameSelect.focus();
    return;
  }

  const filledConditions = _arConditions.filter((c) => c.atpId);
  if (!filledConditions.length && !_arAdd.length && !_arRemove.length) {
    alert("Add at least one Prerequisite Condition, ATP to Add, or ATP to Remove before submitting.");
    return;
  }

  const networkId = typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  if (!networkId) {
    alert("Network not found in session. Please reload the page and try again.");
    return;
  }

  // On create, the rule's planId is the servicePackageId chosen as the
  // Additional Tariff Plan Name. On edit, it's the plan already being modified.
  const planId = isEdit ? _arEditingRuleId : servicePackageId;
  const editingRuleName = isEdit ? _arDetailCache[_arEditingRuleId]?.name || "" : "";

  const payload = {
    networkId: Number(networkId),
    planId: Number(planId),
    atpIncludeExcludeList: filledConditions.map(
      (c) =>
        `${c.atpId}~${c.prerequisiteFlag === "Should Not Contain" ? "E" : "I"}~${c.allOrAnyFlag === "Any" ? "ANY" : "ALL"}`,
    ),
    atpToBeAddedList: _arAdd.map((o) => String(o.id)),
    atpToBeRemovedList: _arRemove.map((o) => String(o.id)),
    tpIncludeExcludeList: [], // no TP-level condition builder in this form yet
    extendAccountValidity: _arExtendValidityToServer(document.getElementById("arExtendValidity").dataset.value),
  };

  const btn = document.getElementById("arSubmitBtn");
  btn.disabled = true;
  btn.textContent = isEdit ? "Updating…" : "Saving…";

  try {
    const res = await fetch(isEdit ? "/planSubscriptionRules/modify" : "/planSubscriptionRules/add", {
      method: isEdit ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();
    if (data.errorCode !== 0) throw new Error(data.errorDesc || "Request failed");

    _arEditingRuleId = null;
    delete _arDetailCache[planId];
    _arSetFormMode(false);

    // Re-pull the rule list and full detail from the server so the view
    // pane reflects exactly what was persisted, instead of guessing locally.
    await _arLoadRules();
    _arSelectedId = null; // ensure _arSelectRule treats this as a fresh selection
    await _arSelectRule(Number(planId));
  } catch (err) {
    console.error("Failed to save ATP rule:", err);
    alert("Couldn't save the rule: " + (err.message || "Please try again."));
    _arSetFormMode(isEdit, editingRuleName); // restore the correct header/button state
  } finally {
    btn.disabled = false;
  }
}

// ── util ──────────────────────────────────────────────────
function _arEscape(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}