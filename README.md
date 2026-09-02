# WinKoKo VPN

A lightweight Android VPN client built around the Xray Android core. The app provides a dark WinKoKo VPN interface, subscription import, server selection, Android VPN permission handling, and a foreground VPN service.

## Included features

The app supports VLESS, VMess, and Trojan subscription links, including common TLS, WebSocket, gRPC, and Reality parameters supported by the parser. The selected server and subscription are persisted locally. The Xray Android core is downloaded by CI and pinned to `v26.8.20` so upstream changes do not silently alter a build.

## Build configuration

| Component | Version or value |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| Kotlin | 2.0.21 |
| Compile SDK | 35 |
| Target SDK | 35 |
| Minimum SDK | 24 |
| Java / Kotlin JVM | 17 |

The minimum SDK is 24 because the pinned `libv2ray.aar` declares API 24 as its minimum supported level.

## Build locally

Install Android SDK Platform 35 and Build Tools 35.0.0, download the pinned Xray AAR, and then run:

```bash
mkdir -p app/libs
curl -fL --retry 5 --retry-all-errors \
  -o app/libs/libv2ray.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.8.20/libv2ray.aar
./gradlew clean assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. A release build can be generated with:

```bash
./gradlew assembleRelease
```

The local release output is unsigned at `app/build/outputs/apk/release/app-release-unsigned.apk`. It is suitable for testing but must be signed with your own private key before production distribution. Never commit a private signing key or its passwords to this repository.

## Build on GitHub

Create a new GitHub repository, upload or push the project files, and push to the `main` branch. The workflow at `.github/workflows/android.yml` will install the Android SDK, download the pinned Xray AAR, and build both debug and release APKs. You can also open **Actions → Build WinKoKo VPN → Run workflow** to start a manual build.

After the workflow completes, open the run and download the `WinKoKo-VPN-debug` or `WinKoKo-VPN-release` artifact. The release artifact is unsigned unless signing secrets and a signing configuration are added by the repository owner.

## Runtime note

A real Android device or emulator test is still required for VPN permission approval, TUN interface creation, DNS behavior, and connection to a real VLESS, VMess, or Trojan server. The build itself has been verified locally for both debug and release variants.
