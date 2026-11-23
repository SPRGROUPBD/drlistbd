// Doctor List BD — Complete Advertisement & Advertiser Management System (Web Admin)
// Uses RTDB paths: /advertising/* and /homeSlider/*

const AD_PACKAGE_TYPES = [
  { key: "HOME_SLIDER", label: "Home Slider Advertisement", defaultPlacement: "HOME_TOP_SLIDER" },
  { key: "NATIVE_300x100", label: "Native Advertisement (300x100)", defaultPlacement: "DOCTOR_LIST" },
  { key: "BOTTOM_BANNER_300x50", label: "Bottom Banner Advertisement (300x50)", defaultPlacement: "BOTTOM_ALL_SCREENS" },
  { key: "APP_OPEN_POPUP_300x200", label: "App Open Popup Advertisement (300x200)", defaultPlacement: "APP_LAUNCH" }
];

const AD_PLACEMENTS = [
  { key: "HOME_TOP_SLIDER", label: "Home Screen Top Slider", forType: "HOME_SLIDER" },
  { key: "DOCTOR_LIST", label: "Doctor List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "HOSPITAL_LIST", label: "Hospital List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "DIAGNOSTIC_LIST", label: "Diagnostic Center List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "FAVORITE_LIST", label: "Favorite List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "BOTTOM_ALL_SCREENS", label: "Fixed Bottom Banner (Applicable Screens)", forType: "BOTTOM_BANNER_300x50" },
  { key: "APP_LAUNCH", label: "App Launch Popup (Once Per Session)", forType: "APP_OPEN_POPUP_300x200" }
];

const ROTATION_QUEUE_LABELS = [
  { key: "home_slider", label: "Home Slider Rotation Queue" },
  { key: "doctor_native", label: "Doctor List Native 300x100 Queue" },
  { key: "hospital_native", label: "Hospital List Native 300x100 Queue" },
  { key: "diagnostic_native", label: "Diagnostic List Native 300x100 Queue" },
  { key: "favorite_native", label: "Favorite List Native 300x100 Queue" },
  { key: "bottom_banner", label: "Bottom Banner 300x50 Queue" },
  { key: "app_open_popup", label: "App Open Popup 300x200 Queue" }
];

let adState = {
  packages: {},
  advertisers: {},
  campaigns: {},
  payments: {},
  trxIndex: {},
  paymentMethods: {},
  placements: {
    adsEnabled: true,
    homeSliderEnabled: true,
    doctorNativeEnabled: true,
    hospitalNativeEnabled: true,
    diagnosticNativeEnabled: true,
    favoriteNativeEnabled: true,
    bottomBannerEnabled: true,
    appOpenPopupEnabled: true,
    popupOncePerSession: true,
    nativeInterval: 3
  },
  rotation: {},
  homeSliderAdvertisers: {},
  homeSliderCustom: {},
  homeSliderSettings: {
    enabled: true,
    customSliderPosition: "BEFORE",
    autoSlideIntervalMs: 4500
  }
};

let adUiFilter = {
  requestStatus: "ALL",
  paymentStatus: "ALL",
  advertiserQuery: "",
  nativePlacement: "ALL",
  historyQuery: ""
};

async function rtdbAdGet(path) {
  const res = await fetch(`${RTDB_ROOT}/${path}.json`);
  if (!res.ok) return null;
  return await res.json();
}

async function rtdbAdPut(path, payload) {
  const res = await fetch(`${RTDB_ROOT}/${path}.json`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} saving ${path}`);
  return await res.json();
}

async function rtdbAdPatch(path, payload) {
  const res = await fetch(`${RTDB_ROOT}/${path}.json`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} patching ${path}`);
  return await res.json();
}

async function rtdbAdDelete(path) {
  const res = await fetch(`${RTDB_ROOT}/${path}.json`, { method: "DELETE" });
  if (!res.ok) throw new Error(`HTTP ${res.status} deleting ${path}`);
}

async function loadAdvertisingData() {
  try {
    const [advRoot, sliderRoot] = await Promise.all([
      rtdbAdGet("advertising"),
      rtdbAdGet("homeSlider")
    ]);
    const a = advRoot && typeof advRoot === "object" ? advRoot : {};
    const h = sliderRoot && typeof sliderRoot === "object" ? sliderRoot : {};

    adState.packages = a.packages && typeof a.packages === "object" ? a.packages : {};
    adState.advertisers = a.advertisers && typeof a.advertisers === "object" ? a.advertisers : {};
    adState.campaigns = a.campaigns && typeof a.campaigns === "object" ? a.campaigns : {};
    adState.payments = a.payments && typeof a.payments === "object" ? a.payments : {};
    adState.trxIndex = a.trxIndex && typeof a.trxIndex === "object" ? a.trxIndex : {};
    adState.paymentMethods = a.paymentMethods && typeof a.paymentMethods === "object" ? a.paymentMethods : {};
    adState.placements = { ...adState.placements, ...(a.placements && typeof a.placements === "object" ? a.placements : {}) };
    adState.rotation = a.rotation && typeof a.rotation === "object" ? a.rotation : {};

    adState.homeSliderAdvertisers = h.advertiserCampaigns && typeof h.advertiserCampaigns === "object" ? h.advertiserCampaigns : {};
    adState.homeSliderCustom = h.adminCustomSliders && typeof h.adminCustomSliders === "object" ? h.adminCustomSliders : {};
    adState.homeSliderSettings = { ...adState.homeSliderSettings, ...(h.settings && typeof h.settings === "object" ? h.settings : {}) };
  } catch (err) {
    console.warn("loadAdvertisingData warning:", err);
  }
}

function getEffectiveCampaignStatus(c, nowMs = Date.now()) {
  if (!c) return "PENDING";
  const st = String(c.campaignStatus || "PENDING").toUpperCase();
  if (st === "REJECTED" || st === "EXPIRED") return st;
  if (String(c.paymentStatus || "").toUpperCase() === "REJECTED" ||
      String(c.approvalStatus || "").toUpperCase() === "REJECTED") {
    return "REJECTED";
  }
  const exp = Number(c.expiryDate || 0);
  if (exp > 0 && exp < nowMs) return "EXPIRED";
  if (st === "PAUSED") return "PAUSED";
  if (String(c.paymentStatus || "").toUpperCase() === "VERIFIED" &&
      String(c.approvalStatus || "").toUpperCase() === "APPROVED" &&
      st === "ACTIVE") {
    return "ACTIVE";
  }
  return "PENDING";
}

async function sweepExpiredAdsSilently() {
  const now = Date.now();
  const updates = {};
  const sliderUpdates = {};
  for (const [id, c] of Object.entries(adState.campaigns || {})) {
    if (!c) continue;
    const exp = Number(c.expiryDate || 0);
    const cur = String(c.campaignStatus || "").toUpperCase();
    if (exp > 0 && exp < now && (cur === "ACTIVE" || cur === "PAUSED")) {
      updates[`${id}/campaignStatus`] = "EXPIRED";
      updates[`${id}/updatedAt`] = now;
      c.campaignStatus = "EXPIRED";
      c.updatedAt = now;
      if (String(c.adType || "").toUpperCase() === "HOME_SLIDER" && adState.homeSliderAdvertisers[id]) {
        sliderUpdates[`${id}/campaignStatus`] = "EXPIRED";
        adState.homeSliderAdvertisers[id].campaignStatus = "EXPIRED";
      }
    }
  }
  if (Object.keys(updates).length > 0) {
    try {
      await rtdbAdPatch("advertising/campaigns", updates);
      if (Object.keys(sliderUpdates).length > 0) {
        await rtdbAdPatch("homeSlider/advertiserCampaigns", sliderUpdates);
      }
    } catch (e) {
      console.warn("Expiry sweep patch failed", e);
    }
  }
}

function adStatusPill(status) {
  const s = String(status || "PENDING").toUpperCase();
  const map = {
    ACTIVE: "bg-emerald-100 text-emerald-800",
    VERIFIED: "bg-emerald-100 text-emerald-800",
    APPROVED: "bg-emerald-100 text-emerald-800",
    PENDING: "bg-amber-100 text-amber-800",
    PAUSED: "bg-slate-200 text-slate-800",
    INACTIVE: "bg-slate-200 text-slate-700",
    REJECTED: "bg-rose-100 text-rose-800",
    EXPIRED: "bg-rose-50 text-rose-700"
  };
  return `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${map[s] || "bg-slate-100 text-slate-700"}">${esc(s)}</span>`;
}

function getAdTabTitle(tabKey) {
  const map = {
    ad_dashboard: "Ad Dashboard & Metrics",
    ad_packages: "Package Management",
    ad_advertisers: "Advertiser Management",
    ad_requests: "Advertisement Requests",
    ad_payments: "Payment Verification",
    ad_payment_methods: "Payment Methods",
    ad_home_slider: "Home Slider Management",
    ad_custom_slider: "Custom Slider Management",
    ad_banners: "Banner Management (300x50)",
    ad_natives: "Native Ad Management (300x100)",
    ad_popups: "Popup Management (300x200)",
    ad_rotation: "Rotation Management",
    ad_history: "Campaign History",
    ad_reports: "Advertisement Reports & CSV",
    ad_settings: "Advertisement Settings"
  };
  return map[tabKey] || null;
}

function updateAdNavBadges() {
  const now = Date.now();
  const campaigns = Object.values(adState.campaigns || {});
  const activeCount = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "ACTIVE").length;
  const pendingCampCount = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "PENDING").length;
  const pendingPayCount = Object.values(adState.payments || {}).filter(p => String(p.status || "PENDING").toUpperCase() === "PENDING").length;

  const setB = (id, count) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = String(count);
    el.classList.toggle("hidden", count <= 0);
  };
  setB("navBadgeActiveAds", activeCount);
  setB("navBadgePendingAds", pendingCampCount);
  setB("navBadgePayments", pendingPayCount);
}

