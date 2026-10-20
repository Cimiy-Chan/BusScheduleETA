# Android Studio 匯出 APK（給朋友測試）

**適用：** Android Studio（Quail 2 等近期版本）  
**目的：** 把專案打成可安裝的 **APK**（不是 PAK），傳到手機安裝測試  
**文件日期：** 2026-10-20（香港時間）  
**範例專案：** 快手巴士（BusScheduleETA）等

---

## 開始前

1. 用 Android Studio **打開專案根目錄**（有 `settings.gradle`／`settings.gradle.kts` 那一層）。
2. 等右下角同步／索引完成（Gradle Sync 成功）。
3. 確認能編譯：選單 **Build → Assemble Project**（有些版本沒有「Make Project」，用這項即可）。

---

## 方法一：產生已簽名的 Release APK（建議給朋友用）

朋友要側載安裝，建議用 **Release + 簽名**，不要用 Debug 檔長期派發。

### 1. 打開匯出精靈

選單：**Build → Generate Signed App Bundle or APK…**  
（有些版本寫成 **Generate Signed Bundle / APK**）

### 2. 選 APK

1. 選 **APK**（不是 Android App Bundle／`.aab`）。  
2. 按 **Next**。

> `.aab` 主要給 Google Play 上架；給朋友直接安裝請選 **APK**。

### 3. 金鑰庫（Key store）

**第一次：**

1. 按 **Create new…**
2. 填寫例如：
   - **Key store path：** 選一個你找得到的位置（例如專案外的資料夾），檔名如 `cimiy-release.jks`
   - **Password／Confirm：** 自己設並記下來
   - **Alias：** 例如 `busscheduleeta`
   - **Key password：** 可與 store 相同或另設
   - **Validity：** 建議 25 年以上
   - Certificate 欄：姓名／機構可填你的名字或公司；其餘可簡填
3. 按 **OK**，再 **Next**

**之後每次：**

1. 選已有的 `.jks`／`.keystore`
2. 輸入密碼與 Alias → **Next**

> 金鑰檔與密碼請自己備份；弄丟就無法用「同一個簽名」更新已安裝的 App。

### 4. 建置類型

1. **Build Variants：** 選 **release**
2. Signature Versions：可勾 **V1** 與 **V2**（一般兩者都勾）
3. 按 **Create**／**Finish**

### 5. 找到 APK 檔

建置成功後，Android Studio 通常會跳出通知，可按 **locate**。

常見路徑（依專案名而定）：

```
專案根目錄/app/release/app-release.apk
```

或：

```
專案根目錄/app/build/outputs/apk/release/app-release.apk
```

把這個 `.apk` 複製到桌面／雲端／傳給朋友即可。

---

## 方法二：只打 Release（已設定好簽名時）

若 `app/build.gradle.kts` 已設好 `signingConfigs`，也可：

1. 右下角／左下 **Build Variants** → `app` 選 **release**
2. **Build → Assemble Project**  
   或右側 **Gradle** → `app` → `Tasks` → `build` → 雙擊 **assembleRelease**

APK 同樣出現在 `app/build/outputs/apk/release/` 一帶。

---

## 傳給朋友與安裝

1. 用 Telegram／WhatsApp／電郵／USB 傳 `app-release.apk`。
2. 在 Android 手機打開該檔。
3. 若提示「禁止未知來源」：到設定允許「從此來源安裝」或「安裝未知應用程式」。
4. 安裝後桌面應出現 App 圖示（例如 **快手巴士**）。

**注意：**

- 若手機上已裝「不同簽名」的舊版（例如之前用 Debug 裝的），可能要先**卸載**再裝，否則會失敗。
- 同一簽名、且 `versionCode` 較新，通常可直接覆蓋更新。
- 正式給人測之前，建議把 `versionName`／關於頁改成正確版本（例如 Ver 1.0），需要覆蓋更新時把 `versionCode` +1。

---

## 常見問題

| 情況 | 處理 |
|------|------|
| Gradle Sync 失敗 | 看 Build 視窗錯誤；網絡／SDK 路徑；試 **File → Sync Project with Gradle Files** |
| 找不到 Generate Signed… | 確認已打開 Android 專案，不是單純資料夾；選單在 **Build** 下 |
| 朋友裝不上 | 傳 Release 簽名 APK；先卸載舊版；允許未知來源 |
| 只要自己機試 | 用 USB 接手機，按綠色 Run 即可，不必每次匯出 APK |

---

## 與本專案文件的關係

- 使用說明：`快速使用手冊.md`
- 技術說明：`技術手冊.md`、`技術手冊-實作附錄.md`
- 本檔只講 **AS → APK** 步驟，方便每次出測試版時照做。

---

*文件日期：2026-10-20（香港時間）*
