<template>
  <div class="mobile-page">
    <div class="page-header">
      <h2>STRM任务</h2>
    </div>

    <div class="search-bar">
      <el-input
        v-model="queryParams.taskName"
        placeholder="搜索任务名称"
        clearable
        prefix-icon="Search"
        @keyup.enter="handleQuery"
      />
      <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
      <el-button type="success" icon="Plus" @click="handleAdd">新建</el-button>
    </div>

    <div class="task-list">
      <div
        v-for="task in strmTasks"
        :key="task.taskId"
        class="task-card"
        @click="$router.push(`/openlist/strm-record?taskId=${task.taskId}`)"
      >
        <div class="task-card-header">
          <span class="task-name">{{ task.taskName }}</span>
          <el-tag :type="task.status === '0' ? 'success' : 'danger'" size="small">
            {{ task.status === '0' ? '运行中' : '已停止' }}
          </el-tag>
        </div>
        <div class="task-card-body">
          <div class="task-meta">
            <el-icon><Folder /></el-icon> {{ task.homePath }}
          </div>
          <div class="task-meta">
            <el-icon><VideoCamera /></el-icon> {{ task.outputPath }}
          </div>
        </div>
        <div class="task-card-footer">
          <span class="task-time">创建: {{ task.createTime }}</span>
        </div>
      </div>
      <el-empty v-if="strmTasks.length === 0" description="暂无STRM任务" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Folder, VideoCamera } from '@element-plus/icons-vue'

const queryParams = ref({ taskName: '' })
const strmTasks = ref([
  { taskId: 1, taskName: '电影STRM', status: '0', homePath: '/movies', outputPath: '/strm/movies', createTime: '2026-04-20' },
  { taskId: 2, taskName: '剧集STRM', status: '1', homePath: '/tv', outputPath: '/strm/tv', createTime: '2026-04-21' }
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
  cursor: pointer;
  transition: transform 0.2s;
  &:active { transform: scale(0.98); }
  .task-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
  .task-name { font-size: 15px; font-weight: 500; color: #303133; }
  .task-card-body { margin-bottom: 8px; }
  .task-meta { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #909399; margin-bottom: 4px; .el-icon { font-size: 14px; } }
  .task-card-footer { border-top: 1px solid #f0f0f0; padding-top: 8px; .task-time { font-size: 11px; color: #c0c4cc; } }
}
</style>
