"use strict";

const crypto = require("crypto");
const admin = require("firebase-admin");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

if (!admin.apps.length) {
  admin.initializeApp({
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app",
  });
}

const REGION = "asia-south1";
const TOPIC_ALL_USERS = "all_app_users";
const VALID_TYPES = new Set(["GENERAL", "APP_UPDATE", "NEW_DOCTOR"]);
const VALID_UPDATE_MODES = new Set(["FLEXIBLE", "IMMEDIATE"]);
const VALID_RECIPIENT_MODES = new Set(["ALL", "USER"]);
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
    "http://localhost:5173",
    "http://localhost:5500",
    "http://localhost:8080",
    "http://127.0.0.1:5000",
    "http://127.0.0.1:5173",
    "http://127.0.0.1:5500",
    "http://127.0.0.1:8080",
    ...configured,
  ]);
}

function applyStrictCors(req, res) {
  const origin = String(req.headers.origin || "").trim();
  const allowed = getAllowedOrigins();
  if (
    origin &&
    (allowed.has(origin) ||
      origin.startsWith("http://localhost:") ||
      origin.startsWith("http://127.0.0.1:"))
  ) {
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
 * Verifies Firebase Auth decoded token (Custom Claims `admin === true`, `/admins/{uid}`,
 * active `/subAdmins/{uid}`, or verified Super Admin email).
 */
async function verifyFirebaseDecodedAdmin(db, decoded) {
  if (!decoded || !decoded.uid) {
    return { ok: false, status: 401, error: "Authentication required." };
  }
  const uid = decoded.uid;
  const email = String(decoded.email || "").toLowerCase();
  const hasAdminClaim = decoded.admin === true;

  const [adminSnap, subSnap] = await Promise.all([
    db.ref(`admins/${uid}`).once("value"),
    db.ref(`subAdmins/${uid}`).once("value"),
  ]);

  const isSuper =
    hasAdminClaim ||
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
      uid,
      role: isSuper ? "SUPER_ADMIN" : "SUB_ADMIN",
      authMethod: hasAdminClaim ? "CUSTOM_CLAIM" : "FIREBASE_AUTH",
    };
  }
  return {
    ok: false,
    status: 403,
    error: "Authenticated account does not have Admin permissions or custom admin claim.",
  };
}

/**
 * Verifies existing RTDB Admin Panel session proof (X-Admin-Id + X-Admin-Proof)
 * via constant-time comparison against /directory/admin_auth/config or active subAdmins.
 */
async function verifyAdminProofCredentials(db, adminIdRaw, adminProofRaw) {
  const adminId = String(adminIdRaw || "").trim();
  const adminProof = String(adminProofRaw || "").trim();
  if (!adminId || !adminProof) {
    return {
      ok: false,
      status: 401,
      error: "Missing admin authorization credentials. Sign in with Firebase Admin Auth or Admin credentials.",
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
    return { ok: true, actor: cfg.adminId, role: "SUPER_ADMIN", authMethod: "ADMIN_PROOF" };
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
      return { ok: true, actor: sub.email || sub.id || adminId, role: "SUB_ADMIN", authMethod: "ADMIN_PROOF" };
    }
  }

  return { ok: false, status: 403, error: "Admin authorization verification failed." };
}

/**
 * Verifies admin authorization for onCall (Firebase httpsCallable).
 */
async function verifyCallableAdmin(request) {
  const db = admin.database();
  if (request.auth && request.auth.token) {
    const res = await verifyFirebaseDecodedAdmin(db, {
      ...request.auth.token,
      uid: request.auth.uid,
    });
    if (res.ok) return res;
    // If signed in as non-admin Firebase user, still allow fallback check if adminProof was explicitly supplied
    const proofData = request.data && request.data.adminAuth;
    if (!proofData) return res;
  }

  const proofData = (request.data && request.data.adminAuth) || {};
  return verifyAdminProofCredentials(db, proofData.adminId, proofData.proof);
}

/**
 * Verifies admin authorization for onRequest (legacy HTTP endpoint).
 */
async function verifyHttpAdmin(req) {
  const db = admin.database();
  const authHeader = String(req.headers.authorization || "");
  if (authHeader.startsWith("Bearer ")) {
    const idToken = authHeader.slice(7).trim();
    if (!idToken) return { ok: false, status: 401, error: "Empty bearer token." };
    try {
      const decoded = await admin.auth().verifyIdToken(idToken, true);
      return verifyFirebaseDecodedAdmin(db, decoded);
    } catch (err) {
      logger.warn("Firebase ID token verification failed", err.message);
      return { ok: false, status: 401, error: "Invalid or expired Firebase Auth token." };
    }
  }
  return verifyAdminProofCredentials(
    db,
    req.headers["x-admin-id"],
    req.headers["x-admin-proof"]
  );
}