function renderAdvertisingTab(tabKey) {
  switch (tabKey) {
    case "ad_dashboard": return renderAdDashboard();
    case "ad_packages": return renderAdPackages();
    case "ad_advertisers": return renderAdAdvertisers();
    case "ad_requests": return renderAdRequests();
    case "ad_payments": return renderAdPayments();
    case "ad_payment_methods": return renderAdPaymentMethods();
    case "ad_home_slider": return renderAdHomeSlider();
    case "ad_custom_slider": return renderAdCustomSlider();
    case "ad_banners": return renderAdBanners();
    case "ad_natives": return renderAdNatives();
    case "ad_popups": return renderAdPopups();
    case "ad_rotation": return renderAdRotation();
    case "ad_history": return renderAdHistory();
    case "ad_reports": return renderAdReports();
    case "ad_settings": return renderAdSettings();
    default: return null;
  }
}

function resolveLinkedRecordSummary(c) {
  if (!c || String(c.adType || "").toUpperCase() !== "HOME_SLIDER") return null;
  const rType = String(c.targetRecordType || "").toLowerCase();
  const rId = String(c.targetRecordId || "").trim();
  if (!rType || !rId) return { exists: false, label: "Missing linked record ID" };
  if (rType === "doctor") {
    const doc = (dbState.doctors || {})[rId];
    return doc
      ? { exists: true, type: "Doctor", label: doc.fullNameEn || doc.fullNameBn || doc.doctorName || rId, photo: doc.photoUrl || "" }
      : { exists: false, type: "Doctor", label: `${rId} (Not found in Doctors)` };
  }
  if (rType === "hospital") {
    const h = (dbState.hospitals || {})[rId];
    return h
      ? { exists: true, type: "Hospital", label: h.nameEn || h.nameBn || rId, photo: h.imageUrl || "" }
      : { exists: false, type: "Hospital", label: `${rId} (Not found in Hospitals)` };
  }
  if (rType === "diagnostic") {
    const d = (dbState.diagnostic_centers || {})[rId];
    return d
      ? { exists: true, type: "Diagnostic", label: d.nameEn || d.nameBn || rId, photo: d.imageUrl || "" }
      : { exists: false, type: "Diagnostic", label: `${rId} (Not found in Diagnostics)` };
  }
  return { exists: false, label: `Unknown record type: ${rType}` };
}

// 1. DASHBOARD (All 11 Mandatory KPIs)
function renderAdDashboard() {
  const now = Date.now();
  const advertisers = Object.values(adState.advertisers || {});
  const packages = Object.values(adState.packages || {});
  const campaigns = Object.values(adState.campaigns || {});
  const payments = Object.values(adState.payments || {});

  const activeAds = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "ACTIVE").length;
  const pendingAds = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "PENDING").length;
  const pendingPays = payments.filter(p => String(p.status || "PENDING").toUpperCase() === "PENDING").length;
  const verifiedPays = payments.filter(p => String(p.status || "").toUpperCase() === "VERIFIED");
  const rejectedPays = payments.filter(p => String(p.status || "").toUpperCase() === "REJECTED").length;
  const totalRevenue = verifiedPays.reduce((acc, p) => acc + Number(p.amount || 0), 0);
  const totalImpressions = campaigns.reduce((acc, c) => acc + Number(c.impressionsCount || 0), 0);
  const totalClicks = campaigns.reduce((acc, c) => acc + Number(c.clicksCount || 0), 0);

  const metrics = [
    ["Total Advertisers", advertisers.length, "ad_advertisers", "text-sky-700"],
    ["Total Packages", packages.length, "ad_packages", "text-indigo-700"],
    ["Total Campaigns", campaigns.length, "ad_requests", "text-slate-900"],
    ["Active Ads", activeAds, "ad_requests", "text-emerald-700"],
    ["Pending Ads", pendingAds, "ad_requests", "text-amber-700"],
    ["Pending Payments", pendingPays, "ad_payments", "text-amber-700"],
    ["Verified Payments", verifiedPays.length, "ad_payments", "text-emerald-700"],
    ["Rejected Payments", rejectedPays, "ad_payments", "text-rose-700"],
    ["Total Revenue", "৳" + totalRevenue.toLocaleString(), "ad_reports", "text-teal-700"],
    ["Total Impressions", totalImpressions.toLocaleString(), "ad_reports", "text-sky-800"],
    ["Total Clicks", totalClicks.toLocaleString(), "ad_reports", "text-indigo-800"]
  ];

  return `
    <div class="space-y-5">
      <div class="rounded-2xl bg-gradient-to-r from-sky-800 to-teal-700 text-white p-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="text-xl font-bold">Doctor List BD — Complete Advertisement Dashboard</h3>
          <p class="text-xs text-sky-100 mt-1">Manage packages, advertisers, separate payment verification, approvals, home sliders & rotation queues.</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="switchTab('ad_payments')" class="px-3.5 py-2 rounded-xl bg-white text-sky-900 text-xs font-bold">Verify Payments (${pendingPayLabel(pendingPays)})</button>
          <button onclick="switchTab('ad_requests')" class="px-3.5 py-2 rounded-xl bg-amber-400 text-slate-950 text-xs font-bold">Review Requests (${pendingAds})</button>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        ${metrics.map(([label, val, tab, cls]) => `
          <button type="button" onclick="switchTab('${tab}')" class="text-left bg-white border border-slate-200 rounded-2xl p-4 shadow-sm hover:border-sky-300 transition">
            <div class="text-xs text-slate-500 font-medium">${esc(label)}</div>
            <div class="text-2xl font-extrabold mt-1 ${cls}">${esc(val)}</div>
          </button>
        `).join("")}
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h4 class="font-bold text-sm mb-2">Package Distribution (Active Campaigns)</h4>
          <div class="space-y-2 text-xs">
            ${AD_PACKAGE_TYPES.map(t => {
              const cnt = campaigns.filter(c => c.adType === t.key && getEffectiveCampaignStatus(c, now) === "ACTIVE").length;
              return `<div class="flex justify-between p-2 rounded-lg bg-slate-50"><span>${esc(t.label)}</span><b>${cnt} active</b></div>`;
            }).join("")}
          </div>
        </div>
        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h4 class="font-bold text-sm mb-2">Quick Actions</h4>
          <div class="grid grid-cols-2 gap-2">
            <button onclick="switchTab('ad_packages')" class="p-3 rounded-xl bg-sky-50 text-sky-900 text-xs font-bold text-left hover:bg-sky-100">+ Create Ad Package</button>
            <button onclick="switchTab('ad_payment_methods')" class="p-3 rounded-xl bg-teal-50 text-teal-900 text-xs font-bold text-left hover:bg-teal-100">Manage Payment Methods</button>
            <button onclick="switchTab('ad_custom_slider')" class="p-3 rounded-xl bg-indigo-50 text-indigo-900 text-xs font-bold text-left hover:bg-indigo-100">+ Add Custom Home Slider</button>
            <button onclick="sweepAndRefreshAds()" class="p-3 rounded-xl bg-slate-100 text-slate-800 text-xs font-bold text-left hover:bg-slate-200">Sweep Expired Ads</button>
          </div>
        </div>
      </div>
    </div>
  `;
}

function pendingPayLabel(n) { return String(n || 0); }

async function sweepAndRefreshAds() {
  await sweepExpiredAdsSilently();
  toast("Expired campaigns swept & updated!");
  renderTab();
}

