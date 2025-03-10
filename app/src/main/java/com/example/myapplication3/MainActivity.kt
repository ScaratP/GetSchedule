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
import androidx.compose.ui.text.font.FontWeight
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

data class CourseSchedule(
    val id: String,
    val courseName: String,
    val location: String,
    val weekDay: String,
    val timeslot: String
//    val startTime: String,
//    val endTime: String
)


suspend fun fetchWebData(url: String, cookies: String?): List<CourseSchedule> {
    return withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView")
            val rows = table.select("tr")

            val dataList = mutableListOf<CourseSchedule>()

            val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            val timeSlots = listOf(
                "07:10-08:00", "08:10-09:00", "09:10-10:00", "10:10-11:00", "11:10-12:00",
                "12:10-13:00", "13:10-14:00", "14:10-15:00", "15:10-16:00", "16:10-17:00",
                "17:10-18:00", "18:10-19:00", "19:10-20:00", "20:10-21:00", "21:10-22:00"
            )

            for ((rowIndex, row) in rows.withIndex()) {
                val columns = row.select("td")

                for ((colIndex, column) in columns.withIndex()) {
                    val span = column.selectFirst("span[title]")
                    val title = span?.attr("title")?.trim() ?: ""
                    val realRowIndex = rowIndex - 1
                    val id = "$colIndex$realRowIndex"

                    val weekDay = if (colIndex in 1..7) weekDays[colIndex - 1] else "未知"
                    val timeSlot = if (realRowIndex in timeSlots.indices) timeSlots[realRowIndex] else "未知時間"
//                    val startTime = timeSlot.split("-")[0]
//                    val endTime = timeSlot.split("-")[1]
2
                    if (title.isNotEmpty()) {
                        Log.d("WebScraper", "Extracted [$id]: $title")
                        val parsedData = parseTitle(title)

                        val courseSchedule = CourseSchedule(
                            id = id,
                            courseName = parsedData["科目名稱"] ?: "未知課程",
                            location = parsedData["場地"] ?: "未知地點",
                            weekDay = weekDay,
                            timeslot = timeSlot
//                            startTime = startTime,
//                            endTime = endTime
                        )

                        dataList.add(courseSchedule)
                    }
                }
            }

            Log.d("WebScraper", "Final parsed data: $dataList")
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
    var scheduleList by remember { mutableStateOf<List<CourseSchedule>>(emptyList()) }
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
                scheduleList.forEach { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "ID: ${course.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(text = "課程: ${course.courseName}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "地點: ${course.location}", fontSize = 14.sp)
                            Text(text = "星期: ${course.weekDay}", fontSize = 14.sp)
                            Text(text = "時間: ${course.timeslot}", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
