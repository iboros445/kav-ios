# Installing Kav on your iPhone (free, with SideStore)

Kav isn't on the App Store. You install an `.ipa` file with **SideStore**, which
re-signs it on the phone with your free Apple ID. Free signing expires every
**7 days**, and SideStore refreshes the app by itself while the phone is on
Wi-Fi (details at the bottom).

Free Apple ID limits: 3 active sideloaded apps (SideStore itself counts as one),
10 App IDs per 7 days.

## 1. Get the IPA

Every push builds one. Until a release exists, take it from the build:

1. GitHub → this repo → **Actions** → the latest green **iOS** run.
2. Download the **Kav-ipa** artifact (a zip containing `Kav.ipa`). On a computer
   this is a normal download. You must be logged in to GitHub.

Once a release exists (tag `ios-v*`), take `Kav.ipa` from **Releases** instead.

## 2. Install SideStore (one time, needs a computer)

Follow SideStore's own current guide: <https://docs.sidestore.io/>. In short:

1. Install the SideStore installer (SideServer/Jitterbug-based) on a Mac/Windows/Linux computer.
2. Plug in the iPhone, trust the computer, and install SideStore with your Apple ID.
3. On the phone: **Settings → General → VPN & Device Management** → trust your Apple ID profile.
4. **Settings → Privacy & Security → Developer Mode** → on, then restart.
5. Install **LocalDevVPN** (from SideStore's docs) and keep it enabled.
   It gives SideStore the loopback tunnel it needs to refresh apps without a computer.
6. Import the pairing file when SideStore asks for it.

## 3. Install Kav

Open the downloaded `Kav.ipa` in SideStore (Share → SideStore, or SideStore →
My Apps → **+** → pick the file). First launch downloads the 176 MB offline map
once.

## 4. Updates (after Phase 5)

Add Kav's source in SideStore → Sources → **+**:

```
https://iboros445.github.io/kav-ios/source.json
```

Updates then appear as one-tap updates.

## Refreshing

SideStore re-signs apps when they have less than 2 days left. Keep LocalDevVPN
on, and optionally add the Shortcuts automation from SideStore's docs so refresh
runs without opening the app. If an app does expire, open SideStore and tap
Refresh. Your Kav data stays on the phone.

## Optional: LiveContainer

Running Kav inside LiveContainer uses no app slot and has no 7-day expiry of its
own. Not recommended if the Live Activity phase (Phase 7) is used, because Live
Activities need Kav installed directly through SideStore.
