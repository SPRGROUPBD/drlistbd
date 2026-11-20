// ============================================================================
// Doctor List BD — Direct Sponsor Advertisement & Offline Cache Admin Suite
// Zero AdMob • Zero Third-Party Ad Networks • Server-Authoritative Lifecycle
// Covers ALL 13 Admin Modules + Admin Direct Ad Upload + Audit Logs
// ============================================================================

const PLACEMENT_TYPES = [
  { key: "BANNER_300x200", label: "300×200 Banner", w: 300, h: 200 },
  { key: "BANNER_320x100", label: "320×100 Banner", w: 320, h: 100 },
  { key: "BANNER_728x90", label: "728×90 Leaderboard Banner", w: 728, h: 90 },
  { key: "BANNER_CUSTOM", label: "Custom-Size Banner", w: 300, h: 200 },
  { key: "SPONSORED_NATIVE_CARD", label: "Sponsored Native Card", w: 320, h: 180 },
  { key: "POPUP_AD", label: "Popup Advertisement", w: 320, h: 200 },
  { key: "ABOUT_US_SPONSOR", label: "About Us Page Sponsor Section", w: 300, h: 200 },
  { key: "ALL", label: "All Placements (Universal)", w: 300, h: 200 }
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
  previewMode: "banner" // banner | native | popup | about_us
};

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
  if (!sp) return "PENDING_APPROVAL";
  const raw = String(sp.status || (sp.isActive === false ? "PAUSED" : "ACTIVE")).toUpperCase();
  if (raw === "REJECTED" || raw === "PAUSED" || raw === "PENDING_PAYMENT" || raw === "PENDING_APPROVAL" || raw === "EXPIRED") {
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
    PENDING_PAYMENT: "bg-amber-100 text-amber-800 border-amber-200",
    PENDING_APPROVAL: "bg-amber-100 text-amber-800 border-amber-200",
    PAUSED: "bg-slate-200 text-slate-700 border-slate-300",
    DISABLED: "bg-slate-200 text-slate-700 border-slate-300",
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
  const pendingAds = allAds.filter(a => a.effectiveStatus === "PENDING_APPROVAL" || a.effectiveStatus === "PENDING_PAYMENT");
  const expiredAds = allAds.filter(a => a.effectiveStatus === "EXPIRED");
  const renewalAds = allAds.filter(a => Boolean(a.renewalOfAdId));

  const pendingPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "PENDING");
  const verifiedPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "VERIFIED");
  const rejectedPayments = allPayments.filter(p => String(p.status || "").toUpperCase() === "REJECTED");

  const verifiedRevenueBdt = verifiedPayments.reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);
  const bkashRevenueBdt = verifiedPayments.filter(p => String(p.paymentMethod || "").toUpperCase() === "BKASH")
    .reduce((sum, p) => sum + Number(p.amountBdt || 0), 0);
  const nagadRevenueBdt = verifiedPayments.filter(p => String(p.paymentMethod || "").toUpperCase() === "NAGAD")
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

  return {
    allAds, activeAds, pendingAds, expiredAds, renewalAds,
    allPayments, pendingPayments, verifiedPayments, rejectedPayments,
    allPackages, allAdvertisers,
    verifiedRevenueBdt, bkashRevenueBdt, nagadRevenueBdt,
    totalImpressions, totalUniqueViews, totalClicks, totalOfflineSynced,
    adStatsMap, dailyRows, pkgNode
  };
}

