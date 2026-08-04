// layout-core-init.js
// Split out of layout.js (originally lines 1-12) for modularity.
// Global init state that must run before anything else on the page: pageshow listener, clone-payload/nav-flag globals, initial username sync.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

window.addEventListener("pageshow", () => {
  window.isInternalNavigation = false;
});

let _currentClonePayload = null;

window.isInternalNavigation = false;

if (USERNAME) {
  sessionStorage.setItem(STORAGE_KEYS.USERNAME, USERNAME);
}

