// ---------- STATE ----------
function getState() {
  const defaultState = { s2: [], s3: [], s4: [] };
  const stored = sessionStorage.getItem("state");
  return stored ? JSON.parse(stored) : defaultState;
}

function saveState(state) {
  sessionStorage.setItem("state", JSON.stringify(state));
}

// ---------- INIT ----------
function initStep4() {
  const state = getState();

  if (!state.s4) state.s4 = [];

  // Kick off the CA Service ~ Unit Type fetch early for every CA card
  // already on the page (bfcache restore / draft reload), scoped to each
  // item's own service type(s) — not a blanket fetch of everything.
  state.s4
    .filter((item) => item.category === "CA")
    .forEach((item) => ensureCaServiceUnitOptionsLoaded(item));

  // Always re-render from state (handles both fresh load and bfcache restore)
  document.getElementById("dropArea").innerHTML = "";
  state.s4.forEach((item) => renderCard(item));

  // Restore pills into in-memory array
  selectedSvcs = [];
  const saved = JSON.parse(sessionStorage.getItem("selectedSvcs_s4") || "[]");

  // Restore Normal/CA category selection
  svcCategory = sessionStorage.getItem("svcCategory_s4") || "NORMAL";
  const radio = document.querySelector(
    `input[name="svcCategory"][value="${svcCategory}"]`,
  );
  if (radio) radio.checked = true;

  saved.forEach((svc) => {
    const pill = document.querySelector(`.svc-pill[data-svc="${svc}"]`);
    if (pill) pill.classList.add("active");
    selectedSvcs.push(svc);
  });

  if (saved.length) refreshSidebar();
}

window.addEventListener("DOMContentLoaded", initStep4);

// FIX: bfcache restores don't re-fire DOMContentLoaded — re-sync DOM from state on pageshow
window.addEventListener("pageshow", (e) => {
  if (e.persisted) initStep4();
});

// ---------- CATEGORY (Normal / CA) ----------
let svcCategory = "NORMAL";

function switchCategory(category) {
  svcCategory = category;
  sessionStorage.setItem("svcCategory_s4", category);
  // No blanket CA fetch here anymore — there's no specific ATP selected
  // yet at this point, so nothing to scope the request to. The fetch now
  // happens per-item, in addToCenter() once a CA ATP (with a known service
  // type) is actually picked from the library.
  refreshSidebar();
}

// ---------- SERVICE TYPE ----------
let selectedSvcs = [];

function toggleSvc(service, el) {
  if (selectedSvcs.includes(service)) {
    selectedSvcs = selectedSvcs.filter((s) => s !== service);
    el.classList.remove("active");
  } else {
    selectedSvcs.push(service);
    el.classList.add("active");
  }

  sessionStorage.setItem("selectedSvcs_s4", JSON.stringify(selectedSvcs));

  if (selectedSvcs.length === 0) {
    // clearCenter();
  }

  refreshSidebar();
}

// function clearCenter() {
//   const state = getState();
//   state.s4 = [];
//   saveState(state);

//   document.getElementById("dropArea").innerHTML = "";
// }

function validateCenterPlans() {
  const state = getState();

  if (!state.s4 || state.s4.length === 0) return;

  const svcMap = {
    201: "VOICE",
    202: "VOICE",
    203: "SMS",
    204: "DATA",
    205: "DATA",
  };

  const validItems = state.s4.filter((item) => {
    const svc = svcMap[String(item.id)];
    return selectedSvcs.includes(svc);
  });

  state.s4 = validItems;
  saveState(state);

  const container = document.getElementById("dropArea");
  container.innerHTML = "";
  state.s4.forEach((item) => renderCard(item));
}

