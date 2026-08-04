// layout-nav-state.js
// Split out of layout.js (originally lines 350-854) for modularity.
// Module/step navigation (privilege gating, active module restore, step access checks), library search, and builder state get/save/reset.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function applyPrivilege() {
  const builderNode = document.getElementById("mn-builder");
  const approverNode = document.getElementById("mn-approver");
  const cloneNode = document.getElementById("mn-clone");

  const hasBuilder = PRIVILEGE_IDS.includes("P26125");
  const hasApprover = PRIVILEGE_IDS.includes("P26126");
  const hasClone = PRIVILEGE_IDS.includes("P26127");

  // Hide nodes individually
  if (!hasBuilder && builderNode) {
    builderNode.style.display = "none";
  }

  if (!hasApprover && approverNode) {
    approverNode.style.display = "none";
  }

  if (!hasClone && cloneNode) {
    cloneNode.style.display = "none";
  }
}

// ── Apply privilege on load ──
window.addEventListener("DOMContentLoaded", () => {
  applyPrivilege();
  restoreActiveModule();
  restoreConfigName();

  // mark all step navigation as internal so draft save is skipped
  document.querySelectorAll(".step-node, .main-node").forEach((link) => {
    link.addEventListener("click", () => {
      window.isInternalNavigation = true;
    });
  });

  // Auto-open Created TPs overlay after a rejected TP is re-submitted and saved
  if (new URLSearchParams(window.location.search).get("openSaved") === "1") {
    history.replaceState(null, "", window.location.pathname);
    openSaved();
  }
});

// ── Restore active module based on current URL ──
function restoreActiveModule() {
  const hasBuilder = PRIVILEGE_IDS.includes("P26125");
  const hasApprover = PRIVILEGE_IDS.includes("P26126");

  const path = window.location.pathname;

  // If already in admin
  if (path.startsWith("/builder/admin")) {
    setModuleUI("approver");
    return;
  }

  // If already in builder
  if (path.startsWith("/builder/step")) {
    setModuleUI("builder");
    return;
  }

  // FIRST LOAD DECISION
  const hasClone = PRIVILEGE_IDS.includes("P26127");

  if (!hasBuilder && hasApprover) {
    window.isInternalNavigation = true;
    window.location.href = "/builder/admin";
  } else if (hasBuilder) {
    window.isInternalNavigation = true;
    window.location.href = "/builder/step1";
  } else if (!hasBuilder && !hasApprover && hasClone) {
    // Clone-only user: land directly on the Clone TP's page
    openClone();
  }
}

// ── Activate module (called from main-rail anchor click) ──
function activateModule(module, el) {
  const hasBuilder = PRIVILEGE_IDS.includes("P26125");
  const hasApprover = PRIVILEGE_IDS.includes("P26126");
  const hasClone = PRIVILEGE_IDS.includes("P26127");

  if (module === "builder" && !hasBuilder) return false;
  if (module === "approver" && !hasApprover) return false;
  if (module === "clone" && !hasClone) return false;

  // RESTORE LIBRARY when switching back
  if (module === "builder") {
    const container = document.getElementById("comp-list");

    if (window._libraryCache !== undefined) {
      container.innerHTML = window._libraryCache;
    } else if (typeof refreshSidebar === "function") {
      refreshSidebar();
    }
  }

  setModuleUI(module);
  return true;
}