// 2. PACKAGE MANAGEMENT (Section 1)
function renderAdPackages() {
  const pkgList = Object.entries(adState.packages || {})
    .map(([id, p]) => ({ id, ...p }))
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
  const edit = editing.adPackage || null;
  const selType = edit ? edit.adType : "HOME_SLIDER";

  return `
    <div class="grid grid-cols-1 xl:grid-cols-12 gap-5">
      <div class="xl:col-span-5 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">${edit ? "Edit Advertisement Package" : "Create Unlimited Ad Package"}</h3>
        <p class="text-xs text-slate-500 mb-3">Only ACTIVE packages are selectable by advertisers in the Android app.</p>
        <form onsubmit="submitAdPackage(event)" class="space-y-3">
          <input type="hidden" id="pkg_id" value="${esc(edit ? edit.id : "")}" />
          <div>
            <label class="block text-xs font-semibold mb-1">Package Name *</label>
            <input id="pkg_name" required class="input" placeholder="e.g. Home Top Slider Featured 30 Days" value="${esc(edit ? edit.name : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Description</label>
            <textarea id="pkg_desc" rows="2" class="input" placeholder="Package details shown to advertisers">${esc(edit ? edit.description : "")}</textarea>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-xs font-semibold mb-1">Advertisement Type *</label>
              <select id="pkg_type" class="input" onchange="syncPackageDefaultPlacement(this.value)">
                ${AD_PACKAGE_TYPES.map(t => `<option value="${t.key}" ${selType === t.key ? "selected" : ""}>${esc(t.label)}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="block text-xs font-semibold mb-1">Placement *</label>
              <select id="pkg_placement" class="input">
                ${AD_PLACEMENTS.map(p => `<option value="${p.key}" ${(edit ? edit.placement : "HOME_TOP_SLIDER") === p.key ? "selected" : ""}>${esc(p.label)}</option>`).join("")}
              </select>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2">
            <div>
              <label class="block text-xs font-semibold mb-1">Price (৳) *</label>
              <input id="pkg_price" type="number" min="1" step="1" required class="input" value="${esc(edit ? edit.price : 1500)}" />
            </div>
            <div>
              <label class="block text-xs font-semibold mb-1">Duration (Days) *</label>
              <input id="pkg_days" type="number" min="1" required class="input" value="${esc(edit ? edit.durationDays : 30)}" />
            </div>
            <div>
              <label class="block text-xs font-semibold mb-1">Max Days *</label>
              <input id="pkg_max_days" type="number" min="1" required class="input" value="${esc(edit ? edit.maxCampaignDurationDays : 365)}" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Package Image URL (optional)</label>
            <input id="pkg_img" type="url" class="input" placeholder="https://..." value="${esc(edit ? (edit.imageUrl || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Status *</label>
            <select id="pkg_status" class="input">
              <option value="ACTIVE" ${!edit || edit.status === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
              <option value="PAUSED" ${edit && edit.status === "PAUSED" ? "selected" : ""}>PAUSED</option>
              <option value="INACTIVE" ${edit && edit.status === "INACTIVE" ? "selected" : ""}>INACTIVE</option>
            </select>
          </div>
          <button type="submit" class="w-full py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">${edit ? "Update Package" : "Save Package"}</button>
          ${edit ? `<button type="button" onclick="editing={};renderTab()" class="w-full py-1.5 text-xs text-slate-500">Cancel Edit</button>` : ""}
        </form>
      </div>

      <div class="xl:col-span-7 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-3">All Advertising Packages (${pkgList.length})</h3>
        ${pkgList.length === 0 ? `<p class="text-xs text-slate-400 py-8 text-center">No packages created yet. Create packages for Home Slider, Native 300x100, Bottom Banner 300x50, and App Open Popup 300x200.</p>` : `
          <div class="space-y-3">
            ${pkgList.map(p => `
              <div class="border border-slate-200 rounded-xl p-3.5 flex flex-wrap justify-between gap-3">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm text-slate-900">${esc(p.name)}</b>
                    ${adStatusPill(p.status)}
                  </div>
                  <div class="text-xs text-sky-800 font-semibold mt-1">${esc(p.adType)} &bull; ${esc(p.placement)}</div>
                  <div class="text-xs text-slate-600 mt-0.5">Price: <b>৳${Number(p.price || 0).toLocaleString()}</b> &bull; Duration: <b>${p.durationDays} days</b> (Max ${p.maxCampaignDurationDays}d)</div>
                  ${p.description ? `<p class="text-[11px] text-slate-500 mt-1">${esc(p.description)}</p>` : ""}
                </div>
                <div class="flex flex-col items-end gap-1.5 text-xs">
                  <div class="flex gap-1.5">
                    <button onclick="editAdPackage('${esc(p.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                    <button onclick="cyclePackageStatus('${esc(p.id)}')" class="px-2.5 py-1 rounded-lg bg-amber-50 text-amber-800 font-semibold">${p.status === "ACTIVE" ? "Pause" : "Activate"}</button>
                    <button onclick="deleteAdPackage('${esc(p.id)}')" class="px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 font-semibold">Delete</button>
                  </div>
                  <span class="text-[10px] text-slate-400">Created ${formatDateOnly(p.createdAt)}</span>
                </div>
              </div>
            `).join("")}
          </div>
        `}
      </div>
    </div>
  `;
}

function syncPackageDefaultPlacement(adType) {
  const sel = document.getElementById("pkg_placement");
  const found = AD_PACKAGE_TYPES.find(t => t.key === adType);
  if (sel && found) sel.value = found.defaultPlacement;
}

async function submitAdPackage(e) {
  e.preventDefault();
  const id = document.getElementById("pkg_id").value || uid("pkg");
  const old = adState.packages[id] || {};
  const now = Date.now();
  const payload = {
    id,
    name: document.getElementById("pkg_name").value.trim(),
    description: document.getElementById("pkg_desc").value.trim(),
    adType: document.getElementById("pkg_type").value,
    placement: document.getElementById("pkg_placement").value,
    price: Number(document.getElementById("pkg_price").value || 0),
    durationDays: Math.max(1, parseInt(document.getElementById("pkg_days").value || "30", 10)),
    maxCampaignDurationDays: Math.max(1, parseInt(document.getElementById("pkg_max_days").value || "365", 10)),
    imageUrl: document.getElementById("pkg_img").value.trim(),
    status: document.getElementById("pkg_status").value || "ACTIVE",
    createdAt: old.createdAt || now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/packages/${id}`, payload);
  adState.packages[id] = payload;
  editing = {};
  await writeAuditLog("SAVE_AD_PACKAGE", "AD_PACKAGE", id, `Saved package ${payload.name} (৳${payload.price})`);
  toast("Package saved!");
  renderTab();
}

function editAdPackage(id) {
  const p = adState.packages[id];
  if (!p) return;
  editing = { adPackage: { id, ...p } };
  renderTab();
}

async function cyclePackageStatus(id) {
  const p = adState.packages[id];
  if (!p) return;
  const next = p.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
  const updated = { ...p, status: next, updatedAt: Date.now() };
  await rtdbAdPut(`advertising/packages/${id}`, updated);
  adState.packages[id] = updated;
  toast(`Package ${next}`);
  renderTab();
}

async function deleteAdPackage(id) {
  if (!confirm("Delete this advertising package? Existing campaigns keep their snapshot price/duration.")) return;
  await rtdbAdDelete(`advertising/packages/${id}`);
  delete adState.packages[id];
  toast("Package deleted.");
  renderTab();
}

// 3. ADVERTISER MANAGEMENT
function renderAdAdvertisers() {
  const q = adUiFilter.advertiserQuery.trim().toLowerCase();
  const list = Object.entries(adState.advertisers || {})
    .map(([id, a]) => ({ id, ...a }))
    .filter(a => [a.advertiserName, a.businessName, a.mobile, a.email, a.category].some(v => String(v || "").toLowerCase().includes(q)))
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  const campList = Object.values(adState.campaigns || {});

  return `
    <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
      <div class="flex flex-wrap justify-between items-center gap-3">
        <div>
          <h3 class="font-bold text-base">Registered Advertisers (${list.length})</h3>
          <p class="text-xs text-slate-500">View advertiser accounts, campaign volume, and activate or suspend access.</p>
        </div>
        <input class="input max-w-xs" placeholder="Search name, business, mobile..." value="${esc(adUiFilter.advertiserQuery)}"
          oninput="adUiFilter.advertiserQuery=this.value;renderTab()" />
      </div>
      ${list.length === 0 ? `<p class="text-xs text-slate-400 py-8 text-center">No advertiser accounts found.</p>` : `
        <div class="overflow-x-auto">
          <table class="w-full text-xs text-left">
            <thead>
              <tr class="bg-slate-50 text-slate-600">
                <th class="p-3">Business &amp; Owner</th>
                <th class="p-3">Mobile / Email</th>
                <th class="p-3">Category</th>
                <th class="p-3">Campaigns</th>
                <th class="p-3">Status</th>
                <th class="p-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              ${list.map(a => {
                const count = campList.filter(c => c.advertiserId === a.id).length;
                const isSusp = String(a.status || "ACTIVE").toUpperCase() !== "ACTIVE";
                return `
                  <tr class="border-t border-slate-100">
                    <td class="p-3">
                      <div class="font-bold text-slate-900">${esc(a.businessName || "—")}</div>
                      <div class="text-slate-500">${esc(a.advertiserName || "—")} &bull; ${esc(a.address || "")}</div>
                    </td>
                    <td class="p-3">
                      <div>${esc(a.mobile || "—")}</div>
                      <div class="text-slate-500">${esc(a.email || "—")}</div>
                    </td>
                    <td class="p-3">${esc(a.category || "Healthcare")}</td>
                    <td class="p-3 font-bold">${count}</td>
                    <td class="p-3">${adStatusPill(a.status || "ACTIVE")}</td>
                    <td class="p-3 whitespace-nowrap">
                      <button onclick="toggleAdvertiserAccountStatus('${esc(a.id)}')" class="px-2.5 py-1 rounded-lg ${isSusp ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"} font-semibold">
                        ${isSusp ? "Activate" : "Suspend"}
                      </button>
                    </td>
                  </tr>
                `;
              }).join("")}
            </tbody>
          </table>
        </div>
      `}
    </div>
  `;
}

async function toggleAdvertiserAccountStatus(advId) {
  const a = adState.advertisers[advId];
  if (!a) return;
  const next = String(a.status || "ACTIVE").toUpperCase() === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
  const updated = { ...a, status: next, updatedAt: Date.now() };
  await rtdbAdPut(`advertising/advertisers/${advId}`, updated);
  adState.advertisers[advId] = updated;
  toast(`Advertiser account ${next}`);
  renderTab();
}

