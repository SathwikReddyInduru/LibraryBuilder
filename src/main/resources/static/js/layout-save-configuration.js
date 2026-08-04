// layout-save-configuration.js
// Split out of layout.js (originally lines 855-1262) for modularity.
// User menu toggle and the main tariff-package submission flow (saveConfiguration).
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function toggleUserMenu() {
  const dropdown = document.getElementById("userDropdown");
  dropdown.classList.toggle("active");
}

document.addEventListener("click", function (e) {
  const menu = document.querySelector(".user-menu");
  if (menu && !menu.contains(e.target)) {
    const dd = document.getElementById("userDropdown");
    if (dd) dd.classList.remove("active");
  }
});

// ═══════════════════════════════════════════════════════
//  SAVE PACKAGE CONFIGURATION
// ═══════════════════════════════════════════════════════
async function saveConfiguration() {
  const configName = document.getElementById("configName").value;

  if (!configName) {
    showToast("Enter Configuration Name");
    return;
  }

  const state = migrateLegacyCaFromS4(JSON.parse(sessionStorage.getItem(STORAGE_KEYS.STATE)));

  const isUpdate = sessionStorage.getItem(STORAGE_KEYS.IS_UPDATE) === "true";

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

  if (!state.endDate) {
    showToast("Select end date");
    return;
  }

  if (!state.publicityCode) {
    showToast("Enter publicity code");
    return;
  }

  // Priority is mandatory for every DATP (s3) and AATP (s4) package
  const missingPriorityS3 = (state.s3 || []).find(
    (item) =>
      item.priority === "" ||
      item.priority === null ||
      item.priority === undefined ||
      Number(item.priority) <= 0,
  );

  if (missingPriorityS3) {
    showToast(
      `Priority is required for "${missingPriorityS3.name}". Please enter a priority greater than 0.`,
    );
    const input = document.getElementById(
      `priority-s3-${missingPriorityS3.id}`,
    );
    if (input) input.focus();
    return;
  }

  const missingPriorityS4 = (state.s4 || []).find(
    (item) =>
      item.priority === "" ||
      item.priority === null ||
      item.priority === undefined ||
      Number(item.priority) <= 0,
  );

  if (missingPriorityS4) {
    showToast(
      `Priority is required for "${missingPriorityS4.name}". Please enter a priority greater than 0.`,
    );
    const input = document.getElementById(
      `priority-s4-${missingPriorityS4.id}`,
    );
    if (input) input.focus();
    return;
  }

  const missingMrpS4 = (state.s4 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );

  if (missingMrpS4) {
    showToast(
      `MRP is required for "${missingMrpS4.name}". Please enter a valid MRP.`,
    );
    const input = document.getElementById(`mrp-s4-${missingMrpS4.id}`);
    if (input) input.focus();
    return;
  }

  // VIP Plan is mandatory for every AATP (s4 is Normal-only now — CA moved
  // to its own Optional Services/step5, which never has this field).
  const missingVipPlanS4 = (state.s4 || []).find((item) => !item.vipPlan);

  if (missingVipPlanS4) {
    showToast(`VIP Plan is required for "${missingVipPlanS4.name}".`);
    const input = document.getElementById(
      `vip-plan-s4-${missingVipPlanS4.id}`,
    );
    if (input) input.focus();
    return;
  }

  function isBlank(v) {
    return v === "" || v === null || v === undefined;
  }

  // Priority + MRP + CA Configuration are mandatory for every Optional
  // Service (s5, CA ATP) package.
  const missingPriorityS5 = (state.s5 || []).find(
    (item) =>
      item.priority === "" ||
      item.priority === null ||
      item.priority === undefined ||
      Number(item.priority) <= 0,
  );

  if (missingPriorityS5) {
    showToast(
      `Priority is required for "${missingPriorityS5.name}". Please enter a priority greater than 0.`,
    );
    const input = document.getElementById(
      `priority-s5-${missingPriorityS5.id}`,
    );
    if (input) input.focus();
    return;
  }

  const missingMrpS5 = (state.s5 || []).find(
    (item) =>
      item.mrp === "" ||
      item.mrp === null ||
      item.mrp === undefined ||
      Number(item.mrp) < 0,
  );

  if (missingMrpS5) {
    showToast(
      `MRP is required for "${missingMrpS5.name}". Please enter a valid MRP.`,
    );
    const input = document.getElementById(`mrp-s5-${missingMrpS5.id}`);
    if (input) input.focus();
    return;
  }

  // Package-level fields (all mandatory except Additional Charge Per Line)
  // plus every service mapping row (mandatory except Topup Charge). At
  // least one mapping row is always required.
  for (const item of state.s5 || []) {
    const ca = item.caConfig || {};

    if (
      isBlank(ca.defaultLinesAllowed) ||
      isBlank(ca.packageRolloverYn) ||
      isBlank(ca.packageStartDate) ||
      isBlank(ca.packageEndDate)
    ) {
      showToast(
        `Please fill all mandatory CA Configuration fields for "${item.name}".`,
      );
      return;
    }

    const mappings = ca.serviceMappings || [];

    if (!mappings.length) {
      showToast(`At least one service mapping is required for "${item.name}".`);
      return;
    }

    const badMapping = mappings.find(
      (m) =>
        isBlank(m.serviceUnitType) ||
        isBlank(m.units) ||
        isBlank(m.maxTransferLimit),
    );

    if (badMapping) {
      showToast(
        `Please fill CA Service, Units, and Max Transfer Limit for every mapping in "${item.name}".`,
      );
      return;
    }
  }

  const payload = buildPackageCore({
    tariffPackageDesc: configName,
    isUpdate,
    submittedOn: new Date().toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }),
    username: USERNAME,
  });

  console.log("REQUEST", payload);

  try {
    const response = await fetch("/prepareSaveConfig", {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({ data: payload }),
    });

    const result = await response.json();

    if (!response.ok || result.error) {
      showToast(result.error || "Validation failed");
      return;
    }

    showToast(result.message, "success");

    if (sessionStorage.getItem(STORAGE_KEYS.LOADED_FROM_DRAFT) === "true") {
      const draftName = sessionStorage.getItem(STORAGE_KEYS.CONFIG_NAME);
      if (draftName) {
        fetch("/draft/save", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: draftName, _delete: true }),
        });
      }
    }

    // If re-submitting a rejected TP, delete it from rejected-tariffs.json
    // using the ORIGINAL tpName (rejectedTpName) — even if user renamed it
    const rejectedTpName = sessionStorage.getItem(STORAGE_KEYS.REJECTED_TP_NAME);
    if (rejectedTpName) {
      await fetch("/rejected/delete/" + encodeURIComponent(rejectedTpName), {
        method: "POST",
      }).catch((err) =>
        console.warn("Could not remove from rejected list:", err),
      );
    }

    const wasRejected = !!rejectedTpName;

    clearBuilderSession();

    window.isInternalNavigation = true;

    await new Promise((resolve) => setTimeout(resolve, 2000));

    if (wasRejected) {
      window.location.href = "/builder/step1?openSaved=1";
    } else {
      window.location.href = "/builder/step1";
    }
  } catch (error) {
    console.error(error);
    showToast("Server error — please try again");
  }
}

