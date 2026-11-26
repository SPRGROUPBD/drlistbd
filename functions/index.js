"use strict";

const crypto = require("crypto");
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

if (!admin.apps.length) {
  admin.initializeApp({
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app",
  });
}

const VALID_TYPES = new Set(["GENERAL", "APP_UPDATE", "NEW_DOCTOR"]);
const VALID_UPDATE_MODES = new Set(["FLEXIBLE", "IMMEDIATE"]);
const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

function getAllowedOrigins() {
  const configured = String(process.env.ALLOWED_ORIGINS || "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  return new Set([
    "https://octor-aad88.web.app",
    "https://octor-aad88.firebaseapp.com",
    "http://localhost:3000",
    "http://localhost:5000",
    "http://localhost:5500",
    "http://localhost:8080",
    "http://127.0.0.1:5000",
    "http://127.0.0.1:5500",
    "http://127.0.0.1:8080",
    ...configured,
  ]);
}

function applyCors(req, res) {
  const origin = String(req.headers.origin || "").trim();
  const allowed = getAllowedOrigins();
  if (origin && (allowed.has(origin) || origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
    res.set("Access-Control-Allow-Origin", origin);
    res.set("Vary", "Origin");
  }
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set(
    "Access-Control-Allow-Headers",
    "Content-Type, Authorization, X-Admin-Id, X-Admin-Proof"
  );
  res.set("Access-Control-Max-Age", "3600");
}

function timingSafeEqualStr(a, b) {
  const bufA = Buffer.from(String(a || ""), "utf8");
  const bufB = Buffer.from(String(b || ""), "utf8");
  if (bufA.length === 0 || bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function sanitizeHttpUrl(raw) {
  const value = String(raw || "").trim();
  if (!value) return "";
  if (value.length > 500) return null;
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

function sanitizeActionUrl(raw) {
  const value = String(raw || "").trim();
  if (!value) return "";
  if (value.length > 500) return null;
  if (/^market:\/\/[a-zA-Z0-9._/?=&%-]+$/i.test(value)) {
    return value;
  }
  return sanitizeHttpUrl(value);
}

/**
 * Verifies admin authorization strictly on the server side.
 * Supports:
 * 1) Firebase Auth ID Token (Authorization: Bearer <idToken>) verified against /admins, active /subAdmins, or verified Super Admin email.
 * 2) Existing RTDB Admin Panel session proof (X-Admin-Id + X-Admin-Proof) verified via timingSafeEqual against /directory/admin_auth/config or active subAdmins.
 */
async function verifyAdminRequest(req) {
  const db = admin.database();

  const authHeader = String(req.headers.authorization || "");
  if (authHeader.startsWith("Bearer ")) {
    const idToken = authHeader.slice(7).trim();
    if (!idToken) {
      return { ok: false, status: 401, error: "Empty bearer token." };
    }
    try {
      const decoded = await admin.auth().verifyIdToken(idToken, true);
      const uid = decoded.uid;
      const email = String(decoded.email || "").toLowerCase();
      const [adminSnap, subSnap] = await Promise.all([
        db.ref(`admins/${uid}`).once("value"),
        db.ref(`subAdmins/${uid}`).once("value"),
      ]);
      const isSuper =
        adminSnap.exists() ||
        (email === "mail.sajjadahmed@gmail.com" && decoded.email_verified === true);
      const subVal = subSnap.val();
      const isSubAdmin =
        subVal &&
        String(subVal.status || "").toUpperCase() === "ACTIVE" &&
        (!subVal.permissions || subVal.permissions.notifications !== false);

      if (isSuper || isSubAdmin) {
        return {
          ok: true,
          actor: decoded.email || uid,
          role: isSuper ? "SUPER_ADMIN" : "SUB_ADMIN",
        };
      }
      return { ok: false, status: 403, error: "Authenticated account is not an authorized Admin." };
    } catch (err) {
      logger.warn("Firebase ID token verification failed", err.message);
      return { ok: false, status: 401, error: "Invalid or expired Firebase Auth token." };
    }
  }

  const adminId = String(req.headers["x-admin-id"] || "").trim();
  const adminProof = String(req.headers["x-admin-proof"] || "").trim();
  if (!adminId || !adminProof) {
    return {
      ok: false,
      status: 401,
      error: "Missing admin authorization credentials.",
    };
  }

  const cfgSnap = await db.ref("directory/admin_auth/config").once("value");
  const cfg = cfgSnap.val();
  if (
    cfg &&
    cfg.adminId &&
    cfg.passwordHash &&
    timingSafeEqualStr(adminId, cfg.adminId) &&
    timingSafeEqualStr(adminProof, cfg.passwordHash)
  ) {
    return { ok: true, actor: cfg.adminId, role: "SUPER_ADMIN" };
  }

  const [dirSubsSnap, rootSubsSnap] = await Promise.all([
    db.ref("directory/subAdmins").once("value"),
    db.ref("subAdmins").once("value"),
  ]);
  const allSubs = {
    ...(dirSubsSnap.val() || {}),
    ...(rootSubsSnap.val() || {}),
  };
  for (const sub of Object.values(allSubs)) {
    if (!sub || typeof sub !== "object") continue;
    if (String(sub.status || "").toUpperCase() !== "ACTIVE") continue;
    const matchesIdentity =
      timingSafeEqualStr(adminId.toLowerCase(), String(sub.email || "").toLowerCase()) ||
      timingSafeEqualStr(adminId, String(sub.id || ""));
    if (matchesIdentity && sub.passwordHash && timingSafeEqualStr(adminProof, sub.passwordHash)) {
      if (sub.permissions && sub.permissions.notifications === false) {
        return { ok: false, status: 403, error: "Sub Admin lacks notification send permission." };
      }
      return { ok: true, actor: sub.email || sub.id || adminId, role: "SUB_ADMIN" };
    }
  }

  return { ok: false, status: 403, error: "Admin authorization verification failed." };
}

/**
 * Collects unique active FCM tokens across /fcmTokens, /fcm_tokens, and /users/{uid}/fcmTokens.
 */
async function collectActiveTokens(db) {
  const tokenToPaths = new Map();

  function addToken(token, rtdbPath) {
    const clean = String(token || "").trim();
    if (clean.length < 20 || clean.length > 512) return;
    if (!tokenToPaths.has(clean)) {
      tokenToPaths.set(clean, []);
    }
    tokenToPaths.get(clean).push(rtdbPath);
  }

  const [scopedSnap, legacySnap, usersSnap] = await Promise.all([
    db.ref("fcmTokens").once("value"),
    db.ref("fcm_tokens").once("value"),
    db.ref("users").once("value"),
  ]);

  const scoped = scopedSnap.val() || {};
  for (const [userKey, devices] of Object.entries(scoped)) {
    if (!devices || typeof devices !== "object") continue;
    for (const [deviceId, rec] of Object.entries(devices)) {
      if (rec && rec.active !== false && rec.token) {
        addToken(rec.token, `fcmTokens/${userKey}/${deviceId}`);
      }
    }
  }

  const legacy = legacySnap.val() || {};
  for (const [deviceId, rec] of Object.entries(legacy)) {
    if (rec && rec.active !== false && rec.token) {
      addToken(rec.token, `fcm_tokens/${deviceId}`);
    }
  }

  const users = usersSnap.val() || {};
  for (const [uid, u] of Object.entries(users)) {
    if (!u || !u.fcmTokens || typeof u.fcmTokens !== "object") continue;
    for (const [deviceId, rec] of Object.entries(u.fcmTokens)) {
      if (rec && rec.active !== false && rec.token) {
        addToken(rec.token, `users/${uid}/fcmTokens/${deviceId}`);
      }
    }
  }

  return tokenToPaths;
}

exports.sendBroadcastNotification = onRequest(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (req, res) => {
    applyCors(req, res);
    if (req.method === "OPTIONS") {
      return res.status(204).send("");
    }
    if (req.method !== "POST") {
      return res.status(405).json({ ok: false, error: "Method not allowed. Use POST." });
    }

    const authResult = await verifyAdminRequest(req);
    if (!authResult.ok) {
      return res.status(authResult.status).json({ ok: false, error: authResult.error });
    }

    const payload = req.body && typeof req.body === "object" ? req.body : {};
    const title = String(payload.title || "").trim();
    const body = String(payload.body || payload.message || "").trim();
    const type = String(payload.type || payload.messageType || "").trim().toUpperCase();
    const imageUrl = sanitizeHttpUrl(payload.imageUrl || "");

    if (title.length < 2 || title.length > 160) {
      return res.status(400).json({ ok: false, error: "Title must be between 2 and 160 characters." });
    }
    if (body.length < 2 || body.length > 1200) {
      return res.status(400).json({ ok: false, error: "Body must be between 2 and 1200 characters." });
    }
    if (!VALID_TYPES.has(type)) {
      return res.status(400).json({
        ok: false,
        error: "Invalid notification type. Allowed: GENERAL, APP_UPDATE, NEW_DOCTOR.",
      });
    }
    if (imageUrl === null) {
      return res.status(400).json({ ok: false, error: "Invalid imageUrl. Must be http:// or https://." });
    }

    const db = admin.database();
    let doctorId = "";
    let selectedDoctorName = "";
    let actionUrl = "";
    let updateMode = "";
    let version = "";
    let deepLink = "";

    if (type === "NEW_DOCTOR") {
      doctorId = String(payload.doctorId || payload.selectedDoctorId || "").trim();
      if (!doctorId) {
        return res.status(400).json({ ok: false, error: "doctorId is required for NEW_DOCTOR notifications." });
      }
      const docSnap = await db.ref(`directory/doctors/${doctorId}`).once("value");
      const docVal = docSnap.val();
      if (!docVal) {
        return res.status(404).json({ ok: false, error: `Selected doctor (${doctorId}) does not exist.` });
      }
      selectedDoctorName = String(
        docVal.fullNameBn || docVal.fullNameEn || payload.selectedDoctorName || ""
      ).trim();
      deepLink = `doctorlistbd://doctor/${doctorId}`;
    } else if (type === "APP_UPDATE") {
      const checkedActionUrl = sanitizeActionUrl(payload.actionUrl || payload.updateUrl || "");
      if (checkedActionUrl === null) {
        return res.status(400).json({
          ok: false,
          error: "Invalid Update URL. Provide a valid https://, http://, or market:// link.",
        });
      }
      actionUrl = checkedActionUrl;
      const rawMode = String(payload.updateMode || "FLEXIBLE").trim().toUpperCase();
      if (!VALID_UPDATE_MODES.has(rawMode)) {
        return res.status(400).json({ ok: false, error: "updateMode must be FLEXIBLE or IMMEDIATE." });
      }
      updateMode = rawMode;
      version = String(payload.version || payload.updateVersion || "").trim().slice(0, 40);
      deepLink = actionUrl;
    }

    const notificationId =
      String(payload.notificationId || "").trim().replace(/[^a-zA-Z0-9_-]/g, "") ||
      `ntf_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
    const createdAt = Date.now();

    const tokenMap = await collectActiveTokens(db);
    const tokens = Array.from(tokenMap.keys());
    const totalRecipients = tokens.length;

    let successCount = 0;
    let failureCount = 0;
    const stalePaths = [];

    const dataPayload = {
      notificationId,
      type,
      messageType: type,
      title,
      body,
      message: body,
      recipient: "ALL_APP_USERS",
      imageUrl: imageUrl || "",
      doctorId,
      selectedDoctorId: doctorId,
      selectedDoctorName,
      actionUrl,
      updateMode,
      version,
      deepLink,
      createdAt: String(createdAt),
    };

    // Send real FCM HTTP v1 multicast batches (500 tokens max per batch)
    const BATCH_SIZE = 500;
    for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
      const batchTokens = tokens.slice(i, i + BATCH_SIZE);
      const multicastMessage = {
        tokens: batchTokens,
        notification: {
          title,
          body,
          ...(imageUrl ? { imageUrl } : {}),
        },
        data: dataPayload,
        android: {
          priority: "high",
          notification: {
            channelId: "doctor_list_bd_alerts",
            sound: "default",
            ...(imageUrl ? { imageUrl } : {}),
          },
        },
      };

      const batchResp = await admin.messaging().sendEachForMulticast(multicastMessage);
      successCount += batchResp.successCount;
      failureCount += batchResp.failureCount;

      batchResp.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error && INVALID_TOKEN_CODES.has(resp.error.code)) {
          const badToken = batchTokens[idx];
          const paths = tokenMap.get(badToken) || [];
          stalePaths.push(...paths);
        }
      });
    }

    // Prune invalid/unregistered tokens from RTDB
    if (stalePaths.length > 0) {
      const pruneUpdates = {};
      stalePaths.forEach((p) => {
        pruneUpdates[p] = null;
      });
      await db.ref().update(pruneUpdates).catch((err) => {
        logger.warn("Failed pruning stale FCM tokens", err.message);
      });
    }

    let status = "INBOX_ONLY";
    if (totalRecipients > 0) {
      if (successCount > 0 && failureCount === 0) status = "SENT";
      else if (successCount > 0 && failureCount > 0) status = "PARTIAL";
      else status = "FAILED";
    }

    const record = {
      notificationId,
      title,
      titleBn: title,
      titleEn: title,
      body,
      message: body,
      messageBn: body,
      messageEn: body,
      type,
      messageType: type,
      recipient: "ALL_APP_USERS",
      recipientType: "all_users",
      targetRole: "ALL",
      imageUrl: imageUrl || "",
      actionUrl,
      doctorId,
      selectedDoctorId: doctorId,
      selectedDoctorName,
      updateMode,
      version,
      deepLink,
      createdAt,
      updatedAt: createdAt,
      createdBy: authResult.actor,
      totalRecipients,
      successCount,
      failureCount,
      prunedInvalidTokens: stalePaths.length,
      status,
    };

    await db.ref().update({
      [`notifications/${notificationId}`]: record,
      [`directory/notifications/${notificationId}`]: record,
    });

    return res.status(200).json({
      ok: true,
      notificationId,
      type,
      status,
      totalRecipients,
      successCount,
      failureCount,
      prunedInvalidTokens: stalePaths.length,
      record,
    });
  }
);