// 4. ADVERTISEMENT REQUESTS (Separate Campaign Approval from Payment Verification!)
function renderAdRequests() {
  const now = Date.now();
  const filter = adUiFilter.requestStatus || "ALL";
  const list = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => filter === "ALL" || c.effectiveStatus === filter || c.approvalStatus === filter)
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  return `
    <div class="space-y-4">
      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="font-bold text-base">Advertisement Requests &amp; Campaign Approval (${list.length})</h3>
          <p class="text-xs text-slate-500">Campaign goes ACTIVE only after BOTH Payment Verification (VERIFIED) and Campaign Approval (APPROVED).</p>
        </div>
        <select class="input max-w-[200px]" onchange="adUiFilter.requestStatus=this.value;renderTab()">
          ${["ALL", "PENDING", "ACTIVE", "PAUSED", "EXPIRED", "REJECTED"].map(f => `<option value="${f}" ${filter === f ? "selected" : ""}>${f}</option>`).join("")}
        </select>
      </div>

      ${list.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No campaigns match filter.</div>` : list.map(c => {
        const linked = resolveLinkedRecordSummary(c);
        const eff = c.effectiveStatus;
        return `
          <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm space-y-3">
            <div class="flex flex-wrap justify-between gap-3">
              <div class="flex gap-3 min-w-0 flex-1">
                ${c.imageUrl ? `<img src="${esc(c.imageUrl)}" class="w-20 h-14 rounded-xl object-cover border border-slate-200 bg-slate-50 flex-shrink-0" onerror="this.style.display='none'" />` : ""}
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-1.5">
                    <b class="text-sm text-slate-900">${esc(c.title || c.targetRecordTitle || "Untitled Ad")}</b>
                    ${adStatusPill(eff)}
                    <span class="px-2 py-0.5 rounded-full bg-sky-50 text-sky-800 text-[10px] font-bold">${esc(c.adType)}</span>
                    <span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-[10px] font-semibold">${esc(c.placement)}</span>
                  </div>
                  <div class="text-xs text-slate-600 mt-1">
                    Advertiser: <b>${esc(c.businessName || c.advertiserName)}</b> (${esc(c.contactMobile || "")}) &bull; Package: <b>${esc(c.packageName)}</b> (৳${Number(c.price || 0).toLocaleString()} / ${c.durationDays}d)
                  </div>
                  ${linked ? `
                    <div class="mt-1 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-semibold ${linked.exists ? "bg-teal-50 text-teal-800" : "bg-rose-50 text-rose-700"}">
                      Linked ${esc(linked.type || "Record")}: ${esc(linked.label)}
                    </div>
                  ` : ""}
                  <div class="flex flex-wrap gap-3 text-[11px] text-slate-500 mt-1.5">
                    <span>Payment: ${adStatusPill(c.paymentStatus)}</span>
                    <span>Approval: ${adStatusPill(c.approvalStatus)}</span>
                    <span>Impressions: <b>${Number(c.impressionsCount || 0).toLocaleString()}</b></span>
                    <span>Clicks: <b>${Number(c.clicksCount || 0).toLocaleString()}</b></span>
                    ${c.expiryDate ? `<span>Valid: ${formatDateOnly(c.startDate)} – ${formatDateOnly(c.expiryDate)}</span>` : ""}
                  </div>
                </div>
              </div>
              <div class="flex flex-wrap items-start gap-1.5 text-xs">
                ${c.approvalStatus !== "APPROVED" && eff !== "EXPIRED" ? `
                  <button onclick="approveCampaignContent('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-emerald-600 text-white font-bold">Approve Ad</button>
                ` : ""}
                ${eff !== "REJECTED" && eff !== "EXPIRED" ? `
                  <button onclick="rejectCampaignContent('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-rose-50 text-rose-700 font-bold">Reject Ad</button>
                ` : ""}
                ${eff === "ACTIVE" ? `
                  <button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-xl bg-amber-100 text-amber-900 font-bold">Pause</button>
                ` : ""}
                ${eff === "PAUSED" ? `
                  <button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-xl bg-sky-600 text-white font-bold">Resume</button>
                ` : ""}
              </div>
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

async function recalculateCampaignActivation(campId) {
  const c = adState.campaigns[campId];
  if (!c) return;
  const now = Date.now();
  const payOk = String(c.paymentStatus || "").toUpperCase() === "VERIFIED";
  const appOk = String(c.approvalStatus || "").toUpperCase() === "APPROVED";
  const rejected = String(c.paymentStatus || "").toUpperCase() === "REJECTED" ||
                   String(c.approvalStatus || "").toUpperCase() === "REJECTED";

  let nextStatus = c.campaignStatus || "PENDING";
  let startDate = Number(c.startDate || 0);
  let expiryDate = Number(c.expiryDate || 0);
  let activatedAt = Number(c.activatedAt || 0);

  if (rejected) {
    nextStatus = "REJECTED";
  } else if (expiryDate > 0 && expiryDate < now) {
    nextStatus = "EXPIRED";
  } else if (payOk && appOk) {
    if (nextStatus !== "PAUSED") nextStatus = "ACTIVE";
    if (!startDate) startDate = now;
    if (!activatedAt) activatedAt = now;
    if (!expiryDate) {
      const days = Math.max(1, Number(c.durationDays || 30));
      expiryDate = startDate + days * 24 * 60 * 60 * 1000;
    }
  } else {
    nextStatus = "PENDING";
  }

  const updated = {
    ...c,
    campaignStatus: nextStatus,
    startDate,
    expiryDate,
    activatedAt,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/campaigns/${campId}`, updated);
  adState.campaigns[campId] = updated;

  if (String(updated.adType || "").toUpperCase() === "HOME_SLIDER") {
    const existingSlider = adState.homeSliderAdvertisers[campId] || {};
    const sliderPayload = {
      ...existingSlider,
      campaignId: campId,
      advertiserId: updated.advertiserId,
      targetRecordType: updated.targetRecordType,
      targetRecordId: updated.targetRecordId,
      approvalStatus: updated.approvalStatus,
      paymentStatus: updated.paymentStatus,
      campaignStatus: updated.campaignStatus,
      paused: updated.campaignStatus === "PAUSED",
      startDate: updated.startDate,
      expiryDate: updated.expiryDate,
      priority: existingSlider.priority || 100,
      createdAt: existingSlider.createdAt || updated.createdAt || now
    };
    await rtdbAdPut(`homeSlider/advertiserCampaigns/${campId}`, sliderPayload);
    adState.homeSliderAdvertisers[campId] = sliderPayload;
  }
}

async function approveCampaignContent(campId) {
  const c = adState.campaigns[campId];
  if (!c) return;
  if (String(c.adType || "").toUpperCase() === "HOME_SLIDER") {
    const linked = resolveLinkedRecordSummary(c);
    if (!linked || !linked.exists) {
      return alert("Cannot approve Home Slider: referenced Doctor / Hospital / Diagnostic Center record does not exist.");
    }
  }
  c.approvalStatus = "APPROVED";
  if (c.campaignStatus === "REJECTED") c.campaignStatus = "PENDING";
  await recalculateCampaignActivation(campId);
  await writeAuditLog("APPROVE_AD_CAMPAIGN", "AD_CAMPAIGN", campId, `Approved campaign ${c.title || campId}`);
  toast(adState.campaigns[campId].campaignStatus === "ACTIVE"
    ? "Campaign APPROVED and now LIVE!"
    : "Campaign APPROVED! Waiting for Payment Verification.");
  renderTab();
}

async function rejectCampaignContent(campId) {
  const c = adState.campaigns[campId];
  if (!c) return;
  const reason = prompt("Reason for rejecting this advertisement (optional):", "Creative/content does not meet guidelines");
  if (reason === null) return;
  c.approvalStatus = "REJECTED";
  c.rejectionReason = reason;
  await recalculateCampaignActivation(campId);
  await writeAuditLog("REJECT_AD_CAMPAIGN", "AD_CAMPAIGN", campId, `Rejected campaign ${c.title || campId}`);
  toast("Campaign rejected.");
  renderTab();
}

async function setCampaignPaused(campId, pause) {
  const c = adState.campaigns[campId];
  if (!c) return;
  if (!pause && getEffectiveCampaignStatus(c) === "EXPIRED") {
    return alert("Expired campaigns cannot be auto-reactivated. Advertiser must submit a new renewal payment.");
  }
  c.campaignStatus = pause ? "PAUSED" : "ACTIVE";
  await recalculateCampaignActivation(campId);
  await writeAuditLog(pause ? "PAUSE_AD_CAMPAIGN" : "RESUME_AD_CAMPAIGN", "AD_CAMPAIGN", campId, `${pause ? "Paused" : "Resumed"} ${c.title || campId}`);
  toast(pause ? "Campaign paused." : "Campaign resumed.");
  renderTab();
}

