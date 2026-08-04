// mapCaAtpItem, mapDefaultAtpItem, mapAllowedAtpItem, and buildPackageCore
// are now shared across all three submit flows — see payload-builder.js.

// Returns today's date as a yyyy-mm-dd string (local time, no timezone drift)
function getTodayStr() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
}

// Adds `days` days to a yyyy-mm-dd string and returns a yyyy-mm-dd string
function addDaysToDateStr(dateStr, days) {
  const d = new Date(dateStr + "T00:00:00");
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// End date must always be strictly after start date (and never in the past) —
// keep the endDate picker's floor in sync with whatever startDate is selected
function syncEndDateMin() {
  const endDateInput = document.getElementById("endDate");
  if (!endDateInput) return;
  const todayStr = getTodayStr();
  const state = getState();
  endDateInput.setAttribute(
    "min",
    state.startDate && state.startDate >= todayStr
      ? addDaysToDateStr(state.startDate, 1)
      : todayStr,
  );
}

window.addEventListener("DOMContentLoaded", () => {
  // Prevent picking a past date — floor the date pickers at today
  const todayStr = getTodayStr();
  const startDateInput = document.getElementById("startDate");
  if (startDateInput) startDateInput.setAttribute("min", todayStr);

  // Restore previously entered values
  const state = getState();
  if (state.price) document.getElementById("price").value = state.price;
  if (state.startDate)
    document.getElementById("startDate").value = state.startDate;
  if (state.endDate) document.getElementById("endDate").value = state.endDate;

  // endDate's floor depends on startDate, so sync it after restoring state
  syncEndDateMin();

  if (state.publicityCode)
    document.getElementById("publicityCode").value = state.publicityCode;
  if (state.isCorporate)
    document.getElementById("isCorporate").checked = state.isCorporate;

  // ── Clone mode: swap Save Config → Clone Package ──────────────
  if (sessionStorage.getItem(STORAGE_KEYS.CLONE_MODE) === "true") {
    const saveBtn = document.getElementById("saveConfigBtn");
    const cloneBtn = document.getElementById("clonePackageBtn");
    if (saveBtn) saveBtn.style.display = "none";
    if (cloneBtn) cloneBtn.style.display = "inline-flex";
    return;
  }

  // ── Approved edit mode: swap Save Config → Update Package ─────
  if (sessionStorage.getItem(STORAGE_KEYS.APPROVED_MODE) === "true") {
    const saveBtn = document.getElementById("saveConfigBtn");
    const updateBtn = document.getElementById("updatePackageBtn");
    if (saveBtn) saveBtn.style.display = "none";
    if (updateBtn) updateBtn.style.display = "inline-flex";
  }
});

// Each input writes to state immediately on change
function onPriceChange(val) {
  const state = getState();
  state.price = val;
  saveState(state);
}

function onStartDateChange(val) {
  const startDateInput = document.getElementById("startDate");
  if (val) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const selected = new Date(val + "T00:00:00");
    if (selected < today) {
      showToast("Package start date cannot be in the past.");
      val = "";
      if (startDateInput) startDateInput.value = "";
    }
  }
  const state = getState();
  state.startDate = val;

  // If the already-selected end date no longer falls after the new start
  // date, clear it so a stale, now-invalid end date can't slip through.
  if (state.endDate && val && state.endDate <= val) {
    showToast("Package end date has been cleared because it must be after the start date.");
    state.endDate = "";
    const endDateInput = document.getElementById("endDate");
    if (endDateInput) endDateInput.value = "";
  }

  saveState(state);
  syncEndDateMin();
}

function onEndDateChange(val) {
  const endDateInput = document.getElementById("endDate");
  if (val) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const selected = new Date(val + "T00:00:00");
    if (selected < today) {
      showToast("Package end date cannot be in the past.");
      val = "";
      if (endDateInput) endDateInput.value = "";
    }
  }
  const state = getState();
  if (val && state.startDate && val <= state.startDate) {
    showToast("Package end date must be after the start date.");
    val = "";
    if (endDateInput) endDateInput.value = "";
  }
  state.endDate = val;
  saveState(state);
}

function onPublicityCodeChange(val) {
  const state = getState();
  state.publicityCode = val;
  saveState(state);
}

function onCorporateChange(checked) {
  const state = getState();
  state.isCorporate = checked;
  saveState(state);
}

