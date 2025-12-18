# Version Code Generation & CI/CD Signing Architecture

This document describes the automatic version code generation system and the secure CI/CD signing architecture used in WebDAV Provider.

## Overview

The app uses a **30-bit version code schema** that automatically derives version codes from git tags. This ensures:

1. **Proper ordering**: Development builds are always higher than previous stable releases
2. **Google Play compliance**: Maximum value (~1.07 billion) is under the 2.1 billion limit
3. **Automation**: No manual version code management needed

## The 30-Bit Schema (7-7-7-9 bits)

| Component | Bits | Range   | Shift Position | Description                            |
|-----------|------|---------|----------------|----------------------------------------|
| Major     | 7    | 0-127   | `<< 23`        | Major version number                   |
| Minor     | 7    | 0-127   | `<< 16`        | Minor version number                   |
| Patch     | 7    | 0-127   | `<< 9`         | Patch version number                   |
| Qualifier | 9    | 0-511   | `<< 0`         | 511 for stable, 0-510 for dev builds   |

### Version Code Formula

```
versionCode = (Major << 23) | (Minor << 16) | (Patch << 9) | Qualifier
```

### Maximum Values

- **Maximum version**: v127.127.127
- **Maximum version code**: 1,073,741,823 (2³⁰ - 1)
- **Google Play limit**: 2,100,000,000

## Build Types & Version Naming

The version name format differs based on the build type:

| Build Type   | Trigger               | Version Name Format                           | Example                          |
|--------------|----------------------|-----------------------------------------------|----------------------------------|
| **Release**  | Git tag `v*.*.*`     | `v{major}.{minor}.{patch}`                   | `v5.4.3`                         |
| **Pre-release** | Push to `master`  | `v{major}.{minor}.{patch}-dev.{count}+{sha}` | `v5.4.4-dev.42+a1b2c3d`          |
| **Test**     | Pull Request         | `ci-test-untrusted-{major}.{minor}.{patch}-dev.{count}+{sha}` | `ci-test-untrusted-5.4.4-dev.42+a1b2c3d` |

### Important Notes

- **Pre-release** and **Test** builds increment the patch version by 1 to ensure they are always HIGHER than the previous stable release
- **Test** builds use an explicitly **non-SemVer** format to make it clear they are untrusted

## Product Flavors

The app has two product flavors for co-installation support:

| Flavor       | Application ID            | Purpose                           |
|--------------|---------------------------|-----------------------------------|
| `prod`       | `org.joefang.webdav`      | Production releases               |
| `untrusted`  | `org.joefang.webdav.test` | Untrusted test builds (PRs)       |

This allows testers to install both the production app and test builds side-by-side.

## Secure Signing Architecture

### GitHub Environments

The CI/CD pipeline uses two separate GitHub Environments to isolate signing keys:

| Environment   | Purpose                      | Key Usage        | Protection Rules              |
|---------------|------------------------------|------------------|-------------------------------|
| `ci:test`     | Signing untrusted PR builds  | **Test Key**     | No restrictions (automated)   |
| `ci:release`  | Signing main/tag builds      | **Release Key**  | Branch: `master`, `v*`        |

### Key Isolation

> ⚠️ **SECURITY**: The test key and release key are **completely separate**. Untrusted PR code never has access to the production release key.

- **Test builds** (from PRs) are signed with a dedicated test key
- **Production builds** (from main branch and tags) are signed with the production release key
- The Build workflow (`build.yml`) has **no access to any signing secrets**

### CI/CD Workflows

#### 1. Build Workflow (`build.yml`)

The "untrusted builder" that builds APKs but never signs them:

- Triggered by: PRs, pushes to `master`, and tags
- Determines `CI_BUILD_TYPE` based on event
- Builds the appropriate flavor (untrusted for PRs, prod for releases)
- Uploads unsigned APKs as artifacts
- **Has no access to signing secrets**

#### 2. Sign Test Workflow (`sign-test.yml`)

Signs untrusted PR builds with the test key:

- Triggered by: Completion of Build workflow for PRs
- Uses environment: `ci:test`
- Downloads unsigned artifacts from Build workflow
- Signs with test key
- Uploads signed test APK for testers
- **Does NOT checkout code from the PR** (security measure)

#### 3. Sign Release Workflow (`sign-release.yml`)

Signs production builds with the release key:

- Triggered by: Completion of Build workflow for main/tags
- Uses environment: `ci:release` (with protection rules)
- Downloads unsigned artifacts from Build workflow
- Signs with production release key
- Creates GitHub Release

## ⚠️ Test Builds Warning

