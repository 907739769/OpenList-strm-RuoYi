<template>
  <div class="mobile-dashboard">
    <div class="welcome-card">
      <h2>欢迎回来, {{ userStore.userInfo?.userName || '管理员' }}</h2>
      <p class="subtitle">OpenList-strm-RuoYi</p>
    </div>

    <div class="stats-grid">
      <div class="stat-card" @click="$router.push('/openlist/strm-task')">
        <div class="stat-icon strm"><VideoCamera /></div>
        <div class="stat-info">
          <div class="stat-value">{{ strmCount }}</div>
          <div class="stat-label">STRM任务</div>
        </div>
      </div>
      <div class="stat-card" @click="$router.push('/openlist/copy-task')">
        <div class="stat-icon copy"><Files /></div>
        <div class="stat-info">
          <div class="stat-value">{{ copyCount }}</div>
          <div class="stat-label">同步任务</div>
        </div>
      </div>
      <div class="stat-card" @click="$router.push('/openlist/rename-task')">
        <div class="stat-icon rename"><EditPen /></div>
        <div class="stat-info">
          <div class="stat-value">{{ renameCount }}</div>
          <div class="stat-label">重命名任务</div>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <h3>快捷操作</h3>
      <div class="action-grid">
        <div class="action-item" @click="$router.push('/system/user')">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </div>
        <div class="action-item" @click="$router.push('/monitor/server')">
          <el-icon><Cpu /></el-icon>
          <span>服务监控</span>
        </div>
        <div class="action-item" @click="$router.push('/monitor/job')">
          <el-icon><Clock /></el-icon>
          <span>定时任务</span>
        </div>
        <div class="action-item" @click="$router.push('/openlist/strm-task')">
          <el-icon><VideoCamera /></el-icon>
          <span>STRM任务</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { VideoCamera, Files, EditPen, User, Cpu, Clock } from '@element-plus/icons-vue'

const userStore = useUserStore()
const strmCount = ref(0)
const copyCount = ref(0)
const renameCount = ref(0)

onMounted(() => {
  strmCount.value = 12
  copyCount.value = 8
  renameCount.value = 5
})
</script>

<style scoped lang="scss">
.mobile-dashboard { padding: 12px; }

.welcome-card {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  padding: 20px 16px;
  border-radius: 12px;
  margin-bottom: 16px;

  h2 { margin: 0 0 4px; font-size: 18px; }
  .subtitle { margin: 0; opacity: 0.9; font-size: 13px; }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;

  .stat-card {
    background: white;
    border-radius: 10px;
    padding: 14px 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    cursor: pointer;
    transition: transform 0.2s;

    &:active { transform: scale(0.97); }

    .stat-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 8px;

      .el-icon { font-size: 20px; color: white; }

      &.strm { background: linear-gradient(135deg, #f56c6c, #ff9900); }
      &.copy { background: linear-gradient(135deg, #409EFF, #67c23a); }
      &.rename { background: linear-gradient(135deg, #909399, #a0a0a0); }
    }

    .stat-info { text-align: center; }
    .stat-value { font-size: 20px; font-weight: bold; color: #303133; }
    .stat-label { font-size: 11px; color: #909399; margin-top: 2px; }
  }
}

.quick-actions {
  h3 { font-size: 15px; color: #303133; margin: 0 0 12px; }

  .action-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
    background: white;
    padding: 16px 8px;
    border-radius: 12px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);

    .action-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 10px 4px;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s;

      &:active { background: #f5f7fa; }

      .el-icon { font-size: 22px; color: #409EFF; margin-bottom: 4px; }
      span { font-size: 11px; color: #606266; }
    }
  }
}
</style>
