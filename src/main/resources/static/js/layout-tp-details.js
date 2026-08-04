// layout-tp-details.js
// Split out of layout.js (originally lines 3546-3953) for modularity.
// Tariff package details modal, and the search/filter helpers for the drafts / saved / rejected list panels.
// Purely a location move — no logic changed. Depends on globals
// (STORAGE_KEYS, USERNAME, etc.) already loaded in <head>, and on
// functions/vars defined in the layout-*.js files loaded before it —
// load order in layout.html must be preserved.

function openTpDetails(groupData) {
  const group = JSON.parse(groupData);

  const modal = document.getElementById("tpDetailsModal");
  const content = document.getElementById("tpModalContent");

  const fee = Number(group.activationFee || 0);

  // ── Filter buckets: only VOICE, SMS, DATA ─────────────
  const ALLOWED = ["VOICE", "SMS", "DATA"];
  const buckets = (group.buckets || []).filter((b) =>
    ALLOWED.includes((b.balanceCategory || "").toUpperCase()),
  );

  const hasVoice = buckets.some((b) => b.balanceCategory === "VOICE");
  const hasSms = buckets.some((b) => b.balanceCategory === "SMS");
  const hasData = buckets.some((b) => b.balanceCategory === "DATA");

  // ── Dynamic notes based on missing categories ──────────
  const notes = [];
  if (!hasSms) notes.push("No Outgoing SMS");
  if (!hasVoice) notes.push("No Voice calls");
  if (!hasData) notes.push("No Data included");
  if (notes.length === 0)
    notes.push("All services included", "Full voice, SMS & data access");
  const notesHtml = notes.map((n) => `<li>${n}</li>`).join("");

  // ── Price block ────────────────────────────────────────
  const priceSup = `
        <div class="tp-modal-price"><sup>₸</sup>${fee.toLocaleString("en-IN")}</div>
        <div class="tp-modal-price-gst">+GST</div>`;

  // ── Buckets: value on top, label below, no dividers ───
  const bucketsHtml = buckets
    .map(
      (b) => `
        <div class="tp-modal-bucket">
            <span class="tp-modal-bucket-val">${b.bucketUnitValue || "-"}</span>
            <span class="tp-modal-bucket-key">${b.balanceCategory.toLowerCase()}</span>
        </div>`,
    )
    .join("");

  // ── DATP benefits: same visual style as the main buckets above,
  //    but no price is shown, and each DATP gets its own stacked row.
  //    The list container has a fixed max-height with scroll once it
  //    grows beyond a few rows (see .tp-modal-datp-list in builder.css).
  const datpBenefits = (group.datpBenefits || []).filter(
    (d) => d && (d.voiceBenefit || d.smsBenefit || d.dataBenefit),
  );

  const datpSectionHtml = datpBenefits.length
    ? `
        <div class="tp-modal-datp-section">
            <div class="tp-modal-datp-title">Benefits</div>
            <div class="tp-modal-datp-list">
                ${datpBenefits
                  .map((d) => {
                    const dRowBuckets = [];
                    if (d.voiceBenefit)
                      dRowBuckets.push({
                        balanceCategory: "VOICE",
                        bucketUnitValue: d.voiceBenefit,
                      });
                    if (d.smsBenefit)
                      dRowBuckets.push({
                        balanceCategory: "SMS",
                        bucketUnitValue: d.smsBenefit,
                      });
                    if (d.dataBenefit)
                      dRowBuckets.push({
                        balanceCategory: "DATA",
                        bucketUnitValue: d.dataBenefit,
                      });

                    const dBucketsHtml = dRowBuckets
                      .map(
                        (b) => `
                        <div class="tp-modal-bucket">
                            <span class="tp-modal-bucket-val">${b.bucketUnitValue || "-"}</span>
                            <span class="tp-modal-bucket-key">${b.balanceCategory.toLowerCase()}</span>
                        </div>`,
                      )
                      .join("");

                    return `
                    <div class="tp-modal-datp-row">
                        <span class="tp-modal-datp-name">${d.datpName || "DATP"}</span>
                        <div class="tp-modal-buckets">${dBucketsHtml}</div>
                    </div>`;
                  })
                  .join("")}
            </div>
        </div>`
    : "";

  // ── Modal badge label ──────────────────────────────────
  const badgeLabel =
    (group.rentalType || "").toLowerCase() === "others"
      ? group.rentalPeriod != null
        ? group.rentalPeriod +
          " Day" +
          (group.rentalPeriod !== 1 ? "s" : "") +
          " Plan"
        : "Others"
      : group.rentalType || "Individual plan";

  // ── Resolve rate-group names to KNOWN OTT services only ──
  // Anything not defined in _OTT_META (allowed:true) is dropped here —
  // no icon strip entry, no list entry, no fallback badge.
  const rateNames = group.rateGroupNames || [];
  const resolvedOtts = rateNames
    .map((name) => _ottLookup(name))
    .filter(Boolean);

  // ── OTT strip (small icons beside notes) — only recognized services ──
  const ottStripHtml = _buildOttStripHtml(rateNames, 4);

  // ── Full OTT benefit list — only recognized services ────
  const ottListHtml = resolvedOtts.length
    ? resolvedOtts
        .map((svc) => {
          const imgHtml =
            svc.srcs && svc.srcs.length
              ? svc.srcs
                  .map(
                    (sub) =>
                      `<img class="tp-modal-ott-item-img" src="${sub.src}" alt="${sub.title}"
                          style="margin-right:4px"
                          onerror="this.onerror=null;this.style.display='none';">`,
                  )
                  .join("")
              : `<img class="tp-modal-ott-item-img" src="${svc.src}" alt="${svc.title}"
                        onerror="this.onerror=null;this.style.display='none';">`;
          return `
            <div class="tp-modal-ott-item">
                <div style="display:flex;align-items:center;gap:2px">${imgHtml}</div>
                <div class="tp-modal-ott-item-info">
                    <span class="tp-modal-ott-item-name">${svc.title}</span>
                    <span class="tp-modal-ott-item-desc">${svc.desc}</span>
                </div>
            </div>`;
        })
        .join("")
    : `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;padding:20px 16px;gap:6px;text-align:center">
               <span class="material-icons" style="font-size:32px;color:red">tv_off</span>
               <span style="font-size:13px;font-weight:600;color:var(--text-secondary,#777)">No OTT benefits included</span>
               <span style="font-size:11.5px;color:var(--text-muted,#aaa)">This plan does not include any streaming services</span>
           </div>`;

  content.innerHTML = `
        <div class="tp-modal-title">${group.tariffPackageDesc || "Pack Details"}</div>

        <div class="tp-modal-badge">${badgeLabel}</div>

        <div class="tp-modal-hero">
            <div class="tp-modal-price-block">
                ${priceSup}
            </div>
            <div class="tp-modal-hero-divider"></div>
            <div class="tp-modal-buckets">
                ${bucketsHtml}
            </div>
        </div>

        ${datpSectionHtml}

        ${
          resolvedOtts.length
            ? `<div class="tp-modal-ott-row">
            <div class="tp-modal-ott-icons">${ottStripHtml}</div>
            <ul class="tp-modal-ott-notes">${notesHtml}</ul>
        </div>`
            : `<ul class="tp-modal-ott-notes" style="margin:12px 0 4px 0">${notesHtml}</ul>`
        }

        <div class="tp-modal-benefits-title">additional benefits</div>

        <div class="tp-modal-scroll-body">
            <div class="tp-modal-ott-list">
                ${ottListHtml}
            </div>
            <div class="tp-modal-your-benefits">
                <div class="tp-modal-your-benefits-title">your benefits</div>
                <p class="tp-modal-your-benefits-text">
                    ${
                      resolvedOtts.length > 0
                        ? "Includes " +
                          resolvedOtts
                            .slice(0, 3)
                            .map((s) => s.title)
                            .join(", ") +
                          (resolvedOtts.length > 3
                            ? " &amp; " +
                              (resolvedOtts.length - 3) +
                              " more OTT" +
                              (resolvedOtts.length - 3 > 1 ? "s" : "") +
                              "."
                            : ".")
                        : "No OTT benefits included."
                    }
                    ${hasData ? buckets.find((b) => b.balanceCategory === "DATA").bucketUnitValue + " Data." : "No Data included."}
                    ${!hasVoice ? "No Voice calls." : ""}
                    ${!hasSms ? "No SMS included." : ""}
                </p>
            </div>
        </div>
    `;

  modal.classList.add("active");

  // Shrink benefit values just enough to fit on one line (no wrap, no scroll)
  requestAnimationFrame(() => _autofitBenefitRows(content));
}

