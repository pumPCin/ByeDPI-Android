# ByeByeDPI Android
[Русский](README.md) | English

<div style="text-align: center;">
  <img alt="ByeDPI Logo" src="https://github.com/romanvht/ByeDPIAndroid/raw/master/.github/images/logo.svg" width="100%" height="200px">
</div>

---

An Android application that locally runs ByeDPI and routes all traffic through it.

For stable operation, you may need to adjust the settings. You can read more about different settings in the [ByeDPI documentation](https://github.com/hufrea/byedpi/blob/v0.13/README.md).

This application is **not** a VPN. It uses Android's VPN mode to route traffic but does not transmit anything to a remote server. It does not encrypt traffic or hide your IP address.

This application is a fork of [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid).

---

### Features
* Autostart service on device boot
* Saving lists of command-line parameters
* Improved compatibility with Android TV/BOX
* Per-app split tunneling
* Import/export settings

### Usage
* To enable auto-start, activate the option in settings.
* It is recommended to connect to the VPN once to accept the request.
* After that, upon device startup, the application will automatically launch the service based on settings (VPN/Proxy).
* If you are using an Android TV/BOX and your Ethernet connection drops when connecting, enable whitelist mode and specify the apps that should work through the VPN (e.g., YouTube).
* Guide for [SberBOX](sbox.md)

### How to use ByeByeDPI with AdGuard?
* Start ByeByeDPI in proxy mode.
* Add ByeByeDPI to AdGuard exclusions on the "App Management" tab.
* In AdGuard settings, specify the proxy:
```plaintext
Proxy Type: SOCKS5
Host: 127.0.0.1
Port: 1080 (default)
```

### Dependencies
- [ByeDPI](https://github.com/hufrea/byedpi)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)