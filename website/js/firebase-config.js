// Firebase Modular SDK Configuration for Doctor List BD Public Visitor Website
// Uses the existing Doctor List BD Firebase Realtime Database project (read-only public access).
// Never store private keys or admin credentials here.

import { initializeApp, getApps } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js";
import { getDatabase } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js";

const DEFAULT_FIREBASE_CONFIG = {
  apiKey: "AIzaSyCfOJu1lzqyJmqmtiJiCGehSQ7Jr89FYPE",
  authDomain: "octor-aad88.firebaseapp.com",
  databaseURL: "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "octor-aad88",
  storageBucket: "octor-aad88.firebasestorage.app",
  messagingSenderId: "606146627263",
  appId: "1:606146627263:android:363cc6ec7e217db259280a"
};

export function getFirebaseConfig() {
  const custom = typeof window !== "undefined" && window.DOCTOR_LIST_BD_FIREBASE_CONFIG;
  if (custom && typeof custom === "object" && custom.databaseURL) {
    return { ...DEFAULT_FIREBASE_CONFIG, ...custom };
  }
  return DEFAULT_FIREBASE_CONFIG;
}

let cachedDb = null;

export function getPublicDatabase() {
  if (cachedDb) return cachedDb;
  const config = getFirebaseConfig();
  const app = getApps().length ? getApps()[0] : initializeApp(config);
  cachedDb = getDatabase(app);
  return cachedDb;
}