// ---------- SIDEBAR ----------
function refreshSidebar() {
  const list = document.getElementById("comp-list");

  const isCA = svcCategory === "CA";

  // VOICE/SMS/DATA -> 1/2/3 (service type codes) for both Normal and CA.
  // The CA endpoint's sec/msg/kb billing-unit mapping is handled server-side now.
  const svcMap = { VOICE: "1", SMS: "2", DATA: "3" };

  const types = selectedSvcs
    .map((s) => svcMap[s])
    .sort()
    .join(",");

  if (!types) {
    list.innerHTML = "";
    return;
  }

  list.innerHTML = '<p class="sidebar-text">Loading...</p>';

  const endpoint = isCA
    ? `/builder/step4/cafilter?types=${types}`
    : `/builder/step4/filter?types=${types}`;

  fetch(endpoint)
    .then((res) => res.json())
    .then((data) => {
      // Always clear search regardless of result
      const searchInput = document.getElementById("librarySearchInput");
      if (searchInput) {
        searchInput.value = "";
        searchInput.dispatchEvent(new Event("input"));
      }

      if (!data || !data.length) {
        list.innerHTML = '<p class="sidebar-text">No Plans</p>';
        return;
      }

      list.innerHTML = data
        .map(
          (plan) => `
			 
			                <div class="draggable-item"

			                    data-network-id="${plan.networkId}"
			                    data-package-id="${plan.servicePackageId}"
			                    onclick="addToCenter('${plan.servicePackageId}','${plan.servicePackageName}','${plan.networkId}','${plan.serviceTypes || ""}')">
			                    ${plan.servicePackageName}
							
							</div>

			            `,
        )
        .join("");
    })
    .catch((err) => {
      console.error(err);
      list.innerHTML = '<p class="sidebar-text">Error loading data</p>';
    });
}

// ---------- SERVICE TYPE HELPERS ----------
// Converts the backend's SERVICE_TYPES codes (from the plan response) into
// readable labels. Requests always send 1/2/3 now (Normal and CA alike);
// sec/msg/kb are kept here only in case a plan's response still echoes them back.
const SVC_TYPE_LABELS = {
  1: "VOICE",
  2: "SMS",
  3: "DATA",
  sec: "VOICE",
  msg: "SMS",
  kb: "DATA",
};
function typeLabelFromCodes(codes) {
  if (!codes) return "";
  return String(codes)
    .split(",")
    .map((c) => SVC_TYPE_LABELS[c.trim()] || c.trim())
    .join(",");
}

// ---------- ADD ----------
function addToCenter(id, name, networkId, serviceTypes) {
  const state = getState();
  if (!state.s4) state.s4 = [];

  // Cross-check: block if already in DATP (s3)
  const s3Items = state.s3 || [];
  if (s3Items.find((item) => String(item.id) === String(id))) {
    alert(`"${name}" is already selected in Default ATPs (DATP).`);
    return;
  }

  // Self-duplicate check
  if (state.s4.find((i) => String(i.id) === String(id))) return;

  const item = {
    id: String(id),
    name: name,
    type: typeLabelFromCodes(serviceTypes),
    category: svcCategory,
    validity: "M",
    rentalPeriod: "",
    renewal: "No",
    midnightExpiry: "No",
    rental: "",
    maxCount: "",
    freeCycles: "0",
    priority: "",
    mrp: "",
    serviceCode: "",
    vipPlan: "",
  };

  // CA-category items need their caConfig (and its one mandatory mapping
  // row, with its rowId) created and saved right here, before the very
  // first render — not lazily inside renderCard() via ensureCaConfig().
  // Otherwise the rowId baked into the initial DOM never makes it into
  // sessionStorage, so the first edit to any mapping field silently no-ops
  // (updateCaMappingField can't find a matching row) until something else
  // (like clicking "Add Mapping") finally persists a caConfig — at which
  // point whatever the user had already typed is gone.
  if (item.category === "CA") {
    item.caConfig = defaultCaConfig();
    // Fetch the CA Service ~ Unit Type options scoped to *this* ATP's own
    // service type(s) (item.type, e.g. "VOICE" or "VOICE,SMS") — not every
    // base service name at once.
    ensureCaServiceUnitOptionsLoaded(item);
  }

  state.s4.push(item);
  saveState(state);

  renderCard(item);
}

