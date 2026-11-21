# Doctor List BD — Repository Security & Release Audit

Audit date: 2026-11-21  
Repository: `SPRGROUPBD/drlistbd`  
Default branch audited: `main`

## Scope and preservation requirements

This audit reviewed the Firebase Realtime Database rules, browser Admin Panel authentication flow, Android manifest/build configuration, APK signing workflow, ad-network dependencies/configuration, and the published `app-ads.txt` file.

The existing Doctor, Hospital, Diagnostic Center, Specialist, Chamber Schedule, Sponsor, Direct Sponsor Ads, Unity Ads, Start.io, and Wortise features must remain intact. No live Firebase data or production ad configuration was changed by this audit branch.

## Findings

### Critical — Firebase RTDB writes are globally open

In `database.rules.json`, `/directory/.write` is set to `true`. This grants unauthenticated clients write access to the entire directory subtree, including doctor records, ad configuration, sponsor campaigns, payment records, audit logs, and admin authentication configuration. The existing validation rules under selected analytics nodes do not protect paths while the ancestor write grant remains open.

**Required remediation:** migrate the web Admin Panel to Firebase Authentication (or a trusted server-side authorization layer), authorize administrator UIDs/roles in RTDB Security Rules, retain public read access only for the public directory data the app needs, and grant narrowly scoped unauthenticated writes only to validated installation/analytics endpoints if that telemetry must remain anonymous. Test the Android REST client and every Admin Panel module against staging rules before production deployment.

**Why this branch does not change the production rules:** the Admin Panel currently uses a browser-side password hash stored at `/directory/admin_auth/config` and a `sessionStorage` flag. It does not establish a Firebase Auth identity. The Android app also reads directory data and posts installation/analytics events through unauthenticated RTDB REST calls. Tightening rules before migrating these clients would break existing Admin and app functionality.

### Critical — Public directory read exposes non-public business and user data

Because `.read: true` is granted at `/directory`, the Android client and any unauthenticated internet client can download the entire directory tree, not just approved doctor listings. The tree includes advertiser account records (mobile/email/address and password hashes), advertiser submissions and payment records, sponsor campaign data, Admin configuration, analytics, and potentially patient appointment/report submissions. Password hashes are not safe to expose: the advertiser service uses a fast SHA-256 construction that can be brute-forced offline if copied. The Android `RtdbSyncService` requests `/directory.json` as a whole rather than fetching only public collections. This creates unnecessary data disclosure and oversized sync responses.

**Required remediation:** split public directory content from private/admin/account/payment/patient nodes; fetch only the public allowlisted paths in Android; move advertiser authentication and payment/account access behind Firebase Auth rules or a trusted backend; never return password hashes to public clients. Treat existing exposed hashes and user data as potentially disclosed and plan credential resets/incident review.

### Critical — Public appointment/report writes permit spam and expose patient details

The Android app submits appointment requests containing patient name, phone, doctor/chamber and notes, and doctor reports. With the ancestor `/directory/.write: true`, these endpoints have no authenticated ownership or rate-limit boundary. This allows automated spam and unauthorized edits/deletions, and makes submitted patient details publicly readable under the ancestor read grant.

**Required remediation:** use authenticated, narrowly scoped writes; separate private appointment/report data from public directory records; validate ownership and payload shape server-side; add App Check/rate limiting or a trusted API for abuse-sensitive submissions.

### High — Admin Panel login is not database authorization

The login check in the browser verifies an ID and SHA-256 password hash, then sets `sessionStorage`. Firebase RTDB never receives an authenticated principal, so Security Rules cannot distinguish an administrator from an ordinary visitor. A client-side session flag is not an authorization boundary.

**Required remediation:** implement Firebase Auth sign-in, server-controlled admin UID/role provisioning, session expiry, and rules-enforced writes. Do not rely on hiding the Admin UI or checking a JavaScript variable.

