// ═══════════════════════════════════════════════════════════
// UPDATE ATP RATE — single-form page: pick an Additional Tariff
// Package, Month and Year, then submit to update that package's
// rate for the selected period.
// ═══════════════════════════════════════════════════════════

let _atrPackages = []; // [{id, name}] deduped Additional Tariff Packages — from /service-packages/service-packages/AATP
let _atrLoaded = false; // packages fetched at least once this session
let _atrPackageDetail = null; // {servicePackageId, monthYear, packageName, oldRate} — set once step 2 is showing

const _ATR_MONTHS = [
  { value: "1", label: "January" }, { value: "2", label: "February" }, { value: "3", label: "March" },
  { value: "4", label: "April" }, { value: "5", label: "May" }, { value: "6", label: "June" },
  { value: "7", label: "July" }, { value: "8", label: "August" }, { value: "9", label: "September" },
  { value: "10", label: "October" }, { value: "11", label: "November" }, { value: "12", label: "December" },
];

// ── Open / close the page ──────────────────────────────────
function openAtpRate() {
  const page = document.getElementById("atpRatePage");
  if (!page) return;

  // ATP Rules is still a full takeover page — force-close it so it
  // doesn't stay stacked on top (same z-index, later in DOM) and hide
  // this modal underneath it. Since it's a takeover page, it also
  // hid the workspace body/header pill/rail/sidebar when it opened —
  // restore all of that too, or the underlying page stays blank once
  // this modal is closed.
  const atpRulesPage = document.getElementById("atpRulesPage");
  if (atpRulesPage) {
    atpRulesPage.classList.remove("visible");
    atpRulesPage.style.display = "none";

    const workBody = document.getElementById("leftPane")?.parentElement;
    const headerPill = document.querySelector(".header-pill-bar");
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
  }

  // Just highlight the nav node — no need to hide/collapse the
  // underlying page since this is a modal sitting on top of it.
  const atpRateNode = document.getElementById("mn-atprate");
  if (atpRateNode) atpRateNode.classList.add("active");

  _atrResetForm();
  page.style.display = "flex";

  requestAnimationFrame(() => {
    requestAnimationFrame(() => page.classList.add("visible"));
  });

  if (!_atrLoaded) _atrLoadPackages();
}

function closeAtpRatePage() {
  const page = document.getElementById("atpRatePage");
  if (!page) return;

  page.classList.remove("visible");

  const atpRateNode = document.getElementById("mn-atprate");
  if (atpRateNode) atpRateNode.classList.remove("active");

  page.addEventListener(
    "transitionend",
    function _restore(e) {
      if (e.propertyName !== "opacity") return;
      page.removeEventListener("transitionend", _restore);
      page.style.display = "none";
    },
    { once: false },
  );
}

// ── Step switching ───────────────────────────────────────────
function _atrShowStep1() {
  const step1 = document.getElementById("atrStep1");
  const step2 = document.getElementById("atrStep2");
  const success = document.getElementById("atrSuccess");
  const footer1 = document.getElementById("atrStep1Footer");
  const footer2 = document.getElementById("atrStep2Footer");
  const sub = document.getElementById("atrHeaderSub");

  if (step1) step1.style.display = "";
  if (step2) step2.style.display = "none";
  if (success) success.style.display = "none";
  if (footer1) footer1.style.display = "";
  if (footer2) footer2.style.display = "none";
  if (sub) sub.style.display = "";
  if (sub) sub.textContent = "Select the Additional Tariff Package, Month and Year to update its rate";
}

function _atrShowStep2() {
  const step1 = document.getElementById("atrStep1");
  const step2 = document.getElementById("atrStep2");
  const success = document.getElementById("atrSuccess");
  const footer1 = document.getElementById("atrStep1Footer");
  const footer2 = document.getElementById("atrStep2Footer");
  const sub = document.getElementById("atrHeaderSub");

  if (step1) step1.style.display = "none";
  if (step2) step2.style.display = "";
  if (success) success.style.display = "none";
  if (footer1) footer1.style.display = "none";
  if (footer2) footer2.style.display = "";
  if (sub) sub.style.display = "";
  if (sub) sub.textContent = "Review the current rate and enter the new rate";
}