// ── Shrinks .tp-modal-buckets rows that overflow their container ──
// Tries progressively smaller font-size/gap steps until the row's
// content fits within its own width, then stops. Never wraps or scrolls.
function _autofitBenefitRows(scope) {
  const rows = scope.querySelectorAll(".tp-modal-buckets");
  const STEPS = [
    { val: 16, key: 11, gap: 14 },
    { val: 15, key: 10.5, gap: 12 },
    { val: 14, key: 10, gap: 10 },
    { val: 13, key: 9.5, gap: 8 },
    { val: 12, key: 9, gap: 6 },
  ];

  rows.forEach((row) => {
    for (const step of STEPS) {
      row.style.setProperty("--bucket-val-size", step.val + "px");
      row.style.setProperty("--bucket-key-size", step.key + "px");
      row.style.columnGap = step.gap + "px";
      if (row.scrollWidth <= row.clientWidth) break;
    }
  });
}

function closeTpDetails() {
  document.getElementById("tpDetailsModal").classList.remove("active");
}

// ── DRAFTS SEARCH ─────────────────────────────────────────────────
function filterDrafts(query) {
  const clr = document.getElementById("draftSearchClear");
  if (clr) clr.style.opacity = query ? "1" : "0";

  const drafts = window.ALL_DRAFTS || [];
  const container = document.getElementById("draftOverlayList");
  if (!drafts.length) return;

  const q = query.toLowerCase().trim();
  const filtered = q
    ? drafts.filter(
        (d) =>
          (d.name || "").toLowerCase().includes(q) ||
          (d.savedOn || "").toLowerCase().includes(q) ||
          (d.packageType || d.pkgType || "").toLowerCase().includes(q),
      )
    : drafts;

  if (!filtered.length) {
    container.innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">search_off</span>
                <p class="drafts-empty-title">No results for "${query}"</p>
            </div>`;
    return;
  }

  container.innerHTML = filtered
    .map((d, i) => {
      const originalIndex = drafts.indexOf(d);
      return `
        <div class="draft-item" style="--i:${i}">
            <div class="draft-info" onclick="loadDraft(${originalIndex})">
                <span class="material-icons draft-icon">description</span>
                <div class="draft-text">
                    <span class="draft-name">${d.name || "Untitled"}</span>
                    <span class="draft-meta">${d.savedOn} · ${d.savedTime}</span>
                </div>
            </div>
            <span class="material-icons draft-delete"
                  onclick="deleteDraft(${originalIndex}, event)">delete_outline</span>
        </div>`;
    })
    .join("");
}

function clearDraftSearch() {
  const inp = document.getElementById("draftSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("draftSearchClear");
  if (clr) clr.style.opacity = "0";
  filterDrafts("");
}

// ── SAVED SEARCH ──────────────────────────────────────────────────
function filterSaved(query) {
  const clr = document.getElementById("savedSearchClear");
  if (clr) clr.style.opacity = query ? "1" : "0";

  const configs = window.ALL_SAVED || [];
  const container = document.getElementById("savedOverlayList");
  if (!configs.length) return;

  const q = query.toLowerCase().trim();
  const filtered = q
    ? configs.filter(
        (c) =>
          (c.tpName || "").toLowerCase().includes(q) ||
          (c.username || "").toLowerCase().includes(q) ||
          (c.data?.submittedOn || "").toLowerCase().includes(q),
      )
    : configs;

  if (!filtered.length) {
    container.innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">search_off</span>
                <p class="drafts-empty-title">No results for "${query}"</p>
            </div>`;
    return;
  }

  container.innerHTML = filtered
    .map((c, i) => {
      const originalIndex = configs.indexOf(c);
      return `
        <div class="draft-item saved">
            <div class="draft-info" onclick="loadSavedPackage(${originalIndex})">
                <span class="material-icons draft-icon">inventory_2</span>
                <div class="draft-text">
                    <span class="draft-name">${c.tpName}</span>
                    <span class="draft-meta">${c.username} · ${c.data?.submittedOn || ""}</span>
                </div>
            </div>
            <span class="material-icons draft-delete"
                  onclick="deleteSaved('${c.tpName}', event)">delete_outline</span>
        </div>`;
    })
    .join("");
}

