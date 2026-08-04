// Shared, framework-free utility functions for the builder frontend.
// Add helpers here that are duplicated across step*.js / layout.js so
// there's a single implementation instead of copies drifting apart.

// Was previously copy-pasted (byte-identical) in three places:
// layout.js (inside saveConfiguration), and twice in step5.js (inside
// clonePackageFromBuilder and updatePackage). Behavior unchanged.
function formatDateToMMDDYYYY(dateStr) {
  if (!dateStr) return "12/31/2030";

  const [year, month, day] = dateStr.split("-");

  return `${month}/${day}/${year}`;
}
