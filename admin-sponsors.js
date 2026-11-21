// ============================================================================
// Doctor List BD — Direct Sponsor Advertisement & Offline Cache Admin Suite
// Zero AdMob • Zero Third-Party Ad Networks • Server-Authoritative Lifecycle
// Covers ALL 13 Admin Modules + Admin Direct Ad Upload + Audit Logs
// ============================================================================

const PLACEMENT_TYPES = [
  { key: "BANNER_300x50", label: "Banner All Screen (300×50 px)", w: 300, h: 50, adType: "BANNER", sizeLabel: "300×50 px", adPlacement: "ALL" },
  { key: "BANNER_300x200", label: "Native Banner All Screen (300×200 px)", w: 300, h: 200, adType: "NATIVE_BANNER", sizeLabel: "300×200 px", adPlacement: "ALL" },
  { key: "POPUP_AD", label: "Popup App Open (Responsive)", w: 320, h: 200, adType: "POPUP", sizeLabel: "Responsive", adPlacement: "APP_OPEN" },
  { key: "REWARD_VIDEO_DOCTOR_CLICK", label: "Reward Video – Doctor Click (Video)", w: 360, h: 200, adType: "REWARD_VIDEO", sizeLabel: "Video", adPlacement: "DOCTOR_CLICK" },
  { key: "BANNER_320x100", label: "Large Banner (320×100 px)", w: 320, h: 100, adType: "BANNER", sizeLabel: "320×100 px", adPlacement: "ALL" },
  { key: "SPONSORED_NATIVE_CARD", label: "Sponsored Native Card", w: 320, h: 180, adType: "NATIVE_BANNER", sizeLabel: "Responsive", adPlacement: "ALL" },
  { key: "ABOUT_US_SPONSOR", label: "About Us Page Sponsor Section", w: 300, h: 200, adType: "NATIVE_BANNER", sizeLabel: "300×200 px", adPlacement: "ABOUT_US" },
  { key: "BANNER_CUSTOM", label: "Custom-Size Banner", w: 300, h: 200, adType: "BANNER", sizeLabel: "Custom", adPlacement: "ALL" },
  { key: "ALL", label: "All Placements (Universal)", w: 300, h: 200, adType: "BANNER", sizeLabel: "Responsive", adPlacement: "ALL" }
];

const PACKAGE_AD_TYPES = [
  { key: "BANNER", label: "Banner Ad" },
  { key: "NATIVE_BANNER", label: "Native Banner" },
  { key: "POPUP", label: "Popup App Open" },
  { key: "REWARD_VIDEO", label: "Reward Video" },
  { key: "SPONSOR_CARD", label: "Sponsor Card" }
];

const PACKAGE_PLACEMENTS = [
  { key: "ALL", label: "All Screens" },
  { key: "APP_OPEN", label: "App Open (Popup)" },
  { key: "DOCTOR_CLICK", label: "Doctor Click (Reward Video)" },
  { key: "HOME", label: "Home Screen" },
  { key: "DOCTOR_LIST", label: "Doctor List" },
  { key: "DOCTOR_DETAILS", label: "Doctor Details" },
  { key: "HOSPITAL_LIST", label: "Hospital List" },
  { key: "DIAGNOSTIC_LIST", label: "Diagnostic Center List" },
  { key: "SPECIALIST", label: "Medical Specialist" },
  { key: "CHAMBER_SCHEDULE", label: "Chamber Schedule" },
  { key: "ABOUT_US", label: "About Us Page" }
];

const AD_SIZE_FORMATS = [
  { key: "320x50", label: "320×50", format: "Small Banner", w: 320, h: 50 },
  { key: "320x100", label: "320×100", format: "Large Banner", w: 320, h: 100 },
  { key: "300x200", label: "300×200", format: "Sponsor Card", w: 300, h: 200 },
  { key: "300x250", label: "300×250", format: "Rectangle", w: 300, h: 250 },
  { key: "336x280", label: "336×280", format: "Large Rectangle", w: 336, h: 280 },
  { key: "300x300", label: "300×300", format: "Square Sponsor", w: 300, h: 300 },
  { key: "RESPONSIVE", label: "Responsive", format: "Native Sponsor Card", w: 320, h: 180 },
  { key: "FULL_WIDTH", label: "Full Width", format: "Section Banner", w: 720, h: 180 },
  { key: "CUSTOM", label: "Admin Defined", format: "Custom Size", w: 300, h: 200 }
];

const MODULE_LABELS = {
  HOME: "A. Home Screen",
  DOCTOR: "B. Doctor Module",
  HOSPITAL: "C. Hospital Module",
  DIAGNOSTIC: "D. Diagnostic Center Module",
  SPECIALIST: "E. Medical Specialist Module",
  CHAMBER: "F. Chamber Schedule Module",
  OTHER: "G. Other & Global Placements"
};

function makeDefCard(id, nameEn, nameBn, moduleName, allowedSizesCsv, d1, d7, d15, d30, customDaily, maxActiveAds, sortOrder) {
  return {
    placementId: id,
    placementNameEn: nameEn,
    placementNameBn: nameBn,
    moduleName,
    allowedSizesCsv,
    sizeRateOverridesJson: "{}",
    dailyRateBdt: d1,
    sevenDaysRateBdt: d7,
    fifteenDaysRateBdt: d15,
    thirtyDaysRateBdt: d30,
    customDailyRateBdt: customDaily,
    maxActiveAds,
    rotationMode: "WEIGHTED_ROTATION",
    cardInsertionFrequency: 5,
    status: "ACTIVE",
    sortOrder
  };
}

