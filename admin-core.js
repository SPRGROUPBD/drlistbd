// Doctor List BD — Core Admin Modules (no advertisement features)
async function sha256Hex(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, "0")).join("");
}
function generateSaltHex(byteLen = 16) {
  const bytes = new Uint8Array(byteLen);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map(b => b.toString(16).padStart(2, "0")).join("");
}
async function hashCredential(password, salt) {
  return sha256Hex(String(salt) + ":" + String(password));
}
function formatDateOnly(ms) {
  const n = Number(ms || 0);
  return n > 0 ? new Date(n).toLocaleDateString("en-GB", {year:"numeric",month:"short",day:"2-digit"}) : "—";
}
function statusBadgeHtml(status) {
  const active = String(status || "").toUpperCase() === "ACTIVE";
  return '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold ' +
    (active ? "bg-emerald-100 text-emerald-800" : "bg-rose-100 text-rose-800") + '">' +
    esc(status || "UNKNOWN") + "</span>";
}
async function writeAuditLog(action, targetType, targetId, summary) {
  const actor = sessionStorage.getItem("dlbd_admin_name") || sessionStorage.getItem("dlbd_admin_id") || "Super Admin";
  const id = uid("log");
  const entry = {id, actor, action, targetType, targetId, summary, timestamp: Date.now()};
  try {
    const res = await fetch(RTDB_BASE + "/audit_logs/" + id + ".json", {
      method:"PUT", headers:{"Content-Type":"application/json"}, body:JSON.stringify(entry)
    });
    if (res.ok) {
      if (!dbState.audit_logs) dbState.audit_logs = {};
      dbState.audit_logs[id] = entry;
    }
  } catch (e) { console.warn("Audit log save failed", e); }
}
function renderAuditLogs() {
  const logs = Object.values(dbState.audit_logs || {}).sort((a,b) => Number(b.timestamp||0)-Number(a.timestamp||0)).slice(0,150);
  return '<section class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">' +
    '<h3 class="font-bold text-base mb-1">Admin Audit Logs (' + logs.length + ')</h3>' +
    '<p class="text-xs text-slate-500 mb-4">Admin activity history for directory, settings and account changes.</p>' +
    (logs.length ? '<div class="space-y-2">' + logs.map(l =>
      '<div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex flex-wrap justify-between gap-2 text-xs">' +
      '<div><b class="text-sky-800">' + esc(l.action) + '</b> — ' + esc(l.summary) +
      '<span class="text-slate-400 ml-2">by ' + esc(l.actor) + '</span></div>' +
      '<span class="text-slate-500">' + formatDateOnly(l.timestamp) + ' ' +
      (l.timestamp ? new Date(Number(l.timestamp)).toLocaleTimeString("en-GB",{hour:"2-digit",minute:"2-digit"}) : "") + '</span></div>'
    ).join("") + '</div>' : '<p class="text-xs text-slate-400 py-6 text-center">No audit events recorded yet.</p>') +
    '</section>';
}
const SUB_ADMIN_PERMISSIONS = [
  {key:"addDoctor",label:"Add Doctor"},{key:"editDoctor",label:"Edit Doctor"},{key:"deleteDoctor",label:"Delete Doctor"},{key:"manageDoctorInfo",label:"Manage Doctor Information"},
  {key:"addDoctorChamber",label:"Add Doctor Chamber"},{key:"editDoctorChamber",label:"Edit Doctor Chamber"},
  {key:"addHospital",label:"Add Hospital"},{key:"editHospital",label:"Edit Hospital"},{key:"manageHospitalInfo",label:"Manage Hospital Information"},
  {key:"addDiagnosticCenter",label:"Add Diagnostic Center"},{key:"editDiagnosticCenter",label:"Edit Diagnostic Center"},{key:"manageDiagnosticInfo",label:"Manage Diagnostic Information"},
  {key:"viewDashboard",label:"View Dashboard"}
];
const TAB_PERMISSION_RULES = {
  dashboard:["viewDashboard"], doctors:["addDoctor","editDoctor","deleteDoctor","manageDoctorInfo"],
  chambers:["addDoctorChamber","editDoctorChamber","manageDoctorInfo"],
  hospitals:["addHospital","editHospital","manageHospitalInfo"],
  diagnostics:["addDiagnosticCenter","editDiagnosticCenter","manageDiagnosticInfo"],
  specialties:["manageDoctorInfo"], locations:["manageDoctorInfo","manageHospitalInfo","manageDiagnosticInfo"],
};
function getAdminSession() {
  const role = sessionStorage.getItem("dlbd_admin_role") || "SUPER_ADMIN";
  let permissions = {};
  if (role !== "SUPER_ADMIN") { try { permissions = JSON.parse(sessionStorage.getItem("dlbd_admin_perms") || "{}") || {}; } catch (_) {} }
  return {
    role,
    uid: sessionStorage.getItem("dlbd_admin_uid") || sessionStorage.getItem("dlbd_admin_id") || "super_admin",
    name: sessionStorage.getItem("dlbd_admin_name") || sessionStorage.getItem("dlbd_admin_id") || "Super Admin",
    permissions
  };
}
function isSuperAdmin() { return getAdminSession().role === "SUPER_ADMIN"; }
function hasPermission(key) { return isSuperAdmin() || Boolean(getAdminSession().permissions[key]); }
function canAccessTab(tabKey) {
  if (isSuperAdmin()) return true;
  const required = TAB_PERMISSION_RULES[tabKey];
  return Array.isArray(required) && required.some(hasPermission);
}
function getAuditActorMeta(existing) {
  const s = getAdminSession(), now = Date.now();
  return {createdBy:existing && existing.createdBy || s.name, createdByUid:existing && existing.createdByUid || s.uid,
    createdAt:existing && existing.createdAt || now, updatedBy:s.name, updatedByUid:s.uid, updatedAt:now};
}
function applyRoleBasedSidebarVisibility() {
  const s = getAdminSession(), badge = document.getElementById("activeAdminRoleBadge");
  if (badge) badge.textContent = (s.role === "SUPER_ADMIN" ? "Super Admin: " : "Sub Admin: ") + s.name;
  document.querySelectorAll("#sidebarNav button[data-tab]").forEach(btn => btn.classList.toggle("hidden", !canAccessTab(btn.dataset.tab)));
  document.querySelectorAll("#sidebarNav .nav-group").forEach(group => {
    group.classList.toggle("hidden", group.querySelectorAll("button[data-tab]:not(.hidden)").length === 0);
  });
  if (!canAccessTab(activeTab)) {
    const first = document.querySelector("#sidebarNav button[data-tab]:not(.hidden)");
    if (first) { activeTab = first.dataset.tab; syncActiveNav(activeTab); }
  }
}
async function verifySubAdminLogin(loginInput, password) {
  const res = await fetch(RTDB_BASE + "/subAdmins.json");
  if (!res.ok) return null;
  const records = await res.json() || {}, norm = String(loginInput).trim().toLowerCase();
  for (const [id, sub] of Object.entries(records)) {
    if (!sub || !sub.salt || !sub.passwordHash) continue;
    if (![sub.email,sub.loginId].some(v => String(v || "").trim().toLowerCase() === norm)) continue;
    if (await hashCredential(password,sub.salt) !== sub.passwordHash) return {error:"Invalid Admin ID or Password."};
    if (String(sub.status || "ACTIVE").toUpperCase() !== "ACTIVE") return {error:"This Sub Admin account is inactive. Contact Super Admin."};
    return {ok:true,subAdmin:{id,...sub}};
  }
  return null;
}
function renderDashboard() {
  const doctors = Object.keys(dbState.doctors || {}).length;
  const chambers = Object.keys(dbState.chambers || {}).length;
  const hospitals = Object.keys(dbState.hospitals || {}).length;
  const diagnostics = Object.keys(dbState.diagnostic_centers || {}).length;
  const specialties = Object.keys(dbState.specialties || {}).length;
  const cards = [
    ["Doctors",doctors,"doctors"],["Chambers & Schedules",chambers,"chambers"],
    ["Hospitals",hospitals,"hospitals"],["Diagnostic Centers",diagnostics,"diagnostics"],
    ["Specialties",specialties,"specialties"]
  ];
  return '<div class="space-y-5"><div class="rounded-2xl bg-gradient-to-r from-slate-900 to-sky-800 text-white p-6">' +
    '<h3 class="text-xl font-bold">Doctor List BD — Admin Dashboard</h3><p class="text-sm text-sky-100 mt-1">Medical directory &amp; user management overview.</p></div>' +
    '<div class="grid grid-cols-2 lg:grid-cols-3 gap-3">' + cards.map(c =>
      '<button type="button" onclick="switchTab(\'' + c[2] + '\')" class="text-left bg-white border border-slate-200 rounded-2xl p-4 shadow-sm hover:border-sky-300">' +
      '<div class="text-xs text-slate-500">' + c[0] + '</div><div class="text-2xl font-extrabold text-slate-900 mt-1">' + c[1].toLocaleString() + '</div></button>'
    ).join("") + '</div><div class="flex flex-wrap gap-2">' +
    '<button onclick="switchTab(\'audit_logs\')" class="px-4 py-2 rounded-xl bg-slate-800 text-white text-xs font-bold">Audit Logs</button></div></div>';
}
let visitorSearchText = "";
function renderVisitorsUsers() {
  const users = rootState.users || {}, visitors = rootState.visitors || {}, merged = new Map();
  for (const [id,u] of Object.entries(users)) merged.set(id,{...(u||{}),uid:(u&&u.uid)||id});
  for (const [id,v] of Object.entries(visitors)) merged.set(id,{...(merged.get(id)||{}),...(v||{}),uid:(v&&v.uid)||id});
  const q = visitorSearchText.trim().toLowerCase();
  const rows = Array.from(merged.values()).filter(u => [u.uid,u.name,u.email,u.role,u.status].some(v => String(v||"").toLowerCase().includes(q)))
    .sort((a,b) => Number(b.lastLoginAt||b.updatedAt||b.createdAt||0)-Number(a.lastLoginAt||a.updatedAt||a.createdAt||0));
  return '<div class="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">' +
    '<div class="flex flex-wrap items-center justify-between gap-3 mb-4"><div><h3 class="font-bold text-base">Visitors & Google Users (' + rows.length + ')</h3>' +
    '<p class="text-xs text-slate-500">Google visitor profiles from Firebase users/visitors records.</p></div>' +
    '<input class="input max-w-xs" placeholder="Search name, email or UID" value="' + esc(visitorSearchText) + '" oninput="visitorSearchText=this.value;renderTab()" /></div>' +
    (rows.length ? '<div class="overflow-x-auto"><table class="w-full text-xs text-left"><thead><tr class="bg-slate-50 text-slate-600"><th class="p-3">User</th><th class="p-3">Email</th><th class="p-3">Role</th><th class="p-3">Status</th><th class="p-3">Created</th><th class="p-3">Last Login</th></tr></thead><tbody>' +
      rows.map(u => '<tr class="border-t border-slate-100"><td class="p-3"><div class="font-bold">' + esc(u.name||"—") + '</div><div class="text-[10px] text-slate-400 font-mono">' + esc(u.uid) + '</div></td>' +
      '<td class="p-3">' + esc(u.email||"—") + '</td><td class="p-3">' + esc(u.role||"visitor") + '</td><td class="p-3">' + statusBadgeHtml(u.status||"ACTIVE") +
      '</td><td class="p-3 whitespace-nowrap">' + formatDateOnly(u.createdAt) + '</td><td class="p-3 whitespace-nowrap">' + formatDateOnly(u.lastLoginAt||u.updatedAt) + '</td></tr>').join("") +
      '</tbody></table></div>' : '<div class="text-center py-10 text-xs text-slate-400">No visitor accounts found.</div>') + '</div>';
}
function renderSubAdmins() {
  if (!isSuperAdmin()) return '<div class="p-6 rounded-2xl bg-rose-50 text-rose-700 text-sm font-bold">Only Super Admin can manage Sub Admins.</div>';
  const records = Object.entries(dbState.subAdmins || {}).map(([id,s]) => ({id,...s})).sort((a,b)=>Number(b.createdAt||0)-Number(a.createdAt||0));
  const edit = editing.subAdmin || null, perms = edit && edit.permissions || {};
  return '<div class="grid grid-cols-1 xl:grid-cols-12 gap-5"><div class="xl:col-span-5 bg-white p-5 rounded-2xl border border-slate-200">' +
    '<h3 class="font-bold mb-3">' + (edit ? "Edit Sub Admin" : "Create Sub Admin") + '</h3><form onsubmit="submitSubAdmin(event)" class="space-y-3">' +
    '<input type="hidden" id="sa_id" value="' + esc(edit && edit.id || "") + '"/>' +
    '<input id="sa_name" required class="input" placeholder="Full Name" value="' + esc(edit && edit.name || "") + '"/>' +
    '<input id="sa_email" required type="email" class="input" placeholder="Email / Login ID" value="' + esc(edit && edit.email || "") + '"/>' +
    '<input id="sa_phone" class="input" placeholder="Phone (optional)" value="' + esc(edit && edit.phone || "") + '"/>' +
    '<input id="sa_pass" type="password" class="input" minlength="8" ' + (edit ? "" : "required") + ' placeholder="' + (edit ? "New password (leave blank to keep)" : "Password (minimum 8 characters)") + '"/>' +
    '<select id="sa_status" class="input"><option value="ACTIVE" ' + (!edit || edit.status==="ACTIVE" ? "selected" : "") + '>ACTIVE</option><option value="INACTIVE" ' + (edit && edit.status==="INACTIVE" ? "selected" : "") + '>INACTIVE</option></select>' +
    '<div class="border rounded-xl p-3"><div class="flex justify-between text-xs font-bold mb-2"><span>Permissions</span><span><button type="button" onclick="toggleAllSubAdminPerms(true)">Select All</button> / <button type="button" onclick="toggleAllSubAdminPerms(false)">Clear</button></span></div>' +
    '<div class="grid grid-cols-1 sm:grid-cols-2 gap-2">' + SUB_ADMIN_PERMISSIONS.map(p =>
      '<label class="text-xs flex gap-2 items-center"><input type="checkbox" name="sa_perm_cb" value="' + p.key + '" ' + (perms[p.key] ? "checked" : "") + '/>' + esc(p.label) + '</label>'
    ).join("") + '</div></div><button class="w-full py-2.5 rounded-xl bg-sky-600 text-white text-xs font-bold" type="submit">' +
    (edit ? "Update Sub Admin" : "Create Sub Admin") + '</button>' +
    (edit ? '<button type="button" onclick="editing={};renderTab()" class="w-full text-xs text-slate-500">Cancel</button>' : '') +
    '</form></div><div class="xl:col-span-7 bg-white p-5 rounded-2xl border border-slate-200"><h3 class="font-bold mb-3">Sub Admin Accounts (' + records.length + ')</h3>' +
    (records.length ? '<div class="space-y-3">' + records.map(s => '<div class="border rounded-xl p-3"><div class="flex justify-between gap-2"><div><b>' + esc(s.name) + '</b> ' + statusBadgeHtml(s.status||"ACTIVE") +
      '<div class="text-xs text-slate-500">' + esc(s.email) + ' ' + esc(s.phone||"") + '</div><div class="text-[10px] text-slate-400">Created ' + formatDateOnly(s.createdAt) + '</div></div>' +
      '<div class="flex gap-1"><button onclick="editSubAdmin(\'' + esc(s.id) + '\')" class="text-xs text-sky-700">Edit</button><button onclick="toggleSubAdminStatus(\'' + esc(s.id) + '\')" class="text-xs text-amber-700">Toggle</button><button onclick="deleteSubAdmin(\'' + esc(s.id) + '\')" class="text-xs text-rose-700">Delete</button></div></div>' +
      '<div class="text-[10px] text-slate-500 mt-2">' + SUB_ADMIN_PERMISSIONS.filter(p=>s.permissions&&s.permissions[p.key]).map(p=>esc(p.label)).join(" • ") + '</div></div>').join("") + '</div>' :
      '<p class="text-xs text-slate-400 py-8 text-center">No Sub Admin accounts yet.</p>') + '</div></div>';
}
function toggleAllSubAdminPerms(checked) {
  document.querySelectorAll('input[name="sa_perm_cb"]').forEach(cb => cb.checked = checked);
}
async function submitSubAdmin(e) {
  e.preventDefault();
  if (!isSuperAdmin()) return alert("Only Super Admin can manage Sub Admins.");
  const id = document.getElementById("sa_id").value || uid("sub");
  const old = dbState.subAdmins && dbState.subAdmins[id] || null;
  const name = document.getElementById("sa_name").value.trim(), email = document.getElementById("sa_email").value.trim();
  const phone = document.getElementById("sa_phone").value.trim(), pass = document.getElementById("sa_pass").value;
  const status = document.getElementById("sa_status").value || "ACTIVE";
  if (!name || !email) return alert("Name and email are required.");
  if ((!old && pass.length < 8) || (pass && pass.length < 8)) return alert("Password must be at least 8 characters.");
  let salt = old && old.salt || "", passwordHash = old && old.passwordHash || "";
  if (pass) { salt = generateSaltHex(16); passwordHash = await hashCredential(pass,salt); }
  const permissions = {};
  SUB_ADMIN_PERMISSIONS.forEach(p => permissions[p.key] = false);
  document.querySelectorAll('input[name="sa_perm_cb"]:checked').forEach(cb => permissions[cb.value] = true);
  const payload = {id,name,email,loginId:email.toLowerCase(),phone,role:"SUB_ADMIN",status,salt,passwordHash,permissions,...getAuditActorMeta(old)};
  editing = {};
  await saveNode("subAdmins",id,payload);
  await writeAuditLog("SAVE_SUB_ADMIN","SUB_ADMIN",id,"Saved Sub Admin " + name);
}
function editSubAdmin(id) {
  const s = dbState.subAdmins && dbState.subAdmins[id]; if (!s) return;
  editing = {subAdmin:{id,...s}}; renderTab();
}
async function toggleSubAdminStatus(id) {
  if (!isSuperAdmin()) return;
  const s = dbState.subAdmins && dbState.subAdmins[id]; if (!s) return;
  const status = String(s.status||"ACTIVE").toUpperCase()==="ACTIVE" ? "INACTIVE" : "ACTIVE";
  await saveNode("subAdmins",id,{...s,status,updatedAt:Date.now()});
  await writeAuditLog("TOGGLE_SUB_ADMIN","SUB_ADMIN",id,"Set " + s.name + " to " + status);
}
async function deleteSubAdmin(id) {
  if (!isSuperAdmin()) return;
  const s = dbState.subAdmins && dbState.subAdmins[id];
  if (!confirm('Delete Sub Admin "' + (s && s.name || id) + '"?')) return;
  await deleteNode("subAdmins",id);
  await writeAuditLog("DELETE_SUB_ADMIN","SUB_ADMIN",id,"Deleted Sub Admin " + (s && s.name || id));
}
