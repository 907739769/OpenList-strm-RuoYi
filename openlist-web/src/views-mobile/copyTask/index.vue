<template>
  <div class="mobile-page">
    <div class="page-header">
      <h2>同步任务</h2>
    </div>

    <div class="search-bar" v-if="showSearch">
      <el-input v-model="queryParams.taskName" placeholder="搜索任务名称" clearable />
      <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
      <el-button type="success" icon="Plus" @click="handleAdd">新建</el-button>
    </div>

    <div class="task-list">
      <div v-for="task in copyTasks" :key="task.taskId" class="task-card">
        <div class="task-card-header">
          <span class="task-name">{{ task.taskName }}</span>
          <el-tag :type="task.status === '0' ? 'success' : 'danger'" size="small">
            {{ task.status === '0' ? '运行中' : '已停止' }}
          </el-tag>
        </div>
        <div class="task-card-body">
          <div class="task-meta"><el-icon><Files /></el-icon> {{ task.sourcePath }}</div>
          <div class="task-meta"><el-icon><FolderOpened /></el-icon> {{ task.targetPath }}</div>
        </div>
        <div class="task-card-footer">
          <span class="task-time">创建: {{ task.createTime }}</span>
        </div>
      </div>
      <el-empty v-if="copyTasks.length === 0" description="暂无同步任务" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Files, FolderOpened } from '@element-plus/icons-vue'

const showSearch = ref(false)
const queryParams = ref({ taskName: '' })
const copyTasks = ref([
  { taskId: 1, taskName: '本地到云端', status: '0', sourcePath: '/local/data', targetPath: '/cloud/data', createTime: '2026-04-20' }
])

const handleQuery = () => { console.log('Query:', queryParams.value) }
const handleAdd = () => { console.log('Add new task') }
</script>

<style scoped lang="scss">
.mobile-page { padding: 12px; }
.page-header { margin-bottom: 12px; h2 { margin: 0; font-size: 18px; } }
.search-bar { display: flex; gap: 8px; margin-bottom: 12px; .el-input { flex: 1; } }
.task-list { display: flex; flex-direction: column; gap: 10px; }
.task-card {
  background: white; border-radius: 10px; padding: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  .task-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
  .task-name { font-size: 15px; font-weight: 500; color: #303133; }
  .task-card-body { margin-bottom: 8px; }
  .task-meta { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #909399; margin-bottom: 4px; .el-icon { font-size: 14px; } }
  .task-card-footer { border-top: 1px solid #f0f0f0; padding-top: 8px; .task-time { font-size: 11px; color: #c0c4cc; } }
}
</style>