const DEFAULT_PLACEMENT_CATALOG = [
  // A. Home Screen
  makeDefCard("HOME-01", "Home Top Banner", "হোম টপ ব্যানার", "HOME", "320x50,320x100,FULL_WIDTH,RESPONSIVE", 50, 200, 320, 500, 25, 3, 1),
  makeDefCard("HOME-02", "Between Home Sections", "Home Section-এর মাঝে", "HOME", "320x100,300x200,300x250,RESPONSIVE,FULL_WIDTH", 40, 170, 270, 420, 20, 3, 2),
  makeDefCard("HOME-03", "Popular Doctor Section", "Popular Doctor Section", "HOME", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 40, 160, 250, 400, 20, 3, 3),
  makeDefCard("HOME-04", "Hospital Section", "Hospital Section", "HOME", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 4),
  makeDefCard("HOME-05", "Diagnostic Section", "Diagnostic Section", "HOME", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 5),
  makeDefCard("HOME-06", "Medical Specialist Section", "Medical Specialist Section", "HOME", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 6),
  makeDefCard("HOME-07", "Home Bottom Banner", "হোম বটম ব্যানার", "HOME", "320x50,320x100,FULL_WIDTH,RESPONSIVE", 40, 160, 250, 400, 20, 3, 7),
  // B. Doctor Module
  makeDefCard("DOC-01", "Between Doctor List", "Doctor List-এর মাঝে", "DOCTOR", "300x200,300x250,RESPONSIVE,320x100", 30, 120, 190, 300, 15, 4, 10),
  makeDefCard("DOC-02", "Doctor Details Page", "Doctor Details Page", "DOCTOR", "300x200,300x250,336x280,RESPONSIVE,FULL_WIDTH", 45, 180, 290, 450, 22, 3, 11),
  makeDefCard("DOC-03", "Between Doctor Search Results", "Doctor Search Results-এর মাঝে", "DOCTOR", "320x100,300x200,RESPONSIVE", 35, 140, 220, 350, 18, 3, 12),
  makeDefCard("DOC-04", "Doctor Profile Bottom", "Doctor Profile Bottom", "DOCTOR", "320x50,320x100,300x200,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 13),
  makeDefCard("DOC-05", "Doctor Details Related Section", "Doctor Details Related Section", "DOCTOR", "300x200,300x300,RESPONSIVE", 30, 120, 190, 300, 15, 3, 14),
  // C. Hospital Module
  makeDefCard("HOS-01", "Between Hospital List", "Hospital List-এর মাঝে", "HOSPITAL", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 4, 20),
  makeDefCard("HOS-02", "Hospital Details Page", "Hospital Details Page", "HOSPITAL", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 21),
  makeDefCard("HOS-03", "Between Hospital Search Results", "Hospital Search Results-এর মাঝে", "HOSPITAL", "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 22),
  makeDefCard("HOS-04", "Hospital Details Bottom", "Hospital Details Bottom", "HOSPITAL", "320x50,320x100,FULL_WIDTH", 25, 100, 160, 260, 14, 3, 23),
  // D. Diagnostic Center Module
  makeDefCard("DIA-01", "Between Diagnostic List", "Diagnostic List-এর মাঝে", "DIAGNOSTIC", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 4, 30),
  makeDefCard("DIA-02", "Diagnostic Details Page", "Diagnostic Details Page", "DIAGNOSTIC", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 31),
  makeDefCard("DIA-03", "Between Diagnostic Search Results", "Diagnostic Search Results-এর মাঝে", "DIAGNOSTIC", "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 32),
  makeDefCard("DIA-04", "Diagnostic Details Bottom", "Diagnostic Details Bottom", "DIAGNOSTIC", "320x50,320x100,FULL_WIDTH", 25, 100, 160, 260, 14, 3, 33),
  // E. Specialist Module
  makeDefCard("SPE-01", "Between Specialist List", "Specialist List-এর মাঝে", "SPECIALIST", "300x200,320x100,RESPONSIVE", 30, 120, 190, 300, 15, 3, 40),
  makeDefCard("SPE-02", "Specialist Details Page", "Specialist Details Page", "SPECIALIST", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 41),
  makeDefCard("SPE-03", "Between Specialist Search Results", "Specialist Search Results-এর মাঝে", "SPECIALIST", "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 42),
  // F. Chamber Schedule Module
  makeDefCard("CHA-01", "Between Chamber Schedule List", "Chamber Schedule List-এর মাঝে", "CHAMBER", "300x200,320x100,RESPONSIVE", 30, 120, 190, 300, 15, 3, 50),
  makeDefCard("CHA-02", "Chamber Details Page", "Chamber Details Page", "CHAMBER", "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 51),
  makeDefCard("CHA-03", "Doctor Schedule Details", "Doctor Schedule Details", "CHAMBER", "320x100,300x200,RESPONSIVE", 35, 140, 220, 350, 18, 3, 52),
  makeDefCard("CHA-04", "Between Chamber Search Results", "Chamber Search Results-এর মাঝে", "CHAMBER", "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 53),
  // G. Other & Global Placements
  makeDefCard("OTH-SEARCH", "Search Result Page", "সার্চ রেজাল্ট পেজ", "OTHER", "320x50,320x100,300x200,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 60),
  makeDefCard("OTH-CATEGORY", "Category Page", "ক্যাটাগরি পেজ", "OTHER", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 61),
  makeDefCard("OTH-DISTRICT", "District-wise Listing", "জেলাভিত্তিক তালিকা", "OTHER", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 62),
  makeDefCard("OTH-UPAZILA", "Upazila-wise Listing", "উপজেলাভিত্তিক তালিকা", "OTHER", "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 63),
  makeDefCard("OTH-FEATURED", "Featured Section", "ফিচার্ড সেকশন", "OTHER", "300x200,300x250,336x280,300x300,RESPONSIVE,FULL_WIDTH", 50, 200, 320, 500, 25, 3, 64),
  makeDefCard("OTH-ABOUT", "App About Us Page", "অ্যাপ About Us পেজ", "OTHER", "300x200,320x100,300x250,RESPONSIVE", 40, 160, 250, 400, 20, 3, 65),
  makeDefCard("OTH-POPUP", "Popup Advertisement", "পপআপ বিজ্ঞাপন", "OTHER", "300x200,300x250,336x280,300x300,CUSTOM", 80, 350, 600, 1000, 55, 2, 66),
  makeDefCard("OTH-FULLSCREEN", "Full Screen Advertisement", "ফুল-স্ক্রিন বিজ্ঞাপন", "OTHER", "300x250,336x280,300x300,FULL_WIDTH,CUSTOM", 100, 450, 800, 1400, 75, 2, 67),
  makeDefCard("OTH-WEB", "Website Page Banner", "ওয়েবসাইট পেজ ব্যানার", "OTHER", "320x50,320x100,FULL_WIDTH,336x280,CUSTOM", 40, 160, 250, 400, 20, 3, 68),
  makeDefCard("OTH-FUTURE", "Future Modules", "ভবিষ্যৎ মডিউলসমূহ", "OTHER", "320x50,320x100,300x200,300x250,336x280,300x300,RESPONSIVE,FULL_WIDTH,CUSTOM", 30, 120, 190, 300, 15, 3, 69)
];

const SPONSOR_PAGE_OPTIONS = [
  { key: "home", label: "Home" },
  { key: "find_doctors", label: "Find Doctors" },
  { key: "facilities", label: "Hospitals & Facilities" },
  { key: "portal", label: "Saved / Portal" },
  { key: "doctor_profile", label: "Doctor Profile" },
  { key: "about_us", label: "About Us" },
  { key: "info", label: "Info Pages" }
];

let adFilterState = {
  search: "",
  advertiserId: "",
  paymentStatus: "ALL",
  reportRange: "30D",
  previewMode: "banner", // banner | native | popup | about_us
  rateModuleFilter: "ALL"
};

function getMergedRateCards() {
  const cloud = dbState.placement_rate_cards || {};
  const cloudById = {};
  for (const [k, v] of Object.entries(cloud)) {
    if (!v) continue;
    const pid = String(v.placementId || k).toUpperCase();
    cloudById[pid] = { ...v, placementId: v.placementId || k };
  }
  const merged = DEFAULT_PLACEMENT_CATALOG.map(def => cloudById[def.placementId.toUpperCase()] || { ...def });
  const known = new Set(merged.map(c => c.placementId.toUpperCase()));
  for (const v of Object.values(cloudById)) {
    if (!known.has(String(v.placementId).toUpperCase())) {
      merged.push(v);
    }
  }
  return merged.sort((a, b) => (Number(a.sortOrder || 99) - Number(b.sortOrder || 99)) || String(a.placementId).localeCompare(String(b.placementId)));
}

function getRateCardSettings() {
  return {
    bundleEnabled: true,
    discount2Pct: 5,
    discount3Pct: 10,
    discount4PlusPct: 15,
    allowCustomDuration: true,
    ...(dbState.rate_card_settings || {})
  };
}

function parseSizeOverridesObj(card) {
  if (card?.sizeRateOverrides && typeof card.sizeRateOverrides === "object") return card.sizeRateOverrides;
  try {
    return JSON.parse(card?.sizeRateOverridesJson || "{}") || {};
  } catch (_) {
    return {};
  }
}

function calculatePlacementRateJs(card, adSizeKey, durationDays) {
  const days = Math.max(1, Number(durationDays || 30));
  let baseRate = 0;
  if (days === 1) baseRate = Number(card.dailyRateBdt || 0);
  else if (days === 7) baseRate = Number(card.sevenDaysRateBdt || 0);
  else if (days === 15) baseRate = Number(card.fifteenDaysRateBdt || 0);
  else if (days === 30) baseRate = Number(card.thirtyDaysRateBdt || 0);
  else {
    baseRate = Number(card.customDailyRateBdt || 0) > 0
      ? days * Number(card.customDailyRateBdt)
      : Math.ceil((Number(card.thirtyDaysRateBdt || 0) / 30) * days);
  }
  const overrides = parseSizeOverridesObj(card);
  const norm = String(adSizeKey || "").trim().toUpperCase();
  const ov = Number(overrides[norm] || overrides[adSizeKey] || 0);
  if (ov > 0) {
    if (days === 30) return ov;
    const t30 = Number(card.thirtyDaysRateBdt || 0);
    return t30 > 0 ? Math.max(1, Math.round(baseRate * (ov / t30))) : Math.max(1, Math.ceil((ov / 30) * days));
  }
  return Math.max(0, baseRate);
}

function calculateMultiPlacementQuoteJs(selectedCards, adSizeKey, durationDays, settings) {
  const lines = selectedCards.map(c => ({
    placementId: c.placementId,
    placementNameEn: c.placementNameEn,
    moduleName: c.moduleName,
    priceBdt: calculatePlacementRateJs(c, adSizeKey, durationDays)
  }));
  const subtotalBdt = lines.reduce((s, l) => s + l.priceBdt, 0);
  let discountPct = 0;
  if (settings?.bundleEnabled && lines.length >= 2) {
    if (lines.length === 2) discountPct = Number(settings.discount2Pct || 0);
    else if (lines.length === 3) discountPct = Number(settings.discount3Pct || 0);
    else discountPct = Number(settings.discount4PlusPct || 0);
  }
  discountPct = Math.max(0, Math.min(80, discountPct));
  const discountBdt = Math.floor((subtotalBdt * discountPct) / 100);
  const totalBdt = Math.max(0, subtotalBdt - discountBdt);
  const breakdownMap = {};
  lines.forEach(l => {
    breakdownMap[l.placementId] = (subtotalBdt > 0 && discountBdt > 0)
      ? Math.round((l.priceBdt * totalBdt) / subtotalBdt)
      : l.priceBdt;
  });
  return { lines, subtotalBdt, discountPct, discountBdt, totalBdt, breakdownMap };
}

// --- CRYPTO HELPERS (Matches Android AdvertiserRemoteService SHA-256(salt:password)) ---
async function sha256Hex(text) {
  const data = new TextEncoder().encode(text);
  const hashBuf = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hashBuf))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

function generateSaltHex(byteLen = 16) {
  const bytes = new Uint8Array(byteLen);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function hashCredential(password, salt) {
  return sha256Hex(`${salt}:${password}`);
}

// --- DATE, STATUS & VALIDATION HELPERS ---
function getDhakaTodayStr() {
  const now = new Date();
  const utcMs = now.getTime() + now.getTimezoneOffset() * 60000;
  const dhaka = new Date(utcMs + 6 * 3600000);
  return dhaka.toISOString().slice(0, 10);
}

function formatDateTime(ms) {
  const n = Number(ms || 0);
  if (!n || n <= 0) return "—";
  return new Date(n).toLocaleString("en-GB", {
    year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit"
  });
}

function formatDateOnly(ms) {
  const n = Number(ms || 0);
  if (!n || n <= 0) return "—";
  return new Date(n).toISOString().slice(0, 10);
}

function normalizeTrxId(raw) {
  return String(raw || "").trim().toUpperCase().replace(/\s+/g, "");
}

function isValidSponsorImageSrc(url) {
  const clean = String(url || "").trim();
  if (!clean) return false;
  return clean.startsWith("https://") || /^data:image\/(png|jpeg|jpg|webp);base64,/i.test(clean);
}

function isValidWebUrl(url) {
  const clean = String(url || "").trim();
  if (!clean) return true;
  return /^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(clean);
}

function placementLabel(key) {
  const found = PLACEMENT_TYPES.find(p => p.key === key);
  return found ? found.label : (key || "All Placements");
}

function getEffectiveAdStatus(sp, nowMs = Date.now()) {
  if (!sp) return "PENDING";
  const raw = String(sp.status || (sp.isActive === false ? "PAUSED" : "ACTIVE")).toUpperCase();
  if (["REJECTED", "PAUSED", "PENDING", "PAYMENT_PENDING", "PENDING_PAYMENT", "PENDING_APPROVAL", "EXPIRED", "CANCELLED"].includes(raw)) {
    return raw;
  }
  const expiryAt = Number(sp.expiryAt || 0);
  if (expiryAt > 0 && expiryAt < Number.MAX_SAFE_INTEGER && nowMs > expiryAt) {
    return "EXPIRED";
  }
  const today = getDhakaTodayStr();
  if (sp.endDate && sp.endDate < today) {
    return "EXPIRED";
  }
  return sp.isActive === false ? "PAUSED" : "ACTIVE";
}

function remainingDaysOfAd(sp, nowMs = Date.now()) {
  if (getEffectiveAdStatus(sp, nowMs) !== "ACTIVE") return 0;
  const expiryAt = Number(sp.expiryAt || 0);
  if (expiryAt > 0) {
    const diff = expiryAt - nowMs;
    return diff <= 0 ? 0 : Math.ceil(diff / 86400000);
  }
  if (sp.endDate) {
    const endMs = new Date(sp.endDate + "T23:59:59+06:00").getTime();
    const diff = endMs - nowMs;
    return diff <= 0 ? 0 : Math.ceil(diff / 86400000);
  }
  return Number(sp.durationDays || 0);
}

function statusBadgeHtml(status) {
  const map = {
    ACTIVE: "bg-emerald-100 text-emerald-800 border-emerald-200",
    VERIFIED: "bg-emerald-100 text-emerald-800 border-emerald-200",
    PENDING: "bg-amber-100 text-amber-800 border-amber-200",
    PAYMENT_PENDING: "bg-amber-100 text-amber-800 border-amber-200",
    PENDING_PAYMENT: "bg-amber-100 text-amber-800 border-amber-200",
    PENDING_APPROVAL: "bg-amber-100 text-amber-800 border-amber-200",
    PAUSED: "bg-slate-200 text-slate-700 border-slate-300",
    INACTIVE: "bg-slate-200 text-slate-700 border-slate-300",
    DISABLED: "bg-slate-200 text-slate-700 border-slate-300",
    CANCELLED: "bg-slate-200 text-slate-600 border-slate-300",
    SUSPENDED: "bg-rose-100 text-rose-800 border-rose-200",
    EXPIRED: "bg-orange-100 text-orange-800 border-orange-200",
    REJECTED: "bg-rose-100 text-rose-800 border-rose-200"
  };
  const cls = map[String(status || "").toUpperCase()] || "bg-slate-100 text-slate-700 border-slate-200";
  return `<span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold border ${cls}">${esc(status)}</span>`;
}

// --- AUDIT LOGGING ---
async function writeAuditLog(action, targetType, targetId, summary) {
  const adminActor = sessionStorage.getItem("dlbd_admin_id") || "super_admin";
  const logId = uid("log");
  const entry = {
    id: logId,
    actor: adminActor,
    action,
    targetType,
    targetId,
    summary,
    timestamp: Date.now()
  };
  try {
    await fetch(`${RTDB_BASE}/audit_logs/${logId}.json`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(entry)
    });
    if (!dbState.audit_logs) dbState.audit_logs = {};
    dbState.audit_logs[logId] = entry;
  } catch (_) {}
}

// --- AUTO-EXPIRE SWEEP (Updates RTDB when approved ads pass expiryAt) ---
async function sweepExpiredAdsSilently() {
  const now = Date.now();
  const sponsors = dbState.sponsors || {};
  const updates = {};
  let expiredCount = 0;
  for (const [id, sp] of Object.entries(sponsors)) {
    if (!sp) continue;
    const raw = String(sp.status || "").toUpperCase();
    if (raw === "ACTIVE" && getEffectiveAdStatus(sp, now) === "EXPIRED") {
      updates[id] = { ...sp, status: "EXPIRED", isActive: false, updatedAt: now };
      expiredCount++;
    }
  }
  if (expiredCount > 0) {
    try {
      await fetch(`${RTDB_BASE}/sponsors.json`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updates)
      });
      for (const [id, v] of Object.entries(updates)) {
        dbState.sponsors[id] = v;
      }
      await writeAuditLog("AUTO_EXPIRE", "SPONSORS", "batch", `Auto-expired ${expiredCount} elapsed sponsor ad(s)`);
    } catch (_) {}
  }
}

// --- ANALYTICS & REVENUE AGGREGATORS ---
function getSponsorArrays() {
  const now = Date.now();
  const allAds = Object.entries(dbState.sponsors || {})
    .map(([id, s]) => ({ id, ...s, effectiveStatus: getEffectiveAdStatus(s, now), remDays: remainingDaysOfAd(s, now) }))
    .sort((a, b) => (Number(b.updatedAt || 0) - Number(a.updatedAt || 0)));

  const allPayments = Object.entries(dbState.ad_payments || {})
    .map(([id, p]) => ({ id, ...p }))
    .sort((a, b) => (Number(b.createdAt || 0) - Number(a.createdAt || 0)));

  const allPackages = Object.entries(dbState.ad_packages || {})
    .map(([id, pk]) => ({ id, ...pk }))
    .sort((a, b) => Number(a.priceBdt || 0) - Number(b.priceBdt || 0));

  const allAdvertisers = Object.entries(dbState.advertisers || {})
    .map(([id, a]) => ({ id, ...a }))
    .sort((a, b) => (Number(b.createdAt || 0) - Number(a.createdAt || 0)));

  const activeAds = allAds.filter(a => a.effectiveStatus === "ACTIVE");

  return { allAds, activeAds, allPayments, allPackages, allAdvertisers };
}

function computeDirectSponsorMetrics() {
  const { allAds, allPayments, allPackages, allAdvertisers } = getSponsorArrays();
  const activeAds = allAds.filter(a => a.effectiveStatus === "ACTIVE");
  const pendingAds = allAds.filter(a => ["PENDING", "PAYMENT_PENDING", "PENDING_APPROVAL", "PENDING_PAYMENT"].includes(a.effectiveStatus));
  const expiredAds = allAds.filter(a => a.effectiveStatus === "EXPIRED");
  const renewalAds = allAds.filter(a => Boolean(a.renewalOfAdId));
  const activePackagesCount = allPackages.filter(pk => String(pk.status || "ACTIVE").toUpperCase() === "ACTIVE").length;

  const pendingPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "PENDING");
  const verifiedPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "VERIFIED");
  const rejectedPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "REJECTED");

  const verifiedRevenueBdt = verifiedPayments.reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);
  const bkashRevenueBdt = verifiedPayments.filter(p => String(p.paymentMethod || "").toUpperCase() === "BKASH")
    .reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);
  const nagadRevenueBdt = verifiedPayments.filter(p => String(p.paymentMethod || "").toUpperCase() === "NAGAD")
    .reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);
  const rocketRevenueBdt = verifiedPayments.filter(p => String(p.paymentMethod || "").toUpperCase() === "ROCKET")
    .reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);

  // View analytics from /directory/ad_analytics
  const analytics = dbState.ad_analytics || {};
  const totalsNode = analytics.totals || {};
  const dailyNode = analytics.daily || {};
  const pkgNode = analytics.packages || {};

  let totalImpressions = 0;
  let totalUniqueViews = 0;
  let totalClicks = 0;
  let totalOfflineSynced = 0;

  const adStatsMap = {};
  for (const [adId, t] of Object.entries(totalsNode)) {
    if (!t || typeof t !== "object") continue;
    const imp = Number(t.totalImpressions || 0);
    const uniq = Number(t.uniqueViews || 0);
    const clk = Number(t.clicks || 0);
    const off = Number(t.offlineSyncedViews || 0);
    totalImpressions += imp;
    totalUniqueViews += uniq;
    totalClicks += clk;
    totalOfflineSynced += off;
    adStatsMap[adId] = { adId, impressions: imp, uniqueViews: uniq, clicks: clk, offlineSynced: off, packageId: t.packageId || "" };
  }

  // Merge any direct counters on sponsor nodes if totalsNode hasn't seen them yet
  for (const ad of allAds) {
    if (!adStatsMap[ad.id]) {
      const imp = Number(ad.totalViews || 0);
      const uniq = Number(ad.uniqueViews || 0);
      adStatsMap[ad.id] = { adId: ad.id, impressions: imp, uniqueViews: uniq, clicks: 0, offlineSynced: 0, packageId: ad.packageId || "" };
      totalImpressions += imp;
      totalUniqueViews += uniq;
    }
  }

  const dailyRows = [];
  for (const [dateStr, adsObj] of Object.entries(dailyNode)) {
    if (!adsObj || typeof adsObj !== "object") continue;
    let imp = 0, uniq = 0, clk = 0, off = 0, dls = 0;
    for (const st of Object.values(adsObj)) {
      if (!st || typeof st !== "object") continue;
      imp += Number(st.totalImpressions || 0);
      uniq += Number(st.uniqueViews || 0);
      clk += Number(st.clicks || 0);
      off += Number(st.offlineSyncedViews || 0);
      dls += Number(st.cacheDownloads || 0);
    }
    dailyRows.push({ date: dateStr, impressions: imp, uniqueViews: uniq, clicks: clk, offlineSynced: off, cacheDownloads: dls });
  }
  dailyRows.sort((a, b) => b.date.localeCompare(a.date));

  const rateCards = getMergedRateCards();
  const activeCards = rateCards.filter(c => String(c.status || "ACTIVE").toUpperCase() === "ACTIVE");
  const cardById = Object.fromEntries(rateCards.map(c => [c.placementId.toUpperCase(), c]));
  const adById = Object.fromEntries(allAds.map(a => [a.id, a]));

  // Slot occupancy across all 37+ placement IDs
  const slotUsage = {};
  rateCards.forEach(c => {
    slotUsage[c.placementId] = {
      placementId: c.placementId,
      name: c.placementNameEn,
      moduleName: c.moduleName,
      maxSlots: Math.max(1, Number(c.maxActiveAds || 3)),
      usedSlots: 0,
      bookingsCount: 0,
      revenueBdt: 0,
      status: c.status || "ACTIVE"
    };
  });

  activeAds.forEach(ad => {
    const pids = String(ad.placementIds || "").split(",").map(s => s.trim().toUpperCase()).filter(Boolean);
    pids.forEach(pid => {
      if (slotUsage[pid]) slotUsage[pid].usedSlots += 1;
    });
  });

  // Booking popularity + Monthly revenue + Placement-wise & Module-wise Revenue
  const nowMs = Date.now();
  const thirtyDaysAgoMs = nowMs - 30 * 86400000;
  let monthlyRevenueBdt = 0;
  const moduleRevenue = {};

  allPayments.forEach(p => {
    const ad = adById[p.adId];
    const pidsRaw = String(p.placementIds || ad?.placementIds || "").split(",").map(s => s.trim().toUpperCase()).filter(Boolean);
    pidsRaw.forEach(pid => {
      if (slotUsage[pid]) slotUsage[pid].bookingsCount += 1;
    });

    if (String(p.status || "").toUpperCase() === "VERIFIED") {
      const amt = Number(p.amountBdt || 0);
      const ts = Number(p.verifiedAt || p.createdAt || 0);
      if (ts >= thirtyDaysAgoMs) monthlyRevenueBdt += amt;

      const bd = (p.placementBreakdown && typeof p.placementBreakdown === "object")
        ? p.placementBreakdown
        : (() => {
            try { return JSON.parse(p.placementBreakdownJson || ad?.placementBreakdownJson || "{}"); } catch (_) { return null; }
          })();

      if (bd && Object.keys(bd).length > 0) {
        for (const [pid, part] of Object.entries(bd)) {
          const val = Number(part || 0);
          const up = pid.toUpperCase();
          if (slotUsage[up]) slotUsage[up].revenueBdt += val;
          const mod = cardById[up]?.moduleName || "OTHER";
          moduleRevenue[mod] = (moduleRevenue[mod] || 0) + val;
        }
      } else if (pidsRaw.length > 0) {
        const share = Math.round(amt / pidsRaw.length);
        pidsRaw.forEach(pid => {
          if (slotUsage[pid]) slotUsage[pid].revenueBdt += share;
          const mod = cardById[pid]?.moduleName || "OTHER";
          moduleRevenue[mod] = (moduleRevenue[mod] || 0) + share;
        });
      } else {
        moduleRevenue["LEGACY_PACKAGE"] = (moduleRevenue["LEGACY_PACKAGE"] || 0) + amt;
      }
    }
  });

  const totalSlots = activeCards.reduce((s, c) => s + Math.max(1, Number(c.maxActiveAds || 3)), 0);
  const usedSlots = activeCards.reduce((s, c) => s + Math.min(Number(c.maxActiveAds || 3), slotUsage[c.placementId]?.usedSlots || 0), 0);
  const availableSlots = Math.max(0, totalSlots - usedSlots);

  const placementStats = Object.values(slotUsage);
  const mostPopularPlacement = [...placementStats].sort((a, b) => (b.bookingsCount - a.bookingsCount) || (b.revenueBdt - a.revenueBdt) || (b.usedSlots - a.usedSlots))[0] || null;

  return {
    allAds, activeAds, pendingAds, expiredAds, renewalAds,
    allPayments, pendingPayments, verifiedPayments, rejectedPayments,
    allPackages, activePackagesCount, allAdvertisers,
    verifiedRevenueBdt, monthlyRevenueBdt, bkashRevenueBdt, nagadRevenueBdt, rocketRevenueBdt,
    totalImpressions, totalUniqueViews, totalClicks, totalOfflineSynced,
    adStatsMap, dailyRows, pkgNode,
    rateCards, totalSlots, usedSlots, availableSlots,
    placementStats, moduleRevenue, mostPopularPlacement
  };
}

// ============================================================================
// MODULE 1: ADMIN DASHBOARD (Direct Sponsor Package Management + Directory)
// ============================================================================
function renderDashboard() {
  const m = computeDirectSponsorMetrics();
  const docs = Object.values(dbState.doctors || {});
  const chambers = Object.values(dbState.chambers || {});
  const activeChambers = chambers.filter(c => c.isActive !== false).length;
  const recentApplications = [...m.allAds].slice(0, 8);

  return `
    <!-- Hero Direct Sponsor Package Dashboard -->
    <div class="bg-gradient-to-r from-slate-900 via-sky-900 to-teal-900 text-white p-6 rounded-3xl shadow-sm mb-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div>
          <div class="flex flex-wrap items-center gap-2 mb-1">
            <span class="px-2.5 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-400/40 text-emerald-200 text-[11px] font-bold">Direct Sponsor Package System</span>
            <span class="px-2.5 py-0.5 rounded-full bg-white/10 text-sky-100 text-[11px] font-semibold">Online: Start.io + Unity Ads + Direct Sponsors &bull; Offline: Cached Direct Sponsors Only</span>
          </div>
          <h3 class="text-xl font-bold">Direct Sponsor Package Dashboard</h3>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="switchTab('ad_packages')" class="px-4 py-2 rounded-xl bg-teal-500 hover:bg-teal-600 text-white text-xs font-bold shadow">
            Manage Packages &rarr;
          </button>
          <button onclick="switchTab('payments')" class="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold shadow">
            Verify Payments (${m.pendingPayments.length})
          </button>
          <button onclick="switchTab('advertisements')" class="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-white text-xs font-bold shadow">
            + Direct Upload Ad
          </button>
          <a href="advertiser.html" target="_blank" class="px-4 py-2 rounded-xl bg-white/15 hover:bg-white/25 text-white text-xs font-semibold">
            Sponsor Booking Page &nearr;
          </a>
        </div>
      </div>

      <!-- Required 6 Core Sponsor Package KPIs -->
      <div class="grid grid-cols-2 lg:grid-cols-3 gap-3">
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('ad_packages')">
          <div class="text-xs text-sky-200 font-semibold">Total Packages</div>
          <div class="text-2xl font-extrabold mt-1">${m.allPackages.length}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">${m.activePackagesCount} active &bull; Fixed Package Rate only</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('advertisements')">
          <div class="text-xs text-cyan-200 font-semibold">Total Sponsor Requests</div>
          <div class="text-2xl font-extrabold mt-1">${m.allAds.length}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">${m.allPayments.length} payment submissions</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('pending_ads')">
          <div class="text-xs text-amber-200 font-semibold">Pending Requests</div>
          <div class="text-2xl font-extrabold mt-1">${m.pendingAds.length}</div>
          <div class="text-[11px] text-amber-100 mt-0.5">${m.pendingPayments.length} awaiting payment verification</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('active_ads')">
          <div class="text-xs text-emerald-200 font-semibold">Active Campaigns</div>
          <div class="text-2xl font-extrabold mt-1">${m.activeAds.length}</div>
          <div class="text-[11px] text-emerald-100 mt-0.5">Live online &amp; cached offline</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('expired_ads')">
          <div class="text-xs text-orange-200 font-semibold">Expired Campaigns</div>
          <div class="text-2xl font-extrabold mt-1">${m.expiredAds.length}</div>
          <div class="text-[11px] text-orange-100 mt-0.5">Auto-removed from active placements</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('payments')">
          <div class="text-xs text-emerald-200 font-semibold">Total Sponsor Package Sales</div>
          <div class="text-2xl font-extrabold mt-1">&#2547;${m.verifiedRevenueBdt.toLocaleString()}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">bKash &#2547;${m.bkashRevenueBdt.toLocaleString()} &bull; Nagad &#2547;${m.nagadRevenueBdt.toLocaleString()} &bull; Rocket &#2547;${m.rocketRevenueBdt.toLocaleString()}</div>
        </div>
      </div>
    </div>

    ${m.pendingPayments.length > 0 ? `
      <div class="mb-6 p-4 rounded-2xl bg-amber-50 border border-amber-300 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-amber-900">
          <strong>Action Required:</strong> You have <strong>${m.pendingPayments.length}</strong> manual bKash / Nagad / Rocket payment(s) waiting for Admin verification.
        </div>
        <button onclick="switchTab('payments')" class="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold">
          Verify Payments Now &rarr;
        </button>
      </div>
    ` : ""}

    <!-- Recent Sponsor Applications & Quick Controls -->
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm mb-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h4 class="font-bold text-slate-900 text-base">Recent Sponsor Applications (${recentApplications.length})</h4>
          <p class="text-xs text-slate-500">Latest package requests submitted via the Mobile App or Website</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="switchTab('payment_settings')" class="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold">Payment Numbers</button>
          <button onclick="switchTab('rate_cards')" class="px-3 py-1.5 rounded-lg bg-teal-50 hover:bg-teal-100 text-teal-800 text-xs font-semibold">Placement Display Settings</button>
          <button onclick="switchTab('advertisements')" class="px-3 py-1.5 rounded-lg bg-sky-50 hover:bg-sky-100 text-sky-800 text-xs font-bold">View All Campaigns &rarr;</button>
        </div>
      </div>
      ${recentApplications.length === 0 ? `
        <div class="p-6 text-center rounded-xl bg-slate-50 border border-dashed border-slate-200 text-xs text-slate-500">
          No sponsor applications yet. Add packages in <strong>Sponsor Packages</strong> so businesses can start booking.
        </div>
      ` : `
        <div class="overflow-x-auto">
          <table class="w-full text-xs text-left border-collapse">
            <thead>
              <tr class="border-b border-slate-200 text-slate-500 uppercase">
                <th class="py-2.5 px-3">Business &amp; Title</th>
                <th class="py-2.5 px-3">Package</th>
                <th class="py-2.5 px-3">Fixed Price</th>
                <th class="py-2.5 px-3">Duration / Expiry</th>
                <th class="py-2.5 px-3">Status</th>
                <th class="py-2.5 px-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              ${recentApplications.map(ad => `
                <tr class="hover:bg-slate-50">
                  <td class="py-2.5 px-3">
                    <div class="font-bold text-slate-900">${esc(ad.name)}</div>
                    <div class="text-slate-500">${esc(ad.promoTitle || "—")}${ad.contactPersonName ? ` &bull; ${esc(ad.contactPersonName)}` : ""}</div>
                  </td>
                  <td class="py-2.5 px-3">
                    <div class="font-semibold text-sky-900">${esc(ad.packageName || placementLabel(ad.placementType))}</div>
                    <div class="text-[11px] text-slate-500">${esc(ad.adPlacement || ad.targetPages || "ALL")}</div>
                  </td>
                  <td class="py-2.5 px-3 font-extrabold text-emerald-700">&#2547;${Number(ad.priceBdt || 0).toLocaleString()}</td>
                  <td class="py-2.5 px-3">
                    <div>${ad.durationDays || 30} Days</div>
                    <div class="text-[11px] text-slate-500">${ad.expiryAt > 0 && ad.expiryAt < Number.MAX_SAFE_INTEGER ? `Exp: ${formatDateOnly(ad.expiryAt)}` : "Not started"}</div>
                  </td>
                  <td class="py-2.5 px-3">${statusBadgeHtml(ad.effectiveStatus)}</td>
                  <td class="py-2.5 px-3 text-right">
                    <button onclick="editing.ad='${esc(ad.id)}';switchTabKeepEdit('advertisements')" class="px-2.5 py-1 rounded-lg bg-sky-100 text-sky-800 font-semibold hover:bg-sky-200">Manage</button>
                  </td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      `}
    </div>

    <!-- Doctor Directory KPIs -->
    <h4 class="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">Doctor Directory Status</h4>
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
      ${[
        ["Total Doctors", docs.length, "text-sky-700", "doctors"],
        ["Active Chambers", activeChambers, "text-emerald-600", "chambers"],
        ["Weekly Time Slots", Object.keys(dbState.chamber_schedules || {}).length, "text-teal-700", "chambers"],
        ["Hospitals", Object.keys(dbState.hospitals || {}).length, "text-sky-600", "hospitals"],
        ["Diagnostic Centers", Object.keys(dbState.diagnostic_centers || {}).length, "text-cyan-700", "diagnostics"],
        ["Specialties", Object.keys(dbState.specialties || {}).length, "text-slate-800", "specialties"],
        ["Custom Locations", Object.keys(dbState.locations || {}).length, "text-amber-700", "locations"],
        ["Advertiser Accounts", m.allAdvertisers.length, "text-indigo-700", "advertisers"]
      ].map(([label, count, cls, tab]) => `
        <div onclick="switchTab('${tab}')" class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm cursor-pointer hover:border-sky-300 transition">
          <div class="text-3xl font-bold ${cls}">${count}</div>
          <div class="text-xs text-slate-500 mt-1 font-medium">${label}</div>
        </div>
      `).join("")}
    </div>
  `;
}

