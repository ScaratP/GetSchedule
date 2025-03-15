package com.example.myapplication3

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.myapplication3.CourseDetailCard as CourseDetailCard
import java.time.LocalTime
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.runBlocking


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
    val startTime: LocalTime, // 改為 LocalTime
    val endTime: LocalTime   // 改為 LocalTime
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

class Converters {
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.toString() // 例如 "09:10"
    }

    @TypeConverter
    fun toLocalTime(timeString: String?): LocalTime? {
        return timeString?.let { LocalTime.parse(it) }
    }
}

@Database(entities = [CourseEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class) // 註冊轉換器
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
                ).build()
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

    private val _selectedCourses = MutableLiveData<List<CourseEntity>?>(null)
    val selectedCourses: LiveData<List<CourseEntity>?> get() = _selectedCourses

    fun loadAllCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            val courses = repository.getAllCourses()
            Log.d("CourseViewModel", "Loaded courses: $courses") // 添加日誌檢查
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
            _allCourses.postValue(emptyList()) // 確保 LiveData 更新為空
            Log.d("CourseViewModel", "Cleared all courses")
        }
    }

    fun selectCourses(courses: List<CourseEntity>?) {
        _selectedCourses.value = courses
    }

    fun updateCourseLocation(courseId: String, newLocation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentCourses = _allCourses.value?.toMutableList() ?: mutableListOf()
            val courseToUpdate = currentCourses.find { it.id == courseId }
            courseToUpdate?.let {
                val updatedCourse = it.copy(location = newLocation)
                repository.insert(updatedCourse)
                val updatedCourses = currentCourses.map { course ->
                    if (course.id == courseId) updatedCourse else course
                }
                _allCourses.postValue(updatedCourses)
                _selectedCourses.value?.let { selected ->
                    val updatedSelected = selected.map { course ->
                        if (course.id == courseId) updatedCourse else course
                    }
                    _selectedCourses.postValue(updatedSelected)
                }
            }
        }
    }

    // 新增方法：檢查資料庫是否有數據
    fun hasDataInDatabase(): Boolean {
        return runBlocking(Dispatchers.IO) {
            repository.getAllCourses().isNotEmpty()
        }
    }
}

