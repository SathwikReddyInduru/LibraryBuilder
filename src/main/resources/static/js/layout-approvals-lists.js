// layout-approvals-lists.js
// Split out of layout.js (originally lines 1554-2265) for modularity.
// Approve/reject actions, and the saved / approved / rejected package list panels (open/close/load/filter) plus step back/next navigation.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function approvePackage(tpName, btn) {
  event.stopImmediatePropagation();

  if (!confirm("Approve " + tpName + " ?")) return;

  const originalHTML = btn ? btn.innerHTML : null;
  if (btn) {
    btn.disabled = true;
    btn.innerHTML = '<span class="btn-spinner"></span>Approving…';
  }

  fetch("/approve/" + tpName, {
    method: "POST",
  })
    .then((res) => {
      if (!res.ok) throw new Error("Approve failed");

      return res.json();
    })
    .then((data) => {
      console.log("APPROVED", data);

      if (data.status === "error") {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = originalHTML;
        }
        const detail = data.failedTable
          ? "\nFailed at: " + data.failedStep + " -> " + data.failedTable
          : "";
        showToast(
          " Approve failed:\n" + (data.message || "Unknown error") + detail,
        );
        return;
      }

      showToast(
        tpName + " approved. Tariff Package Created with ID: " + data.tariffPackageId,
        "success",
      );
      setTimeout(() => {
        window.location.href = "/builder/admin";
      }, 1200);
    })
    .catch((err) => {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = originalHTML;
      }
      console.error(err);

      showToast(" Error approving tariff package.");
    });
}

/* REJECT — shows remarks modal first */
function rejectPackage(tpName, btn) {
  event.stopImmediatePropagation();

  // Build modal if it doesn't exist yet
  if (!document.getElementById("rejectRemarksModal")) {
    const modal = document.createElement("div");
    modal.id = "rejectRemarksModal";
    modal.style.cssText = [
      "display:none",
      "position:fixed",
      "inset:0",
      "z-index:9999",
      "background:rgba(0,0,0,0.45)",
      "align-items:center",
      "justify-content:center",
    ].join(";");
    modal.innerHTML = `
            <div style="background:#fff;border-radius:12px;padding:32px 28px;width:440px;max-width:90vw;box-shadow:0 8px 32px rgba(0,0,0,0.18);">
                <h3 style="margin:0 0 6px;font-size:18px;color:#1e293b;">Reject Tariff Package</h3>
                <p id="rejectModalTpName" style="margin:0 0 18px;font-size:13px;color:#64748b;font-weight:600;"></p>
                <label style="display:block;font-size:13px;font-weight:600;color:#374151;margin-bottom:6px;">
                    Remarks <span style="color:#ef4444;">*</span>
                </label>
                <textarea id="rejectRemarksInput"
                    placeholder="Enter reason for rejection..."
                    rows="4"
                    style="width:100%;box-sizing:border-box;padding:10px 12px;border:1.5px solid #e2e8f0;border-radius:8px;font-size:14px;resize:vertical;outline:none;font-family:inherit;"
                    oninput="document.getElementById('rejectRemarksError').style.display='none'">
                </textarea>
                <p id="rejectRemarksError" style="display:none;color:#ef4444;font-size:12px;margin:4px 0 0;">Please enter remarks before rejecting.</p>
                <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px;">
                    <button onclick="closeRejectModal()"
                        style="padding:9px 20px;border:1.5px solid #e2e8f0;border-radius:8px;background:#fff;cursor:pointer;font-size:14px;color:#374151;">
                        Cancel
                    </button>
                    <button onclick="submitReject()"
                        style="padding:9px 20px;border:none;border-radius:8px;background:#ef4444;color:#fff;cursor:pointer;font-size:14px;font-weight:600;">
                        Confirm Reject
                    </button>
                </div>
            </div>`;
    document.body.appendChild(modal);
  }

  // Store tpName on the modal for submitReject to use
  const modal = document.getElementById("rejectRemarksModal");
  modal.dataset.tpName = tpName;
  document.getElementById("rejectModalTpName").textContent = tpName;
  document.getElementById("rejectRemarksInput").value = "";
  document.getElementById("rejectRemarksError").style.display = "none";
  modal.style.display = "flex";
}

function closeRejectModal() {
  const modal = document.getElementById("rejectRemarksModal");
  if (modal) modal.style.display = "none";
}

