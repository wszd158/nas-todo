package com.example.nastodoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.ProgressDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.UUID

// 注意：数据模型 Task, Note, ImageInfo 已在 DataModels.kt 中定义，此处无需重复

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: View
    private lateinit var chipGroup: ChipGroup

    private val client = OkHttpClient()
    private val gson = Gson()

    private val tasks = mutableListOf<Task>()
    private var allTasksBackup = listOf<Task>()

    private lateinit var adapter: TaskAdapter

    private var serverUrl: String = ""
    private var authHeader: String = ""

    private var currentSortMode = "created_desc"
    private var isShowArchived = false
    private var currentSearchQuery = ""
    private var selectedCategory: String? = null


    // 数据库实例
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化数据库
        db = AppDatabase.getDatabase(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        val user = prefs.getString("username", "") ?: ""

        val intentPass = intent.getStringExtra("password")
        val savedPass = prefs.getString("password", "")
        val pass = if (!intentPass.isNullOrEmpty()) intentPass else savedPass

        if (serverUrl.isEmpty() || user.isEmpty() || pass.isNullOrEmpty()) {
            performLogout()
            return
        }

        val credentials = "$user:$pass"
        val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        authHeader = "Basic $base64Credentials"

        recyclerView = findViewById(R.id.recyclerView)
        fabAdd = findViewById(R.id.fabAdd)
        chipGroup = findViewById(R.id.chipGroupCategories)

        adapter = TaskAdapter(tasks,
            onCheckedChange = { task, isChecked -> updateTaskStatus(task, isChecked) },
            onItemClick = { task ->
                val intent = Intent(this, TaskDetailActivity::class.java)
                intent.putExtra("task_id", task.id)
                startActivity(intent)
            },
            onLongClick = { task -> deleteTask(task) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 2. 启动时加载
        loadTasks()

        fabAdd.setOnClickListener { showAddTaskDialog() }
    }

    override fun onResume() {
        super.onResume()
        if (serverUrl.isNotEmpty()) {
            loadTasks()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query ?: ""; loadTasks(); return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) { currentSearchQuery = ""; loadTasks() }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sort_created_desc -> { currentSortMode = "created_desc"; loadTasks(); true }
            R.id.sort_due_date -> { currentSortMode = "due_date"; loadTasks(); true }
            R.id.sort_completed -> { currentSortMode = "completed_desc"; loadTasks(); true }
            R.id.action_toggle_archive -> {
                isShowArchived = !isShowArchived
                item.isChecked = isShowArchived
                supportActionBar?.title = if (isShowArchived) "归档箱" else "我的待办"
                loadTasks()
                true
            }
            R.id.action_refresh -> { loadTasks(); true }
            R.id.action_theme -> { showThemeChooser(); true }
            R.id.action_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeChooser() {
        val themes = arrayOf("默认亮色", "夜间深色", "护眼模式 (黄)")
        AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setItems(themes) { _, which ->
                getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
                    .edit().putInt("theme_mode", which).apply()
                recreate()
            }
            .show()
    }

    private fun performLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出吗？\n\n为了保护隐私，退出后将清空本地所有数据。")
            .setPositiveButton("退出") { _, _ ->
                // 显示进度条，体验更好
                val loading = ProgressDialog(this).apply {
                    setMessage("正在清理数据...")
                    setCancelable(false)
                    show()
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. 【核心】清空数据库（这就是你想要的优化）
                        db.clearAllTables()

                        // 2. 清空账号密码缓存
                        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()

                        // 3. 跳转
                        withContext(Dispatchers.Main) {
                            loading.dismiss()
                            val intent = Intent(this@MainActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loading.dismiss()
                            Toast.makeText(this@MainActivity, "清理失败", Toast.LENGTH_SHORT).show()
                            // 即使清理失败也强制跳转，防止死循环
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. 先显示本地数据 (秒开)
            val localList = db.taskDao().getAllTasks()
            updateListUI(localList)

            // 2. 【关键】先把本地没传上去的离线任务推给服务器
            syncLocalChanges()

            // 3. 最后拉取服务器最新数据
            fetchTasksFromNetwork()
        }
    }

    // === 新增：同步本地修改到服务器 (Push) ===
    private suspend fun syncLocalChanges() {
        val dao = db.taskDao()

        // --- 1. 同步离线任务 (新增/修改) ---
        val dirtyTasks = dao.getDirtyTasks()
        if (dirtyTasks.isNotEmpty()) {
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "正在同步 ${dirtyTasks.size} 个任务...", Toast.LENGTH_SHORT).show() }
        }

        dirtyTasks.forEach { task ->
            // 如果是标记为删除的任务，走删除逻辑
            if (task.is_deleted) {
                try {
                    val url = "$serverUrl/api/tasks/${task.id}"
                    val request = Request.Builder().url(url).delete().header("Authorization", authHeader).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful || response.code == 404) {
                        dao.deletePhysically(task.id) // 服务器删成功了，本地也彻底删掉
                    }
                    response.close()
                } catch (e: Exception) { }
            } else {
                // 正常的 dirty 任务 (新增/修改)
                try {
                    val url = "$serverUrl/api/tasks"
                    val json = gson.toJson(task)
                    val requestBody = json.toRequestBody("application/json".toMediaType())

                    // 先试 POST
                    var request = Request.Builder().url(url).post(requestBody).header("Authorization", authHeader).build()
                    var response = client.newCall(request).execute()

                    // 如果 409 冲突，改试 PUT
                    if (response.code == 409) {
                        response.close()
                        val updateUrl = "$serverUrl/api/tasks/${task.id}"
                        request = Request.Builder().url(updateUrl).put(requestBody).header("Authorization", authHeader).build()
                        response = client.newCall(request).execute()
                    }

                    if (response.isSuccessful) {
                        dao.insertOrUpdate(task.copy(is_dirty = false))
                    }
                    response.close()
                } catch (e: Exception) { }
            }
        }

        // --- 2. 同步离线笔记 (新增) ---
        val offlineNotes = dao.getOfflineNotes()
        if (offlineNotes.isNotEmpty()) {
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "正在同步 ${offlineNotes.size} 条笔记...", Toast.LENGTH_SHORT).show() }
        }

        offlineNotes.forEach { note ->
            try {
                val url = "$serverUrl/api/notes"
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                builder.addFormDataPart("task_id", note.task_id)
                builder.addFormDataPart("content", note.content)
                builder.addFormDataPart("id", note.id)

                // 尝试恢复图片 (这是一个简单的尝试，如果 URI 失效可能传不上去)
                try {
                    val uriList = gson.fromJson(note.image_uris, List::class.java) as? List<String>
                    uriList?.forEach { uriStr ->
                        val uri = android.net.Uri.parse(uriStr)
                        val inputStream = contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            val filename = "upload_${System.currentTimeMillis()}.jpg"
                            val requestBody = RequestBody.create("image/jpeg".toMediaType(), bytes)
                            builder.addFormDataPart("images", filename, requestBody)
                        }
                    }
                } catch (e: Exception) {
                    // 图片读取失败 (可能文件被删了)，只传文字
                }

                val request = Request.Builder().url(url).post(builder.build()).header("Authorization", authHeader).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    dao.deleteOfflineNote(note.id) // 同步成功，从暂存区移除
                }
                response.close()
            } catch (e: Exception) { }
        }
    }

    private suspend fun fetchTasksFromNetwork() {
        var url = "$serverUrl/api/tasks?sort_by=$currentSortMode"
        if (isShowArchived) url += "&show_archived=true"
        if (currentSearchQuery.isNotEmpty()) url += "&q=$currentSearchQuery"

        val request = Request.Builder().url(url).header("Authorization", authHeader).build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                    if (apiResponse.data != null) {
                        db.taskDao().insertAll(apiResponse.data)
                        val updatedList = db.taskDao().getAllTasks()
                        updateListUI(updatedList)
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                if (tasks.isEmpty()) Toast.makeText(this@MainActivity, "离线模式: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun updateListUI(rawList: List<Task>) {
        withContext(Dispatchers.Main) {
            allTasksBackup = rawList
            refreshCategoryChips()
            filterTaskList()
        }
    }

    private fun refreshCategoryChips() {
        val categories = allTasksBackup.map { it.category ?: "其他" }.distinct().sorted()
        chipGroup.removeAllViews()
        val allChip = layoutInflater.inflate(R.layout.layout_chip_choice, chipGroup, false) as Chip
        allChip.text = "全部"
        allChip.id = View.generateViewId()
        allChip.isChecked = (selectedCategory == null)
        allChip.setOnClickListener { selectedCategory = null; filterTaskList() }
        chipGroup.addView(allChip)
        categories.forEach { cat ->
            val chip = layoutInflater.inflate(R.layout.layout_chip_choice, chipGroup, false) as Chip
            chip.text = cat
            chip.id = View.generateViewId()
            if (cat == selectedCategory) chip.isChecked = true
            chip.setOnClickListener { selectedCategory = cat; filterTaskList() }
            chipGroup.addView(chip)
        }
    }

    private fun filterTaskList() {
        tasks.clear()

        var filtered = allTasksBackup

        // 过滤逻辑
        filtered = if (isShowArchived) filtered.filter { it.is_archived == true }
        else filtered.filter { it.is_archived != true }

        if (selectedCategory != null) {
            filtered = filtered.filter { (it.category ?: "其他") == selectedCategory }
        }

        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter { it.title.contains(currentSearchQuery, true) }
        }

        // 排序逻辑 (修复了类型推断问题)
        filtered = when (currentSortMode) {
            "created_asc" -> filtered.sortedBy { it.created_at }
            "created_desc" -> filtered.sortedByDescending { it.created_at }

            // 显式声明 Lambda 参数类型 (t1: Task, t2: Task)
            "due_date" -> filtered.sortedWith { t1: Task, t2: Task ->
                val d1 = t1.due_date
                val d2 = t2.due_date
                if (d1 == null && d2 == null) 0
                else if (d1 == null) 1
                else if (d2 == null) -1
                else d1.compareTo(d2)
            }

            // 现在 completed_at 存在了，这里就不会报错了
            // 逻辑：刚完成的(时间晚)排前面；没完成的(null)排后面
            "completed_desc" -> filtered.sortedWith { t1: Task, t2: Task ->
                val c1 = t1.completed_at
                val c2 = t2.completed_at
                if (c1 == null && c2 == null) 0
                else if (c1 == null) 1  // 未完成/无时间的沉底
                else if (c2 == null) -1
                else c2.compareTo(c1)   // 倒序：时间晚的(大)在前面
            }

            else -> filtered.sortedByDescending { it.created_at }
        }

        tasks.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun addTask(title: String, category: String) {
        val newTaskId = UUID.randomUUID().toString()
        val url = "$serverUrl/api/tasks"
        val json = """{"id": "$newTaskId", "title": "$title", "category": "$category", "priority": "Normal"}"""
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(requestBody).header("Authorization", authHeader).build()

        CoroutineScope(Dispatchers.IO).launch {
            // === 修复点：使用具名参数创建 Task，补全 completed_at 和 notes ===
            val localTask = Task(
                id = newTaskId,
                title = title,
                category = category,
                content = "",
                priority = "Normal",
                start_date = null,
                due_date = null,
                is_archived = false,
                completed = false,
                completed_at = null,   // 【新增】完成时间
                created_at = null,     // 创建时间（暂留空，或者是当前时间）
                updated_at = null,
                notes = emptyList(),   // 【新增】笔记列表初始化为空
                is_dirty = true        // 标记为脏数据，等待同步
            )

            db.taskDao().insertOrUpdate(localTask)
            loadTasks() // 刷新界面

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "添加成功", Toast.LENGTH_SHORT).show()
                            // 发送成功，标记为已同步
                            db.taskDao().insertOrUpdate(localTask.copy(is_dirty = false))
                            loadTasks()
                        }
                    } else {
                        showError("添加失败 (已存本地)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "已离线保存 (待同步)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteTask(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("删除任务")
            .setMessage("确定要删除 \"${task.title}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                val url = "$serverUrl/api/tasks/${task.id}"
                val request = Request.Builder().url(url).delete().header("Authorization", authHeader).build()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                db.taskDao().deletePhysically(task.id)
                                loadTasks()
                                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    } catch (e: Exception) {
                        showError("离线删除暂未支持，请联网")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateTaskStatus(task: Task, isCompleted: Boolean) {
        val url = "$serverUrl/api/tasks/${task.id}"
        val json = """{"completed": $isCompleted}"""
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).put(requestBody).header("Authorization", authHeader).build()

        CoroutineScope(Dispatchers.IO).launch {
            val updatedTask = task.copy(completed = isCompleted)
            db.taskDao().insertOrUpdate(updatedTask)
            try {
                client.newCall(request).execute()
            } catch (e: Exception) {
            }
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)

        val inputTitle = dialogView.findViewById<EditText>(R.id.etTaskTitle)
        val inputCategory = dialogView.findViewById<EditText>(R.id.etTaskCategory)

        // 获取我们在 XML 里新加的按钮
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmAddTask)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTask)

        // 创建对话框，但不使用系统自带的按钮
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 设置点击事件
        btnConfirm.setOnClickListener {
            val title = inputTitle.text.toString()
            val cat = inputCategory.text.toString().ifEmpty { "其他" }
            if (title.isNotEmpty()) {
                addTask(title, cat)
                dialog.dismiss() // 手动关闭
            } else {
                Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyAppTheme() {
        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
        when (prefs.getInt("theme_mode", 0)) {
            0 -> setTheme(R.style.Theme_NasTodoApp)
            1 -> setTheme(R.style.Theme_NasTodoApp_Dark)
            2 -> setTheme(R.style.Theme_NasTodoApp_Care)
        }
    }

    inner class TaskAdapter(
        private val taskList: List<Task>,
        private val onCheckedChange: (Task, Boolean) -> Unit,
        private val onItemClick: (Task) -> Unit,
        private val onLongClick: (Task) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

        inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
            val cbCompleted: CheckBox = itemView.findViewById(R.id.cbCompleted)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = taskList[position]
            holder.tvTitle.text = task.title
            holder.tvCategory.text = task.category ?: "其他"
            holder.tvPriority.text = task.priority ?: "Normal"
            holder.cbCompleted.setOnCheckedChangeListener(null)
            holder.cbCompleted.isChecked = task.completed
            holder.cbCompleted.setOnCheckedChangeListener { _, isChecked -> onCheckedChange(task, isChecked) }
            holder.itemView.setOnClickListener { onItemClick(task) }
            holder.itemView.setOnLongClickListener { onLongClick(task); true }
        }

        override fun getItemCount() = taskList.size
    }
}