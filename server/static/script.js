// 1. 视图切换逻辑
function switchView(view) {
    const cardView = document.getElementById('view-card');
    const listView = document.getElementById('view-list');
    const btnCard = document.getElementById('btn-card-view');
    const btnList = document.getElementById('btn-list-view');
    if (view === 'card') {
        cardView.classList.remove('d-none'); listView.classList.add('d-none');
        btnCard.classList.add('active'); btnList.classList.remove('active');
        localStorage.setItem('viewPreference', 'card');
    } else {
        cardView.classList.add('d-none'); listView.classList.remove('d-none');
        btnCard.classList.remove('active'); btnList.classList.add('active');
        localStorage.setItem('viewPreference', 'list');
    }
}

// 2. 黑夜模式逻辑
const themeToggle = document.getElementById('themeToggle');
const htmlElement = document.documentElement;

// 初始化：检查本地存储
const savedTheme = localStorage.getItem('theme') || 'light';
htmlElement.setAttribute('data-bs-theme', savedTheme);

if (themeToggle) {
    themeToggle.addEventListener('click', () => {
        const currentTheme = htmlElement.getAttribute('data-bs-theme');
        const newTheme = currentTheme === 'light' ? 'dark' : 'light';
        htmlElement.setAttribute('data-bs-theme', newTheme);
        localStorage.setItem('theme', newTheme);
    });
}

// 3. 编辑模态框填充
function openEditModal(task) {
    const modal = new bootstrap.Modal(document.getElementById('editTaskModal'));
    document.getElementById('edit_task_id').value = task.id;
    document.getElementById('edit_title').value = task.title;
    document.getElementById('edit_category').value = task.category;
    document.getElementById('edit_content').value = task.content;
    document.getElementById('edit_priority').value = task.priority;
    if(task.start_date) document.getElementById('edit_start_date').value = task.start_date.replace(' ', 'T');
    if(task.due_date) document.getElementById('edit_due_date').value = task.due_date.replace(' ', 'T');
    document.getElementById('edit_recurrence_days').value = task.recurrence_days;
    modal.show();
}

// 4. 初始化加载与系统保活
document.addEventListener('DOMContentLoaded', () => {
    // 恢复视图偏好
    const pref = localStorage.getItem('viewPreference');
    if (pref) switchView(pref);

    // 系统保活机制：每5分钟 ping 一次服务器
    setInterval(() => {
        fetch('/ping').then(r => console.log('Session Keep-Alive:', r.status));
    }, 5 * 60 * 1000);
});

// === 批量操作逻辑 ===

// 1. 切换批量模式
function toggleBatchMode() {
    document.body.classList.toggle('batch-mode');
    const bar = document.getElementById('batch-action-bar');
    
    if (document.body.classList.contains('batch-mode')) {
        bar.classList.add('active');
    } else {
        bar.classList.remove('active');
        // 退出时清空选择
        document.querySelectorAll('.task-checkbox').forEach(cb => {
            cb.checked = false;
            updateSelectionStyle(cb);
        });
        updateCount();
    }
}

// 2. 监听复选框点击
document.addEventListener('change', function(e) {
    if (e.target.classList.contains('task-checkbox')) {
        updateSelectionStyle(e.target);
        updateCount();
    }
});

// 3. 更新卡片高亮样式
function updateSelectionStyle(checkbox) {
    // 找到最近的 .task-card 父元素（针对卡片视图）
    const card = checkbox.closest('.task-card');
    if (card) {
        if (checkbox.checked) card.classList.add('selected');
        else card.classList.remove('selected');
    }
}

// 4. 更新计数器
function updateCount() {
    const count = document.querySelectorAll('.task-checkbox:checked').length;
    document.getElementById('selected-count').textContent = count;
}

// 5. 提交批量操作 (支持 action: 'archive' 或 'export')
function submitBatchAction(actionType) {
    const checkboxes = document.querySelectorAll('.task-checkbox:checked');
    if (checkboxes.length === 0) {
        alert('请先选择至少一项');
        return;
    }

    if (actionType === 'archive' && !confirm('确定要将选中项移入归档箱吗？')) {
        return;
    }

    // 创建表单
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/batch_action'; // 统一提交到这个新路由
    
    // 添加 action_type 字段
    const typeInput = document.createElement('input');
    typeInput.type = 'hidden';
    typeInput.name = 'action_type';
    typeInput.value = actionType;
    form.appendChild(typeInput);

    // 添加选中的 ID
    checkboxes.forEach(cb => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'task_ids[]';
        input.value = cb.value;
        form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
    
    // 如果是归档操作，提交后稍等一下关闭批量模式
    if (actionType === 'archive') {
        setTimeout(toggleBatchMode, 500);
    } 
    // 如果是导出操作，页面不会刷新（因为是下载文件），所以手动关闭批量模式
    else {
        setTimeout(toggleBatchMode, 1000);
    }
}

// === 新增：全选/反选逻辑 ===
function toggleSelectAll() {
    const allCheckboxes = document.querySelectorAll('.task-checkbox');
    // 检查当前是否已经是“全选”状态
    const allChecked = Array.from(allCheckboxes).every(cb => cb.checked);
    
    allCheckboxes.forEach(cb => {
        // 如果已全选，则全不选；否则全选
        cb.checked = !allChecked;
        updateSelectionStyle(cb);
    });
    updateCount();
}

// === 修改：提交函数 (增加了 delete 的确认逻辑) ===
function submitBatchAction(actionType) {
    const checkboxes = document.querySelectorAll('.task-checkbox:checked');
    if (checkboxes.length === 0) {
        alert('请先选择至少一项');
        return;
    }

    // 针对不同动作的二次确认
    if (actionType === 'archive' && !confirm('确定要将选中项移入归档箱吗？')) {
        return;
    }
    if (actionType === 'delete' && !confirm('【高能预警】\n确定要彻底删除选中项吗？\n删除后无法恢复！')) {
        return;
    }

    // 创建表单提交
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/batch_action';
    
    const typeInput = document.createElement('input');
    typeInput.type = 'hidden';
    typeInput.name = 'action_type';
    typeInput.value = actionType;
    form.appendChild(typeInput);

    checkboxes.forEach(cb => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'task_ids[]';
        input.value = cb.value;
        form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
    
    // 界面交互延迟关闭
    if (actionType === 'archive' || actionType === 'delete') {
        setTimeout(toggleBatchMode, 500);
    } else {
        setTimeout(toggleBatchMode, 1000);
    }
}