// ---------- RENDER ----------
function renderCard(item) {
  const container = document.getElementById("dropArea");

  if (document.getElementById(`card-s4-${item.id}`)) return;

  const card = document.createElement("div");
  card.className = "service-card";
  card.id = `card-s4-${item.id}`;

  card.innerHTML = `
    <div style="display:flex;justify-content:space-between;margin-bottom:10px;">
        <b>${item.name}</b>
        <span onclick="removeItem('${item.id}')" style="color:red;cursor:pointer;">✕</span>
    </div>
 
    <div class="card-grid">
        <div class="card-field">
            <label>VALIDITY</label>
            <select onchange="handleValidityChange('${item.id}', this.value)">
                <option value="M"  ${item.validity === "M" ? "selected" : ""}>Monthly</option>
                <option value="D"  ${item.validity === "D" ? "selected" : ""}>Daily</option>
                <option value="W"  ${item.validity === "W" ? "selected" : ""}>Weekly</option>
                <option value="Y"  ${item.validity === "Y" ? "selected" : ""}>Yearly</option>
                <option value="FM"  ${item.validity === "FM" ? "selected" : ""}>Fixed Month</option>
                <option value="CW"  ${item.validity === "CW" ? "selected" : ""}>Calendar Week</option>
                <option value="CM"  ${item.validity === "CM" ? "selected" : ""}>Calendar Month</option>
                <option value="U"  ${item.validity === "U" ? "selected" : ""}>Unlimited</option>
                <option value="O"  ${item.validity === "O" ? "selected" : ""}>Others</option>
            </select>
        </div>
        <div class="card-field" id="rental-period-${item.id}" style="display:${item.validity === "O" ? "block" : "none"};">
            <label>NO. OF DAYS</label>
            <input type="number" min="1"
                value="${item.rentalPeriod || ""}"
                placeholder="Enter days"
                oninput="updateField('${item.id}', 'rentalPeriod', this.value)">
        </div>
 
        <div class="card-field">
            <label>MIDNIGHT EXPIRY</label>
            <select onchange="updateField('${item.id}','midnightExpiry',this.value)">
                <option ${item.midnightExpiry === "No" ? "selected" : ""}>No</option>
                <option ${item.midnightExpiry === "Yes" ? "selected" : ""}>Yes</option>
            </select>
        </div>
 
        <div class="card-field">
            <label>AUTO RENEWAL</label>
            <select onchange="handleRenewalChange('${item.id}',this.value)">
                <option ${item.renewal === "No" ? "selected" : ""}>No</option>
                <option ${item.renewal === "Yes" ? "selected" : ""}>Yes</option>
            </select>
        </div>
 
        <div id="renewal-${item.id}" style="display:${item.renewal === "Yes" ? "contents" : "none"};">
            <div class="card-field">
                <label>RENTAL</label>
                <input type="number"
                       value="${item.rental || ""}"
                       oninput="updateField('${item.id}','rental',this.value)">
            </div>
 
            <div class="card-field">
                <label>MAX COUNT</label>
                <input type="number"
                       value="${item.maxCount || ""}"
                       oninput="updateField('${item.id}','maxCount',this.value)">
            </div>
 
            <div class="card-field">
                <label>FREE CYCLES</label>
                <input type="number"
                       value="${item.freeCycles || 0}"
                       oninput="updateField('${item.id}','freeCycles',this.value)">
            </div>
        </div>
        <div class="card-field">
            <label>PRIORITY <span class="required">*</span></label>
            <input type="number" min="1"
                  id="priority-s4-${item.id}"
                  value="${item.priority ?? ""}"
                  oninput="updateField('${item.id}', 'priority', this.value)"
                  onblur="validatePriority('${item.id}', 's4')">
        </div>

        <div class="card-field">
            <label>MRP <span class="required">*</span></label>
            <input type="number" min="0"
                    id="mrp-s4-${item.id}"
                  value="${item.mrp ?? ""}"
                  placeholder="Enter MRP"
                  oninput="updateField('${item.id}', 'mrp', this.value)">
        </div>

        ${
          item.category !== "CA"
            ? `
        <div class="card-field">
            <label>SERVICE CODE</label>
            <input type="text"
                    id="service-code-s4-${item.id}"
                  value="${item.serviceCode ?? ""}"
                  placeholder="Enter service code"
                  oninput="updateField('${item.id}', 'serviceCode', this.value)">
        </div>

        <div class="card-field">
            <label>VIP PLAN <span class="required">*</span></label>
            <select id="vip-plan-s4-${item.id}"
                  onchange="updateField('${item.id}', 'vipPlan', this.value)">
                <option value="" ${!item.vipPlan ? "selected" : ""}>Select</option>
                <option value="Y" ${item.vipPlan === "Y" ? "selected" : ""}>Yes</option>
                <option value="N" ${item.vipPlan === "N" ? "selected" : ""}>No</option>
            </select>
        </div>
        `
      : ""
    }
    </div>

    ${item.category === "CA" ? renderCaSection(item) : ""}
    `;

  container.prepend(card);
}