// Shown after a successful Bulk rate update — replaces the form with a
// checkmark + message inside the same modal, then auto-closes it.
function _atrShowSuccess(message) {
  const step1 = document.getElementById("atrStep1");
  const step2 = document.getElementById("atrStep2");
  const success = document.getElementById("atrSuccess");
  const footer1 = document.getElementById("atrStep1Footer");
  const footer2 = document.getElementById("atrStep2Footer");
  const sub = document.getElementById("atrHeaderSub");
  const successMessage = document.getElementById("atrSuccessMessage");

  if (step1) step1.style.display = "none";
  if (step2) step2.style.display = "none";
  if (footer1) footer1.style.display = "none";
  if (footer2) footer2.style.display = "none";
  if (sub) sub.style.display = "none";
  if (successMessage) successMessage.textContent = message || "Rate updated successfully.";
  if (success) success.style.display = "";

  setTimeout(() => _atrResetForm(), 1600);
}

function _atrGoToStep1() {
  _atrShowStep1();
}

// ── Reset the form back to its default state ────────────────
function _atrResetForm() {
  const pkgSelect = document.getElementById("atrPackageSelect");
  if (pkgSelect) pkgSelect.value = "";

  const monthSelect = document.getElementById("atrMonthSelect");
  if (monthSelect) {
    monthSelect.innerHTML =
      `<option value="">Select</option>` +
      _ATR_MONTHS.map((m) => `<option value="${m.value}">${m.label}</option>`).join("");
    monthSelect.value = "";
  }

  const yearSelect = document.getElementById("atrYearSelect");
  if (yearSelect) {
    // No year-list API given — offer a sensible range around the current year.
    const currentYear = new Date().getFullYear();
    const years = [];
    for (let y = currentYear - 1; y <= currentYear + 3; y++) years.push(y);
    yearSelect.innerHTML =
      `<option value="">Select</option>` +
      years.map((y) => `<option value="${y}">${y}</option>`).join("");
    yearSelect.value = "";
  }

  const detailPackage = document.getElementById("atrDetailPackage");
  if (detailPackage) detailPackage.value = "";
  const detailOldRate = document.getElementById("atrDetailOldRate");
  if (detailOldRate) detailOldRate.value = "";
  const newRateInput = document.getElementById("atrNewRateInput");
  if (newRateInput) newRateInput.value = "";
  _atrPackageDetail = null;

  _atrShowStep1();
}

// ── Load Additional Tariff Packages ─────────────────────────
// GET /service-packages/service-packages/AATP?networkId=..
// The API returns one row per chargeId, so the same package can repeat —
// dedupe by servicePackageId before populating the dropdown.
async function _atrLoadPackages() {
  const pkgSelect = document.getElementById("atrPackageSelect");
  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";

  if (pkgSelect) {
    pkgSelect.disabled = true;
    pkgSelect.innerHTML = `<option value="">Loading…</option>`;
  }

  if (!networkId) {
    console.error("ATP Rate: NETWORK_ID not found in session.");
    _atrPackages = [];
    _atrRenderPackageOptions();
    return;
  }

  try {
    const res = await fetch("/service-packages/service-packages/AATP?networkId=" + networkId);
    if (!res.ok) throw new Error("HTTP " + res.status);
    const body = await res.json();
    const rows = body.data || [];

    const seen = new Set();
    _atrPackages = [];
    rows.forEach((row) => {
      const id = row.servicePackageId;
      if (seen.has(id)) return;
      seen.add(id);
      _atrPackages.push({ id, name: row.servicePackageName });
    });

    _atrLoaded = true;
  } catch (err) {
    console.error("Failed to load Additional Tariff Packages:", err);
    _atrPackages = [];
  }

  _atrRenderPackageOptions();
}

function _atrRenderPackageOptions() {
  const pkgSelect = document.getElementById("atrPackageSelect");
  if (!pkgSelect) return;

  const options = _atrPackages
    .map((p) => `<option value="${p.id}">${_atrEscape(p.name)}</option>`)
    .join("");

  pkgSelect.innerHTML = `<option value="">Select</option>${options}`;
  pkgSelect.disabled = false;
}

