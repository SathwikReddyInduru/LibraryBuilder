// ═══════════════════════════════════════════════════════════════════════
//  TOAST / SNACKBAR NOTIFICATIONS
// ═══════════════════════════════════════════════════════════════════════

const TOAST_CONTAINER_ID = "toast-container";
const TOAST_MAX_VISIBLE = 3;
const TOAST_DEFAULT_DURATIONS = {
  error: 5000,
  success: 3500,
  info: 4000,
};

const TOAST_ICONS = {
  error: "error",
  success: "check_circle",
  info: "info",
};

function ensureToastContainer() {
  let container = document.getElementById(TOAST_CONTAINER_ID);

  if (!container) {
    container = document.createElement("div");
    container.id = TOAST_CONTAINER_ID;
    container.className = "toast-container";
    container.setAttribute("aria-live", "polite");
    document.body.appendChild(container);
  }

  return container;
}

// Keeps at most TOAST_MAX_VISIBLE toasts on screen at once — oldest first.
// A toast already on its way out (toast--leaving) doesn't count against
// the cap and isn't dismissed again.
function enforceMaxVisibleToasts(container) {
  const active = Array.from(container.children).filter(
    (el) => !el.classList.contains("toast--leaving"),
  );

  const overflow = active.length - TOAST_MAX_VISIBLE;
  if (overflow <= 0) return;

  active.slice(0, overflow).forEach((el) => el._dismiss && el._dismiss());
}

function showToast(message, type = "error", duration) {
  const container = ensureToastContainer();
  const resolvedType = TOAST_ICONS[type] ? type : "error";
  const resolvedDuration =
    duration !== undefined ? duration : TOAST_DEFAULT_DURATIONS[resolvedType];

  const toast = document.createElement("div");
  toast.className = `toast toast--${resolvedType}`;

  const icon = TOAST_ICONS[resolvedType];
  const hasTimer = resolvedDuration > 0;

  toast.innerHTML = `
    <div class="toast-row">
      <span class="material-icons toast-icon">${icon}</span>
      <span class="toast-message"></span>
      <span class="material-icons toast-close">close</span>
    </div>
    ${hasTimer ? '<div class="toast-progress-track"><div class="toast-progress-bar"></div></div>' : ""}
  `;

  // Set via textContent, not innerHTML — messages are built from names/
  // values the user entered elsewhere in the form, so they must never be
  // interpreted as markup.
  toast.querySelector(".toast-message").textContent = message;

  const progressBar = toast.querySelector(".toast-progress-bar");

  const removeToast = () => {
    if (!toast.isConnected) return;
    toast.classList.remove("toast--visible");
    toast.classList.add("toast--leaving");
    setTimeout(() => toast.remove(), 200);
  };

  toast.querySelector(".toast-close").addEventListener("click", removeToast);

  // Exposed so enforceMaxVisibleToasts() (called on every new toast) can
  // evict this one from the outside, reusing the same exit animation as a
  // normal timeout/click dismissal rather than yanking it out instantly.
  toast._dismiss = removeToast;

  // Countdown/progress-bar state. remainingMs tracks time left across
  // pause/resume cycles; dismissTimer is the currently-armed setTimeout
  // (cleared on hover, re-armed on mouse-leave with whatever's left).
  let remainingMs = resolvedDuration;
  let segmentStartedAt = null;
  let dismissTimer = null;

  const armTimer = (ms) => {
    segmentStartedAt = performance.now();
    dismissTimer = setTimeout(removeToast, ms);
  };

  const pauseTimer = () => {
    if (!hasTimer || dismissTimer === null) return;
    clearTimeout(dismissTimer);
    dismissTimer = null;
    remainingMs -= performance.now() - segmentStartedAt;
    if (progressBar) progressBar.style.animationPlayState = "paused";
  };

  const resumeTimer = () => {
    if (!hasTimer || remainingMs <= 0) return;
    if (progressBar) progressBar.style.animationPlayState = "running";
    armTimer(remainingMs);
  };

  if (hasTimer) {
    toast.addEventListener("mouseenter", pauseTimer);
    toast.addEventListener("mouseleave", resumeTimer);
  }

  container.appendChild(toast);
  enforceMaxVisibleToasts(container);

  // Force a reflow before adding the "visible" class (and starting the
  // progress-bar animation/timer) so the enter transition actually plays
  // instead of the toast appearing pre-shown.
  void toast.offsetWidth;
  toast.classList.add("toast--visible");

  if (hasTimer) {
    if (progressBar) {
      progressBar.style.animationDuration = `${resolvedDuration}ms`;
      progressBar.classList.add("toast-progress-bar--running");
    }
    armTimer(resolvedDuration);
  }

  return toast;
}