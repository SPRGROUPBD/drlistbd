# Production Live Ads & Revenue Verification Checklist — Doctor List BD

> **Important Notice:** Actual ad earnings are never guaranteed. Real revenue depends on genuine organic user traffic, geographic advertiser demand, ad fill rate, viewability, verified `app-ads.txt` authorization, account approval status, and each network's official payment policies.

---

## 1. Release Build Live-Mode Enforcement (Verified in Code)

- **Test Mode Strictly Disabled in Release Builds**: `HybridNetworkAdController.isTestModeActive(config, BuildConfig.DEBUG)` unconditionally returns `false` when `BuildConfig.DEBUG == false`. Even if remote Admin RTDB `testMode` is left enabled, the published Release APK/AAB will **never** request test ads.
- **Demo / Sample Credential Blocker**: `HybridNetworkAdController.isValidProductionCredential()` blocks blank, demo, sample, placeholder, and known SDK test IDs from sending live requests in Release builds.
- **Zero Test Impressions Counted as Revenue**: `AdRevenueTracker` strictly gates all online network telemetry (`requests`, `loads`, `errors`, `impressions`, `clicks`, `rewarded`, `sdkRevenueMicros`) behind `liveProductionTrackingEnabled`, ensuring Debug/Test sessions never inflate production revenue metrics.
- **Offline Protection**: When offline, Unity Ads, Start.io, and Wortise execute zero initialization and zero ad requests; only valid cached Custom Direct Sponsor Ads display.

---

## 2. Production Credentials Checklist (Admin Panel → Multi-Ad Network)

Configure your real, approved production IDs in the Web Admin Panel (**Multi-Ad Network** tab → **Save Configuration**). Never enter sample/demo IDs.

| Network | Required Production Fields | Current Status / Action Needed |
|---|---|---|
| **Start.io** | Production App ID (`startIoAppId`) | `210630916` currently configured (verify it matches your active approved Start.io Android app `com.doctorlist.bd`). |
| **Unity Ads** | Production Android Game ID (`unityGameId`), Banner (`Banner_Android`), Interstitial (`Interstitial_Android`), Rewarded (`Rewarded_Android`) | Enter your live **Unity Cloud / LevelPlay Android Game ID** in the Admin Panel and verify placement IDs match your Unity Dashboard exactly. Disable "Test Mode" override inside Unity Dashboard Project Settings. |
| **Wortise** | Production App ID (`wortiseAppId`), Banner, Native, Interstitial, Rewarded, and App Open AdUnit IDs | Enter your live **Wortise App ID** and each **AdUnit ID** created for `com.doctorlist.bd` in the Wortise Dashboard. Formats with blank IDs safely fall back to Unity Ads / Start.io. |
| **Admin Test Mode** | `Test Mode` toggle | Set to **OFF (Live Production Ads)** before launch (Release builds also force this OFF automatically). |

---

## 3. `app-ads.txt` Public Accessibility & Verification

1. **File Included in Repo**: `/app-ads.txt` contains your Start.io (`start.io, 165433365, DIRECT`), Wortise (`ownerdomain=wortise.com`, `wortise.com, 3f844795-ebd1-4a52-b934-587b7d56571d, DIRECT`, `wortise.com, c44ddb94-ab0d-42c3-89d2-f0a20262d36a, DIRECT`), and authorized reseller/mediation lines.
2. **Verify Unity Direct Line**: Confirm in your **Unity Ads Dashboard → Settings → app-ads.txt** whether your personal Unity Publisher ID matches the existing `unity.com` line or requires adding your own `unity.com, <YOUR_PUBLISHER_ID>, DIRECT, 96cabb5fbdde37a7` line.
3. **Publish at Root Domain**: Upload `/app-ads.txt` to the root of the **exact developer website domain** listed in your Google Play Console store listing:
   - Example: `https://yourdomain.com/app-ads.txt`
4. **Verify HTTP Accessibility**:
   - Returns **HTTP 200 OK** with `Content-Type: text/plain`.
   - No login wall, no `robots.txt` block for `Googlebot` / `AdsBot-Google` / `Start.io` / `Unity` / `Wortise` crawlers.
   - Check verification status inside **Start.io Portal**, **Unity Dashboard**, and **Wortise Dashboard** (crawlers typically refresh within 24–72 hours).

---

## 4. Live Production Verification Checklist (Step-by-Step)

Perform these checks on a **physical Android device** running the **signed Release APK / Internal Testing AAB** (do NOT click your own live ads):

- [ ] **Production SDK Initialization**: Confirm via release device verification / dashboards that Unity Ads (`4.14.1`), Start.io (`5.1.0`), and Wortise Core (`1.8.0`) initialize cleanly without crashes and with Test Mode **OFF**.
- [ ] **Real Ad Requests**: Open Home, Doctor List, Hospital List, and Doctor Details while online; confirm real ad requests register in Unity Ads, Start.io, and Wortise dashboards (allow 15–60 minutes for dashboard latency).
- [ ] **Ad Fill Rate & Smart Fallback**: Verify Banner, Native, Interstitial/App Open, and Rewarded Video render real creatives with no blank containers. If Priority #1 returns No Fill, confirm Priority #2 / #3 automatically fills.
- [ ] **Eligible Impressions & Policy Compliance**: Confirm impressions are recorded organically without auto-clicking, forced clicks, or excessive ad frequency. Verify rewarded access is granted **only** after watching a complete rewarded ad.
- [ ] **Estimated Revenue vs. Official Dashboard Earnings**:
  - In the Web Admin Panel (**Ad Revenue & Analytics**), monitor per-network impressions, fill rate, and estimated revenue separately for **Unity Ads**, **Start.io**, and **Wortise**.
  - Reconcile daily/monthly totals against official finalized earnings reported inside the **Unity Ads**, **Start.io**, and **Wortise** dashboards.
  - Confirm **zero test impressions** appear in production revenue analytics.
- [ ] **Account Approval & Payment Status**:
  - **Google Play Store Listing**: App published (or live on Play Store) with accurate **Data Safety** disclosure (advertising ID / ad performance data) and **Developer Website** linked for `app-ads.txt`.
  - **Unity Ads**: Organization verified, app linked to Google Play Store URL, tax & payout profile approved.
  - **Start.io**: App `com.doctorlist.bd` approved and linked to Google Play Store URL, payout method configured.
  - **Wortise**: App `com.doctorlist.bd` approved in Wortise Dashboard, `app-ads.txt` status shows Verified, payout details configured.

---

## 5. Signing & Google Play Release Artifacts

- **Signed Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Signed Google Play AAB**: `app/build/outputs/bundle/release/app-release.aab`
- **Upload Keystore**: Never lose your production `my-upload-key.jks` or its passwords. In GitHub Actions, store `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` as repository secrets so every release build is signed with your real Google Play upload key.
