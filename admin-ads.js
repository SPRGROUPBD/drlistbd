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
  { key: "ALL_NATIVE_LISTS", label: "All Native Lists (Doctors, Hospitals, Diagnostics & Home Popular Doctors)", forType: "NATIVE_300x100" },
  { key: "DOCTOR_LIST", label: "Doctor List & Home Popular Doctors (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "HOSPITAL_LIST", label: "Hospital List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "DIAGNOSTIC_LIST", label: "Diagnostic Center List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "FAVORITE_LIST", label: "Favorite List (After every 3 cards)", forType: "NATIVE_300x100" },
  { key: "BOTTOM_ALL_SCREENS", label: "Fixed Bottom Banner (10s Auto Rotation)", forType: "BOTTOM_BANNER_300x50" },
  { key: "APP_LAUNCH", label: "App Launch Popup (Once Per Session)", forType: "APP_OPEN_POPUP_300x200" }
];

const ADMOB_OFFICIAL_TEST_IDS = {
  appId: "ca-app-pub-3940256099942544~3347511713",
  bannerUnitId: "ca-app-pub-3940256099942544/6300978111",
  nativeUnitId: "ca-app-pub-3940256099942544/2247696110"
};

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
  bottomBanners: {},
  admob: {
    enabled: true,
    testMode: true,
    appId: ADMOB_OFFICIAL_TEST_IDS.appId,
    bannerEnabled: true,
    bannerUnitId: ADMOB_OFFICIAL_TEST_IDS.bannerUnitId,
    nativeEnabled: true,
    nativeUnitId: ADMOB_OFFICIAL_TEST_IDS.nativeUnitId
  },
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
    adState.bottomBanners = a.bottomBanners && typeof a.bottomBanners === "object" ? a.bottomBanners : {};
    adState.admob = {
      ...adState.admob,
      ...(a.admob && typeof a.admob === "object" ? a.admob : {})
    };
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

function handleAdImageFileUpload(fileInput, targetInputId, onReadyCallback, maxWidth = 960) {
  const file = fileInput && fileInput.files && fileInput.files[0];
  if (!file) return;
  const allowed = ["image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"];
  if (!allowed.includes((file.type || "").toLowerCase())) {
    fileInput.value = "";
    return alert("Invalid image format! Please upload PNG, JPG, WEBP, or GIF.");
  }
  if (file.size > 5 * 1024 * 1024) {
    fileInput.value = "";
    return alert("Image file is too large (max 5MB allowed).");
  }
  const reader = new FileReader();
  reader.onload = () => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxWidth / Math.max(img.width || 1, img.height || 1));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      const ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL("image/jpeg", 0.82);
      const target = document.getElementById(targetInputId);
      if (target) target.value = dataUrl;
      if (typeof onReadyCallback === "function") onReadyCallback(dataUrl);
      toast("Image uploaded & optimized for fast app loading!");
    };
    img.onerror = () => alert("Could not read image file. Try another PNG/JPG image.");
    img.src = reader.result;
  };
  reader.readAsDataURL(file);
}

function getEffectiveBottomBannerStatus(b, nowMs = Date.now()) {
  if (!b) return "INACTIVE";
  const st = String(b.status || "ACTIVE").toUpperCase();
  if (st !== "ACTIVE") return "INACTIVE";
  const start = Number(b.startDate || 0);
  const exp = Number(b.expiryDate || 0);
  if (exp > 0 && nowMs > exp) return "EXPIRED";
  if (start > 0 && nowMs < start) return "SCHEDULED";
  return "ACTIVE";
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
    ad_custom_slider: "Custom Slider Management",
    ad_bottom_banners: "Bottom Banner Advertisement",
    ad_rotation: "Rotation Management",
    ad_history: "Campaign History",
    ad_reports: "Advertisement Reports & CSV",
    ad_settings: "Advertisement & AdMob Settings"
  };
  return map[tabKey] || null;
}

function updateAdNavBadges() {
  const now = Date.now();
  const campaigns = Object.values(adState.campaigns || {});
  const activeCount = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "ACTIVE").length;
  const pendingCampCount = campaigns.filter(c => getEffectiveCampaignStatus(c, now) === "PENDING").length;
  const pendingPayCount = Object.values(adState.payments || {}).filter(p => String(p.status || "PENDING").toUpperCase() === "PENDING").length;
  const activeBottomBanners = Object.values(adState.bottomBanners || {}).filter(b => getEffectiveBottomBannerStatus(b, now) === "ACTIVE").length;

  const setB = (id, count) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = String(count);
    el.classList.toggle("hidden", count <= 0);
  };
  setB("navBadgeActiveAds", activeCount);
  setB("navBadgePendingAds", pendingCampCount);
  setB("navBadgePayments", pendingPayCount);
  setB("navBadgeBottomBanners", activeBottomBanners);
}

function renderAdvertisingTab(tabKey) {
  switch (tabKey) {
    case "ad_dashboard": return renderAdDashboard();
    case "ad_packages": return renderAdPackages();
    case "ad_advertisers": return renderAdAdvertisers();
    case "ad_requests": return renderAdRequests();
    case "ad_payments": return renderAdPayments();
    case "ad_payment_methods": return renderAdPaymentMethods();
    case "ad_custom_slider": return renderAdCustomSlider();
    case "ad_bottom_banners": return renderAdBottomBanners();
    case "ad_rotation": return renderAdRotation();
    case "ad_history": return renderAdHistory();
    case "ad_reports": return renderAdReports();
    case "ad_settings": return renderAdSettings();
    case "ad_home_slider":
    case "ad_banners":
    case "ad_natives":
    case "ad_popups":
      return renderAdRequests();
    default: return null;
  }
}