// ============================================================================
// MODULE 2: ADVERTISER MANAGEMENT
// ============================================================================
function renderAdvertisers() {
  const { allAdvertisers, allAds, allPayments } = getSponsorArrays();
  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 class="font-bold text-lg text-slate-900">Registered Advertisers (${allAdvertisers.length})</h3>
          <p class="text-xs text-slate-500">Only businesses/institutions purchasing advertisements require accounts. Normal app users browse free without login.</p>
        </div>
        <a href="advertiser.html" target="_blank" class="px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">
          Open Advertiser Portal &nearr;
        </a>
      </div>
      ${allAdvertisers.length === 0 ? `
        <div class="p-8 text-center rounded-2xl bg-slate-50 border border-dashed border-slate-300 text-sm text-slate-500">
          No advertiser accounts registered yet. Businesses can register from the Android App ("Advertise With Us") or <code>advertiser.html</code>.
        </div>
      ` : `
        <div class="overflow-x-auto">
          <table class="w-full text-xs text-left border-collapse">
            <thead>
              <tr class="border-b border-slate-200 text-slate-500 uppercase">
                <th class="py-2.5 px-3">Business / Owner</th>
                <th class="py-2.5 px-3">Contact &amp; Address</th>
                <th class="py-2.5 px-3">Ads</th>
                <th class="py-2.5 px-3">Verified Paid</th>
                <th class="py-2.5 px-3">Status</th>
                <th class="py-2.5 px-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              ${allAdvertisers.map(adv => {
                const ads = allAds.filter(a => a.advertiserId === adv.id);
                const active = ads.filter(a => a.effectiveStatus === "ACTIVE").length;
                const paid = allPayments
                  .filter(p => p.advertiserId === adv.id && String(p.status).toUpperCase() === "VERIFIED")
                  .reduce((s, p) => s + Number(p.amountBdt || 0), 0);
                const isActive = String(adv.status || "ACTIVE").toUpperCase() === "ACTIVE";
                return `
                  <tr class="hover:bg-slate-50">
                    <td class="py-3 px-3">
                      <div class="font-bold text-slate-900">${esc(adv.businessName)}</div>
                      <div class="text-slate-500">Owner: ${esc(adv.ownerName)} &bull; ID: <code>${esc(adv.id)}</code></div>
                      <div class="text-[11px] text-slate-400">Joined: ${formatDateOnly(adv.createdAt)}</div>
                    </td>
                    <td class="py-3 px-3">
                      <div class="font-semibold text-slate-800">${esc(adv.mobile)}</div>
                      ${adv.email ? `<div class="text-slate-500">${esc(adv.email)}</div>` : ""}
                      <div class="text-slate-500 max-w-xs truncate">${esc(adv.address)}</div>
                    </td>
                    <td class="py-3 px-3">
                      <span class="font-bold text-emerald-700">${active} active</span> / ${ads.length} total
                    </td>
                    <td class="py-3 px-3 font-bold text-sky-800">&#2547;${paid.toLocaleString()}</td>
                    <td class="py-3 px-3">${statusBadgeHtml(isActive ? "ACTIVE" : "SUSPENDED")}</td>
                    <td class="py-3 px-3 text-right space-x-1.5 whitespace-nowrap">
                      <button onclick="filterAdsByAdvertiser('${esc(adv.id)}')" class="px-2.5 py-1.5 rounded-lg bg-sky-50 text-sky-700 font-semibold hover:bg-sky-100">View Ads</button>
                      <button onclick="toggleAdvertiserStatus('${esc(adv.id)}')" class="px-2.5 py-1.5 rounded-lg ${isActive ? "bg-amber-50 text-amber-800 hover:bg-amber-100" : "bg-emerald-50 text-emerald-800 hover:bg-emerald-100"} font-semibold">
                        ${isActive ? "Suspend" : "Activate"}
                      </button>
                      <button onclick="deleteAdvertiser('${esc(adv.id)}')" class="px-2.5 py-1.5 rounded-lg bg-rose-50 text-rose-700 font-semibold hover:bg-rose-100">Delete</button>
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

function filterAdsByAdvertiser(advId) {
  adFilterState.advertiserId = advId;
  switchTab("advertisements");
}

async function toggleAdvertiserStatus(advId) {
  const cur = dbState.advertisers?.[advId];
  if (!cur) return;
  const nextStatus = String(cur.status || "ACTIVE").toUpperCase() === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
  await saveNode("advertisers", advId, { ...cur, status: nextStatus, updatedAt: Date.now() });
  await writeAuditLog("ADVERTISER_STATUS", "ADVERTISER", advId, `Changed ${cur.businessName} status to ${nextStatus}`);
}

async function deleteAdvertiser(advId) {
  const cur = dbState.advertisers?.[advId];
  if (!confirm(`Delete advertiser account "${cur?.businessName || advId}"?`)) return;
  await deleteNode("advertisers", advId);
  await writeAuditLog("DELETE_ADVERTISER", "ADVERTISER", advId, `Deleted advertiser ${cur?.businessName || advId}`);
}

// ============================================================================
// MODULE 3: DIRECT SPONSOR ADVERTISEMENT PACKAGE MANAGEMENT (FIXED RATE ONLY)
// ============================================================================
function renderAdPackages() {
  const { allPackages } = getSponsorArrays();
  const pk = editing.pkg ? { id: editing.pkg, ...(dbState.ad_packages?.[editing.pkg] || {}) } : null;

  return `
    <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
      <div class="xl:col-span-5 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="font-bold text-base text-sky-900">${pk ? "Edit Sponsor Package" : "Create Sponsor Package"}</h3>
            <p class="text-xs text-slate-500">Only active packages created here appear in the Mobile App &amp; Website for purchase.</p>
          </div>
          ${pk ? `<button type="button" onclick="editing={};renderTab()" class="text-xs font-semibold text-slate-500">Cancel</button>` : ""}
        </div>

        <div class="p-3 mb-4 rounded-xl bg-teal-50 border border-teal-200 text-xs text-teal-900">
          <strong>Fixed Package Pricing Rule:</strong> Total Package Price = <strong>Fixed Package Rate</strong>. Never multiplied by duration days. Duration only controls campaign expiry.
        </div>

        <form onsubmit="submitAdPackage(event)" class="space-y-3">
          <input type="hidden" id="pkg_id" value="${esc(pk?.id || "")}" />
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Package Name *</label>
            <input id="pkg_name" required value="${esc(pk?.name || "")}" placeholder="e.g. Banner All Screen" class="input bg-white" />
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Format Preset *</label>
              <select id="pkg_placement" onchange="onPkgPlacementChange(this.value)" class="input bg-white text-xs">
                ${PLACEMENT_TYPES.map(p => `<option value="${p.key}" ${(pk?.placementType || "BANNER_300x50") === p.key ? "selected" : ""}>${p.label}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Advertisement Type *</label>
              <select id="pkg_ad_type" class="input bg-white text-xs">
                ${PACKAGE_AD_TYPES.map(t => `<option value="${t.key}" ${(pk?.adType || "BANNER") === t.key ? "selected" : ""}>${t.label}</option>`).join("")}
              </select>
            </div>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Advertisement Size Label *</label>
              <input id="pkg_size_label" required value="${esc(pk?.sizeLabel || "300×50 px")}" placeholder="300×50 px / Responsive / Video" class="input bg-white text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Advertisement Placement *</label>
              <select id="pkg_ad_placement" class="input bg-white text-xs">
                ${PACKAGE_PLACEMENTS.map(pl => `<option value="${pl.key}" ${(pk?.adPlacement || "ALL") === pl.key ? "selected" : ""}>${pl.label}</option>`).join("")}
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Width (px)</label>
              <input id="pkg_w" type="number" min="0" max="2000" value="${esc(pk?.widthPx ?? 300)}" class="input bg-white text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Height (px)</label>
              <input id="pkg_h" type="number" min="0" max="2000" value="${esc(pk?.heightPx ?? 50)}" class="input bg-white text-xs" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Fixed Package Rate (BDT &#2547;) *</label>
              <input id="pkg_price" type="number" min="0" required value="${esc(pk?.priceBdt ?? 200)}" class="input bg-white font-bold" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Duration (Days) *</label>
              <input id="pkg_days" type="number" min="1" max="3650" required value="${esc(pk?.durationDays || 30)}" class="input bg-white" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Package Description</label>
            <textarea id="pkg_desc" rows="2" placeholder="Describe ad placement & campaign benefits..." class="input bg-white">${esc(pk?.description || "")}</textarea>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Optional Package Image (HTTPS URL or Upload)</label>
            <input id="pkg_image_url" value="${esc(pk?.packageImageUrl || "")}" placeholder="https://.../package-preview.jpg" class="input bg-white text-xs mb-1.5" />
            <input type="file" accept="image/png,image/jpeg,image/webp" onchange="handleValidatedAdImageUpload(event, 'pkg_image_url')" class="text-[11px] text-slate-600 w-full" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Package Status *</label>
            <select id="pkg_status" class="input bg-white">
              <option value="ACTIVE" ${(pk?.status || "ACTIVE") === "ACTIVE" ? "selected" : ""}>ACTIVE (Visible &amp; Purchasable)</option>
              <option value="INACTIVE" ${pk?.status === "INACTIVE" || pk?.status === "DISABLED" ? "selected" : ""}>INACTIVE (Hidden from Sponsors)</option>
            </select>
          </div>
          <button type="submit" class="w-full py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-sm">
            ${pk ? "Update Package" : "Create Sponsor Package"}
          </button>
        </form>
      </div>

      <div class="xl:col-span-7 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
          <div>
            <h3 class="font-bold text-base text-slate-900">Sponsor Advertisement Packages (${allPackages.length})</h3>
            <p class="text-xs text-slate-500">Create, edit, duplicate, activate/deactivate, or delete unlimited packages.</p>
          </div>
          <button type="button" onclick="seedStarterPackages()" class="px-3 py-1.5 rounded-lg bg-teal-50 text-teal-800 border border-teal-200 hover:bg-teal-100 text-xs font-bold">
            + Load 4 Default Demo Packages
          </button>
        </div>
        ${allPackages.length === 0 ? `
          <div class="p-8 text-center rounded-2xl bg-slate-50 border border-dashed border-slate-300 text-sm text-slate-500">
            No packages created yet. Click <strong>+ Load 4 Default Demo Packages</strong> or create a package on the left.
          </div>
        ` : `
          <div class="space-y-3">
            ${allPackages.map(item => `
              <div class="p-4 rounded-2xl border border-slate-200 bg-slate-50/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div class="flex items-start gap-3">
                  ${isValidSponsorImageSrc(item.packageImageUrl) ? `<img src="${esc(item.packageImageUrl)}" class="w-16 h-12 rounded-xl object-cover border border-slate-200 flex-shrink-0" alt="" />` : ""}
                  <div>
                    <div class="flex flex-wrap items-center gap-2">
                      <strong class="text-sm text-slate-900">${esc(item.name)}</strong>
                      ${statusBadgeHtml(item.status || "ACTIVE")}
                    </div>
                    <div class="text-xs text-sky-900 font-bold mt-1">
                      &#2547;${Number(item.priceBdt || 0).toLocaleString()} Fixed Rate &bull; ${esc(item.durationDays)} Days &bull; ${esc(item.sizeLabel || `${item.widthPx}×${item.heightPx} px`)}
                    </div>
                    <div class="text-[11px] text-slate-500 mt-0.5">
                      Type: ${esc(item.adType || "BANNER")} &bull; Placement: ${esc(item.adPlacement || placementLabel(item.placementType))}${item.description ? " &bull; " + esc(item.description) : ""}
                    </div>
                  </div>
                </div>
                <div class="flex flex-wrap gap-1.5">
                  <button onclick="togglePackageStatus('${esc(item.id)}')" class="px-2.5 py-1.5 rounded-lg bg-amber-50 text-amber-800 text-xs font-semibold hover:bg-amber-100">
                    ${String(item.status).toUpperCase() === "ACTIVE" ? "Deactivate" : "Activate"}
                  </button>
                  <button onclick="duplicatePackage('${esc(item.id)}')" class="px-2.5 py-1.5 rounded-lg bg-indigo-50 text-indigo-800 text-xs font-semibold hover:bg-indigo-100">Duplicate</button>
                  <button onclick="editing.pkg='${esc(item.id)}';renderTab()" class="px-2.5 py-1.5 rounded-lg bg-sky-100 text-sky-800 text-xs font-semibold hover:bg-sky-200">Edit</button>
                  <button onclick="deletePackage('${esc(item.id)}')" class="px-2.5 py-1.5 rounded-lg bg-rose-100 text-rose-700 text-xs font-semibold hover:bg-rose-200">Delete</button>
                </div>
              </div>
            `).join("")}
          </div>
        `}
      </div>
    </div>
  `;
}

function onPkgPlacementChange(val) {
  const found = PLACEMENT_TYPES.find(p => p.key === val);
  if (!found || val === "BANNER_CUSTOM") return;
  const w = document.getElementById("pkg_w");
  const h = document.getElementById("pkg_h");
  const adType = document.getElementById("pkg_ad_type");
  const sizeLbl = document.getElementById("pkg_size_label");
  const adPlace = document.getElementById("pkg_ad_placement");
  if (w) w.value = found.w;
  if (h) h.value = found.h;
  if (adType && found.adType) adType.value = found.adType;
  if (sizeLbl && found.sizeLabel) sizeLbl.value = found.sizeLabel;
  if (adPlace && found.adPlacement) adPlace.value = found.adPlacement;
}

async function submitAdPackage(e) {
  e.preventDefault();
  const id = document.getElementById("pkg_id").value.trim() || uid("pkg");
  const pkgImg = (document.getElementById("pkg_image_url")?.value || "").trim();
  if (pkgImg && !isValidSponsorImageSrc(pkgImg)) {
    return toast("Package image must be a valid HTTPS image URL or PNG/JPEG/WebP upload.");
  }
  const payload = {
    id,
    name: document.getElementById("pkg_name").value.trim(),
    placementType: document.getElementById("pkg_placement").value,
    adType: document.getElementById("pkg_ad_type")?.value || "BANNER",
    sizeLabel: (document.getElementById("pkg_size_label")?.value || "").trim() || "Responsive",
    adPlacement: document.getElementById("pkg_ad_placement")?.value || "ALL",
    widthPx: Math.max(0, parseInt(document.getElementById("pkg_w").value, 10) || 0),
    heightPx: Math.max(0, parseInt(document.getElementById("pkg_h").value, 10) || 0),
    durationDays: Math.max(1, parseInt(document.getElementById("pkg_days").value, 10) || 30),
    priceBdt: Math.max(0, parseInt(document.getElementById("pkg_price").value, 10) || 0),
    description: document.getElementById("pkg_desc").value.trim(),
    packageImageUrl: pkgImg,
    status: document.getElementById("pkg_status").value || "ACTIVE",
    updatedAt: Date.now()
  };
  await saveNode("ad_packages", id, payload);
  await writeAuditLog(editing.pkg ? "EDIT_PACKAGE" : "CREATE_PACKAGE", "PACKAGE", id, `${payload.name} (${payload.sizeLabel}, ${payload.durationDays}d, Fixed BDT ${payload.priceBdt})`);
  editing = {};
  renderTab();
}

async function duplicatePackage(id) {
  const cur = dbState.ad_packages?.[id];
  if (!cur) return;
  const newId = uid("pkg");
  const copy = {
    ...cur,
    id: newId,
    name: `${cur.name} (Copy)`,
    updatedAt: Date.now()
  };
  await saveNode("ad_packages", newId, copy);
  await writeAuditLog("DUPLICATE_PACKAGE", "PACKAGE", newId, `Duplicated package ${cur.name}`);
}

async function togglePackageStatus(id) {
  const cur = dbState.ad_packages?.[id];
  if (!cur) return;
  const next = String(cur.status || "ACTIVE").toUpperCase() === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  await saveNode("ad_packages", id, { ...cur, status: next, updatedAt: Date.now() });
  await writeAuditLog("TOGGLE_PACKAGE", "PACKAGE", id, `Set ${cur.name} to ${next}`);
}

async function deletePackage(id) {
  const cur = dbState.ad_packages?.[id];
  await deleteNode("ad_packages", id);
  await writeAuditLog("DELETE_PACKAGE", "PACKAGE", id, `Deleted package ${cur?.name || id}`);
}

async function seedStarterPackages() {
  const now = Date.now();
  const batch = {
    pkg_banner_all_300x50: {
      id: "pkg_banner_all_300x50",
      name: "Banner All Screen",
      adType: "BANNER",
      sizeLabel: "300×50 px",
      adPlacement: "ALL",
      widthPx: 300,
      heightPx: 50,
      durationDays: 30,
      priceBdt: 200,
      placementType: "BANNER_300x50",
      packageImageUrl: "",
      description: "Compact 300×50 banner displayed across all app screens (online & offline cached). Fixed package price ৳200 for 30 days.",
      status: "ACTIVE",
      updatedAt: now
    },
    pkg_native_all_300x200: {
      id: "pkg_native_all_300x200",
      name: "Native Banner All Screen",
      adType: "NATIVE_BANNER",
      sizeLabel: "300×200 px",
      adPlacement: "ALL",
      widthPx: 300,
      heightPx: 200,
      durationDays: 30,
      priceBdt: 300,
      placementType: "BANNER_300x200",
      packageImageUrl: "",
      description: "Rich 300×200 native banner displayed between directory cards across all screens. Fixed package price ৳300 for 30 days.",
      status: "ACTIVE",
      updatedAt: now
    },
    pkg_popup_app_open: {
      id: "pkg_popup_app_open",
      name: "Popup App Open",
      adType: "POPUP",
      sizeLabel: "Responsive",
      adPlacement: "APP_OPEN",
      widthPx: 320,
      heightPx: 200,
      durationDays: 30,
      priceBdt: 180,
      placementType: "POPUP_AD",
      packageImageUrl: "",
      description: "High-visibility responsive popup shown when users open the app. Fixed package price ৳180 for 30 days.",
      status: "ACTIVE",
      updatedAt: now
    },
    pkg_reward_video_doctor: {
      id: "pkg_reward_video_doctor",
      name: "Reward Video – Doctor Click",
      adType: "REWARD_VIDEO",
      sizeLabel: "Video",
      adPlacement: "DOCTOR_CLICK",
      widthPx: 360,
      heightPx: 200,
      durationDays: 30,
      priceBdt: 600,
      placementType: "REWARD_VIDEO_DOCTOR_CLICK",
      packageImageUrl: "",
      description: "Direct sponsor reward video shown when users click a doctor profile. Fixed package price ৳600 for 30 days.",
      status: "ACTIVE",
      updatedAt: now
    }
  };
  await patchCollectionNodes("ad_packages", batch, "4 default demo packages loaded! You can edit rates, durations, or duplicate anytime.");
  await writeAuditLog("SEED_PACKAGES", "PACKAGE", "demo_4", "Loaded 4 default Direct Sponsor demo packages");
}

// ============================================================================
// MODULE 4, 6, 7, 8, 9: ADVERTISEMENT MANAGEMENT + ADMIN DIRECT AD UPLOAD
// ============================================================================
function renderAdvertisements(filterMode = "ALL") {
  const { allAds, allPackages, allAdvertisers, adStatsMap = {} } = computeDirectSponsorMetrics();
  const sp = editing.ad ? { id: editing.ad, ...(dbState.sponsors?.[editing.ad] || {}) } : null;
  const advMap = Object.fromEntries(allAdvertisers.map(a => [a.id, a]));

  let list = allAds;
  if (filterMode === "ACTIVE") list = allAds.filter(a => a.effectiveStatus === "ACTIVE");
  else if (filterMode === "PENDING") list = allAds.filter(a => ["PENDING", "PAYMENT_PENDING", "PENDING_APPROVAL", "PENDING_PAYMENT"].includes(a.effectiveStatus));
  else if (filterMode === "EXPIRED") list = allAds.filter(a => a.effectiveStatus === "EXPIRED");
  else if (filterMode === "RENEWALS") list = allAds.filter(a => Boolean(a.renewalOfAdId));

  if (adFilterState.advertiserId) {
    list = list.filter(a => a.advertiserId === adFilterState.advertiserId);
  }
  if (adFilterState.search.trim()) {
    const q = adFilterState.search.trim().toLowerCase();
    list = list.filter(a =>
      (a.name || "").toLowerCase().includes(q) ||
      (a.promoTitle || "").toLowerCase().includes(q) ||
      (a.id || "").toLowerCase().includes(q) ||
      (a.packageName || "").toLowerCase().includes(q)
    );
  }

  const targetPagesRaw = sp?.targetPages || "ALL";
  const isAllPages = !targetPagesRaw || targetPagesRaw.toUpperCase() === "ALL";
  const selectedPages = isAllPages ? [] : targetPagesRaw.split(",").map(s => s.trim().toLowerCase()).filter(Boolean);
  const previewAd = sp || allAds[0] || {
    name: "Sample Healthcare Sponsor",
    promoTitle: "20% Off Full Diagnostic Package",
    description: "Trusted medical diagnostics & pharmacy services across Bangladesh.",
    widthPx: 300, heightPx: 200, contactNumber: "+8801700000000"
  };

  return `
    ${filterMode === "ALL" ? `
      <!-- ADMIN DIRECT AD UPLOAD & EDIT FORM -->
      <div class="grid grid-cols-1 xl:grid-cols-12 gap-6 mb-6">
        <div class="xl:col-span-7 bg-white p-6 rounded-2xl border-2 border-sky-100 shadow-sm">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
            <div>
              <div class="flex items-center gap-2">
                <h3 class="font-bold text-base text-sky-900">${sp ? `Edit Advertisement (${esc(sp.id)})` : "Admin Direct Advertisement Upload & Publish"}</h3>
                <span class="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold">Instant Publish (No Payment Required)</span>
              </div>
              <p class="text-xs text-slate-500 mt-0.5">Admin can upload &amp; publish sponsor ads directly here, or edit any advertiser-submitted ad.</p>
            </div>
            ${sp ? `<button type="button" onclick="editing={};renderTab()" class="text-xs font-semibold text-slate-500">Cancel Edit</button>` : ""}
          </div>

          <form onsubmit="submitAdminDirectAd(event)" class="space-y-3.5">
            <input type="hidden" id="ad_id" value="${esc(sp?.id || "")}" />
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Business / Sponsor Name *</label>
                <input id="ad_name" required value="${esc(sp?.name || "")}" oninput="refreshAdminAdPreview()" placeholder="e.g. Popular Diagnostic Center" class="input bg-white" />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Advertisement Title *</label>
                <input id="ad_title" required value="${esc(sp?.promoTitle || "")}" oninput="refreshAdminAdPreview()" placeholder="e.g. 25% Discount on Health Checkup" class="input bg-white" />
              </div>
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Description</label>
              <textarea id="ad_desc" rows="2" oninput="refreshAdminAdPreview()" placeholder="Promotional text shown on Sponsored cards &amp; popups..." class="input bg-white">${esc(sp?.description || "")}</textarea>
            </div>

            <!-- Package Preset or Custom Placement/Size -->
            <div class="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs font-semibold text-slate-700 mb-1">Apply Dynamic Package (Optional)</label>
                  <select id="ad_pkg_select" onchange="applyPackageToAdminAd(this.value)" class="input bg-white text-xs">
                    <option value="">-- Admin Direct / Custom --</option>
                    ${allPackages.map(pk => `<option value="${esc(pk.id)}" ${sp?.packageId === pk.id ? "selected" : ""}>${esc(pk.name)} (${pk.widthPx}x${pk.heightPx}, ${pk.durationDays}d, &#2547;${pk.priceBdt})</option>`).join("")}
                  </select>
                </div>
                <div>
                  <label class="block text-xs font-semibold text-slate-700 mb-1">Placement Type *</label>
                  <select id="ad_placement" class="input bg-white text-xs">
                    ${PLACEMENT_TYPES.map(p => `<option value="${p.key}" ${(sp?.placementType || "ALL") === p.key ? "selected" : ""}>${p.label}</option>`).join("")}
                  </select>
                </div>
              </div>
              <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                <div>
                  <label class="block text-[11px] font-semibold text-slate-600 mb-1">Width (px)</label>
                  <input id="ad_w" type="number" min="100" max="2000" value="${esc(sp?.widthPx || 300)}" class="input bg-white text-xs" />
                </div>
                <div>
                  <label class="block text-[11px] font-semibold text-slate-600 mb-1">Height (px)</label>
                  <input id="ad_h" type="number" min="50" max="2000" value="${esc(sp?.heightPx || 200)}" class="input bg-white text-xs" />
                </div>
                <div>
                  <label class="block text-[11px] font-semibold text-slate-600 mb-1">Duration (Days) *</label>
                  <input id="ad_days" type="number" min="1" max="3650" required value="${esc(sp?.durationDays || 30)}" class="input bg-white text-xs" />
                </div>
                <div>
                  <label class="block text-[11px] font-semibold text-slate-600 mb-1">Priority (1-100)</label>
                  <input id="ad_priority" type="number" min="1" max="100" value="${esc(sp?.displayPriority || 50)}" class="input bg-white text-xs" />
                </div>
              </div>
            </div>

            <!-- Validated Image Uploads (PNG/JPEG/WebP <= 3MB) or HTTPS URL -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Ad Banner Image (Upload or HTTPS URL) *</label>
                <input id="ad_banner" required value="${esc(sp?.bannerImageUrl || "")}" oninput="refreshAdminAdPreview()" placeholder="https://.../banner.jpg" class="input bg-white text-xs mb-1.5" />
                <input type="file" accept="image/png,image/jpeg,image/webp" onchange="handleValidatedAdImageUpload(event, 'ad_banner', 'ad_img_meta')" class="text-[11px] text-slate-600 w-full" />
                <div id="ad_img_meta" class="text-[11px] text-teal-700 font-semibold mt-0.5"></div>
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Sponsor Logo (Optional Upload or HTTPS URL)</label>
                <input id="ad_logo" value="${esc(sp?.logoUrl || "")}" oninput="refreshAdminAdPreview()" placeholder="https://.../logo.png" class="input bg-white text-xs mb-1.5" />
                <input type="file" accept="image/png,image/jpeg,image/webp" onchange="handleValidatedAdImageUpload(event, 'ad_logo')" class="text-[11px] text-slate-600 w-full" />
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Website / Social URL (Optional)</label>
                <input id="ad_web" type="url" value="${esc(sp?.websiteUrl || "")}" placeholder="https://example.com" class="input bg-white text-xs" />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Contact Number (Works Offline)</label>
                <input id="ad_phone" value="${esc(sp?.contactNumber || "")}" oninput="refreshAdminAdPreview()" placeholder="+8801XXXXXXXXX" class="input bg-white text-xs" />
              </div>
            </div>

            <!-- Target Screen Placements (Canonical 37+ IDs) & Size Format -->
            <div class="p-3.5 rounded-xl bg-teal-50/50 border border-teal-200 space-y-2.5">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-xs font-bold text-teal-900">Target Screen Placement IDs (Check 1 or more)</span>
                <select id="ad_size_key" class="px-2.5 py-1 rounded-lg border border-teal-300 bg-white text-xs font-bold">
                  ${AD_SIZE_FORMATS.map(f => `<option value="${f.key}" ${(sp?.adSizeKey || "300x200") === f.key ? "selected" : ""}>${f.label} (${f.format})</option>`).join("")}
                </select>
              </div>
              <div class="max-h-40 overflow-y-auto grid grid-cols-1 sm:grid-cols-2 gap-1.5 pr-1">
                ${(() => {
                  const spPids = String(sp?.placementIds || "").split(",").map(s => s.trim().toUpperCase()).filter(Boolean);
                  return getMergedRateCards().map(rc => `
                    <label class="flex items-center gap-1.5 px-2 py-1 rounded-lg bg-white border border-slate-200 text-[11px] cursor-pointer">
                      <input type="checkbox" name="ad_placement_id_cb" value="${esc(rc.placementId)}" ${spPids.includes(rc.placementId.toUpperCase()) ? "checked" : ""} class="accent-teal-600" />
                      <span class="font-bold text-sky-900">${esc(rc.placementId)}</span>
                      <span class="truncate text-slate-600">${esc(rc.placementNameEn)}</span>
                    </label>
                  `).join("");
                })()}
              </div>
            </div>

            <!-- Target Pages -->
            <div class="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs font-bold text-slate-700">Target App Pages (Legacy Filter)</span>
                <label class="flex items-center gap-1.5 text-xs font-semibold text-sky-800 cursor-pointer">
                  <input id="ad_pages_all" type="checkbox" ${isAllPages ? "checked" : ""} onchange="document.getElementById('ad_specific_pages').classList.toggle('opacity-40', this.checked);document.getElementById('ad_specific_pages').classList.toggle('pointer-events-none', this.checked)" class="w-4 h-4 accent-sky-600" />
                  All Pages
                </label>
              </div>
              <div id="ad_specific_pages" class="flex flex-wrap gap-2 ${isAllPages ? "opacity-40 pointer-events-none" : ""}">
                ${SPONSOR_PAGE_OPTIONS.map(p => `
                  <label class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-xs cursor-pointer">
                    <input type="checkbox" name="ad_page_cb" value="${p.key}" ${selectedPages.includes(p.key) ? "checked" : ""} class="accent-teal-600" />
                    <span>${p.label}</span>
                  </label>
                `).join("")}
              </div>
            </div>

            <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
              <div class="flex items-center gap-2">
                <label class="text-xs font-semibold text-slate-700">Publish Status:</label>
                <select id="ad_status" class="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-bold bg-white">
                  <option value="ACTIVE" ${(sp?.status || "ACTIVE") === "ACTIVE" ? "selected" : ""}>ACTIVE (Live Immediately)</option>
                  <option value="PENDING" ${sp?.status === "PENDING" ? "selected" : ""}>PENDING</option>
                  <option value="PAYMENT_PENDING" ${sp?.status === "PAYMENT_PENDING" || sp?.status === "PENDING_APPROVAL" || sp?.status === "PENDING_PAYMENT" ? "selected" : ""}>PAYMENT_PENDING</option>
                  <option value="PAUSED" ${sp?.status === "PAUSED" ? "selected" : ""}>PAUSED</option>
                  <option value="EXPIRED" ${sp?.status === "EXPIRED" ? "selected" : ""}>EXPIRED</option>
                  <option value="REJECTED" ${sp?.status === "REJECTED" ? "selected" : ""}>REJECTED</option>
                  <option value="CANCELLED" ${sp?.status === "CANCELLED" ? "selected" : ""}>CANCELLED</option>
                </select>
              </div>
              <button type="submit" class="px-6 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-xs shadow-sm">
                ${sp ? "Update Advertisement" : "Publish Direct Sponsor Ad"}
              </button>
            </div>
          </form>
        </div>

        <!-- Live Preview Panel -->
        <div class="xl:col-span-5 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col">
          <div class="flex items-center justify-between mb-3">
            <h3 class="font-bold text-slate-900 text-base">Live Sponsored Preview</h3>
            <div class="flex gap-1">
              ${[["banner","Banner"],["native","Native"],["popup","Popup"],["about_us","About Us"]].map(([k,lbl]) => `
                <button type="button" onclick="adFilterState.previewMode='${k}';refreshAdminAdPreview();renderTab()" class="px-2.5 py-1 rounded-lg text-[11px] font-bold ${adFilterState.previewMode === k ? "bg-sky-600 text-white" : "bg-slate-100 text-slate-600"}">${lbl}</button>
              `).join("")}
            </div>
          </div>
          <p class="text-[11px] text-slate-500 mb-3">Every ad is clearly labeled <strong>Sponsored</strong> and cached offline on Android devices.</p>
          <div id="adminAdPreviewBox" class="flex-1 flex items-center justify-center p-4 rounded-2xl bg-slate-100 border border-dashed border-slate-300">
            ${renderSponsorAdPreview(previewAd, adFilterState.previewMode)}
          </div>
        </div>
      </div>
    ` : ""}

    <!-- Filtered Advertisements Table/List -->
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 class="font-bold text-base text-slate-900">
            ${filterMode === "ACTIVE" ? "Active Advertisements"
              : filterMode === "PENDING" ? "Pending Advertisements (Awaiting Payment / Verification)"
              : filterMode === "EXPIRED" ? "Expired Advertisements"
              : filterMode === "RENEWALS" ? "Renewal Requests & Renewed Ads"
              : "All Advertisements"} (${list.length})
          </h3>
          ${adFilterState.advertiserId ? `
            <div class="text-xs text-sky-700 mt-0.5">
              Filtered by Advertiser: <strong>${esc(advMap[adFilterState.advertiserId]?.businessName || adFilterState.advertiserId)}</strong>
              <button onclick="adFilterState.advertiserId='';renderTab()" class="ml-2 underline text-rose-600">Clear</button>
            </div>
          ` : ""}
        </div>
        <input value="${esc(adFilterState.search)}" oninput="adFilterState.search=this.value;renderTab()" placeholder="Search title, sponsor, package..." class="px-3 py-2 rounded-xl border border-slate-200 text-xs w-64" />
      </div>

      ${list.length === 0 ? `
        <div class="p-8 text-center rounded-2xl bg-slate-50 border border-dashed border-slate-300 text-sm text-slate-500">
          No advertisements in this category yet.
        </div>
      ` : `
        <div class="space-y-3">
          ${list.map(ad => {
            const st = adStatsMap?.[ad.id] || { impressions: Number(ad.totalViews || 0), uniqueViews: Number(ad.uniqueViews || 0), clicks: 0 };
            const adv = advMap[ad.advertiserId];
            return `
              <div class="p-4 rounded-2xl border border-slate-200 bg-slate-50/70 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div class="flex items-start gap-3">
                  <div class="w-20 h-14 rounded-xl bg-sky-100 border border-slate-200 overflow-hidden flex-shrink-0 flex items-center justify-center">
                    ${isValidSponsorImageSrc(ad.bannerImageUrl || ad.logoUrl)
                      ? `<img src="${esc(ad.bannerImageUrl || ad.logoUrl)}" class="w-full h-full object-cover" alt="" />`
                      : `<span class="text-xs font-bold text-sky-800">AD</span>`}
                  </div>
                  <div class="space-y-0.5">
                    <div class="flex flex-wrap items-center gap-1.5">
                      <strong class="text-sm text-slate-900">${esc(ad.promoTitle || ad.name)}</strong>
                      ${statusBadgeHtml(ad.effectiveStatus)}
                      <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${ad.submittedBy === "ADMIN" ? "bg-indigo-100 text-indigo-800" : "bg-teal-100 text-teal-800"}">
                        ${ad.submittedBy === "ADMIN" ? "Admin Direct" : "Advertiser"}
                      </span>
                      ${ad.renewalOfAdId ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-purple-800">Renewal of ${esc(ad.renewalOfAdId)}</span>` : ""}
                    </div>
                    <div class="text-xs text-slate-700 font-medium">
                      ${esc(ad.name)}${adv ? ` (Owner: ${esc(adv.ownerName)}, ${esc(adv.mobile)})` : ""}
                    </div>
                    <div class="text-[11px] text-slate-500">
                      ${esc(ad.packageName || placementLabel(ad.placementType))} &bull; ${ad.widthPx || 300}&times;${ad.heightPx || 200}px &bull; ${ad.durationDays || 30}d
                      ${ad.priceBdt ? ` &bull; &#2547;${Number(ad.priceBdt).toLocaleString()}` : ""}
                      &bull; Priority ${ad.displayPriority || 10}
                    </div>
                    ${ad.placementIds ? `
                      <div class="flex flex-wrap gap-1 pt-0.5">
                        ${String(ad.placementIds).split(",").map(s => s.trim()).filter(Boolean).map(pid => `<span class="px-1.5 py-0.5 rounded bg-sky-100 text-sky-900 font-bold text-[10px]">${esc(pid)}</span>`).join("")}
                        ${ad.adSizeKey ? `<span class="px-1.5 py-0.5 rounded bg-teal-100 text-teal-900 font-bold text-[10px]">${esc(ad.adSizeKey)}</span>` : ""}
                      </div>
                    ` : ""}
                    <div class="text-[11px] text-slate-600">
                      ${ad.effectiveStatus === "ACTIVE"
                        ? `<strong class="text-emerald-700">${ad.remDays} day(s) left</strong> &bull; Activated: ${formatDateOnly(ad.activatedAt)} &rarr; Expires: ${formatDateOnly(ad.expiryAt)}`
                        : `Start: ${formatDateOnly(ad.activatedAt)} &bull; Expiry: ${formatDateOnly(ad.expiryAt)}`}
                      &bull; Views: <strong>${st.impressions.toLocaleString()}</strong> (Unique: ${st.uniqueViews.toLocaleString()}, Clicks: ${st.clicks.toLocaleString()})
                    </div>
                  </div>
                </div>
                <div class="flex flex-wrap items-center gap-1.5">
                  ${ad.effectiveStatus !== "ACTIVE" ? `
                    <button onclick="adminActivateAd('${esc(ad.id)}')" class="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold">Approve / Activate</button>
                  ` : `
                    <button onclick="adminSetAdStatus('${esc(ad.id)}','PAUSED')" class="px-3 py-1.5 rounded-lg bg-amber-100 text-amber-900 hover:bg-amber-200 text-xs font-semibold">Pause</button>
                    <button onclick="adminSetAdStatus('${esc(ad.id)}','EXPIRED')" class="px-3 py-1.5 rounded-lg bg-orange-100 text-orange-900 hover:bg-orange-200 text-xs font-semibold">Expire Now</button>
                  `}
                  ${["PENDING", "PAYMENT_PENDING", "PENDING_APPROVAL", "PENDING_PAYMENT"].includes(ad.effectiveStatus) ? `
                    <button onclick="adminSetAdStatus('${esc(ad.id)}','REJECTED')" class="px-3 py-1.5 rounded-lg bg-rose-100 text-rose-800 hover:bg-rose-200 text-xs font-semibold">Reject</button>
                  ` : ""}
                  ${ad.effectiveStatus !== "CANCELLED" ? `
                    <button onclick="adminSetAdStatus('${esc(ad.id)}','CANCELLED')" class="px-3 py-1.5 rounded-lg bg-slate-200 text-slate-700 hover:bg-slate-300 text-xs font-semibold">Cancel</button>
                  ` : ""}
                  <button onclick="editing.ad='${esc(ad.id)}';switchTabKeepEdit('advertisements')" class="px-3 py-1.5 rounded-lg bg-sky-100 text-sky-800 hover:bg-sky-200 text-xs font-semibold">Edit</button>
                  <button onclick="deleteSponsorAd('${esc(ad.id)}')" class="px-3 py-1.5 rounded-lg bg-rose-50 text-rose-700 hover:bg-rose-100 text-xs font-semibold">Delete</button>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      `}
    </div>
  `;
}

function switchTabKeepEdit(tabKey) {
  if (typeof syncActiveNav === "function") {
    syncActiveNav(tabKey);
  } else {
    document.querySelectorAll("#sidebarNav button[data-tab]").forEach(b => b.classList.toggle("active", b.dataset.tab === tabKey));
  }
  activeTab = tabKey;
  renderTab();
}

function applyPackageToAdminAd(pkgId) {
  const pk = dbState.ad_packages?.[pkgId];
  if (!pk) return;
  document.getElementById("ad_placement").value = pk.placementType || "BANNER_300x200";
  document.getElementById("ad_w").value = pk.widthPx || 300;
  document.getElementById("ad_h").value = pk.heightPx || 200;
  document.getElementById("ad_days").value = pk.durationDays || 30;
}

function handleValidatedAdImageUpload(event, targetInputId, metaLabelId) {
  const file = event.target.files?.[0];
  if (!file) return;
  const allowed = ["image/png", "image/jpeg", "image/webp"];
  if (!allowed.includes(file.type)) {
    toast("Invalid image format! Only PNG, JPEG, or WebP allowed.");
    event.target.value = "";
    return;
  }
  if (file.size > 3 * 1024 * 1024) {
    toast("Image exceeds 3 MB maximum size limit.");
    event.target.value = "";
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    const img = new Image();
    img.onload = () => {
      // Compress large images onto canvas for fast RTDB & offline Android caching
      const maxW = 1080;
      let w = img.naturalWidth, h = img.naturalHeight;
      let dataUrl = reader.result;
      if (w > maxW || file.size > 350000) {
        const scale = w > maxW ? maxW / w : 1;
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(w * scale);
        canvas.height = Math.round(h * scale);
        canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
        dataUrl = canvas.toDataURL("image/jpeg", 0.84);
      }
      const el = document.getElementById(targetInputId);
      if (el) el.value = dataUrl;
      if (metaLabelId) {
        const mEl = document.getElementById(metaLabelId);
        if (mEl) mEl.textContent = `Validated: ${w}×${h}px (${Math.round(file.size / 1024)} KB)`;
      }
      refreshAdminAdPreview();
      toast(`Image validated (${w}×${h}px) & attached!`);
    };
    img.onerror = () => toast("Corrupted or unreadable image file.");
    img.src = reader.result;
  };
  reader.readAsDataURL(file);
}

function renderSponsorAdPreview(sp, mode) {
  const name = esc(sp.name || "Sponsor Business");
  const title = esc(sp.promoTitle || "Promotional Title");
  const desc = esc(sp.description || "Promotional description shown to patients.");
  const hasBanner = isValidSponsorImageSrc(sp.bannerImageUrl);
  const hasLogo = isValidSponsorImageSrc(sp.logoUrl);

  if (mode === "popup") {
    return `
      <div class="w-full max-w-xs bg-white rounded-2xl shadow-lg overflow-hidden border border-slate-200">
        <div class="relative h-36 bg-gradient-to-br from-sky-800 to-teal-700 flex items-center justify-center">
          ${hasBanner ? `<img src="${esc(sp.bannerImageUrl)}" class="w-full h-full object-cover" alt="" />` : `<span class="text-white font-bold text-sm">${name}</span>`}
          <span class="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/65 text-white text-[10px] font-bold">Sponsored</span>
          <span class="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/75 text-white flex items-center justify-center text-xs font-bold">&times;</span>
        </div>
        <div class="p-4 space-y-2">
          <div class="font-bold text-sm text-slate-900">${name}</div>
          <div class="text-xs font-semibold text-sky-700">${title}</div>
          <p class="text-xs text-slate-600">${desc}</p>
          <div class="py-1.5 rounded-lg bg-sky-600 text-white text-center text-xs font-bold">Visit Sponsor</div>
        </div>
      </div>
    `;
  }
  if (mode === "native" || mode === "about_us") {
    return `
      <div class="w-full max-w-sm bg-white rounded-2xl border-2 border-teal-200 shadow-sm overflow-hidden">
        <div class="p-3 flex items-center justify-between bg-teal-50/50">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-lg bg-sky-100 overflow-hidden flex items-center justify-center">
              ${hasLogo ? `<img src="${esc(sp.logoUrl)}" class="w-full h-full object-cover" alt="" />` : `<span class="text-[10px] font-bold text-sky-800">${name.slice(0,2).toUpperCase()}</span>`}
            </div>
            <div>
              <div class="text-xs font-bold text-slate-900">${name}</div>
              <div class="text-[11px] font-semibold text-sky-800">${title}</div>
            </div>
          </div>
          <span class="px-2 py-0.5 rounded bg-teal-600 text-white text-[10px] font-bold">Sponsored</span>
        </div>
        ${hasBanner ? `<img src="${esc(sp.bannerImageUrl)}" class="w-full h-36 object-cover px-3 pt-2" alt="" />` : ""}
        <div class="p-3 space-y-2">
          <p class="text-xs text-slate-600">${desc}</p>
          <div class="flex gap-2">
            <div class="flex-1 py-1.5 rounded-lg bg-sky-600 text-white text-center text-xs font-bold">Visit Sponsor</div>
            ${sp.contactNumber ? `<div class="px-3 py-1.5 rounded-lg border border-teal-600 text-teal-700 text-xs font-bold">Call</div>` : ""}
          </div>
        </div>
      </div>
    `;
  }
  return `
    <div class="w-full bg-white rounded-xl border border-teal-300 shadow-sm p-2.5 flex items-center gap-2.5">
      <div class="w-16 h-12 rounded-lg bg-sky-100 overflow-hidden flex items-center justify-center flex-shrink-0">
        ${hasBanner ? `<img src="${esc(sp.bannerImageUrl)}" class="w-full h-full object-cover" alt="" />`
          : hasLogo ? `<img src="${esc(sp.logoUrl)}" class="w-full h-full object-cover" alt="" />`
          : `<span class="text-xs font-bold text-sky-800">AD</span>`}
      </div>
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-1.5">
          <span class="px-1.5 py-0.5 rounded bg-teal-600 text-white text-[9px] font-bold">Sponsored</span>
          <span class="text-xs font-bold text-slate-900 truncate">${name}</span>
        </div>
        <div class="text-[11px] text-slate-600 truncate">${title}</div>
      </div>
      <div class="px-3 py-1.5 rounded-lg bg-sky-600 text-white text-[11px] font-bold">Visit</div>
    </div>
  `;
}

function refreshAdminAdPreview() {
  const box = document.getElementById("adminAdPreviewBox");
  if (!box) return;
  box.innerHTML = renderSponsorAdPreview({
    name: document.getElementById("ad_name")?.value || "",
    promoTitle: document.getElementById("ad_title")?.value || "",
    description: document.getElementById("ad_desc")?.value || "",
    bannerImageUrl: document.getElementById("ad_banner")?.value || "",
    logoUrl: document.getElementById("ad_logo")?.value || "",
    contactNumber: document.getElementById("ad_phone")?.value || ""
  }, adFilterState.previewMode);
}

async function submitAdminDirectAd(e) {
  e.preventDefault();
  const existingId = document.getElementById("ad_id").value.trim();
  const prev = existingId ? (dbState.sponsors?.[existingId] || {}) : {};
  const id = existingId || uid("sp");
  const bannerImageUrl = document.getElementById("ad_banner").value.trim();
  const logoUrl = document.getElementById("ad_logo").value.trim();
  const websiteUrl = document.getElementById("ad_web").value.trim();

  if (!isValidSponsorImageSrc(bannerImageUrl)) return toast("Provide a valid HTTPS image URL or upload PNG/JPEG/WebP.");
  if (logoUrl && !isValidSponsorImageSrc(logoUrl)) return toast("Logo must be a valid HTTPS image URL or uploaded image.");
  if (!isValidWebUrl(websiteUrl)) return toast("Website URL must start with https:// or http://");

  const pkgId = document.getElementById("ad_pkg_select").value;
  const pkg = pkgId ? dbState.ad_packages?.[pkgId] : null;
  const durationDays = Math.max(1, parseInt(document.getElementById("ad_days").value, 10) || 30);
  const status = document.getElementById("ad_status").value || "ACTIVE";
  const now = Date.now();
  const allPages = document.getElementById("ad_pages_all").checked;
  let targetPages = "ALL";
  if (!allPages) {
    const picked = Array.from(document.querySelectorAll('input[name="ad_page_cb"]:checked')).map(c => c.value);
    targetPages = picked.length ? picked.join(",") : "ALL";
  }

  let activatedAt = Number(prev.activatedAt || 0);
  let expiryAt = Number(prev.expiryAt || 0);
  if (status === "ACTIVE" && (!activatedAt || expiryAt <= now)) {
    activatedAt = now;
    expiryAt = now + durationDays * 86400000;
  }

  const pickedPlacementIds = Array.from(document.querySelectorAll('input[name="ad_placement_id_cb"]:checked')).map(c => c.value);
  const placementIdsCsv = pickedPlacementIds.length ? pickedPlacementIds.join(",") : (prev.placementIds || "");
  const adSizeKey = document.getElementById("ad_size_key")?.value || prev.adSizeKey || "";
  const rcMap = Object.fromEntries(getMergedRateCards().map(r => [r.placementId.toUpperCase(), r]));
  const moduleNamesCsv = pickedPlacementIds.length
    ? Array.from(new Set(pickedPlacementIds.map(pid => rcMap[pid.toUpperCase()]?.moduleName).filter(Boolean))).join(",")
    : (prev.moduleNames || "");

  const payload = {
    ...prev,
    id,
    advertiserId: prev.advertiserId || "",
    submittedBy: prev.submittedBy || "ADMIN",
    packageId: pkgId || prev.packageId || "admin_direct",
    packageName: pkg ? pkg.name : (prev.packageName || (pickedPlacementIds.length ? `Direct Placements (${pickedPlacementIds.join("+")})` : "Admin Direct Campaign")),
    placementIds: placementIdsCsv,
    moduleNames: moduleNamesCsv,
    adSizeKey,
    widthPx: Math.max(100, parseInt(document.getElementById("ad_w").value, 10) || 300),
    heightPx: Math.max(40, parseInt(document.getElementById("ad_h").value, 10) || 200),
    durationDays,
    priceBdt: pkg ? Number(pkg.priceBdt || 0) : Number(prev.priceBdt || 0),
    placementType: document.getElementById("ad_placement").value || "ALL",
    status,
    isActive: status === "ACTIVE",
    name: document.getElementById("ad_name").value.trim(),
    sponsorName: document.getElementById("ad_name").value.trim(),
    promoTitle: document.getElementById("ad_title").value.trim(),
    title: document.getElementById("ad_title").value.trim(),
    description: document.getElementById("ad_desc").value.trim(),
    logoUrl,
    bannerImageUrl,
    websiteUrl,
    contactNumber: document.getElementById("ad_phone").value.trim(),
    targetPages,
    displayPriority: Math.max(1, Math.min(100, parseInt(document.getElementById("ad_priority").value, 10) || 50)),
    displayFrequency: Number(prev.displayFrequency || 1),
    activatedAt,
    expiryAt,
    startDate: activatedAt > 0 ? formatDateOnly(activatedAt) : "",
    endDate: expiryAt > 0 ? formatDateOnly(expiryAt) : "",
    updatedAt: now
  };

  await saveNode("sponsors", id, payload);
  await writeAuditLog(existingId ? "EDIT_AD" : "ADMIN_DIRECT_UPLOAD_AD", "SPONSOR_AD", id, `${payload.promoTitle} (${payload.name}) [${status}]`);
  editing = {};
  renderTab();
}

async function adminActivateAd(adId) {
  const cur = dbState.sponsors?.[adId];
  if (!cur) return;
  const now = Date.now();
  const days = Math.max(1, Number(cur.durationDays || 30));
  const activatedAt = now;
  const expiryAt = now + days * 86400000;
  await saveNode("sponsors", adId, {
    ...cur,
    status: "ACTIVE",
    isActive: true,
    activatedAt,
    expiryAt,
    startDate: formatDateOnly(activatedAt),
    endDate: formatDateOnly(expiryAt),
    updatedAt: now
  });
  await writeAuditLog("ACTIVATE_AD", "SPONSOR_AD", adId, `Activated "${cur.promoTitle || cur.name}" for ${days} days (expires ${formatDateOnly(expiryAt)})`);
}

async function adminSetAdStatus(adId, newStatus) {
  const cur = dbState.sponsors?.[adId];
  if (!cur) return;
  const now = Date.now();
  await saveNode("sponsors", adId, {
    ...cur,
    status: newStatus,
    isActive: newStatus === "ACTIVE",
    expiryAt: newStatus === "EXPIRED" ? now - 1000 : (cur.expiryAt || 0),
    updatedAt: now
  });
  await writeAuditLog("SET_AD_STATUS", "SPONSOR_AD", adId, `Changed "${cur.promoTitle || cur.name}" to ${newStatus}`);
}

async function deleteSponsorAd(adId) {
  const cur = dbState.sponsors?.[adId];
  await deleteNode("sponsors", adId);
  await writeAuditLog("DELETE_AD", "SPONSOR_AD", adId, `Deleted advertisement "${cur?.promoTitle || adId}"`);
}

// ============================================================================
// MODULE 5: MANUAL bKash & Nagad PAYMENT VERIFICATION
// ============================================================================
function renderPaymentVerification() {
  const { allPayments, allAdvertisers } = getSponsorArrays();
  const advMap = Object.fromEntries(allAdvertisers.map(a => [a.id, a]));
  const spMap = dbState.sponsors || {};

  // Duplicate TrxID detection across all payments
  const trxCounts = {};
  allPayments.forEach(p => {
    const t = normalizeTrxId(p.transactionIdNormalized || p.transactionId);
    if (t) trxCounts[t] = (trxCounts[t] || 0) + 1;
  });

  const filtered = allPayments.filter(p =>
    adFilterState.paymentStatus === "ALL" || String(p.status || "PENDING").toUpperCase() === adFilterState.paymentStatus
  );

  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div>
          <h3 class="font-bold text-lg text-slate-900">Manual bKash, Nagad &amp; Rocket Payment Verification</h3>
          <p class="text-xs text-slate-500">Campaigns NEVER start before Admin verifies the TrxID &amp; fixed package amount. Approval prevents duplicate TrxIDs and starts the campaign duration clock.</p>
        </div>
        <div class="flex gap-1.5">
          ${["ALL","PENDING","VERIFIED","REJECTED"].map(st => `
            <button onclick="adFilterState.paymentStatus='${st}';renderTab()" class="px-3 py-1.5 rounded-xl text-xs font-bold border ${adFilterState.paymentStatus === st ? "bg-sky-600 text-white border-sky-600" : "bg-white text-slate-600 border-slate-200"}">
              ${st} (${st === "ALL" ? allPayments.length : allPayments.filter(p => String(p.status).toUpperCase() === st).length})
            </button>
          `).join("")}
        </div>
      </div>

      ${filtered.length === 0 ? `
        <div class="p-8 text-center rounded-2xl bg-slate-50 border border-dashed border-slate-300 text-sm text-slate-500">
          No payments in "${adFilterState.paymentStatus}" state.
        </div>
      ` : `
        <div class="space-y-3">
          ${filtered.map(pay => {
            const st = String(pay.status || "PENDING").toUpperCase();
            const ad = spMap[pay.adId];
            const adv = advMap[pay.advertiserId];
            const normTrx = normalizeTrxId(pay.transactionIdNormalized || pay.transactionId);
            const isDupTrx = normTrx && trxCounts[normTrx] > 1;
            const pm = String(pay.paymentMethod || "BKASH").toUpperCase();
            const pmCls = pm === "BKASH" ? "bg-pink-100 text-pink-800" : pm === "ROCKET" ? "bg-purple-100 text-purple-800" : "bg-orange-100 text-orange-800";
            return `
              <div class="p-4 rounded-2xl border ${isDupTrx ? "border-rose-400 bg-rose-50/40" : "border-slate-200 bg-slate-50/60"} flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div class="space-y-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="px-2.5 py-0.5 rounded-lg text-xs font-extrabold ${pmCls}">
                      ${esc(pm)}
                    </span>
                    <strong class="text-base text-slate-900">&#2547;${Number(pay.amountBdt || 0).toLocaleString()} BDT</strong>
                    ${statusBadgeHtml(st)}
                    ${pay.isRenewal ? `<span class="px-2 py-0.5 rounded-full bg-purple-100 text-purple-800 text-[10px] font-bold">RENEWAL</span>` : ""}
                    ${isDupTrx ? `<span class="px-2 py-0.5 rounded-full bg-rose-600 text-white text-[10px] font-bold">DUPLICATE TrxID WARNING</span>` : ""}
                  </div>
                  <div class="text-xs text-slate-800">
                    <strong>TrxID:</strong> <code class="px-1.5 py-0.5 rounded bg-white border border-slate-200 font-bold">${esc(pay.transactionId)}</code>
                    &bull; <strong>Sender:</strong> ${esc(pay.senderMobile)}
                    &bull; <strong>Submitted:</strong> ${formatDateTime(pay.createdAt)}
                  </div>
                  <div class="text-xs text-slate-600">
                    <strong>Advertiser:</strong> ${esc(adv?.businessName || pay.advertiserId)} (${esc(adv?.mobile || "")})
                    &bull; <strong>Package:</strong> ${esc(pay.packageName || pay.packageId)}
                    &bull; <strong>Ad:</strong> ${esc(ad?.promoTitle || pay.adId)}
                  </div>
                  ${pay.adminNote ? `<div class="text-xs text-slate-500"><strong>Admin Note:</strong> ${esc(pay.adminNote)}</div>` : ""}
                  ${isValidSponsorImageSrc(pay.screenshotUrl) ? `
                    <div class="pt-1">
                      <button onclick="openImageModal('${esc(pay.screenshotUrl)}', 'Payment Screenshot — TrxID ${esc(pay.transactionId)}')" class="text-xs font-bold text-sky-700 underline">
                        View Payment Screenshot &nearr;
                      </button>
                    </div>
                  ` : ""}
                </div>
                <div class="flex flex-wrap items-center gap-2">
                  ${st === "PENDING" ? `
                    <button onclick="verifyPaymentAndActivateAd('${esc(pay.id)}')" class="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-sm">
                      Verify &amp; Activate Ad
                    </button>
                    <button onclick="rejectPayment('${esc(pay.id)}')" class="px-4 py-2 rounded-xl bg-rose-100 hover:bg-rose-200 text-rose-800 text-xs font-bold">
                      Reject
                    </button>
                  ` : `
                    <span class="text-[11px] text-slate-500">Processed: ${formatDateTime(pay.verifiedAt)}</span>
                  `}
                </div>
              </div>
            `;
          }).join("")}
        </div>
      `}
    </div>
  `;
}

async function verifyPaymentAndActivateAd(payId) {
  const pay = dbState.ad_payments?.[payId];
  if (!pay) return toast("Payment record not found.");
  if (String(pay.status).toUpperCase() === "VERIFIED") {
    return toast("This payment is already verified (duplicate approval prevented).");
  }
  const ad = dbState.sponsors?.[pay.adId];
  if (!ad) return toast("Linked advertisement not found.");

  // Check if another VERIFIED payment already used this normalized TrxID
  const normTrx = normalizeTrxId(pay.transactionIdNormalized || pay.transactionId);
  const dupVerified = Object.values(dbState.ad_payments || {}).find(
    other => other && other.id !== payId && String(other.status).toUpperCase() === "VERIFIED" &&
      normalizeTrxId(other.transactionIdNormalized || other.transactionId) === normTrx
  );
  if (dupVerified) {
    return toast(`Cannot approve: TrxID ${pay.transactionId} was already verified on payment ${dupVerified.id}!`);
  }

  const now = Date.now();
  const days = Math.max(1, Number(ad.durationDays || 30));
  const activatedAt = now;
  const expiryAt = now + days * 86400000;
  const adminActor = sessionStorage.getItem("dlbd_admin_id") || "admin";

  const updatedPay = {
    ...pay,
    status: "VERIFIED",
    verifiedBy: adminActor,
    verifiedAt: now,
    adminNote: `Verified by ${adminActor}. Ad activated until ${formatDateOnly(expiryAt)}.`
  };
  const updatedAd = {
    ...ad,
    status: "ACTIVE",
    isActive: true,
    activatedAt,
    expiryAt,
    startDate: formatDateOnly(activatedAt),
    endDate: formatDateOnly(expiryAt),
    updatedAt: now
  };

  await saveNode("ad_payments", payId, updatedPay);
  await saveNode("sponsors", pay.adId, updatedAd);
  await writeAuditLog("VERIFY_PAYMENT", "PAYMENT", payId, `Verified ${pay.paymentMethod} BDT ${pay.amountBdt} (TrxID ${pay.transactionId}) & activated Ad ${pay.adId} for ${days}d`);
  toast(`Payment verified! Advertisement is now ACTIVE for ${days} days.`);
}

async function rejectPayment(payId) {
  const pay = dbState.ad_payments?.[payId];
  if (!pay || String(pay.status).toUpperCase() !== "PENDING") return;
  const reason = prompt("Enter rejection reason for advertiser (e.g. TrxID not found / amount mismatch):", "Transaction ID could not be verified.");
  if (reason === null) return;
  const now = Date.now();
  const adminActor = sessionStorage.getItem("dlbd_admin_id") || "admin";

  await saveNode("ad_payments", payId, {
    ...pay,
    status: "REJECTED",
    verifiedBy: adminActor,
    verifiedAt: now,
    adminNote: reason.trim() || "Rejected by Admin"
  });
  const ad = dbState.sponsors?.[pay.adId];
  if (ad) {
    await saveNode("sponsors", pay.adId, { ...ad, status: "REJECTED", isActive: false, updatedAt: now });
  }
  await writeAuditLog("REJECT_PAYMENT", "PAYMENT", payId, `Rejected payment ${pay.transactionId}: ${reason}`);
  toast("Payment rejected and advertisement marked REJECTED.");
}

function openImageModal(src, title) {
  const existing = document.getElementById("imgPreviewModal");
  if (existing) existing.remove();
  const modal = document.createElement("div");
  modal.id = "imgPreviewModal";
  modal.className = "fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4";
  modal.innerHTML = `
    <div class="bg-white rounded-2xl max-w-lg w-full overflow-hidden shadow-2xl">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <strong class="text-sm text-slate-900">${esc(title)}</strong>
        <button onclick="document.getElementById('imgPreviewModal').remove()" class="px-2.5 py-1 rounded-lg bg-slate-100 text-xs font-bold">Close &times;</button>
      </div>
      <div class="p-4 bg-slate-50 flex justify-center">
        <img src="${esc(src)}" class="max-h-[70vh] rounded-xl object-contain" alt="Screenshot" />
      </div>
    </div>
  `;
  document.body.appendChild(modal);
}

// ============================================================================
// MODULE 10: bKash, Nagad & Rocket PAYMENT SETTINGS
// ============================================================================
function renderPaymentSettings() {
  const p = dbState.payment_settings || {};
  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm max-w-3xl">
      <h3 class="font-bold text-lg text-sky-900 mb-1">bKash, Nagad &amp; Rocket Payment Settings</h3>
      <p class="text-xs text-slate-500 mb-5">Displayed dynamically to sponsors inside both the Android App and Website during package booking &amp; payment submission.</p>

      <form onsubmit="submitPaymentSettings(event)" class="space-y-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 rounded-2xl bg-pink-50/50 border border-pink-200">
          <div>
            <label class="block text-xs font-bold text-pink-900 mb-1">bKash Number *</label>
            <input id="ps_bkash" value="${esc(p.bkashNumber || "")}" placeholder="01XXXXXXXXX" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-bold text-pink-900 mb-1">bKash Account Type</label>
            <select id="ps_bkash_type" class="input bg-white">
              ${["Personal", "Merchant", "Agent"].map(t => `<option value="${t}" ${(p.bkashAccountType || "Personal") === t ? "selected" : ""}>${t}</option>`).join("")}
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 rounded-2xl bg-orange-50/50 border border-orange-200">
          <div>
            <label class="block text-xs font-bold text-orange-900 mb-1">Nagad Number *</label>
            <input id="ps_nagad" value="${esc(p.nagadNumber || "")}" placeholder="01XXXXXXXXX" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-bold text-orange-900 mb-1">Nagad Account Type</label>
            <select id="ps_nagad_type" class="input bg-white">
              ${["Personal", "Merchant", "Agent"].map(t => `<option value="${t}" ${(p.nagadAccountType || "Personal") === t ? "selected" : ""}>${t}</option>`).join("")}
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 rounded-2xl bg-purple-50/50 border border-purple-200">
          <div>
            <label class="block text-xs font-bold text-purple-900 mb-1">Rocket Number *</label>
            <input id="ps_rocket" value="${esc(p.rocketNumber || "")}" placeholder="01XXXXXXXXX" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-bold text-purple-900 mb-1">Rocket Account Type</label>
            <select id="ps_rocket_type" class="input bg-white">
              ${["Personal", "Merchant", "Agent"].map(t => `<option value="${t}" ${(p.rocketAccountType || "Personal") === t ? "selected" : ""}>${t}</option>`).join("")}
            </select>
          </div>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">Payment Instructions (Bangla)</label>
          <textarea id="ps_ins_bn" rows="2" placeholder="বিকাশ, নগদ বা রকেট Send Money / Payment করে TrxID সাবমিট করুন। অ্যাডমিন যাচাইয়ের পর ক্যাম্পেইন চালু হবে।" class="input bg-white">${esc(p.instructionsBn || "")}</textarea>
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">Payment Instructions (English)</label>
          <textarea id="ps_ins_en" rows="2" placeholder="Send the fixed package amount via bKash, Nagad, or Rocket and submit your Transaction ID (TrxID). Campaign activates automatically after Admin verification." class="input bg-white">${esc(p.instructionsEn || "")}</textarea>
        </div>

        <button type="submit" class="px-6 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-sm">
          Save Payment Settings
        </button>
      </form>
    </div>
  `;
}

async function submitPaymentSettings(e) {
  e.preventDefault();
  const payload = {
    bkashNumber: document.getElementById("ps_bkash").value.trim(),
    bkashAccountType: document.getElementById("ps_bkash_type").value,
    nagadNumber: document.getElementById("ps_nagad").value.trim(),
    nagadAccountType: document.getElementById("ps_nagad_type").value,
    rocketNumber: document.getElementById("ps_rocket").value.trim(),
    rocketAccountType: document.getElementById("ps_rocket_type").value,
    instructionsBn: document.getElementById("ps_ins_bn").value.trim(),
    instructionsEn: document.getElementById("ps_ins_en").value.trim(),
    updatedAt: Date.now()
  };
  await saveNode("payment_settings", null, payload);
  await writeAuditLog("UPDATE_PAYMENT_SETTINGS", "PAYMENT_SETTINGS", "global", `Updated bKash (${payload.bkashNumber}), Nagad (${payload.nagadNumber}) & Rocket (${payload.rocketNumber})`);
}

// ============================================================================
// MODULE 11: ADVERTISEMENT VIEW REPORTS (Impressions, Unique, Daily, Package)
// ============================================================================
function renderViewReports() {
  const m = computeDirectSponsorMetrics();
  const spMap = Object.fromEntries(m.allAds.map(a => [a.id, a]));
  const pkgMap = Object.fromEntries(m.allPackages.map(p => [p.id, p]));
  const adRows = Object.values(m.adStatsMap).sort((a, b) => b.impressions - a.impressions);

  // Package-wise aggregation
  const pkgAgg = {};
  for (const row of adRows) {
    const ad = spMap[row.adId];
    const pkId = ad?.packageId || row.packageId || "admin_direct";
    const pkName = pkgMap[pkId]?.name || ad?.packageName || (pkId === "admin_direct" ? "Admin Direct Campaigns" : pkId);
    if (!pkgAgg[pkId]) pkgAgg[pkId] = { id: pkId, name: pkName, impressions: 0, uniqueViews: 0, clicks: 0, ads: 0 };
    pkgAgg[pkId].impressions += row.impressions;
    pkgAgg[pkId].uniqueViews += row.uniqueViews;
    pkgAgg[pkId].clicks += row.clicks;
    pkgAgg[pkId].ads += 1;
  }
  const pkgRows = Object.values(pkgAgg).sort((a, b) => b.impressions - a.impressions);

  return `
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div class="text-xs text-slate-500">Total Impressions</div>
        <div class="text-3xl font-extrabold text-sky-700 mt-1">${m.totalImpressions.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div class="text-xs text-slate-500">Unique Views (Per Device/Day)</div>
        <div class="text-3xl font-extrabold text-teal-700 mt-1">${m.totalUniqueViews.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div class="text-xs text-slate-500">Total CTA Clicks</div>
        <div class="text-3xl font-extrabold text-indigo-700 mt-1">${m.totalClicks.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div class="text-xs text-slate-500">Offline Synced Views</div>
        <div class="text-3xl font-extrabold text-amber-700 mt-1">${m.totalOfflineSynced.toLocaleString()}</div>
      </div>
    </div>

    <!-- Ad-wise Views -->
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm mb-6">
      <div class="flex items-center justify-between mb-3">
        <h3 class="font-bold text-base text-slate-900">Advertisement-Wise View Report</h3>
        <button onclick="exportViewReportsCsv()" class="px-3.5 py-1.5 rounded-xl bg-teal-600 text-white text-xs font-bold">Export Views CSV</button>
      </div>
      ${adRows.length === 0 ? `<div class="text-xs text-slate-400">No visible ad impressions recorded yet.</div>` : `
        <div class="overflow-x-auto">
          <table class="w-full text-xs text-left border-collapse">
            <thead>
              <tr class="border-b border-slate-200 text-slate-500 uppercase">
                <th class="py-2 px-2">Advertisement</th>
                <th class="py-2 px-2">Package</th>
                <th class="py-2 px-2">Status</th>
                <th class="py-2 px-2">Impressions</th>
                <th class="py-2 px-2">Unique Views</th>
                <th class="py-2 px-2">Clicks</th>
                <th class="py-2 px-2">CTR</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              ${adRows.map(r => {
                const ad = spMap[r.adId];
                const ctr = r.impressions > 0 ? ((r.clicks / r.impressions) * 100).toFixed(2) : "0.00";
                return `
                  <tr class="hover:bg-slate-50">
                    <td class="py-2.5 px-2 font-bold text-slate-900">${esc(ad?.promoTitle || ad?.name || r.adId)}</td>
                    <td class="py-2.5 px-2 text-slate-600">${esc(ad?.packageName || r.packageId || "Admin Direct")}</td>
                    <td class="py-2.5 px-2">${statusBadgeHtml(ad?.effectiveStatus || "ARCHIVED")}</td>
                    <td class="py-2.5 px-2 font-bold text-sky-800">${r.impressions.toLocaleString()}</td>
                    <td class="py-2.5 px-2 font-semibold text-teal-700">${r.uniqueViews.toLocaleString()}</td>
                    <td class="py-2.5 px-2">${r.clicks.toLocaleString()}</td>
                    <td class="py-2.5 px-2 font-semibold">${ctr}%</td>
                  </tr>
                `;
              }).join("")}
            </tbody>
          </table>
        </div>
      `}
    </div>

    <!-- Package-wise & Daily Reports -->
    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base text-slate-900 mb-3">Package-Wise View Report</h3>
        ${pkgRows.length === 0 ? `<div class="text-xs text-slate-400">No package view data yet.</div>` : `
          <div class="space-y-2.5">
            ${pkgRows.map(p => `
              <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs">
                <div>
                  <div class="font-bold text-slate-900">${esc(p.name)}</div>
                  <div class="text-slate-500">${p.ads} ad(s)</div>
                </div>
                <div class="text-right">
                  <div class="font-extrabold text-sky-800">${p.impressions.toLocaleString()} impr</div>
                  <div class="text-[11px] text-teal-700">${p.uniqueViews.toLocaleString()} unique &bull; ${p.clicks} clicks</div>
                </div>
              </div>
            `).join("")}
          </div>
        `}
      </div>

      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base text-slate-900 mb-3">Daily View Report</h3>
        ${m.dailyRows.length === 0 ? `<div class="text-xs text-slate-400">No daily impressions yet.</div>` : `
          <div class="overflow-x-auto max-h-80">
            <table class="w-full text-xs text-left border-collapse">
              <thead>
                <tr class="border-b border-slate-200 text-slate-500 uppercase">
                  <th class="py-2 px-2">Date</th>
                  <th class="py-2 px-2">Impressions</th>
                  <th class="py-2 px-2">Unique</th>
                  <th class="py-2 px-2">Offline Synced</th>
                  <th class="py-2 px-2">Clicks</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                ${m.dailyRows.map(d => `
                  <tr>
                    <td class="py-2 px-2 font-bold">${esc(d.date)}</td>
                    <td class="py-2 px-2 font-bold text-sky-800">${d.impressions.toLocaleString()}</td>
                    <td class="py-2 px-2 text-teal-700">${d.uniqueViews.toLocaleString()}</td>
                    <td class="py-2 px-2 text-amber-700">${d.offlineSynced.toLocaleString()}</td>
                    <td class="py-2 px-2">${d.clicks.toLocaleString()}</td>
                  </tr>
                `).join("")}
              </tbody>
            </table>
          </div>
        `}
      </div>
    </div>
  `;
}

function exportViewReportsCsv() {
  const m = computeDirectSponsorMetrics();
  const rows = Object.values(m.adStatsMap);
  if (!rows.length) return toast("No view data to export yet.");
  const lines = ["ad_id,package_id,impressions,unique_views,clicks,offline_synced"];
  rows.forEach(r => lines.push([r.adId, r.packageId, r.impressions, r.uniqueViews, r.clicks, r.offlineSynced].join(",")));
  downloadCsv(lines.join("\n"), "doctor_list_bd_sponsor_view_reports.csv");
}

// ============================================================================
// MODULE 12: DIRECT SPONSOR REVENUE REPORTS (Verified BDT Revenue Only)
// ============================================================================
function renderRevenueReports() {
  const m = computeDirectSponsorMetrics();
  const byPkg = {};
  const byDay = {};
  m.verifiedPayments.forEach(p => {
    const pk = p.packageName || p.packageId || "Other";
    if (!byPkg[pk]) byPkg[pk] = { name: pk, count: 0, amount: 0 };
    byPkg[pk].count += 1;
    byPkg[pk].amount += Number(p.amountBdt || 0);

    const day = formatDateOnly(p.verifiedAt || p.createdAt);
    if (!byDay[day]) byDay[day] = { day, bkash: 0, nagad: 0, total: 0, count: 0 };
    const amt = Number(p.amountBdt || 0);
    if (String(p.paymentMethod).toUpperCase() === "NAGAD") byDay[day].nagad += amt;
    else byDay[day].bkash += amt;
    byDay[day].total += amt;
    byDay[day].count += 1;
  });

  const pkgRevRows = Object.values(byPkg).sort((a, b) => b.amount - a.amount);
  const dayRevRows = Object.values(byDay).sort((a, b) => b.day.localeCompare(a.day));
  const pendingBdt = m.pendingPayments.reduce((s, p) => s + Number(p.amountBdt || 0), 0);

  const revPlacements = [...m.placementStats].filter(p => p.revenueBdt > 0).sort((a, b) => b.revenueBdt - a.revenueBdt);
  const revModules = Object.entries(m.moduleRevenue || {}).sort((a, b) => b[1] - a[1]);

  return `
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      <div class="bg-gradient-to-br from-emerald-700 to-teal-700 text-white p-5 rounded-2xl shadow-sm">
        <div class="text-xs text-emerald-100 font-semibold">Total Verified Revenue</div>
        <div class="text-3xl font-extrabold mt-1">&#2547;${m.verifiedRevenueBdt.toLocaleString()}</div>
        <div class="text-xs text-emerald-100 mt-1">Monthly (30d): &#2547;${m.monthlyRevenueBdt.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border-2 border-pink-200 shadow-sm">
        <div class="text-xs text-pink-700 font-bold">bKash Verified Revenue</div>
        <div class="text-2xl font-extrabold text-slate-900 mt-1">&#2547;${m.bkashRevenueBdt.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border-2 border-orange-200 shadow-sm">
        <div class="text-xs text-orange-700 font-bold">Nagad Verified Revenue</div>
        <div class="text-2xl font-extrabold text-slate-900 mt-1">&#2547;${m.nagadRevenueBdt.toLocaleString()}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-amber-200 shadow-sm">
        <div class="text-xs text-amber-800 font-bold">Pending Verification</div>
        <div class="text-2xl font-extrabold text-amber-700 mt-1">&#2547;${pendingBdt.toLocaleString()}</div>
        <div class="text-[11px] text-slate-500 mt-0.5">${m.pendingPayments.length} pending</div>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base text-slate-900 mb-3">Placement-Wise Revenue Breakdown</h3>
        ${revPlacements.length === 0 ? `<div class="text-xs text-slate-400">No placement-based revenue recorded yet.</div>` : `
          <div class="space-y-2">
            ${revPlacements.map(r => `
              <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs">
                <div>
                  <span class="px-2 py-0.5 rounded bg-sky-900 text-white font-bold text-[10px] mr-1">${esc(r.placementId)}</span>
                  <strong class="text-slate-900">${esc(r.name)}</strong>
                  <span class="text-slate-500 ml-1">(${r.bookingsCount} booking(s))</span>
                </div>
                <strong class="text-emerald-700 text-sm">&#2547;${r.revenueBdt.toLocaleString()}</strong>
              </div>
            `).join("")}
          </div>
        `}
      </div>

      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base text-slate-900 mb-3">Module-Wise Revenue Breakdown</h3>
        ${revModules.length === 0 ? `<div class="text-xs text-slate-400">No module-wise revenue recorded yet.</div>` : `
          <div class="space-y-2">
            ${revModules.map(([mod, amt]) => `
              <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs">
                <strong class="text-slate-900">${esc(MODULE_LABELS[mod] || mod)}</strong>
                <strong class="text-teal-700 text-sm">&#2547;${amt.toLocaleString()}</strong>
              </div>
            `).join("")}
          </div>
        `}
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <h3 class="font-bold text-base text-slate-900">Revenue by Booking / Package</h3>
          <button onclick="exportSponsorRevenueCsv()" class="px-3.5 py-1.5 rounded-xl bg-emerald-600 text-white text-xs font-bold">Export Revenue CSV</button>
        </div>
        ${pkgRevRows.length === 0 ? `<div class="text-xs text-slate-400">No verified package sales yet.</div>` : `
          <div class="space-y-2.5">
            ${pkgRevRows.map(r => `
              <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs">
                <div>
                  <div class="font-bold text-slate-900">${esc(r.name)}</div>
                  <div class="text-slate-500">${r.count} verified order(s)</div>
                </div>
                <div class="text-base font-extrabold text-emerald-700">&#2547;${r.amount.toLocaleString()}</div>
              </div>
            `).join("")}
          </div>
        `}
      </div>

      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <h3 class="font-bold text-base text-slate-900 mb-3">Daily Verified Revenue</h3>
        ${dayRevRows.length === 0 ? `<div class="text-xs text-slate-400">No verified daily revenue yet.</div>` : `
          <table class="w-full text-xs text-left border-collapse">
            <thead>
              <tr class="border-b border-slate-200 text-slate-500 uppercase">
                <th class="py-2 px-2">Date</th>
                <th class="py-2 px-2 text-pink-700">bKash</th>
                <th class="py-2 px-2 text-orange-700">Nagad</th>
                <th class="py-2 px-2">Orders</th>
                <th class="py-2 px-2">Total BDT</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              ${dayRevRows.map(r => `
                <tr>
                  <td class="py-2 px-2 font-bold">${esc(r.day)}</td>
                  <td class="py-2 px-2">&#2547;${r.bkash.toLocaleString()}</td>
                  <td class="py-2 px-2">&#2547;${r.nagad.toLocaleString()}</td>
                  <td class="py-2 px-2">${r.count}</td>
                  <td class="py-2 px-2 font-extrabold text-emerald-700">&#2547;${r.total.toLocaleString()}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        `}
      </div>
    </div>
  `;
}

function exportSponsorRevenueCsv() {
  const m = computeDirectSponsorMetrics();
  if (!m.verifiedPayments.length) return toast("No verified payments to export.");
  const lines = ["payment_id,verified_date,method,trx_id,sender_mobile,package,amount_bdt,is_renewal"];
  m.verifiedPayments.forEach(p => {
    lines.push([p.id, formatDateOnly(p.verifiedAt), p.paymentMethod, p.transactionId, p.senderMobile, `"${String(p.packageName||"").replace(/"/g,'""')}"`, p.amountBdt, Boolean(p.isRenewal)].join(","));
  });
  downloadCsv(lines.join("\n"), "doctor_list_bd_direct_sponsor_revenue.csv");
}

function downloadCsv(content, filename) {
  const blob = new Blob([content], { type: "text/csv" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
}

// ============================================================================
// MODULE 13: ABOUT US PAGE & GLOBAL DIRECT SPONSOR PLACEMENT SETTINGS
// ============================================================================
function renderAboutUsAdSettings() {
  const a = dbState.ad_config || {};
  const ab = dbState.about_us_ad_settings || {};
  const { activeAds = [] } = getSponsorArrays();

  return `
    <form onsubmit="submitAboutUsAndPlacementSettings(event)" class="space-y-6 max-w-4xl">
      <!-- About Us Page Dedicated Sponsor Section -->
      <div class="bg-white p-6 rounded-2xl border-2 border-teal-200 shadow-sm space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-bold text-lg text-teal-900">About Us Page Advertisement Settings</h3>
            <p class="text-xs text-slate-500">Controls the dedicated Admin-Managed Sponsor Section on the Android App's About Us screen (Online &amp; Offline Cached).</p>
          </div>
          <label class="flex items-center gap-2 text-xs font-bold text-teal-900 cursor-pointer">
            <input id="ab_enabled" type="checkbox" ${ab.enabled !== false ? "checked" : ""} class="w-5 h-5 accent-teal-600" />
            Enable Section
          </label>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Section Title (Bangla)</label>
            <input id="ab_title_bn" value="${esc(ab.sectionTitleBn || "আমাদের স্পন্সর পার্টনার")}" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Section Title (English)</label>
            <input id="ab_title_en" value="${esc(ab.sectionTitleEn || "Our Sponsor Partners")}" class="input bg-white" />
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Max Ads Shown on About Us</label>
            <input id="ab_max" type="number" min="1" max="10" value="${esc(ab.maxAds || 3)}" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Layout Style</label>
            <select id="ab_layout" class="input bg-white">
              <option value="CARDS" ${(ab.layoutStyle || "CARDS") === "CARDS" ? "selected" : ""}>Rich Sponsor Cards</option>
              <option value="COMPACT" ${ab.layoutStyle === "COMPACT" ? "selected" : ""}>Compact Banner List</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Pinned Featured Ad (Optional)</label>
            <select id="ab_featured" class="input bg-white">
              <option value="">Auto-Select by Priority</option>
              ${activeAds.map(ad => `<option value="${esc(ad.id)}" ${ab.featuredAdId === ad.id ? "selected" : ""}>${esc(ad.promoTitle || ad.name)}</option>`).join("")}
            </select>
          </div>
        </div>
      </div>

      <!-- Global App-Wide Direct Sponsor Placement Controls -->
      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <div>
          <h3 class="font-bold text-base text-sky-900">App-Wide Direct Sponsor Placement Controls</h3>
          <p class="text-xs text-slate-500">All placements serve exclusively approved Direct Sponsor Ads (Zero AdMob / Zero 3rd-party SDKs).</p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <label class="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer">
            <span class="text-xs font-bold text-slate-800">Master Sponsor Switch</span>
            <input id="cfg_ads_enabled" type="checkbox" ${a.adsEnabled !== false ? "checked" : ""} class="w-5 h-5 accent-sky-600" />
          </label>
          <label class="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer">
            <span class="text-xs font-bold text-slate-800">All-Page Bottom Banner</span>
            <input id="cfg_banner" type="checkbox" ${a.sponsorBannerEnabled !== false ? "checked" : ""} class="w-5 h-5 accent-sky-600" />
          </label>
          <label class="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer">
            <span class="text-xs font-bold text-slate-800">App Open Sponsor Popup</span>
            <input id="cfg_popup" type="checkbox" ${a.appOpenPopupEnabled !== false ? "checked" : ""} class="w-5 h-5 accent-sky-600" />
          </label>
          <label class="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer">
            <span class="text-xs font-bold text-slate-800">Native Sponsor Cards</span>
            <input id="cfg_native" type="checkbox" ${a.nativeSponsorEnabled !== false ? "checked" : ""} class="w-5 h-5 accent-sky-600" />
          </label>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Popup Frequency</label>
            <select id="cfg_popup_freq" class="input bg-white">
              <option value="EVERY_LAUNCH" ${(a.appOpenPopupFrequency || "EVERY_LAUNCH") === "EVERY_LAUNCH" ? "selected" : ""}>Every Launch</option>
              <option value="ONCE_DAILY" ${a.appOpenPopupFrequency === "ONCE_DAILY" ? "selected" : ""}>Once Daily</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Preferred Popup Sponsor</label>
            <select id="cfg_popup_ad" class="input bg-white">
              <option value="">Auto (Highest Priority Active)</option>
              ${activeAds.map(ad => `<option value="${esc(ad.id)}" ${a.appOpenPopupSponsorId === ad.id ? "selected" : ""}>${esc(ad.promoTitle || ad.name)}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Native Card Interval</label>
            <select id="cfg_native_interval" class="input bg-white">
              ${[4, 5, 6].map(n => `<option value="${n}" ${(Number(a.nativeSponsorInterval) || 5) === n ? "selected" : ""}>Every ${n} Cards</option>`).join("")}
            </select>
          </div>
        </div>
      </div>

      <button type="submit" class="px-6 py-3 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-sm shadow-sm">
        Save About Us &amp; Placement Settings
      </button>
    </form>
  `;
}

async function submitAboutUsAndPlacementSettings(e) {
  e.preventDefault();
  const now = Date.now();
  const aboutPayload = {
    enabled: document.getElementById("ab_enabled").checked,
    sectionTitleBn: document.getElementById("ab_title_bn").value.trim() || "আমাদের স্পন্সর পার্টনার",
    sectionTitleEn: document.getElementById("ab_title_en").value.trim() || "Our Sponsor Partners",
    maxAds: Math.max(1, Math.min(10, parseInt(document.getElementById("ab_max").value, 10) || 3)),
    layoutStyle: document.getElementById("ab_layout").value || "CARDS",
    featuredAdId: document.getElementById("ab_featured").value || "",
    updatedAt: now
  };
  const cfgPayload = {
    ...(dbState.ad_config || {}),
    adsEnabled: document.getElementById("cfg_ads_enabled").checked,
    sponsorBannerEnabled: document.getElementById("cfg_banner").checked,
    appOpenPopupEnabled: document.getElementById("cfg_popup").checked,
    appOpenPopupFrequency: document.getElementById("cfg_popup_freq").value || "EVERY_LAUNCH",
    appOpenPopupSponsorId: document.getElementById("cfg_popup_ad").value || "",
    nativeSponsorEnabled: document.getElementById("cfg_native").checked,
    nativeSponsorInterval: Math.max(4, Math.min(6, parseInt(document.getElementById("cfg_native_interval").value, 10) || 5)),
    aboutUsSponsorEnabled: aboutPayload.enabled,
    updatedAt: now
  };
  await saveNode("about_us_ad_settings", null, aboutPayload);
  await saveNode("ad_config", null, cfgPayload);
  await writeAuditLog("UPDATE_AD_SETTINGS", "AD_CONFIG", "global", "Updated About Us & Direct Sponsor placement settings");
  toast("About Us & Direct Sponsor placement settings saved!");
}

// ============================================================================
// AUDIT LOGS VIEW
// ============================================================================
function renderAuditLogs() {
  const logs = Object.values(dbState.audit_logs || {})
    .sort((a, b) => Number(b.timestamp || 0) - Number(a.timestamp || 0))
    .slice(0, 100);
  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
      <h3 class="font-bold text-base text-slate-900 mb-1">Admin Audit Logs (${logs.length})</h3>
      <p class="text-xs text-slate-500 mb-4">Tracks every payment verification, advertisement approval/rejection, package edit, rate card update, and direct upload.</p>
      ${logs.length === 0 ? `<div class="text-xs text-slate-400">No audit events recorded yet.</div>` : `
        <div class="space-y-2">
          ${logs.map(l => `
            <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex flex-wrap items-center justify-between gap-2 text-xs">
              <div>
                <span class="px-2 py-0.5 rounded bg-sky-100 text-sky-900 font-bold text-[10px]">${esc(l.action)}</span>
                <strong class="ml-1.5 text-slate-800">${esc(l.summary)}</strong>
                <span class="ml-2 text-slate-400">by ${esc(l.actor)}</span>
              </div>
              <span class="text-[11px] text-slate-500">${formatDateTime(l.timestamp)}</span>
            </div>
          `).join("")}
        </div>
      `}
    </div>
  `;
}

// ============================================================================
// MODULE 14: PLACEMENT & DYNAMIC RATE CARD MANAGEMENT (37+ Placements, 9 Sizes)
// ============================================================================
function renderRateCards() {
  const cards = getMergedRateCards();
  const cfg = getRateCardSettings();
  const m = computeDirectSponsorMetrics();
  const usageMap = Object.fromEntries(m.placementStats.map(s => [s.placementId.toUpperCase(), s]));
  const ed = editing.rateCard || null;
  const edOverrides = ed ? parseSizeOverridesObj(ed) : {};
  const edAllowed = ed
    ? String(ed.allowedSizesCsv || "").split(",").map(s => s.trim().toUpperCase()).filter(Boolean)
    : ["320x50", "320x100", "300x200", "300x250", "RESPONSIVE", "FULL_WIDTH"];

  const modules = ["ALL", ...Array.from(new Set(cards.map(c => String(c.moduleName || "OTHER").toUpperCase())))];
  const filtered = adFilterState.rateModuleFilter === "ALL"
    ? cards
    : cards.filter(c => String(c.moduleName || "").toUpperCase() === adFilterState.rateModuleFilter);

  const knownMods = ["HOME", "DOCTOR", "HOSPITAL", "DIAGNOSTIC", "SPECIALIST", "CHAMBER", "OTHER"];
  const isCustomMod = ed && !knownMods.includes(String(ed.moduleName || "").toUpperCase());

  return `
    <!-- 1. Global Multi-Placement Bundle Discount & Custom Duration Rules -->
    <form onsubmit="submitRateCardSettings(event)" class="bg-gradient-to-r from-sky-50 to-teal-50 p-5 rounded-2xl border border-teal-200 shadow-sm mb-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
        <div>
          <h3 class="font-bold text-base text-slate-900">Multi-Placement Bundle Discount &amp; Global Rate Rules</h3>
          <p class="text-xs text-slate-600">When advertisers book multiple placements together (e.g. Home Top + Doctor List + Hospital List + Diagnostic List), optional bundle discounts apply automatically.</p>
        </div>
        <button type="button" onclick="seedAllRateCardsToCloud()" class="px-3.5 py-2 rounded-xl bg-sky-700 hover:bg-sky-800 text-white text-xs font-bold shadow-sm">
          Sync Default 37 Placements to Firebase
        </button>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-6 gap-3 items-end">
        <label class="flex items-center gap-2 p-2.5 rounded-xl bg-white border border-slate-200 text-xs font-bold cursor-pointer">
          <input id="rc_bundle_enabled" type="checkbox" ${cfg.bundleEnabled ? "checked" : ""} class="w-4 h-4 accent-teal-600" />
          Bundle Discount ON
        </label>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">2 Placements (%)</label>
          <input id="rc_disc_2" type="number" min="0" max="80" value="${esc(cfg.discount2Pct ?? 5)}" class="input bg-white text-xs" />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">3 Placements (%)</label>
          <input id="rc_disc_3" type="number" min="0" max="80" value="${esc(cfg.discount3Pct ?? 10)}" class="input bg-white text-xs" />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">4+ Placements (%)</label>
          <input id="rc_disc_4" type="number" min="0" max="80" value="${esc(cfg.discount4PlusPct ?? 15)}" class="input bg-white text-xs" />
        </div>
        <label class="flex items-center gap-2 p-2.5 rounded-xl bg-white border border-slate-200 text-xs font-bold cursor-pointer">
          <input id="rc_custom_dur" type="checkbox" ${cfg.allowCustomDuration !== false ? "checked" : ""} class="w-4 h-4 accent-sky-600" />
          Custom Days ON
        </label>
        <button type="submit" class="py-2.5 px-4 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold">
          Save Bundle Rules
        </button>
      </div>
    </form>

    <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
      <!-- 2. Admin Rate Card Form (All 13 Fields + Per-Size Rate Overrides) -->
      <div class="xl:col-span-5 bg-white p-6 rounded-2xl border-2 border-sky-100 shadow-sm h-fit">
        <div class="flex items-center justify-between mb-3">
          <div>
            <h3 class="font-bold text-base text-sky-900">${ed ? `Edit Rate Card: ${esc(ed.placementId)}` : "Add / Configure Placement Rate Card"}</h3>
            <p class="text-xs text-slate-500">Same ad size can have different prices across placements.</p>
          </div>
          ${ed ? `<button type="button" onclick="editing={};renderTab()" class="text-xs font-bold text-slate-500 hover:text-slate-800">Cancel Edit</button>` : ""}
        </div>

        <form onsubmit="submitRateCard(event)" class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Placement ID *</label>
              <input id="rc_id" required ${ed ? "readonly" : ""} value="${esc(ed?.placementId || "")}" placeholder="e.g. HOME-01 or CUSTOM-01" class="input bg-white text-xs font-bold uppercase ${ed ? "bg-slate-100" : ""}" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Module Name *</label>
              <select id="rc_module" onchange="document.getElementById('rc_custom_mod_wrap').classList.toggle('hidden', this.value !== 'CUSTOM_MODULE')" class="input bg-white text-xs">
                ${knownMods.map(mod => `<option value="${mod}" ${(ed?.moduleName || "HOME").toUpperCase() === mod ? "selected" : ""}>${mod}</option>`).join("")}
                <option value="CUSTOM_MODULE" ${isCustomMod ? "selected" : ""}>+ Custom Future Module</option>
              </select>
            </div>
          </div>

          <div id="rc_custom_mod_wrap" class="${isCustomMod ? "" : "hidden"}">
            <label class="block text-xs font-semibold text-slate-700 mb-1">Custom Module Name *</label>
            <input id="rc_custom_mod" value="${isCustomMod ? esc(ed.moduleName) : ""}" placeholder="e.g. PHARMACY" class="input bg-white text-xs uppercase" />
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Placement Name (English) *</label>
              <input id="rc_name_en" required value="${esc(ed?.placementNameEn || "")}" placeholder="Home Top Banner" class="input bg-white text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Placement Name (Bangla)</label>
              <input id="rc_name_bn" value="${esc(ed?.placementNameBn || "")}" placeholder="হোম টপ ব্যানার" class="input bg-white text-xs" />
            </div>
          </div>

          <!-- Duration Rates -->
          <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
            <div class="text-xs font-bold text-slate-800">Base Rates (BDT &#2547;)</div>
            <div class="grid grid-cols-3 sm:grid-cols-5 gap-2">
              <div>
                <label class="block text-[10px] font-semibold text-slate-600 mb-0.5">Daily</label>
                <input id="rc_d1" type="number" min="0" required value="${esc(ed?.dailyRateBdt ?? 35)}" class="input bg-white text-xs px-2 py-1.5" />
              </div>
              <div>
                <label class="block text-[10px] font-semibold text-slate-600 mb-0.5">7 Days</label>
                <input id="rc_d7" type="number" min="0" required value="${esc(ed?.sevenDaysRateBdt ?? 140)}" class="input bg-white text-xs px-2 py-1.5" />
              </div>
              <div>
                <label class="block text-[10px] font-semibold text-slate-600 mb-0.5">15 Days</label>
                <input id="rc_d15" type="number" min="0" required value="${esc(ed?.fifteenDaysRateBdt ?? 220)}" class="input bg-white text-xs px-2 py-1.5" />
              </div>
              <div>
                <label class="block text-[10px] font-semibold text-slate-600 mb-0.5">30 Days</label>
                <input id="rc_d30" type="number" min="0" required value="${esc(ed?.thirtyDaysRateBdt ?? 300)}" class="input bg-white text-xs px-2 py-1.5" />
              </div>
              <div>
                <label class="block text-[10px] font-semibold text-slate-600 mb-0.5">Custom/Day</label>
                <input id="rc_dcustom" type="number" min="0" required value="${esc(ed?.customDailyRateBdt ?? 15)}" class="input bg-white text-xs px-2 py-1.5" />
              </div>
            </div>
          </div>

          <!-- Allowed Ad Sizes + Optional Per-Size 30d Overrides -->
          <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
            <div class="text-xs font-bold text-slate-800">Allowed Ad Sizes &amp; Optional Size-Specific 30d Rate (&#2547;)</div>
            <div class="space-y-1.5 max-h-52 overflow-y-auto pr-1">
              ${AD_SIZE_FORMATS.map(f => {
                const checked = edAllowed.includes(f.key.toUpperCase());
                const ov = edOverrides[f.key.toUpperCase()] || edOverrides[f.key] || "";
                return `
                  <div class="flex items-center justify-between gap-2 bg-white px-2.5 py-1.5 rounded-lg border border-slate-200 text-xs">
                    <label class="flex items-center gap-2 cursor-pointer flex-1 min-w-0">
                      <input type="checkbox" name="rc_size_cb" value="${f.key}" ${checked ? "checked" : ""} class="accent-sky-600" />
                      <span class="font-bold text-slate-900">${esc(f.label)}</span>
                      <span class="text-[11px] text-slate-500 truncate">${esc(f.format)}</span>
                    </label>
                    <input type="number" min="0" data-size-override="${f.key}" value="${esc(ov)}" placeholder="30d override" class="w-24 px-2 py-1 rounded border border-slate-200 text-[11px]" />
                  </div>
                `;
              }).join("")}
            </div>
          </div>

          <!-- Capacity, Rotation, Insertion Frequency, Status -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Max Active Ads *</label>
              <input id="rc_max_ads" type="number" min="1" max="50" required value="${esc(ed?.maxActiveAds ?? 3)}" class="input bg-white text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Card Insertion Frequency</label>
              <input id="rc_freq" type="number" min="2" max="20" required value="${esc(ed?.cardInsertionFrequency ?? 5)}" class="input bg-white text-xs" />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Advertisement Rotation</label>
              <select id="rc_rotation" class="input bg-white text-xs">
                ${[
                  ["WEIGHTED_ROTATION", "Weighted Rotation"],
                  ["PRIORITY_FIRST", "Highest Priority First"],
                  ["SEQUENTIAL", "Sequential Rotation"],
                  ["RANDOM", "Random Balanced"]
                ].map(([k, lbl]) => `<option value="${k}" ${(ed?.rotationMode || "WEIGHTED_ROTATION") === k ? "selected" : ""}>${lbl}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Placement Status</label>
              <select id="rc_status" class="input bg-white text-xs font-bold">
                <option value="ACTIVE" ${(ed?.status || "ACTIVE") === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
                <option value="INACTIVE" ${ed?.status === "INACTIVE" ? "selected" : ""}>INACTIVE</option>
              </select>
            </div>
          </div>

          <button type="submit" class="w-full py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-xs shadow-sm">
            ${ed ? "Update Placement Rate Card" : "Save New Placement Rate Card"}
          </button>
        </form>
      </div>

      <!-- 3. All 37+ Placements Directory & Rate Table -->
      <div class="xl:col-span-7 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
          <div>
            <h3 class="font-bold text-base text-slate-900">Screen Placement Catalog (${filtered.length} shown / ${cards.length} total)</h3>
            <p class="text-xs text-slate-500">Click Edit on any row to customize rates, allowed sizes, slots, rotation, frequency, or Active/Inactive status.</p>
          </div>
        </div>

        <div class="flex flex-wrap gap-1.5 mb-4">
          ${modules.map(mod => `
            <button type="button" onclick="adFilterState.rateModuleFilter='${esc(mod)}';renderTab()" class="px-2.5 py-1 rounded-lg text-xs font-bold border ${adFilterState.rateModuleFilter === mod ? "bg-sky-600 text-white border-sky-600" : "bg-slate-50 text-slate-600 border-slate-200"}">
              ${esc(mod)}
            </button>
          `).join("")}
        </div>

        <div class="space-y-2.5 max-h-[820px] overflow-y-auto pr-1">
          ${filtered.map(card => {
            const st = String(card.status || "ACTIVE").toUpperCase();
            const usage = usageMap[card.placementId.toUpperCase()] || { usedSlots: 0, maxSlots: card.maxActiveAds || 3, revenueBdt: 0 };
            const isActive = st === "ACTIVE";
            const allowedList = String(card.allowedSizesCsv || "").split(",").map(s => s.trim()).filter(Boolean);
            return `
              <div class="p-3.5 rounded-2xl border ${isActive ? "border-slate-200 bg-slate-50/70" : "border-rose-200 bg-rose-50/30"}">
                <div class="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div class="flex flex-wrap items-center gap-1.5">
                      <span class="px-2 py-0.5 rounded bg-sky-900 text-white font-bold text-[10px]">${esc(card.placementId)}</span>
                      <span class="px-2 py-0.5 rounded bg-slate-200 text-slate-700 font-bold text-[10px]">${esc(card.moduleName)}</span>
                      <strong class="text-sm text-slate-900">${esc(card.placementNameEn)}</strong>
                      <span class="text-xs text-slate-500">(${esc(card.placementNameBn)})</span>
                      ${statusBadgeHtml(isActive ? "ACTIVE" : "DISABLED")}
                    </div>
                    <div class="text-xs text-teal-800 font-bold mt-1">
                      1d: &#2547;${card.dailyRateBdt} &bull; 7d: &#2547;${card.sevenDaysRateBdt} &bull; 15d: &#2547;${card.fifteenDaysRateBdt} &bull; 30d: &#2547;${card.thirtyDaysRateBdt} &bull; Custom/d: &#2547;${card.customDailyRateBdt}
                    </div>
                    <div class="text-[11px] text-slate-600 mt-1">
                      Slots: <strong>${usage.usedSlots}/${card.maxActiveAds}</strong> &bull; Rotation: <strong>${esc(card.rotationMode)}</strong> &bull; Freq: every <strong>${card.cardInsertionFrequency}</strong> &bull; Rev: <strong class="text-emerald-700">&#2547;${usage.revenueBdt.toLocaleString()}</strong>
                    </div>
                    <div class="flex flex-wrap gap-1 mt-1.5">
                      ${allowedList.map(sz => `<span class="px-1.5 py-0.5 rounded bg-white border border-slate-200 text-[10px] font-semibold text-slate-700">${esc(sz)}</span>`).join("")}
                    </div>
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <button onclick="toggleRateCardStatus('${esc(card.placementId)}')" class="px-2.5 py-1 rounded-lg ${isActive ? "bg-amber-50 text-amber-800" : "bg-emerald-50 text-emerald-800"} text-xs font-bold">
                      ${isActive ? "Deactivate" : "Activate"}
                    </button>
                    <button onclick="editRateCard('${esc(card.placementId)}')" class="px-2.5 py-1 rounded-lg bg-sky-100 text-sky-800 text-xs font-bold">Edit</button>
                  </div>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      </div>
    </div>
  `;
}

function editRateCard(placementId) {
  const cards = getMergedRateCards();
  const found = cards.find(c => c.placementId.toUpperCase() === placementId.toUpperCase());
  if (!found) return;
  editing = { rateCard: { ...found } };
  renderTab();
}

async function submitRateCard(e) {
  e.preventDefault();
  const placementId = document.getElementById("rc_id").value.trim().toUpperCase();
  if (!placementId) return toast("Placement ID is required.");
  let moduleName = document.getElementById("rc_module").value;
  if (moduleName === "CUSTOM_MODULE") {
    moduleName = document.getElementById("rc_custom_mod").value.trim().toUpperCase() || "OTHER";
  }
  const pickedSizes = Array.from(document.querySelectorAll('input[name="rc_size_cb"]:checked')).map(c => c.value);
  const overrides = {};
  document.querySelectorAll('input[data-size-override]').forEach(inp => {
    const val = parseInt(inp.value, 10) || 0;
    if (val > 0) overrides[inp.dataset.sizeOverride] = val;
  });

  const existing = dbState.placement_rate_cards?.[placementId]
    || DEFAULT_PLACEMENT_CATALOG.find(c => c.placementId.toUpperCase() === placementId)
    || {};

  const payload = {
    ...existing,
    placementId,
    moduleName,
    placementNameEn: document.getElementById("rc_name_en").value.trim(),
    placementNameBn: document.getElementById("rc_name_bn").value.trim() || document.getElementById("rc_name_en").value.trim(),
    allowedSizesCsv: pickedSizes.length ? pickedSizes.join(",") : "300x200,320x100,RESPONSIVE",
    sizeRateOverrides: overrides,
    sizeRateOverridesJson: JSON.stringify(overrides),
    dailyRateBdt: Math.max(0, parseInt(document.getElementById("rc_d1").value, 10) || 0),
    sevenDaysRateBdt: Math.max(0, parseInt(document.getElementById("rc_d7").value, 10) || 0),
    fifteenDaysRateBdt: Math.max(0, parseInt(document.getElementById("rc_d15").value, 10) || 0),
    thirtyDaysRateBdt: Math.max(0, parseInt(document.getElementById("rc_d30").value, 10) || 0),
    customDailyRateBdt: Math.max(0, parseInt(document.getElementById("rc_dcustom").value, 10) || 0),
    maxActiveAds: Math.max(1, parseInt(document.getElementById("rc_max_ads").value, 10) || 3),
    cardInsertionFrequency: Math.max(2, Math.min(20, parseInt(document.getElementById("rc_freq").value, 10) || 5)),
    rotationMode: document.getElementById("rc_rotation").value || "WEIGHTED_ROTATION",
    status: document.getElementById("rc_status").value || "ACTIVE",
    sortOrder: Number(existing.sortOrder || 99),
    updatedAt: Date.now()
  };

  await saveNode("placement_rate_cards", placementId, payload);
  await writeAuditLog("SAVE_RATE_CARD", "RATE_CARD", placementId, `Updated ${placementId} (${payload.thirtyDaysRateBdt} BDT/30d, ${payload.status})`);
  editing = {};
  renderTab();
}

async function toggleRateCardStatus(placementId) {
  const cards = getMergedRateCards();
  const cur = cards.find(c => c.placementId.toUpperCase() === placementId.toUpperCase());
  if (!cur) return;
  const nextStatus = String(cur.status || "ACTIVE").toUpperCase() === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  const payload = { ...cur, status: nextStatus, updatedAt: Date.now() };
  await saveNode("placement_rate_cards", cur.placementId, payload);
  await writeAuditLog("TOGGLE_RATE_CARD", "RATE_CARD", cur.placementId, `Set ${cur.placementId} to ${nextStatus}`);
}

async function submitRateCardSettings(e) {
  e.preventDefault();
  const payload = {
    bundleEnabled: document.getElementById("rc_bundle_enabled").checked,
    discount2Pct: Math.max(0, Math.min(80, parseInt(document.getElementById("rc_disc_2").value, 10) || 0)),
    discount3Pct: Math.max(0, Math.min(80, parseInt(document.getElementById("rc_disc_3").value, 10) || 0)),
    discount4PlusPct: Math.max(0, Math.min(80, parseInt(document.getElementById("rc_disc_4").value, 10) || 0)),
    allowCustomDuration: document.getElementById("rc_custom_dur").checked,
    updatedAt: Date.now()
  };
  await saveNode("rate_card_settings", null, payload);
  await writeAuditLog("UPDATE_RATE_SETTINGS", "RATE_CARD_SETTINGS", "global", `Updated multi-placement bundle rules (${payload.discount2Pct}/${payload.discount3Pct}/${payload.discount4PlusPct}%)`);
}

async function seedAllRateCardsToCloud() {
  const now = Date.now();
  const batch = {};
  const merged = getMergedRateCards();
  merged.forEach(c => {
    batch[c.placementId] = { ...c, updatedAt: now };
  });
  await patchCollectionNodes("placement_rate_cards", batch, `Synced ${merged.length} placement rate cards to Firebase!`);
  await writeAuditLog("SEED_RATE_CARDS", "RATE_CARD", "all", `Synced ${merged.length} canonical placement rate cards`);
}