// ---------- CA CONFIGURATIONS (CA-category only) ----------
// A CA-category AATP additionally carries its own package-level configuration
// (name, line limits, rollover, validity window) plus a set of
// service -> unit-type/units/charge mappings. Instead of the old "select 4
// fields, click an arrow to move them into a second list box" pattern, this
// is a plain add/remove row list — each mapping is its own inline row with
// its own remove button, so building several mappings is just repeated
// "Add Mapping" clicks rather than a multi-field-then-transfer motion.

// CA Service ~ Unit Type options for the mapping-row dropdown are no longer
// hardcoded — they come from GET /api/services?serviceName=... (see
// ServiceMetaDataController), which already returns them pre-formatted as
// "VOICE_COMMON~Sec" / "MMS_MT_COMMON~Msg" / etc. (see
// ServiceMetaDataRepository's SQL, which builds that exact string server-side).
// The four base service names below are the ones that expand out to the full
// set of CA service~unit rows (VOICE/MMS both have multiple call-type/category
// rows under one base name).
const CA_SERVICE_META_NAMES = ["VOICE", "SMS", "DATA", "MMS"];

// Cache is keyed per base service name now (not one flat list), since we
// only want to fetch/hold the types a given CA ATP actually needs —
// e.g. an ATP typed "VOICE" only ever touches the VOICE entry.
// Shape: { VOICE: { state: 'loading'|'loaded'|'error', options: [...] }, ... }
const caServiceUnitCache = {};

// item.type (set in addToCenter from typeLabelFromCodes) is a comma-
// separated string like "VOICE" or "VOICE,SMS". Maps it down to the base
// service names the API understands, falling back to the full set only if
// the type is missing/unrecognized (legacy/edge-case data) so the dropdown
// never ends up empty.
function resolveCaServiceTypes(itemType) {
  const matched = (itemType || "")
    .split(",")
    .map((t) => t.trim().toUpperCase())
    .filter((t) => CA_SERVICE_META_NAMES.includes(t));

  return matched.length ? matched : CA_SERVICE_META_NAMES;
}

// Entry point used by addToCenter()/initStep4(): fetches only the service
// types relevant to this one item, skipping any type that's already
// loading/loaded.
function ensureCaServiceUnitOptionsLoaded(item) {
  loadCaServiceUnitOptionsFor(resolveCaServiceTypes(item.type));
}

// Fetches the given base service names (e.g. ["VOICE"] or ["VOICE","SMS"])
// in one request — GET /api/services?serviceName=VOICE&serviceName=SMS —
// and caches each one separately. Re-renders any CA mapping rows already on
// screen once the data (or an error) comes back, since a card can render
// before the fetch resolves.
function loadCaServiceUnitOptionsFor(types) {
  const toFetch = types.filter((t) => {
    const entry = caServiceUnitCache[t];
    return !entry || (entry.state !== "loading" && entry.state !== "loaded");
  });
  if (!toFetch.length) return;

  toFetch.forEach((t) => {
    caServiceUnitCache[t] = { state: "loading", options: [] };
  });

  const query = toFetch
    .map((name) => `serviceName=${encodeURIComponent(name)}`)
    .join("&");

  fetch(`/api/services?${query}`)
    .then((res) => res.json())
    .then((data) => {
      // The response's serviceName is the composite display string (e.g.
      // "VOICE_COMMON~Sec" / "MMS_MT_COMMON~Msg") — its base service name is
      // always the prefix before the first underscore, so bucket each
      // result back to the type that produced it. Keep serviceId alongside
      // serviceName — the backend needs it, not just the display string.
      const byType = {};
      toFetch.forEach((t) => (byType[t] = []));

      (data || []).forEach((d) => {
        const base = String(d.serviceName).split("_")[0];
        if (byType[base]) {
          byType[base].push({ serviceId: d.serviceId, serviceName: d.serviceName });
        }
      });

      toFetch.forEach((t) => {
        caServiceUnitCache[t] = { state: "loaded", options: byType[t] };
      });

      refreshAllCaMappingRows();
    })
    .catch((err) => {
      console.error("Failed to load CA service list for", toFetch, err);
      toFetch.forEach((t) => {
        caServiceUnitCache[t] = { state: "error", options: [] };
      });
      refreshAllCaMappingRows();
    });
}