> **IMPORTANT**: Test builds (`.test` variant) are signed with a **separate test key** and are **UNTRUSTED**.
>
> These builds are provided for convenience of testing and should **NEVER** be used in production environments. They are clearly marked with the `ci-test-untrusted-` prefix in their version name.
>
> The test key is not the same as our production signing key. Installing test builds does not verify the authenticity of the source.

## Version Ordering Example

Assuming the latest release tag is `v5.4.3`:

| Version                              | Type        | Qualifier | Version Code |
|--------------------------------------|-------------|-----------|--------------|
| v5.4.3                               | Release     | 511       | 42,207,231   |
| v5.4.4-dev.1+a1b2c3d                 | Pre-release | 1         | 42,207,233   |
| ci-test-untrusted-5.4.4-dev.1+a1b2c3d| Test        | 1         | 42,207,233   |
| v5.4.4-dev.42+b2c3d4e                | Pre-release | 42        | 42,207,274   |
| v5.4.4                               | Release     | 511       | 42,207,743   |

## Environment Variables

The version system can be controlled via environment variables (used in CI):

| Variable              | Description                                      | Default        |
|-----------------------|--------------------------------------------------|----------------|
| `VERSION_MAJOR`       | Override major version                           | From git tag   |
| `VERSION_MINOR`       | Override minor version                           | From git tag   |
| `VERSION_PATCH`       | Override patch version                           | From git tag   |
| `VERSION_BUILD_COUNT` | Override build count (commits since tag)         | From git       |
| `VERSION_STABLE`      | Set to `"true"` for stable releases              | `"false"`      |
| `CI_BUILD_TYPE`       | Controls version name format                     | `"local"`      |

### CI_BUILD_TYPE Values

| Value        | Effect                                                    |
|--------------|-----------------------------------------------------------|
| `release`    | Version name: `v{M}.{m}.{p}`                             |
| `prerelease` | Version name: `v{M}.{m}.{p}-dev.{count}+{sha}`           |
| `test`       | Version name: `ci-test-untrusted-{M}.{m}.{p}-dev.{count}+{sha}` |
| `local`      | Same as `prerelease` (default for local builds)           |

## Key Generation

To generate signing keys for the GitHub Environments:

```bash
# Generate Test Key (for ci:test environment)
export ANDROID_KEY_ALIAS="test"
export ANDROID_KEYSTORE_PASSWORD="<your-strong-password>"
keytool -genkeypair -v \
  -keystore project-test.jks \
  -storetype JKS \
  -alias "$ANDROID_KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 1048576 \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -keypass "$ANDROID_KEYSTORE_PASSWORD" \
  -dname "CN=test,O=YourOrg,OU=Mobile,L=Toronto,ST=Ontario,C=CA"

# Generate Release Key (for ci:release environment)
export ANDROID_KEY_ALIAS="release"
export ANDROID_KEYSTORE_PASSWORD="<your-strong-password>"
keytool -genkeypair -v \
  -keystore project-release.jks \
  -storetype JKS \
  -alias "$ANDROID_KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 1048576 \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -keypass "$ANDROID_KEYSTORE_PASSWORD" \
  -dname "CN=release,O=YourOrg,OU=Mobile,L=Toronto,ST=Ontario,C=CA"

# Base64 encode for GitHub Secret
base64 -w 0 project-test.jks > project-test.jks.b64
base64 -w 0 project-release.jks > project-release.jks.b64
```

### Required Secrets/Variables per Environment

| Environment  | Secrets                                              | Variables              |
|--------------|------------------------------------------------------|------------------------|
| `ci:test`    | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`  | `ANDROID_KEY_ALIAS`    |
| `ci:release` | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`  | `ANDROID_KEY_ALIAS`    |

## Local Development

When building locally:

1. **With git tags**: Version derived automatically from latest `v*.*.*` tag
2. **Without git tags**: Falls back to `v0.0.1-dev.0`
3. **Override**: Use environment variables to set specific versions

```bash
# Build with custom version (stable release)
VERSION_MAJOR=1 VERSION_MINOR=2 VERSION_PATCH=3 VERSION_STABLE=true CI_BUILD_TYPE=release \
  ./gradlew assembleProdRelease

# Build test variant
CI_BUILD_TYPE=test ./gradlew assembleUntrustedRelease
```

## Troubleshooting

### "Build count exceeds limit"

You have more than 510 commits since the last release. Create a new release tag.

### "Version components exceed allocated bit width"

Major, minor, or patch version exceeds 127. The build system automatically rolls patch → minor → major when patch exceeds 127 for dev builds.

### Version code is lower than expected

Ensure you're using `fetch-depth: 0` in CI checkout to get full git history.
