// Builds the caAtps entry for one CA-category AATP item, mirroring the shape
// used in the main save-config payload (see layout.js saveConfiguration()).
function buildCaAtpEntry(item) {
  const ca = item.caConfig || {};

  return {
    servicePackageId: Number(item.id),
    packageName: item.name,
    chargeId: item.chargeId || "",
    validity: item.validity,
    rentalPeriod: item.validity === "O" ? item.rentalPeriod || 1 : "",
    midnightExpiry: item.midnightExpiry,
    renewal: item.renewal,
    rental: item.rental || 0,
    maxCount: item.maxCount || 0,
    freeCycles: item.freeCycles || 0,
    priority:
      item.priority !== "" &&
      item.priority !== null &&
      item.priority !== undefined &&
      Number(item.priority) > 0
        ? Number(item.priority)
        : 0,
    mrp: item.mrp || 0,
    defaultLinesAllowed: ca.defaultLinesAllowed || 0,
    additionalChargePerLine: ca.additionalChargePerLine || 0,
    packageRolloverYn: ca.packageRolloverYn || "",
    packageStartDate: ca.packageStartDate || "",
    packageEndDate: ca.packageEndDate || "",
    serviceMappings: (ca.serviceMappings || []).map((m) => ({
      serviceUnitType: m.serviceUnitType || "",
      serviceId: m.serviceId || "",
      units: m.units || 0,
      topupCharge: m.topupCharge || 0,
      maxTransferLimit: m.maxTransferLimit || 0,
    })),
  };
}

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
  if (sessionStorage.getItem("cloneMode") === "true") {
    const saveBtn = document.getElementById("saveConfigBtn");
    const cloneBtn = document.getElementById("clonePackageBtn");
    if (saveBtn) saveBtn.style.display = "none";
    if (cloneBtn) cloneBtn.style.display = "inline-flex";
    return;
  }

  // ── Approved edit mode: swap Save Config → Update Package ─────
  if (sessionStorage.getItem("approvedMode") === "true") {
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
      alert("Package start date cannot be in the past.");
      val = "";
      if (startDateInput) startDateInput.value = "";
    }
  }
  const state = getState();
  state.startDate = val;

  // If the already-selected end date no longer falls after the new start
  // date, clear it so a stale, now-invalid end date can't slip through.
  if (state.endDate && val && state.endDate <= val) {
    alert("Package end date has been cleared because it must be after the start date.");
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
      alert("Package end date cannot be in the past.");
      val = "";
      if (endDateInput) endDateInput.value = "";
    }
  }
  const state = getState();
  if (val && state.startDate && val <= state.startDate) {
    alert("Package end date must be after the start date.");
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
    alert("Service Plan selection in Step 2 is required");
    return;
  }
  if (!state.price) {
    alert("Enter charge amount");
    return;
  }
  if (!state.startDate) {
    alert("Select start date");
    return;
  }
  if (!state.endDate) {
    alert("Select end date");
    return;
  }
  if (state.endDate <= state.startDate) {
    alert("Package end date must be after the start date.");
    return;
  }
  if (!state.publicityCode) {
    alert("Enter publicity code");
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
    alert(
      `MRP is required for "${missingMrpS4Clone.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const originalTpName = sessionStorage.getItem("cloneTpName");
  const networkId = sessionStorage.getItem("cloneNetworkId");
  const username =
    sessionStorage.getItem("username") ||
    (typeof USERNAME !== "undefined" ? USERNAME : "");
  const cloneType = sessionStorage.getItem("cloneType") || "direct";
  const origPublicityId = sessionStorage.getItem("cloneOriginalPublicityId");
  const origTpName = sessionStorage.getItem("cloneOriginalTpName");

  if (!originalTpName || !networkId) {
    alert(
      "Clone context missing. Please go back to the Clone page and try again.",
    );
    return;
  }

  function formatDateToMMDDYYYY(dateStr) {
    if (!dateStr) return "12/31/2030";
    const [year, month, day] = dateStr.split("-");
    return `${month}/${day}/${year}`;
  }

  // CHANGE 7: no chargeId in ATP objects
  const atpMapper = (item) => ({
    servicePackageId: Number(item.id),
    packageName: item.name,
    validity: item.validity,
    rentalPeriod: item.validity === "O" ? item.rentalPeriod || 1 : "",
    midnightExpiry: item.midnightExpiry,
    renewal: item.renewal,
    rental: item.rental || 0,
    maxCount: item.maxCount || 0,
    freeCycles: item.freeCycles || 0,
    mrp: item.mrp || 0,
    category: item.category,
    serviceCode: item.serviceCode,
    vipPlan: item.vipPlan,
  });

  const dataPayload = {
    tariffPackageDesc: originalTpName,
    packageType: sessionStorage.getItem("pkgType") || "",
    tariffPackCategory: sessionStorage.getItem("pkgSubType") || "NORMAL",
    charge: state.price,
    startDate: formatDateToMMDDYYYY(state.startDate),
    endDate: formatDateToMMDDYYYY(state.endDate),
    publicityId: state.publicityCode,
    isCorporateYn: state.isCorporate || false,
    tariffPlanId: Number(state.s2[0].id),
    tariffPlanName: state.s2[0].name,
    periodicChargeID: sessionStorage.getItem("periodicChargeID") || "",
    selectedSvcs_s2: sessionStorage.getItem("selectedSvcs_s2") || "[]",
    selectedSvcs_s3: sessionStorage.getItem("selectedSvcs_s3") || "[]",
    selectedSvcs_s4: sessionStorage.getItem("selectedSvcs_s4") || "[]",
    defaultAtps: (state.s3 || []).map(atpMapper),
    allowedAtps: (state.s4 || []).map(atpMapper),
    caAtps: (state.s4 || [])
      .filter((item) => item.category === "CA")
      .map((item) => buildCaAtpEntry(item)),
  };

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
        alert(validateResult.message || "Validation failed. Please try again.");
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
      alert(result.message || "Clone failed. Please try again.");
      return;
    }

    alert("✅ Cloned successfully!\nNew plan: " + result.clonedTpName);

    // Clean up clone-specific session keys
    sessionStorage.removeItem("cloneMode");
    sessionStorage.removeItem("cloneType");
    sessionStorage.removeItem("cloneTpName");
    sessionStorage.removeItem("cloneNetworkId");
    sessionStorage.removeItem("cloneOriginalPublicityId");
    sessionStorage.removeItem("cloneOriginalTpName");
    clearBuilderSession();

    window.isInternalNavigation = true;
    window.location.href = "/builder/step1";
  } catch (err) {
    console.error("Clone error:", err);
    alert("Server error during clone. Please try again.");
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
    alert("Service Plan selection in Step 2 is required");
    return;
  }
  if (!state.price) {
    alert("Enter charge amount");
    return;
  }
  if (!state.startDate) {
    alert("Select start date");
    return;
  }
  if (!state.endDate) {
    alert("Select end date");
    return;
  }
  if (state.endDate <= state.startDate) {
    alert("Package end date must be after the start date.");
    return;
  }
  if (!state.publicityCode) {
    alert("Enter publicity code");
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
    alert(
      `MRP is required for "${missingMrpS4Update.name}". Please enter a valid MRP.`,
    );
    return;
  }

  const tariffPackageId = sessionStorage.getItem("approvedTariffPackageId");
  const networkId =
    sessionStorage.getItem("networkId") ||
    (typeof NETWORK_ID !== "undefined" ? NETWORK_ID : "");

  if (!tariffPackageId || !networkId) {
    alert(
      "Update context missing. Please reload the package from the Approved list.",
    );
    return;
  }

  function formatDateToMMDDYYYY(dateStr) {
    if (!dateStr) return "12/31/2030";
    const [year, month, day] = dateStr.split("-");
    return `${month}/${day}/${year}`;
  }

  const atpMapper = (item) => ({
    servicePackageId: Number(item.id),
    packageName: item.name,
    chargeId: item.chargeId || "",
    validity: item.validity,
    rentalPeriod: item.validity === "O" ? item.rentalPeriod || 1 : "",
    midnightExpiry: item.midnightExpiry,
    renewal: item.renewal,
    rental: item.rental || 0,
    maxCount: item.maxCount || 0,
    freeCycles: item.freeCycles || 0,
    priority:
      item.priority !== "" &&
      item.priority !== null &&
      item.priority !== undefined &&
      Number(item.priority) > 0
        ? Number(item.priority)
        : 0,
    mrp: item.mrp || 0,
    category: item.category,
    serviceCode: item.serviceCode,
    vipPlan: item.vipPlan,
  });

  const payload = {
    tpName: sessionStorage.getItem("approvedTpName") || "",
    username: USERNAME,
    networkId: Number(networkId),
    data: {
      username: USERNAME,
      isUpdate: true,
      submittedOn: new Date().toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }),
      packageType: sessionStorage.getItem("pkgType") || "",
      tariffPackCategory: sessionStorage.getItem("pkgSubType") || "NORMAL",
      tariffPackageDesc: sessionStorage.getItem("configName") || "",
      periodicChargeID: sessionStorage.getItem("periodicChargeID") || "",
      charge: state.price,
      startDate: formatDateToMMDDYYYY(state.startDate),
      endDate: formatDateToMMDDYYYY(state.endDate),
      publicityId: state.publicityCode,
      isCorporateYn: state.isCorporate || false,
      tariffPlanId: Number(state.s2[0].id),
      tariffPlanName: state.s2[0].name,
      selectedSvcs_s2: sessionStorage.getItem("selectedSvcs_s2") || "[]",
      selectedSvcs_s3: sessionStorage.getItem("selectedSvcs_s3") || "[]",
      selectedSvcs_s4: sessionStorage.getItem("selectedSvcs_s4") || "[]",
      defaultAtps: (state.s3 || []).map(atpMapper),
      allowedAtps: (state.s4 || []).map(atpMapper),
      caAtps: (state.s4 || [])
        .filter((item) => item.category === "CA")
        .map((item) => buildCaAtpEntry(item)),
    },
  };

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
      alert(result.message || "Update failed. Please try again.");
      return;
    }

    alert(
      "✅ Package updated successfully!\nPlan: " +
        (sessionStorage.getItem("configName") || ""),
    );
    clearBuilderSession();
    window.isInternalNavigation = true;
    window.location.href = "/builder/step1";
  } catch (err) {
    console.error("Update package error:", err);
    alert("Server error during update. Please try again.");
  } finally {
    if (updateBtn) {
      updateBtn.disabled = false;
      updateBtn.innerHTML =
        '<span class="material-icons">save</span> UPDATE PACKAGE';
    }
  }
}