function clearSavedSearch() {
  const inp = document.getElementById("savedSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("savedSearchClear");
  if (clr) clr.style.opacity = "0";
  filterSaved("");
}

// ── REJECTED SEARCH ───────────────────────────────────────────────
function filterRejected(query) {
  const clr = document.getElementById("rejectedSearchClear");
  if (clr) clr.style.opacity = query ? "1" : "0";

  const items = window.ALL_REJECTED || [];
  const container = document.getElementById("rejectedOverlayList");
  if (!items.length) return;

  const q = query.toLowerCase().trim();
  const filtered = q
    ? items.filter(
        (c) =>
          (c.tpName || "").toLowerCase().includes(q) ||
          (c.username || "").toLowerCase().includes(q) ||
          (c.remarks || "").toLowerCase().includes(q) ||
          (c.rejectedOn || "").substring(0, 10).includes(q),
      )
    : items;

  if (!filtered.length) {
    container.innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">search_off</span>
                <p class="drafts-empty-title">No results for "${query}"</p>
            </div>`;
    return;
  }

  container.innerHTML = filtered
    .map((c, i) => {
      const originalIndex = items.indexOf(c);
      return `
        <div class="draft-item saved" style="--i:${i}">
            <div class="draft-info" onclick="loadRejectedPackage(${originalIndex})" style="cursor:pointer;">
                <span class="material-icons draft-icon" style="color:#ef4444;">cancel</span>
                <div class="draft-text">
                    <span class="draft-name">${c.tpName}</span>
                    <span class="draft-meta">${c.username || ""} · ${c.rejectedOn ? c.rejectedOn.substring(0, 10) : ""}</span>
                    <span class="draft-meta" style="color:#ef4444; margin-top:3px;">
                        <b>Remarks:</b> ${c.remarks || "—"}
                    </span>
                </div>
            </div>
        </div>`;
    })
    .join("");
}

function clearRejectedSearch() {
  const inp = document.getElementById("rejectedSearchInput");
  if (inp) inp.value = "";
  const clr = document.getElementById("rejectedSearchClear");
  if (clr) clr.style.opacity = "0";
  filterRejected("");
}