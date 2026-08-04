// layout-session-hierarchy.js
// Split out of layout.js (originally lines 1263-1553) for modularity.
// Session clear/logout, network hierarchy tree view, and the admin two-pane approver package selection.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function clearBuilderSession() {
  sessionStorage.removeItem(STORAGE_KEYS.STATE);
  sessionStorage.removeItem(STORAGE_KEYS.SELECTED_SVCS_S2);
  sessionStorage.removeItem(STORAGE_KEYS.SELECTED_SVCS_S3);
  sessionStorage.removeItem(STORAGE_KEYS.SELECTED_SVCS_S4);
  sessionStorage.removeItem(STORAGE_KEYS.SELECTED_SVCS_S5);
  sessionStorage.removeItem(STORAGE_KEYS.CONFIG_NAME);
  sessionStorage.removeItem(STORAGE_KEYS.PKG_TYPE);
  sessionStorage.removeItem(STORAGE_KEYS.PKG_SUB_TYPE);
  sessionStorage.removeItem(STORAGE_KEYS.PERIODIC_CHARGE_ID);
  sessionStorage.removeItem(STORAGE_KEYS.IS_UPDATE);
  sessionStorage.removeItem(STORAGE_KEYS.LOADED_FROM_DRAFT);
  sessionStorage.removeItem(STORAGE_KEYS.CLONE_MODE);
  sessionStorage.removeItem(STORAGE_KEYS.CLONE_TP_NAME);
  sessionStorage.removeItem(STORAGE_KEYS.CLONE_NETWORK_ID);
  sessionStorage.removeItem(STORAGE_KEYS.REJECTED_TP_NAME);
  sessionStorage.removeItem(STORAGE_KEYS.APPROVED_MODE);
  sessionStorage.removeItem(STORAGE_KEYS.APPROVED_TP_NAME);
  sessionStorage.removeItem(STORAGE_KEYS.APPROVED_TARIFF_PACKAGE_ID);
}

// ═══════════════════════════════════════════════════════
//  HIERARCHY MODAL
// ═══════════════════════════════════════════════════════
function viewTree() {
  const state = getState();
  const name = document.getElementById("configName").value || "Unnamed Package";
  const type = sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE) || "";
  const sub = sessionStorage.getItem(STORAGE_KEYS.PKG_SUB_TYPE) || "";

  document.getElementById("treeName").textContent = name;
  document.getElementById("treeMeta").textContent =
    `${type ? type + " | " : ""}${sub} | ${state.isCorporate ? "Corporate" : "Retail"}`;
  document.getElementById("treeMain").textContent =
    `📦 Main Service Plan: ${state.s2 && state.s2[0] ? state.s2[0].name : "None"}`;
  const datpNames = (state.s3 || []).map((item) => item.name).join(", ");
  const aatpItems = [...(state.s4 || []), ...(state.s5 || [])];
  const aatpNames = aatpItems.map((item) => item.name).join(", ");

  document.getElementById("treeDatp").innerHTML = `
        <div class="tree-section">
            <div class="tree-section-title">➕ DATP Components</div>
            <div class="tree-tags">
                ${
                  (state.s3 || []).length
                    ? state.s3
                        .map(
                          (item) =>
                            `<span class="tree-tag">${item.name}</span>`,
                        )
                        .join("")
                    : '<span class="tree-empty">No DATP Components</span>'
                }
            </div>
        </div>
    `;

  document.getElementById("treeAatp").innerHTML = `
        <div class="tree-section">
            <div class="tree-section-title">🛒 AATP Components</div>
            <div class="tree-tags">
                ${
                  aatpItems.length
                    ? aatpItems
                        .map(
                          (item) =>
                            `<span class="tree-tag">${item.name}</span>`,
                        )
                        .join("")
                    : '<span class="tree-empty">No AATP Components</span>'
                }
            </div>
        </div>
    `;
  document.getElementById("treeCharge").innerHTML =
   `<b>Charge: RM ${state.price || "0.00"}</b> | <b>Starts: ${state.startDate || "Immediate"}</b> | <b>Ends: ${state.endDate || "Permanent"}</b>`;  
  document.getElementById("treeModal").classList.add("open");
}

function closeTree() {
  document.getElementById("treeModal").classList.remove("open");
}

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeTree();
});

