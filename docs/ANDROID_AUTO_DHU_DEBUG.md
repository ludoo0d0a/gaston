## Debugging Android Auto with DHU

This guide explains how to debug Gaston on Android Auto using the **Android Auto Desktop Head Unit (DHU)** and the helper script `scripts/run-dhu.sh`.

**Quick start (USB device):** Use `scripts/debug-play-dhu.sh` to run checks, build/install the **debug** build, and start DHU in one go. See: `./scripts/debug-play-dhu.sh --help`.

### 1. Prerequisites

- **Android SDK / DHU installed**
  - In Android Studio: **Preferences → Appearance & Behavior → System Settings → Android SDK → SDK Tools**
  - Enable **Android Auto Desktop Head Unit emulator** and apply.
  - The DHU binary is expected at:
    - `"$ANDROID_HOME/extras/google/auto/desktop-head-unit"` (or using `ANDROID_SDK_ROOT`)
- **Device (physical only)**
  - **Physical Android phone** with **Android Auto** installed.
    DHU connects only to real devices; Android emulators are **not** supported for Android Auto with DHU.
- Developer mode + unknown sources enabled for Android Auto.
- **Environment**
  - `ANDROID_HOME` or `ANDROID_SDK_ROOT` set, or SDK installed in the default macOS path `~/Library/Android/sdk`.
  - `adb` available in your `PATH` (for ADB tunneling mode).

### 2. Build and install

- **Option A – Android Studio**
  - Run configuration **androidApp (debug)** (or select variant **debug**).
  - Pick your **physical phone** as the run target.
  - Click **Run** to install and start the app on the device.

- **Option B – CLI**

```bash
./gradlew :androidApp:installDebug
```

Then unlock the phone and start Gaston once so the system does not put it to sleep.

### 3. Starting DHU via `run-dhu.sh`

The helper script lives at `scripts/run-dhu.sh` and wraps the official DHU binary, handling SDK paths and (optionally) `adb forward`.
DHU always talks to a **physical phone** (over USB or ADB); it does not connect to the Android emulator.

Basic usages:

```bash
# USB accessory mode (default)
./scripts/run-dhu.sh

# ADB tunneling (recommended when USB mode is flaky)
./scripts/run-dhu.sh --adb

# Use a custom DHU config file
./scripts/run-dhu.sh -c config/default_1080p.ini
```

Key points:

- **Default mode: USB Accessory**
  - Connect the phone via **USB**.
  - Keep the screen **unlocked**.
  - Run:

    ```bash
    ./scripts/run-dhu.sh
    ```

  - The script launches DHU with:
    - `--usb -c config/default_720p.ini` (unless overridden by `-c`).

- **ADB tunneling mode (`--adb`)**
  - On the phone, in the Android Auto app:
    - Open **Developer settings**.
    - Enable **Start head unit server**.
    - Ensure **Add new cars to Android Auto** (or **Unknown sources**) is enabled.
  - Then run:

    ```bash
    ./scripts/run-dhu.sh --adb
    ```

  - The script tries:
    - `adb forward tcp:5277 tcp:5277`
  - If forwarding fails, it prints troubleshooting hints (e.g. `adb kill-server && adb start-server`).

You can pass any DHU `.ini` config file via `-c` to match your monitor resolution or layout.

### 4. Attaching the debugger

Once DHU is running and the phone is connected:

1. **Start Gaston on the phone**
   - Ensure the **debug** build is installed (see section 2).
   - Open Gaston once so Android Auto knows about it.
2. **Start an Android Auto session in DHU**
   - With DHU running, the phone should detect a “car” connection.
   - Android Auto should appear inside the DHU window.
3. **Attach Android Studio debugger**
   - In Android Studio, select your **phone** as the target device.
   - Use **Run → Attach Debugger to Android Process…** and pick the Gaston process.
   - Interact with Gaston via the DHU window; breakpoints in:
     - `VoiceAppService`
     - `VoiceSession`
     - Android Auto UI screens (e.g. `MainScreen`)
     will now be hit while you test.

### 5. Quick recap

- **Build/install** the debug build on your phone.
- **Enable** Android Auto developer mode + unknown sources.
- **Start DHU** via:
  - `./scripts/run-dhu.sh` (USB), or
  - `./scripts/run-dhu.sh --adb` (ADB tunneling).
- **Attach debugger** in Android Studio to the Gaston process while using the DHU window as your “car” screen.