// ── Clone Package — CHANGE 3: direct vs modify modes ─────────────
async function clonePackageFromBuilder() {
  const state = getState();

  if (!state?.s2?.length) {
    showToast("Service Plan selection in Step 2 is required");
    return;
  }
  if (!state.price) {
    showToast("Enter charge amount");
    return;
  }
  if (!state.startDate) {
    showToast("Select start date");
    return;
  }
  if (!state.endDate) {
    showToast("Select end date");
    return;
  }
  if (state.endDate <= state.startDate) {
    showToast("Package end date must be after the start date.");
    return;
  }
  if (!state.publicityCode) {
    showToast("Enter publicity code");
    return;
  }

  const missingMrpS4Clone = (state.s4 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );
  if (missingMrpS4Clone) {
    showToast(
      `MRP is required for "${missingMrpS4Clone.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const missingMrpS5Clone = (state.s5 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );
  if (missingMrpS5Clone) {
    showToast(
      `MRP is required for "${missingMrpS5Clone.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const originalTpName = sessionStorage.getItem(STORAGE_KEYS.CLONE_TP_NAME);
  const networkId = sessionStorage.getItem(STORAGE_KEYS.CLONE_NETWORK_ID);
  const username =
    sessionStorage.getItem(STORAGE_KEYS.USERNAME) ||
    (typeof USERNAME !== "undefined" ? USERNAME : "");
  const cloneType = sessionStorage.getItem(STORAGE_KEYS.CLONE_TYPE) || "direct";
  const origPublicityId = sessionStorage.getItem(STORAGE_KEYS.CLONE_ORIGINAL_PUBLICITY_ID);
  const origTpName = sessionStorage.getItem(STORAGE_KEYS.CLONE_ORIGINAL_TP_NAME);

  if (!originalTpName || !networkId) {
    showToast(
      "Clone context missing. Please go back to the Clone page and try again.",
    );
    return;
  }

  const dataPayload = buildPackageCore({
    tariffPackageDesc: originalTpName,
    isUpdate: false,
    submittedOn: new Date().toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }),
  });

  // Detect if user changed tpName or publicityId
  const userEnteredTpName = state.publicityCode ? originalTpName : origTpName;
  const userEnteredPublicityId = state.publicityCode;

  const tpNameChanged = userEnteredTpName !== origTpName;
  const publicityChanged = userEnteredPublicityId !== origPublicityId;
  const isModify =
    cloneType === "modify" && (tpNameChanged || publicityChanged);

  const cloneBtn = document.getElementById("clonePackageBtn");
  if (cloneBtn) {
    cloneBtn.disabled = true;
    cloneBtn.textContent = "Cloning…";
  }

  try {
    // CHANGE 3: if modify mode with changed values, validate first
    if (isModify) {
      const validateRes = await fetch("/clone/validate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          networkId: Number(networkId),
          tpName: userEnteredTpName,
          publicityId: userEnteredPublicityId,
        }),
      });
      const validateResult = await validateRes.json();
      if (validateResult.status === "error") {
        showToast(validateResult.message || "Validation failed. Please try again.");
        return;
      }
    }

    // Build clone payload
    const payload = {
      tpName: originalTpName,
      networkId: Number(networkId),
      username: username,
      data: dataPayload,
    };

    if (isModify) {
      payload.cloneMode = "modify";
      payload.overrideTpName = userEnteredTpName;
      payload.overridePublicityId = userEnteredPublicityId;
    } else {
      payload.cloneMode = "direct";
    }

    const res = await fetch("/clone", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    const result = await res.json();

    if (result.status === "error") {
      showToast(result.message || "Clone failed. Please try again.");
      return;
    }

    showToast("Cloned successfully! New plan: " + result.clonedTpName, "success");

    // Clean up clone-specific session keys
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_MODE);
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_TYPE);
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_TP_NAME);
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_NETWORK_ID);
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_ORIGINAL_PUBLICITY_ID);
    sessionStorage.removeItem(STORAGE_KEYS.CLONE_ORIGINAL_TP_NAME);
    clearBuilderSession();

    window.isInternalNavigation = true;

    await new Promise((resolve) => setTimeout(resolve, 1200));

    window.location.href = "/builder/step1";
  } catch (err) {
    console.error("Clone error:", err);
    showToast("Server error during clone. Please try again.");
  } finally {
    if (cloneBtn) {
      cloneBtn.disabled = false;
      cloneBtn.textContent = "CLONE PACKAGE";
    }
  }
}
// ── Update Package (approved-edit flow) ─────────────────────────
async function updatePackage() {
  const state = getState();

  if (!state?.s2?.length) {
    showToast("Service Plan selection in Step 2 is required");
    return;
  }
  if (!state.price) {
    showToast("Enter charge amount");
    return;
  }
  if (!state.startDate) {
    showToast("Select start date");
    return;
  }
  if (!state.endDate) {
    showToast("Select end date");
    return;
  }
  if (state.endDate <= state.startDate) {
    showToast("Package end date must be after the start date.");
    return;
  }
  if (!state.publicityCode) {
    showToast("Enter publicity code");
    return;
  }

  const missingMrpS4Update = (state.s4 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );
  if (missingMrpS4Update) {
    showToast(
      `MRP is required for "${missingMrpS4Update.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const missingMrpS5Update = (state.s5 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );
  if (missingMrpS5Update) {
    showToast(
      `MRP is required for "${missingMrpS5Update.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const tariffPackageId = sessionStorage.getItem(STORAGE_KEYS.APPROVED_TARIFF_PACKAGE_ID);
  const networkId =
    sessionStorage.getItem(STORAGE_KEYS.NETWORK_ID) ||
    (typeof NETWORK_ID !== "undefined" ? NETWORK_ID : "");

  if (!tariffPackageId || !networkId) {
    showToast(
      "Update context missing. Please reload the package from the Approved list.",
    );
    return;
  }

  const core = buildPackageCore({
    tariffPackageDesc: sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME) || "",
    isUpdate: true,
    submittedOn: new Date().toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }),
    username: USERNAME,
  });

  // Unlike main submit/clone (where CA items belong only in caAtps),
  // update's backend syncs/deletes existing ATPs by comparing against
  // whatever is in allowedAtps — a CA item missing from allowedAtps would
  // be treated as removed and deleted from the package. So update's
  // allowedAtps must stay unfiltered and include both s4 (Normal/AATP) and
  // s5 (Optional Services/CA ATP), overriding buildPackageCore's default
  // CA-filtered allowedAtps.
  core.allowedAtps = [...(state.s4 || []), ...(state.s5 || [])].map(mapAllowedAtpItem);

  // Unlike main submit/clone (where chargeId is discarded/recomputed
  // server-side), update's backend requires a real, existing per-item
  // chargeId. Add it back on top of the shared core, index-aligned with
  // the same source arrays used to build each list above.
  const defaultAtpsSource = state.s3 || [];
  const allowedAtpsSource = [...(state.s4 || []), ...(state.s5 || [])];
  const caAtpsSource = state.s5 || [];

  core.defaultAtps = core.defaultAtps.map((mapped, i) => ({
    ...mapped,
    chargeId: defaultAtpsSource[i].chargeId || "",
  }));
  core.allowedAtps = core.allowedAtps.map((mapped, i) => ({
    ...mapped,
    chargeId: allowedAtpsSource[i].chargeId || "",
  }));
  core.caAtps = core.caAtps.map((mapped, i) => ({
    ...mapped,
    chargeId: caAtpsSource[i].chargeId || "",
  }));

  const payload = { data: core };

  const updateBtn = document.getElementById("updatePackageBtn");
  if (updateBtn) {
    updateBtn.disabled = true;
    updateBtn.textContent = "Updating…";
  }

  try {
    const res = await fetch(
      `/update/${tariffPackageId}?networkId=${networkId}`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      },
    );

    const result = await res.json();

    if (result.status === "error") {
      showToast(result.message || "Update failed. Please try again.");
      return;
    }

    showToast(
      "Package updated successfully! Plan: " +
        (sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME) || ""),
      "success",
    );
    clearBuilderSession();
    window.isInternalNavigation = true;

    await new Promise((resolve) => setTimeout(resolve, 1200));

    window.location.href = "/builder/step1";
  } catch (err) {
    console.error("Update package error:", err);
    showToast("Server error during update. Please try again.");
  } finally {
    if (updateBtn) {
      updateBtn.disabled = false;
      updateBtn.innerHTML =
        '<span class="material-icons">save</span> UPDATE PACKAGE';
    }
  }
}