<template>
  <div class="mobile-page">
    <div class="page-header">
      <h2>STRM记录</h2>
    </div>

    <div class="record-list">
      <div v-for="record in records" :key="record.recordId" class="record-card">
        <div class="record-header">
          <span class="record-name">{{ record.fileName }}</span>
          <el-tag :type="getStatusType(record.status)" size="small">
            {{ getStatusText(record.status) }}
          </el-tag>
        </div>
        <div class="record-body">
          <div class="record-meta">路径: {{ record.filePath }}</div>
          <div class="record-meta">任务: {{ record.taskName }}</div>
        </div>
        <div class="record-footer">
          <span class="record-time">{{ record.updateTime }}</span>
        </div>
      </div>
      <el-empty v-if="records.length === 0" description="暂无记录" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const records = ref([
  { recordId: 1, fileName: '电影.mp4', status: '0', filePath: '/movies/1.mp4', taskName: '电影STRM', updateTime: '2026-04-20 10:30' },
  { recordId: 2, fileName: '电视剧.mp4', status: '2', filePath: '/tv/1.mp4', taskName: '剧集STRM', updateTime: '2026-04-20 11:00' }
])

const getStatusType = (status: string) => status === '0' ? 'success' : status === '3' ? 'warning' : 'danger'
const getStatusText = (status: string) => {
  const map: Record<string, string> = { '0': '成功', '1': '处理中', '2': '失败', '3': '跳过' }
  return map[status] || '未知'
}
</script>

<style scoped lang="scss">
.mobile-page { padding: 12px; }
.page-header { margin-bottom: 12px; h2 { margin: 0; font-size: 18px; } }
.record-list { display: flex; flex-direction: column; gap: 10px; }
.record-card {
  background: white; border-radius: 10px; padding: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  .record-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
  .record-name { font-size: 14px; font-weight: 500; color: #303133; }
  .record-body { margin-bottom: 8px; }
  .record-meta { font-size: 12px; color: #909399; margin-bottom: 2px; }
  .record-footer { border-top: 1px solid #f0f0f0; padding-top: 8px; .record-time { font-size: 11px; color: #c0c4cc; } }
}
</style>