// ── Submit — step 1 ──────────────────────────────────────────
// Validates the three mandatory fields, then looks up the current
// rate via POST /service-packages/service-package-detail. On success,
// advances to step 2 (Old Rate + New Rate).
async function _atrSubmit() {
  const pkgSelect = document.getElementById("atrPackageSelect");
  const monthSelect = document.getElementById("atrMonthSelect");
  const yearSelect = document.getElementById("atrYearSelect");
  const submitBtn = document.getElementById("atrSubmitBtn");

  if (!pkgSelect.value) {
    alert("Please select the Additional Tariff Package.");
    pkgSelect.focus();
    return;
  }
  if (!monthSelect.value) {
    alert("Please select the Month.");
    monthSelect.focus();
    return;
  }
  if (!yearSelect.value) {
    alert("Please select the Year.");
    yearSelect.focus();
    return;
  }

  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  const servicePackageId = pkgSelect.value;
  const monthYear = monthSelect.value.padStart(2, "0") + yearSelect.value;

  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = "Loading…";
  }

  try {
    const res = await fetch("/service-packages/service-package-detail", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        networkId: networkId ? Number(networkId) : networkId,
        servicePackageId: Number(servicePackageId),
        monthYear,
      }),
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const detail = await res.json();

    // res.ok alone isn't enough — this backend's error handler returns
    // HTTP 200 even on failure, with an {status:"error", message} body
    // instead of the real {servicePackageDesc, activationFee} shape.
    // Only advance once we actually have a usable activationFee.
    if (detail.status === "error" || detail.activationFee === undefined || detail.activationFee === null) {
      throw new Error(detail.message || "No rate data returned for this package/month/year.");
    }

    _atrPackageDetail = {
      servicePackageId,
      monthYear,
      packageName: detail.servicePackageDesc ?? pkgSelect.options[pkgSelect.selectedIndex]?.textContent ?? "",
      oldRate: detail.activationFee,
    };

    const detailPackage = document.getElementById("atrDetailPackage");
    if (detailPackage) detailPackage.value = _atrPackageDetail.packageName;
    const detailOldRate = document.getElementById("atrDetailOldRate");
    if (detailOldRate) detailOldRate.value = _atrPackageDetail.oldRate;
    const newRateInput = document.getElementById("atrNewRateInput");
    if (newRateInput) newRateInput.value = "";

    _atrShowStep2();
  } catch (err) {
    console.error("Failed to fetch service package detail:", err);
    alert(
      err && err.message && err.message !== "Failed to fetch"
        ? err.message
        : "Couldn't fetch the current rate for this package. Please try again.",
    );
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = "Submit";
    }
  }
}

// ── Submit — step 2 ──────────────────────────────────────────
// Saves the new rate via PUT /Bulk/bulk-rate/update.
async function _atrSubmitNewRate() {
  const newRateInput = document.getElementById("atrNewRateInput");
  const finalSubmitBtn = document.getElementById("atrFinalSubmitBtn");

  if (!newRateInput.value || Number(newRateInput.value) < 0) {
    alert("Please enter a valid New Rate.");
    newRateInput.focus();
    return;
  }
  if (!_atrPackageDetail) {
    alert("Something went wrong — please start over.");
    _atrShowStep1();
    return;
  }

  const networkId =
    typeof NETWORK_ID !== "undefined" && NETWORK_ID ? NETWORK_ID : "";
  const userId = typeof USERNAME !== "undefined" && USERNAME ? USERNAME : "";

  if (finalSubmitBtn) {
    finalSubmitBtn.disabled = true;
    finalSubmitBtn.textContent = "Submitting…";
  }

  try {
    const res = await fetch("/Bulk/bulk-rate/update", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        networkId: networkId ? Number(networkId) : networkId,
        userId,
        serviceIds: [Number(_atrPackageDetail.servicePackageId)],
        oldRates: [_atrPackageDetail.oldRate],
        newRates: [Number(newRateInput.value)],
        monthYear: _atrPackageDetail.monthYear,
        flag: "UPDATE",
      }),
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const body = await res.json();

    _atrShowSuccess(body.message);
  } catch (err) {
    console.error("Failed to update ATP rate:", err);
    alert("Couldn't update the rate. Please try again.");
  } finally {
    if (finalSubmitBtn) {
      finalSubmitBtn.disabled = false;
      finalSubmitBtn.textContent = "Submit";
    }
  }
}

// ── util ──────────────────────────────────────────────────
function _atrEscape(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}