// 5. PAYMENT VERIFICATION (Separate Control + Duplicate TRX check)
function renderAdPayments() {
  const filter = adUiFilter.paymentStatus || "ALL";
  const allPayments = Object.entries(adState.payments || {}).map(([id, p]) => ({ id, ...p }));
  const trxCounts = {};
  allPayments.forEach(p => {
    const n = String(p.normalizedTrxId || p.trxId || "").trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "");
    if (n) trxCounts[n] = (trxCounts[n] || 0) + 1;
  });

  const list = allPayments
    .filter(p => filter === "ALL" || String(p.status || "PENDING").toUpperCase() === filter)
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  return `
    <div class="space-y-4">
      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="font-bold text-base">Payment Verification (${list.length})</h3>
          <p class="text-xs text-slate-500">Verify or reject advertiser payments independently of ad content approval.</p>
        </div>
        <select class="input max-w-[180px]" onchange="adUiFilter.paymentStatus=this.value;renderTab()">
          ${["ALL", "PENDING", "VERIFIED", "REJECTED"].map(f => `<option value="${f}" ${filter === f ? "selected" : ""}>${f}</option>`).join("")}
        </select>
      </div>

      ${list.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No payments match filter.</div>` : list.map(p => {
        const normTrx = String(p.normalizedTrxId || p.trxId || "").trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "");
        const isDup = normTrx && (trxCounts[normTrx] || 0) > 1;
        const st = String(p.status || "PENDING").toUpperCase();
        return `
          <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap justify-between gap-3">
            <div class="space-y-1 text-xs min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <b class="text-sm text-slate-900">TRX: ${esc(p.trxId)}</b>
                ${adStatusPill(st)}
                ${isDup ? `<span class="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-[10px] font-bold">DUPLICATE TRX</span>` : ""}
              </div>
              <div class="text-slate-700">
                <b>${esc(p.businessName || p.advertiserName)}</b> &bull; Package: <b>${esc(p.packageName)}</b> &bull; Paid: <b class="text-teal-700">৳${Number(p.amount || 0).toLocaleString()}</b> (Expected: ৳${Number(p.expectedAmount || 0).toLocaleString()})
              </div>
              <div class="text-slate-500">
                Method: <b>${esc(p.provider)} — ${esc(p.methodName)}</b> &bull; Sender: <b>${esc(p.senderMobile)}</b> &rarr; Receiving: <b>${esc(p.receivingNumber)}</b> &bull; Date: ${esc(p.paymentDate || formatDateOnly(p.createdAt))}
              </div>
              ${p.screenshotUrl ? `<a href="${esc(p.screenshotUrl)}" target="_blank" class="inline-block text-sky-700 font-semibold hover:underline">View Payment Screenshot &nearr;</a>` : ""}
              ${p.rejectionReason ? `<div class="text-rose-700 font-medium">Rejection Note: ${esc(p.rejectionReason)}</div>` : ""}
            </div>
            <div class="flex flex-wrap items-start gap-1.5 text-xs">
              ${st !== "VERIFIED" ? `<button onclick="verifyAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-emerald-600 text-white font-bold">Verify Payment</button>` : ""}
              ${st !== "REJECTED" ? `<button onclick="rejectAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-rose-50 text-rose-700 font-bold">Reject</button>` : ""}
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

async function verifyAdPayment(payId) {
  const p = adState.payments[payId];
  if (!p) return;
  const now = Date.now();
  const updatedPay = {
    ...p,
    status: "VERIFIED",
    verifiedBy: getAdminSession().name,
    verifiedAt: now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/payments/${payId}`, updatedPay);
  adState.payments[payId] = updatedPay;

  if (p.campaignId && adState.campaigns[p.campaignId]) {
    adState.campaigns[p.campaignId].paymentStatus = "VERIFIED";
    if (adState.campaigns[p.campaignId].campaignStatus === "REJECTED") {
      adState.campaigns[p.campaignId].campaignStatus = "PENDING";
    }
    await recalculateCampaignActivation(p.campaignId);
  }
  await writeAuditLog("VERIFY_AD_PAYMENT", "AD_PAYMENT", payId, `Verified payment TRX ${p.trxId} (৳${p.amount})`);
  toast("Payment VERIFIED!");
  renderTab();
}