function submitReject() {
  const modal = document.getElementById("rejectRemarksModal");
  const tpName = modal.dataset.tpName;
  const remarks = document.getElementById("rejectRemarksInput").value.trim();

  if (!remarks) {
    document.getElementById("rejectRemarksError").style.display = "block";
    return;
  }

  closeRejectModal();

  fetch("/reject/" + tpName, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ remarks: remarks }),
  })
    .then((res) => {
      if (!res.ok) throw new Error("Reject failed");

      return res.json();
    })
    .then((data) => {
      console.log("REJECTED", data);

      showToast(tpName + " rejected", "success");

      setTimeout(() => {
        window.location.href = "/builder/admin";
      }, 1200);
    })
    .catch((err) => {
      console.error(err);

      showToast("Error rejecting tariff");
    });
}

function openSaved() {
  const overlay = document.getElementById("savedOverlay");
  overlay.style.display = "block";
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      overlay.classList.add("active");
    });
  });
  loadSaved();
  const inp = document.getElementById("savedSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("savedSearchClear");
  if (clr) clr.style.opacity = "0";
}

function loadSaved() {
  const container = document.getElementById("savedOverlayList");

  container.innerHTML = '<p class="sidebar-text">Loading...</p>';

  fetch("/saved/list")
    .then((res) => res.json())

    .then((data) => {
      // convert map → array
      const configs = Object.values(data);

      if (!configs.length) {
        container.innerHTML = `
 
                <div class="drafts-empty">
                    <span class="material-icons">
                        inventory_2
                    </span>
 
                    <p class="drafts-empty-title">
                        No saved configs
                    </p>
                </div>
            `;

        return;
      }

      window.ALL_SAVED = configs;

      container.innerHTML = configs
        .map(
          (c, i) => `
                    <div class="draft-item saved">
                        <div class="draft-info"
                            onclick="loadSavedPackage(${i})">
                            <span class="material-icons draft-icon">inventory_2</span>
                            <div class="draft-text">
                                <span class="draft-name">${c.tpName}</span>
                                <span class="draft-meta">${c.username} · ${c.data?.submittedOn || ""}</span>
                            </div>
                        </div>

                        <span class="material-icons draft-delete"
                            onclick="deleteSaved('${c.tpName}', event)">
                            delete_outline
                        </span>
                    </div>
            `,
        )
        .join("");
    })

    .catch(() => {
      container.innerHTML = "<p>Error loading configs</p>";
    });
}

function loadSavedPackage(index) {
  const config = window.ALL_SAVED[index];

  const d = config.data;

  const state = rebuildStateFromPackage(d);

  /*
       SAME keys as draft loader
    */

  sessionStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(state));

  sessionStorage.setItem(STORAGE_KEYS.CONFIG_NAME, config.tpName || "");

  sessionStorage.setItem(STORAGE_KEYS.PKG_TYPE, d.packageType || "");

  sessionStorage.setItem(STORAGE_KEYS.PKG_SUB_TYPE, d.tariffPackCategory || "");

  sessionStorage.setItem(STORAGE_KEYS.PERIODIC_CHARGE_ID, d.periodicChargeID || "");

  /*
       IMPORTANT for sidebar selections
    */

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S2, d.selectedSvcs_s2 || "[]");

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S3, d.selectedSvcs_s3 || "[]");

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S4, d.selectedSvcs_s4 || "[]");

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S5, d.selectedSvcs_s5 || "[]");

  sessionStorage.setItem(STORAGE_KEYS.IS_UPDATE, "true");

  window.isInternalNavigation = true;

  window.location.href = "/builder/step1";
}

function deleteSaved(tpName, e) {
  e.stopPropagation();

  if (!confirm(`Delete "${tpName}"?`)) return;

  fetch("/saved/delete/" + tpName, {
    method: "POST",
  })
    .then(() => {
      // remove from UI instantly
      loadSaved(); // reload list
    })
    .catch(() => {
      showToast("Delete failed ");
    });
}

function closeSaved() {
  const overlay = document.getElementById("savedOverlay");
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
  if (e.target.id === "savedOverlay") {
    closeSaved();
  }
});

// ── APPROVED TPs OVERLAY ─────────────────────────────────────────

function openApproved() {
  const overlay = document.getElementById("approvedOverlay");
  overlay.style.display = "block";
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      overlay.classList.add("active");
    });
  });
  loadApproved();
  const inp = document.getElementById("approvedSearchInput");
  if (inp) {
    inp.value = "";
  }
  const clr = document.getElementById("approvedSearchClear");
  if (clr) {
    clr.style.opacity = "0";
  }
}

