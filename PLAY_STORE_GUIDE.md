# Google Play Store Deployment Guide

This guide provides step-by-step instructions on how to configure your Google Play Store account and GitHub repository to enable automated deployments using the provided GitHub Actions workflow.

## Step 1: Google Play Console Setup

### 1. Enable the Google Play Android Developer API
- Go to the [Google Play Console](https://play.google.com/console).
- In the left-hand menu, navigate to **Setup > API access**.
- Click **Choose a project to link** and select the Google Cloud project you want to link. If you don't have one, you can create a new one.
- The **Google Play Android Developer API** should be automatically enabled. If not, you can enable it from the [Google Cloud Console](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).

### 2. Create a Service Account
- From the **API access** page in the Google Play Console, scroll down to the **Service accounts** section.
- Click **Create new service account**.
- Follow the on-screen instructions to create a service account. You will be redirected to the Google Cloud Platform.
- When creating the service account, assign it the **Service Account User** role.
- After creating the service account, you will need to create a key. Choose **JSON** as the key type and download the file. This file contains the credentials needed to authenticate with the Google Play API.

### 3. Grant Permissions in Play Console
- Go back to the **API access** page in the Google Play Console.
- Find the newly created service account in the list and click **Grant access**.
- Grant the following permissions:
  - **Admin (all permissions)**: This is the simplest option, but you can also grant more granular permissions if needed.
- Click **Invite user** to save the changes.

## Step 2: GitHub Secrets Configuration

You need to add the following secrets to your GitHub repository to allow the workflow to access the Play Store and sign the release.

- Go to your repository's **Settings** tab.
- In the left-hand menu, navigate to **Secrets and variables > Actions**.
- Click **New repository secret** for each of the following secrets.

See [Encrypted secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets) in the GitHub Actions documentation for more details.

### 1. `SERVICE_ACCOUNT_JSON`

- Open the JSON file you downloaded when creating the service account.
- Copy the **entire** content of the file and paste it into the secret's value field.

### 2. `SIGNING_KEY`

This is your Android app signing keystore, **base64-encoded** (single line, no newlines). The workflow decodes it at build time to sign the release bundle.

#### Option A: Generate a new keystore

If you don't have a signing key yet:

1. **Generate the keystore** using `keytool` (included with the JDK):

   ```bash
   keytool -genkey -v -keystore julius-app.keystore -alias julius-app -keyalg RSA -keysize 2048 -validity 10000
   ```

   You will be prompted for the keystore password, key password, and certificate details. Remember the **alias** and **passwords** — you'll need them for the other secrets.

   Reference: [Android app signing](https://developer.android.com/studio/publish/app-signing#generate-key) | [keytool documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)

2. **Back up the keystore file** — store it securely. If you lose it, you cannot update your app on the Play Store.

#### Option B: Use an existing keystore

If you already have a `.keystore` or `.jks` file (e.g. from a previous build or Play App Signing setup), use that file.

#### Encode the keystore for GitHub

3. **Base64-encode the keystore** (no line breaks — the workflow expects a single line):

   **macOS / Linux (OpenSSL):**
   ```bash
   openssl base64 < julius-app.keystore | tr -d '\n' | tee julius-app.keystore.base64.txt
   ```

   **Linux (GNU base64):**
   ```bash
   base64 -w 0 julius-app.keystore > julius-app.keystore.base64.txt
   ```

4. **Copy the encoded value** from `julius-app.keystore.base64.txt` and paste it into the `SIGNING_KEY` secret. Do not add spaces, newlines, or quotes.

### 3. `ALIAS`

The alias of your signing key — the value you used with `-alias` when generating the keystore (e.g. `my-alias`).

### 4. `KEY_STORE_PASSWORD`

The password you set for the keystore when generating it.

### 5. `KEY_PASSWORD`

The password for the key itself. For many keystores this is the same as `KEY_STORE_PASSWORD`.

## Troubleshooting

### "Only releases with status draft may be created on draft app"

This error occurs when your app has never been published to production. Google Play requires draft releases for apps in draft status.

The workflow uses `status: draft` by default. After the workflow uploads successfully:

1. Go to [Google Play Console](https://play.google.com/console) → your app → **Testing** → **Internal testing**
2. You should see a new draft release with your uploaded build
3. Click **Review release** → **Start rollout to Internal testing**

**Note:** For pre-production apps, Google Play often rejects `status: completed` even after the first rollout. Manual validation of each release may be required until the app is published to production.

### "The Android App Bundle was signed with the wrong key"

This error means the keystore in `SIGNING_KEY` does not match the one Google Play expects. **You must use the exact keystore that was used when the app was first published** — Google Play does not accept uploads signed with a different key.

1. **Find your original upload keystore** — the `.jks` or `.keystore` file used for the first Play Store upload.
2. **Verify the SHA1 fingerprint** matches what Play Console expects:
   ```bash
   keytool -list -v -keystore your-release.keystore -alias your-alias
   ```
   Compare the SHA1 with the one shown in the error message (the "expected" value).
3. **Update GitHub secrets** with that keystore: re-encode it, set `SIGNING_KEY`, and ensure `ALIAS`, `KEY_STORE_PASSWORD`, and `KEY_PASSWORD` match.

If you no longer have the original keystore, you cannot update the app. See [Google's key loss guidance](https://support.google.com/googleplay/android-developer/answer/9842756).

## Conclusion

Once you have completed these steps, the GitHub Actions workflow will be able to automatically build, sign, and deploy your Android application to the Google Play Store on every push or when triggered manually via **Actions > Deploy to Google Play Store > Run workflow**.
