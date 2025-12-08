package com.example.nastodoapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.Serializable

// === 1. 工具类：类型转换器 (让数据库能存 List) ===
class Converters {
    private val gson = Gson()

    // 处理 List<Note> <-> String (JSON)
    @TypeConverter
    fun fromNoteList(value: List<Note>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toNoteList(value: String): List<Note>? {
        val listType = object : TypeToken<List<Note>>() {}.type
        return try {
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 处理 List<ImageInfo> <-> String (JSON)
    @TypeConverter
    fun fromImageList(value: List<ImageInfo>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toImageList(value: String): List<ImageInfo>? {
        val listType = object : TypeToken<List<ImageInfo>>() {}.type
        return try {
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// === 2. 数据模型 (打上 Room 标签) ===

data class ImageInfo(
    @SerializedName("original_url") val original: String? = null,
    @SerializedName("thumb_base64") val thumb_base64: String? = null,
    val filename: String? = null
) : Serializable

// 虽然 Note 也是数据，但为了简化同步逻辑，我们暂时把它作为 Task 的一部分存成 JSON 字符串
// 如果后续需要独立查询笔记，再拆分表。目前这样最符合 API 结构。
data class Note(
    val id: String,
    val content: String,
    val images_info: List<ImageInfo>?
) : Serializable

@Entity(tableName = "tasks") // <--- 标记为数据库表
@TypeConverters(Converters::class) // <--- 启用转换器
data class Task(
    @PrimaryKey val id: String, // <--- 标记为主键 (UUID)

    val title: String,
    val category: String?,
    val content: String?,
    val priority: String?,
    val start_date: String?,
    val due_date: String?,
    val is_archived: Boolean?,
    val completed: Boolean,
    val completed_at: String?,
    val created_at: String?,
    val updated_at: String?,

    // 关键：Room 会调用 Converters 把这个 List 转成 String 存进数据库
    val notes: List<Note>?,

    // === 离线同步核心字段 (新增) ===
    // 0: 已同步, 1: 待同步(新增/修改)
    val is_dirty: Boolean = false,
    // 0: 正常, 1: 待删除
    val is_deleted: Boolean = false
) : Serializable

// API 响应结构不变
data class ApiResponse(
    val status: String,
    val data: List<Task>?
)

data class SingleTaskResponse(
    val status: String,
    val data: Task?
)

@Entity(tableName = "offline_notes")
data class OfflineNote(
    @PrimaryKey val id: String, // 笔记 UUID
    val task_id: String,        // 所属任务 UUID
    val content: String,        // 文字内容
    // 图片暂时存 URI 字符串，同步时再尝试读取
    // 注意：复杂的离线图片同步比较困难，我们先打通文字和基础图片路径
    val image_uris: String = ""
) : Serializable