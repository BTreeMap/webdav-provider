# Version Code Generation

This document describes the automatic version code generation system used in WebDAV Provider.

## Overview

The app uses a **30-bit version code schema** that automatically derives version codes from git tags. This ensures:

1. **Proper ordering**: Beta builds are always higher than previous stable releases
2. **Google Play compliance**: Maximum value (~1.07 billion) is under the 2.1 billion limit
3. **Automation**: No manual version code management needed

## The 30-Bit Schema (7-7-7-9 bits)

| Component | Bits | Range   | Shift Position | Description                            |
|-----------|------|---------|----------------|----------------------------------------|
| Major     | 7    | 0-127   | `<< 23`        | Major version number                   |
| Minor     | 7    | 0-127   | `<< 16`        | Minor version number                   |
| Patch     | 7    | 0-127   | `<< 9`         | Patch version number                   |
| Qualifier | 9    | 0-511   | `<< 0`         | 511 for stable, 0-510 for beta builds  |

### Version Code Formula

```
versionCode = (Major << 23) | (Minor << 16) | (Patch << 9) | Qualifier
```

### Maximum Values

- **Maximum version**: v127.127.127
- **Maximum version code**: 1,073,741,823 (2³⁰ - 1)
- **Google Play limit**: 2,100,000,000

## Release Types

### Stable Releases (Tagged)

Stable releases are created by pushing a git tag matching `v*.*.*` pattern:

```bash
git tag v5.4.3
git push origin v5.4.3
```

- **Qualifier**: 511 (maximum value)
- **Version Name**: `v5.4.3`
- **Version Code**: `(5 << 23) | (4 << 16) | (3 << 9) | 511 = 42,207,231`

### Beta/Development Builds

Beta builds are created automatically for any commit after a stable release:

- **Qualifier**: Commits since last tag (1-510)
- **Patch**: Incremented by 1 from the tag version
- **Version Name**: `v5.4.4-beta.X` (where X = commits since tag)

This increment ensures beta builds are **always higher** than the previous stable release.

## Version Ordering Example

Assuming the latest release tag is `v5.4.3`:

| Version          | Type   | Major | Minor | Patch | Qualifier | Version Code |
|------------------|--------|-------|-------|-------|-----------|--------------|
| v5.4.3           | Stable | 5     | 4     | 3     | 511       | 42,207,231   |
| v5.4.4-beta.1    | Beta   | 5     | 4     | 4     | 1         | 42,207,233   |
| v5.4.4-beta.2    | Beta   | 5     | 4     | 4     | 2         | 42,207,234   |
| v5.4.4-beta.55   | Beta   | 5     | 4     | 4     | 55        | 42,207,287   |
| v5.4.4           | Stable | 5     | 4     | 4     | 511       | 42,207,743   |
| v5.5.0           | Stable | 5     | 5     | 0     | 511       | 42,271,231   |

**Key insight**: `v5.4.3 < v5.4.4-beta.1 < v5.4.4-beta.2 < v5.4.4 < v5.5.0`

## Why Increment Patch for Betas?

If betas used the same Major.Minor.Patch as the previous stable:
- `v5.4.3` (stable) = `(5 << 23) | (4 << 16) | (3 << 9) | 511` = 42,207,231
- `v5.4.3-beta.1` = `(5 << 23) | (4 << 16) | (3 << 9) | 1` = 42,206,721 ❌ **LOWER!**

By incrementing patch:
- `v5.4.3` (stable) = `(5 << 23) | (4 << 16) | (3 << 9) | 511` = 42,207,231
- `v5.4.4-beta.1` = `(5 << 23) | (4 << 16) | (4 << 9) | 1` = 42,207,233 ✅ **HIGHER!**

## Build Count Limits

The qualifier field has 9 bits, allowing values 0-511:
- **0**: Used when on the exact tag commit itself (no beta, no stable flag set)
- **1-510**: Available for beta builds (commits after last tag)
- **511**: Reserved for stable releases

If you exceed 510 commits between releases, the build will fail. Solution: create a new release tag.

## Environment Variables

The version system can be controlled via environment variables (used in CI):

| Variable             | Description                                      | Default        |
|----------------------|--------------------------------------------------|----------------|
| `VERSION_MAJOR`      | Override major version                           | From git tag   |
| `VERSION_MINOR`      | Override minor version                           | From git tag   |
| `VERSION_PATCH`      | Override patch version                           | From git tag   |
| `VERSION_BUILD_COUNT`| Override build count (commits since tag)         | From git       |
| `VERSION_STABLE`     | Set to `"true"` for stable releases              | `"false"`      |

## CI/CD Workflows

### Pull Request / Push Builds (`ci-build.yml`)

- Builds debug APK with beta versioning
- Patch is auto-incremented for beta builds
- Artifacts uploaded for testing

### Tag-Triggered Releases (`release.yml`)

- Triggered by pushing `v*.*.*` tags
- Builds signed release APK
- Creates GitHub release with APK attachment

### Legacy Workflow (`build-release.yaml`)

- Deprecated, kept for backward compatibility
- Builds beta releases on master push
- Use tag-triggered releases for new stable releases

## Local Development

When building locally:

1. **With git tags**: Version derived automatically from latest `v*.*.*` tag
2. **Without git tags**: Falls back to `v0.0.1-beta.0`
3. **Override**: Use environment variables to set specific versions

```bash
# Build with custom version
VERSION_MAJOR=1 VERSION_MINOR=2 VERSION_PATCH=3 VERSION_STABLE=true ./gradlew assembleRelease
```

## Troubleshooting

### "Build count exceeds limit"

You have more than 510 commits since the last release. Create a new release tag.

### "Version components exceed allocated bit width"

Major, minor, or patch version exceeds 127. This is extremely rare and indicates one of:
- **Patch overflow**: The build system automatically rolls patch → minor → major when patch exceeds 127 for beta builds. If this happens, the next stable release should use the rolled-over version.
- **Very high version numbers**: If you've legitimately reached v127.x.x, consider resetting to v0.0.0 with a new package ID.

### Version code is lower than expected

Ensure you're using `fetch-depth: 0` in CI checkout to get full git history.
