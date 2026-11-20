// ============================================================================
// Doctor List BD — App Installation Tracking, Active Device Monitoring,
// Uninstall Estimation & Visual Analytics Suite
// Privacy-First: Random Installation IDs only (Zero IMEI, Phone, or Contacts)
// Uses Real RTDB Collections: app_installations, app_analytics, installation_settings
// ============================================================================

let installUiState = {
  searchId: "",
  dateFrom: "",
  dateTo: "",
  versionFilter: "ALL",
  statusFilter: "ALL",
  page: 1,
  pageSize: 25,
  trendRangePreset: "30D", // 7D | 30D | 90D | ALL | CUSTOM
  reportDateFrom: "",
  reportDateTo: "",
  _lastAppliedTab: null
};

function getInstallSettings() {
  return {
    inactiveThresholdDays: 7,
    uninstallEstimateThresholdDays: 30,
    activeWindowHours: 24,
    trackOfflineEvents: true,
    fcmUninstallCheckEnabled: true,
    ...(dbState.installation_settings || {})
  };
}

function toDhakaDateKey(ms) {
  const n = Number(ms || 0);
  if (!n || n <= 0) return "";
  const utc = new Date(n);
  const dhaka = new Date(utc.getTime() + 6 * 3600000);
  return dhaka.toISOString().slice(0, 10);
}

function getDhakaTodayKey() {
  return toDhakaDateKey(Date.now());
}

function shiftDateKey(dateKey, deltaDays) {
  const [y, m, d] = dateKey.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + deltaDays));
  return dt.toISOString().slice(0, 10);
}

