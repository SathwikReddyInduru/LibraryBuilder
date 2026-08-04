// ═══════════════════════════════════════════════════════════════════════
//  SHARED BUILDER (single source of truth)
// ═══════════════════════════════════════════════════════════════════════

function getState() {
  const defaultState = {
    s2: [],
    s3: [],
    s4: [],
    s5: [],
    price: "",
    publicityCode: "",
    startDate: "",
    endDate: "",
    isCorporate: false,
  };

  const stored = sessionStorage.getItem(STORAGE_KEYS.STATE);
  return stored ? migrateLegacyCaFromS4(JSON.parse(stored)) : defaultState;
}

function saveState(state) {
  sessionStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(state));
}

// s3 / defaultAtps item shape. Deliberately narrower than the s4 shape
// below (no mrp/category/serviceCode/vipPlan) — step3's UI has no inputs
// for those fields, so there's nothing real to send.
function mapDefaultAtpItem(item) {
  return {
    servicePackageId: Number(item.id),
    packageName: item.name,
    type: item.type || "",
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
  };
}

// s4 / allowedAtps item shape (non-CA items). Includes the fields step4's
// UI actually collects (mrp/category/serviceCode/vipPlan).
function mapAllowedAtpItem(item) {
  return {
    servicePackageId: Number(item.id),
    packageName: item.name,
    type: item.type || "",
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
    category: item.category || "NORMAL",
    serviceCode: item.serviceCode || "",
    vipPlan: item.vipPlan || "",
  };
}

// s5 / caAtps item shape. This is its own mapper — deliberately NOT built
// by spreading mapAllowedAtpItem() — because step5 (Optional Services)'s
// CA form never collects serviceCode/vipPlan (those are AATP/step4-only
// inputs). Reusing the AATP mapper here was what caused serviceCode/vipPlan
// to show up as empty fields in the saved caAtps JSON; this mapper only
// includes fields the CA form actually has.
function mapCaAtpItem(item) {
  const ca = item.caConfig || {};

  return {
    servicePackageId: Number(item.id),
    packageName: item.name,
    type: item.type || "",
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
    category: item.category || "CA",
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

// One-time migration: CA ATPs used to live inside s4 (category: "CA"),
// mixed in with normal AATPs. Now that Optional Services (CA ATP) is its
// own step5, any state still carrying CA items inside s4 (e.g. a draft
// saved before this change) gets those items moved over to s5 the first
// time that state is read. Safe to call repeatedly — a no-op once s4 has
// no CA items left.
function migrateLegacyCaFromS4(state) {
  if (!state || !Array.isArray(state.s4) || !state.s4.length) return state;

  const legacyCa = state.s4.filter((item) => item.category === "CA");
  if (!legacyCa.length) return state;

  state.s4 = state.s4.filter((item) => item.category !== "CA");
  state.s5 = [...(state.s5 || []), ...legacyCa];

  return state;
}

// Builds every package field that's genuinely identical across all three
// submit flows. tariffPackageDesc/isUpdate/submittedOn are passed in
// because each flow means something different by them (see chat history):
//   - main submit: tariffPackageDesc = the current session's config name
//   - clone: tariffPackageDesc = the name of the package being cloned FROM
//   - update: tariffPackageDesc = the current session's config name
// username is optional — clone doesn't carry it inside its data object
// (it's already required at the outer envelope level for that flow).
function buildPackageCore({ tariffPackageDesc, isUpdate, submittedOn, username } = {}) {
  const state = migrateLegacyCaFromS4(getState());

  const core = {
    isUpdate: !!isUpdate,
    submittedOn,
    packageType: sessionStorage.getItem(STORAGE_KEYS.PKG_TYPE) || "",
    tariffPackCategory: sessionStorage.getItem(STORAGE_KEYS.PKG_SUB_TYPE) || "",
    tariffPackageDesc,
    periodicChargeID: sessionStorage.getItem(STORAGE_KEYS.PERIODIC_CHARGE_ID) || "",
    charge: state.price,
    startDate: formatDateToMMDDYYYY(state.startDate),
    endDate: formatDateToMMDDYYYY(state.endDate),
    publicityId: state.publicityCode,
    isCorporateYn: state.isCorporate || false,
    tariffPlanId: Number(state.s2[0].id),
    tariffPlanName: state.s2[0].name,
    selectedSvcs_s2: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S2) || "[]",
    selectedSvcs_s3: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S3) || "[]",
    selectedSvcs_s4: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S4) || "[]",
    selectedSvcs_s5: sessionStorage.getItem(STORAGE_KEYS.SELECTED_SVCS_S5) || "[]",
    defaultAtps: (state.s3 || []).map(mapDefaultAtpItem),
    // Defensive filter: s4 (AATP/step4) is normal-only going forward, but a
    // pre-migration draft could still have a stray CA item in here.
    allowedAtps: (state.s4 || [])
      .filter((item) => item.category !== "CA")
      .map(mapAllowedAtpItem),
    // CA ATPs (Optional Services/step5) — built with the dedicated CA
    // mapper, not mapAllowedAtpItem, so serviceCode/vipPlan never appear.
    caAtps: (state.s5 || []).map(mapCaAtpItem),
  };

  if (username !== undefined) {
    core.username = username;
  }

  return core;
}