### High — Release workflow generated a predictable fallback signing key

The original APK workflow generated a release keystore with a fixed password whenever GitHub signing secrets were absent. This is unsafe and can also produce an APK that cannot update an already-published app signed with the original upload key.

**Change made on this branch:** release APKs now require the owner's persistent keystore and all three GitHub Actions secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`). On ordinary branch/PR builds, the workflow can still build and upload a Debug APK when these secrets are not configured. Version-tag builds fail clearly if persistent signing secrets are missing. The key alias expected by the existing Gradle signing configuration is `upload`.

### High — Advertiser login is also client-side SHA-256 authentication

The advertiser portal and Android `AdvertiserRemoteService` read advertiser records directly from the public RTDB tree and compare a salted SHA-256 hash in client code. The salt/hash are therefore retrievable by any visitor while public read remains enabled; the hash is not a server-side login boundary. Advertiser ownership checks performed by the client do not protect database records from direct REST access.

**Required remediation:** migrate advertiser accounts to Firebase Authentication or a trusted backend, enforce advertiser UID ownership in rules, keep password hashes out of readable RTDB paths, and migrate existing accounts with a forced password reset or secure account linking flow.

### High — app-ads.txt is incomplete

The current file contains comments only and no verified seller authorization records. This can prevent ad systems from verifying authorized inventory.

**Required remediation:** obtain the exact publisher/seller records from the official Unity Ads, Start.io, and Wortise dashboards and publish them at the developer domain configured in each network. Do not invent seller IDs or copy another publisher's records. Confirm the domain ownership and app-store listing association.

### Medium — Android cleartext traffic is enabled

The manifest sets `android:usesCleartextTraffic="true"`. This permits cleartext HTTP traffic for the app process. Before disabling it, verify every configured API, image, deep link, and SDK endpoint uses HTTPS; then disable cleartext traffic and test all ad SDKs and directory flows on supported Android versions.

### Medium — Android backup is enabled

The manifest sets `android:allowBackup="true"`. Review the backup/data-extraction rules and ensure no credentials, admin session data, tokens, or private app state are included in device/cloud backups. The app already stores its installation identity in a no-backup preference file; keep that behavior.

### Review — Release shrinking is disabled

The release build currently has `isMinifyEnabled = false`. This is not a confirmed functional defect. Enable R8 only after adding/validating keep rules for Room, Compose, Start.io, Unity Ads, Wortise, and any reflection/serialization code, then run release smoke tests. Blindly enabling shrinking could break SDK callbacks or database mapping.

## Ad network preservation check

The source currently declares the Start.io, Unity Ads, and Wortise Android SDK dependencies and includes separate managers/controller references for all three. This audit branch does not remove or replace any of them. SDK presence alone does not prove live ad fill or revenue; production verification requires valid network credentials, approved inventory, physical-device tests, and each network's dashboard reporting.

## Validation still required before production

- Run the new GitHub Actions workflow and confirm Debug APK build succeeds.
- Configure the original persistent upload keystore secrets and confirm the Release APK signature matches the currently published app.
- Test banner, native, interstitial/app-open, and rewarded formats for Unity Ads, Start.io, Wortise, and Direct Sponsor Ads on physical devices.
- Complete Firebase Auth/admin-role migration and test all Admin modules against staging RTDB rules before applying production rules.
- Add verified seller records to `app-ads.txt` and verify each network's authorization checker.
- Review backup contents and HTTP endpoint usage before changing Android manifest security flags.

## Branch changes

- `.github/workflows/build-apk.yml`: removed predictable release-key fallback; release signing now requires persistent owner-provided secrets; debug builds remain possible without release secrets.
- Added a CI-only standard Debug keystore generation step because the project explicitly points debugConfig at an ignored local `debug.keystore`; this does not touch Release signing.
- No production Firebase rules, ad IDs, publisher IDs, app data, or existing ad integrations were changed.
