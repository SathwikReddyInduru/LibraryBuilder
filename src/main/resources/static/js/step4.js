// ---------- STATE ----------
// getState()/saveState() now live in payload-builder.js (loaded in
// layout.html's <head>, before this file) — single shared definition.

// ---------- INIT ----------
function initStep4() {
  const state = getState();

  if (!state.s4) state.s4 = [];

  // Always re-render from state (handles both fresh load and bfcache restore)
  document.getElementById("dropArea").innerHTML = "";
  state.s4.forEach((item) => renderCard(item));

  // Restore pills into in-memory array
  selectedSvcs = [];
  const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S4) || "[]");

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

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S4, JSON.stringify(selectedSvcs));

  refreshSidebar();
}

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

  // VOICE/SMS/DATA -> 1/2/3 (service type codes)
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

  fetch(`/builder/step4/filter?types=${types}`)
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
// readable labels.
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
    showToast(`"${name}" is already selected in Default ATPs (DATP).`);
    return;
  }

  // Self-duplicate check
  if (state.s4.find((i) => String(i.id) === String(id))) return;

  const item = {
    id: String(id),
    name: name,
    type: typeLabelFromCodes(serviceTypes),
    category: "NORMAL",
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
    </div>
    `;

  container.prepend(card);
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

// Priority must be unique across DATP (s3), AATP (s4) and Optional
// Services/CA ATP (s5) — the whole package shares one priority sequence.
function validatePriority(id, table) {
  const state = getState();
  const item = (state[table] || []).find((i) => String(i.id) === String(id));
  if (!item) return;

  const value = item.priority;
  if (value === "" || value === undefined || value === null) return;

  const input = document.getElementById(`priority-${table}-${id}`);

  if (Number(value) <= 0) {
    showToast("Priority must be greater than 0.");
    item.priority = "";
    saveState(state);
    if (input) input.value = "";
    return;
  }

  const isTaken = (arr, arrTable) =>
    (arr || []).some(
      (i) =>
        !(arrTable === table && String(i.id) === String(id)) &&
        String(i.priority).trim() === String(value).trim(),
    );

  if (
    isTaken(state.s3, "s3") ||
    isTaken(state.s4, "s4") ||
    isTaken(state.s5, "s5")
  ) {
    showToast(`Priority ${value} is already assigned to another package.`);
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