// ═══════════════════════════════════════════════════════
//  LOGOUT
// ═══════════════════════════════════════════════════════
function logout() {
  sessionStorage.clear();
  window.location.href = "/logout";
}

const storedSessionId = sessionStorage.getItem(STORAGE_KEYS.SESSION_ID);

if (SESSION_ID && storedSessionId !== SESSION_ID) {
  clearBuilderSession(); // safer
  sessionStorage.setItem(STORAGE_KEYS.SESSION_ID, SESSION_ID);
}

// ═══════════════════════════════════════════════════════
//  ADMIN — TWO-PANE APPROVER UI (with working arrows)
// ═══════════════════════════════════════════════════════

function selectPackage(cardElement) {
  const tpName = cardElement.dataset.tpname;
  const isAlreadySelected = cardElement.classList.contains("selected");

  document
    .querySelectorAll(".approval-card")
    .forEach((c) => c.classList.remove("selected"));

  if (isAlreadySelected) {
    document.getElementById("hierarchy-view").classList.add("hidden");
    document.getElementById("no-selection").classList.remove("hidden");
    return;
  }

  cardElement.classList.add("selected");
  loadHierarchy(tpName);
}

function loadHierarchy(tpName) {
  const VALIDITY_LABELS = {
    M: "Monthly",
    O: "Others",
    D: "Daily",
    W: "Weekly",
    FM: "Fixed Month",
    CW: "Calendar Week",
    CM: "Calendar Month",
    U: "Unlimited",
    Y: "Yearly",
  };
  function validityLabel(code) {
    if (!code || code === "—") return code || "—";
    return VALIDITY_LABELS[code] || code;
  }

  fetch("/admin/hierarchy/" + tpName)
    .then((res) => res.json())
    .then((tp) => {
      if (!tp || !tp.data) {
        showToast("No hierarchy data found");
        return;
      }

      const data = tp.data || {};

      // Switch views
      document.getElementById("no-selection").classList.add("hidden");
      document.getElementById("no-packages")?.classList.add("hidden");
      const view = document.getElementById("hierarchy-view");
      view.classList.remove("hidden");

      // Header
      document.getElementById("h-name").textContent =
        data.tariffPackageDesc || tpName;

      const metaHTML = `
			<span class="meta-pill">
			        <span class="pill-label">Billing Type</span>
			        <span class="pill-value">${data.packageType}</span>
			    </span>
			    <span class="meta-pill">
			        <span class="pill-label">Category</span>
			        <span class="pill-value">${data.tariffPackCategory || ""}</span>
			    </span>
			    <span class="meta-pill">
			        <span class="pill-label">Segment</span>
			        <span class="pill-value">${data.isCorporateYn ? "Corporate" : "Retail"}</span>
			    </span>
			`;
      document.getElementById("h-meta").innerHTML = metaHTML;
      document.getElementById("h-meta").innerHTML = metaHTML;

      const submittedBy = data.username || "—";
      const submittedOn = data.submittedOn || "—";
      document.getElementById("h-submeta").innerHTML = `
                <span>Submitted by <strong>${data.username || "—"}</strong></span>
                <span>${data.submittedOn || "—"}</span>
            `;

      // Main Plan
      document.getElementById("h-main-header").textContent =
        `📦 ${data.tariffPlanName || "None"}`;

      // DATP Components
      const datp = data.defaultAtps || [];
      document.getElementById("h-datp-header").textContent =
        `🛒 DATP - ${datp.length} COMPONENTS`;
      const datpHtml = datp
        .map(
          (item) => `
                <div class="component-box">
                    <div class="comp-name">${item.packageName}</div>
                    <div class="comp-details">
                        <span class="pill"><strong>Validity:</strong> ${validityLabel(item.validity)}</span>
                        ${item.validity === "O" && item.rentalPeriod != null ? `<span class="pill"><strong>Rental Period:</strong> ${item.rentalPeriod} Days</span>` : ""}
                        <span class="pill"><strong>Midnight Expiry:</strong> ${item.midnightExpiry || "—"}</span>
                        <span class="pill"><strong>Renewal:</strong> ${item.renewal || "—"}</span>
                        <span class="pill"><strong>Rental:</strong> ${item.rental || "0"}</span>
                        <span class="pill"><strong>Max Count:</strong> ${item.maxCount || "0"}</span>
                        <span class="pill"><strong>Free Cycles:</strong> ${item.freeCycles || "0"}</span>
                        <span class="pill"><strong>Priority:</strong> ${item.priority ?? 0}</span>
                    </div>
                </div>
            `,
        )
        .join("");

      document.getElementById("h-datp").innerHTML =
        datpHtml ||
        '<p style="color:#94a3b8; font-size:13px; padding:8px 0;">No DATP components</p>';

      // AATP Components
      const caAtpsPreview = data.caAtps || [];
      // CA-category AATPs live only in caAtps now (see saveConfiguration()),
      // so merge them back in here as their own AATP entries for display.
      const aatp = [
        ...(data.allowedAtps || []),
        ...caAtpsPreview.map((c) => ({ ...c, category: "CA" })),
      ];
      document.getElementById("h-aatp-header").textContent =
        `➕ AATP - ${aatp.length} COMPONENTS`;
      const aatpHtml = aatp
        .map((item) => {
          const ca =
            item.category === "CA"
              ? caAtpsPreview.find(
                  (c) =>
                    String(c.servicePackageId) ===
                    String(item.servicePackageId),
                )
              : null;

          return `
                <div class="component-box">
                    <div class="comp-name">${item.packageName}</div>
                    <div class="comp-details">
                        <span class="pill"><strong>Validity:</strong> ${validityLabel(item.validity)}</span>
                        ${item.validity === "O" && item.rentalPeriod != null ? `<span class="pill"><strong>Rental Period:</strong> ${item.rentalPeriod} Days</span>` : ""}
                        <span class="pill"><strong>Midnight Expiry:</strong> ${item.midnightExpiry || "—"}</span>
                        <span class="pill"><strong>Renewal:</strong> ${item.renewal || "—"}</span>
                        <span class="pill"><strong>Rental:</strong> ${item.rental || "0"}</span>
                        <span class="pill"><strong>Max Count:</strong> ${item.maxCount || "0"}</span>
                        <span class="pill"><strong>Free Cycles:</strong> ${item.freeCycles || "0"}</span>
						<span class="pill"><strong>Priority:</strong> ${item.priority ?? 0}</span>
						<span class="pill"><strong>MRP:</strong> ${item.mrp ?? "0"}</span>
						${item.serviceCode ? `<span class="pill"><strong>Service Code:</strong> ${item.serviceCode}</span>` : ""}
						${item.category !== "CA" ? `<span class="pill"><strong>VIP Plan:</strong> ${item.vipPlan === "Y" ? "Yes" : item.vipPlan === "N" ? "No" : "—"}</span>` : ""}
						${
              ca
                ? `
						<span class="pill"><strong>Default Lines:</strong> ${ca.defaultLinesAllowed ?? "0"}</span>
						<span class="pill"><strong>Charge/Line:</strong> ${ca.additionalChargePerLine ?? "0"}</span>
						<span class="pill"><strong>Rollover:</strong> ${ca.packageRolloverYn || "—"}</span>
						${ca.packageStartDate ? `<span class="pill"><strong>CA Start:</strong> ${ca.packageStartDate}</span>` : ""}
						${ca.packageEndDate ? `<span class="pill"><strong>CA End:</strong> ${ca.packageEndDate}</span>` : ""}
						${(ca.serviceMappings || [])
              .map(
                (m) =>
                  `<span class="pill"><strong>${m.serviceUnitType || "Mapping"}:</strong> ${m.units ?? 0} units, topup ${m.topupCharge ?? 0}, max xfer ${m.maxTransferLimit ?? 0}%</span>`,
              )
              .join("")}
						`
                : ""
            }
                    </div>
                </div>
            `;
        })
        .join("");

      document.getElementById("h-aatp").innerHTML =
        aatpHtml ||
        '<p style="color:#94a3b8; font-size:13px; padding:8px 0;">No AATP components</p>';

      // Footer
      document.getElementById("h-footer-bar").innerHTML = `
                <div><b>Charge:</b> RM ${data.charge}</div>
                ${data.startDate ? `<div><b>Starts:</b> ${data.startDate}</div>` : ""}
                <div><b>Ends:</b> ${data.endDate}</div>
            `;
    })
    .catch((err) => {
      console.error(err);
      showToast("Could not load hierarchy");
    });
}

