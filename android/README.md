# 📝 NasTodo (Android Client)

> 一个基于 **离线优先 (Offline-First)** 架构的个人待办事项管理客户端，专为自托管 NAS 用户设计。

![Version](https://img.shields.io/badge/version-1.1.5-blue)
![Language](https://img.shields.io/badge/language-Kotlin-purple)
![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Repository-green)
![Status](https://img.shields.io/badge/status-Stable-success)

## 📖 简介

**NasTodo Android** 是 NasTodo 生态系统的移动端入口。与传统的在线待办清单不同，NasTodo 采用 **离线优先** 策略：数据存储在手机本地 (Room Database)，保证了在无网络、弱网环境下依然可以秒开应用、创建任务和编辑笔记。当网络恢复时，应用会自动将本地变更同步至你的私有 NAS 服务器。

## ✨ 核心特性

### ⚡️ 离线优先 & 自动同步
* **断网可用**：无论是在飞机上还是电梯里，随时增删改查。
* **智能同步**：采用 UUID 全局唯一标识，支持双向数据流。联网自动推送本地“脏数据”(Dirty Data) 并拉取最新变更。
* **本地缓存**：使用 Room 数据库作为单一数据源，加载速度极快。

### 📝 任务与笔记管理
* **任务管理**：支持优先级、截止日期、分类标签、归档管理。
* **富笔记**：任务下可挂载多条笔记，支持**图文混排**。
* **图片支持**：支持上传图片附件，支持编辑笔记时追加/删除图片，支持查看原图及保存到相册。
* **文档导出**：一键将任务及其所有笔记导出为 `.docx` 文档，格式完美还原。

### 🎨 现代化 UI/UX
* **全新蓝色主题**：基于 Material Design 的清爽蓝色视觉风格。
* **深色模式适配**：完美适配 Android Dark Mode，顶部沉浸式设计，深夜使用不刺眼。
* **多主题切换**：内置亮色、暗色、护眼（羊皮纸黄）三种主题。
* **便捷交互**：登录页历史记录 FAB 悬浮按钮、列表页实时排序（按到期日/完成时间等）。

## 📱 截图展示

| 登录页面 | 任务列表 (亮色) | 任务列表 (深色) | 任务详情 & 笔记 |
|:---:|:---:|:---:|:---:|
| | | | |
| *历史记录快捷登录* | *离线数据秒开* | *沉浸式暗色模式* | *图文笔记编辑* |

## 🛠 技术栈

* **语言**: [Kotlin](https://kotlinlang.org/)
* **架构**: Offline-First (Local Database as Single Source of Truth)
* **网络**: [OkHttp](https://square.github.io/okhttp/) + [Retrofit](https://square.github.io/retrofit/)
* **数据库**: [Jetpack Room](https://developer.android.com/training/data-storage/room) (SQLite)
* **图片加载**: [Coil](https://coil-kt.github.io/coil/)
* **数据解析**: [Gson](https://github.com/google/gson)
* **UI 组件**: Material Components, ConstraintLayout, CoordinatorLayout

## 🚀 快速开始 (Quick Start)

### 1. 准备工作
NasTodo 是一个**自托管 (Self-Hosted)** 应用。在开始使用安卓端之前，请确保：
* 您已经在 NAS 或服务器上部署了 NasTodo 后端服务。
* 您拥有后端服务的访问地址 (例如 `http://192.168.1.5:5000` 或 `https://todo.yourdomain.com`)。
* 确保服务端数据库已迁移至支持 UUID 的最新版本。

### 2. 下载与安装
1.  前往本项目的 **[Releases 页面]** (此处建议放您的 Github Release 链接) 下载最新版本的 `.apk` 安装包 (例如 `NasTodo_v1.1.1.apk`)。
2.  将 APK 文件发送到您的 Android 手机。
3.  点击文件进行安装。
    * *注意：如果是首次安装，系统可能会提示“禁止安装未知来源应用”，请在设置中允许该权限。*

### 3. 登录配置
1.  打开 App，进入登录页面。
2.  **服务器地址**：填写您的后端完整地址。
    * *局域网模式*：如 `http://192.168.1.5:5000` (需手机连接同一 WiFi)。
    * *公网模式*：如 `https://mydomain.com` (需配置反向代理或公网 IP)。
3.  **账号密码**：输入您的 NasTodo 用户名和密码。
4.  **历史记录**：登录成功后，您可以点击右下角的悬浮按钮快速切换或填入历史账号。

### 4. 常见操作指南
* **离线使用**：您可以在飞行模式或无网络环境下随意创建任务、编辑笔记。数据会安全地保存在本地数据库中。
* **数据同步**：
    * **上行 (Push)**：联网后，App 会在启动或刷新时自动将离线产生的“脏数据”推送至服务器。
    * **下行 (Pull)**：下拉任务列表，即可从服务器拉取最新的任务状态。
* **文档导出**：进入任务详情页，点击右上角的“下载 DOCX”，即可将当前任务及所有图文笔记导出为文档。

## 📋 更新日志 (v1.1.1)

* **[Fix]** 修复了离线模式下排序功能失效的问题，现支持内存级实时排序。
* **[Fix]** 修复了部分机型添加任务闪退的 Bug (补全 `completed_at` 字段)。
* **[Feat]** 新增 DOCX 文档下载功能。
* **[Feat]** 笔记编辑功能增强：支持追加图片、删除已有图片。
* **[UI]** 品牌色更新为 Material Blue，优化深色模式下的菜单可见性。

## 🤝 贡献

欢迎提交 Issue 或 Pull Request 来帮助改进 NasTodo！

## 📄 许可证

[MIT License](LICENSE)