// ============================================================================
// MODULE 1: ADMIN DASHBOARD (Direct Sponsor + Doctor Directory Unified)
// ============================================================================
function renderDashboard() {
  const m = computeDirectSponsorMetrics();
  const docs = Object.values(dbState.doctors || {});
  const chambers = Object.values(dbState.chambers || {});
  const activeChambers = chambers.filter(c => c.isActive !== false).length;

  return `
    <!-- Hero Direct Sponsor Monetization Banner -->
    <div class="bg-gradient-to-r from-slate-900 via-sky-900 to-teal-900 text-white p-6 rounded-3xl shadow-sm mb-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div>
          <div class="flex flex-wrap items-center gap-2 mb-1">
            <span class="px-2.5 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-400/40 text-emerald-200 text-[11px] font-bold">100% Direct Sponsor Platform</span>
            <span class="px-2.5 py-0.5 rounded-full bg-white/10 text-sky-100 text-[11px] font-semibold">Zero AdMob &bull; Zero 3rd-Party Ad Networks</span>
          </div>
          <h3 class="text-xl font-bold">Direct Sponsor Advertisement &amp; Offline Cache Overview</h3>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="switchTab('advertisements')" class="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-white text-xs font-bold shadow">
            + Direct Upload Ad (Admin)
          </button>
          <a href="advertiser.html" target="_blank" class="px-4 py-2 rounded-xl bg-white/15 hover:bg-white/25 text-white text-xs font-semibold">
            Open Advertiser Portal &nearr;
          </a>
        </div>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15">
          <div class="text-xs text-emerald-200 font-semibold">Verified Ad Revenue</div>
          <div class="text-2xl font-extrabold mt-1">&#2547;${m.verifiedRevenueBdt.toLocaleString()}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">${m.verifiedPayments.length} verified payment(s)</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('payments')">
          <div class="text-xs text-amber-200 font-semibold">Pending Payments</div>
          <div class="text-2xl font-extrabold mt-1">${m.pendingPayments.length}</div>
          <div class="text-[11px] text-amber-100 mt-0.5">Awaiting manual verification</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('active_ads')">
          <div class="text-xs text-teal-200 font-semibold">Active Sponsor Ads</div>
          <div class="text-2xl font-extrabold mt-1">${m.activeAds.length}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">${m.pendingAds.length} pending &bull; ${m.expiredAds.length} expired</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('view_reports')">
          <div class="text-xs text-sky-200 font-semibold">Total Impressions</div>
          <div class="text-2xl font-extrabold mt-1">${m.totalImpressions.toLocaleString()}</div>
          <div class="text-[11px] text-sky-100 mt-0.5">${m.totalUniqueViews.toLocaleString()} unique views</div>
        </div>
        <div class="p-4 rounded-2xl bg-white/10 border border-white/15 cursor-pointer hover:bg-white/15" onclick="switchTab('advertisers')">
          <div class="text-xs text-indigo-200 font-semibold">Advertisers / Packages</div>
          <div class="text-2xl font-extrabold mt-1">${m.allAdvertisers.length} <span class="text-sm font-normal text-sky-200">/ ${m.allPackages.length}</span></div>
          <div class="text-[11px] text-sky-100 mt-0.5">Dynamic packages active</div>
        </div>
      </div>
    </div>

    ${m.pendingPayments.length > 0 ? `
      <div class="mb-6 p-4 rounded-2xl bg-amber-50 border border-amber-300 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-amber-900">
          <strong>Action Required:</strong> You have <strong>${m.pendingPayments.length}</strong> manual bKash/Nagad payment(s) waiting for Admin verification. Approved ads activate automatically!
        </div>
        <button onclick="switchTab('payments')" class="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold">
          Verify Payments Now &rarr;
        </button>
      </div>
    ` : ""}

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
        ["Renewal Requests", m.renewalAds.length, "text-indigo-700", "renewals"]
      ].map(([label, count, cls, tab]) => `
        <div onclick="switchTab('${tab}')" class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm cursor-pointer hover:border-sky-300 transition">
          <div class="text-3xl font-bold ${cls}">${count}</div>
          <div class="text-xs text-slate-500 mt-1 font-medium">${label}</div>
        </div>
      `).join("")}
    </div>

    <!-- Quick Action Grid -->
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
      <h4 class="font-bold text-slate-900 mb-3">Quick Workflow Guide</h4>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs text-slate-600">
        <div class="p-4 rounded-xl bg-slate-50 border border-slate-200">
          <div class="font-bold text-sky-900 mb-1">1. Configure Packages &amp; Payment Info</div>
          Create dynamic ad packages (e.g. 300×200, 30 days, &#2547;300) in <strong>Package Management</strong> and set your bKash &amp; Nagad numbers in <strong>bKash &amp; Nagad Settings</strong>.
        </div>
        <div class="p-4 rounded-xl bg-slate-50 border border-slate-200">
          <div class="font-bold text-teal-900 mb-1">2. Verify Advertiser Payments or Upload Directly</div>
          Approve advertiser bKash/Nagad payments in <strong>Payment Verification</strong> (duration starts only on approval) OR publish sponsor ads directly in <strong>Advertisement Management</strong>.
        </div>
        <div class="p-4 rounded-xl bg-slate-50 border border-slate-200">
          <div class="font-bold text-emerald-900 mb-1">3. Automatic Offline Caching &amp; Analytics</div>
          Active approved ads automatically download to Android devices for offline display, auto-expire on server sync, and queue offline views with duplicate protection.
        </div>
      </div>
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
// MODULE 3: DYNAMIC ADVERTISEMENT PACKAGE MANAGEMENT
// ============================================================================
function renderAdPackages() {
  const { allPackages } = getSponsorArrays();
  const pk = editing.pkg ? { id: editing.pkg, ...(dbState.ad_packages?.[editing.pkg] || {}) } : null;

  return `
    <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
      <div class="xl:col-span-5 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="font-bold text-base text-sky-900">${pk ? "Edit Advertising Package" : "Create Dynamic Ad Package"}</h3>
            <p class="text-xs text-slate-500">All prices, pixel dimensions &amp; durations are 100% dynamic — never hardcoded.</p>
          </div>
          ${pk ? `<button type="button" onclick="editing={};renderTab()" class="text-xs font-semibold text-slate-500">Cancel</button>` : ""}
        </div>

        <form onsubmit="submitAdPackage(event)" class="space-y-3.5">
          <input type="hidden" id="pkg_id" value="${esc(pk?.id || "")}" />
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Package Name *</label>
            <input id="pkg_name" required value="${esc(pk?.name || "")}" placeholder="e.g. Standard 300x200 Monthly Banner" class="input bg-white" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Placement Type *</label>
            <select id="pkg_placement" onchange="onPkgPlacementChange(this.value)" class="input bg-white">
              ${PLACEMENT_TYPES.map(p => `<option value="${p.key}" ${(pk?.placementType || "BANNER_300x200") === p.key ? "selected" : ""}>${p.label}</option>`).join("")}
            </select>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Width (px) *</label>
              <input id="pkg_w" type="number" min="100" max="2000" required value="${esc(pk?.widthPx || 300)}" class="input bg-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Height (px) *</label>
              <input id="pkg_h" type="number" min="50" max="2000" required value="${esc(pk?.heightPx || 200)}" class="input bg-white" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Duration (Days) *</label>
              <input id="pkg_days" type="number" min="1" max="3650" required value="${esc(pk?.durationDays || 30)}" class="input bg-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Price (BDT &#2547;) *</label>
              <input id="pkg_price" type="number" min="0" required value="${esc(pk?.priceBdt ?? 300)}" class="input bg-white" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Description (Optional)</label>
            <textarea id="pkg_desc" rows="2" placeholder="Explain visibility benefits for advertisers..." class="input bg-white">${esc(pk?.description || "")}</textarea>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Package Status *</label>
            <select id="pkg_status" class="input bg-white">
              <option value="ACTIVE" ${(pk?.status || "ACTIVE") === "ACTIVE" ? "selected" : ""}>ACTIVE (Visible to Advertisers)</option>
              <option value="DISABLED" ${pk?.status === "DISABLED" ? "selected" : ""}>DISABLED (Hidden)</option>
            </select>
          </div>
          <button type="submit" class="w-full py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-sm">
            ${pk ? "Update Package" : "Create Advertising Package"}
          </button>
        </form>
      </div>

      <div class="xl:col-span-7 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
          <h3 class="font-bold text-base text-slate-900">Configured Advertising Packages (${allPackages.length})</h3>
          <button type="button" onclick="seedStarterPackages()" class="px-3 py-1.5 rounded-lg bg-teal-50 text-teal-800 border border-teal-200 hover:bg-teal-100 text-xs font-bold">
            + Add Sample Starter Packages
          </button>
        </div>
        ${allPackages.length === 0 ? `
          <div class="p-8 text-center rounded-2xl bg-slate-50 border border-dashed border-slate-300 text-sm text-slate-500">
            No packages created yet. Create your first dynamic package on the left (e.g. 300×200 px, 30 days, &#2547;300).
          </div>
        ` : `
          <div class="space-y-3">
            ${allPackages.map(item => `
              <div class="p-4 rounded-2xl border border-slate-200 bg-slate-50/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div>
                  <div class="flex flex-wrap items-center gap-2">
                    <strong class="text-sm text-slate-900">${esc(item.name)}</strong>
                    ${statusBadgeHtml(item.status || "ACTIVE")}
                  </div>
                  <div class="text-xs text-sky-800 font-semibold mt-1">
                    ${esc(item.widthPx)}&times;${esc(item.heightPx)} px &bull; ${esc(item.durationDays)} Days &bull; &#2547;${Number(item.priceBdt || 0).toLocaleString()} BDT
                  </div>
                  <div class="text-[11px] text-slate-500 mt-0.5">
                    Placement: ${esc(placementLabel(item.placementType))}${item.description ? " &bull; " + esc(item.description) : ""}
                  </div>
                </div>
                <div class="flex flex-wrap gap-1.5">
                  <button onclick="togglePackageStatus('${esc(item.id)}')" class="px-2.5 py-1.5 rounded-lg bg-amber-50 text-amber-800 text-xs font-semibold hover:bg-amber-100">
                    ${item.status === "ACTIVE" ? "Disable" : "Enable"}
                  </button>
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
  if (w) w.value = found.w;
  if (h) h.value = found.h;
}

async function submitAdPackage(e) {
  e.preventDefault();
  const id = document.getElementById("pkg_id").value.trim() || uid("pkg");
  const payload = {
    id,
    name: document.getElementById("pkg_name").value.trim(),
    placementType: document.getElementById("pkg_placement").value,
    widthPx: Math.max(100, parseInt(document.getElementById("pkg_w").value, 10) || 300),
    heightPx: Math.max(50, parseInt(document.getElementById("pkg_h").value, 10) || 200),
    durationDays: Math.max(1, parseInt(document.getElementById("pkg_days").value, 10) || 30),
    priceBdt: Math.max(0, parseInt(document.getElementById("pkg_price").value, 10) || 0),
    description: document.getElementById("pkg_desc").value.trim(),
    status: document.getElementById("pkg_status").value || "ACTIVE",
    updatedAt: Date.now()
  };
  await saveNode("ad_packages", id, payload);
  await writeAuditLog(editing.pkg ? "EDIT_PACKAGE" : "CREATE_PACKAGE", "PACKAGE", id, `${payload.name} (${payload.widthPx}x${payload.heightPx}, ${payload.durationDays}d, BDT ${payload.priceBdt})`);
  editing = {};
  renderTab();
}

async function togglePackageStatus(id) {
  const cur = dbState.ad_packages?.[id];
  if (!cur) return;
  const next = cur.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
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
    pkg_300x200_30d: { id: "pkg_300x200_30d", name: "Standard Banner 300×200 (30 Days)", widthPx: 300, heightPx: 200, durationDays: 30, priceBdt: 300, placementType: "BANNER_300x200", description: "Displayed across app pages & offline cached.", status: "ACTIVE", updatedAt: now },
    pkg_320x100_30d: { id: "pkg_320x100_30d", name: "Mobile Banner 320×100 (30 Days)", widthPx: 320, heightPx: 100, durationDays: 30, priceBdt: 500, placementType: "BANNER_320x100", description: "Persistent bottom banner on all screens.", status: "ACTIVE", updatedAt: now },
    pkg_native_30d: { id: "pkg_native_30d", name: "Sponsored Native Card (30 Days)", widthPx: 320, heightPx: 180, durationDays: 30, priceBdt: 800, placementType: "SPONSORED_NATIVE_CARD", description: "High-visibility native card inside doctor lists.", status: "ACTIVE", updatedAt: now },
    pkg_popup_15d: { id: "pkg_popup_15d", name: "App Open Popup Sponsor (15 Days)", widthPx: 320, heightPx: 200, durationDays: 15, priceBdt: 1200, placementType: "POPUP_AD", description: "Featured launch popup with Close (X) button.", status: "ACTIVE", updatedAt: now },
    pkg_about_30d: { id: "pkg_about_30d", name: "About Us Partner Spotlight (30 Days)", widthPx: 300, heightPx: 200, durationDays: 30, priceBdt: 400, placementType: "ABOUT_US_SPONSOR", description: "Featured partner section on About Us screen.", status: "ACTIVE", updatedAt: now }
  };
  await patchCollectionNodes("ad_packages", batch, "Starter dynamic packages created! You can edit or delete any package anytime.");
  await writeAuditLog("SEED_PACKAGES", "PACKAGE", "starter", "Added 5 starter editable packages");
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
  else if (filterMode === "PENDING") list = allAds.filter(a => a.effectiveStatus === "PENDING_APPROVAL" || a.effectiveStatus === "PENDING_PAYMENT");
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

            <!-- Target Pages -->
            <div class="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs font-bold text-slate-700">Target App Pages</span>
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
                  <option value="PAUSED" ${sp?.status === "PAUSED" ? "selected" : ""}>PAUSED</option>
                  <option value="PENDING_APPROVAL" ${sp?.status === "PENDING_APPROVAL" ? "selected" : ""}>PENDING_APPROVAL</option>
                  <option value="EXPIRED" ${sp?.status === "EXPIRED" ? "selected" : ""}>EXPIRED</option>
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
                  ${ad.effectiveStatus === "PENDING_APPROVAL" || ad.effectiveStatus === "PENDING_PAYMENT" ? `
                    <button onclick="adminSetAdStatus('${esc(ad.id)}','REJECTED')" class="px-3 py-1.5 rounded-lg bg-rose-100 text-rose-800 hover:bg-rose-200 text-xs font-semibold">Reject</button>
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

  const payload = {
    ...prev,
    id,
    advertiserId: prev.advertiserId || "",
    submittedBy: prev.submittedBy || "ADMIN",
    packageId: pkgId || prev.packageId || "admin_direct",
    packageName: pkg ? pkg.name : (prev.packageName || "Admin Direct Campaign"),
    widthPx: Math.max(100, parseInt(document.getElementById("ad_w").value, 10) || 300),
    heightPx: Math.max(50, parseInt(document.getElementById("ad_h").value, 10) || 200),
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
          <h3 class="font-bold text-lg text-slate-900">Manual bKash &amp; Nagad Payment Verification</h3>
          <p class="text-xs text-slate-500">Advertisements NEVER start before Admin verifies the TrxID &amp; payment amount. Approval prevents duplicate activation and starts the duration clock server-side.</p>
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
            return `
              <div class="p-4 rounded-2xl border ${isDupTrx ? "border-rose-400 bg-rose-50/40" : "border-slate-200 bg-slate-50/60"} flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div class="space-y-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="px-2.5 py-0.5 rounded-lg text-xs font-extrabold ${String(pay.paymentMethod).toUpperCase() === "BKASH" ? "bg-pink-100 text-pink-800" : "bg-orange-100 text-orange-800"}">
                      ${esc(pay.paymentMethod || "BKASH")}
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
// MODULE 10: bKash & Nagad PAYMENT SETTINGS
// ============================================================================
function renderPaymentSettings() {
  const p = dbState.payment_settings || {};
  return `
    <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm max-w-3xl">
      <h3 class="font-bold text-lg text-sky-900 mb-1">bKash &amp; Nagad Payment Settings</h3>
      <p class="text-xs text-slate-500 mb-5">Displayed dynamically to advertisers inside both the Android App and Web Advertiser Portal during payment submission.</p>

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

        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">Payment Instructions (Bangla)</label>
          <textarea id="ps_ins_bn" rows="2" placeholder="বিকাশ বা নগদ Send Money / Payment করে TrxID সাবমিট করুন। অ্যাডমিন যাচাইয়ের পর বিজ্ঞাপন চালু হবে।" class="input bg-white">${esc(p.instructionsBn || "")}</textarea>
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">Payment Instructions (English)</label>
          <textarea id="ps_ins_en" rows="2" placeholder="Send package amount via bKash or Nagad and submit your Transaction ID (TrxID). Your ad activates automatically after Admin verification." class="input bg-white">${esc(p.instructionsEn || "")}</textarea>
        </div>

        <button type="submit" class="px-6 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-bold text-sm">
          Save bKash &amp; Nagad Settings
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
    instructionsBn: document.getElementById("ps_ins_bn").value.trim(),
    instructionsEn: document.getElementById("ps_ins_en").value.trim(),
    updatedAt: Date.now()
  };
  await saveNode("payment_settings", null, payload);
  await writeAuditLog("UPDATE_PAYMENT_SETTINGS", "PAYMENT_SETTINGS", "global", `Updated bKash (${payload.bkashNumber}) & Nagad (${payload.nagadNumber})`);
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

  return `
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      <div class="bg-gradient-to-br from-emerald-700 to-teal-700 text-white p-5 rounded-2xl shadow-sm">
        <div class="text-xs text-emerald-100 font-semibold">Total Verified Revenue</div>
        <div class="text-3xl font-extrabold mt-1">&#2547;${m.verifiedRevenueBdt.toLocaleString()}</div>
        <div class="text-xs text-emerald-100 mt-1">${m.verifiedPayments.length} verified transaction(s)</div>
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

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <h3 class="font-bold text-base text-slate-900">Revenue by Package</h3>
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
      <p class="text-xs text-slate-500 mb-4">Tracks every payment verification, advertisement approval/rejection, package edit, and direct upload.</p>
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