function setModuleUI(module) {
  const stepRail = document.getElementById("stepRail");
  const sidebar = document.getElementById("sidebar");
  const builderNode = document.getElementById("mn-builder");
  const approverNode = document.getElementById("mn-approver");
  const configInput = document.getElementById("configName");

  // ── Active node highlight ──
  const cloneNode = document.getElementById("mn-clone");
  const atpRulesNode = document.getElementById("mn-atprules");
  const atpRateNode = document.getElementById("mn-atprate");
  if (builderNode) builderNode.classList.toggle("active", module === "builder");
  if (approverNode)
    approverNode.classList.toggle("active", module === "approver");
  if (cloneNode) cloneNode.classList.toggle("active", module === "clone");
  if (atpRulesNode)
    atpRulesNode.classList.toggle("active", module === "atprules");
  if (atpRateNode)
    atpRateNode.classList.toggle("active", module === "atprate");

  if (module === "clone" || module === "atprules" || module === "atprate") {
    if (stepRail) stepRail.classList.add("collapsed");
    if (sidebar) sidebar.classList.add("collapsed");
    if (footerActions) footerActions.style.display = "none";
    if (configInput) configInput.style.display = "none";
  } else if (module === "builder") {
    if (stepRail) stepRail.classList.remove("collapsed");
    if (footerActions) footerActions.style.display = "flex";
    if (configInput) configInput.style.display = "block";

    // Sidebar visible only on steps 2, 3, 4, 5
    const step = getActiveStep();
    if (sidebar) {
      if (step === 2 || step === 3 || step === 4 || step === 5) {
        sidebar.classList.remove("collapsed");
        // Ensure search bar is present once sidebar is visible
        requestAnimationFrame(() => initLibrarySearch());
      } else {
        sidebar.classList.add("collapsed");
      }
    }

    // Hierarchy button visible only on step 6 (Pricing/finalize)
    applyHierarchyButtonVisibility(step);
  } else {
    // Approver — collapse step rail + sidebar (they're irrelevant)
    if (stepRail) stepRail.classList.add("collapsed");
    if (sidebar) sidebar.classList.add("collapsed");
    if (footerActions) footerActions.style.display = "none";
    if (configInput) configInput.style.display = "none";
  }
}

// Returns the current active step number (1-6) from the URL, or 0 for non-step pages
function getActiveStep() {
  const match = window.location.pathname.match(/step(\d)/);
  return match ? parseInt(match[1], 10) : 0;
}

// Shows/hides the Hierarchy button — only visible on step 6 (Pricing/finalize)
function applyHierarchyButtonVisibility(step) {
  // The Hierarchy button is the first btn-hierarchy in footerActions
  const hierarchyBtn = document.querySelector("#footerActions .btn-hierarchy");
  if (hierarchyBtn) {
    hierarchyBtn.style.display = step === 6 ? "inline-flex" : "none";
  }
}

// ═══════════════════════════════════════════════════════
//  LIBRARY SEARCH
// ═══════════════════════════════════════════════════════

// Call this once the sidebar is populated (from step JS after loadLibrary/refreshSidebar)
function initLibrarySearch() {
  const sidebar = document.getElementById("sidebar");
  if (!sidebar) return;

  // Don't inject twice
  if (document.getElementById("librarySearchInput")) return;

  const searchWrapper = document.createElement("div");
  searchWrapper.id = "librarySearchWrapper";
  searchWrapper.innerHTML = `
        <div class="library-search-box">
            <span class="material-icons library-search-icon">search</span>
            <input
                id="librarySearchInput"
                class="library-search-input"
                type="text"
                placeholder="Search packages..."
                autocomplete="off"
            />
            <span class="material-icons library-search-clear" id="librarySearchClear"
                  onclick="clearLibrarySearch()" title="Clear">close</span>
        </div>
    `;

  // Insert before the sidebar-content div
  const content = document.getElementById("comp-list");
  if (content && content.parentNode) {
    content.parentNode.insertBefore(searchWrapper, content);
  }

  document
    .getElementById("librarySearchInput")
    .addEventListener("input", function () {
      filterLibraryItems(this.value.trim());
      const clearBtn = document.getElementById("librarySearchClear");
      if (clearBtn) clearBtn.style.opacity = this.value ? "1" : "0";
    });
}

function clearLibrarySearch() {
  const input = document.getElementById("librarySearchInput");
  if (input) {
    input.value = "";
    input.dispatchEvent(new Event("input"));
    input.focus();
  }
}

function hasTrigramMatch(text, query) {
  if (!query) return true;

  return text.toLowerCase().includes(query.toLowerCase());
}

