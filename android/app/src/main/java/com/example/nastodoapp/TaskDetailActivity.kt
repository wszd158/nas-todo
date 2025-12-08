package com.example.nastodoapp

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.load
import coil.ImageLoader
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import java.util.concurrent.TimeUnit

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etCategory: EditText
    private lateinit var etContent: EditText
    private lateinit var spPriority: Spinner
    private lateinit var tvStartDate: TextView
    private lateinit var tvDueDate: TextView
    private lateinit var cbArchived: CheckBox
    private lateinit var rvNotes: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var btnAddNote: Button
    private lateinit var btnDownloadDocx: TextView

    private val gson = Gson()
    private val client = OkHttpClient()
    private var currentTask: Task? = null

    private var serverUrl: String = ""
    private var authHeader: String = ""
    private val priorityOptions = listOf("High", "Normal", "Low")

    private val selectedImageUris = mutableListOf<Uri>()
    private var tvSelectedCount: TextView? = null
    private var llPreviewContainer: LinearLayout? = null // 图片预览容器

    private lateinit var db: AppDatabase

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImageUris.clear()
        selectedImageUris.addAll(uris)

        tvSelectedCount?.text = "已选择 ${uris.size} 张"

        llPreviewContainer?.removeAllViews()
        uris.forEach { uri ->
            val imgView = ImageView(this)
            val params = LinearLayout.LayoutParams(200, 200)
            params.setMargins(0, 0, 16, 0)
            imgView.layoutParams = params
            imgView.scaleType = ImageView.ScaleType.CENTER_CROP
            imgView.load(uri)
            llPreviewContainer?.addView(imgView)
        }
    }

    private val customImageLoader by lazy {
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            }
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        db = AppDatabase.getDatabase(this)

        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""
        val credentials = "$user:$pass"
        val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        authHeader = "Basic $base64Credentials"

        initViews()

        val taskId = intent.getStringExtra("task_id")

        if (!taskId.isNullOrEmpty()) {
            loadTaskFromLocal(taskId)
            fetchLatestTaskData(taskId)
        } else {
            Toast.makeText(this, "无效的任务 ID", Toast.LENGTH_SHORT).show()
            finish()
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

    private fun initViews() {
        etTitle = findViewById(R.id.etDetailTitle)
        etCategory = findViewById(R.id.etDetailCategory)
        etContent = findViewById(R.id.etDetailContent)
        spPriority = findViewById(R.id.spDetailPriority)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvDueDate = findViewById(R.id.tvDueDate)
        cbArchived = findViewById(R.id.cbArchived)
        rvNotes = findViewById(R.id.rvNotes)
        btnSave = findViewById(R.id.btnSaveDetail)
        btnAddNote = findViewById(R.id.btnAddNote)
        btnDownloadDocx = findViewById(R.id.btnDownloadDocx)

        spPriority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorityOptions)
        tvStartDate.setOnClickListener { showDateTimePicker(tvStartDate) }
        tvDueDate.setOnClickListener { showDateTimePicker(tvDueDate) }

        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.isNestedScrollingEnabled = false

        btnSave.setOnClickListener { saveTaskChanges() }
        btnAddNote.setOnClickListener { showAddNoteDialog() }

        btnDownloadDocx.setOnClickListener {
            currentTask?.let { task -> downloadDocx(task) }
        }
    }

    private fun downloadDocx(task: Task) {
        val downloadUrl = "$serverUrl/download_task/${task.id}"

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("${task.title}.docx")
            .setDescription("正在下载任务文档...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NasTodo_${task.title}.docx")
            .addRequestHeader("Authorization", authHeader)

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(this, "已开始下载，请查看通知栏", Toast.LENGTH_SHORT).show()
    }

    private fun showDateTimePicker(textView: TextView) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val formatted = String.format("%04d-%02d-%02d %02d:%02d", year, month + 1, day, hour, minute)
                textView.text = formatted
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadTaskFromLocal(taskId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val localTask = db.taskDao().getTaskById(taskId)
            if (localTask != null) {
                currentTask = localTask
                withContext(Dispatchers.Main) { updateUI() }
            }
        }
    }

    private fun fetchLatestTaskData(taskId: String) {
        val url = "$serverUrl/api/tasks/$taskId"
        val request = Request.Builder().url(url).header("Authorization", authHeader).build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val apiResponse = gson.fromJson(bodyStr, SingleTaskResponse::class.java)
                        if (apiResponse.data != null) {
                            db.taskDao().insertOrUpdate(apiResponse.data)
                            currentTask = apiResponse.data
                            withContext(Dispatchers.Main) { updateUI() }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun updateUI() {
        currentTask?.let { task ->
            if (!etTitle.hasFocus()) etTitle.setText(task.title)
            if (!etCategory.hasFocus()) etCategory.setText(task.category)
            if (!etContent.hasFocus()) etContent.setText(task.content)
            val prioIndex = priorityOptions.indexOf(task.priority ?: "Normal")
            if (prioIndex >= 0) spPriority.setSelection(prioIndex)
            tvStartDate.text = task.start_date ?: ""
            tvDueDate.text = task.due_date ?: ""
            cbArchived.isChecked = task.is_archived == true
            rvNotes.adapter = NoteAdapter(task.notes ?: emptyList())
        }
    }

    private fun saveTaskChanges() {
        val task = currentTask ?: return
        val url = "$serverUrl/api/tasks/${task.id}"
        val selectedPriority = spPriority.selectedItem.toString()
        val startStr = tvStartDate.text.toString().ifEmpty { null }
        val dueStr = tvDueDate.text.toString().ifEmpty { null }

        val jsonMap = mutableMapOf(
            "title" to etTitle.text.toString(),
            "category" to etCategory.text.toString(),
            "content" to etContent.text.toString(),
            "priority" to selectedPriority,
            "start_date" to startStr,
            "due_date" to dueStr,
            "is_archived" to cbArchived.isChecked
        )
        val updatedTask = task.copy(
            title = etTitle.text.toString(), category = etCategory.text.toString(), content = etContent.text.toString(),
            priority = selectedPriority, start_date = startStr, due_date = dueStr, is_archived = cbArchived.isChecked, is_dirty = true
        )
        val requestBody = gson.toJson(jsonMap).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).put(requestBody).header("Authorization", authHeader).build()
        btnSave.isEnabled = false
        btnSave.text = "保存中..."
        CoroutineScope(Dispatchers.IO).launch {
            db.taskDao().insertOrUpdate(updatedTask)
            try {
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        btnSave.isEnabled = true
                        btnSave.text = "保存修改"
                        if (response.isSuccessful) {
                            Toast.makeText(this@TaskDetailActivity, "保存成功", Toast.LENGTH_SHORT).show()
                            db.taskDao().insertOrUpdate(updatedTask.copy(is_dirty = false))
                        } else Toast.makeText(this@TaskDetailActivity, "已离线保存", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSave.isEnabled = true
                    btnSave.text = "保存修改"
                    Toast.makeText(this@TaskDetailActivity, "已离线保存", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // === 修复点：这里是完整的 showAddNoteDialog 方法 ===
    private fun showAddNoteDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_note)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val etNoteContent = dialog.findViewById<EditText>(R.id.etNoteContent)
        val btnSelectImages = dialog.findViewById<Button>(R.id.btnSelectImages)
        tvSelectedCount = dialog.findViewById<TextView>(R.id.tvSelectedCount)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitNote)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        llPreviewContainer = dialog.findViewById(R.id.llImagePreviewContainer)
        selectedImageUris.clear()
        llPreviewContainer?.removeAllViews()
        tvSelectedCount?.text = "未选择"

        btnSelectImages.setOnClickListener { pickImagesLauncher.launch("image/*") }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val content = etNoteContent.text.toString()
            uploadNote(content, selectedImageUris, dialog)
        }
        dialog.show()
    }

    private fun uploadNote(content: String, uris: List<Uri>, dialog: Dialog) {
        val task = currentTask ?: return
        val url = "$serverUrl/api/notes"
        val newNoteId = UUID.randomUUID().toString()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("task_id", task.id)
        builder.addFormDataPart("content", content)
        builder.addFormDataPart("id", newNoteId)
        uris.forEach { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val fileName = "upload_${System.currentTimeMillis()}.jpg"
                    val requestBody = RequestBody.create("image/jpeg".toMediaType(), bytes)
                    builder.addFormDataPart("images", fileName, requestBody)
                }
            } catch (e: Exception) { Log.e("Upload", "读取图片失败: $uri", e) }
        }
        val request = Request.Builder().url(url).post(builder.build()).header("Authorization", authHeader).build()
        dialog.findViewById<Button>(R.id.btnSubmitNote).isEnabled = false
        dialog.findViewById<Button>(R.id.btnSubmitNote).text = "处理中..."
        CoroutineScope(Dispatchers.IO).launch {
            val newNote = Note(newNoteId, content, emptyList())
            val currentNotes = task.notes?.toMutableList() ?: mutableListOf()
            currentNotes.add(newNote)
            val updatedTask = task.copy(notes = currentNotes)
            db.taskDao().insertOrUpdate(updatedTask)
            withContext(Dispatchers.Main) { currentTask = updatedTask; updateUI() }
            try {
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@TaskDetailActivity, "笔记已同步", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            fetchLatestTaskData(task.id)
                        } else throw java.io.IOException("Error")
                    }
                }
            } catch (e: Exception) {
                val uriStrings = uris.map { it.toString() }
                val offlineNote = OfflineNote(id = newNoteId, task_id = task.id, content = content, image_uris = Gson().toJson(uriStrings))
                db.taskDao().insertOfflineNote(offlineNote)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TaskDetailActivity, "已离线保存", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun deleteNote(noteId: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                val url = "$serverUrl/api/notes/$noteId"
                val request = Request.Builder().url(url).delete().header("Authorization", authHeader).build()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        client.newCall(request).execute().use { response ->
                            withContext(Dispatchers.Main) {
                                if (response.isSuccessful) {
                                    Toast.makeText(this@TaskDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                                    currentTask?.let { fetchLatestTaskData(it.id) }
                                } else Toast.makeText(this@TaskDetailActivity, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {}
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun showEditNoteDialog(note: Note) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_note)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "编辑笔记"

        val etNoteContent = dialog.findViewById<EditText>(R.id.etNoteContent)
        val btnSelectImages = dialog.findViewById<Button>(R.id.btnSelectImages)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitNote)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        tvSelectedCount = dialog.findViewById(R.id.tvSelectedCount)
        llPreviewContainer = dialog.findViewById(R.id.llImagePreviewContainer)

        // === 新增：已有图片相关控件 ===
        val tvExistingLabel = dialog.findViewById<TextView>(R.id.tvExistingLabel)
        val svExistingImages = dialog.findViewById<HorizontalScrollView>(R.id.svExistingImages)
        val llExistingContainer = dialog.findViewById<LinearLayout>(R.id.llExistingImagesContainer)

        // 记录用户想要删除的文件名
        val deletedImageNames = mutableListOf<String>()

        etNoteContent.setText(note.content)

        // --- 初始化已有图片 ---
        if (!note.images_info.isNullOrEmpty()) {
            tvExistingLabel.visibility = View.VISIBLE
            svExistingImages.visibility = View.VISIBLE
            llExistingContainer.removeAllViews()

            note.images_info.forEach { imgInfo ->
                // 创建一个 FrameLayout 包裹图片和删除图标
                val frame = FrameLayout(this)
                val frameParams = LinearLayout.LayoutParams(220, 220)
                frameParams.setMargins(0, 0, 16, 0)
                frame.layoutParams = frameParams

                // 图片
                val imgView = ImageView(this)
                val imgParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imgView.layoutParams = imgParams
                imgView.scaleType = ImageView.ScaleType.CENTER_CROP

                // 加载逻辑 (同 Adapter)
                var finalUrl = imgInfo.original ?: ""
                if (finalUrl.isNotEmpty() && !finalUrl.startsWith("http")) {
                    val base = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                    val path = if (finalUrl.startsWith("/")) finalUrl else "/$finalUrl"
                    finalUrl = "$base$path"
                }

                // 优先加载缩略图 Base64，没有则加载网络图
                if (!imgInfo.thumb_base64.isNullOrEmpty()) {
                    try {
                        val pureBase64 = if (imgInfo.thumb_base64.contains(",")) imgInfo.thumb_base64.substringAfter(",") else imgInfo.thumb_base64
                        imgView.load(Base64.decode(pureBase64, Base64.DEFAULT))
                    } catch (e: Exception) { imgView.load(finalUrl, imageLoader = customImageLoader) { addHeader("Authorization", authHeader) } }
                } else {
                    imgView.load(finalUrl, imageLoader = customImageLoader) { addHeader("Authorization", authHeader) }
                }

                // 删除蒙层/图标 (红色 X)
                val deleteOverlay = ImageView(this)
                val overlayParams = FrameLayout.LayoutParams(60, 60)
                overlayParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                overlayParams.setMargins(8, 8, 8, 8)
                deleteOverlay.layoutParams = overlayParams
                deleteOverlay.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                deleteOverlay.background = getDrawable(R.drawable.ic_delete_red) // 复用之前的红色背景或者简单设个背景
                deleteOverlay.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)

                frame.addView(imgView)
                frame.addView(deleteOverlay)

                // 点击事件：标记为删除
                frame.setOnClickListener {
                    if (frame.alpha == 1.0f) {
                        // 变成半透明 + 标记删除
                        frame.alpha = 0.3f
                        if (imgInfo.filename != null) deletedImageNames.add(imgInfo.filename)
                        Toast.makeText(this, "已标记删除 (提交后生效)", Toast.LENGTH_SHORT).show()
                    } else {
                        // 恢复 + 取消删除
                        frame.alpha = 1.0f
                        if (imgInfo.filename != null) deletedImageNames.remove(imgInfo.filename)
                    }
                }

                llExistingContainer.addView(frame)
            }
        } else {
            tvExistingLabel.visibility = View.GONE
            svExistingImages.visibility = View.GONE
        }

        // --- 初始化新图片选择 ---
        selectedImageUris.clear()
        llPreviewContainer?.removeAllViews()
        tvSelectedCount?.text = "新增图片 (可选)"

        btnSelectImages.setOnClickListener { pickImagesLauncher.launch("image/*") }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val newContent = etNoteContent.text.toString()
            // 传入新图片 + 要删除的旧图片列表
            updateNoteWithImages(note.id, newContent, selectedImageUris, deletedImageNames, dialog)
        }
        dialog.show()
    }

    private fun updateNoteWithImages(
        noteId: String,
        content: String,
        newUris: List<Uri>,
        deletedImageNames: List<String>,
        dialog: Dialog
    ) {
        val url = "$serverUrl/api/notes/$noteId"
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("content", content)

        // 1. 处理新增图片
        newUris.forEach { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val fileName = "new_img_${System.currentTimeMillis()}.jpg"
                    val requestBody = RequestBody.create("image/jpeg".toMediaType(), bytes)
                    builder.addFormDataPart("new_images", fileName, requestBody)
                }
            } catch (e: Exception) {}
        }

        // 2. 处理删除图片 (后端接收 list)
        deletedImageNames.forEach { filename ->
            builder.addFormDataPart("delete_images", filename)
        }

        val request = Request.Builder().url(url).put(builder.build()).header("Authorization", authHeader).build()

        dialog.findViewById<Button>(R.id.btnSubmitNote).isEnabled = false
        dialog.findViewById<Button>(R.id.btnSubmitNote).text = "保存中..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@TaskDetailActivity, "修改成功", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            fetchLatestTaskData(currentTask?.id ?: "") // 刷新数据
                        } else {
                            Toast.makeText(this@TaskDetailActivity, "修改失败: ${response.code}", Toast.LENGTH_SHORT).show()
                            dialog.findViewById<Button>(R.id.btnSubmitNote).isEnabled = true
                            dialog.findViewById<Button>(R.id.btnSubmitNote).text = "提交"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TaskDetailActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    dialog.findViewById<Button>(R.id.btnSubmitNote).isEnabled = true
                    dialog.findViewById<Button>(R.id.btnSubmitNote).text = "提交"
                }
            }
        }
    }

    inner class NoteAdapter(private val notes: List<Note>) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {
        inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvContent: TextView = view.findViewById(R.id.tvNoteContent)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteNote)
            val btnEdit: ImageButton = view.findViewById(R.id.btnEditNote)
            val llImagesContainer: LinearLayout = view.findViewById(R.id.llImagesContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notes[position]
            holder.tvContent.text = note.content
            holder.btnDelete.setOnClickListener { deleteNote(note.id) }
            holder.btnEdit.setOnClickListener { showEditNoteDialog(note) }

            holder.llImagesContainer.removeAllViews()
            note.images_info?.forEach { imgInfo ->
                val imageView = ImageView(holder.itemView.context)
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500)
                params.setMargins(0, 16, 0, 0)
                imageView.layoutParams = params
                imageView.adjustViewBounds = true
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                var loadSuccess = false
                if (!imgInfo.thumb_base64.isNullOrEmpty()) {
                    try {
                        val pureBase64 = if (imgInfo.thumb_base64.contains(",")) imgInfo.thumb_base64.substringAfter(",") else imgInfo.thumb_base64
                        imageView.load(Base64.decode(pureBase64, Base64.DEFAULT)) { crossfade(true); listener(onSuccess = { _, _ -> imageView.requestLayout() }) }
                        loadSuccess = true
                    } catch (e: Exception) {}
                }

                if (!loadSuccess && !imgInfo.original.isNullOrEmpty()) {
                    var finalUrl = imgInfo.original
                    if (!finalUrl.startsWith("http")) {
                        val base = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                        val path = if (finalUrl.startsWith("/")) finalUrl else "/$finalUrl"
                        finalUrl = "$base$path"
                    }
                    imageView.load(finalUrl, imageLoader = customImageLoader) {
                        addHeader("Authorization", authHeader)
                        crossfade(true)
                        listener(onSuccess = { _, _ -> imageView.requestLayout() })
                    }
                }

                imageView.setOnClickListener {
                    if (!imgInfo.original.isNullOrEmpty()) showFullImage(imgInfo.original)
                }
                holder.llImagesContainer.addView(imageView)
            }
        }
        override fun getItemCount() = notes.size
    }

    private fun showFullImage(urlPart: String) {
        if (urlPart.isBlank()) return
        try {
            var finalUrl = urlPart
            if (!finalUrl.startsWith("http")) {
                val base = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                val path = if (urlPart.startsWith("/")) urlPart else "/$urlPart"
                finalUrl = "$base$path"
            }

            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val container = FrameLayout(this)
            container.setBackgroundColor(0xFF000000.toInt())
            val imgView = ImageView(this)
            imgView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            imgView.scaleType = ImageView.ScaleType.FIT_CENTER

            val progressBar = ProgressBar(this)
            val lpProgress = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpProgress.gravity = android.view.Gravity.CENTER
            progressBar.layoutParams = lpProgress

            val btnSave = ImageButton(this)
            val lpBtn = FrameLayout.LayoutParams(120, 120)
            lpBtn.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            lpBtn.setMargins(0, 0, 48, 48)
            btnSave.layoutParams = lpBtn
            btnSave.setImageResource(android.R.drawable.ic_menu_save)
            btnSave.background = null
            btnSave.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)

            val downloadUrl = finalUrl
            btnSave.setOnClickListener { saveImageToGallery(downloadUrl) }

            container.addView(imgView)
            container.addView(progressBar)
            container.addView(btnSave)
            dialog.setContentView(container)
            dialog.show()

            imgView.load(finalUrl, imageLoader = customImageLoader) {
                addHeader("Authorization", authHeader)
                listener(
                    onStart = { progressBar.visibility = View.VISIBLE },
                    onSuccess = { _, _ -> progressBar.visibility = View.GONE },
                    onError = { _, res ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@TaskDetailActivity, "加载失败: ${res.throwable.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            imgView.setOnClickListener { dialog.dismiss() }
            container.setOnClickListener { dialog.dismiss() }
        } catch (e: Exception) {
            Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery(imageUrl: String) {
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show()
        val request = Request.Builder().url(imageUrl).header("Authorization", authHeader).build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@TaskDetailActivity, "下载失败", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                val bytes = response.body!!.bytes()
                val filename = "NasTodo_${System.currentTimeMillis()}.jpg"
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NasTodo")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = applicationContext.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream -> outputStream.write(bytes) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(this@TaskDetailActivity, "已保存到相册", Toast.LENGTH_LONG).show() }
                } ?: run { withContext(Dispatchers.Main) { Toast.makeText(this@TaskDetailActivity, "保存失败", Toast.LENGTH_SHORT).show() } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@TaskDetailActivity, "保存出错: ${e.message}", Toast.LENGTH_SHORT).show() } }
        }
    }
}