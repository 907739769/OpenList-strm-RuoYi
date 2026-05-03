<template>
  <div class="app-wrapper" :class="{ 'sidebar-collapsed': !appStore.sidebarOpened }">
    <!-- Sidebar -->
    <div class="sidebar-container">
      <div class="logo">
        <img src="/favicon.svg" alt="Logo" class="logo-img" />
        <h1 v-show="appStore.sidebarOpened" class="logo-title">OSR</h1>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="!appStore.sidebarOpened"
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

    <!-- Main Content -->
    <div class="main-container">
      <div class="navbar">
        <el-icon class="hamburger" @click="toggleSidebar">
          <Fold v-if="appStore.sidebarOpened" />
          <Expand v-else />
        </el-icon>
        <div class="right-menu">
          <el-dropdown @command="handleDropdownCommand">
            <span class="avatar-wrapper">
              <el-avatar :size="32" class="user-avatar">管</el-avatar>
              <span class="username">管理员</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><Setting /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
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
import { Fold, Expand, Odometer, Setting, SwitchButton, ArrowDown, Document, Monitor, Calendar, Picture, Coin, Promotion, Tools, Watermelon, Menu as IconMenu, VideoPlay, RefreshRight, EditPen, FolderOpened, DocumentCopy, MagicStick } from '@element-plus/icons-vue'
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
  if (iconMap[icon]) return iconMap[icon]
  if (icon.includes('fa ')) return undefined
  return undefined
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const showPasswordDialog = ref(false)

const activeMenu = computed(() => route.path)
const sidebarMenus = computed(() => userStore.routes.filter((r: any) => r.meta?.hidden !== true))

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
/* ============================================
   App Wrapper
   ============================================ */
.app-wrapper {
  display: flex;
  height: 100%;
  width: 100%;
}

/* ============================================
   Sidebar Container
   ============================================ */
.sidebar-container {
  width: var(--osr-sidebar-width);
  min-height: 100%;
  background-color: var(--osr-bg-sidebar);
  border-right: 1px solid var(--osr-border-base);
  display: flex;
  flex-direction: column;
  transition: width var(--osr-transition-slow);
  position: relative;
  z-index: 100;
}

.sidebar-collapsed .sidebar-container {
  width: var(--osr-sidebar-collapsed-width) !important;
}

/* ============================================
   Logo Area
   ============================================ */
.logo {
  display: flex;
  align-items: center;
  padding: 16px;
  background-color: var(--osr-surface);
  border-bottom: 1px solid var(--osr-border-base);
  height: var(--osr-navbar-height);
  flex-shrink: 0;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 16px;
    right: 16px;
    height: 2px;
    background: linear-gradient(90deg, var(--osr-primary), var(--osr-primary-light-5));
    opacity: 0.6;
  }

  .logo-img {
    width: 32px;
    height: 32px;
    margin-right: 10px;
    flex-shrink: 0;
  }

  .logo-title {
    color: var(--osr-text-primary);
    font-size: 18px;
    font-weight: 700;
    white-space: nowrap;
    letter-spacing: 0.5px;
  }
}

.sidebar-collapsed .logo .logo-title {
  display: none;
}

/* ============================================
   Menu
   ============================================ */
:deep(.el-menu) {
  border-right: none;
  background-color: transparent !important;
  padding: 8px;

  .el-menu-item,
  .el-sub-menu__title {
    color: var(--osr-text-secondary);
    font-weight: 500;
    border-radius: var(--osr-radius-base);
    margin-bottom: 2px;
    transition: all var(--osr-transition-fast);

    .el-icon {
      font-size: 18px;
    }

    &:hover {
      background-color: var(--osr-bg-sidebar-hover) !important;
      color: var(--osr-text-primary);
    }
  }

  .el-menu-item.is-active {
    background-color: var(--osr-bg-sidebar-active) !important;
    color: var(--osr-primary) !important;
    font-weight: 600;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      background-color: var(--osr-primary);
      border-radius: 0 2px 2px 0;
    }
  }
}

:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--osr-primary) !important;
  font-weight: 600;
}

/* ============================================
   Sidebar Scrollbar
   ============================================ */
:deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

/* ============================================
   Main Container
   ============================================ */
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ============================================
   Navbar
   ============================================ */
.navbar {
  height: var(--osr-navbar-height);
  background-color: var(--osr-surface);
  border-bottom: 1px solid var(--osr-border-light);
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: var(--osr-shadow-sm);
  flex-shrink: 0;
  z-index: 50;

  .hamburger {
    font-size: 20px;
    cursor: pointer;
    margin-right: 16px;
    color: var(--osr-text-secondary);
    transition: color var(--osr-transition-fast);
    padding: 4px;
    border-radius: var(--osr-radius-sm);

    &:hover {
      color: var(--osr-primary);
      background-color: var(--osr-primary-light-9);
    }
  }

  .right-menu {
    display: flex;
    align-items: center;
    margin-left: auto;

    .avatar-wrapper {
      display: flex;
      align-items: center;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: var(--osr-radius-base);
      transition: background-color var(--osr-transition-fast);

      &:hover {
        background-color: var(--osr-primary-light-9);
      }

      .user-avatar {
        border: 2px solid var(--osr-primary-light-8);
        background-color: var(--osr-primary);
        color: white;
        font-weight: 600;
        font-size: 13px;
      }

      .username {
        margin-left: 8px;
        font-size: 14px;
        font-weight: 500;
        color: var(--osr-text-primary);
      }

      .arrow {
        font-size: 12px;
        color: var(--osr-text-secondary);
        margin-left: 4px;
        transition: transform var(--osr-transition-fast);
      }
    }
  }
}

/* ============================================
   Main Content
   ============================================ */
.main-content {
  flex: 1;
  padding: var(--osr-content-padding);
  overflow-y: auto;
  background-color: var(--osr-bg-page);
}

/* ============================================
   Page Transition
   ============================================ */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity var(--osr-transition-base) ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
