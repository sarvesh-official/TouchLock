# BudFreeze Play Store Submission Checklist

Last updated: August 21, 2026

## Build Status

- Debug APK: built (21.6 MB)
- Release APK: built and signed (1.96 MB)
- Release AAB: built and signed (3.79 MB) - this is what Play Console needs
- Keystore: /Users/sarvesh/Workspace/Projects/realme-buds-touchlock/android/keystore/touchlock-upload.jks
- Package name: com.sarvesh.touchlock

## Fix Applied

- Added androidx.fragment:fragment-ktx:1.8.5 dependency to fix lint vital error (InvalidFragmentVersionForActivityResult)
- Release build now passes lint and builds successfully

## Play Console Submission Steps

### 1. Create new app in Play Console
- Go to https://play.google.com/console
- Click "Create app"
- App name: BudFreeze
- Default language: English (United States)
- App type: App
- Free/Paid: Free

### 2. Store listing (from store-assets/STORE_LISTING.md)
- [x] App name: BudFreeze
- [x] Short description (80 chars): "Lock and unlock touch controls on your Bluetooth earbuds"
- [x] Full description: ready in STORE_LISTING.md
- [x] App icon: 512x512 PNG (store-assets/app-icon-512.png)
- [x] Feature graphic: 1024x500 PNG (store-assets/feature-graphic/feature-graphic.png)
- [x] Screenshots: 9 screenshots ready (store-assets/screenshots/)
- [x] Category: Tools
- [x] Content rating: Everyone
- [x] Target audience: All ages
- [x] Privacy policy URL: https://github.com/sarvesh-official/BudFreeze/blob/main/PRIVACY_POLICY.md
- [x] Keywords: earbuds, bluetooth, touch lock, realme buds, oneplus buds

### 3. App content (required by Play Console)
- [x] Privacy policy: PRIVACY_POLICY.md written and ready
- [x] App access: No special access required
- [x] Ads: No ads
- [x] Content rating: Complete the IARC questionnaire (answer "No" to all violence/sex/gambling questions)
- [x] Target audience: All ages
- [x] News app: No
- [x] COVID-19 contact tracing: No
- [x] Data safety: Fill out the data safety form
  - Data collected: None
  - Data shared: None
  - Data encrypted: N/A (no data collected)
  - Data deletion: N/A (no data collected)

### 4. Data safety form answers
- "Does your app collect or share any of the required user data types?" = No
- "Is all of the user data collected by your app encrypted in transit?" = N/A
- "Provide a way for users to request that their data be deleted" = N/A

### 5. Upload AAB
- Go to Production > Create new release
- Upload: /Users/sarvesh/Workspace/Projects/realme-buds-touchlock/android/app/build/outputs/bundle/release/app-release.aab
- Release name: 1.0.0
- Release notes: "Initial release. Lock and unlock touch controls on Realme, OnePlus, and OPPO earbuds."

### 6. Review and roll out
- Review the release
- If there are warnings, address them
- Start rollout to production (or internal testing first if you want to test with a small group)

### 7. After submission
- Google review typically takes 1-3 days for new apps
- You'll get an email when it's approved or if there are issues
- Once approved, the app is live on the Play Store

## Before You Submit - Double Check

- [ ] Test the release APK on a real device (install app-release.apk and verify all features work)
- [ ] Verify the supporter purchase flow works (Google Play Billing)
- [ ] Verify the Quick Settings tile works
- [ ] Verify Find Nearby works
- [ ] Check that the app doesn't crash on devices without supported earbuds
- [ ] Make sure the privacy policy URL is accessible (repo must be public or policy hosted elsewhere)

## Commit the Fix

The fragment dependency fix needs to be committed:
```bash
cd /Users/sarvesh/Workspace/Projects/realme-buds-touchlock
git add android/app/build.gradle.kts
git commit -m "Add fragment-ktx dependency to fix lint vital release build"
```