// Looks up the serviceId for a given serviceUnitType (the composite display
// string) by scanning whatever's currently cached. Used when a row's
// dropdown selection changes, so the mapping can carry serviceId alongside
// serviceUnitType without the <select>'s onchange needing to pass it
// directly.
function findCaServiceId(serviceName) {
  for (const type of Object.keys(caServiceUnitCache)) {
    const match = (caServiceUnitCache[type].options || []).find(
      (o) => o.serviceName === serviceName,
    );
    if (match) return match.serviceId;
  }
  return "";
}

// Combines the cache entries for one item's relevant types into a single
// {state, options} view for rendering: loading if any relevant type hasn't
// resolved yet, error only if every relevant type failed, otherwise the
// merged option list from whichever types succeeded.
function getCaOptionsStateForItem(itemType) {
  const types = resolveCaServiceTypes(itemType);

  const anyPending = types.some((t) => {
    const entry = caServiceUnitCache[t];
    return !entry || entry.state === "loading";
  });
  if (anyPending) return { state: "loading", options: [] };

  const allErrored = types.every(
    (t) => caServiceUnitCache[t] && caServiceUnitCache[t].state === "error",
  );
  if (allErrored) return { state: "error", options: [] };

  const options = types.flatMap(
    (t) => (caServiceUnitCache[t] && caServiceUnitCache[t].options) || [],
  );
  return { state: "loaded", options };
}

// Re-renders just the mapping-rows list for every CA card currently on
// screen (not the whole card), same pattern as refreshCaMappingRows().
function refreshAllCaMappingRows() {
  const state = getState();
  (state.s4 || [])
    .filter((item) => item.category === "CA")
    .forEach((item) => refreshCaMappingRows(item.id));
}

function defaultCaConfig() {
  return {
    defaultLinesAllowed: "",
    additionalChargePerLine: "0",
    packageRolloverYn: "",
    packageStartDate: "",
    packageEndDate: "",
    // At least one service mapping is mandatory, so a fresh CA config
    // always starts with one blank row instead of an empty list.
    serviceMappings: [blankCaMapping()],
  };
}

function blankCaMapping() {
  return {
    rowId: nextCaRowId(),
    serviceUnitType: "",
    serviceId: "",
    units: "",
    topupCharge: "",
    maxTransferLimit: "",
  };
}

// Ensures item.caConfig exists (lazily created so plain NORMAL items never
// carry the extra object) and returns it.
function ensureCaConfig(item) {
  if (!item.caConfig) item.caConfig = defaultCaConfig();
  if (!item.caConfig.serviceMappings || !item.caConfig.serviceMappings.length) {
    item.caConfig.serviceMappings = [blankCaMapping()];
  }
  return item.caConfig;
}