function getIsoWeekKey(dateKey) {
  if (!dateKey || dateKey.length < 10) return "";
  const [y, m, d] = dateKey.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  const dayNum = dt.getUTCDay() || 7;
  dt.setUTCDate(dt.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(dt.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil((((dt - yearStart) / 86400000) + 1) / 7);
  return `${dt.getUTCFullYear()}-W${String(weekNo).padStart(2, "0")}`;
}

function formatTs(ms) {
  const n = Number(ms || 0);
  if (!n || n <= 0) return "—";
  return new Date(n).toLocaleString("en-GB", {
    year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit"
  });
}

/**
 * Classifies an installation strictly keeping Inactive devices separate
 * from Suspected/Estimated Uninstall records.
 */
function classifyInstallation(rec, settings, nowMs) {
  const lastActive = Number(rec.lastActiveDate || rec.firstInstallDate || 0);
  const firstInstall = Number(rec.firstInstallDate || lastActive || 0);
  const inactiveCutoffMs = Math.max(1, Number(settings.inactiveThresholdDays || 7)) * 86400000;
  const uninstallCutoffMs = Math.max(inactiveCutoffMs + 86400000, Number(settings.uninstallEstimateThresholdDays || 30) * 86400000);
  const activeWindowMs = Math.max(1, Number(settings.activeWindowHours || 24)) * 3600000;

  let effectiveStatus = "ACTIVE";
  let evidence = String(rec.uninstallEvidence || "");
  const fcmStatus = String(rec.fcmTokenStatus || "NOT_CONFIGURED").toUpperCase();

  if (fcmStatus === "UNREGISTERED" || evidence === "FCM_UNREGISTERED" || String(rec.status || "").toUpperCase() === "UNINSTALLED_ESTIMATE") {
    effectiveStatus = "UNINSTALLED_ESTIMATE";
    evidence = evidence || (fcmStatus === "UNREGISTERED" ? "FCM_UNREGISTERED" : "INACTIVITY_THRESHOLD");
  } else if (lastActive > 0 && (nowMs - lastActive) > uninstallCutoffMs) {
    effectiveStatus = "UNINSTALLED_ESTIMATE";
    evidence = "INACTIVITY_THRESHOLD";
  } else if (lastActive > 0 && (nowMs - lastActive) > inactiveCutoffMs) {
    effectiveStatus = "INACTIVE";
    evidence = "";
  } else {
    effectiveStatus = "ACTIVE";
    evidence = "";
  }

  const isCurrentlyActive = effectiveStatus === "ACTIVE" && lastActive > 0 && (nowMs - lastActive) <= activeWindowMs;
  const firstDateKey = rec.firstInstallDateKey || toDhakaDateKey(firstInstall);
  const lastDateKey = rec.lastActiveDateKey || toDhakaDateKey(lastActive);

  return {
    ...rec,
    installationId: String(rec.installationId || rec._key || ""),
    firstInstallDate: firstInstall,
    firstInstallDateKey: firstDateKey,
    lastActiveDate: lastActive,
    lastActiveDateKey: lastDateKey,
    appVersion: String(rec.appVersion || "Unknown"),
    androidVersion: String(rec.androidVersion || "Unknown"),
    deviceManufacturer: String(rec.deviceManufacturer || "Unknown"),
    deviceModel: String(rec.deviceModel || "Unknown"),
    effectiveStatus,
    uninstallEvidence: evidence,
    isCurrentlyActive,
    fcmTokenStatus: fcmStatus,
    totalAppOpens: Number(rec.totalAppOpens || 0)
  };
}

function getNormalizedInstallations() {
  const settings = getInstallSettings();
  const nowMs = Date.now();
  const rawMap = dbState.app_installations || {};
  const dedup = new Map();
  for (const [k, v] of Object.entries(rawMap)) {
    if (!v || typeof v !== "object") continue;
    const id = String(v.installationId || k).trim();
    if (!id) continue;
    const item = classifyInstallation({ ...v, _key: k, installationId: id }, settings, nowMs);
    if (!dedup.has(id)) {
      dedup.set(id, item);
    }
  }
  return Array.from(dedup.values()).sort((a, b) => (b.firstInstallDate || 0) - (a.firstInstallDate || 0));
}

function getAnalyticsEventsList() {
  const raw = dbState.app_analytics || {};
  const list = [];
  for (const [k, v] of Object.entries(raw)) {
    if (!v || typeof v !== "object") continue;
    const ts = Number(v.eventTimestamp || 0);
    list.push({
      eventId: String(v.eventId || k),
      installationId: String(v.installationId || ""),
      eventType: String(v.eventType || "APP_OPEN").toUpperCase(),
      eventTimestamp: ts,
      eventDateKey: String(v.eventDateKey || toDhakaDateKey(ts)),
      appVersion: String(v.appVersion || "")
    });
  }
  return list;
}

function resolveAnalyticsRange() {
  const todayKey = getDhakaTodayKey();
  if (installUiState.trendRangePreset === "CUSTOM" && installUiState.reportDateFrom && installUiState.reportDateTo) {
    return {
      fromKey: installUiState.reportDateFrom <= installUiState.reportDateTo ? installUiState.reportDateFrom : installUiState.reportDateTo,
      toKey: installUiState.reportDateTo >= installUiState.reportDateFrom ? installUiState.reportDateTo : installUiState.reportDateFrom
    };
  }
  if (installUiState.trendRangePreset === "7D") return { fromKey: shiftDateKey(todayKey, -6), toKey: todayKey };
  if (installUiState.trendRangePreset === "90D") return { fromKey: shiftDateKey(todayKey, -89), toKey: todayKey };
  if (installUiState.trendRangePreset === "ALL") return { fromKey: "2024-01-01", toKey: todayKey };
  return { fromKey: shiftDateKey(todayKey, -29), toKey: todayKey };
}

function computeInstallationAnalytics() {
  const settings = getInstallSettings();
  const installs = getNormalizedInstallations();
  const events = getAnalyticsEventsList();
  const todayKey = getDhakaTodayKey();
  const weekStartKey = shiftDateKey(todayKey, -6);
  const monthStartKey = shiftDateKey(todayKey, -29);
  const { fromKey, toKey } = resolveAnalyticsRange();

  let activeCount = 0;
  let currentlyActiveCount = 0;
  let inactiveCount = 0;
  let uninstallEstimateCount = 0;
  let uninstallFcmCount = 0;
  let uninstallInactivityCount = 0;
  let todayNewCount = 0;
  let weeklyNewCount = 0;
  let monthlyNewCount = 0;
  let fcmTokenValidCount = 0;

  const dauSet = new Set();
  const mauSet = new Set();
  const versionMap = {};
  const mfgMap = {};
  const androidMap = {};
  const dailyInstallMap = {};
  const weeklyInstallMap = {};
  const monthlyInstallMap = {};

  let openSumFromDevices = 0;
  const openEventsCountByInstall = {};

  events.forEach(ev => {
    if (!ev.installationId) return;
    if (ev.eventType === "APP_OPEN") {
      openEventsCountByInstall[ev.installationId] = (openEventsCountByInstall[ev.installationId] || 0) + 1;
    }
    if (ev.eventDateKey === todayKey) {
      dauSet.add(ev.installationId);
    }
    if (ev.eventDateKey >= monthStartKey && ev.eventDateKey <= todayKey) {
      mauSet.add(ev.installationId);
    }
  });

  installs.forEach(rec => {
    if (rec.effectiveStatus === "ACTIVE") activeCount++;
    else if (rec.effectiveStatus === "INACTIVE") inactiveCount++;
    else if (rec.effectiveStatus === "UNINSTALLED_ESTIMATE") {
      uninstallEstimateCount++;
      if (rec.uninstallEvidence === "FCM_UNREGISTERED") uninstallFcmCount++;
      else uninstallInactivityCount++;
    }
    if (rec.isCurrentlyActive) currentlyActiveCount++;
    if (rec.fcmToken && rec.fcmTokenStatus === "VALID") fcmTokenValidCount++;

    const fk = rec.firstInstallDateKey;
    if (fk === todayKey) todayNewCount++;
    if (fk && fk >= weekStartKey && fk <= todayKey) weeklyNewCount++;
    if (fk && fk >= monthStartKey && fk <= todayKey) monthlyNewCount++;

    const lk = rec.lastActiveDateKey;
    if (lk === todayKey) dauSet.add(rec.installationId);
    if (lk && lk >= monthStartKey && lk <= todayKey) mauSet.add(rec.installationId);

    const opensForDev = Math.max(rec.totalAppOpens, openEventsCountByInstall[rec.installationId] || 0);
    openSumFromDevices += opensForDev;

    // Distributions (reflecting range if CUSTOM/7D/30D/90D or ALL)
    const inRange = !fk || (fk >= fromKey && fk <= toKey);
    if (inRange) {
      versionMap[rec.appVersion] = (versionMap[rec.appVersion] || 0) + 1;
      mfgMap[rec.deviceManufacturer] = (mfgMap[rec.deviceManufacturer] || 0) + 1;
      androidMap[rec.androidVersion] = (androidMap[rec.androidVersion] || 0) + 1;

      if (fk) {
        dailyInstallMap[fk] = (dailyInstallMap[fk] || 0) + 1;
        const wk = getIsoWeekKey(fk);
        if (wk) weeklyInstallMap[wk] = (weeklyInstallMap[wk] || 0) + 1;
        const mk = fk.slice(0, 7);
        if (mk) monthlyInstallMap[mk] = (monthlyInstallMap[mk] || 0) + 1;
      }
    }
  });

  // Include any orphan APP_OPEN events not attached to loaded installation map
  const knownIds = new Set(installs.map(i => i.installationId));
  let extraOpens = 0;
  events.forEach(ev => {
    if (ev.eventType === "APP_OPEN" && !knownIds.has(ev.installationId)) extraOpens++;
  });

  // Build ordered daily series across last N days (up to 30 points visible clearly)
  const dailySeries = [];
  const startCursor = installUiState.trendRangePreset === "ALL" ? shiftDateKey(todayKey, -29) : fromKey;
  let cur = startCursor;
  let guard = 0;
  while (cur <= toKey && guard < 120) {
    dailySeries.push({ label: cur.slice(5), fullKey: cur, value: dailyInstallMap[cur] || 0 });
    cur = shiftDateKey(cur, 1);
    guard++;
  }

  // Last 8 weeks series
  const weeklyKeys = [];
  for (let i = 7; i >= 0; i--) {
    const wk = getIsoWeekKey(shiftDateKey(todayKey, -i * 7));
    if (wk && !weeklyKeys.includes(wk)) weeklyKeys.push(wk);
  }
  const weeklySeries = weeklyKeys.map(wk => ({ label: wk.slice(5), fullKey: wk, value: weeklyInstallMap[wk] || 0 }));

  // Last 6 months series
  const monthlySeries = [];
  const [ty, tm] = todayKey.split("-").map(Number);
  for (let i = 5; i >= 0; i--) {
    const dt = new Date(Date.UTC(ty, tm - 1 - i, 1));
    const mk = dt.toISOString().slice(0, 7);
    monthlySeries.push({ label: mk, fullKey: mk, value: monthlyInstallMap[mk] || 0 });
  }

  const sortDist = (obj) => Object.entries(obj)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);

  const totalInstalls = installs.length;
  const dau = dauSet.size;
  const mau = mauSet.size;

  return {
    settings,
    installs,
    totalInstalls,
    activeCount,
    currentlyActiveCount,
    inactiveCount,
    uninstallEstimateCount,
    uninstallFcmCount,
    uninstallInactivityCount,
    todayNewCount,
    weeklyNewCount,
    monthlyNewCount,
    totalAppOpens: openSumFromDevices + extraOpens,
    dau,
    mau,
    stickinessPct: mau > 0 ? Math.round((dau / mau) * 100) : 0,
    fcmTokenValidCount,
    fromKey,
    toKey,
    dailySeries,
    weeklySeries,
    monthlySeries,
    versionDist: sortDist(versionMap),
    mfgDist: sortDist(mfgMap),
    androidDist: sortDist(androidMap)
  };
}

// ============================================================================
// INTERACTIVE SVG CHARTS (Zero External CDN Failures • Light & Dark Adaptive)
// ============================================================================
function renderBarTrendSvg(series, colorHex, emptyMsg) {
  if (!series.length) return `<div class="h-44 flex items-center justify-center text-xs text-slate-400">${esc(emptyMsg)}</div>`;
  const maxVal = Math.max(1, ...series.map(s => s.value));
  const w = 520, h = 175, padL = 32, padR = 10, padT = 16, padB = 28;
  const plotW = w - padL - padR;
  const plotH = h - padT - padB;
  const step = plotW / series.length;
  const barW = Math.max(4, Math.min(26, step * 0.66));
  const labelEvery = series.length > 16 ? Math.ceil(series.length / 8) : 1;

  const bars = series.map((pt, idx) => {
    const bh = pt.value > 0 ? Math.max(4, Math.round((pt.value / maxVal) * plotH)) : 2;
    const x = padL + idx * step + (step - barW) / 2;
    const y = padT + plotH - bh;
    const showLbl = idx % labelEvery === 0 || idx === series.length - 1;
    return `
      <g class="group cursor-pointer">
        <title>${esc(pt.fullKey)}: ${pt.value} installations</title>
        <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${bh}" rx="4"
          fill="${pt.value > 0 ? colorHex : '#CBD5E1'}" opacity="${pt.value > 0 ? '0.9' : '0.35'}"></rect>
        ${pt.value > 0 && series.length <= 14 ? `<text x="${(x + barW / 2).toFixed(1)}" y="${(y - 4).toFixed(1)}" text-anchor="middle" font-size="9" font-weight="700" fill="currentColor">${pt.value}</text>` : ""}
        ${showLbl ? `<text x="${(x + barW / 2).toFixed(1)}" y="${h - 8}" text-anchor="middle" font-size="9" fill="#64748B">${esc(pt.label)}</text>` : ""}
      </g>
    `;
  }).join("");

  return `
    <svg viewBox="0 0 ${w} ${h}" class="w-full h-44 text-slate-700 overflow-visible">
      <line x1="${padL}" y1="${padT}" x2="${w - padR}" y2="${padT}" stroke="#E2E8F0" stroke-dasharray="3 3" />
      <line x1="${padL}" y1="${padT + plotH / 2}" x2="${w - padR}" y2="${padT + plotH / 2}" stroke="#E2E8F0" stroke-dasharray="3 3" />
      <line x1="${padL}" y1="${padT + plotH}" x2="${w - padR}" y2="${padT + plotH}" stroke="#CBD5E1" />
      <text x="${padL - 5}" y="${padT + 4}" text-anchor="end" font-size="9" fill="#64748B">${maxVal}</text>
      <text x="${padL - 5}" y="${padT + plotH + 3}" text-anchor="end" font-size="9" fill="#64748B">0</text>
      ${bars}
    </svg>
  `;
}

function renderStatusDonutSvg(active, inactive, uninstallEst) {
  const total = active + inactive + uninstallEst;
  if (total === 0) {
    return `<div class="h-44 flex items-center justify-center text-xs text-slate-400">No installation data recorded yet</div>`;
  }
  const r = 54, cx = 74, cy = 74, circ = 2 * Math.PI * r;
  const segs = [
    { label: "Active", val: active, color: "#10B981" },
    { label: "Inactive", val: inactive, color: "#F59E0B" },
    { label: "Suspected Uninstall", val: uninstallEst, color: "#EF4444" }
  ];
  let offset = 0;
  const circles = segs.map(s => {
    const len = (s.val / total) * circ;
    const dash = `${len.toFixed(2)} ${(circ - len).toFixed(2)}`;
    const c = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${s.color}" stroke-width="18"
      stroke-dasharray="${dash}" stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 ${cx} ${cy})">
      <title>${s.label}: ${s.val} (${Math.round((s.val / total) * 100)}%)</title></circle>`;
    offset += len;
    return c;
  }).join("");

  return `
    <div class="flex flex-wrap items-center gap-5">
      <svg viewBox="0 0 148 148" class="w-36 h-36 flex-shrink-0">
        <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#E2E8F0" stroke-width="18"></circle>
        ${circles}
        <text x="${cx}" y="${cy - 2}" text-anchor="middle" font-size="18" font-weight="800" fill="currentColor">${total}</text>
        <text x="${cx}" y="${cy + 14}" text-anchor="middle" font-size="9" fill="#64748B">DEVICES</text>
      </svg>
      <div class="space-y-2 text-xs flex-1 min-w-[140px]">
        ${segs.map(s => `
          <div class="flex items-center justify-between gap-2">
            <span class="flex items-center gap-2 font-semibold">
              <span class="w-2.5 h-2.5 rounded-full" style="background:${s.color}"></span>
              ${s.label}
            </span>
            <span class="font-bold">${s.val} <span class="text-slate-400 font-normal">(${Math.round((s.val / total) * 100)}%)</span></span>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderDistributionBars(items, barColorClass, emptyMsg) {
  if (!items.length) return `<div class="text-xs text-slate-400 py-4 text-center">${esc(emptyMsg)}</div>`;
  const total = items.reduce((s, x) => s + x.count, 0) || 1;
  return `
    <div class="space-y-2.5 max-h-56 overflow-y-auto pr-1">
      ${items.slice(0, 8).map(item => {
        const pct = Math.round((item.count / total) * 100);
        return `
          <div>
            <div class="flex items-center justify-between text-xs mb-1 gap-2">
              <span class="font-semibold truncate">${esc(item.name)}</span>
              <span class="font-bold flex-shrink-0">${item.count} <span class="text-slate-400 font-normal">(${pct}%)</span></span>
            </div>
            <div class="h-2 rounded-full bg-slate-100 overflow-hidden">
              <div class="h-full rounded-full ${barColorClass}" style="width:${Math.max(4, pct)}%"></div>
            </div>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

// ============================================================================
// MAIN DASHBOARD TAB: APP INSTALLATION & ANALYTICS OVERVIEW
// ============================================================================
function renderInstallAnalytics() {
  const a = computeInstallationAnalytics();
  const s = a.settings;

  return `
    <div class="space-y-6">
      <!-- Top Control Bar: Date Range Selector + Download Report + Refresh -->
      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="font-bold text-base text-slate-900">App Installation Tracking &amp; Real-Time Analytics</h3>
          <p class="text-xs text-slate-500">Privacy-safe anonymous installation IDs &bull; Server-side timestamps &bull; Offline event sync active</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          ${[
            ["7D", "7 Days"],
            ["30D", "30 Days"],
            ["90D", "90 Days"],
            ["ALL", "All Time"]
          ].map(([k, lbl]) => `
            <button type="button" onclick="installUiState.trendRangePreset='${k}';renderTab()"
              class="px-3 py-1.5 rounded-xl text-xs font-bold border ${installUiState.trendRangePreset === k ? "bg-sky-600 text-white border-sky-600" : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"}">${lbl}</button>
          `).join("")}
          <input id="ia_from" type="date" value="${esc(a.fromKey)}" onchange="onCustomInstallDateChange()" class="px-2.5 py-1.5 rounded-xl border border-slate-200 text-xs" />
          <span class="text-xs text-slate-400">to</span>
          <input id="ia_to" type="date" value="${esc(a.toKey)}" onchange="onCustomInstallDateChange()" class="px-2.5 py-1.5 rounded-xl border border-slate-200 text-xs" />
          <button type="button" onclick="exportInstallAnalyticsReportCsv()" class="px-3 py-1.5 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold">
            Download Report (CSV)
          </button>
          <button type="button" onclick="loadAllData()" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold">
            Refresh
          </button>
        </div>
      </div>

      <!-- 9 Required KPI Statistic Cards -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
        <div onclick="openInstallFilterTab('ALL')" class="cursor-pointer p-4 rounded-2xl bg-gradient-to-br from-sky-600 to-sky-700 text-white shadow-sm hover:opacity-95 transition">
          <div class="text-xs text-sky-100 font-semibold">Total First-Time Installations</div>
          <div class="text-3xl font-extrabold mt-1">${a.totalInstalls.toLocaleString()}</div>
          <div class="text-[11px] text-sky-100 mt-1">1 count per unique Install ID</div>
        </div>

        <div onclick="openInstallFilterTab('ACTIVE')" class="cursor-pointer p-4 rounded-2xl bg-gradient-to-br from-emerald-600 to-teal-700 text-white shadow-sm hover:opacity-95 transition">
          <div class="text-xs text-emerald-100 font-semibold">Currently Active Devices</div>
          <div class="text-3xl font-extrabold mt-1">${a.currentlyActiveCount.toLocaleString()}</div>
          <div class="text-[11px] text-emerald-100 mt-1">${a.activeCount} within ${s.inactiveThresholdDays}d window</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Today's New Installations</div>
          <div class="text-3xl font-extrabold text-sky-700 mt-1">${a.todayNewCount.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">${esc(getDhakaTodayKey())}</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Weekly Installations (7d)</div>
          <div class="text-3xl font-extrabold text-indigo-700 mt-1">${a.weeklyNewCount.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">Last 7 days</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Monthly Installations (30d)</div>
          <div class="text-3xl font-extrabold text-teal-700 mt-1">${a.monthlyNewCount.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">Last 30 days</div>
        </div>

        <div onclick="openInstallFilterTab('UNINSTALLED_ESTIMATE')" class="cursor-pointer p-4 rounded-2xl bg-rose-50 border border-rose-200 shadow-sm hover:bg-rose-100/70 transition">
          <div class="text-xs text-rose-700 font-bold">Total Uninstall Estimates</div>
          <div class="text-3xl font-extrabold text-rose-800 mt-1">${a.uninstallEstimateCount.toLocaleString()}</div>
          <div class="text-[10px] text-rose-600 mt-1">FCM Unreg: ${a.uninstallFcmCount} &bull; Inactivity &gt;${s.uninstallEstimateThresholdDays}d: ${a.uninstallInactivityCount}</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Total App Opens</div>
          <div class="text-3xl font-extrabold text-slate-900 mt-1">${a.totalAppOpens.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">Verified launch sessions</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Daily Active Users (DAU)</div>
          <div class="text-3xl font-extrabold text-emerald-700 mt-1">${a.dau.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">Unique active devices today</div>
        </div>

        <div class="p-4 rounded-2xl bg-white border border-slate-200 shadow-sm">
          <div class="text-xs text-slate-500 font-semibold">Monthly Active Users (MAU)</div>
          <div class="text-3xl font-extrabold text-violet-700 mt-1">${a.mau.toLocaleString()}</div>
          <div class="text-[11px] text-slate-400 mt-1">Stickiness (DAU/MAU): ${a.stickinessPct}%</div>
        </div>

        <div onclick="openInstallFilterTab('INACTIVE')" class="cursor-pointer p-4 rounded-2xl bg-amber-50 border border-amber-200 shadow-sm hover:bg-amber-100/70 transition">
          <div class="text-xs text-amber-800 font-bold">Inactive Devices (Separate)</div>
          <div class="text-3xl font-extrabold text-amber-900 mt-1">${a.inactiveCount.toLocaleString()}</div>
          <div class="text-[10px] text-amber-700 mt-1">Inactive ${s.inactiveThresholdDays}–${s.uninstallEstimateThresholdDays}d (not uninstalled)</div>
        </div>
      </div>

      <!-- Transparency Notice on Uninstall Estimation -->
      <div class="p-3.5 rounded-2xl bg-sky-50 border border-sky-200 text-xs text-sky-900 flex flex-wrap items-center justify-between gap-2">
        <div>
          <strong>Uninstall Detection Policy:</strong> Android does not provide an exact uninstall callback to apps. Suspected uninstalls use
          <strong>FCM Unregistered Push Token evidence</strong> (${a.uninstallFcmCount}) + configurable <strong>Inactivity &gt;${s.uninstallEstimateThresholdDays}d threshold</strong> (${a.uninstallInactivityCount}),
          strictly separated from <strong>Inactive Devices</strong> (${a.inactiveCount}).
        </div>
        <button type="button" onclick="openInstallFilterTab('ALL')" class="px-3 py-1.5 rounded-xl bg-sky-700 text-white font-bold text-xs">
          View Installation History &rarr;
        </button>
      </div>

      <!-- Interactive Visual Charts Grid (All 7 Charts) -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center justify-between mb-2">
            <h4 class="font-bold text-sm text-slate-900">1. Daily Installation Trends</h4>
            <span class="text-[11px] text-slate-500">${esc(a.fromKey)} &rarr; ${esc(a.toKey)}</span>
          </div>
          ${renderBarTrendSvg(a.dailySeries, "#0284C7", "No daily installations in selected range")}
        </div>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center justify-between mb-2">
            <h4 class="font-bold text-sm text-slate-900">2. Weekly Installation Trends (8 Weeks)</h4>
            <span class="text-[11px] text-slate-500">ISO Weekly Breakdown</span>
          </div>
          ${renderBarTrendSvg(a.weeklySeries, "#0D9488", "No weekly installations recorded yet")}
        </div>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center justify-between mb-2">
            <h4 class="font-bold text-sm text-slate-900">3. Monthly Installation Trends (6 Months)</h4>
            <span class="text-[11px] text-slate-500">Monthly Growth</span>
          </div>
          ${renderBarTrendSvg(a.monthlySeries, "#4F46E5", "No monthly installations recorded yet")}
        </div>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <h4 class="font-bold text-sm text-slate-900">4. Active vs Inactive vs Suspected Uninstalled</h4>
            <span class="text-[11px] text-slate-500">Separated Status</span>
          </div>
          ${renderStatusDonutSvg(a.activeCount, a.inactiveCount, a.uninstallEstimateCount)}
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h4 class="font-bold text-sm text-slate-900 mb-3">5. App Version Distribution</h4>
          ${renderDistributionBars(a.versionDist, "bg-sky-600", "No version data yet")}
        </div>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h4 class="font-bold text-sm text-slate-900 mb-3">6. Device Manufacturer Distribution</h4>
          ${renderDistributionBars(a.mfgDist, "bg-teal-600", "No manufacturer data yet")}
        </div>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <h4 class="font-bold text-sm text-slate-900 mb-3">7. Android Version Distribution</h4>
          ${renderDistributionBars(a.androidDist, "bg-indigo-600", "No Android OS data yet")}
        </div>
      </div>

      <!-- Configurable Thresholds + FCM Token Uninstall Verification -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <form onsubmit="submitInstallationSettings(event)" class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-4">
          <div>
            <h4 class="font-bold text-sm text-slate-900">Active / Inactive &amp; Uninstall Estimate Thresholds</h4>
            <p class="text-xs text-slate-500">Controls how devices are classified into Active, Inactive, or Suspected Uninstalled.</p>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Currently Active (Hours)</label>
              <input id="is_active_hrs" type="number" min="1" max="168" required value="${esc(s.activeWindowHours ?? 24)}" class="input text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Inactive After (Days)</label>
              <input id="is_inactive_days" type="number" min="1" max="180" required value="${esc(s.inactiveThresholdDays ?? 7)}" class="input text-xs" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Uninstall Estimate (Days)</label>
              <input id="is_uninstall_days" type="number" min="7" max="365" required value="${esc(s.uninstallEstimateThresholdDays ?? 30)}" class="input text-xs" />
            </div>
          </div>
          <button type="submit" class="px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold">
            Save Activity Thresholds
          </button>
        </form>

        <div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm space-y-3">
          <div class="flex items-center justify-between gap-2">
            <div>
              <h4 class="font-bold text-sm text-slate-900">FCM Token Uninstall Evidence Tool</h4>
              <p class="text-xs text-slate-500">Valid FCM Tokens: <strong>${a.fcmTokenValidCount}</strong> &bull; Confirmed Unregistered: <strong>${a.uninstallFcmCount}</strong></p>
            </div>
          </div>
          <p class="text-[11px] text-slate-600">
            Mark specific FCM tokens reported as <code class="px-1 bg-slate-100 rounded">UNREGISTERED</code> / <code class="px-1 bg-slate-100 rounded">NotRegistered</code> by Firebase Cloud Messaging, or run an optional ephemeral dry-run token probe (OAuth token never stored in DB or APK):
          </p>
          <textarea id="fcm_unreg_tokens" rows="2" placeholder="Paste unregistered FCM tokens or Installation IDs (comma or newline separated)..." class="input text-xs"></textarea>
          <div class="flex flex-wrap gap-2">
            <button type="button" onclick="markUnregisteredTokensManual()" class="px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold">
              Flag as Suspected Uninstalled
            </button>
            <button type="button" onclick="openInstallFilterTab('UNINSTALLED_ESTIMATE')" class="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold">
              Inspect Uninstall Estimates
            </button>
          </div>
        </div>
      </div>
    </div>
  `;
}

// ============================================================================
// INSTALLATION HISTORY & FILTERED DEVICE VIEWS
// ============================================================================
function renderInstallHistory(presetStatus) {
  const curTab = typeof activeTab !== "undefined" ? activeTab : "";
  if (installUiState._lastAppliedTab !== curTab) {
    installUiState._lastAppliedTab = curTab;
    installUiState.statusFilter = presetStatus || "ALL";
    installUiState.page = 1;
  }
  const all = getNormalizedInstallations();
  const versions = Array.from(new Set(all.map(r => r.appVersion).filter(Boolean))).sort();

  const q = installUiState.searchId.trim().toLowerCase();
  const filtered = all.filter(rec => {
    const matchSearch = !q ||
      rec.installationId.toLowerCase().includes(q) ||
      rec.deviceModel.toLowerCase().includes(q) ||
      rec.deviceManufacturer.toLowerCase().includes(q);
    const matchVer = installUiState.versionFilter === "ALL" || rec.appVersion === installUiState.versionFilter;
    const matchStatus = installUiState.statusFilter === "ALL" || rec.effectiveStatus === installUiState.statusFilter;
    const fk = rec.firstInstallDateKey;
    const matchFrom = !installUiState.dateFrom || (fk && fk >= installUiState.dateFrom);
    const matchTo = !installUiState.dateTo || (fk && fk <= installUiState.dateTo);
    return matchSearch && matchVer && matchStatus && matchFrom && matchTo;
  });

  const pageSize = Math.max(5, Number(installUiState.pageSize || 25));
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  if (installUiState.page > totalPages) installUiState.page = totalPages;
  if (installUiState.page < 1) installUiState.page = 1;
  const startIdx = (installUiState.page - 1) * pageSize;
  const pageItems = filtered.slice(startIdx, startIdx + pageSize);

  const statusBadge = (rec) => {
    if (rec.effectiveStatus === "ACTIVE") {
      return `<span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800">Active</span>`;
    }
    if (rec.effectiveStatus === "INACTIVE") {
      return `<span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-100 text-amber-800">Inactive</span>`;
    }
    const evTag = rec.uninstallEvidence === "FCM_UNREGISTERED" ? "FCM Unregistered" : "Inactivity Est.";
    return `<span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-100 text-rose-800" title="${esc(evTag)}">Uninstalled Estimate (${esc(evTag)})</span>`;
  };

  return `
    <div class="space-y-4">
      <!-- Status Quick Pills -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap gap-2">
          ${[
            ["ALL", `All Installations (${all.length})`],
            ["ACTIVE", `Active Devices (${all.filter(x => x.effectiveStatus === "ACTIVE").length})`],
            ["INACTIVE", `Inactive Devices (${all.filter(x => x.effectiveStatus === "INACTIVE").length})`],
            ["UNINSTALLED_ESTIMATE", `Uninstall Estimates (${all.filter(x => x.effectiveStatus === "UNINSTALLED_ESTIMATE").length})`]
          ].map(([k, lbl]) => `
            <button type="button" onclick="installUiState.statusFilter='${k}';installUiState.page=1;renderTab()"
              class="px-3.5 py-2 rounded-xl text-xs font-bold border transition ${installUiState.statusFilter === k ? "bg-sky-600 text-white border-sky-600" : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"}">${lbl}</button>
          `).join("")}
        </div>
        <div class="flex flex-wrap gap-2">
          <button type="button" onclick="exportInstallationsCsv()" class="px-3.5 py-2 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold">
            Export CSV
          </button>
          <button type="button" onclick="exportInstallationsExcel()" class="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold">
            Export Excel (.xls)
          </button>
          <button type="button" onclick="loadAllData()" class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold">
            Refresh
          </button>
        </div>
      </div>

      <!-- Search & Filters -->
      <div class="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end">
        <div class="lg:col-span-2">
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">Search Installation ID / Model</label>
          <input type="text" value="${esc(installUiState.searchId)}" oninput="installUiState.searchId=this.value;installUiState.page=1;renderTab()"
            placeholder="e.g. inst_..." class="input text-xs" />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">Install Date From</label>
          <input type="date" value="${esc(installUiState.dateFrom)}" onchange="installUiState.dateFrom=this.value;installUiState.page=1;renderTab()" class="input text-xs" />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">Install Date To</label>
          <input type="date" value="${esc(installUiState.dateTo)}" onchange="installUiState.dateTo=this.value;installUiState.page=1;renderTab()" class="input text-xs" />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-600 mb-1">App Version</label>
          <select onchange="installUiState.versionFilter=this.value;installUiState.page=1;renderTab()" class="input text-xs bg-white">
            <option value="ALL">All Versions</option>
            ${versions.map(v => `<option value="${esc(v)}" ${installUiState.versionFilter === v ? "selected" : ""}>${esc(v)}</option>`).join("")}
          </select>
        </div>
        <div class="flex gap-2">
          <select onchange="installUiState.pageSize=Number(this.value);installUiState.page=1;renderTab()" class="input text-xs bg-white">
            ${[15, 25, 50, 100].map(sz => `<option value="${sz}" ${pageSize === sz ? "selected" : ""}>${sz} / page</option>`).join("")}
          </select>
          <button type="button" onclick="resetInstallFilters()" class="px-3 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold">Reset</button>
        </div>
      </div>

      <!-- Installation Table -->
      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-50 border-b border-slate-200 text-slate-600 uppercase text-[11px]">
                <th class="py-3 px-3 font-bold">#</th>
                <th class="py-3 px-3 font-bold">Installation ID</th>
                <th class="py-3 px-3 font-bold">First Install Date</th>
                <th class="py-3 px-3 font-bold">Last Active Date</th>
                <th class="py-3 px-3 font-bold">App Version</th>
                <th class="py-3 px-3 font-bold">Android Version</th>
                <th class="py-3 px-3 font-bold">Manufacturer</th>
                <th class="py-3 px-3 font-bold">Device Model</th>
                <th class="py-3 px-3 font-bold">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              ${pageItems.length === 0 ? `
                <tr><td colspan="9" class="py-10 text-center text-slate-400">No installation records match current filters.</td></tr>
              ` : pageItems.map((rec, idx) => `
                <tr class="hover:bg-slate-50/80 transition">
                  <td class="py-3 px-3 font-semibold text-slate-500">${startIdx + idx + 1}</td>
                  <td class="py-3 px-3 font-mono text-[11px] font-bold text-sky-800">${esc(rec.installationId)}</td>
                  <td class="py-3 px-3 whitespace-nowrap">${esc(formatTs(rec.firstInstallDate))}</td>
                  <td class="py-3 px-3 whitespace-nowrap">${esc(formatTs(rec.lastActiveDate))}</td>
                  <td class="py-3 px-3"><span class="px-2 py-0.5 rounded bg-slate-100 font-semibold">${esc(rec.appVersion)}</span></td>
                  <td class="py-3 px-3">${esc(rec.androidVersion)}</td>
                  <td class="py-3 px-3 font-semibold">${esc(rec.deviceManufacturer)}</td>
                  <td class="py-3 px-3">${esc(rec.deviceModel)}</td>
                  <td class="py-3 px-3 whitespace-nowrap">${statusBadge(rec)}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>

        <!-- Pagination Bar -->
        <div class="p-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div class="text-slate-600">
            Showing <strong>${filtered.length === 0 ? 0 : startIdx + 1}–${Math.min(filtered.length, startIdx + pageSize)}</strong> of <strong>${filtered.length}</strong> installations
          </div>
          <div class="flex items-center gap-2">
            <button type="button" ${installUiState.page <= 1 ? "disabled" : ""} onclick="installUiState.page--;renderTab()"
              class="px-3 py-1.5 rounded-lg border border-slate-200 font-bold disabled:opacity-40 hover:bg-slate-50">&larr; Prev</button>
            <span class="font-bold">Page ${installUiState.page} / ${totalPages}</span>
            <button type="button" ${installUiState.page >= totalPages ? "disabled" : ""} onclick="installUiState.page++;renderTab()"
              class="px-3 py-1.5 rounded-lg border border-slate-200 font-bold disabled:opacity-40 hover:bg-slate-50">Next &rarr;</button>
          </div>
        </div>
      </div>
    </div>
  `;
}

// ============================================================================
// ADMIN ACTIONS: FILTERS, THRESHOLDS, FCM UNINSTALL MARKING & EXPORTS
// ============================================================================
function onCustomInstallDateChange() {
  const f = document.getElementById("ia_from")?.value || "";
  const t = document.getElementById("ia_to")?.value || "";
  if (f && t) {
    installUiState.trendRangePreset = "CUSTOM";
    installUiState.reportDateFrom = f;
    installUiState.reportDateTo = t;
    renderTab();
  }
}

function openInstallFilterTab(status) {
  if (status === "ACTIVE") return switchTab("install_active");
  if (status === "INACTIVE") return switchTab("install_inactive");
  if (status === "UNINSTALLED_ESTIMATE") return switchTab("install_uninstalls");
  return switchTab("install_history");
}

function resetInstallFilters() {
  installUiState.searchId = "";
  installUiState.dateFrom = "";
  installUiState.dateTo = "";
  installUiState.versionFilter = "ALL";
  installUiState.statusFilter = "ALL";
  installUiState.page = 1;
  renderTab();
}

async function submitInstallationSettings(e) {
  e.preventDefault();
  const activeHrs = Math.max(1, parseInt(document.getElementById("is_active_hrs").value, 10) || 24);
  const inactDays = Math.max(1, parseInt(document.getElementById("is_inactive_days").value, 10) || 7);
  const uninstDays = Math.max(inactDays + 1, parseInt(document.getElementById("is_uninstall_days").value, 10) || 30);
  const payload = {
    activeWindowHours: activeHrs,
    inactiveThresholdDays: inactDays,
    uninstallEstimateThresholdDays: uninstDays,
    trackOfflineEvents: true,
    fcmUninstallCheckEnabled: true,
    updatedAt: Date.now()
  };
  await saveNode("installation_settings", null, payload);
  if (typeof writeAuditLog === "function") {
    await writeAuditLog("UPDATE_INSTALL_THRESHOLDS", "INSTALLATIONS", "installation_settings", `Active=${activeHrs}h, Inactive=${inactDays}d, UninstallEst=${uninstDays}d`);
  }
}

async function markUnregisteredTokensManual() {
  const raw = document.getElementById("fcm_unreg_tokens")?.value || "";
  const tokens = raw.split(/[\s,;\n]+/).map(s => s.trim()).filter(Boolean);
  if (!tokens.length) return toast("Enter at least one unregistered FCM token or Installation ID.");
  const tokenSet = new Set(tokens);
  const patchMap = {};
  let matched = 0;

  for (const [id, rec] of Object.entries(dbState.app_installations || {})) {
    if (!rec) continue;
    const instId = String(rec.installationId || id);
    if (tokenSet.has(instId) || (rec.fcmToken && tokenSet.has(String(rec.fcmToken).trim()))) {
      patchMap[id] = {
        ...rec,
        status: "UNINSTALLED_ESTIMATE",
        fcmTokenStatus: "UNREGISTERED",
        uninstallEvidence: "FCM_UNREGISTERED",
        uninstallSuspectedAt: Date.now(),
        updatedAt: Date.now()
      };
      matched++;
    }
  }
  if (!matched) return toast("No matching FCM token or Installation ID found.");
  await patchCollectionNodes("app_installations", patchMap, `Flagged ${matched} device(s) as Suspected Uninstalled (FCM Unregistered).`);
  if (typeof writeAuditLog === "function") {
    await writeAuditLog("FLAG_FCM_UNREGISTERED", "INSTALLATIONS", "app_installations", `Flagged ${matched} devices via unregistered FCM token evidence`);
  }
}

function getFilteredInstallationsForExport() {
  const all = getNormalizedInstallations();
  const q = installUiState.searchId.trim().toLowerCase();
  return all.filter(rec => {
    const matchSearch = !q || rec.installationId.toLowerCase().includes(q) || rec.deviceModel.toLowerCase().includes(q);
    const matchVer = installUiState.versionFilter === "ALL" || rec.appVersion === installUiState.versionFilter;
    const matchStatus = installUiState.statusFilter === "ALL" || rec.effectiveStatus === installUiState.statusFilter;
    const fk = rec.firstInstallDateKey;
    const matchFrom = !installUiState.dateFrom || (fk && fk >= installUiState.dateFrom);
    const matchTo = !installUiState.dateTo || (fk && fk <= installUiState.dateTo);
    return matchSearch && matchVer && matchStatus && matchFrom && matchTo;
  });
}

function exportInstallationsCsv() {
  const rows = getFilteredInstallationsForExport();
  if (!rows.length) return toast("No installation records to export.");
  const headers = [
    "Serial Number", "Installation ID", "First Installation Date", "Last Active Date",
    "App Version", "Android Version", "Device Manufacturer", "Device Model", "Installation Status", "Uninstall Evidence"
  ];
  const lines = rows.map((r, i) => [
    i + 1,
    r.installationId,
    formatTs(r.firstInstallDate),
    formatTs(r.lastActiveDate),
    r.appVersion,
    r.androidVersion,
    r.deviceManufacturer,
    r.deviceModel,
    r.effectiveStatus,
    r.uninstallEvidence || ""
  ].map(v => `"${String(v ?? "").replace(/"/g, '""')}"`).join(","));

  const csv = "\uFEFF" + [headers.join(","), ...lines].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `doctor_list_bd_installations_${getDhakaTodayKey()}.csv`;
  a.click();
  toast(`Exported ${rows.length} installations to CSV.`);
}

function exportInstallationsExcel() {
  const rows = getFilteredInstallationsForExport();
  if (!rows.length) return toast("No installation records to export.");
  const html = `
    <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">
    <head><meta charset="UTF-8" /></head>
    <body>
      <table border="1">
        <thead>
          <tr>
            <th>Serial No</th><th>Installation ID</th><th>First Installation Date</th><th>Last Active Date</th>
            <th>App Version</th><th>Android Version</th><th>Device Manufacturer</th><th>Device Model</th>
            <th>Status</th><th>Evidence</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((r, i) => `
            <tr>
              <td>${i + 1}</td>
              <td>${esc(r.installationId)}</td>
              <td>${esc(formatTs(r.firstInstallDate))}</td>
              <td>${esc(formatTs(r.lastActiveDate))}</td>
              <td>${esc(r.appVersion)}</td>
              <td>${esc(r.androidVersion)}</td>
              <td>${esc(r.deviceManufacturer)}</td>
              <td>${esc(r.deviceModel)}</td>
              <td>${esc(r.effectiveStatus)}</td>
              <td>${esc(r.uninstallEvidence || "")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </body></html>
  `;
  const blob = new Blob(["\uFEFF" + html], { type: "application/vnd.ms-excel;charset=utf-8;" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `doctor_list_bd_installations_${getDhakaTodayKey()}.xls`;
  a.click();
  toast(`Exported ${rows.length} installations to Excel (.xls).`);
}

function exportInstallAnalyticsReportCsv() {
  const a = computeInstallationAnalytics();
  const lines = [
    ["Metric", "Value"],
    ["Report Range", `${a.fromKey} to ${a.toKey}`],
    ["Total First-Time Installations", a.totalInstalls],
    ["Currently Active Devices", a.currentlyActiveCount],
    ["Today New Installations", a.todayNewCount],
    ["Weekly Installations", a.weeklyNewCount],
    ["Monthly Installations", a.monthlyNewCount],
    ["Total Uninstall Estimates (Suspected)", a.uninstallEstimateCount],
    ["Inactive Devices (Separate)", a.inactiveCount],
    ["Total App Opens", a.totalAppOpens],
    ["Daily Active Users (DAU)", a.dau],
    ["Monthly Active Users (MAU)", a.mau],
    ["", ""],
    ["Date", "Daily Installations"],
    ...a.dailySeries.map(d => [d.fullKey, d.value]),
    ["", ""],
    ["App Version", "Devices"],
    ...a.versionDist.map(v => [v.name, v.count]),
    ["", ""],
    ["Device Manufacturer", "Devices"],
    ...a.mfgDist.map(m => [m.name, m.count]),
    ["", ""],
    ["Android Version", "Devices"],
    ...a.androidDist.map(o => [o.name, o.count])
  ].map(row => row.map(v => `"${String(v ?? "").replace(/"/g, '""')}"`).join(",")).join("\n");

  const blob = new Blob(["\uFEFF" + lines], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `doctor_list_bd_install_analytics_report_${getDhakaTodayKey()}.csv`;
  link.click();
  toast("Downloaded full Installation Analytics report CSV.");
}
