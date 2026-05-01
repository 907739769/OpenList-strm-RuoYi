<template>
  <div class="mobile-layout" :class="{ 'menu-open': menuOpen }">
    <!-- Overlay -->
    <div v-if="menuOpen" class="overlay" @click="menuOpen = false" />

    <!-- Drawer menu -->
    <div class="drawer-menu" :class="{ 'drawer-open': menuOpen }">
      <div class="drawer-header">
        <img src="/favicon.svg" alt="Logo" class="drawer-logo" />
        <span class="drawer-title">OpenList</span>
        <el-icon class="close-btn" @click="menuOpen = false"><Close /></el-icon>
      </div>
      <el-scrollbar>
        <el-menu default-active="/dashboard" router>
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-menu-item index="/system/user">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/system/role">
            <el-icon><UserFilled /></el-icon>
            <template #title>角色管理</template>
          </el-menu-item>
          <el-menu-item index="/openlist/strm-task">
            <el-icon><VideoCamera /></el-icon>
            <template #title>STRM任务</template>
          </el-menu-item>
          <el-menu-item index="/openlist/copy-task">
            <el-icon><Files /></el-icon>
            <template #title>同步任务</template>
          </el-menu-item>
          <el-menu-item index="/openlist/rename-task">
            <el-icon><EditPen /></el-icon>
            <template #title>重命名任务</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </div>

    <!-- Main content -->
    <div class="mobile-main">
      <!-- Top bar -->
      <div class="mobile-navbar">
        <el-icon class="hamburger" @click="menuOpen = !menuOpen"><Fold /></el-icon>
        <span class="page-title">{{ pageTitle }}</span>
      </div>

      <!-- Content -->
      <div class="mobile-content">
        <router-view />
      </div>

      <!-- Bottom TabBar -->
      <div class="mobile-tabbar">
        <div class="tabbar-item" @click="$router.push('/dashboard')">
          <el-icon :class="{ active: $route.path === '/dashboard' }"><Odometer /></el-icon>
          <span>首页</span>
        </div>
        <div class="tabbar-item" @click="$router.push('/openlist/strm-task')">
          <el-icon :class="{ active: $route.path.includes('strm') }"><VideoCamera /></el-icon>
          <span>STRM</span>
        </div>
        <div class="tabbar-item" @click="$router.push('/openlist/copy-task')">
          <el-icon :class="{ active: $route.path.includes('copy') }"><Files /></el-icon>
          <span>同步</span>
        </div>
        <div class="tabbar-item" @click="$router.push('/openlist/rename-task')">
          <el-icon :class="{ active: $route.path.includes('rename') }"><EditPen /></el-icon>
          <span>重命名</span>
        </div>
        <div class="tabbar-item" @click="$router.push('/system/user')">
          <el-icon :class="{ active: $route.path.includes('system') }"><User /></el-icon>
          <span>我的</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Fold, Close, Odometer, User, UserFilled, VideoCamera, Files, EditPen } from '@element-plus/icons-vue'

const route = useRoute()
const menuOpen = ref(false)

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    '/dashboard': '首页',
    '/system/user': '用户管理',
    '/system/role': '角色管理',
    '/openlist/strm-task': 'STRM任务',
    '/openlist/copy-task': '同步任务',
    '/openlist/rename-task': '重命名任务'
  }
  for (const key of Object.keys(titles)) {
    if (route.path.startsWith(key)) return titles[key]
  }
  return 'OpenList'
})
</script>

<style scoped lang="scss">
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

.overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 998;
}

.drawer-menu {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 280px;
  background: #fff;
  z-index: 999;
  transform: translateX(-100%);
  transition: transform 0.3s;

  &.drawer-open {
    transform: translateX(0);
  }

  .drawer-header {
    display: flex;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #eee;

    .drawer-logo {
      width: 32px;
      height: 32px;
      margin-right: 8px;
    }

    .drawer-title {
      flex: 1;
      font-size: 18px;
      font-weight: bold;
    }

    .close-btn {
      font-size: 24px;
      cursor: pointer;
    }
  }
}

.mobile-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.mobile-navbar {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  padding: 0 16px;

  .hamburger {
    font-size: 24px;
    cursor: pointer;
    margin-right: 12px;
  }

  .page-title {
    font-size: 16px;
    font-weight: 500;
  }
}

.mobile-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  padding-bottom: 60px;
}

.mobile-tabbar {
  height: 56px;
  background: #fff;
  border-top: 1px solid #eee;
  display: flex;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 997;

  .tabbar-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    font-size: 11px;
    color: #909399;

    .el-icon {
      font-size: 20px;
      margin-bottom: 2px;

      &.active {
        color: #409EFF;
      }
    }
  }
}
</style>