// Same "no past dates, end must be strictly after start" rule as Step 5's
// startDate/endDate handling, reimplemented per-card here since each CA
// item has its own pair of date inputs instead of one global pair.
function getCaTodayStr() {
  const t = new Date();
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}-${String(t.getDate()).padStart(2, "0")}`;
}

function addDaysToCaDateStr(dateStr, days) {
  const d = new Date(dateStr + "T00:00:00");
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function syncCaEndDateMin(id, startDate) {
  const endDateInput = document.getElementById(`ca-end-date-${id}`);
  if (!endDateInput) return;
  const todayStr = getCaTodayStr();
  endDateInput.setAttribute(
    "min",
    startDate && startDate >= todayStr ? addDaysToCaDateStr(startDate, 1) : todayStr,
  );
}

function onCaStartDateChange(id, value) {
  const startDateInput = document.getElementById(`ca-start-date-${id}`);
  const todayStr = getCaTodayStr();

  if (value && value < todayStr) {
    alert("Package start date cannot be in the past.");
    value = "";
    if (startDateInput) startDateInput.value = "";
  }

  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  const ca = ensureCaConfig(item);
  ca.packageStartDate = value;

  if (ca.packageEndDate && value && ca.packageEndDate <= value) {
    alert("Package end date has been cleared because it must be after the start date.");
    ca.packageEndDate = "";
    const endDateInput = document.getElementById(`ca-end-date-${id}`);
    if (endDateInput) endDateInput.value = "";
  }

  saveState(state);
  syncCaEndDateMin(id, value);
}

function onCaEndDateChange(id, value) {
  const endDateInput = document.getElementById(`ca-end-date-${id}`);
  const todayStr = getCaTodayStr();

  if (value && value < todayStr) {
    alert("Package end date cannot be in the past.");
    value = "";
    if (endDateInput) endDateInput.value = "";
  }

  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  const ca = ensureCaConfig(item);

  if (value && ca.packageStartDate && value <= ca.packageStartDate) {
    alert("Package end date must be after the start date.");
    value = "";
    if (endDateInput) endDateInput.value = "";
  }

  ca.packageEndDate = value;
  saveState(state);
}

function renderCaSection(item) {
  const ca = ensureCaConfig(item);
  const mappings = ca.serviceMappings || [];
  const todayStr = getCaTodayStr();
  const endDateMin =
    ca.packageStartDate && ca.packageStartDate >= todayStr
      ? addDaysToCaDateStr(ca.packageStartDate, 1)
      : todayStr;

  return `
    <div class="ca-config-block">
        <div class="ca-config-label">CA Configurations</div>

        <div class="card-grid">
            <div class="card-field">
                <label>Default Lines Allowed <span class="required">*</span></label>
                <input type="number" min="0"
                    value="${ca.defaultLinesAllowed ?? ""}"
                    placeholder="Enter number of lines"
                    oninput="updateCaField('${item.id}', 'defaultLinesAllowed', this.value)">
            </div>

            <div class="card-field">
                <label>Additional Charge Per Line</label>
                <input type="number" min="0"
                    value="${ca.additionalChargePerLine ?? 0}"
                    oninput="updateCaField('${item.id}', 'additionalChargePerLine', this.value)">
            </div>

            <div class="ca-date-row">
                <div class="card-field">
                    <label>Package Start Date <span class="required">*</span></label>
                    <input type="date"
                        id="ca-start-date-${item.id}"
                        min="${todayStr}"
                        value="${ca.packageStartDate ?? ""}"
                        onchange="onCaStartDateChange('${item.id}', this.value)">
                </div>

                <div class="card-field">
                    <label>Package End Date <span class="required">*</span></label>
                    <input type="date"
                        id="ca-end-date-${item.id}"
                        min="${endDateMin}"
                        value="${ca.packageEndDate ?? ""}"
                        onchange="onCaEndDateChange('${item.id}', this.value)">
                </div>
            </div>

            <div class="card-field">
                <label>Package Rollover <span class="required">*</span></label>
                <select onchange="updateCaField('${item.id}', 'packageRolloverYn', this.value)">
                    <option value="" ${!ca.packageRolloverYn ? "selected" : ""}>Select</option>
                    <option value="Y" ${ca.packageRolloverYn === "Y" ? "selected" : ""}>Yes</option>
                    <option value="N" ${ca.packageRolloverYn === "N" ? "selected" : ""}>No</option>
                </select>
            </div>
        </div>

        <div class="ca-mapping-section">
            <div class="ca-mapping-header">
                <label>Add Units To Services <span class="required">*</span></label>
                <button type="button" class="ca-mapping-add-btn" onclick="addCaMapping('${item.id}')">
                    <span class="material-icons">add</span> Add Mapping
                </button>
            </div>

            <div class="ca-mapping-col-headers">
                <span>CA Service ~ Unit Type <span class="required">*</span></span>
                <span>Units <span class="required">*</span></span>
                <span>Topup Charge</span>
                <span>Max Transfer Limit (%) <span class="required">*</span></span>
                <span></span>
            </div>

            <div class="ca-mapping-rows" id="ca-mapping-rows-${item.id}">
                ${mappings
                  .map((m) =>
                    renderCaMappingRow(
                      item.id,
                      item.type,
                      m,
                      mappings.length,
                      usedServiceUnitTypes(mappings, m.rowId),
                    ),
                  )
                  .join("")}
            </div>
        </div>
    </div>
  `;
}

// The service~unit types already picked by *other* rows for this item —
// each one can only be mapped once per CA package, so these get excluded
// from every other row's dropdown.
function usedServiceUnitTypes(mappings, excludeRowId) {
  return mappings
    .filter((m) => String(m.rowId) !== String(excludeRowId) && m.serviceUnitType)
    .map((m) => m.serviceUnitType);
}

function renderCaMappingRow(itemId, itemType, m, totalCount, usedValues) {
  return `
    <div class="ca-mapping-row" id="ca-row-${itemId}-${m.rowId}">
        <select onchange="updateCaMappingField('${itemId}', '${m.rowId}', 'serviceUnitType', this.value)">
            <option value="">Select CA Service ~ Unit Type</option>
            ${caServiceUnitOptionsMarkup(m.serviceUnitType, usedValues, itemType)}
        </select>
        <input type="text" placeholder="Units"
            value="${m.units ?? ""}"
            oninput="updateCaMappingField('${itemId}', '${m.rowId}', 'units', this.value)">
        <input type="text" placeholder="Topup Charge"
            value="${m.topupCharge ?? ""}"
            oninput="updateCaMappingField('${itemId}', '${m.rowId}', 'topupCharge', this.value)">
        <input type="text" placeholder="Max Transfer Limit (%)"
            value="${m.maxTransferLimit ?? ""}"
            oninput="updateCaMappingField('${itemId}', '${m.rowId}', 'maxTransferLimit', this.value)">
        ${
          totalCount > 1
            ? `<span class="ca-mapping-remove" title="Remove mapping" onclick="removeCaMapping('${itemId}', '${m.rowId}')">✕</span>`
            : `<span class="ca-mapping-remove ca-mapping-remove--disabled" title="At least one mapping is required">✕</span>`
        }
    </div>
  `;
}

// Builds the <option> list for the CA Service ~ Unit Type dropdown from
// whatever's currently cached. If the fetch hasn't resolved yet (or failed),
// shows a disabled placeholder instead of an empty/stale dropdown — and if
// the row's already-saved value isn't in the fetched list (e.g. old draft
// data), keeps it selectable rather than silently dropping it. Options
// already picked by another row (usedValues) are left out entirely, so the
// same service can't be mapped twice on one CA package.
function caServiceUnitOptionsMarkup(selectedValue, usedValues = [], itemType) {
  const { state, options: allOptions } = getCaOptionsStateForItem(itemType);

  if (state === "loading") {
    return `<option value="" disabled>Loading services…</option>`;
  }

  if (state === "error") {
    return `<option value="" disabled>Unable to load services — please refresh</option>`;
  }

  const options = allOptions.filter(
    (opt) => opt.serviceName === selectedValue || !usedValues.includes(opt.serviceName),
  );
  if (selectedValue && !options.some((opt) => opt.serviceName === selectedValue)) {
    // Row's already-saved value isn't in the fetched list (e.g. old draft
    // data) — keep it selectable rather than silently dropping it.
    options.push({ serviceId: "", serviceName: selectedValue });
  }

  return options
    .map(
      (opt) =>
        `<option value="${opt.serviceName}" ${selectedValue === opt.serviceName ? "selected" : ""}>${opt.serviceName}</option>`,
    )
    .join("");
}

// Single-value CA fields behave exactly like updateField(): write straight
// into state on every input, no re-render (so focus/caret position is never
// disturbed while typing).
function updateCaField(id, key, value) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  ensureCaConfig(item)[key] = value;
  saveState(state);
}

let caRowSeq = 0;
function nextCaRowId() {
  caRowSeq += 1;
  return `ca${Date.now()}${caRowSeq}`;
}

function addCaMapping(id) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  const ca = ensureCaConfig(item);
  // New mappings go on top, so the row someone just added is the first
  // thing they see rather than something they have to scroll down to.
  ca.serviceMappings.unshift(blankCaMapping());
  saveState(state);

  refreshCaMappingRows(id);
}

function removeCaMapping(id, rowId) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item || !item.caConfig) return;

  // At least one mapping is mandatory — refuse to remove the last one.
  if (item.caConfig.serviceMappings.length <= 1) return;

  item.caConfig.serviceMappings = item.caConfig.serviceMappings.filter(
    (m) => String(m.rowId) !== String(rowId),
  );
  saveState(state);

  refreshCaMappingRows(id);
}

// Mapping row fields also write straight into state on every input, same as
// updateCaField()/updateField() elsewhere — the only re-render is the add/
// remove case, PLUS a serviceUnitType change, since that changes which
// options are excluded from every other row's dropdown (each service can
// only be mapped once per CA package).
function updateCaMappingField(id, rowId, key, value) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item || !item.caConfig) return;

  const mapping = item.caConfig.serviceMappings.find(
    (m) => String(m.rowId) === String(rowId),
  );
  if (!mapping) return;

  if (key === "serviceUnitType" && value) {
    const alreadyMapped = item.caConfig.serviceMappings.some(
      (m) => String(m.rowId) !== String(rowId) && m.serviceUnitType === value,
    );
    if (alreadyMapped) {
      // Belt-and-suspenders: the dropdown already excludes taken services,
      // so this only fires on a stale/race-y selection. Revert the <select>
      // back to the row's saved value instead of silently accepting it.
      alert(`"${value}" is already mapped in another row — each service can only be mapped once.`);
      refreshCaMappingRows(id);
      return;
    }
  }

  mapping[key] = value;
  if (key === "serviceUnitType") {
    // Carry the matching serviceId along with the display string — it's
    // looked up from the cache rather than passed from the <select>'s
    // onchange, since that only gives us the option's text/value.
    mapping.serviceId = value ? findCaServiceId(value) : "";
  }
  saveState(state);

  if (key === "serviceUnitType") refreshCaMappingRows(id);
}

// Re-renders only the mapping-rows list for one card (not the whole card),
// so add/remove never disturbs the other CA fields or the base card fields.
function refreshCaMappingRows(id) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  const container = document.getElementById(`ca-mapping-rows-${id}`);
  if (!container) return;

  const mappings = (item.caConfig && item.caConfig.serviceMappings) || [];
  container.innerHTML = mappings
    .map((m) =>
      renderCaMappingRow(
        id,
        item.type,
        m,
        mappings.length,
        usedServiceUnitTypes(mappings, m.rowId),
      ),
    )
    .join("");
}

function updateField(id, key, value) {
  const state = getState();
  const item = state.s4.find((i) => String(i.id) === String(id));
  if (!item) return;

  // NOTE: priority is intentionally NOT validated here. This runs on every
  // keystroke (oninput), so validating a partially-typed value (e.g. the "1"
  // while typing "10") would incorrectly flag it as a duplicate/invalid.
  // Priority validation happens on blur instead — see validatePriority().
  item[key] = value;

  saveState(state);
}

function validatePriority(id, table) {
  const state = getState();
  const item = (state[table] || []).find((i) => String(i.id) === String(id));
  if (!item) return;

  const value = item.priority;
  if (value === "" || value === undefined || value === null) return;

  const input = document.getElementById(`priority-${table}-${id}`);

  if (Number(value) <= 0) {
    alert("Priority must be greater than 0.");
    item.priority = "";
    saveState(state);
    if (input) input.value = "";
    return;
  }

  const takenInS4 = (state.s4 || []).some(
    (i) =>
      !(table === "s4" && String(i.id) === String(id)) &&
      String(i.priority).trim() === String(value).trim(),
  );

  const takenInS3 = (state.s3 || []).some(
    (i) =>
      !(table === "s3" && String(i.id) === String(id)) &&
      String(i.priority).trim() === String(value).trim(),
  );

  if (takenInS4 || takenInS3) {
    alert(`Priority ${value} is already assigned to another package.`);
    item.priority = "";
    saveState(state);
    if (input) input.value = "";
    return;
  }
}

// ---------- VALIDITY ----------
function handleValidityChange(id, value) {
  updateField(id, "validity", value);

  const daysField = document.getElementById(`rental-period-${id}`);
  if (daysField) daysField.style.display = value === "O" ? "block" : "none";

  // Clear rentalPeriod when switching away from Others
  if (value !== "O") updateField(id, "rentalPeriod", "");
}

// ---------- RENEWAL ----------
function handleRenewalChange(id, value) {
  updateField(id, "renewal", value);

  const section = document.getElementById(`renewal-${id}`);
  section.style.display = value === "Yes" ? "contents" : "none";
}

// ---------- REMOVE ----------
function removeItem(id) {
  const state = getState();

  state.s4 = state.s4.filter((i) => String(i.id) !== String(id));
  saveState(state);

  document.getElementById(`card-s4-${id}`)?.remove();
}