function filterLibraryItems(query) {
  const container = document.getElementById("comp-list");

  if (!container) return;

  const items = container.querySelectorAll(
    "[data-name], .comp-card, .service-card, .library-item, .comp-item, .sidebar-item",
  );

  let visibleCount = 0;

  if (!items.length) {
    const children = container.children;

    Array.from(children).forEach((el) => {
      const name = el.dataset.name || el.textContent || "";

      const match = hasTrigramMatch(name, query);

      el.style.display = match ? "" : "none";

      if (match) visibleCount++;
    });
  } else {
    items.forEach((el) => {
      const name =
        el.dataset.name ||
        el.querySelector("[data-name]")?.dataset.name ||
        el.querySelector(".comp-name, .item-name, .service-name, .card-title")
          ?.textContent ||
        el.textContent;

      const match = hasTrigramMatch(name, query);

      el.style.display = match ? "" : "none";

      if (match) visibleCount++;
    });
  }

  // remove old message if exists
  const oldMsg = document.getElementById("noResultsMsg");

  if (oldMsg) oldMsg.remove();

  // show message if no match
  if (visibleCount === 0 && query) {
    const msg = document.createElement("div");

    msg.id = "noResultsMsg";

    msg.className = "no-results";

    msg.innerHTML = "No Results Found";

    container.appendChild(msg);
  }
}

// getState()/saveState() now live in payload-builder.js (loaded in
// layout.html's <head>, before this file) — single shared definition.

// ── Restore config name across steps ──
function restoreConfigName() {
  const input = document.getElementById("configName");
  if (!input) return;

  const savedName = sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME) || "";
  input.value = savedName;

  input.addEventListener("input", () => {
    sessionStorage.setItem(STORAGE_KEYS.CONFIG_NAME, input.value);
  });
}

function resetBuilder() {
  const state = JSON.parse(sessionStorage.getItem(STORAGE_KEYS.STATE) || "{}");
  const pkgType = sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE);
  const configName = sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME);

  const isEmpty =
    !pkgType &&
    !configName &&
    !state.s2?.length &&
    !state.s3?.length &&
    !state.s4?.length &&
    !state.s5?.length;

  if (isEmpty) {
    showToast("Nothing to reset — Builder is already empty.");
    return;
  }

  if (!confirm("Reset all selections and start over?")) return;
  clearBuilderSession();
  window.isInternalNavigation = true;
  window.location.href = "/builder/step1";
}

