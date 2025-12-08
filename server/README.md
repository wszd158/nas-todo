# 📝 NAS To-Do (Private Cloud Task Manager)

![Version](https://img.shields.io/badge/version-1.8.0-blue) ![Docker](https://img.shields.io/badge/deployment-docker-2496ED) ![Python](https://img.shields.io/badge/backend-Flask-green)

**NAS To-Do** 是一款专为 NAS 用户打造的、**自托管**的轻量级待办事项与项目日志管理系统。

它不仅仅是一个 To-Do List，更是一个支持**富文本笔记**、**图片快照**和**全格式导出**的过程记录工具。配合 Android 客户端，它支持完全的离线操作与双向同步。

---

## ✨ 核心特性 (Features)

### 🎯 任务管理 (Task Management)
- **多维度管理**：支持优先级（High/Normal/Low）、分类、开始/截止时间。
- **循环任务**：支持按天循环的任务自动生成。
- **归档系统**：独特的“归档箱”机制，保持工作台整洁，同时保留历史记录。
- **高级筛选**：支持按名称、分类、时间、归档状态进行组合搜索与排序。

### 📝 笔记与媒体 (Notes & Media)
- **过程记录**：每个任务下可添加多条笔记，记录执行过程中的灵感与复盘。
- **图片支持**：支持多图上传，后端自动生成**缩略图**。
- **极速同步**：列表页采用 **Base64 缩略图内嵌**技术，移动端浏览“秒开”，告别加载裂图。

### 💾 数据主权与导出 (Data Sovereignty)
- **Word 导出**：支持将单个任务或批量任务导出为 `.docx` 文档，**图片自动嵌入文档**，实现真正的离线备份。
- **批量操作**：支持批量归档、批量删除、批量打包下载。
- **完全私有**：所有数据（数据库、图片）均存储在您的私有 NAS 上。

### ⚡ 生产级架构 (Architecture)
- **UUID 主键**：底层数据库采用 **UUID** 替代自增 ID，完美支持多端离线创建与无冲突同步。
- **Waitress 服务器**：内置生产级 WSGI 服务器，配置 **32 线程**并发，无惧多图请求。
- **RESTful API**：提供完善的 API 接口，支持第三方应用或移动端接入。

---

## 🛠️ 技术栈 (Tech Stack)

- **Backend**: Python 3.9+, Flask, SQLAlchemy (SQLite)
- **Server**: Waitress (Production WSGI)
- **Image Processing**: Pillow (Auto-thumbnail generation)
- **Document**: python-docx (Word export)
- **Frontend**: Bootstrap 5, Jinja2, Vanilla JS
- **Deployment**: Docker & Docker Compose

---

## 🚀 快速开始 (Quick Start)

### 1. 前置要求
- 一台安装了 Docker 的 NAS 或服务器。
- Git (用于拉取代码)。

### 2. 部署步骤

将代码克隆到本地，或创建项目文件夹：

```bash
git clone https://your-gitea-url/nas-todo.git
cd nas-todo
````

确保目录结构如下：

```text
nas-todo/
├── app.py
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
├── static/
└── templates/
```

### 3\. 启动容器

使用 Docker Compose 一键构建并启动：

```bash
# 构建镜像并启动 (-d 后台运行)
docker-compose up -d --build
```

启动后，访问 `http://NAS_IP:5000` 即可开始使用。
*首次启动会自动初始化 UUID 结构的数据库。*

-----

## ⚙️ 配置说明 (Configuration)

### docker-compose.yml 参考

```yaml
version: '3'
services:
  nas-todo:
    build: .
    container_name: nas-todo
    restart: always
    ports:
      - "5000:5000"  # 如有冲突可修改左侧端口，如 "15050:5000"
    volumes:
      - ./data/todo.db:/data/todo.db     # 数据库持久化
      - ./data/uploads:/data/uploads     # 图片持久化
      - /etc/localtime:/etc/localtime:ro # 同步宿主机时间
      - /etc/timezone:/etc/timezone:ro
```

-----

## 🔌 API 文档 (For Developers)

本项目提供 RESTful API，支持 Basic Auth 认证。

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| `GET` | `/api/tasks` | 获取任务列表（支持 `show_archived`, `sort_by`, `q` 参数） |
| `POST` | `/api/tasks` | 创建任务（支持客户端生成 UUID 实现离线创建） |
| `PUT` | `/api/tasks/<uuid>` | 修改任务（全字段更新） |
| `POST` | `/api/notes` | 添加笔记（支持 `multipart/form-data` 图片上传） |

*详细 API 定义请参考源码 `app.py`。*

-----

## 📅 版本历史 (Changelog)

  - **v1.7.5** (Current): 数据库重构为 **UUID** 主键，支持离线同步架构；增加 `updated_at` 字段用于冲突检测。
  - **v1.7.2**: 引入 **Waitress** 服务器与 **Base64** 缩略图方案，解决移动端并发断流问题。
  - **v1.7.1**: 新增 Word 导出与归档系统。
  - **v1.6.0**: 初始发布，基础 CRUD 功能。

-----

## 📄 License

MIT License. Feel free to fork and modify for your own personal use.

