# Virgil — Release Build & Signing

How to produce the signed `.aab` you upload to Play Console. This file is for the maintainer; nothing here is shipped to users.

## One-time keystore setup

Generate the upload keystore. Do this **once**, store the result in a password manager, and never lose it — losing the upload key means starting over with a new app listing.

```sh
keytool -genkey -v \
  -keystore virgil-upload.jks \
  -alias virgil-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

`keytool` will prompt for a store password and a key password. Use the same value for both unless you have a reason not to. Pick something long and store it in a password manager — there is no recovery path.

Move the resulting `virgil-upload.jks` somewhere outside the repo (e.g. `~/.virgil-keystore/`). It is `.gitignore`'d but treat that as a backstop, not protection.

## Local signing config

Create `keystore.properties` at the **repo root** (alongside `android/`, `web/`, `Makefile`):

```properties
storeFile=/absolute/path/to/virgil-upload.jks
storePassword=<the store password>
keyAlias=virgil-upload
keyPassword=<the key password>
```

This file is `.gitignore`'d. Do not commit it.

Alternative: export the same values as environment variables prefixed with `VIRGIL_` (e.g. `VIRGIL_STOREFILE`, `VIRGIL_STOREPASSWORD`, …). The build reads `keystore.properties` first and falls back to the env vars, so CI can use env without writing a file.

If neither is present, the release build falls back to the debug signing config so local `make assemble` keeps working without a real keystore. **An unsigned-or-debug-signed APK cannot be uploaded to Play Console** — that fallback is for development only.

## Build the AAB

```sh
cd android
./gradlew bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`. This is what you upload.

To sanity-check the signature on the produced bundle:

```sh
keytool -printcert -jarfile android/app/build/outputs/bundle/release/app-release.aab
```

The fingerprint should match the upload keystore. If it shows the debug certificate, your `keystore.properties` was not picked up.

## Versioning

Bump `versionCode` and `versionName` in [android/app/build.gradle.kts](../android/app/build.gradle.kts) before each Play upload:

- `versionCode` is a monotonically increasing integer. Play Console rejects re-uploads at the same code.
- `versionName` is the human-readable string shown in the Play listing (e.g. `0.1.1`, `0.2.0`).

For the first internal-testing upload, `versionCode = 1` / `versionName = "0.1.0"` is fine. Bump to `2` / `0.1.1` for the next upload.

## Play App Signing

When you create the Play Console listing, opt **into** Play App Signing (the default). Google holds the app-signing key; you keep the upload key. If you ever lose the upload key, Google can reset it from the Console — that recovery path does not exist if you opt out.

## Pre-upload checklist

- [ ] `make quality` passes locally.
- [ ] `versionCode` is greater than the highest one ever uploaded.
- [ ] `keystore.properties` is set and points at the upload keystore (not the debug one).
- [ ] `app-release.aab` certificate fingerprint matches the upload keystore.
- [ ] Privacy policy at [docs/PRIVACY.md](PRIVACY.md) is published at a public URL and the URL is in the Play Console listing.
- [ ] [docs/PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) copy has been pasted into the Play Console fields.
- [ ] Data Safety form answers match [PRIVACY.md](PRIVACY.md).
- [ ] Foreground service `specialUse` declaration in Play Console matches the manifest subtype.
