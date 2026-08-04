// Centralized sessionStorage key names used across the builder frontend.
// Every file that reads/writes sessionStorage should reference these
// constants instead of retyping the raw string literal, so a typo can't
// silently create a second, disconnected key.
//
// IMPORTANT: the string values below must stay EXACTLY as they were before
// this file existed (same spelling, same casing, same _s2/_s4 suffixes) —
// they are what's already sitting in users' live sessionStorage data.
const STORAGE_KEYS = {
  SESSION_ID: "SESSION_ID",
  APPROVED_MODE: "approvedMode",
  APPROVED_TARIFF_PACKAGE_ID: "approvedTariffPackageId",
  APPROVED_TP_NAME: "approvedTpName",
  BUILDER_STATE: "builderState",
  CLONE_MODE: "cloneMode",
  CLONE_NETWORK_ID: "cloneNetworkId",
  CLONE_ORIGINAL_PUBLICITY_ID: "cloneOriginalPublicityId",
  CLONE_ORIGINAL_TP_NAME: "cloneOriginalTpName",
  CLONE_TP_NAME: "cloneTpName",
  CLONE_TYPE: "cloneType",
  CONFIG_NAME: "configName",
  IS_UPDATE: "isUpdate",
  LOADED_FROM_DRAFT: "loadedFromDraft",
  NETWORK_ID: "networkId",
  PERIODIC_CHARGE_ID: "periodicChargeID",
  PKG_SUB_TYPE: "pkgSubType",
  PKG_TYPE: "pkgType",
  REJECTED_TP_NAME: "rejectedTpName",
  SELECTED_SVCS_S2: "selectedSvcs_s2",
  SELECTED_SVCS_S3: "selectedSvcs_s3",
  SELECTED_SVCS_S4: "selectedSvcs_s4",
  SELECTED_SVCS_S5: "selectedSvcs_s5",
  STATE: "state",
  SVC_CATEGORY_S2: "svcCategory_s2",
  SVC_CATEGORY_S4: "svcCategory_s4",
  USERNAME: "username",
};
