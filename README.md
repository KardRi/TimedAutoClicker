# 定時自動點擊器 (Timed Auto Clicker) - Android 13

專為 **Android 13 (API Level 33)** 設計的高精度定時螢幕自動點擊工具。基於 Android 官方 **無障礙服務 (AccessibilityService)** 的 `dispatchGesture` API 實作，無需 Root、無需啟用 USB 除錯，即可在指定時間精準自動點擊指定螢幕座標。

---

## 🌟 主要功能特性

1. **多元時間設定格式**：
   - **標準日期時間 (Standard Datetime)**：年、月、日、時、分、秒，外加毫秒級微調（0~999ms），提供圖形化日期與時間選擇器。
   - **Epoch 時間戳 (Epoch Timestamp)**：支援輸入 10 位數（秒，如 `1773900000`）或 13 位數（毫秒，如 `1773900000000`），自動辨識轉換。
   - **相對倒數計時 (Countdown)**：指定幾秒後自動執行點擊。
2. **毫秒級高精準度觸發引擎 (High-Precision Timing)**：
   - 採用 **前台服務 (Foreground Service)** + **CPU 喚醒鎖定 (WakeLock)**。
   - 採用分段排程機制：在觸發前 50ms 進入微秒級自旋等待 (Spin-wait & Thread yield)，確保在搶票、搶購或排程打卡情境下無延遲觸發。
3. **螢幕座標選取與懸浮靶心 (Floating Crosshair Overlay)**：
   - 支援手動輸入像素座標 $(X, Y)$。
   - 提供**懸浮靶心定位儀**：拖曳靶心到任何第三方 App 的按鈕上方，一鍵自動將中心座標帶入！
   - 提供**「立即測試點擊」**按鈕：預先驗證座標與無障礙服務運作正常。
4. **進階點擊選項**：
   - 支援連點次數（Burst Click，如連點 3 次）。
   - 支援連點間隔（ms）與按壓持續時間（ms）。
5. **完整適配 Android 13 隱私與安全規範**：
   - 完整適配 Android 13 的 `POST_NOTIFICATIONS` 執行時期通知權限。
   - 支援 `SCHEDULE_EXACT_ALARM` 與電池最佳化白名單檢查。

---

## 📱 核心架構與無障礙原理

### 1. 手勢派發 (Accessibility Gesture Dispatch)
在 [`AutoClickAccessibilityService.kt`](app/src/main/java/com/autoclicker/timed/service/AutoClickAccessibilityService.kt) 中透過系統提供的 `dispatchGesture` 模擬點擊：

```kotlin
val path = Path().apply { moveTo(x, y) }
val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
val gesture = GestureDescription.Builder().addStroke(stroke).build()

dispatchGesture(gesture, object : GestureResultCallback() {
    override fun onCompleted(gestureDescription: GestureDescription?) {
        // 點擊成功
    }
    override fun onCancelled(gestureDescription: GestureDescription?) {
        // 點擊被取消
    }
}, null)
```

### 2. Android 13 無障礙設定檔 ([`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml))
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

---

## ⚠️ Android 13 安裝與設定關鍵步驟（必讀）

Android 13 對於非 Google Play 下載的 APK 增加了**「受限制的設定 (Restricted Settings)」**安全防護，如果直接去開無障礙服務可能會顯示「無法使用這項設定 (Setting is currently unavailable)」。請依照下列步驟解鎖：

1. **安裝 APK** 後，請先不要直接開無障礙設定。
2. 長按「定時自動點擊器」桌面圖示 ➔ 點擊 **「應用程式資訊 (App info)」**（或從「設定」➔「應用程式」找到本 App）。
3. 點擊右上角的 **三個垂直點 (`⋮`)**。
4. 點選 **「允許受限制的設定 (Allow restricted settings)」**，並驗證指紋或解鎖密碼。
5. 回到本 App，點擊「無障礙服務 (點擊核心)」➔ 在系統列表找到「定時自動點擊輔助服務」並開啟。
6. 開啟「懸浮窗權限」與「精確鬧鐘權限」。
7. 建議在電池設定中將本 App 設為「無限制 (Unrestricted)」，避免系統後台深層休眠。

---

## 🛠️ 如何編譯成 APK

### 方式 1：使用 Android Studio 直接開啟（推薦）
1. 開啟 **Android Studio**。
2. 點擊 **Open**，選擇專案資料夾：
   `C:\Users\user\.gemini\antigravity-ide\scratch\TimedAutoClicker`
3. 等待 Gradle Sync 完成。
4. 在上方選單點選 **Build** ➔ **Build Bundle(s) / APK(s)** ➔ **Build APK(s)**。
5. 編譯完成後點擊通知中的 **locate**，即可取得 `app-debug.apk`。

### 方式 2：使用命令列編譯
需先安裝 JDK 17 與 Android SDK：
```bash
./gradlew assembleDebug
```
編譯產物位於：`app/build/outputs/apk/debug/app-debug.apk`。

### 方式 3：使用 GitHub Actions 免費線上自動編譯
專案內已附帶 [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)：
1. 將此專案推送至您個人的 GitHub 倉庫。
2. GitHub Actions 會自動啟動虛擬機編譯。
3. 於 Actions 頁面直接下載編譯好的 `TimedAutoClicker-Debug-APK.zip` 即可！

---

## 🚀 透過 ADB 安裝到手機
若手機已開啟 USB 除錯並連接電腦：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
