<img align="left" width="80" height="80" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"
alt="App icon">

# WebDAV Provider [![Build](https://github.com/BTreeMap/WebDAV/actions/workflows/build.yml/badge.svg)](https://github.com/BTreeMap/WebDAV/actions/workflows/build.yml)

__WebDAV Provider__ is an Android app that can expose WebDAV through Android's
Storage Access Framework (SAF). This allows you to access your WebDAV storage
through Android's built-in file explorer, as well as other apps on your device.

## Fork Information

> **⚠️ This is a fork of [alexbakker/webdav-provider](https://github.com/alexbakker/webdav-provider)**
>
> This project has been forked and significantly modified. We use a **different package name** (`org.joefang.webdav` instead of `dev.rocli.android.webdav`) and **do not maintain compatibility** with the original project. Updates and features may diverge from the upstream project.
>
> If you're looking for the original project, please visit [alexbakker/webdav-provider](https://github.com/alexbakker/webdav-provider).

### Key Differences from Upstream

- **Package Name**: `org.joefang.webdav` (original: `dev.rocli.android.webdav`)
- **Separate Signing Keys**: Test builds use a different signing key than production releases
- **No Backward Compatibility**: This fork may introduce breaking changes
- **Independent Development**: Features and bug fixes may not be merged back upstream

## Screenshots

[<img src="screenshots/screenshot1.png"
width="200">](screenshots/screenshot1.png) [<img
src="screenshots/screenshot2.png" width="200">](screenshots/screenshot2.png)
[<img src="screenshots/screenshot3.png"
width="200">](screenshots/screenshot3.png) [<img
src="screenshots/screenshot4.png" width="200">](screenshots/screenshot4.png)

## Development

This project is automatically tested against a variety of different WebDAV servers. The tests run in an Android emulator and connect to the WebDAV servers running in separate containers on the host machine. 

### Testing

To spin up the test environment:

```sh
docker compose --project-directory tests up -d --wait --force-recreate --build --renew-anon-volumes --remove-orphans
```

Assuming an Android emulator is running, use the following command to run the tests:

```sh
./gradlew connectedCheck
```

To shut the test environment down:

```sh
docker compose --project-directory tests down -v
```

### Versioning

This project uses an automatic versioning system based on git tags. See [docs/VERSIONING.md](docs/VERSIONING.md) for details on:

- The 30-bit version code schema
- How stable vs development versions are calculated
- CI/CD workflow configuration and signing architecture
