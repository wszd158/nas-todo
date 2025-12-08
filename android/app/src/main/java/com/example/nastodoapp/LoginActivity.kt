package com.example.nastodoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 历史记录数据模型
data class LoginHistory(val url: String, val username: String)

class LoginActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 读取保存的数据
        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "")
        val savedUser = prefs.getString("username", "")
        val savedPass = prefs.getString("password", "")
        val isRemembered = prefs.getBoolean("remember_me", false)

        // 2. 自动登录逻辑
        if (isRemembered && !savedPass.isNullOrEmpty() && !savedUrl.isNullOrEmpty()) {
            goToMainActivity(savedPass!!)
            return
        }

        setContentView(R.layout.activity_login)

        val etUrl = findViewById<EditText>(R.id.etLoginUrl)
        val etUser = findViewById<EditText>(R.id.etLoginUsername)
        val etPass = findViewById<EditText>(R.id.etLoginPassword)
        val cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnHistory = findViewById<ImageButton>(R.id.btnHistory)

        // 3. 回填数据
        if (!savedUrl.isNullOrEmpty()) etUrl.setText(savedUrl)
        if (!savedUser.isNullOrEmpty()) etUser.setText(savedUser)
        cbRememberMe.isChecked = isRemembered

        // 4. 历史记录按钮点击事件
        btnHistory.setOnClickListener {
            showHistoryDialog(etUrl, etUser)
        }

        // 5. 登录按钮点击事件
        btnLogin.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val rememberMe = cbRememberMe.isChecked

            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 去除末尾斜杠
            val cleanUrl = if (url.endsWith("/")) url.dropLast(1) else url

            // UI 反馈
            btnLogin.isEnabled = false
            btnLogin.text = "验证中..."

            verifyAndLogin(cleanUrl, user, pass, rememberMe, prefs, btnLogin)
        }
    }

    private fun showHistoryDialog(etUrl: EditText, etUser: EditText) {
        val prefs = getSharedPreferences("NasTodoPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("login_history", "[]")

        // 解析历史记录列表
        val type = object : TypeToken<List<LoginHistory>>() {}.type
        val historyList: List<LoginHistory> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (historyList.isEmpty()) {
            Toast.makeText(this, "暂无历史记录", Toast.LENGTH_SHORT).show()
            return
        }

        // 构造显示列表
        val items = historyList.map { "${it.username} @ ${it.url}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("切换历史账号")
            .setItems(items) { _, which ->
                val selected = historyList[which]
                etUrl.setText(selected.url)
                etUser.setText(selected.username)
                Toast.makeText(this, "已填入", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清空历史") { _, _ ->
                prefs.edit().remove("login_history").apply()
                Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun verifyAndLogin(
        url: String,
        user: String,
        pass: String,
        rememberMe: Boolean,
        prefs: android.content.SharedPreferences,
        btnLogin: Button
    ) {
        val credentials = "$user:$pass"
        val base64Credentials = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)

        var finalUrl = url
        if (!finalUrl.startsWith("http")) finalUrl = "http://$finalUrl"

        val request = Request.Builder()
            .url("$finalUrl/api/tasks?limit=1")
            .header("Authorization", "Basic $base64Credentials")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            // === 验证成功 ===

                            // 1. 保存到历史记录
                            saveToHistory(finalUrl, user, prefs)

                            // 2. 保存当前配置
                            val editor = prefs.edit()
                            editor.putString("server_url", finalUrl)
                            editor.putString("username", user)
                            editor.putBoolean("remember_me", rememberMe)
                            if (rememberMe) {
                                editor.putString("password", pass)
                            } else {
                                editor.remove("password")
                            }
                            editor.apply()

                            Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                            goToMainActivity(pass)
                        } else {
                            // === 验证失败 ===
                            btnLogin.isEnabled = true
                            btnLogin.text = "登 录"
                            val msg = when (response.code) {
                                401 -> "用户名或密码错误"
                                404 -> "服务器地址错误 (接口未找到)"
                                else -> "登录失败: ${response.code}"
                            }
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "登 录"
                    Toast.makeText(this@LoginActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToHistory(url: String, user: String, prefs: android.content.SharedPreferences) {
        val json = prefs.getString("login_history", "[]")
        val type = object : TypeToken<MutableList<LoginHistory>>() {}.type
        val list: MutableList<LoginHistory> = try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

        // 避免重复：先删旧的，再加新的到头部
        list.removeAll { it.url == url && it.username == user }
        list.add(0, LoginHistory(url, user))

        // 只保留最近 5 条
        if (list.size > 5) {
            list.removeAt(list.size - 1)
        }

        prefs.edit().putString("login_history", gson.toJson(list)).apply()
    }

    private fun goToMainActivity(password: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("password", password)
        startActivity(intent)
        finish()
    }
}