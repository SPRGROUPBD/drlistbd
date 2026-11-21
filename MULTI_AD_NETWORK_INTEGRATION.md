# Multi Ad Network Integration Guide
## Unity Ads + Start.io + Wortise + Custom Direct Sponsor Ads

This document describes the production Multi Ad Network Integration architecture implemented in **Doctor List BD** (`com.doctorlist.bd`) and its Web Admin Panel.

---

## 1. Architecture & Network Coexistence

All existing monetization systems remain active and unchanged, while **Wortise Ads** is integrated as a third official online ad network:

### Online Mode
1. **Unity Ads** (`com.unity3d.ads:unity-ads:4.14.1`) — Preserved with existing Game ID, placements, and callbacks.
2. **Start.io Ads** (`com.startapp:inapp-sdk:5.1.0`) — Preserved with existing App ID, Banner, MREC, Native, Interstitial, and Rewarded flows.
3. **Wortise Ads** (`com.wortise:android-sdk:1.8.0`) — Newly integrated official Android SDK supporting Banner, Native, Interstitial, Rewarded, and App Open formats.
4. **Custom Direct Sponsor Ads** — Preserved with Fixed Package Pricing, 7 campaign statuses, bKash/Nagad/Rocket verification, and Admin-managed packages.

### Offline Mode
- **Unity Ads, Start.io, and Wortise are strictly disabled** (zero SDK initialization, zero network requests, zero blank containers).
- **Only valid cached Custom Direct Sponsor Ads** are displayed.
- Online ad networks automatically initialize and resume when internet connectivity returns.

---

## 2. Gradle & Manifest Configuration

### Repositories (`settings.gradle.kts`)
Official Wortise Maven repository and mediation adapter repositories are configured alongside Google and Maven Central:
- `https://maven.wortise.com/artifactory/public`
- `https://artifact.bytedance.com/repository/pangle`
- `https://artifactory.bidmachine.io/bidmachine`

### Dependencies (`app/build.gradle.kts`)
```kotlin
implementation("com.startapp:inapp-sdk:5.1.0")
implementation("com.unity3d.ads:unity-ads:4.14.1")
implementation("com.wortise:android-sdk:1.8.0")
```

### Android Manifest (`app/src/main/AndroidManifest.xml`)
- Preserves Start.io manifest metadata and existing `ACCESS_ADSERVICES_*` permission hygiene (`tools:node="remove"`).
- Declares `com.google.android.gms.ads.AD_MANAGER_APP = true` required by Wortise's internal mediation runtime so app startup never crashes.
- **No private Ad Unit IDs or App IDs are hardcoded in the manifest or Kotlin code.**

---

## 3. Admin Panel Configuration (Remote, Zero App Update)

Open the Web Admin Panel → **Multi-Ad Network** tab:

### A. Network Switches & Fallback Priority Order
- Toggle **Unity Ads**, **Start.io Ads**, and **Wortise Ads** independently ON or OFF.
- Configure **Priority #1 → Priority #2 → Priority #3** (default: `Unity Ads → Start.io → Wortise`) or click one of the quick presets:
  - `Unity → Start.io → Wortise`
  - `Start.io → Unity → Wortise`
  - `Wortise → Unity → Start.io`
- Changes sync live via Firebase Realtime Database (`/directory/ad_config`) into Room v11 (`ad_config`) without rebuilding or republishing the APK.

### B. Wortise Configuration Card
Enter credentials from your Wortise Dashboard (never hardcoded in app source):
- **Wortise App ID** (`wortiseAppId`)
- **Banner AdUnit ID** (`wortiseBannerAdUnitId`)
- **Native AdUnit ID** (`wortiseNativeAdUnitId`)
- **Interstitial AdUnit ID** (`wortiseInterstitialAdUnitId`)
- **Rewarded AdUnit ID** (`wortiseRewardedAdUnitId`)
- **App Open AdUnit ID** (`wortiseAppOpenAdUnitId`)

Formats with blank AdUnit IDs are automatically skipped for Wortise and fall back cleanly to Unity Ads or Start.io.