/**
 * Collects unique active FCM tokens across /fcmTokens, /fcm_tokens, and /users/{uid}/fcmTokens.
 * If targetUserId is provided, collects ONLY that specific user's active device tokens.
 */
async function collectActiveTokens(db, targetUserId = "") {
  const tokenToPaths = new Map();
  const targetUid = String(targetUserId || "").trim();

  function addToken(token, rtdbPath) {
    const clean = String(token || "").trim();
    if (clean.length < 20 || clean.length > 512) return;
    if (!tokenToPaths.has(clean)) {
      tokenToPaths.set(clean, []);
    }
    tokenToPaths.get(clean).push(rtdbPath);
  }

  if (targetUid) {
    const [scopedSnap, userTokensSnap, legacySnap] = await Promise.all([
      db.ref(`fcmTokens/${targetUid}`).once("value"),
      db.ref(`users/${targetUid}/fcmTokens`).once("value"),
      db.ref("fcm_tokens").orderByChild("uid").equalTo(targetUid).once("value"),
    ]);

    const scoped = scopedSnap.val() || {};
    for (const [deviceId, rec] of Object.entries(scoped)) {
      if (rec && rec.active !== false && rec.token) {
        addToken(rec.token, `fcmTokens/${targetUid}/${deviceId}`);
      }
    }

    const userTokens = userTokensSnap.val() || {};
    for (const [deviceId, rec] of Object.entries(userTokens)) {
      if (rec && rec.active !== false && rec.token) {
        addToken(rec.token, `users/${targetUid}/fcmTokens/${deviceId}`);
      }
    }

    const legacy = legacySnap.val() || {};
    for (const [deviceId, rec] of Object.entries(legacy)) {
      if (rec && rec.active !== false && rec.token) {
        addToken(rec.token, `fcm_tokens/${deviceId}`);
      }
    }

    return tokenToPaths;
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

/**
 * Core notification persistence + FCM HTTP v1 dispatch pipeline.
 * 1) Validates all inputs.
 * 2) Saves notification to Realtime Database FIRST (preserving inbox record even if FCM fails).
 * 3) Dispatches FCM HTTP v1 multicast (<=500 tokens/batch) + topic broadcast (for ALL).
 * 4) Prunes invalid/expired tokens and persists REAL delivery status (never fake push success).
 */
async function executeNotificationDispatch(db, authResult, rawPayload, opts = {}) {
  const payload = rawPayload && typeof rawPayload === "object" ? rawPayload : {};
  const isRetry = Boolean(opts.isRetry);
  const inboxOnly = Boolean(payload.inboxOnly);

  const title = String(payload.title || "").trim();
  const body = String(payload.body || payload.message || "").trim();
  const type = String(payload.type || payload.messageType || "GENERAL").trim().toUpperCase();
  const imageUrl = sanitizeHttpUrl(payload.imageUrl || "");
  const recipientMode = String(payload.recipientMode || (payload.targetUserId ? "USER" : "ALL"))
    .trim()
    .toUpperCase();
  const targetUserId = recipientMode === "USER" ? String(payload.targetUserId || "").trim() : "";
  const targetUserLabel = recipientMode === "USER" ? String(payload.targetUserLabel || targetUserId).trim().slice(0, 120) : "";

  if (title.length < 2 || title.length > 160) {
    return { ok: false, statusCode: 400, error: "Title must be between 2 and 160 characters." };
  }
  if (body.length < 2 || body.length > 1200) {
    return { ok: false, statusCode: 400, error: "Body must be between 2 and 1200 characters." };
  }
  if (!VALID_TYPES.has(type)) {
    return {
      ok: false,
      statusCode: 400,
      error: "Invalid notification type. Allowed: GENERAL, APP_UPDATE, NEW_DOCTOR.",
    };
  }
  if (!VALID_RECIPIENT_MODES.has(recipientMode)) {
    return { ok: false, statusCode: 400, error: "Invalid recipientMode. Allowed: ALL, USER." };
  }
  if (recipientMode === "USER" && !targetUserId) {
    return { ok: false, statusCode: 400, error: "Please select a target user for Individual User notification." };
  }
  if (imageUrl === null) {
    return { ok: false, statusCode: 400, error: "Invalid imageUrl. Must start with http:// or https://." };
  }

  let doctorId = "";
  let selectedDoctorName = "";
  let actionUrl = "";
  let updateMode = "";
  let version = "";
  let deepLink = "";

  if (type === "NEW_DOCTOR") {
    doctorId = String(payload.doctorId || payload.selectedDoctorId || "").trim();
    if (!doctorId) {
      return { ok: false, statusCode: 400, error: "doctorId is required for NEW_DOCTOR notifications." };
    }
    const docSnap = await db.ref(`directory/doctors/${doctorId}`).once("value");
    const docVal = docSnap.val();
    if (!docVal && !isRetry) {
      return { ok: false, statusCode: 404, error: `Selected doctor (${doctorId}) does not exist.` };
    }
    selectedDoctorName = String(
      (docVal && (docVal.fullNameBn || docVal.fullNameEn)) || payload.selectedDoctorName || ""
    ).trim();
    deepLink = `doctorlistbd://doctor/${doctorId}`;
  } else if (type === "APP_UPDATE") {
    const checkedActionUrl = sanitizeActionUrl(payload.actionUrl || payload.updateUrl || "");
    if (checkedActionUrl === null) {
      return {
        ok: false,
        statusCode: 400,
        error: "Invalid Update URL. Provide a valid https://, http://, or market:// link.",
      };
    }
    actionUrl = checkedActionUrl;
    const rawMode = String(payload.updateMode || "FLEXIBLE").trim().toUpperCase();
    if (!VALID_UPDATE_MODES.has(rawMode)) {
      return { ok: false, statusCode: 400, error: "updateMode must be FLEXIBLE or IMMEDIATE." };
    }
    updateMode = rawMode;
    version = String(payload.version || payload.updateVersion || "").trim().slice(0, 40);
    deepLink = actionUrl;
  }

  const notificationId =
    String(payload.notificationId || "").trim().replace(/[^a-zA-Z0-9_-]/g, "") ||
    `ntf_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const now = Date.now();
  const createdAt = Number(payload.createdAt || now);
  const retryCount = isRetry ? Number(payload.retryCount || 0) + 1 : 0;

  const baseRecord = {
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
    recipient: recipientMode === "USER" ? targetUserId : "ALL_APP_USERS",
    recipientMode,
    recipientType: recipientMode === "USER" ? "single_user" : "all_users",
    targetRole: recipientMode === "USER" ? "USER" : "ALL",
    targetUserId,
    targetUserLabel,
    imageUrl: imageUrl || "",
    actionUrl,
    doctorId,
    selectedDoctorId: doctorId,
    selectedDoctorName,
    updateMode,
    version,
    deepLink,
    createdAt,
    updatedAt: now,
    createdBy: authResult.actor,
    authMethod: authResult.authMethod || "ADMIN",
    retryCount,
    totalRecipients: 0,
    successCount: 0,
    failureCount: 0,
    topicDelivered: false,
    topicMessageId: "",
    prunedInvalidTokens: 0,
    fcmError: "",
    status: inboxOnly ? "INBOX_ONLY" : "PENDING",
  };

  // STEP 1: Save notification to Realtime Database FIRST so the Inbox record is guaranteed
  const initialDbUpdates = {
    [`notifications/${notificationId}`]: baseRecord,
  };
  if (recipientMode === "ALL") {
    initialDbUpdates[`directory/notifications/${notificationId}`] = baseRecord;
  } else if (targetUserId) {
    initialDbUpdates[`userNotifications/${targetUserId}/${notificationId}`] = {
      ...baseRecord,
      isRead: false,
      isDisplayed: false,
      isDeleted: false,
      receivedAt: createdAt,
    };
  }
  await db.ref().update(initialDbUpdates);

  if (inboxOnly) {
    return {
      ok: true,
      notificationId,
      type,
      recipientMode,
      targetUserId,
      status: "INBOX_ONLY",
      fcmSent: false,
      totalRecipients: 0,
      successCount: 0,
      failureCount: 0,
      topicDelivered: false,
      prunedInvalidTokens: 0,
      retryCount,
      record: baseRecord,
    };
  }

  // STEP 2: Collect active tokens & send FCM HTTP v1 after successful Inbox save
  const tokenMap = await collectActiveTokens(db, targetUserId);
  const tokens = Array.from(tokenMap.keys());
  const totalRecipients = tokens.length;

  let successCount = 0;
  let failureCount = 0;
  let topicDelivered = false;
  let topicMessageId = "";
  let fcmError = "";
  const stalePaths = [];

  const dataPayload = {
    notificationId,
    type,
    messageType: type,
    title,
    body,
    message: body,
    recipient: recipientMode === "USER" ? targetUserId : "ALL_APP_USERS",
    recipientMode,
    targetUserId,
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

  const androidConfig = {
    priority: "high",
    ttl: 2419200 * 1000,
    notification: {
      channelId: "doctor_list_bd_alerts",
      icon: "ic_notification",
      color: "#0B5ED7",
      sound: "default",
      defaultSound: true,
      defaultVibrateTimings: true,
      visibility: "public",
      priority: "max",
      clickAction: "OPEN_DOCTOR_LIST_NOTIFICATION",
      tag: notificationId,
      ...(imageUrl ? { imageUrl } : {}),
    },
  };

  const baseNotification = {
    title,
    body,
    ...(imageUrl ? { imageUrl } : {}),
  };

  try {
    // Multicast in batches of 500 tokens max per request
    const BATCH_SIZE = 500;
    for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
      const batchTokens = tokens.slice(i, i + BATCH_SIZE);
      const batchResp = await admin.messaging().sendEachForMulticast({
        tokens: batchTokens,
        notification: baseNotification,
        data: dataPayload,
        android: androidConfig,
      });
      successCount += batchResp.successCount;
      failureCount += batchResp.failureCount;

      batchResp.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
          logger.warn("FCM token delivery error", {
            notificationId,
            code: resp.error.code,
            message: resp.error.message,
          });
          if (!fcmError) fcmError = `${resp.error.code}: ${resp.error.message}`;
          if (INVALID_TOKEN_CODES.has(resp.error.code)) {
            const badToken = batchTokens[idx];
            const paths = tokenMap.get(badToken) || [];
            stalePaths.push(...paths);
          }
        }
      });
    }

    // For ALL broadcast, also send to topic "all_app_users" with identical tag for guaranteed Background/Closed System Tray delivery
    if (recipientMode === "ALL") {
      try {
        topicMessageId = await admin.messaging().send({
          topic: TOPIC_ALL_USERS,
          notification: baseNotification,
          data: dataPayload,
          android: androidConfig,
        });
        topicDelivered = Boolean(topicMessageId);
      } catch (topicErr) {
        logger.error("FCM topic broadcast failed", {
          notificationId,
          error: topicErr.message,
        });
        if (!fcmError) fcmError = `Topic error: ${topicErr.message}`;
      }
    }
  } catch (err) {
    logger.error("FCM push dispatch exception (Inbox record preserved)", {
      notificationId,
      error: err.message,
    });
    fcmError = err.message || "Unexpected FCM dispatch error.";
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

  // Compute real delivery status (NEVER report fake push success)
  let status = "INBOX_ONLY";
  if (totalRecipients > 0) {
    if (successCount > 0 && failureCount === 0) status = "SENT";
    else if (successCount > 0 && failureCount > 0) status = "PARTIAL";
    else if (recipientMode === "ALL" && topicDelivered) status = "SENT";
    else status = "FAILED";
  } else if (recipientMode === "ALL" && topicDelivered) {
    status = "SENT";
  } else if (fcmError) {
    status = "FAILED";
  } else {
    status = "INBOX_ONLY";
  }

  const finalRecord = {
    ...baseRecord,
    updatedAt: Date.now(),
    totalRecipients,
    successCount,
    failureCount,
    topicDelivered,
    topicMessageId,
    prunedInvalidTokens: stalePaths.length,
    fcmError,
    status,
  };

  // STEP 3: Update RTDB record with authoritative delivery results
  const finalDbUpdates = {
    [`notifications/${notificationId}`]: finalRecord,
  };
  if (recipientMode === "ALL") {
    finalDbUpdates[`directory/notifications/${notificationId}`] = finalRecord;
  } else if (targetUserId) {
    finalDbUpdates[`userNotifications/${targetUserId}/${notificationId}`] = {
      ...finalRecord,
      isRead: false,
      isDisplayed: false,
      isDeleted: false,
      receivedAt: createdAt,
    };
  }
  await db.ref().update(finalDbUpdates);

  return {
    ok: true,
    notificationId,
    type,
    recipientMode,
    targetUserId,
    status,
    fcmSent: status === "SENT" || status === "PARTIAL",
    totalRecipients,
    successCount,
    failureCount,
    topicDelivered,
    topicMessageId,
    prunedInvalidTokens: stalePaths.length,
    retryCount,
    fcmError,
    record: finalRecord,
  };
}

/**
 * 1. Health & Connection Check (httpsCallable — fixes "Failed to fetch")
 */
exports.checkNotificationBackend = onCall(
  {
    region: REGION,
    cors: true,
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request) => {
    const authResult = await verifyCallableAdmin(request);
    if (!authResult.ok) {
      throw new HttpsError(
        authResult.status === 401 ? "unauthenticated" : "permission-denied",
        authResult.error
      );
    }
    const db = admin.database();
    const tokenMap = await collectActiveTokens(db);
    const [usersSnap, fcmUsersSnap] = await Promise.all([
      db.ref("users").once("value"),
      db.ref("fcmTokens").once("value"),
    ]);
    const userKeys = new Set([
      ...Object.keys(usersSnap.val() || {}),
      ...Object.keys(fcmUsersSnap.val() || {}),
    ]);
    return {
      ok: true,
      region: REGION,
      projectId: process.env.GCLOUD_PROJECT || "octor-aad88",
      actor: authResult.actor,
      role: authResult.role,
      authMethod: authResult.authMethod,
      activeTokenCount: tokenMap.size,
      registeredUsersCount: userKeys.size,
      serverTime: Date.now(),
    };
  }
);

/**
 * 2. Primary Notification Dispatcher (httpsCallable — Firebase Functions v2)
 */
exports.sendNotificationCallable = onCall(
  {
    region: REGION,
    cors: true,
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    const authResult = await verifyCallableAdmin(request);
    if (!authResult.ok) {
      throw new HttpsError(
        authResult.status === 401 ? "unauthenticated" : "permission-denied",
        authResult.error
      );
    }
    const db = admin.database();
    const result = await executeNotificationDispatch(db, authResult, request.data || {}, {
      isRetry: false,
    });
    if (!result.ok) {
      throw new HttpsError(
        result.statusCode === 404 ? "not-found" : "invalid-argument",
        result.error
      );
    }
    return result;
  }
);

/**
 * 3. Retry Failed / Partial / Inbox-Only Notification Push (httpsCallable)
 */
exports.retryNotificationCallable = onCall(
  {
    region: REGION,
    cors: true,
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    const authResult = await verifyCallableAdmin(request);
    if (!authResult.ok) {
      throw new HttpsError(
        authResult.status === 401 ? "unauthenticated" : "permission-denied",
        authResult.error
      );
    }
    const notificationId = String((request.data && request.data.notificationId) || "")
      .trim()
      .replace(/[^a-zA-Z0-9_-]/g, "");
    if (!notificationId) {
      throw new HttpsError("invalid-argument", "notificationId is required for retry.");
    }

    const db = admin.database();
    const [rootSnap, dirSnap] = await Promise.all([
      db.ref(`notifications/${notificationId}`).once("value"),
      db.ref(`directory/notifications/${notificationId}`).once("value"),
    ]);
    const existing = rootSnap.val() || dirSnap.val();
    if (!existing) {
      throw new HttpsError("not-found", `Notification (${notificationId}) not found in database.`);
    }

    const result = await executeNotificationDispatch(
      db,
      authResult,
      { ...existing, notificationId, inboxOnly: false },
      { isRetry: true }
    );
    if (!result.ok) {
      throw new HttpsError("invalid-argument", result.error);
    }
    return result;
  }
);

/**
 * 4. Backward-Compatible HTTP Endpoint (strict allowlisted CORS — no wildcard on authenticated route)
 */
exports.sendBroadcastNotification = onRequest(
  {
    region: REGION,
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (req, res) => {
    applyStrictCors(req, res);
    if (req.method === "OPTIONS") {
      return res.status(204).send("");
    }
    if (req.method !== "POST") {
      return res.status(405).json({ ok: false, error: "Method not allowed. Use POST." });
    }

    const authResult = await verifyHttpAdmin(req);
    if (!authResult.ok) {
      return res.status(authResult.status).json({ ok: false, error: authResult.error });
    }

    const db = admin.database();
    const result = await executeNotificationDispatch(db, authResult, req.body || {}, {
      isRetry: false,
    });
    if (!result.ok) {
      return res.status(result.statusCode || 400).json({ ok: false, error: result.error });
    }
    return res.status(200).json(result);
  }
);
