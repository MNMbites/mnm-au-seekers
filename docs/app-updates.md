# Managed APK updates

MNM AU Seekers supports manual update checks for sideloaded installations. The
Android app reads the latest public release metadata from the official
`MNMbites/mnm-au-seekers` GitHub repository only after the user selects
`Check for updates`.

The checker accepts only strict `vMAJOR.MINOR.PATCH` release tags and the exact
versioned asset name `MNM-AU-Seekers-MAJOR.MINOR.PATCH.apk`. Both the release
page and APK download must use HTTPS on `github.com` under this repository's
release path. A lookalike host, redirect, query parameter, unexpected asset
name, malformed response, or missing APK is rejected.

When a newer version is available, `Open update download` hands the validated
URL to an external browser. Android remains responsible for download and
installation confirmation. The app does not download in the background,
silently install packages, request `REQUEST_INSTALL_PACKAGES`, or bypass the
device's unknown-apps controls.

## Stable signing is required

Android accepts an APK as an update only when its application ID and signing
certificate match the installed app. Generate one release keystore, store it in
an offline backup, and use it for every managed release. Losing or rotating the
key breaks in-place upgrades for existing installations.

The current CI debug APKs use ephemeral runner debug keys. They cannot become an
in-place update chain. Before installing the first managed release, export any
needed reports or paper records, uninstall the debug build, and install the
managed release once. Every later managed release can then update it in place.
Uninstalling clears private app data.

Generate the keystore locally and keep it outside the repository:

```bash
keytool -genkeypair -v \
  -keystore mnm-au-seekers-release.jks \
  -alias mnm-au-seekers \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Add these repository Actions secrets under **Settings → Secrets and variables →
Actions**:

- `MNM_ANDROID_KEYSTORE_BASE64`: the keystore encoded as a single base64 value;
- `MNM_ANDROID_KEYSTORE_PASSWORD`: the keystore password;
- `MNM_ANDROID_KEY_ALIAS`: the alias created above; and
- `MNM_ANDROID_KEY_PASSWORD`: the key password.

Pin the public signing-certificate fingerprint in the repository Actions
variable `MNM_ANDROID_CERT_SHA256`. Generate it from the same keystore:

```bash
keytool -exportcert \
  -keystore mnm-au-seekers-release.jks \
  -alias mnm-au-seekers \
  -rfc \
  | openssl x509 -noout -fingerprint -sha256 \
  | cut -d= -f2
```

Colons and letter case are ignored during comparison. A different certificate
stops the release before the APK is published.

For GNU base64, create the first value with:

```bash
base64 -w0 mnm-au-seekers-release.jks
```

Do not commit the keystore, encoded keystore, passwords, or recovery copies.
Keep a second encrypted offline backup and record which production installs use
its certificate.

## Publish an upgrade

The `appVersionName` in `app/build.gradle.kts` and the tag must match. Its
Android `versionCode` is derived from `MAJOR.MINOR.PATCH`, so every component
must be from 0 to 999 and every release version must increase. After the release
commit is merged into the default branch, create and push the tag:

```bash
git tag v0.16.0
git push origin v0.16.0
```

`.github/workflows/android-release.yml` verifies that the tagged commit belongs
to the default branch, validates the tag against `versionName`, checks the
keystore, alias, and pinned certificate fingerprint, runs release unit tests and
lint, assembles a signed APK, and verifies that final APK signature. It publishes
the versioned APK and SHA-256 checksum as an immutable GitHub Release. Missing
signing configuration, mismatched tags or certificates, test failures, or
signature errors stop publication.

The optional repository Actions variable `MNM_MARKET_DATA_BASE_URL` is still the
only live-service origin compiled into release builds. It must contain an HTTPS
origin without credentials. Update infrastructure adds no broker, account, or
execution capability.
