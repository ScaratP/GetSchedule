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
fun WebScraperScreen(title: String, schedule: List<String>) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = title, fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (schedule.isEmpty()) {
            Text(text = "No schedule found", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        } else {
            schedule.forEach { item ->
                Text(text = item, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

suspend fun fetchWebData(url: String, cookies: String?): List<String> {
    return try {
        val doc: Document = Jsoup.connect(url).apply {
            cookies?.let { header("Cookie", it) }
            userAgent("Mozilla/5.0")
            timeout(10000)
        }.get()

        val titles = doc.select("span").mapNotNull { it.attr("title").takeIf { it.isNotEmpty() } }

        Log.d("WebScraper", "Title: ${doc.title()}")
        titles.forEach { Log.d("WebScraper", "Title: $it") }

        titles
    } catch (e: Exception) {
        Log.e("WebScraper", "Error fetching data", e)
        emptyList()
    }
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
    var scheduleList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(cookies) {
        cookies?.let {
            isLoading = true
            scheduleList = withContext(Dispatchers.IO) {
                fetchWebData("https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=", it)
            }
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
                    Text(text = item, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}
