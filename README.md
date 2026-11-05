# 📱 SMS Auto Forwarder (Android)

A robust, battery-optimized Android application designed to automatically forward incoming SIM SMS messages (OTPs, bank alerts, verification codes, or all messages) to **another phone number** via SMS.

Built with **Kotlin**, **Material 3**, and designed for modern Android versions (Android 7.0 / API 24 up to Android 14+ / API 34).

---

## 🚀 Key Features

* **Instant SMS Forwarding:** Listens to incoming SIM SMS via a high-priority `BroadcastReceiver` and immediately forwards to your specified phone number.
* **Anti-Loop Protection:** Intelligently detects and prevents forwarding loops (ignores messages already containing `[FWD from` or originating from your target number).
* **Multi-Part Message Support:** Automatically concatenates multi-segment incoming messages and splits outgoing messages properly using `SmsManager.sendMultipartTextMessage()`.
* **Keyword & Rule Filtering:**
  * **Forward All SMS:** Forwards every incoming message.
  * **Keyword Filter:** Forwards only messages matching specific keywords (e.g. `OTP, bank, alert, debit, credit, verification, balance`).
* **Dual-SIM Slot Selection:** Choose whether to send the forwarded SMS from SIM 1, SIM 2, or your system default SIM.
* **24/7 Background Persistence:**
  * **Foreground Service:** Runs with a low-priority notification to prevent Android OS and OEM task killers from killing the process.
  * **Boot Receiver:** Automatically restarts forwarding when the device is rebooted.
  * **Battery Optimization Helper:** One-click shortcut to exempt the app from aggressive Doze/Battery saver modes.
* **In-App Forwarding History:** Built-in SQLite database tracks recent messages, showing sender, timestamp, full body, target number, and delivery status (`SENT`, `FILTERED`, or `FAILED`).
* **Test SMS Button:** Verify your SIM setup and forwarding number with a single click before deploying.

---

## 📁 Project Structure

```
Ghajni/
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/ghajni/smsforwarder/
│   │   │   ├── data/
│   │   │   │   ├── AppPreferences.kt        # Settings & SharedPreferences
│   │   │   │   ├── SmsLogDbHelper.kt        # SQLite database for history
│   │   │   │   └── SmsLogItem.kt            # Data model
│   │   │   ├── receiver/
│   │   │   │   ├── SmsReceiver.kt           # Catches incoming SMS & triggers forwarding
│   │   │   │   └── BootReceiver.kt          # Restarts service upon device reboot
│   │   │   ├── service/
│   │   │   │   └── SmsForwarderService.kt   # Persistent Foreground Service
│   │   │   ├── util/
│   │   │   │   └── SmsSenderHelper.kt       # Multi-part & Dual-SIM SMS dispatcher
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt          # Dashboard, permissions & controls
│   │   │       └── LogAdapter.kt            # RecyclerView history adapter
│   │   └── res/
│   │       ├── layout/                      # Material 3 UI layouts
│   │       └── values/                      # Themes, colors, and strings
├── build.gradle
├── settings.gradle
└── gradlew.bat
```

---

## 🛠️ How to Open & Build the App

### Option A: Open with Android Studio (Recommended)
1. Open **Android Studio**.
2. Click **File -> Open...** (or "Open Existing Project" from the welcome screen).
3. Select this folder: `c:\Users\ptlpa\Desktop\New folder (3)\Ghajni`.
4. Allow Gradle to sync.
5. Connect your Android phone with **USB Debugging** enabled (under Developer Options).
6. Click the green **Run (▶)** button or press `Shift + F10`.

### Option B: Build APK via Terminal
Open a terminal in this folder and run:
```bash
gradlew assembleDebug
```
The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚙️ Initial Phone Setup (First Launch)

1. **Grant Permissions:**
   When you first open the app, grant the requested permissions:
   * **Receive SMS & Read SMS:** Allows detecting incoming messages.
   * **Send SMS:** Allows forwarding messages via your SIM plan.
   * **Phone State:** Needed to detect SIM slots in dual-SIM phones.
   * **Notifications:** Needed for Android 13+ background status indicator.

2. **Configure Forward-To Number:**
   * Enter the destination phone number including country code (e.g. `+1234567890` or `+919876543210`).
   * Tap **Save**.
   * Tap **Test SMS** to verify your SIM can send to that number.

3. **Select Rules & SIM Slot:**
   * Select **Forward All** or enable **Keyword Filter**.
   * Pick which SIM slot to send from (Default, SIM 1, or SIM 2).

4. **Turn ON Forwarding Switch:**
   * Flip the master toggle switch at the top. The status badge will turn green (**ACTIVE**).

---

## 🔋 Crucial OEM Background Settings (Don't Miss This!)

Android manufacturers (Samsung, Xiaomi/Redmi, OnePlus/Oppo, Vivo) have aggressive battery managers that sleep apps in the background. To ensure 100% reliable 24/7 forwarding:

1. **Disable Battery Optimization:**
   * Inside the app, tap **Exempt** on the Battery Optimization card (or go to `Settings -> Apps -> SMS Auto Forwarder -> Battery -> Unrestricted`).
2. **Enable Autostart (Xiaomi / MIUI / Vivo / Oppo):**
   * Go to `Settings -> Apps -> Manage Apps -> SMS Auto Forwarder -> Enable "Autostart"`.
3. **Lock App in Recents:**
   * Open the app switcher / recent apps screen, long-press or swipe down on the SMS Forwarder card, and tap the **Lock (🔒)** icon.