suspend fun fetchWebData(url: String, cookies: String?): List<CourseEntity> {
    return withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView")
            val rows = table.select("tr")
            val tempDataList = mutableListOf<CourseEntity>()

            val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            val startTimes = listOf(
                "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
                "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00"
            ).map { LocalTime.parse(it) }
            val endTimes = listOf(
                "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00",
                "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00"
            ).map { LocalTime.parse(it) }

            // 收集階段：使用原始時間，不提前調整
            val contentRows = rows.drop(1)
            for ((rowIndex, row) in contentRows.withIndex()) {
                val columns = row.select("td")
                for ((colIndex, column) in columns.withIndex()) {
                    val span = column.selectFirst("span[title]")
                    val title = span?.attr("title")?.trim() ?: ""

                    if (rowIndex >= startTimes.size) continue

                    val weekDay = if (colIndex in 1..7) weekDays[colIndex - 1] else "未知"
                    val startTime = startTimes[rowIndex] // 未調整的原始時間
                    val endTime = endTimes[rowIndex]

                    if (title.isNotEmpty()) {
                        val parsedData = parseTitle(title)
                        val courseSchedule = CourseEntity(
                            id = "$colIndex$rowIndex",
                            courseName = parsedData["科目名稱"] ?: "未知課程",
                            teacherName = parsedData["授課教師"] ?: "未知教師",
                            location = parsedData["場地"] ?: "其它",
                            weekDay = weekDay,
                            startTime = startTime,
                            endTime = endTime
                        )
                        tempDataList.add(courseSchedule)
                    }
                }
            }

            // 合併連續時間並在最後調整開始時間
            val mergedDataList = mutableListOf<CourseEntity>()
            tempDataList.groupBy { it.courseName to it.weekDay }.forEach { (key, courses) ->
                val sortedCourses = courses.sortedBy { it.startTime }
                var currentStartTime: LocalTime? = null
                var currentEndTime: LocalTime? = null
                var currentId: String = sortedCourses.first().id

                for (course in sortedCourses) {
                    if (currentStartTime == null) {
                        currentStartTime = course.startTime
                        currentEndTime = course.endTime
                    } else if (currentEndTime == course.startTime) {
                        // 連續時間，更新結束時間
                        currentEndTime = course.endTime
                    } else {
                        // 不連續，存入前一個範圍並調整開始時間
                        mergedDataList.add(
                            CourseEntity(
                                id = currentId,
                                courseName = key.first,
                                teacherName = courses.first().teacherName,
                                location = courses.first().location,
                                weekDay = key.second,
                                startTime = currentStartTime.plusMinutes(10), // 調整開始時間
                                endTime = currentEndTime!!
                            )
                        )
                        currentStartTime = course.startTime
                        currentEndTime = course.endTime
                        currentId = course.id
                    }
                }
                // 存入最後一個範圍並調整開始時間
                if (currentStartTime != null && currentEndTime != null) {
                    mergedDataList.add(
                        CourseEntity(
                            id = currentId,
                            courseName = key.first,
                            teacherName = courses.first().teacherName,
                            location = courses.first().location,
                            weekDay = key.second,
                            startTime = currentStartTime.plusMinutes(10), // 調整開始時間
                            endTime = currentEndTime
                        )
                    )
                }
            }

            Log.d("fetchWebData", "Final merged data: $mergedDataList")
            return@withContext mergedDataList
        } catch (e: Exception) {
            Log.e("fetchWebData", "Error fetching data", e)
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
                        view.scrollTo(0, -100)
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
    val selectedCourses by viewModel.selectedCourses.observeAsState(null)
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val onCourseSelected = remember(viewModel) {
        { courses: List<CourseEntity>? -> viewModel.selectCourses(courses) }
    }

    // 初始載入已有數據
    LaunchedEffect(Unit) {
        viewModel.loadAllCourses()
    }

    // 當 cookies 更新時執行刷新
    LaunchedEffect(cookies) {
        if (cookies != null) {
            isLoading = true
            val fetchedData = fetchWebData(
                "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=",
                cookies!!
            )
            if (fetchedData.isNotEmpty()) {
                // 只有成功獲取新數據時才清除舊數據
                viewModel.clearAllCourses()
                fetchedData.forEach { viewModel.insertCourse(it) }
                viewModel.loadAllCourses()
            } else {
                Log.d("ScheduleScreen", "No new data fetched, keeping existing data")
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "課程表",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        cookies = null // 觸發重新登入
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                } else {
                    Text("刷新")
                }
            }
        }

        if (cookies == null) {
            WebViewScreen("https://infosys.nttu.edu.tw/InfoLoginNew.aspx") { newCookies ->
                cookies = newCookies
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ScheduleTable(
                        scheduleList = scheduleList,
                        onCourseSelected = onCourseSelected
                    )
                }
                selectedCourses?.let { courses ->
                    Spacer(modifier = Modifier.height(16.dp))
                    CourseDetailCard(
                        courses = courses,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun CourseDetailCard(
    courses: List<CourseEntity>,
    viewModel: CourseViewModel,
    modifier: Modifier = Modifier // 添加 modifier 參數
) {
    if (courses.isEmpty()) return

    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var newLocation by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                if (courses.any { it.location == "其它" }) {
                    showEditDialog = true
                } else {
                    Toast.makeText(context, "只有地點為「其它」的課程可以編輯", Toast.LENGTH_SHORT).show()
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "課程: ${courses.first().courseName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "老師: ${courses.map { it.teacherName }.distinct().joinToString(", ")}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "地點: ${courses.map { it.location }.distinct().joinToString(", ")}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "時間安排:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            courses.forEach { course ->
                Text(
                    text = "${course.weekDay} ${course.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} - ${course.endTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("編輯地點") },
            text = {
                TextField(
                    value = newLocation,
                    onValueChange = { newLocation = it },
                    label = { Text("輸入新地點") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLocation.isNotBlank()) {
                            courses.filter { it.location == "其它" }.forEach { course ->
                                viewModel.updateCourseLocation(course.id, newLocation)
                            }
                            showEditDialog = false
                            newLocation = ""
                        }
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                Button(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}





@Composable
fun ScheduleTable(
    scheduleList: List<CourseEntity>,
    onCourseSelected: (List<CourseEntity>?) -> Unit
) {
    val timeSlots = listOf(
        "07:00" to "08:00", "08:00" to "09:00", "09:00" to "10:00", "10:00" to "11:00",
        "11:00" to "12:00", "12:00" to "13:00", "13:00" to "14:00", "14:00" to "15:00",
        "15:00" to "16:00", "16:00" to "17:00", "17:00" to "18:00", "18:00" to "19:00",
        "19:00" to "20:00", "20:00" to "21:00", "21:00" to "22:00"
    ).map { LocalTime.parse(it.first) to LocalTime.parse(it.second) }

    val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val activeCols = scheduleList.map { weekDays.indexOf(it.weekDay) + 1 }.filter { it > 0 }.distinct().sorted()
    val activeRows = (0 until timeSlots.size).filter { rowIndex ->
        scheduleList.any { course ->
            val courseStart = course.startTime
            val courseEnd = course.endTime
            val slotStart = timeSlots[rowIndex].first
            val slotEnd = timeSlots[rowIndex].second
            courseStart < slotEnd && courseEnd > slotStart
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Schedule",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                if (scheduleList.isEmpty()) {
                    Text(
                        text = "No schedule found",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Log.d("ScheduleScreen", "Rendering schedule with ${scheduleList.size} courses")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(
                                    text = "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                activeCols.forEach { colIndex ->
                                    Text(
                                        text = weekDays[colIndex - 1],
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            activeRows.forEach { realRowIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (activeRows.indexOf(realRowIndex) % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                ) {
                                    Text(
                                        text = "第${realRowIndex}節",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f).padding(8.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    activeCols.forEach { colIndex ->
                                        val slotStart = timeSlots[realRowIndex].first
                                        val slotEnd = timeSlots[realRowIndex].second
                                        val weekDay = weekDays[colIndex - 1]
                                        val courseAtSlot = scheduleList.firstOrNull { course ->
                                            val matches = course.weekDay == weekDay &&
                                                    course.startTime < slotEnd && course.endTime > slotStart
                                            Log.d("ScheduleScreen", "col=$colIndex, row=$realRowIndex, course=${course.courseName}, matches=$matches")
                                            matches
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(60.dp)
                                                .padding(2.dp)
                                                .background(
                                                    if (courseAtSlot != null) MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable(enabled = courseAtSlot != null) {
                                                    courseAtSlot?.let {
                                                        val relatedCourses = scheduleList.filter { course ->
                                                            course.courseName == it.courseName
                                                        }
                                                        onCourseSelected(relatedCourses)
                                                    }
                                                }
                                                .border(
                                                    width = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = courseAtSlot?.courseName ?: "",
                                                fontSize = 12.sp,
                                                color = if (courseAtSlot != null) MaterialTheme.colorScheme.onPrimaryContainer else Color.Transparent,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



suspend fun fetchNewData(viewModel: CourseViewModel, cookies: String) {
    val fetchedData = fetchWebData(
        "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=",
        cookies
    )
    if (fetchedData.isNotEmpty()) {
        viewModel.clearAllCourses()
        fetchedData.forEach { viewModel.insertCourse(it) }
        viewModel.loadAllCourses()
    }
}