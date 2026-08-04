// layout-drafts.js
// Split out of layout.js (originally lines 13-349) for modularity.
// Draft save/list/load/delete flow (open/close drafts panel, auto-save-on-exit, manual save, CA config attach on load).
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function saveDraftOnExit() {
  if (window.isInternalNavigation) return;

  const isBuilderPage = window.location.pathname.startsWith("/builder/step");
  if (!isBuilderPage) return;

  const state = JSON.parse(sessionStorage.getItem(STORAGE_KEYS.STATE) || "{}");
  const configName = sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME);
  const pkgType = sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE);

  const hasData =
    pkgType ||
    configName ||
    state?.s2?.length ||
    state?.s3?.length ||
    state?.s4?.length ||
    state?.s5?.length;

  if (!hasData) return;

  const now = new Date();
  const savedOn = now.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
  const savedTime = now.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });

  const username = sessionStorage.getItem(STORAGE_KEYS.USERNAME) || "guest";

  const payload = JSON.stringify({
    name: configName || "Untitled Draft",
    packageType: pkgType,
    tariffPackCategory: sessionStorage.getItem(STORAGE_KEYS.PKG_SUB_TYPE),
    periodicChargeID: sessionStorage.getItem(STORAGE_KEYS.PERIODIC_CHARGE_ID) || "",
    savedOn,
    savedTime,
    username,
    selectedSvcs_s2: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S2),
    selectedSvcs_s3: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S3),
    selectedSvcs_s4: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S4),
    selectedSvcs_s5: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S5),
    state,
  });

  navigator.sendBeacon(
    "/draft/save",
    new Blob([payload], { type: "application/json" }),
  );
}

window.addEventListener("beforeunload", saveDraftOnExit);

function openDrafts() {
  const overlay = document.getElementById("draftOverlay");
  overlay.style.display = "block";
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      overlay.classList.add("active");
    });
  });
  loadDrafts("draftOverlayList");
  const inp = document.getElementById("draftSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("draftSearchClear");
  if (clr) clr.style.opacity = "0";
}

function closeDrafts() {
  const overlay = document.getElementById("draftOverlay");
  overlay.classList.remove("active");

  // Hide after slide-out transition completes
  overlay.addEventListener("transitionend", function handler() {
    if (!overlay.classList.contains("active")) {
      overlay.style.display = "none";
    }
    overlay.removeEventListener("transitionend", handler);
  });
}

document.addEventListener("click", function (e) {
  if (e.target.id === "draftOverlay") {
    closeDrafts();
  }
});

function loadDrafts(targetId = "comp-list") {
  const container = document.getElementById(targetId);
  container.innerHTML = '<p class="sidebar-text">Loading...</p>';

  fetch("/draft/list")
    .then((res) => res.json())
    .then((drafts) => {
      if (!drafts.length) {
        container.innerHTML = `
                    <div class="drafts-empty">
                        <span class="material-icons">edit_note</span>
                        <p class="drafts-empty-title">No drafts yet</p>
                        <p class="drafts-empty-sub">
                            Your in-progress packages will appear here
                        </p>
                    </div>
                `;
        return;
      }

      window.ALL_DRAFTS = drafts;

      container.innerHTML = drafts
        .map(
          (d, i) => `
                <div class="draft-item" style="--i:${i}">
                    <div class="draft-info" onclick="loadDraft(${i})">
                        <span class="material-icons draft-icon">description</span>
                        <div class="draft-text">
                            <span class="draft-name">${d.name || "Untitled"}</span>
                            <span class="draft-meta">${d.savedOn} · ${d.savedTime}</span>
                        </div>
                    </div>
                    <span class="material-icons draft-delete"
                          onclick="deleteDraft(${i}, event)">delete_outline</span>
                </div>
            `,
        )
        .join("");
    })
    .catch(() => {
      container.innerHTML = '<p class="sidebar-text">Error loading drafts</p>';
    });
}

// Note: CA ATP reconstruction from a saved package now lives in
// mapCaAtpsToS5() (payload-builder.js) — see loadSavedPackage/
// loadRejectedPackage (layout-approvals-lists.js) and the clone/modify
// flow (layout-clone.js). This used to be attachCaConfigToS4(), which
// merged CA items back into s4; CA is its own Optional Services/s5 now.