function closeApproved() {
  const overlay = document.getElementById("approvedOverlay");
  overlay.classList.remove("active");
  overlay.addEventListener("transitionend", function handler() {
    if (!overlay.classList.contains("active")) {
      overlay.style.display = "none";
    }
    overlay.removeEventListener("transitionend", handler);
  });
}

document.addEventListener("click", function (e) {
  if (e.target.id === "approvedOverlay") closeApproved();
});

function loadApproved() {
  const container = document.getElementById("approvedOverlayList");
  container.innerHTML = '<p class="sidebar-text">Loading...</p>';

  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  if (!networkId) {
    container.innerHTML =
      '<p class="sidebar-text">Network ID not found in session.</p>';
    return;
  }

  fetch("/tariffpacks?networkId=" + networkId)
    .then((res) => {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.json();
    })
    .then((plans) => {
      if (!plans || !plans.length) {
        container.innerHTML = `
                    <div class="drafts-empty">
                        <span class="material-icons">check_circle</span>
                        <p class="drafts-empty-title">No approved TPs yet</p>
                    </div>`;
        return;
      }

      window.ALL_APPROVED = plans;

      container.innerHTML = plans
        .map(
          (p, i) => `
                <div class="draft-item saved" style="--i:${i}">
                    <div class="draft-info" onclick="openApprovedTpDetails(${i})" style="cursor:pointer;">
                        <span class="material-icons draft-icon" style="color:#22c55e;">check_circle</span>
                        <div class="draft-text">
                            <span class="draft-name">${p.tariffPackageDesc}</span>
                            <span class="draft-meta">${p.rentalType || ""} · ${p.rentalPeriod != null ? p.rentalPeriod + " days" : ""}</span>
                        </div>
                    </div>
                </div>
            `,
        )
        .join("");
    })
    .catch(() => {
      container.innerHTML =
        '<p class="sidebar-text">Error loading approved TPs</p>';
    });
}