async function rejectAdPayment(payId) {
  const p = adState.payments[payId];
  if (!p) return;
  const reason = prompt("Reason for rejecting this payment:", "Transaction ID not matched / invalid amount");
  if (reason === null) return;
  const now = Date.now();
  const updatedPay = {
    ...p,
    status: "REJECTED",
    rejectionReason: reason,
    verifiedBy: getAdminSession().name,
    verifiedAt: now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/payments/${payId}`, updatedPay);
  adState.payments[payId] = updatedPay;

  if (p.campaignId && adState.campaigns[p.campaignId]) {
    adState.campaigns[p.campaignId].paymentStatus = "REJECTED";
    await recalculateCampaignActivation(p.campaignId);
  }
  await writeAuditLog("REJECT_AD_PAYMENT", "AD_PAYMENT", payId, `Rejected payment TRX ${p.trxId}`);
  toast("Payment rejected.");
  renderTab();
}

// 6. PAYMENT METHODS (Dynamic receiving accounts visible to advertisers)
function renderAdPaymentMethods() {
  const methods = Object.entries(adState.paymentMethods || {}).map(([id, m]) => ({ id, ...m }));
  const edit = editing.paymentMethod || null;
  return `
    <div class="grid grid-cols-1 xl:grid-cols-12 gap-5">
      <div class="xl:col-span-5 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-3">${edit ? "Edit Payment Method" : "Add Payment Method"}</h3>
        <form onsubmit="submitAdPaymentMethod(event)" class="space-y-3">
          <input type="hidden" id="pm_id" value="${esc(edit ? edit.id : "")}" />
          <div>
            <label class="block text-xs font-semibold mb-1">Provider *</label>
            <select id="pm_provider" class="input">
              ${["bKash", "Nagad", "Rocket", "Upay", "Bank"].map(pr => `<option value="${pr}" ${(edit ? edit.provider : "bKash") === pr ? "selected" : ""}>${pr}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Method Name *</label>
            <input id="pm_name" required class="input" placeholder="e.g. bKash Merchant Payment" value="${esc(edit ? edit.methodName : "")}" />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-xs font-semibold mb-1">Receiving Number *</label>
              <input id="pm_num" required class="input" placeholder="017XXXXXXXX" value="${esc(edit ? edit.receivingNumber : "")}" />
            </div>
            <div>
              <label class="block text-xs font-semibold mb-1">Account Type *</label>
              <select id="pm_acc_type" class="input">
                ${["Personal", "Merchant", "Agent", "Bank Account"].map(t => `<option value="${t}" ${(edit ? edit.accountType : "Personal") === t ? "selected" : ""}>${t}</option>`).join("")}
              </select>
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Payment Instructions</label>
            <textarea id="pm_inst" rows="2" class="input" placeholder="Send Money / Payment reference instructions">${esc(edit ? (edit.instructions || "") : "")}</textarea>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Status</label>
            <select id="pm_status" class="input">
              <option value="ACTIVE" ${!edit || edit.status === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
              <option value="INACTIVE" ${edit && edit.status === "INACTIVE" ? "selected" : ""}>INACTIVE</option>
            </select>
          </div>
          <button type="submit" class="w-full py-2.5 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold">${edit ? "Update Method" : "Save Method"}</button>
          ${edit ? `<button type="button" onclick="editing={};renderTab()" class="w-full py-1.5 text-xs text-slate-500">Cancel Edit</button>` : ""}
        </form>
      </div>

      <div class="xl:col-span-7 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-3">Configured Payment Methods (${methods.length})</h3>
        ${methods.length === 0 ? `<p class="text-xs text-slate-400 py-8 text-center">No payment methods configured yet.</p>` : `
          <div class="space-y-2.5">
            ${methods.map(m => `
              <div class="border border-slate-200 rounded-xl p-3.5 flex flex-wrap justify-between gap-2">
                <div class="text-xs">
                  <div class="flex items-center gap-2">
                    <b class="text-sm text-slate-900">${esc(m.provider)} — ${esc(m.methodName)}</b>
                    ${adStatusPill(m.status)}
                  </div>
                  <div class="text-teal-800 font-bold mt-1">${esc(m.receivingNumber)} (${esc(m.accountType)})</div>
                  ${m.instructions ? `<div class="text-slate-500 mt-0.5">${esc(m.instructions)}</div>` : ""}
                </div>
                <div class="flex items-center gap-1.5 text-xs">
                  <button onclick="editAdPaymentMethod('${esc(m.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                  <button onclick="deleteAdPaymentMethod('${esc(m.id)}')" class="px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 font-semibold">Delete</button>
                </div>
              </div>
            `).join("")}
          </div>
        `}
      </div>
    </div>
  `;
}

async function submitAdPaymentMethod(e) {
  e.preventDefault();
  const id = document.getElementById("pm_id").value || uid("pm");
  const payload = {
    id,
    provider: document.getElementById("pm_provider").value,
    methodName: document.getElementById("pm_name").value.trim(),
    receivingNumber: document.getElementById("pm_num").value.trim(),
    accountType: document.getElementById("pm_acc_type").value,
    instructions: document.getElementById("pm_inst").value.trim(),
    status: document.getElementById("pm_status").value,
    updatedAt: Date.now()
  };
  await rtdbAdPut(`advertising/paymentMethods/${id}`, payload);
  adState.paymentMethods[id] = payload;
  editing = {};
  toast("Payment method saved!");
  renderTab();
}

function editAdPaymentMethod(id) {
  const m = adState.paymentMethods[id];
  if (!m) return;
  editing = { paymentMethod: { id, ...m } };
  renderTab();
}

async function deleteAdPaymentMethod(id) {
  if (!confirm("Delete this payment method?")) return;
  await rtdbAdDelete(`advertising/paymentMethods/${id}`);
  delete adState.paymentMethods[id];
  toast("Payment method deleted.");
  renderTab();
}

// 7. HOME SLIDER MANAGEMENT (Advertiser Existing Record Sliders)
function renderAdHomeSlider() {
  const now = Date.now();
  const sliderCamps = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => String(c.adType || "").toUpperCase() === "HOME_SLIDER")
    .sort((a, b) => Number((adState.homeSliderAdvertisers[a.id]?.priority) || 100) - Number((adState.homeSliderAdvertisers[b.id]?.priority) || 100));

  return `
    <div class="space-y-4">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap justify-between items-center gap-3">
        <div>
          <h3 class="font-bold text-base">Advertiser Home Slider Campaigns (${sliderCamps.length})</h3>
          <p class="text-xs text-slate-500">Links strictly to existing Doctor, Hospital, or Diagnostic Center records — zero duplicate directory data.</p>
        </div>
        <button onclick="switchTab('ad_custom_slider')" class="px-3.5 py-2 rounded-xl bg-teal-600 text-white text-xs font-bold">Manage Custom Sliders &amp; Order</button>
      </div>

      ${sliderCamps.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No Advertiser Home Slider campaigns yet.</div>` : sliderCamps.map(c => {
        const linked = resolveLinkedRecordSummary(c);
        const idxObj = adState.homeSliderAdvertisers[c.id] || {};
        return `
          <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap justify-between items-center gap-3">
            <div class="text-xs space-y-1">
              <div class="flex items-center gap-2">
                <b class="text-sm text-slate-900">${esc(linked ? linked.label : (c.targetRecordTitle || c.title))}</b>
                ${adStatusPill(c.effectiveStatus)}
                <span class="px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-800 text-[10px] font-bold">${esc(c.targetRecordType)}</span>
              </div>
              <div class="text-slate-500">Advertiser: ${esc(c.businessName || c.advertiserName)} &bull; Record ID: <code>${esc(c.targetRecordId)}</code></div>
              <div class="text-slate-500">Impressions: <b>${c.impressionsCount || 0}</b> &bull; Clicks: <b>${c.clicksCount || 0}</b> &bull; Priority: <b>${idxObj.priority || 100}</b></div>
            </div>
            <div class="flex items-center gap-2 text-xs">
              <input type="number" min="1" class="input w-20" value="${esc(idxObj.priority || 100)}"
                onchange="updateHomeSliderPriority('${esc(c.id)}', this.value)" title="Lower appears earlier" />
              ${c.effectiveStatus === "ACTIVE" ? `<button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-lg bg-amber-100 text-amber-900 font-bold">Pause</button>` : ""}
              ${c.effectiveStatus === "PAUSED" ? `<button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-lg bg-sky-600 text-white font-bold">Resume</button>` : ""}
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

async function updateHomeSliderPriority(campId, val) {
  const prio = Math.max(1, parseInt(val || "100", 10));
  const cur = adState.homeSliderAdvertisers[campId] || { campaignId: campId };
  const updated = { ...cur, priority: prio };
  await rtdbAdPut(`homeSlider/advertiserCampaigns/${campId}`, updated);
  adState.homeSliderAdvertisers[campId] = updated;
  toast("Slider priority updated!");
}

// 8. ADMIN CUSTOM SLIDER MANAGEMENT + BEFORE/AFTER POSITION TOGGLE (Sections 4B & 4C)
function renderAdCustomSlider() {
  const customList = Object.entries(adState.homeSliderCustom || {})
    .map(([id, s]) => ({ id, ...s }))
    .sort((a, b) => Number(a.order || 1) - Number(b.order || 1));
  const edit = editing.customSlider || null;
  const settings = adState.homeSliderSettings || { enabled: true, customSliderPosition: "BEFORE", autoSlideIntervalMs: 4500 };

  return `
    <div class="space-y-5">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">Home Slider Display Order &amp; Controls</h3>
        <p class="text-xs text-slate-500 mb-3">Choose whether Admin Custom Sliders appear BEFORE or AFTER Advertiser Sliders on the Android Home screen.</p>
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-3 items-end">
          <div>
            <label class="block text-xs font-semibold mb-1">Custom Slider Position</label>
            <select id="hs_pos" class="input">
              <option value="BEFORE" ${settings.customSliderPosition !== "AFTER" ? "selected" : ""}>BEFORE Advertiser Sliders</option>
              <option value="AFTER" ${settings.customSliderPosition === "AFTER" ? "selected" : ""}>AFTER Advertiser Sliders</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Auto-Slide Interval (ms)</label>
            <input id="hs_interval" type="number" min="2000" max="15000" step="500" class="input" value="${esc(settings.autoSlideIntervalMs || 4500)}" />
          </div>
          <div>
            <label class="flex items-center gap-2 text-xs font-semibold py-2.5">
              <input id="hs_enabled" type="checkbox" ${settings.enabled !== false ? "checked" : ""} /> Home Slider Enabled
            </label>
          </div>
          <button onclick="saveHomeSliderSettings()" class="py-2.5 px-4 rounded-xl bg-sky-600 text-white text-xs font-bold">Save Slider Layout</button>
        </div>
      </div>

      <div class="grid grid-cols-1 xl:grid-cols-12 gap-5">
        <div class="xl:col-span-5 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h3 class="font-bold text-base mb-3">${edit ? "Edit Custom Slider" : "Add Admin Custom Slider"}</h3>
          <form onsubmit="submitCustomSlider(event)" class="space-y-3">
            <input type="hidden" id="cs_id" value="${esc(edit ? edit.id : "")}" />
            <input id="cs_title" required class="input" placeholder="Slider Title *" value="${esc(edit ? edit.title : "")}" />
            <textarea id="cs_desc" rows="2" class="input" placeholder="Description (optional)">${esc(edit ? (edit.description || "") : "")}</textarea>
            <input id="cs_img" required type="url" class="input" placeholder="Slider Image URL *" value="${esc(edit ? edit.imageUrl : "")}" />
            <input id="cs_dest" type="url" class="input" placeholder="Destination URL (https://...)" value="${esc(edit ? (edit.destinationUrl || "") : "")}" />
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-xs font-semibold mb-1">Display Order</label>
                <input id="cs_order" type="number" min="1" class="input" value="${esc(edit ? (edit.order || 1) : 1)}" />
              </div>
              <div>
                <label class="block text-xs font-semibold mb-1">Status</label>
                <select id="cs_status" class="input">
                  <option value="ACTIVE" ${!edit || edit.status === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
                  <option value="PAUSED" ${edit && edit.status === "PAUSED" ? "selected" : ""}>PAUSED</option>
                  <option value="INACTIVE" ${edit && edit.status === "INACTIVE" ? "selected" : ""}>INACTIVE</option>
                </select>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-xs font-semibold mb-1">Start Date (optional)</label>
                <input id="cs_start" type="date" class="input" value="${edit && edit.startDate ? new Date(edit.startDate).toISOString().slice(0,10) : ""}" />
              </div>
              <div>
                <label class="block text-xs font-semibold mb-1">Expiry Date (optional)</label>
                <input id="cs_end" type="date" class="input" value="${edit && edit.expiryDate ? new Date(edit.expiryDate).toISOString().slice(0,10) : ""}" />
              </div>
            </div>
            <button type="submit" class="w-full py-2.5 rounded-xl bg-teal-600 text-white text-xs font-bold">${edit ? "Update Custom Slider" : "Add Custom Slider"}</button>
            ${edit ? `<button type="button" onclick="editing={};renderTab()" class="w-full py-1.5 text-xs text-slate-500">Cancel Edit</button>` : ""}
          </form>
        </div>

        <div class="xl:col-span-7 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h3 class="font-bold text-base mb-3">Custom Sliders (${customList.length})</h3>
          ${customList.length === 0 ? `<p class="text-xs text-slate-400 py-8 text-center">No Admin custom sliders added yet.</p>` : `
            <div class="space-y-3">
              ${customList.map(s => `
                <div class="border border-slate-200 rounded-xl p-3 flex flex-wrap justify-between items-center gap-3">
                  <div class="flex items-center gap-3 min-w-0 flex-1">
                    <img src="${esc(s.imageUrl)}" class="w-20 h-12 rounded-lg object-cover border border-slate-200 flex-shrink-0" />
                    <div class="min-w-0 text-xs">
                      <div class="flex items-center gap-1.5">
                        <span class="px-1.5 py-0.5 rounded bg-slate-100 font-bold">#${s.order || 1}</span>
                        <b class="truncate text-sm">${esc(s.title)}</b>
                        ${adStatusPill(s.status || "ACTIVE")}
                      </div>
                      <div class="text-slate-500 truncate">${esc(s.description || s.destinationUrl || "No link")}</div>
                    </div>
                  </div>
                  <div class="flex gap-1.5 text-xs">
                    <button onclick="editCustomSlider('${esc(s.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                    <button onclick="toggleCustomSliderStatus('${esc(s.id)}')" class="px-2.5 py-1 rounded-lg bg-amber-50 text-amber-800 font-semibold">${s.status === "ACTIVE" ? "Pause" : "Activate"}</button>
                    <button onclick="deleteCustomSlider('${esc(s.id)}')" class="px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 font-semibold">Delete</button>
                  </div>
                </div>
              `).join("")}
            </div>
          `}
        </div>
      </div>
    </div>
  `;
}

async function saveHomeSliderSettings() {
  const payload = {
    enabled: document.getElementById("hs_enabled").checked,
    customSliderPosition: document.getElementById("hs_pos").value,
    autoSlideIntervalMs: Math.max(2000, parseInt(document.getElementById("hs_interval").value || "4500", 10))
  };
  await rtdbAdPut("homeSlider/settings", payload);
  adState.homeSliderSettings = payload;
  toast("Home Slider layout saved!");
}

async function submitCustomSlider(e) {
  e.preventDefault();
  const id = document.getElementById("cs_id").value || uid("cs");
  const old = adState.homeSliderCustom[id] || {};
  const startStr = document.getElementById("cs_start").value;
  const endStr = document.getElementById("cs_end").value;
  const payload = {
    id,
    title: document.getElementById("cs_title").value.trim(),
    description: document.getElementById("cs_desc").value.trim(),
    imageUrl: document.getElementById("cs_img").value.trim(),
    destinationUrl: document.getElementById("cs_dest").value.trim(),
    order: Math.max(1, parseInt(document.getElementById("cs_order").value || "1", 10)),
    startDate: startStr ? new Date(startStr + "T00:00:00").getTime() : 0,
    expiryDate: endStr ? new Date(endStr + "T23:59:59").getTime() : 0,
    status: document.getElementById("cs_status").value || "ACTIVE",
    createdAt: old.createdAt || Date.now()
  };
  await rtdbAdPut(`homeSlider/adminCustomSliders/${id}`, payload);
  adState.homeSliderCustom[id] = payload;
  editing = {};
  toast("Custom slider saved!");
  renderTab();
}

function editCustomSlider(id) {
  const s = adState.homeSliderCustom[id];
  if (!s) return;
  editing = { customSlider: { id, ...s } };
  renderTab();
}

async function toggleCustomSliderStatus(id) {
  const s = adState.homeSliderCustom[id];
  if (!s) return;
  const next = s.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
  const updated = { ...s, status: next };
  await rtdbAdPut(`homeSlider/adminCustomSliders/${id}`, updated);
  adState.homeSliderCustom[id] = updated;
  toast(`Custom slider ${next}`);
  renderTab();
}

async function deleteCustomSlider(id) {
  if (!confirm("Delete this custom slider?")) return;
  await rtdbAdDelete(`homeSlider/adminCustomSliders/${id}`);
  delete adState.homeSliderCustom[id];
  toast("Custom slider deleted.");
  renderTab();
}

// 9. BANNER MANAGEMENT (300x50 — earliest activated first, max 1 live)
function renderAdBanners() {
  const now = Date.now();
  const list = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => String(c.adType || "").toUpperCase() === "BOTTOM_BANNER_300x50")
    .sort((a, b) => {
      const pa = Number(a.priorityOverride || 2147483647), pb = Number(b.priorityOverride || 2147483647);
      if (pa !== pb) return pa - pb;
      return Number(a.activatedAt || a.createdAt || 0) - Number(b.activatedAt || b.createdAt || 0);
    });

  const firstActiveId = (list.find(c => c.effectiveStatus === "ACTIVE") || {}).id;

  return `
    <div class="space-y-4">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base">Bottom Banner Management (300&times;50 px)</h3>
        <p class="text-xs text-slate-500">Only 1 Bottom Banner displays at a time. Automatically selects the earliest activated eligible campaign (or lowest Priority Override) and switches seamlessly on pause/expiry.</p>
      </div>
      ${list.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No Bottom Banner 300x50 campaigns found.</div>` : list.map(c => `
        <div class="bg-white p-4 rounded-2xl border ${c.id === firstActiveId ? "border-emerald-400 ring-1 ring-emerald-200" : "border-slate-200"} flex flex-wrap justify-between items-center gap-3">
          <div class="flex items-center gap-3 text-xs">
            ${c.imageUrl ? `<img src="${esc(c.imageUrl)}" class="w-24 h-8 object-cover rounded border border-slate-200" />` : ""}
            <div>
              <div class="flex items-center gap-2">
                <b class="text-sm">${esc(c.title || c.businessName)}</b>
                ${adStatusPill(c.effectiveStatus)}
                ${c.id === firstActiveId ? `<span class="px-2 py-0.5 rounded-full bg-emerald-600 text-white text-[10px] font-bold">CURRENTLY LIVE ON APP</span>` : ""}
              </div>
              <div class="text-slate-500 mt-0.5">Activated: ${formatDateOnly(c.activatedAt)} &bull; Expires: ${formatDateOnly(c.expiryDate)} &bull; Impressions: ${c.impressionsCount || 0} &bull; Clicks: ${c.clicksCount || 0}</div>
            </div>
          </div>
          <div class="flex items-center gap-2 text-xs">
            <input type="number" min="1" class="input w-24" placeholder="Priority" value="${c.priorityOverride && c.priorityOverride < 2147483647 ? esc(c.priorityOverride) : ""}"
              onchange="setBannerPriorityOverride('${esc(c.id)}', this.value)" title="Optional Priority Override" />
            ${c.effectiveStatus === "ACTIVE" ? `<button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-lg bg-amber-100 text-amber-900 font-bold">Pause</button>` : ""}
            ${c.effectiveStatus === "PAUSED" ? `<button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-lg bg-sky-600 text-white font-bold">Resume</button>` : ""}
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

async function setBannerPriorityOverride(campId, val) {
  const c = adState.campaigns[campId];
  if (!c) return;
  const num = parseInt(val || "", 10);
  c.priorityOverride = isNaN(num) ? 2147483647 : Math.max(1, num);
  await rtdbAdPut(`advertising/campaigns/${campId}`, c);
  toast("Banner priority updated!");
  renderTab();
}

// 10. NATIVE AD MANAGEMENT (300x100 across 4 independent lists)
function renderAdNatives() {
  const now = Date.now();
  const filterPl = adUiFilter.nativePlacement || "ALL";
  const nativeList = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => String(c.adType || "").toUpperCase() === "NATIVE_300x100")
    .filter(c => filterPl === "ALL" || c.placement === filterPl);

  return `
    <div class="space-y-4">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap justify-between items-center gap-3">
        <div>
          <h3 class="font-bold text-base">Native Ad Management (300&times;100 px)</h3>
          <p class="text-xs text-slate-500">Shown after every 3 content cards on Doctor List, Hospital List, Diagnostic Center List, and Favorite List using independent rotation queues.</p>
        </div>
        <select class="input max-w-[200px]" onchange="adUiFilter.nativePlacement=this.value;renderTab()">
          <option value="ALL">All 4 Native Placements</option>
          <option value="DOCTOR_LIST" ${filterPl === "DOCTOR_LIST" ? "selected" : ""}>Doctor List</option>
          <option value="HOSPITAL_LIST" ${filterPl === "HOSPITAL_LIST" ? "selected" : ""}>Hospital List</option>
          <option value="DIAGNOSTIC_LIST" ${filterPl === "DIAGNOSTIC_LIST" ? "selected" : ""}>Diagnostic Center List</option>
          <option value="FAVORITE_LIST" ${filterPl === "FAVORITE_LIST" ? "selected" : ""}>Favorite List</option>
        </select>
      </div>
      ${nativeList.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No Native 300x100 campaigns found.</div>` : nativeList.map(c => `
        <div class="bg-white p-4 rounded-2xl border border-slate-200 flex flex-wrap justify-between items-center gap-3 text-xs">
          <div class="flex items-center gap-3">
            ${c.imageUrl ? `<img src="${esc(c.imageUrl)}" class="w-24 h-10 object-cover rounded border border-slate-200" />` : ""}
            <div>
              <div class="flex items-center gap-2">
                <b class="text-sm">${esc(c.title || c.businessName)}</b>
                ${adStatusPill(c.effectiveStatus)}
                <span class="px-2 py-0.5 rounded-full bg-sky-50 text-sky-800 font-bold">${esc(c.placement)}</span>
              </div>
              <div class="text-slate-500 mt-0.5">Impressions: ${c.impressionsCount || 0} &bull; Clicks: ${c.clicksCount || 0} &bull; Valid until ${formatDateOnly(c.expiryDate)}</div>
            </div>
          </div>
          <div class="flex gap-1.5">
            ${c.effectiveStatus === "ACTIVE" ? `<button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-lg bg-amber-100 text-amber-900 font-bold">Pause</button>` : ""}
            ${c.effectiveStatus === "PAUSED" ? `<button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-lg bg-sky-600 text-white font-bold">Resume</button>` : ""}
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

// 11. POPUP MANAGEMENT (300x200 once per session)
function renderAdPopups() {
  const now = Date.now();
  const list = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => String(c.adType || "").toUpperCase() === "APP_OPEN_POPUP_300x200");

  return `
    <div class="space-y-4">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base">App Open Popup Management (300&times;200 px)</h3>
        <p class="text-xs text-slate-500">Shown ONCE per app session on app launch with fair round-robin rotation and a visible Close button.</p>
      </div>
      ${list.length === 0 ? `<div class="bg-white p-8 rounded-2xl border border-slate-200 text-center text-xs text-slate-400">No App Open Popup 300x200 campaigns found.</div>` : list.map(c => `
        <div class="bg-white p-4 rounded-2xl border border-slate-200 flex flex-wrap justify-between items-center gap-3 text-xs">
          <div class="flex items-center gap-3">
            ${c.imageUrl ? `<img src="${esc(c.imageUrl)}" class="w-20 h-14 object-cover rounded border border-slate-200" />` : ""}
            <div>
              <div class="flex items-center gap-2"><b class="text-sm">${esc(c.title || c.businessName)}</b>${adStatusPill(c.effectiveStatus)}</div>
              <div class="text-slate-500 mt-0.5">Impressions: ${c.impressionsCount || 0} &bull; Clicks: ${c.clicksCount || 0} &bull; Expires: ${formatDateOnly(c.expiryDate)}</div>
            </div>
          </div>
          <div class="flex gap-1.5">
            ${c.effectiveStatus === "ACTIVE" ? `<button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-lg bg-amber-100 text-amber-900 font-bold">Pause</button>` : ""}
            ${c.effectiveStatus === "PAUSED" ? `<button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-lg bg-sky-600 text-white font-bold">Resume</button>` : ""}
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

// 12. ROTATION MANAGEMENT (All 7 Independent Queues)
function renderAdRotation() {
  return `
    <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
      <div>
        <h3 class="font-bold text-base">Independent Rotation Queues</h3>
        <p class="text-xs text-slate-500">Live round-robin pointers synced across devices via Firebase RTDB <code>/advertising/rotation</code>.</p>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        ${ROTATION_QUEUE_LABELS.map(q => {
          const r = adState.rotation[q.key] || {};
          return `
            <div class="border border-slate-200 rounded-xl p-4 flex justify-between items-center gap-2 text-xs">
              <div>
                <div class="font-bold text-slate-900">${esc(q.label)}</div>
                <div class="text-slate-500 mt-0.5">Queue Key: <code>${esc(q.key)}</code> &bull; Pointer Index: <b>${Number(r.index || 0)}</b></div>
                ${r.lastCampaignId ? `<div class="text-slate-400">Last Ad: <code>${esc(r.lastCampaignId)}</code></div>` : ""}
              </div>
              <button onclick="resetRotationQueue('${esc(q.key)}')" class="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-700 font-semibold hover:bg-slate-200">Reset</button>
            </div>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

async function resetRotationQueue(queueKey) {
  const payload = { index: 0, lastCampaignId: "", updatedAt: Date.now() };
  await rtdbAdPut(`advertising/rotation/${queueKey}`, payload);
  adState.rotation[queueKey] = payload;
  toast(`Rotation queue ${queueKey} reset to 0`);
  renderTab();
}

// 13. CAMPAIGN HISTORY
function renderAdHistory() {
  const now = Date.now();
  const q = adUiFilter.historyQuery.trim().toLowerCase();
  const rows = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => [c.title, c.businessName, c.advertiserName, c.packageName, c.adType, c.effectiveStatus].some(v => String(v || "").toLowerCase().includes(q)))
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  return `
    <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
      <div class="flex flex-wrap justify-between items-center gap-3">
        <h3 class="font-bold text-base">Complete Campaign History (${rows.length})</h3>
        <input class="input max-w-xs" placeholder="Search campaigns..." value="${esc(adUiFilter.historyQuery)}"
          oninput="adUiFilter.historyQuery=this.value;renderTab()" />
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-xs text-left">
          <thead>
            <tr class="bg-slate-50 text-slate-600">
              <th class="p-3">Campaign</th>
              <th class="p-3">Type / Placement</th>
              <th class="p-3">Amount</th>
              <th class="p-3">Payment / Approval / Status</th>
              <th class="p-3">Start &ndash; Expiry</th>
            </tr>
          </thead>
          <tbody>
            ${rows.map(c => `
              <tr class="border-t border-slate-100">
                <td class="p-3"><b>${esc(c.title || c.targetRecordTitle || "Untitled")}</b><div class="text-slate-500">${esc(c.businessName)}</div></td>
                <td class="p-3">${esc(c.adType)}<div class="text-slate-400">${esc(c.placement)}</div></td>
                <td class="p-3 font-bold">৳${Number(c.price || 0).toLocaleString()}</td>
                <td class="p-3 space-x-1">${adStatusPill(c.paymentStatus)} ${adStatusPill(c.approvalStatus)} ${adStatusPill(c.effectiveStatus)}</td>
                <td class="p-3 whitespace-nowrap">${formatDateOnly(c.startDate)} &ndash; ${formatDateOnly(c.expiryDate)}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

// 14. REPORTS & CSV EXPORT
function renderAdReports() {
  const campaigns = Object.entries(adState.campaigns || {}).map(([id, c]) => ({ id, ...c }));
  const totalImp = campaigns.reduce((a, c) => a + Number(c.impressionsCount || 0), 0);
  const totalClk = campaigns.reduce((a, c) => a + Number(c.clicksCount || 0), 0);
  const ctr = totalImp > 0 ? ((totalClk * 100) / totalImp).toFixed(2) : "0.00";
  const revenue = Object.values(adState.payments || {})
    .filter(p => String(p.status || "").toUpperCase() === "VERIFIED")
    .reduce((a, p) => a + Number(p.amount || 0), 0);

  return `
    <div class="space-y-4">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap justify-between items-center gap-3">
        <div>
          <h3 class="font-bold text-base">Advertisement Analytics &amp; Revenue Report</h3>
          <p class="text-xs text-slate-500">Live Impressions, Clicks, CTR, and Verified Revenue breakdown.</p>
        </div>
        <button onclick="exportAdReportsCsv()" class="px-4 py-2 rounded-xl bg-teal-600 text-white text-xs font-bold">Export Ad Report CSV</button>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div class="bg-white p-4 rounded-2xl border border-slate-200"><div class="text-xs text-slate-500">Total Impressions</div><div class="text-2xl font-extrabold text-sky-800">${totalImp.toLocaleString()}</div></div>
        <div class="bg-white p-4 rounded-2xl border border-slate-200"><div class="text-xs text-slate-500">Total Clicks</div><div class="text-2xl font-extrabold text-indigo-800">${totalClk.toLocaleString()}</div></div>
        <div class="bg-white p-4 rounded-2xl border border-slate-200"><div class="text-xs text-slate-500">Average CTR</div><div class="text-2xl font-extrabold text-emerald-700">${ctr}%</div></div>
        <div class="bg-white p-4 rounded-2xl border border-slate-200"><div class="text-xs text-slate-500">Verified Revenue</div><div class="text-2xl font-extrabold text-teal-700">৳${revenue.toLocaleString()}</div></div>
      </div>

      <div class="bg-white p-5 rounded-2xl border border-slate-200 overflow-x-auto">
        <table class="w-full text-xs text-left">
          <thead><tr class="bg-slate-50 text-slate-600"><th class="p-3">Campaign</th><th class="p-3">Type</th><th class="p-3">Impressions</th><th class="p-3">Clicks</th><th class="p-3">CTR</th></tr></thead>
          <tbody>
            ${campaigns.map(c => {
              const imp = Number(c.impressionsCount || 0);
              const clk = Number(c.clicksCount || 0);
              const cCtr = imp > 0 ? ((clk * 100) / imp).toFixed(2) : "0.00";
              return `<tr class="border-t border-slate-100"><td class="p-3 font-bold">${esc(c.title || c.targetRecordTitle || c.id)}</td><td class="p-3">${esc(c.adType)}</td><td class="p-3">${imp}</td><td class="p-3">${clk}</td><td class="p-3 font-semibold">${cCtr}%</td></tr>`;
            }).join("")}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function exportAdReportsCsv() {
  const rows = Object.entries(adState.campaigns || {}).map(([id, c]) => ({ id, ...c }));
  if (!rows.length) return toast("No campaign report data to export.");
  const headers = ["id", "title", "businessName", "adType", "placement", "price", "paymentStatus", "approvalStatus", "campaignStatus", "impressionsCount", "clicksCount", "startDate", "expiryDate"];
  const csv = [
    headers.join(","),
    ...rows.map(r => headers.map(h => `"${String(r[h] ?? "").replace(/"/g, '""')}"`).join(","))
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "doctor_list_bd_advertising_report.csv";
  a.click();
}

// 15. ADVERTISEMENT SETTINGS
function renderAdSettings() {
  const s = adState.placements || {};
  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm max-w-2xl space-y-4">
      <div>
        <h3 class="font-bold text-base">Global Advertisement Placement Settings</h3>
        <p class="text-xs text-slate-500">Dynamically enable or disable each advertisement placement across the Android application.</p>
      </div>
      <div class="space-y-2.5 text-sm">
        ${[
          ["adsEnabled", "Master Advertisement Switch (All Ads Enabled)"],
          ["homeSliderEnabled", "Top Home Slider Carousel Enabled"],
          ["doctorNativeEnabled", "Doctor List Native Ads (300x100 after every 3 cards)"],
          ["hospitalNativeEnabled", "Hospital List Native Ads (300x100 after every 3 cards)"],
          ["diagnosticNativeEnabled", "Diagnostic List Native Ads (300x100 after every 3 cards)"],
          ["favoriteNativeEnabled", "Favorite List Native Ads (300x100 after every 3 cards)"],
          ["bottomBannerEnabled", "Fixed Bottom Banner Ads (300x50, max 1)"],
          ["appOpenPopupEnabled", "App Open Popup Ads (300x200 on launch)"],
          ["popupOncePerSession", "Show App Open Popup strictly once per session"]
        ].map(([k, label]) => `
          <label class="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer">
            <span class="text-xs font-semibold text-slate-800">${esc(label)}</span>
            <input type="checkbox" id="adset_${k}" ${s[k] !== false ? "checked" : ""} />
          </label>
        `).join("")}
      </div>
      <div class="flex flex-wrap gap-3 pt-2">
        <button onclick="saveGlobalAdPlacementSettings()" class="px-5 py-2.5 rounded-xl bg-sky-600 text-white text-xs font-bold">Save Placement Settings</button>
        <button onclick="sweepAndRefreshAds()" class="px-4 py-2.5 rounded-xl bg-slate-100 text-slate-800 text-xs font-bold">Run Expiry Sweep Now</button>
      </div>
    </div>
  `;
}

async function saveGlobalAdPlacementSettings() {
  const payload = {
    adsEnabled: document.getElementById("adset_adsEnabled").checked,
    homeSliderEnabled: document.getElementById("adset_homeSliderEnabled").checked,
    doctorNativeEnabled: document.getElementById("adset_doctorNativeEnabled").checked,
    hospitalNativeEnabled: document.getElementById("adset_hospitalNativeEnabled").checked,
    diagnosticNativeEnabled: document.getElementById("adset_diagnosticNativeEnabled").checked,
    favoriteNativeEnabled: document.getElementById("adset_favoriteNativeEnabled").checked,
    bottomBannerEnabled: document.getElementById("adset_bottomBannerEnabled").checked,
    appOpenPopupEnabled: document.getElementById("adset_appOpenPopupEnabled").checked,
    popupOncePerSession: document.getElementById("adset_popupOncePerSession").checked,
    nativeInterval: 3,
    updatedAt: Date.now()
  };
  await rtdbAdPut("advertising/placements", payload);
  adState.placements = payload;
  await writeAuditLog("UPDATE_AD_SETTINGS", "AD_SETTINGS", "placements", "Updated global ad placement toggles");
  toast("Advertisement settings saved!");
}