function loadDraft(index) {
  const draft = window.ALL_DRAFTS[index];

  sessionStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(draft.state || {}));
  sessionStorage.setItem(STORAGE_KEYS.CONFIG_NAME, draft.name || "");
  // Accept the current field name (packageType/tariffPackCategory) or the
  // older name (pkgType/pkgSubType) still present in drafts saved before
  // this rename, so existing on-disk drafts keep loading correctly.
  sessionStorage.setItem(STORAGE_KEYS.PKG_TYPE, draft.packageType || draft.pkgType || "");
  sessionStorage.setItem(STORAGE_KEYS.PKG_SUB_TYPE, draft.tariffPackCategory || draft.pkgSubType || "");
  sessionStorage.setItem(STORAGE_KEYS.PERIODIC_CHARGE_ID, draft.periodicChargeID || "");

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S2, draft.selectedSvcs_s2 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S3, draft.selectedSvcs_s3 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S4, draft.selectedSvcs_s4 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S5, draft.selectedSvcs_s5 || "[]");

  sessionStorage.setItem(STORAGE_KEYS.LOADED_FROM_DRAFT, "true");

  window.isInternalNavigation = true;

  window.location.href = "/builder/step1";
}
async function deleteDraft(index, e) {
  e.stopPropagation();
  const draft = window.ALL_DRAFTS[index];
  if (!confirm(`Delete draft "${draft.name}"?`)) return;

  await fetch("/draft/save", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...draft, _delete: true }),
  });

  // Remove from in-memory array and re-render without a network round-trip
  window.ALL_DRAFTS.splice(index, 1);

  // Determine which container is currently active
  const overlayOpen = document
    .getElementById("draftOverlay")
    ?.classList.contains("active");
  const targetId = overlayOpen ? "draftOverlayList" : "comp-list";

  if (!window.ALL_DRAFTS.length) {
    document.getElementById(targetId).innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">edit_note</span>
                <p class="drafts-empty-title">No drafts yet</p>
                <p class="drafts-empty-sub">Your in-progress packages will appear here</p>
            </div>
        `;
    return;
  }

  // Re-render with corrected indices
  document.getElementById(targetId).innerHTML = window.ALL_DRAFTS.map(
    (d, i) => `
        <div class="draft-item" style="--i:${i}">
            <div class="draft-info" onclick="loadDraft(${i})">
                <span class="material-icons draft-icon">description</span>
                <div class="draft-text">
                    <span class="draft-name">${d.name || "Untitled"}</span>
                    <span class="draft-meta">${d.savedOn} · ${d.savedTime}</span>
                </div>
            </div>
            <span class="material-icons draft-delete"
                  onclick="deleteDraft(${i}, event)">delete_outline</span>
        </div>
    `,
  ).join("");
}

function manualSaveDraft() {
  const state = JSON.parse(sessionStorage.getItem(STORAGE_KEYS.STATE) || "{}");

  const configName = sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME);

  const pkgType = sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE);
  const hasData =
    pkgType ||
    configName ||
    state?.s2?.length ||
    state?.s3?.length ||
    state?.s4?.length ||
    state?.s5?.length;

  if (!hasData) {
    showToast("Nothing to save as draft");
    return;
  }
  if (!configName) {
    showToast("Please enter config Name to save");
    return;
  }
  const now = new Date();

  const savedOn = now.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });

  const savedTime = now.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });
  const payload = {
    name: configName,

    packageType: pkgType,

    tariffPackCategory: sessionStorage.getItem(STORAGE_KEYS.PKG_SUB_TYPE),

    periodicChargeID: sessionStorage.getItem(STORAGE_KEYS.PERIODIC_CHARGE_ID) || "",

    savedOn,

    savedTime,

    selectedSvcs_s2: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S2),

    selectedSvcs_s3: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S3),

    selectedSvcs_s4: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S4),

    selectedSvcs_s5: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S5),

    state,
  };
  fetch("/draft/save", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then(() => {
      showToast("Draft saved — " + configName, "success");
    })

    .catch(() => {
      showToast("Failed to save draft — " + configName);
    });
}

