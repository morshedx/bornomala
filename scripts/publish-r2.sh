#!/usr/bin/env bash
# Build a signed release APK and publish it + latest.json to Cloudflare R2,
# so the in-app OTA updater can find and install it.
#
# Prerequisites:
#   - keystore.properties present (for signing)
#   - local.properties has UPDATE_TOKEN (baked into BuildConfig at build time)
#   - wrangler installed and authenticated (npm i -g wrangler; wrangler login)
#   - env vars:
#       R2_BUCKET     name of your R2 bucket (e.g. app-releases)
#       R2_BASE_URL   public base URL of the bucket (e.g. https://app-releases.morshed.im)
#                     must match UpdateConfig.MANIFEST_URL's host
#       CLOUDFLARE_ACCOUNT_ID  only needed if your login has multiple accounts
#
# Usage:
#   R2_BUCKET=app-releases R2_BASE_URL=https://app-releases.morshed.im \
#     scripts/publish-r2.sh "Release notes shown in the app"
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES="${1:-}"
: "${R2_BUCKET:?set R2_BUCKET}"
: "${R2_BASE_URL:?set R2_BASE_URL}"

# Derive version from app/build.gradle.kts (single source of truth).
VERSION_NAME=$(grep -E 'val appVersionName' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
VERSION_CODE=$(grep -E 'val appVersionCode' app/build.gradle.kts | grep -oE '[0-9]+')
APK_NAME="bornomala-${VERSION_NAME}-release.apk"

echo "Building $APK_NAME (versionCode $VERSION_CODE)…"
./gradlew :app:assembleRelease --console=plain
APK_PATH="app/build/outputs/apk/release/${APK_NAME}"
[ -f "$APK_PATH" ] || { echo "APK not found: $APK_PATH"; exit 1; }

# Write the manifest the app polls.
MANIFEST="build/latest.json"
mkdir -p build
cat > "$MANIFEST" <<JSON
{
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE},
  "apkUrl": "${R2_BASE_URL}/bornomala/${APK_NAME}",
  "notes": $(python3 -c "import json,sys;print(json.dumps(sys.argv[1]))" "$NOTES")
}
JSON

echo "Uploading APK + manifest to R2 bucket '$R2_BUCKET'…"
# APK is immutable (version is in the name) -> cache forever.
wrangler r2 object put "${R2_BUCKET}/bornomala/${APK_NAME}" --remote \
  --file="$APK_PATH" \
  --content-type="application/vnd.android.package-archive" \
  --cache-control="public, max-age=31536000, immutable"
# latest.json changes every release -> short TTL so update checks see it fast.
wrangler r2 object put "${R2_BUCKET}/bornomala/latest.json" --remote \
  --file="$MANIFEST" \
  --content-type="application/json" \
  --cache-control="public, max-age=60"

echo "Published v${VERSION_NAME}. Manifest: ${R2_BASE_URL}/bornomala/latest.json"
