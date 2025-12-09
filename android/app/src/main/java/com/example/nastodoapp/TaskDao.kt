package com.example.nastodoapp

import androidx.room.*

@Dao
interface TaskDao {
    // 1. 获取所有未删除的任务 (用于显示)
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 ORDER BY created_at DESC")
    suspend fun getAllTasks(): List<Task>

    // 2. 根据 ID 获取任务
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): Task?

    // 3. 插入或更新任务 (冲突时替换)
    // 用于：联网拉取到新数据时，覆盖本地
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: Task)

    // 4. 批量插入 (初始化用)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    // 5. 逻辑删除 (标记为删除，等待同步)
    @Query("UPDATE tasks SET is_deleted = 1, is_dirty = 1 WHERE id = :taskId")
    suspend fun markAsDeleted(taskId: String)

    // 6. 物理删除 (同步完成后，彻底从手机移除)
    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deletePhysically(taskId: String)

    // 7. 获取所有脏数据 (需要同步给服务器的)
    @Query("SELECT * FROM tasks WHERE is_dirty = 1")
    suspend fun getDirtyTasks(): List<Task>

    // 8. 清空所有非脏数据 (登出时清理缓存)
    @Query("DELETE FROM tasks WHERE is_dirty = 0")
    suspend fun clearSyncedTasks()

    // === 新增：离线笔记操作 ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineNote(note: OfflineNote)

    @Query("SELECT * FROM offline_notes")
    suspend fun getOfflineNotes(): List<OfflineNote>

    @Query("DELETE FROM offline_notes WHERE id = :noteId")
    suspend fun deleteOfflineNote(noteId: String)

    @Delete fun delete(task: Task)
}