// ═══════════════════════════════════════════════════════
//  STEP ACCESS GUARD
// ═══════════════════════════════════════════════════════
function checkStepAccess(targetStep) {
  const currentPath = window.location.pathname;

  if (currentPath.includes(`step${targetStep}`)) return true;
  if (targetStep === 1) return true;

  const pkgType = sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE);
  const state = getState();

  // const periodicCharge = document.getElementById("periodicCharge").value;
  // const periodicCharge = sessionStorage.getItem(STORAGE_KEYS.PERIODIC_CHARGE_ID);

  if (!pkgType) {
    showToast("Please select PREPAID or POSTPAID in Step 1");
    return false;
  }
  // if (!periodicCharge) {
  //     showToast("Please select a Periodic Charge");
  //     return;
  // }

  const hasStep2Data =
    state.s2 && Array.isArray(state.s2) && state.s2.length > 0;

  if (targetStep > 2 && !hasStep2Data) {
    showToast("Please select a Service Plan in Step 2");
    return false;
  }

  // Priority is mandatory on every DATP (s3) package before moving past Step 3
  const hasStep3Data =
    state.s3 && Array.isArray(state.s3) && state.s3.length > 0;

  if (targetStep > 3 && hasStep3Data) {
    const missing = state.s3.find(
      (item) =>
        item.priority === "" ||
        item.priority === null ||
        item.priority === undefined ||
        Number(item.priority) <= 0,
    );

    if (missing) {
      showToast(`Please enter a priority for "${missing.name}" before proceeding.`);
      const input = document.getElementById(`priority-s3-${missing.id}`);
      if (input) input.focus();
      return false;
    }
  }

  // Priority is mandatory on every AATP (s4) package before moving past Step 4
  const hasStep4Data =
    state.s4 && Array.isArray(state.s4) && state.s4.length > 0;

  if (targetStep > 4 && hasStep4Data) {
    const missing = state.s4.find(
      (item) =>
        item.priority === "" ||
        item.priority === null ||
        item.priority === undefined ||
        Number(item.priority) <= 0,
    );

    if (missing) {
      showToast(`Please enter a priority for "${missing.name}" before proceeding.`);
      const input = document.getElementById(`priority-s4-${missing.id}`);
      if (input) input.focus();
      return false;
    }

    const missingMrp = state.s4.find(
      (item) =>
        item.mrp === "" ||
        item.mrp === null ||
        item.mrp === undefined ||
        Number(item.mrp) < 0,
    );

    if (missingMrp) {
      showToast(
        `Please enter a valid MRP for "${missingMrp.name}" before proceeding.`,
      );
      const input = document.getElementById(`mrp-s4-${missingMrp.id}`);
      if (input) input.focus();
      return false;
    }

    // VIP Plan is mandatory for every AATP (s4 is Normal-only now — CA
    // moved to its own Optional Services/step5, which never has this field).
    const missingVipPlan = state.s4.find((item) => !item.vipPlan);

    if (missingVipPlan) {
      showToast(
        `Please select VIP Plan for "${missingVipPlan.name}" before proceeding.`,
      );
      const input = document.getElementById(
        `vip-plan-s4-${missingVipPlan.id}`,
      );
      if (input) input.focus();
      return false;
    }
  }

  // Priority + MRP + CA Configuration are mandatory on every Optional
  // Service (s5, CA ATP) package before moving past Step 5.
  const hasStep5Data =
    state.s5 && Array.isArray(state.s5) && state.s5.length > 0;

  if (targetStep > 5 && hasStep5Data) {
    const missingPriority = state.s5.find(
      (item) =>
        item.priority === "" ||
        item.priority === null ||
        item.priority === undefined ||
        Number(item.priority) <= 0,
    );

    if (missingPriority) {
      showToast(`Please enter a priority for "${missingPriority.name}" before proceeding.`);
      const input = document.getElementById(`priority-s5-${missingPriority.id}`);
      if (input) input.focus();
      return false;
    }

    const missingMrp = state.s5.find(
      (item) =>
        item.mrp === "" ||
        item.mrp === null ||
        item.mrp === undefined ||
        Number(item.mrp) < 0,
    );

    if (missingMrp) {
      showToast(
        `Please enter a valid MRP for "${missingMrp.name}" before proceeding.`,
      );
      const input = document.getElementById(`mrp-s5-${missingMrp.id}`);
      if (input) input.focus();
      return false;
    }

    // Package-level fields (all mandatory except Additional Charge Per
    // Line) + every mapping row (mandatory except Topup Charge) must be
    // filled before leaving Step 5. Mirrors the same check that runs again
    // in saveConfiguration() as a final safety net.
    const isBlankCa = (v) => v === "" || v === null || v === undefined;

    for (const item of state.s5) {
      const ca = item.caConfig || {};

      if (
        isBlankCa(ca.defaultLinesAllowed) ||
        isBlankCa(ca.packageRolloverYn) ||
        isBlankCa(ca.packageStartDate) ||
        isBlankCa(ca.packageEndDate)
      ) {
        showToast(
          `Please fill all mandatory CA Configuration fields for "${item.name}" before proceeding.`,
        );
        return false;
      }

      const caMappings = ca.serviceMappings || [];

      if (!caMappings.length) {
        showToast(
          `At least one service mapping is required for "${item.name}" before proceeding.`,
        );
        return false;
      }

      const badCaMapping = caMappings.find(
        (m) =>
          isBlankCa(m.serviceUnitType) ||
          isBlankCa(m.units) ||
          isBlankCa(m.maxTransferLimit),
      );

      if (badCaMapping) {
        showToast(
          `Please fill CA Service, Units, and Max Transfer Limit for every mapping in "${item.name}" before proceeding.`,
        );
        return false;
      }
    }
  }

  return true;
}

// ═══════════════════════════════════════════════════════
//  USER MENU
// ═══════════════════════════════════════════════════════