function resolveSingleTargetRef(recordType, recordId, fallbackTitle = "") {
  const rType = String(recordType || "").toLowerCase();
  const rId = String(recordId || "").trim();
  if (!rType || !rId) return { exists: false, type: rType || "Record", recordId: rId, label: fallbackTitle || "Missing linked record ID" };
  if (rType === "doctor") {
    const doc = (dbState.doctors || {})[rId];
    return doc
      ? { exists: true, type: "Doctor", recordType: "doctor", recordId: rId, label: doc.fullNameEn || doc.fullNameBn || doc.doctorName || rId, photo: doc.photoUrl || "" }
      : { exists: false, type: "Doctor", recordType: "doctor", recordId: rId, label: `${fallbackTitle || rId} (Not found in Doctors)` };
  }
  if (rType === "hospital") {
    const h = (dbState.hospitals || {})[rId];
    return h
      ? { exists: true, type: "Hospital", recordType: "hospital", recordId: rId, label: h.nameEn || h.nameBn || rId, photo: h.imageUrl || "" }
      : { exists: false, type: "Hospital", recordType: "hospital", recordId: rId, label: `${fallbackTitle || rId} (Not found in Hospitals)` };
  }
  if (rType === "diagnostic") {
    const d = (dbState.diagnostic_centers || {})[rId];
    return d
      ? { exists: true, type: "Diagnostic", recordType: "diagnostic", recordId: rId, label: d.nameEn || d.nameBn || rId, photo: d.imageUrl || "" }
      : { exists: false, type: "Diagnostic", recordType: "diagnostic", recordId: rId, label: `${fallbackTitle || rId} (Not found in Diagnostics)` };
  }
  return { exists: false, type: rType, recordType: rType, recordId: rId, label: fallbackTitle || `Unknown record type: ${rType}` };
}

function resolveCampaignPromotedTargets(c) {
  if (!c || String(c.adType || "").toUpperCase() !== "HOME_SLIDER") return [];
  const rawList = Array.isArray(c.promotedTargets)
    ? c.promotedTargets
    : (c.promotedTargets && typeof c.promotedTargets === "object" ? Object.values(c.promotedTargets) : []);
  const results = [];
  const seen = new Set();
  for (const item of rawList) {
    if (!item || !item.recordId) continue;
    const key = `${String(item.recordType || "").toLowerCase()}_${item.recordId}`;
    if (seen.has(key)) continue;
    seen.add(key);
    results.push(resolveSingleTargetRef(item.recordType, item.recordId, item.title || ""));
  }
  if (results.length === 0 && c.targetRecordType && c.targetRecordId) {
    results.push(resolveSingleTargetRef(c.targetRecordType, c.targetRecordId, c.targetRecordTitle || ""));
  }
  return results;
}

