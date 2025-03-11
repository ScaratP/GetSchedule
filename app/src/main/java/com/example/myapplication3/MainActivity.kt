package com.example.myapplication3

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MainActivity : ComponentActivity() {
    private val viewModel: CourseViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = CourseRepository(db.courseDao())
                @Suppress("UNCHECKED_CAST")
                return CourseViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleScreen(viewModel)
        }
    }
}



@Entity(tableName = "course_table")
data class CourseEntity(
    @PrimaryKey val id: String,
    val courseName: String,
    val teacherName: String,
    val location: String,
    val weekDay: String,
    val startTime: String,
    val endTime: String
)

@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    @Query("SELECT * FROM course_table WHERE weekDay = :weekDay AND startTime = :startTime")
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<CourseEntity>>

    @Query("SELECT * FROM course_table")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("DELETE FROM course_table")
    suspend fun clearAllCourses()
}

@Database(entities = [CourseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CourseRepository(private val courseDao: CourseDao) {
    suspend fun insert(course: CourseEntity) = courseDao.insert(course)
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<CourseEntity>> = courseDao.getCourseByTime(weekDay, startTime)
    suspend fun getAllCourses(): List<CourseEntity> = courseDao.getAllCourses()
    suspend fun clearAllCourses() = courseDao.clearAllCourses()
}

class CourseViewModel(private val repository: CourseRepository) : ViewModel() {
    private val _allCourses = MutableLiveData<List<CourseEntity>>()
    val allCourses: LiveData<List<CourseEntity>> get() = _allCourses

    fun loadAllCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            val courses = repository.getAllCourses()
            _allCourses.postValue(courses)
        }
    }

    fun insertCourse(course: CourseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(course)
            Log.d("DatabaseCheck", "Inserted Course: $course")
        }
    }

    fun clearAllCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllCourses()
        }
    }
}


suspend fun fetchWebData(url: String, cookies: String?): List<CourseEntity> {
    return withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView")
            val rows = table.select("tr")

            val dataList = mutableListOf<CourseEntity>()

            val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            val timeSlots = listOf(
                "07:10-08:00", "08:10-09:00", "09:10-10:00", "10:10-11:00", "11:10-12:00",
                "12:10-13:00", "13:10-14:00", "14:10-15:00", "15:10-16:00", "16:10-17:00",
                "17:10-18:00", "18:10-19:00", "19:10-20:00", "20:10-21:00", "21:10-22:00"
            )

            val startTimes = listOf("07:10", "08:10", "09:10", "10:10", "11:10", "12:10", "13:10", "14:10", "15:10", "16:10", "17:10", "18:10", "19:10", "20:10", "21:10")

            val endTimes = listOf("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00")


            for ((rowIndex, row) in rows.withIndex()) {
                val columns = row.select("td")

                for ((colIndex, column) in columns.withIndex()) {
                    val span = column.selectFirst("span[title]")
                    val title = span?.attr("title")?.trim() ?: ""
                    val realRowIndex = rowIndex - 1
                    val id = "$colIndex$realRowIndex"

                    val weekDay = if (colIndex in 1..7) weekDays[colIndex - 1] else "未知"
//                    val timeSlot = if (realRowIndex in timeSlots.indices) timeSlots[realRowIndex] else "未知時間"
                    val startTime = if (realRowIndex in startTimes.indices) startTimes[realRowIndex] else "None"
                    val endTime = if (realRowIndex in endTimes.indices) endTimes[realRowIndex] else "None"

                    if (title.isNotEmpty()) {
                        Log.d("WebScraper", "Extracted [$id]: $title")
                        val parsedData = parseTitle(title)

                        val courseSchedule = CourseEntity(
                            id = id,
                            courseName = parsedData["科目名稱"] ?: "未知課程",
                            teacherName = parsedData["授課教師"] ?: "未知教師",
                            location = parsedData["場地"] ?: "未知地點",
                            weekDay = weekDay,
//                            timeslot = timeSlot
                            startTime = startTime,
                            endTime = endTime
                        )
                        Log.d("WebScrapper", "fetchWebData: $courseSchedule")
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
        "授課教師" to """授課教師：(.+?)\n""",
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
fun ScheduleScreen(viewModel: CourseViewModel) {
    var cookies by remember { mutableStateOf<String?>(null) }
    val scheduleList by viewModel.allCourses.observeAsState(emptyList())
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(cookies) {
        if (cookies != null) {
            isLoading = true
            val fetchedData = fetchWebData(
                "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=", cookies!!
            )
            isLoading = false

            viewModel.clearAllCourses() // 避免重複儲存
            fetchedData.forEach { course ->
                viewModel.insertCourse(
                    CourseEntity(
                        id = course.id,
                        courseName = course.courseName,
                        teacherName = course.teacherName,
                        location = course.location,
                        weekDay = course.weekDay,
                        startTime = course.startTime,
                        endTime = course.endTime
                    )
                )
            }
            viewModel.loadAllCourses()
        }
    }


    if (cookies == null) {
        WebViewScreen("https://infosys.nttu.edu.tw/InfoLoginNew.aspx") {
            cookies = it
        }
    } else {
        Column(modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())) {
            Text(text = "Schedule", fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (scheduleList.isEmpty()) {
                Text(text = "No schedule found", fontSize = 14.sp)
            } else {
                scheduleList.forEach { course ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "ID: ${course.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(text = "課程: ${course.courseName}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "老師: ${course.teacherName}", fontSize = 14.sp)
                            Text(text = "地點: ${course.location}", fontSize = 14.sp)
                            Text(text = "星期: ${course.weekDay}", fontSize = 14.sp)
//                            Text(text = "時間: ${course.timeslot}", fontSize = 14.sp)
                            Text(text = "時間: ${course.startTime} - ${course.endTime}", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