// ═══════════════════════════════════════════════════════════════════════
//  SHARED RESTORER (single source of truth)
// ═══════════════════════════════════════════════════════════════════════

function rebuildStateFromPackage(d, { includeChargeId = false } = {}) {
  const parseSlashDate = (dateStr) => {
    if (!dateStr) return "";
    const p = dateStr.split("/");
    return p.length === 3 ? `${p[2]}-${p[0]}-${p[1]}` : dateStr;
  };

  return {
    s2: [
      {
        id: d.tariffPlanId,
        name: d.tariffPlanName,
      },
    ],

    s3: (d.defaultAtps || []).map((a) => ({
      id: a.servicePackageId,
      name: a.packageName,
      type: a.type || "",
      ...(includeChargeId ? { chargeId: a.chargeId || "" } : {}),
      validity: a.validity,
      rentalPeriod: a.rentalPeriod || "",
      midnightExpiry: a.midnightExpiry,
      renewal: a.renewal,
      rental: a.rental,
      maxCount: a.maxCount,
      freeCycles: a.freeCycles,
      priority: a.priority,
    })),

    s4: (d.allowedAtps || [])
      .filter((a) => (a.category || "NORMAL") !== "CA")
      .map((a) => ({
        id: a.servicePackageId,
        name: a.packageName,
        type: a.type || "",
        ...(includeChargeId ? { chargeId: a.chargeId || "" } : {}),
        validity: a.validity,
        rentalPeriod: a.rentalPeriod || "",
        midnightExpiry: a.midnightExpiry,
        renewal: a.renewal,
        rental: a.rental,
        maxCount: a.maxCount,
        freeCycles: a.freeCycles,
        priority: a.priority,
        mrp: a.mrp,
        category: a.category || "NORMAL",
        serviceCode: a.serviceCode || "",
        vipPlan: a.vipPlan || "",
      })),

    s5: mapCaAtpsToS5(d.caAtps),

    price: d.charge || "",
    publicityCode: d.publicityId || "",
    startDate: parseSlashDate(d.startDate),
    endDate: parseSlashDate(d.endDate),
    isCorporate: d.isCorporateYn || false,
  };
}

// Rebuilds s5 (Optional Services/CA ATP) state items from a backend
// caAtps list — used wherever session state is reconstructed from a
// previously-saved tariff package (approvals lists, clone). Replaces the
// old attachCaConfigToS4(), which merged these into s4; now that CA ATPs
// are their own step5, they get their own array.
function mapCaAtpsToS5(caAtps) {
  return (caAtps || []).map((match, idx) => {
    const restoredMappings = (match.serviceMappings || []).map((m, i) => ({
      rowId: `restored-${match.servicePackageId}-${idx}-${i}`,
      serviceUnitType: m.serviceUnitType || "",
      serviceId: m.serviceId ?? "",
      units: m.units ?? "",
      topupCharge: m.topupCharge ?? "",
      maxTransferLimit: m.maxTransferLimit ?? "",
    }));

    return {
      id: String(match.servicePackageId),
      name: match.packageName,
      type: match.type || "",
      chargeId: match.chargeId || "",
      category: "CA",
      validity: match.validity,
      rentalPeriod: match.rentalPeriod || "",
      midnightExpiry: match.midnightExpiry,
      renewal: match.renewal,
      rental: match.rental,
      maxCount: match.maxCount,
      freeCycles: match.freeCycles,
      priority: match.priority,
      mrp: match.mrp,
      caConfig: {
        defaultLinesAllowed: match.defaultLinesAllowed ?? "",
        additionalChargePerLine: match.additionalChargePerLine ?? 0,
        packageRolloverYn: match.packageRolloverYn || "",
        packageStartDate: match.packageStartDate || "",
        packageEndDate: match.packageEndDate || "",
        // At least one mapping is always required, even if older saved
        // data somehow has none.
        serviceMappings: restoredMappings.length
          ? restoredMappings
          : [
              {
                rowId: `restored-${match.servicePackageId}-${idx}-0`,
                serviceUnitType: "",
                serviceId: "",
                units: "",
                topupCharge: "",
                maxTransferLimit: "",
              },
            ],
      },
    };
  });
}