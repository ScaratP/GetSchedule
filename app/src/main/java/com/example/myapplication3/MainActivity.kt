package com.example.myapplication3

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleScreen()
        }
    }
}

@Composable
fun WebScraperScreen(title: String, schedule: List<Map<String, String>>) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = title, fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (schedule.isEmpty()) {
            Text(text = "No schedule found", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        } else {
            schedule.forEach { item ->
                item.forEach { (key, value) ->
                    Text(text = "$key: $value", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

suspend fun fetchWebData(url: String, cookies: String?): List<Map<String, String>> {
    return withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView") // 選擇目標表格
            val rows = table.select("tr") // 獲取所有行

            val dataList = mutableListOf<Map<String, String>>()

            for ((rowIndex, row) in rows.withIndex()) { // 遍歷每一行
                val columns = row.select("td") // 選擇該行的所有列
                val rowData = mutableMapOf<String, String>()

                for ((colIndex, column) in columns.withIndex()) { // 遍歷每一列
                    val span = column.selectFirst("span[title]") // 取得該列內的 span (如果有)
                    val title = span?.attr("title")?.trim() ?: "" // 獲取 title 屬性
                    val realRowIndex=rowIndex-1
                    val id = "$colIndex$realRowIndex" // 調整為 (colIndex)(realRowIndex)

                    if (title.isNotEmpty()) {
                        Log.d("WebScraper", "Extracted [$id]: $title") // **偵錯用，先印出 title**
                        val parsedData = parseTitle(title)
                        rowData[id] = parsedData.entries.joinToString { "${it.key}: ${it.value}" } // 轉為 "科目名稱: 物理, 場地: B101"
                    }
                }

                if (rowData.isNotEmpty()) {
                    dataList.add(rowData) // 只有當該行有資料時才加入
                }
            }

            Log.d("WebScraper", "Final parsed data: $dataList") // **印出最後的分析結果**
            return@withContext dataList
        } catch (e: Exception) {
            Log.e("WebScraper", "Error fetching data", e)
            emptyList()
        }
    }
}



fun parseTitle(title: String): Map<String, String> {
    val regexMap = mapOf(
        "科目名稱" to """科目名稱：(.+?)\n""",
        "場地" to """場地：(.+?)\n"""
    )

    val result = mutableMapOf<String, String>()
    regexMap.forEach { (key, pattern) ->
        val regex = Regex(pattern)
        val match = regex.find(title)
        if (match != null) {
            result[key] = match.groupValues.drop(1).joinToString(" ").trim()
        }
    }
    return result
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, onLoginSuccess: (String) -> Unit) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.contains("InfoLoginNew.aspx")) {
                        view.scrollTo(0, 0)
                        return
                    }
                    val cookies = CookieManager.getInstance().getCookie(url)
                    onLoginSuccess(cookies ?: "")
                }
            }
            loadUrl(url)
        }
    })
}

@Composable
fun ScheduleScreen() {
    var cookies by remember { mutableStateOf<String?>(null) }
    var scheduleList by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(cookies) {
        if (cookies != null) {
            isLoading = true
            scheduleList = fetchWebData(
                "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=", cookies!!
            )
            isLoading = false
        }
    }


    if (cookies == null) {
        WebViewScreen("https://infosys.nttu.edu.tw/InfoLoginNew.aspx") {
            cookies = it
        }
    } else {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(text = "Schedule", fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (scheduleList.isEmpty()) {
                Text(text = "No schedule found", fontSize = 14.sp)
            } else {
                scheduleList.forEach { item ->
                    item.forEach { (key, value) ->
                        Text(text = "$key: $value", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
