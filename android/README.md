# OpenTreeNap Android — V0.1

Experimental modern Android client for the OpenTreeNap instance in Naples.

## V0.1 scope

The first milestone focuses on the smallest end-to-end path:

1. build with a current Android toolchain;
2. open Google Maps centered on Naples;
3. sign an OpenTreeMap API v4 request with HMAC-SHA256;
4. call `/api/v4/instance/<instance>/plots`;
5. parse tree coordinates;
6. show the returned trees as markers.

Editing, login, photos, geolocation, search, monumental-tree pins, botanical
cards and ecosystem benefits are deferred until this read-only path is stable.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk 36
- targetSdk 36
- minSdk 23
- Kotlin through AGP built-in Kotlin support
- Google Maps SDK for Android 20.0.0

## Configure locally

From the `android/` directory:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
MAPS_API_KEY=...
OTM_BASE_URL=https://...
OTM_INSTANCE=napoli
OTM_ACCESS_KEY=...
OTM_SECRET_KEY=...
```

Do not commit `local.properties`.

Restrict the Maps API key in Google Cloud to Android package
`org.opentreenap.mobile` and the certificate fingerprint(s) used to sign the
app.

### Create a development OTM API credential

On the OpenTreeNap server:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T web python manage.py shell <<'PY'
from api.models import APIAccessCredential

key = APIAccessCredential.create()
print("ACCESS_KEY =", key.access_key)
print("SECRET_KEY =", key.secret_key)
PY
```

Use a dedicated development credential. The secret is compiled into a debug
APK, therefore this legacy shared-secret model is not appropriate for a public
release. Before Play Store distribution, move HMAC signing behind a small
OpenTreeNap mobile API/proxy or replace it with a modern client-safe auth flow.

## Build

```bash
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Expected result

The app opens a map centered on Naples. With valid API configuration it loads
the current trees from OpenTreeNap and shows them as green markers.

## Next milestone

V0.2:

- marker clustering;
- tree detail bottom sheet;
- current-position permission and nearby trees;
- distinct monumental/centenarian tree markers;
- species/common/scientific name normalization;
- API error diagnostics;
- CI debug APK build.
