<template>
  <div class="app-wrapper" :class="{ 'sidebar-collapsed': !appStore.sidebarOpened }">
    <div class="sidebar-container">
      <div class="logo">
        <img src="/favicon.svg" alt="Logo" class="logo-img" />
        <h1 v-show="appStore.sidebarOpened" class="logo-title">OpenList</h1>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="!appStore.sidebarOpened"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          :unique-opened="true"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <template v-for="menu in sidebarMenus" :key="menu.path">
            <el-sub-menu v-if="menu.children?.length" :index="menu.path">
              <template #title>
                <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
                <span>{{ menu.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="sub in menu.children"
                :key="sub.path"
                :index="sub.path.startsWith('/') ? sub.path : menu.path + '/' + sub.path"
              >
                <el-icon v-if="getIconComponent(sub.meta?.icon)"><component :is="getIconComponent(sub.meta?.icon)" /></el-icon>
                <template #title>{{ sub.meta?.title }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.path">
              <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
              <template #title>{{ menu.meta?.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </div>

    <div class="main-container">
      <div class="navbar">
        <el-icon class="hamburger" @click="toggleSidebar">
          <Fold v-if="appStore.sidebarOpened" />
          <Expand v-else />
        </el-icon>
        <div class="breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right-menu">
          <el-dropdown @command="handleDropdownCommand">
            <span class="avatar-wrapper">
              <el-avatar :size="32">管</el-avatar>
              <span class="username">管理员</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <main class="main-content">
        <router-view />
      </main>

      <ChangePasswordDialog v-model:visible="showPasswordDialog" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { Fold, Expand, Odometer, Setting, Document, Monitor, Calendar, Picture, Coin, Promotion, Tools, Watermelon, Menu as IconMenu, VideoPlay, RefreshRight, EditPen, FolderOpened, DocumentCopy, MagicStick } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

// Map Font Awesome icon classes to Element Plus icons
const iconMap: Record<string, Component> = {
  'fa fa-gear': Setting,
  'fa fa-cog': Setting,
  'fa fa-bookmark-o': Document,
  'fa fa-sun-o': Picture,
  'fa fa-video-camera': Monitor,
  'fa fa-tasks': Tools,
  'fa fa-calendar': Calendar,
  'fa fa-picture-o': Picture,
  'fa fa-yen': Coin,
  'fa fa-send-o': Promotion,
  'fa fa-diamond': Watermelon,
  'fa fa-bars': IconMenu,
  'fa fa-list-ul': IconMenu,
  'fa fa-list': IconMenu,
  'fa fa-file-code-o': DocumentCopy,
  'fa fa-folder-open-o': FolderOpened,
  'fa fa-play-circle-o': VideoPlay,
  'fa fa-video-play': VideoPlay,
  'fa fa-copy': RefreshRight,
  'fa fa-edit': EditPen,
  'fa fa-magic': MagicStick,
}

function getIconComponent(icon?: string): Component | undefined {
  if (!icon) return undefined
  // Check exact match first
  if (iconMap[icon]) return iconMap[icon]
  // Check if it's a Font Awesome class (contains 'fa ')
  if (icon.includes('fa ')) return undefined
  // Otherwise try to resolve as a dynamic import
  return undefined
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const showPasswordDialog = ref(false)

const activeMenu = computed(() => route.path)

const sidebarMenus = computed(() => userStore.routes)

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleDropdownCommand = async (command: string) => {
  if (command === 'profile') {
    showPasswordDialog.value = true
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await userStore.logout()
      router.push('/login')
    } catch {
      // cancelled
    }
  }
}
</script>

<style scoped lang="scss">
.app-wrapper {
  display: flex;
  height: 100%;
  width: 100%;
}

.sidebar-container {
  width: 210px;
  min-height: 100%;
  background-color: #304156;
  transition: width 0.3s;

  .logo {
    display: flex;
    align-items: center;
    padding: 16px;
    background-color: #263445;

    .logo-img {
      width: 32px;
      height: 32px;
      margin-right: 8px;
    }

    .logo-title {
      color: #fff;
      font-size: 16px;
      font-weight: bold;
      white-space: nowrap;
    }
  }
}

.sidebar-collapsed .sidebar-container {
  width: 54px !important;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.navbar {
  height: 50px;
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  padding: 0 16px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);

  .hamburger {
    font-size: 20px;
    cursor: pointer;
    margin-right: 16px;
  }

  .breadcrumb {
    flex: 1;
  }

  .right-menu {
    display: flex;
    align-items: center;

    .avatar-wrapper {
      display: flex;
      align-items: center;
      cursor: pointer;

      .username {
        margin-left: 8px;
        font-size: 14px;
      }
    }
  }
}

.main-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
</style>
