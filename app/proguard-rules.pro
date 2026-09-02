# WinKoKo VPN / Xray native bridge
-keep class libv2ray.** { *; }
-dontwarn libv2ray.**

# Keep JSON model/signature metadata used by the app and native bridge.
-keepattributes Signature
-keepattributes *Annotation*

# Android VPN service callbacks are referenced by the AAR/native bridge.
-keep class com.winkokoo.vpn.WinKoKoVpnService { *; }