function filterApproved(query) {
  const clr = document.getElementById("approvedSearchClear");
  if (clr) clr.style.opacity = query ? "1" : "0";

  const plans = window.ALL_APPROVED || [];
  const container = document.getElementById("approvedOverlayList");
  if (!plans.length) return;

  const q = query.toLowerCase().trim();

  const filtered = q
    ? plans.filter((p) => {
        const name = (p.tariffPackageDesc || "").toLowerCase();
        const rental = (p.rentalType || "").toLowerCase();
        const fee = Number(p.activationFee || 0).toLocaleString("en-IN");
        return name.includes(q) || rental.includes(q) || fee.includes(q);
      })
    : plans;

  if (!filtered.length) {
    container.innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">search_off</span>
                <p class="drafts-empty-title">No results for "${query}"</p>
            </div>`;
    return;
  }

  container.innerHTML = filtered
    .map((p, i) => {
      const isOthers = (p.rentalType || "").toLowerCase() === "others";
      const meta = isOthers
        ? p.rentalPeriod != null
          ? p.rentalPeriod + " days"
          : "Others"
        : p.rentalType || "";
      const fee = Number(p.activationFee || 0).toLocaleString("en-IN");
      const originalIndex = plans.indexOf(p);
      return `
        <div class="draft-item saved" style="--i:${i}">
            <div class="draft-info" onclick="openApprovedTpDetails(${originalIndex})" style="cursor:pointer;">
                <span class="material-icons draft-icon" style="color:#22c55e;">check_circle</span>
                <div class="draft-text">
                    <span class="draft-name">${p.tariffPackageDesc}</span>
                    <span class="draft-meta">${meta}</span>
                </div>
            </div>
            <span class="draft-delete" style="cursor:default;font-style:normal;font-size:13px;font-weight:600;color:var(--text-muted,#888);">₸${fee}</span>
        </div>`;
    })
    .join("");
}

function clearApprovedSearch() {
  const inp = document.getElementById("approvedSearchInput");
  if (inp) {
    inp.value = "";
  }
  const clr = document.getElementById("approvedSearchClear");
  if (clr) {
    clr.style.opacity = "0";
  }
  filterApproved("");
}

// Clicking an approved TP card now opens the same details modal used in the clone
function openApprovedTpDetails(index) {
  const plan = window.ALL_APPROVED[index];
  if (!plan) return;
  openCloneTree(
    encodeURIComponent(plan.tariffPackageDesc),
    plan.tariff_package_id,
    "approved",
  );
}

// ── REJECTED TPs OVERLAY ─────────────────────────────────────────

function openRejected() {
  const overlay = document.getElementById("rejectedOverlay");
  overlay.style.display = "block";
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      overlay.classList.add("active");
    });
  });
  loadRejected();
  const inp = document.getElementById("rejectedSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("rejectedSearchClear");
  if (clr) clr.style.opacity = "0";
}

function closeRejected() {
  const overlay = document.getElementById("rejectedOverlay");
  overlay.classList.remove("active");
  overlay.addEventListener("transitionend", function handler() {
    if (!overlay.classList.contains("active")) {
      overlay.style.display = "none";
    }
    overlay.removeEventListener("transitionend", handler);
  });
}

document.addEventListener("click", function (e) {
  if (e.target.id === "rejectedOverlay") closeRejected();
});

function loadRejected() {
  const container = document.getElementById("rejectedOverlayList");
  container.innerHTML = '<p class="sidebar-text">Loading...</p>';

  fetch("/rejected/list")
    .then((res) => res.json())
    .then((data) => {
      const items = Object.values(data);
      if (!items.length) {
        container.innerHTML = `
                    <div class="drafts-empty">
                        <span class="material-icons">cancel</span>
                        <p class="drafts-empty-title">No rejected TPs</p>
                    </div>`;
        return;
      }

      window.ALL_REJECTED = items;

      container.innerHTML = items
        .map(
          (c, i) => `
                <div class="draft-item saved" style="--i:${i}">
                    <div class="draft-info" onclick="loadRejectedPackage(${i})" style="cursor:pointer;">
                        <div class="draft-text">
                            <span class="draft-name">${c.tpName}</span>
                            <span class="draft-meta">${c.username || ""} · ${c.rejectedOn ? c.rejectedOn.substring(0, 10) : ""}</span>
                            <span class="draft-meta" style="color:#ef4444; margin-top:3px;">
                                <b>Remarks:</b> ${c.remarks || "—"}
                            </span>
                        </div>
                    </div>
                </div>
            `,
        )
        .join("");
    })
    .catch(() => {
      container.innerHTML =
        '<p class="sidebar-text">Error loading rejected TPs</p>';
    });
}

function loadRejectedPackage(index) {
  const config = window.ALL_REJECTED[index];
  const d = config.data;

  // Convert rejected TP format → builder state (same shape as loadSavedPackage)
  const state = rebuildStateFromPackage(d);

  sessionStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(state));
  sessionStorage.setItem(STORAGE_KEYS.CONFIG_NAME, config.tpName || "");
  sessionStorage.setItem(STORAGE_KEYS.PKG_TYPE, d.packageType || "");
  sessionStorage.setItem(STORAGE_KEYS.PKG_SUB_TYPE, d.tariffPackCategory || "");
  sessionStorage.setItem(STORAGE_KEYS.PERIODIC_CHARGE_ID, d.periodicChargeID || "");

  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S2, d.selectedSvcs_s2 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S3, d.selectedSvcs_s3 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S4, d.selectedSvcs_s4 || "[]");
  sessionStorage.setItem(STORAGE_KEYS.SELECTED_SVCS_S5, d.selectedSvcs_s5 || "[]");

  // Mark as coming from rejected so saveConfiguration() can remove the old
  // rejected entry after a successful re-submit (see layout-save-configuration.js)
  sessionStorage.setItem(STORAGE_KEYS.REJECTED_TP_NAME, config.tpName);
  sessionStorage.setItem(STORAGE_KEYS.IS_UPDATE, "true");

  window.isInternalNavigation = true;
  window.location.href = "/builder/step1";
}

function goBack() {
  const step = getActiveStep();

  if (step <= 1) return;

  window.isInternalNavigation = true;
  window.location.href = `/builder/step${step - 1}`;
}

function goNext() {
  const step = getActiveStep();

  if (!checkStepAccess(step + 1)) return;

  if (step >= 6) return;

  window.isInternalNavigation = true;
  window.location.href = `/builder/step${step + 1}`;
}

// ═══════════════════════════════════════════════════════
//  PLAN HOVER TOOLTIP
// ═══════════════════════════════════════════════════════