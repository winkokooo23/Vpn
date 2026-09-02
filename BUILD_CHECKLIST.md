# Build checklist

- [x] Android namespace/applicationId aligned
- [x] SDK 35
- [x] Java/Kotlin JVM 17
- [x] AGP 8.7.3 / Gradle 8.9
- [x] Xray Android core pinned to v26.8.20
- [x] Foreground VPN service declaration
- [x] VPN permission flow
- [x] Subscription persistence
- [x] Selected-server persistence
- [x] Broadcast-based connection state refresh
- [x] R8/ProGuard keep rules for native bridge
- [x] GitHub Actions debug + release artifacts

## Final runtime check

A real Android device/emulator check is still required for VPN permission, TUN creation, DNS, and a real VLESS/VMess/Trojan server connection.
