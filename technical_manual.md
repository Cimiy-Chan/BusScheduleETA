# 三、技術手冊

**專案：** BusScheduleETA  
**語言／介面：** Kotlin、XML ViewBinding、Material 3（淺色主題）  
**最低實務：** 需 INTERNET；已設 `networkSecurityConfig` 信任香港郵政 CA（解決部分手機 SSL／Certificate 問題）。

## 架構概要
- **UI：** `MainActivity`（常用格）→ `EtaActivity`（到站）→ 新增流程 `PickRouteActivity` → `PickDirectionActivity` → `PickStopActivity`。  
- **本機資料：** Room（`FavoriteTrip`），Flow 自動刷新首頁。  
- **網絡：** OkHttp + 自訂 `BusApi`；九巴／城巴各轉成統一的 `RouteOption`／`StopOption`／`EtaItem`。  
- **刷新：** 到站頁於 `onStart` 每 30 秒查一次，`onStop` 取消。

## 主要資料表 `favorites`
| 欄位 | 用途 |
|------|------|
| company | `KMB`／`CTB` |
| route / direction / serviceType | 查 ETA 用（九巴要 serviceType） |
| stopId | 車站編號（城巴 6 位；九巴 16 字元） |
| routeLabel / destLabel / stopLabel | 畫面上繁體文案 |
| sortOrder | 排序 |

上限 9 筆；滿則隱藏 FAB。

## 外部 API
| 公司 | Base | 重點 |
|------|------|------|
| 九巴 | `https://data.etabus.gov.hk/v1/transport/kmb/` | 可一次取全路線／全站；ETA 要篩 `dir`（及 service_type） |
| 城巴 | `https://rt.data.gov.hk/v2/transport/citybus/` | 用 **v2**；無全站清單，站名逐個查並快取；注意 403／429，已做 delay＋重試 |

到站時間字串用 `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")` 解析；畫面顯示剩餘分鐘，&lt;1 分鐘為「即將到站」。

## 主要畫面檔
`activity_main`、`item_favorite`、`activity_eta`、`activity_pick_route`、`item_route`、`activity_pick_direction`、`activity_pick_stop`、`item_stop`、`view_app_toast`；啟動圖為巴士＋時鐘 vector（safe zone 0.85）。

## 建置與備份
- 插件：KSP、Room 2.8.x、ViewBinding、OkHttp、Coroutines、Lifecycle。  
- 倉庫建議獨立：`BusScheduleETA`（Private）。  
- 勿提交 `local.properties`、keystore。

## 測試建議
1. 九巴、城巴各加一常用，核對市面 App 分鐘數。  
2. 搜尋：`72` 開頭相符；`72 `（加空格）僅完全相同。  
3. 同路線加反方向（城巴較慢，不應再輕易 403）。  
4. 長按刪除、滿 9 個時「＋」消失。  
5. 飛行模式：應見網絡錯誤提示（可帶警告圖示）。

---

三份齊了。@軟件開發工程師 技術段落若要補版本號或更細 API 路徑，請直接加在第三份後面。備份完、今日收工。明天見。