function resolveLinkedRecordSummary(c) {
  if (!c || String(c.adType || "").toUpperCase() !== "HOME_SLIDER") return null;
  const targets = resolveCampaignPromotedTargets(c);
  if (targets.length === 0) return { exists: false, label: "Missing linked record ID", targets: [] };
  const validCount = targets.filter(t => t.exists).length;
  return {
    exists: validCount > 0,
    type: targets.length > 1 ? `${targets.length} Targets` : targets[0].type,
    label: targets.map(t => `${t.type}: ${t.label}`).join(" • "),
    targets
  };
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
            <button onclick="switchTab('ad_bottom_banners')" class="p-3 rounded-xl bg-emerald-50 text-emerald-900 text-xs font-bold text-left hover:bg-emerald-100">+ Bottom Banner Ad (10s)</button>
            <button onclick="switchTab('ad_custom_slider')" class="p-3 rounded-xl bg-indigo-50 text-indigo-900 text-xs font-bold text-left hover:bg-indigo-100">+ Add Custom Home Slider</button>
            <button onclick="switchTab('ad_settings')" class="p-3 rounded-xl bg-amber-50 text-amber-900 text-xs font-bold text-left hover:bg-amber-100">AdMob Banner &amp; Native IDs</button>
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
            <label class="block text-xs font-semibold mb-1">Package Status *</label>
            <select id="pkg_status" class="input">
              <option value="ACTIVE" ${!edit || edit.status === "ACTIVE" ? "selected" : ""}>ACTIVE (Visible &amp; Selectable in App)</option>
              <option value="INACTIVE" ${edit && edit.status !== "ACTIVE" ? "selected" : ""}>INACTIVE (Hidden from New Bookings)</option>
            </select>
          </div>
          <button type="submit" class="w-full py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">${edit ? "Update Package" : "Save Package"}</button>
          ${edit ? `<button type="button" onclick="editing={};renderTab()" class="w-full py-1.5 text-xs text-slate-500">Cancel Edit</button>` : ""}
        </form>
      </div>

      <div class="xl:col-span-7 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">All Advertising Packages (${pkgList.length})</h3>
        <p class="text-xs text-slate-500 mb-3">Existing advertiser bookings remain preserved automatically if a package is deactivated or deleted.</p>
        ${pkgList.length === 0 ? `<p class="text-xs text-slate-400 py-8 text-center">No packages created yet. Create unlimited packages for Home Slider, Native 300x100, and App Open Popup 300x200.</p>` : `
          <div class="space-y-3">
            ${pkgList.map(p => `
              <div class="border border-slate-200 rounded-xl p-3.5 flex flex-wrap justify-between gap-3">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm text-slate-900">${esc(p.name)}</b>
                    ${adStatusPill(p.status)}
                  </div>
                  <div class="text-xs text-sky-800 font-semibold mt-1">${esc(p.adType)} &bull; ${esc(p.placement)}</div>
                  <div class="text-xs text-slate-600 mt-0.5">Price: <b>৳${Number(p.price || 0).toLocaleString()}</b> &bull; Duration: <b>${p.durationDays} days</b> (Max ${p.maxCampaignDurationDays || p.durationDays}d)</div>
                  ${p.description ? `<p class="text-[11px] text-slate-500 mt-1">${esc(p.description)}</p>` : ""}
                </div>
                <div class="flex flex-col items-end gap-1.5 text-xs">
                  <div class="flex gap-1.5">
                    <button onclick="editAdPackage('${esc(p.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                    <button onclick="cyclePackageStatus('${esc(p.id)}')" class="px-2.5 py-1 rounded-lg ${p.status === "ACTIVE" ? "bg-amber-50 text-amber-800" : "bg-emerald-50 text-emerald-700"} font-semibold">${p.status === "ACTIVE" ? "Deactivate" : "Activate"}</button>
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
  const name = document.getElementById("pkg_name").value.trim();
  const adType = document.getElementById("pkg_type").value;
  const placement = document.getElementById("pkg_placement").value;
  const price = Number(document.getElementById("pkg_price").value || 0);
  const durationDays = parseInt(document.getElementById("pkg_days").value || "0", 10);
  const maxCampaignDurationDays = Math.max(durationDays || 1, parseInt(document.getElementById("pkg_max_days").value || String(durationDays || 30), 10));

  if (name.length < 2) return alert("Package Name is required.");
  if (!adType) return alert("Advertisement Type is required.");
  if (!placement) return alert("Advertisement Placement is required.");
  if (isNaN(price) || price < 0) return alert("Enter a valid package price (0 or more).");
  if (isNaN(durationDays) || durationDays < 1) return alert("Package Duration must be at least 1 day.");

  const now = Date.now();
  const payload = {
    id,
    name,
    description: document.getElementById("pkg_desc").value.trim(),
    adType,
    placement,
    price,
    durationDays,
    maxCampaignDurationDays,
    imageUrl: document.getElementById("pkg_img").value.trim(),
    status: document.getElementById("pkg_status").value === "INACTIVE" ? "INACTIVE" : "ACTIVE",
    createdAt: old.createdAt || now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/packages/${id}`, payload);
  adState.packages[id] = payload;
  editing = {};
  await writeAuditLog("SAVE_AD_PACKAGE", "AD_PACKAGE", id, `Saved package ${payload.name} (৳${payload.price}, ${payload.status})`);
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
  const next = p.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  const updated = { ...p, status: next, updatedAt: Date.now() };
  await rtdbAdPut(`advertising/packages/${id}`, updated);
  adState.packages[id] = updated;
  await writeAuditLog("TOGGLE_AD_PACKAGE", "AD_PACKAGE", id, `Set package ${p.name} to ${next}`);
  toast(`Package marked ${next}`);
  renderTab();
}

async function deleteAdPackage(id) {
  if (!confirm("Delete this advertising package? Existing advertiser bookings will remain preserved.")) return;
  await rtdbAdDelete(`advertising/packages/${id}`);
  delete adState.packages[id];
  await writeAuditLog("DELETE_AD_PACKAGE", "AD_PACKAGE", id, `Deleted package ${id}`);
  toast("Package deleted.");
  renderTab();
}

// 3. ADVERTISER MANAGEMENT (Full Add / Edit / Delete + Saved Institution Dropdown)
function getSavedInstitutionsList() {
  const list = [];
  for (const [id, h] of Object.entries(dbState.hospitals || {})) {
    if (!h) continue;
    list.push({
      id,
      type: "hospital",
      nameEn: h.nameEn || h.nameBn || id,
      nameBn: h.nameBn || h.nameEn || id,
      address: [h.address, h.upazila, h.district].filter(Boolean).join(", "),
      category: "Hospital"
    });
  }
  for (const [id, d] of Object.entries(dbState.diagnostic_centers || {})) {
    if (!d) continue;
    list.push({
      id,
      type: "diagnostic",
      nameEn: d.nameEn || d.nameBn || id,
      nameBn: d.nameBn || d.nameEn || id,
      address: [d.address, d.upazila, d.district].filter(Boolean).join(", "),
      category: "Diagnostic Center"
    });
  }
  return list.sort((a, b) => a.nameEn.localeCompare(b.nameEn));
}

function renderAdAdvertisers() {
  const q = adUiFilter.advertiserQuery.trim().toLowerCase();
  const allAdv = Object.entries(adState.advertisers || {}).map(([id, a]) => ({ id, ...a }));
  const list = allAdv
    .filter(a => [a.advertiserName, a.businessName, a.mobile, a.email, a.category].some(v => String(v || "").toLowerCase().includes(q)))
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  const campList = Object.values(adState.campaigns || {});
  const edit = editing.adAdvertiser || null;
  const savedInstitutions = getSavedInstitutionsList();
  const claimedIds = new Set(allAdv.filter(a => (!edit || a.id !== edit.id) && a.institutionId).map(a => String(a.institutionId)));
  const claimedNames = new Set(allAdv.filter(a => (!edit || a.id !== edit.id) && a.businessName).map(a => String(a.businessName).trim().toLowerCase()));

  return `
    <div class="space-y-5">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">${edit ? "Edit Advertiser Account" : "Add Advertiser Account"}</h3>
        <p class="text-xs text-slate-500 mb-3">Select a saved Hospital or Diagnostic Center from the dropdown so two advertisers cannot register the same institution.</p>
        <form onsubmit="submitAdAdvertiser(event)" class="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input type="hidden" id="adv_id" value="${esc(edit ? edit.id : "")}" />
          <div class="md:col-span-2">
            <label class="block text-xs font-semibold mb-1">Saved Institution Dropdown (হাসপাতাল / ডায়াগনস্টিক)</label>
            <select id="adv_inst_select" class="input" onchange="onAdminAdvInstitutionSelected(this.value)">
              <option value="">-- Custom / Other Institution --</option>
              ${savedInstitutions.map(inst => {
                const isTaken = claimedIds.has(inst.id) || claimedNames.has(inst.nameEn.trim().toLowerCase()) || claimedNames.has(inst.nameBn.trim().toLowerCase());
                const selected = edit && edit.institutionId === inst.id;
                return `<option value="${esc(inst.type)}::${esc(inst.id)}" ${selected ? "selected" : ""} ${isTaken && !selected ? "disabled" : ""}>${esc(inst.nameEn)} (${inst.category})${isTaken && !selected ? " — Already Registered" : ""}</option>`;
              }).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Category</label>
            <input id="adv_cat" class="input" placeholder="Hospital / Diagnostic / Clinic" value="${esc(edit ? (edit.category || "Healthcare") : "Healthcare")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Business / Institution Name *</label>
            <input id="adv_biz" required class="input" placeholder="Institution / Business Name *" value="${esc(edit ? (edit.businessName || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Owner / Contact Name *</label>
            <input id="adv_name" required class="input" placeholder="Advertiser Full Name *" value="${esc(edit ? (edit.advertiserName || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Mobile Number *</label>
            <input id="adv_mobile" required class="input" placeholder="017XXXXXXXX *" value="${esc(edit ? (edit.mobile || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Email (optional)</label>
            <input id="adv_email" type="email" class="input" placeholder="email@example.com" value="${esc(edit ? (edit.email || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Password ${edit ? "(leave blank to keep)" : "*"}</label>
            <input id="adv_pass" type="password" ${edit ? "" : "required"} minlength="6" class="input" placeholder="${edit ? "New password (optional)" : "Min 6 characters *"}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Status</label>
            <select id="adv_status" class="input">
              <option value="ACTIVE" ${!edit || edit.status === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
              <option value="SUSPENDED" ${edit && edit.status === "SUSPENDED" ? "selected" : ""}>SUSPENDED</option>
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="block text-xs font-semibold mb-1">Address</label>
            <input id="adv_addr" class="input" placeholder="Full Address" value="${esc(edit ? (edit.address || "") : "")}" />
          </div>
          <div class="flex items-end gap-2">
            <button type="submit" class="flex-1 py-2.5 px-4 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">${edit ? "Update Advertiser" : "Save Advertiser"}</button>
            ${edit ? `<button type="button" onclick="editing={};renderTab()" class="px-3 py-2.5 rounded-xl bg-slate-100 text-xs font-semibold">Cancel</button>` : ""}
          </div>
        </form>
      </div>

      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <div class="flex flex-wrap justify-between items-center gap-3">
          <div>
            <h3 class="font-bold text-base">Registered Advertisers (${list.length})</h3>
            <p class="text-xs text-slate-500">Add, edit, suspend, or delete advertiser accounts.</p>
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
                        ${a.institutionId ? `<div class="text-[10px] text-teal-700 font-semibold">Linked ${esc(a.institutionType || "institution")}: ${esc(a.institutionId)}</div>` : ""}
                      </td>
                      <td class="p-3">
                        <div>${esc(a.mobile || "—")}</div>
                        <div class="text-slate-500">${esc(a.email || "—")}</div>
                      </td>
                      <td class="p-3">${esc(a.category || "Healthcare")}</td>
                      <td class="p-3 font-bold">${count}</td>
                      <td class="p-3">${adStatusPill(a.status || "ACTIVE")}</td>
                      <td class="p-3 whitespace-nowrap space-x-1">
                        <button onclick="editAdAdvertiser('${esc(a.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                        <button onclick="toggleAdvertiserAccountStatus('${esc(a.id)}')" class="px-2.5 py-1 rounded-lg ${isSusp ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"} font-semibold">
                          ${isSusp ? "Activate" : "Suspend"}
                        </button>
                        <button onclick="deleteAdAdvertiser('${esc(a.id)}')" class="px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 font-semibold">Delete</button>
                      </td>
                    </tr>
                  `;
                }).join("")}
              </tbody>
            </table>
          </div>
        `}
      </div>
    </div>
  `;
}

function onAdminAdvInstitutionSelected(val) {
  if (!val) return;
  const [type, instId] = val.split("::");
  const inst = getSavedInstitutionsList().find(i => i.id === instId && i.type === type);
  if (!inst) return;
  const bizEl = document.getElementById("adv_biz");
  const catEl = document.getElementById("adv_cat");
  const addrEl = document.getElementById("adv_addr");
  if (bizEl) bizEl.value = inst.nameEn;
  if (catEl) catEl.value = inst.category;
  if (addrEl && !addrEl.value.trim()) addrEl.value = inst.address;
}

async function submitAdAdvertiser(e) {
  e.preventDefault();
  const id = (document.getElementById("adv_id").value || "").trim() || uid("adv");
  const old = adState.advertisers[id] || {};
  const instVal = document.getElementById("adv_inst_select").value || "";
  const [institutionType, institutionId] = instVal ? instVal.split("::") : ["", ""];
  const businessName = document.getElementById("adv_biz").value.trim();
  const advertiserName = document.getElementById("adv_name").value.trim();
  const mobile = document.getElementById("adv_mobile").value.trim();
  const email = document.getElementById("adv_email").value.trim().toLowerCase();
  const pass = document.getElementById("adv_pass").value;

  // Duplicate institution guard
  for (const [otherId, other] of Object.entries(adState.advertisers || {})) {
    if (otherId === id || !other) continue;
    if (institutionId && other.institutionId === institutionId) {
      return alert("This saved institution is already registered by another advertiser.");
    }
    if (businessName && other.businessName && other.businessName.trim().toLowerCase() === businessName.toLowerCase()) {
      return alert("An advertiser with this institution/business name already exists.");
    }
  }

  let salt = old.passwordSalt || "";
  let passwordHash = old.passwordHash || "";
  if (pass) {
    salt = generateSaltHex(16);
    passwordHash = await hashCredential(pass, salt);
  }
  const now = Date.now();
  const payload = {
    ...old,
    id,
    advertiserName,
    businessName,
    institutionId: institutionId || old.institutionId || "",
    institutionType: institutionType || old.institutionType || "",
    mobile,
    email,
    address: document.getElementById("adv_addr").value.trim(),
    category: document.getElementById("adv_cat").value.trim() || "Healthcare",
    status: document.getElementById("adv_status").value || "ACTIVE",
    passwordSalt: salt,
    passwordHash,
    createdAt: old.createdAt || now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/advertisers/${id}`, payload);
  adState.advertisers[id] = payload;
  editing = {};
  await writeAuditLog("SAVE_ADVERTISER", "ADVERTISER", id, `Saved advertiser ${businessName}`);
  toast("Advertiser saved!");
  renderTab();
}

function editAdAdvertiser(id) {
  const a = adState.advertisers[id];
  if (!a) return;
  editing = { adAdvertiser: { id, ...a } };
  renderTab();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function deleteAdAdvertiser(id) {
  if (!confirm("Delete this advertiser account?")) return;
  await rtdbAdDelete(`advertising/advertisers/${id}`);
  delete adState.advertisers[id];
  await writeAuditLog("DELETE_ADVERTISER", "ADVERTISER", id, `Deleted advertiser ${id}`);
  toast("Advertiser deleted.");
  renderTab();
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

// 4. ADVERTISEMENT REQUESTS & CAMPAIGNS (Full Add / Edit / Delete + Multi-Target Home Slider)
function renderAdRequests() {
  const now = Date.now();
  const filter = adUiFilter.requestStatus || "ALL";
  const list = Object.entries(adState.campaigns || {})
    .map(([id, c]) => ({ id, ...c, effectiveStatus: getEffectiveCampaignStatus(c, now) }))
    .filter(c => filter === "ALL" || c.effectiveStatus === filter || c.approvalStatus === filter)
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));

  const edit = editing.adCampaign || null;
  const advertisers = Object.entries(adState.advertisers || {}).map(([id, a]) => ({ id, ...a }));
  const packages = Object.entries(adState.packages || {}).map(([id, p]) => ({ id, ...p }));
  const editTargets = edit ? resolveCampaignPromotedTargets(edit) : [];
  const selectedTargetKeys = new Set(editTargets.map(t => `${t.recordType}::${t.recordId}`));

  const allPromoOptions = [
    ...Object.entries(dbState.hospitals || {}).map(([id, h]) => ({ key: `hospital::${id}`, label: `[Hospital] ${h.nameEn || h.nameBn || id}` })),
    ...Object.entries(dbState.diagnostic_centers || {}).map(([id, d]) => ({ key: `diagnostic::${id}`, label: `[Diagnostic] ${d.nameEn || d.nameBn || id}` })),
    ...Object.entries(dbState.doctors || {}).map(([id, d]) => ({ key: `doctor::${id}`, label: `[Doctor] ${d.fullNameEn || d.fullNameBn || d.doctorName || id}` }))
  ];

  return `
    <div class="space-y-5">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">${edit ? "Edit Advertisement Campaign" : "Add Advertisement Campaign"}</h3>
        <p class="text-xs text-slate-500 mb-3">Create or edit any ad campaign (Home Slider multi-target, Native 300x100, Bottom Banner 300x50, or App Open Popup 300x200).</p>
        <form onsubmit="submitAdCampaign(event)" class="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input type="hidden" id="camp_id" value="${esc(edit ? edit.id : "")}" />
          <div>
            <label class="block text-xs font-semibold mb-1">Advertiser</label>
            <select id="camp_adv" class="input" onchange="onCampaignAdvChange(this.value)">
              <option value="">-- Admin / Direct --</option>
              ${advertisers.map(a => `<option value="${esc(a.id)}" ${edit && edit.advertiserId === a.id ? "selected" : ""}>${esc(a.businessName || a.advertiserName)} (${esc(a.mobile || "")})</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Package</label>
            <select id="camp_pkg" class="input" onchange="onCampaignPkgChange(this.value)">
              <option value="">-- Select Package (optional) --</option>
              ${packages.map(p => `<option value="${esc(p.id)}" ${edit && edit.packageId === p.id ? "selected" : ""}>${esc(p.name)} (${esc(p.adType)} - ৳${p.price})</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Campaign Title *</label>
            <input id="camp_title" required class="input" placeholder="Ad Headline / Title *" value="${esc(edit ? (edit.title || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Business Name</label>
            <input id="camp_biz" class="input" placeholder="Business / Hospital Name" value="${esc(edit ? (edit.businessName || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Advertisement Type *</label>
            <select id="camp_type" class="input" onchange="syncCampaignDefaultPlacement(this.value)">
              ${AD_PACKAGE_TYPES.map(t => `<option value="${t.key}" ${(edit ? edit.adType : "BOTTOM_BANNER_300x50") === t.key ? "selected" : ""}>${esc(t.label)}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Placement *</label>
            <select id="camp_placement" class="input">
              ${AD_PLACEMENTS.map(p => `<option value="${p.key}" ${(edit ? edit.placement : "BOTTOM_ALL_SCREENS") === p.key ? "selected" : ""}>${esc(p.label)}</option>`).join("")}
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="block text-xs font-semibold mb-1">Creative Image URL (or Base64 data URI)</label>
            <input id="camp_img" class="input" placeholder="https://... or data:image/..." value="${esc(edit ? (edit.imageUrl || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Destination URL / Call Link</label>
            <input id="camp_dest" class="input" placeholder="https://... or tel:017..." value="${esc(edit ? (edit.destinationUrl || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Price (৳)</label>
            <input id="camp_price" type="number" min="0" class="input" value="${esc(edit ? (edit.price || 0) : 0)}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Duration (Days)</label>
            <input id="camp_days" type="number" min="1" class="input" value="${esc(edit ? (edit.durationDays || 30) : 30)}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Payment / Approval / Status</label>
            <div class="grid grid-cols-3 gap-1">
              <select id="camp_pay_st" class="input text-[11px] px-1.5">
                ${["VERIFIED", "PENDING", "REJECTED"].map(st => `<option value="${st}" ${(edit ? edit.paymentStatus : "VERIFIED") === st ? "selected" : ""}>Pay: ${st}</option>`).join("")}
              </select>
              <select id="camp_app_st" class="input text-[11px] px-1.5">
                ${["APPROVED", "PENDING", "REJECTED"].map(st => `<option value="${st}" ${(edit ? edit.approvalStatus : "APPROVED") === st ? "selected" : ""}>Appr: ${st}</option>`).join("")}
              </select>
              <select id="camp_st" class="input text-[11px] px-1.5">
                ${["ACTIVE", "PENDING", "PAUSED", "EXPIRED", "REJECTED"].map(st => `<option value="${st}" ${(edit ? edit.campaignStatus : "ACTIVE") === st ? "selected" : ""}>${st}</option>`).join("")}
              </select>
            </div>
          </div>
          <div class="md:col-span-3">
            <label class="block text-xs font-semibold mb-1">Home Slider Promoted Targets (Ctrl/Cmd+Click to multi-select Institution &amp; Doctors)</label>
            <select id="camp_targets" multiple size="4" class="input">
              ${allPromoOptions.map(o => `<option value="${esc(o.key)}" ${selectedTargetKeys.has(o.key) ? "selected" : ""}>${esc(o.label)}</option>`).join("")}
            </select>
          </div>
          <div class="md:col-span-3 flex gap-2">
            <button type="submit" class="px-5 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">${edit ? "Update Campaign" : "Save Campaign"}</button>
            ${edit ? `<button type="button" onclick="editing={};renderTab()" class="px-4 py-2.5 rounded-xl bg-slate-100 text-xs font-semibold">Cancel Edit</button>` : ""}
          </div>
        </form>
      </div>

      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="font-bold text-base">Advertisement Requests &amp; Campaigns (${list.length})</h3>
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
                    Advertiser: <b>${esc(c.businessName || c.advertiserName || "Direct")}</b> (${esc(c.contactMobile || "")}) &bull; Package: <b>${esc(c.packageName || "Custom")}</b> (৳${Number(c.price || 0).toLocaleString()} / ${c.durationDays || 30}d)
                  </div>
                  ${linked && linked.targets && linked.targets.length ? `
                    <div class="mt-1.5 flex flex-wrap gap-1">
                      ${linked.targets.map(t => `
                        <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-[11px] font-semibold ${t.exists ? "bg-teal-50 text-teal-800" : "bg-rose-50 text-rose-700"}">
                          ${esc(t.type)}: ${esc(t.label)}
                        </span>
                      `).join("")}
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
                  <button onclick="approveCampaignContent('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-emerald-600 text-white font-bold">Approve</button>
                ` : ""}
                ${eff !== "REJECTED" && eff !== "EXPIRED" ? `
                  <button onclick="rejectCampaignContent('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 font-bold">Reject</button>
                ` : ""}
                ${eff === "ACTIVE" ? `
                  <button onclick="setCampaignPaused('${esc(c.id)}', true)" class="px-3 py-1.5 rounded-xl bg-amber-100 text-amber-900 font-bold">Pause</button>
                ` : ""}
                ${eff === "PAUSED" ? `
                  <button onclick="setCampaignPaused('${esc(c.id)}', false)" class="px-3 py-1.5 rounded-xl bg-sky-600 text-white font-bold">Resume</button>
                ` : ""}
                <button onclick="editAdCampaign('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-sky-50 text-sky-700 font-bold">Edit</button>
                <button onclick="deleteAdCampaign('${esc(c.id)}')" class="px-3 py-1.5 rounded-xl bg-rose-50 text-rose-700 font-bold">Delete</button>
              </div>
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

function onCampaignAdvChange(advId) {
  const a = adState.advertisers[advId];
  if (!a) return;
  const biz = document.getElementById("camp_biz");
  if (biz && !biz.value.trim()) biz.value = a.businessName || a.advertiserName || "";
}

function onCampaignPkgChange(pkgId) {
  const p = adState.packages[pkgId];
  if (!p) return;
  const t = document.getElementById("camp_type");
  const pl = document.getElementById("camp_placement");
  const pr = document.getElementById("camp_price");
  const dy = document.getElementById("camp_days");
  if (t) t.value = p.adType;
  if (pl) pl.value = p.placement;
  if (pr) pr.value = p.price || 0;
  if (dy) dy.value = p.durationDays || 30;
}

function syncCampaignDefaultPlacement(adType) {
  const sel = document.getElementById("camp_placement");
  const found = AD_PACKAGE_TYPES.find(t => t.key === adType);
  if (sel && found) sel.value = found.defaultPlacement;
}

async function submitAdCampaign(e) {
  e.preventDefault();
  const id = (document.getElementById("camp_id").value || "").trim() || uid("camp");
  const old = adState.campaigns[id] || {};
  const advId = document.getElementById("camp_adv").value || "";
  const adv = adState.advertisers[advId] || {};
  const pkgId = document.getElementById("camp_pkg").value || "";
  const pkg = adState.packages[pkgId] || {};
  const adType = document.getElementById("camp_type").value;
  const placement = document.getElementById("camp_placement").value;
  const durationDays = Math.max(1, parseInt(document.getElementById("camp_days").value || "30", 10));
  const selectedOpts = Array.from(document.getElementById("camp_targets")?.selectedOptions || []);
  const promotedTargets = selectedOpts.map(opt => {
    const [rType, rId] = opt.value.split("::");
    const resolved = resolveSingleTargetRef(rType, rId);
    return { recordType: rType, recordId: rId, title: resolved.label };
  });
  const firstTarget = promotedTargets[0] || null;
  const now = Date.now();
  const payload = {
    ...old,
    id,
    advertiserId: advId || old.advertiserId || "",
    advertiserName: adv.advertiserName || old.advertiserName || "Admin",
    businessName: document.getElementById("camp_biz").value.trim() || adv.businessName || old.businessName || "",
    contactMobile: adv.mobile || old.contactMobile || "",
    packageId: pkgId || old.packageId || "",
    packageName: pkg.name || old.packageName || adType,
    adType,
    placement,
    title: document.getElementById("camp_title").value.trim(),
    imageUrl: document.getElementById("camp_img").value.trim(),
    destinationUrl: document.getElementById("camp_dest").value.trim(),
    targetRecordType: firstTarget ? firstTarget.recordType : (adType === "HOME_SLIDER" ? (old.targetRecordType || "") : ""),
    targetRecordId: firstTarget ? firstTarget.recordId : (adType === "HOME_SLIDER" ? (old.targetRecordId || "") : ""),
    targetRecordTitle: firstTarget ? firstTarget.title : (adType === "HOME_SLIDER" ? (old.targetRecordTitle || "") : ""),
    promotedTargets: adType === "HOME_SLIDER" ? promotedTargets : [],
    price: Number(document.getElementById("camp_price").value || 0),
    durationDays,
    paymentStatus: document.getElementById("camp_pay_st").value || "VERIFIED",
    approvalStatus: document.getElementById("camp_app_st").value || "APPROVED",
    campaignStatus: document.getElementById("camp_st").value || "ACTIVE",
    createdAt: old.createdAt || now,
    updatedAt: now
  };
  adState.campaigns[id] = payload;
  await recalculateCampaignActivation(id);
  editing = {};
  await writeAuditLog("SAVE_AD_CAMPAIGN", "AD_CAMPAIGN", id, `Saved campaign ${payload.title}`);
  toast("Campaign saved!");
  renderTab();
}

function editAdCampaign(id) {
  const c = adState.campaigns[id];
  if (!c) return;
  editing = { adCampaign: { id, ...c } };
  if (activeTab !== "ad_requests") {
    switchTab("ad_requests");
  } else {
    renderTab();
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function deleteAdCampaign(id) {
  if (!confirm("Delete this advertisement campaign permanently?")) return;
  await rtdbAdDelete(`advertising/campaigns/${id}`);
  delete adState.campaigns[id];
  if (adState.homeSliderAdvertisers && adState.homeSliderAdvertisers[id]) {
    await rtdbAdDelete(`homeSlider/advertiserCampaigns/${id}`);
    delete adState.homeSliderAdvertisers[id];
  }
  await writeAuditLog("DELETE_AD_CAMPAIGN", "AD_CAMPAIGN", id, `Deleted campaign ${id}`);
  toast("Campaign deleted.");
  renderTab();
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
      promotedTargets: Array.isArray(updated.promotedTargets) ? updated.promotedTargets : [],
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

// 5. PAYMENT VERIFICATION (Full Add / Edit / Delete + Separate Verify/Reject)
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

  const edit = editing.adPayment || null;
  const campaigns = Object.entries(adState.campaigns || {}).map(([id, c]) => ({ id, ...c }));

  return `
    <div class="space-y-5">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base mb-1">${edit ? "Edit Payment Record" : "Add Payment Record"}</h3>
        <p class="text-xs text-slate-500 mb-3">Record or update advertiser payments manually, or verify/reject submitted payments below.</p>
        <form onsubmit="submitAdPayment(event)" class="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input type="hidden" id="pay_id" value="${esc(edit ? edit.id : "")}" />
          <div>
            <label class="block text-xs font-semibold mb-1">Linked Campaign</label>
            <select id="pay_camp" class="input" onchange="onPaymentCampaignSelected(this.value)">
              <option value="">-- Select Campaign (optional) --</option>
              ${campaigns.map(c => `<option value="${esc(c.id)}" ${edit && edit.campaignId === c.id ? "selected" : ""}>${esc(c.title || c.id)} — ${esc(c.businessName || "")}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Business / Advertiser *</label>
            <input id="pay_biz" required class="input" placeholder="Business Name *" value="${esc(edit ? (edit.businessName || edit.advertiserName || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Transaction ID (TRX) *</label>
            <input id="pay_trx" required class="input" placeholder="e.g. BK9X72LM41 *" value="${esc(edit ? (edit.trxId || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Provider</label>
            <select id="pay_provider" class="input">
              ${["bKash", "Nagad", "Rocket", "Upay", "Bank", "Cash"].map(pr => `<option value="${pr}" ${(edit ? edit.provider : "bKash") === pr ? "selected" : ""}>${pr}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Sender Mobile</label>
            <input id="pay_sender" class="input" placeholder="017XXXXXXXX" value="${esc(edit ? (edit.senderMobile || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Receiving Number</label>
            <input id="pay_recv" class="input" placeholder="017XXXXXXXX" value="${esc(edit ? (edit.receivingNumber || "") : "")}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Amount Paid (৳) *</label>
            <input id="pay_amt" type="number" min="0" required class="input" value="${esc(edit ? (edit.amount || 0) : 0)}" />
          </div>
          <div>
            <label class="block text-xs font-semibold mb-1">Status</label>
            <select id="pay_status" class="input">
              ${["PENDING", "VERIFIED", "REJECTED"].map(st => `<option value="${st}" ${(edit ? edit.status : "VERIFIED") === st ? "selected" : ""}>${st}</option>`).join("")}
            </select>
          </div>
          <div class="flex items-end gap-2">
            <button type="submit" class="flex-1 py-2.5 px-4 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold">${edit ? "Update Payment" : "Save Payment"}</button>
            ${edit ? `<button type="button" onclick="editing={};renderTab()" class="px-3 py-2.5 rounded-xl bg-slate-100 text-xs font-semibold">Cancel</button>` : ""}
          </div>
        </form>
      </div>

      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="font-bold text-base">Payment Verification (${list.length})</h3>
          <p class="text-xs text-slate-500">Verify, reject, edit, or delete advertiser payments independently of ad content approval.</p>
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
                <b>${esc(p.businessName || p.advertiserName)}</b> &bull; Package: <b>${esc(p.packageName || "Custom")}</b> &bull; Paid: <b class="text-teal-700">৳${Number(p.amount || 0).toLocaleString()}</b> (Expected: ৳${Number(p.expectedAmount || p.amount || 0).toLocaleString()})
              </div>
              <div class="text-slate-500">
                Method: <b>${esc(p.provider)} ${p.methodName ? "— " + esc(p.methodName) : ""}</b> &bull; Sender: <b>${esc(p.senderMobile || "—")}</b> &rarr; Receiving: <b>${esc(p.receivingNumber || "—")}</b> &bull; Date: ${esc(p.paymentDate || formatDateOnly(p.createdAt))}
              </div>
              ${p.screenshotUrl ? `<a href="${esc(p.screenshotUrl)}" target="_blank" class="inline-block text-sky-700 font-semibold hover:underline">View Payment Screenshot &nearr;</a>` : ""}
              ${p.rejectionReason ? `<div class="text-rose-700 font-medium">Rejection Note: ${esc(p.rejectionReason)}</div>` : ""}
            </div>
            <div class="flex flex-wrap items-start gap-1.5 text-xs">
              ${st !== "VERIFIED" ? `<button onclick="verifyAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-emerald-600 text-white font-bold">Verify</button>` : ""}
              ${st !== "REJECTED" ? `<button onclick="rejectAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 font-bold">Reject</button>` : ""}
              <button onclick="editAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-sky-50 text-sky-700 font-bold">Edit</button>
              <button onclick="deleteAdPayment('${esc(p.id)}')" class="px-3 py-1.5 rounded-xl bg-rose-50 text-rose-700 font-bold">Delete</button>
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

function onPaymentCampaignSelected(campId) {
  const c = adState.campaigns[campId];
  if (!c) return;
  const biz = document.getElementById("pay_biz");
  const amt = document.getElementById("pay_amt");
  if (biz && !biz.value.trim()) biz.value = c.businessName || c.advertiserName || "";
  if (amt && (!amt.value || Number(amt.value) === 0)) amt.value = c.price || 0;
}

async function submitAdPayment(e) {
  e.preventDefault();
  const id = (document.getElementById("pay_id").value || "").trim() || uid("pay");
  const old = adState.payments[id] || {};
  const campId = document.getElementById("pay_camp").value || old.campaignId || "";
  const camp = adState.campaigns[campId] || {};
  const trxId = document.getElementById("pay_trx").value.trim();
  const normTrx = trxId.toUpperCase().replace(/[^A-Z0-9_-]/g, "");
  const status = document.getElementById("pay_status").value || "PENDING";
  const amount = Number(document.getElementById("pay_amt").value || 0);
  const now = Date.now();
  const payload = {
    ...old,
    id,
    campaignId: campId,
    advertiserId: camp.advertiserId || old.advertiserId || "",
    advertiserName: camp.advertiserName || old.advertiserName || "",
    businessName: document.getElementById("pay_biz").value.trim(),
    packageName: camp.packageName || old.packageName || "Custom",
    provider: document.getElementById("pay_provider").value || "bKash",
    methodName: old.methodName || document.getElementById("pay_provider").value || "bKash",
    senderMobile: document.getElementById("pay_sender").value.trim(),
    receivingNumber: document.getElementById("pay_recv").value.trim(),
    trxId,
    normalizedTrxId: normTrx,
    amount,
    expectedAmount: old.expectedAmount || camp.price || amount,
    status,
    createdAt: old.createdAt || now,
    updatedAt: now
  };
  await rtdbAdPut(`advertising/payments/${id}`, payload);
  adState.payments[id] = payload;
  if (normTrx) {
    await rtdbAdPut(`advertising/trxIndex/${normTrx}`, { paymentId: id, campaignId: campId, trxId, updatedAt: now });
    adState.trxIndex[normTrx] = { paymentId: id, campaignId: campId, trxId, updatedAt: now };
  }
  if (campId && adState.campaigns[campId]) {
    adState.campaigns[campId].paymentStatus = status;
    await recalculateCampaignActivation(campId);
  }
  editing = {};
  await writeAuditLog("SAVE_AD_PAYMENT", "AD_PAYMENT", id, `Saved payment TRX ${trxId} (৳${amount})`);
  toast("Payment saved!");
  renderTab();
}

function editAdPayment(id) {
  const p = adState.payments[id];
  if (!p) return;
  editing = { adPayment: { id, ...p } };
  renderTab();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function deleteAdPayment(id) {
  if (!confirm("Delete this payment record?")) return;
  const p = adState.payments[id];
  const normTrx = p ? String(p.normalizedTrxId || p.trxId || "").trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "") : "";
  await rtdbAdDelete(`advertising/payments/${id}`);
  delete adState.payments[id];
  if (normTrx && adState.trxIndex && adState.trxIndex[normTrx] && adState.trxIndex[normTrx].paymentId === id) {
    await rtdbAdDelete(`advertising/trxIndex/${normTrx}`);
    delete adState.trxIndex[normTrx];
  }
  await writeAuditLog("DELETE_AD_PAYMENT", "AD_PAYMENT", id, `Deleted payment ${id}`);
  toast("Payment deleted.");
  renderTab();
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

// 13. CAMPAIGN HISTORY (With Edit & Delete)
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
              <th class="p-3">Actions</th>
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
                <td class="p-3 whitespace-nowrap space-x-1">
                  <button onclick="editAdCampaign('${esc(c.id)}')" class="px-2.5 py-1 rounded-lg bg-sky-50 text-sky-700 font-semibold">Edit</button>
                  <button onclick="deleteAdCampaign('${esc(c.id)}')" class="px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 font-semibold">Delete</button>
                </td>
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
