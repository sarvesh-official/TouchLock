# Publishing Setup Guide

This guide walks you through the one-time setup needed for the automated CI/CD pipeline.

## Overview

Once set up, the workflow is:
- **Push to `main`** → builds signed AAB → uploads to Play Console internal testing (available in minutes, no review)
- **Push a tag like `v1.1.0`** → builds signed AAB → uploads to Play Console production (goes through review)

## Step 1: Create the app in Play Console

1. Go to [Play Console](https://play.google.com/console)
2. Click **Create app**
3. App name: `Touch Lock`
4. Default language: English
5. App type: App
6. Pricing: Free
7. Declarations: check all required boxes
8. Click **Create app**

## Step 2: Set up app signing

1. In Play Console, go to your app → **Setup** → **App signing**
2. Under **App signing key**, select **Use Google-generated key** (recommended)
3. This means Google holds the final signing key. You only need the upload key (which we already generated).
4. Click **Enroll**

## Step 3: Create a service account for CI/CD

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project (you can use the same project that Play Console created)
3. Go to **IAM & Admin** → **Service Accounts** → **Create Service Account**
4. Name: `touchlock-ci`
5. Grant access: no roles needed at this point
6. Click **Done**
7. Click on the service account → **Keys** → **Add Key** → **Create new key** → **JSON**
8. Save the JSON file — this is your `play-service-account.json`

## Step 4: Link the service account to Play Console

1. In Play Console, go to **Setup** → **API access**
2. If prompted, link your Google Cloud project
3. Under **Service accounts**, click **Add service account**
4. Paste the service account email (from the JSON file's `client_email` field)
5. Grant **Admin** access (or at minimum, "Manage releases" and "Edit store listing")

## Step 5: Add GitHub Secrets

Go to your GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these 5 secrets:

### `KEYSTORE_BASE64`
Base64-encoded keystore file:
```bash
base64 -i android/keystore/touchlock-upload.jks | pbcopy
```
Paste the output as the secret value.

### `KEYSTORE_PASSWORD`
The keystore password: `touchlock2026`

### `KEY_ALIAS`
The key alias: `touchlock-upload`

### `KEY_PASSWORD`
The key password: `touchlock2026`

### `PLAY_SERVICE_ACCOUNT_BASE64`
Base64-encoded service account JSON:
```bash
base64 -i play-service-account.json | pbcopy
```
Paste the output as the secret value.

## Step 6: Create the in-app product

1. In Play Console, go to your app → **Monetize** → **Products** → **In-app products**
2. Click **Create product**
3. Product ID: `supporter`
4. Name: `Supporter`
5. Description: `Support TouchLock development with a one-time purchase`
6. Price: Set to $2.99 (or your preferred amount)
7. Click **Save** → **Activate**

## Step 7: Complete the store listing

Before you can publish to production, you need:
- App name, short description, full description
- App icon (512x512 PNG)
- Feature graphic (1024x500 PNG)
- At least 2 screenshots (phone)
- Privacy policy URL (use the GitHub Pages URL or raw GitHub URL)
- App category
- Content rating (complete the IARC questionnaire)
- Target audience
- Data safety form

## Step 8: First publish

1. Push to `main` — this triggers the CI/CD pipeline
2. The pipeline builds the signed AAB and uploads to **internal testing**
3. Add yourself as a tester in Play Console → **Testing** → **Internal testing**
4. Open the opt-in URL on your phone and install the app
5. Verify everything works

## For production releases

When you're ready to go public:
```bash
git tag v1.0.0
git push origin v1.0.0
```

This triggers a production upload. Google reviews it (1-3 days for first submission).

## Updating the app

Just push to `main`:
```bash
git add .
git commit -m "Add new feature"
git push
```

The pipeline builds and uploads to internal testing automatically. Testers get the update within minutes.

For production, bump `versionCode` and `versionName` in `build.gradle.kts`, then tag:
```bash
git tag v1.1.0
git push origin v1.1.0
```
