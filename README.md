# 臺東大學課程表應用程式

這是一個為臺東大學學生開發的課程表應用程式，能夠自動從學校的資訊系統擷取課程資料並以直觀的方式呈現。本應用程式以 Jetpack Compose 開發，提供了現代化的使用者介面和流暢的使用體驗。

## 功能特色

- **自動登入校園系統**：使用內建 WebView 進行校園系統登入，無需重複輸入帳號密碼
- **自動擷取課表**：登入成功後自動從學校系統擷取最新課程資料
- **直觀課表顯示**：以星期和時間為軸的表格形式顯示課程
- **課程詳細資訊**：點擊課程可查看完整的課程資訊，包括老師、地點和時間安排
- **課程地點編輯**：對於標示為「其它」的課程地點，可自行編輯更新
- **離線瀏覽**：資料存儲於本地資料庫，無需連網也能查看課表
- **資料自動更新**：可隨時點擊「刷新」按鈕更新課表資料

## 技術實現

### 開發環境
- **程式語言**：Kotlin 2.0.0
- **最低 SDK 版本**：26 (Android 8.0)
- **目標 SDK 版本**：35 (Android 15)
- **Gradle 版本**：8.10.2

### 使用技術
- **Jetpack Compose**：現代化的 Android UI 開發框架
- **Room Database**：本地資料儲存
- **Coroutines**：非同步處理
- **ViewModel & LiveData**：資料狀態管理
- **WebView**：校園系統登入
- **JSoup**：網頁解析
- **Material 3 Design**：UI 設計規範

### 資料結構
```kotlin
@Entity(tableName = "course_table")
data class CourseEntity(
    @PrimaryKey val id: String,
    val courseName: String,
    val teacherName: String,
    val location: String,
    val weekDay: String,
    val startTime: LocalTime,
    val endTime: LocalTime
)
```

## 使用指南

### 首次登入
1. 首次啟動應用程式時，會自動導向校園資訊系統登入頁面
2. 使用您的學校帳號密碼登入（登入資訊不會被應用程式存取）
3. 登入成功後，系統會自動擷取課表資訊並顯示於主畫面

### 課表瀏覽
- 課表以星期為列、時間為欄的方式排列
- 有課程的時間段會以醒目的顏色標示
- 點擊課程方格可查看該課程的詳細資訊
- 沒有課程的時間段顯示為空白

### 課程詳細資訊
- 點擊課程後，畫面下方會顯示課程的詳細資訊
- 詳細資訊包括課程名稱、授課教師、上課地點及時間安排
- 若課程地點顯示為「其它」，可點擊卡片編輯地點

### 更新課表
- 點擊右上角的「刷新」按鈕可以重新登入並更新課表資料
- 更新操作會保留您手動編輯的課程地點資訊

## 系統需求
- Android 8.0 (Oreo) 或更高版本
- 網絡連接（首次登入或更新課表時需要）
- 學校的有效帳號密碼

## 安裝方式
1. 直接下載 APK 檔案安裝
2. 或使用 Android Studio 編譯並安裝到您的裝置

## 開發與貢獻
如果您想為本專案做出貢獻：
1. Fork 本專案 
2. 建立新的功能分支 (`git checkout -b feature/new-feature`)
3. 提交您的更改 (`git commit -am 'Add some feature'`)
4. 推送到分支 (`git push origin feature/new-feature`)
5. 建立 Pull Request

## 隱私聲明
本應用程式不會收集或儲存您的學校帳號密碼，所有的帳號驗證都直接通過學校的網站進行。課程資料僅儲存在您的設備上，不會上傳到其他伺服器。

## 免責聲明
本應用程式為非官方應用，與臺東大學官方無關。應用程式的功能可能因學校系統變更而受影響。