### C. Format Switches, Timeout, Retry & Frequency Caps
- Independent switches for **Banner**, **Native**, **Interstitial**, **Rewarded Video**, and **App Open**.
- **Ad Request Timeout (sec)** — Automatically fails over to the next network if a request stalls.
- **No-Fill Retry Cooldown (sec)** — Prevents tight retry loops after No Fill responses.
- **Banner Refresh Interval (sec)** — Controls safe banner auto-refresh timing.
- **App Open / Interstitial Cooldown (sec)** and **Max Interstitials Per Session** — Prevents repeated rapid fullscreen ads.
- **Native Feed Interval** — Controls spacing between in-feed ads.

### D. Smart Screen Placement Management
Configure per-screen overrides (`/directory/screen_ad_placements` → Room `screen_ad_placements`) for all 8 screens:
1. Home Screen (`HOME`)
2. Doctor List (`DOCTOR_LIST`)
3. Doctor Details (`DOCTOR_DETAILS`)
4. Hospital List (`HOSPITAL_LIST`)
5. Diagnostic Center (`DIAGNOSTIC_CENTER`)
6. Medical Specialist (`MEDICAL_SPECIALIST`)
7. Chamber Schedule (`CHAMBER_SCHEDULE`)
8. Other Content Screens (`OTHER_SCREENS`)

Each screen supports: **Enable/Disable**, **Preferred Network** (`Auto Global Priority`, `Unity Ads`, `Start.io`, or `Wortise` — with automatic fallback to the remaining eligible networks), **Format**, **Priority**, **Frequency Cap**, and **Refresh Interval**.

---

## 4. Smart Fallback & Zero Blank Containers

`HybridNetworkAdController` manages all three SDKs:
1. Evaluates connectivity (must be online), master switch, format switch, screen placement enablement, and session frequency caps.
2. Resolves the ordered list of eligible networks (honoring screen preference + Admin global priority).
3. Requests from Priority #1. If the SDK returns **No Fill**, an error, or exceeds the configured timeout, immediately falls back to Priority #2, then Priority #3.
4. Prevents duplicate simultaneous requests per format and enforces cooldown windows after failures.
5. Banner and Native ad containers remain collapsed (`0dp` / minimal unobtrusive footprint) until an ad genuinely loads—**never displaying empty or broken boxes**.

---

## 5. Revenue & Analytics Dashboard

Open the Web Admin Panel → **Ad Revenue & Analytics** tab:
- Displays **Unity Ads**, **Start.io**, and **Wortise** in separate dedicated network cards and comparison tables.
- Tracks real SDK lifecycle metrics: **Requests**, **Loads**, **Fill Rate (%)**, **No Fill / Errors**, **Impressions**, **Clicks**, **CTR (%)**, and **Rewarded Completions**.
- Clearly separates:
  - **Estimated Revenue** (calculated from genuine impressions × configurable eCPM per network, plus genuine Wortise `RevenueData` paid callbacks when reported by the SDK).
  - **Finalized / Official Network Earnings** (manually logged from Unity Ads, Start.io, and Wortise official dashboards).
- Includes Daily Performance Breakdown and one-click **CSV Report Export**.

---

## 6. Real Advertisement Policy & Production Checklist

- **Genuine Inventory Only**: All ads come directly from the official Unity Ads, Start.io, and Wortise Android SDKs.
- **Zero Artificial Activity**: No fake impressions, no fake clicks, no auto-clicking, and no forced clicks.
- **Verified Rewarded Callbacks**: Users receive rewarded access **strictly** inside each SDK's verified completion callback (`UnityAdsShowCompletionState.COMPLETED`, Start.io `VideoListener`, and Wortise `onRewardedCompleted`).
- **Automatic Test Mode Safety**: Debug builds and Android emulators automatically enable official SDK test mode to avoid policy violations during testing. Release builds on physical devices strictly honor Admin's `testMode` toggle.

### Before Production Release
1. In the Web Admin Panel (**Multi-Ad Network**), enter your live **Unity Game ID**, **Start.io App ID**, and **Wortise App ID + AdUnit IDs**.
2. Set **Test Mode = OFF** (live ads) and save configuration.
3. Verify `app-ads.txt` on your developer domain includes authorized seller lines for Unity Ads, Start.io, and Wortise.
4. Build a signed Release APK/AAB and verify live ad fill and dashboard telemetry on a physical Android device.
