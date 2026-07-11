<template>
  <div v-if="isHiddenRoute">
    <router-view />
  </div>
  <DesktopLayout v-else-if="!isMobileDevice">
    <router-view />
  </DesktopLayout>
  <MobileLayout v-else>
    <router-view />
  </MobileLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useRegisterSW } from 'virtual:pwa-register/vue'
import { useAppStore } from '@/stores/app'
import DesktopLayout from '@/layouts/DesktopLayout.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'

// PWA 更新检测：新版本就绪时弹出确认框，用户点击刷新后激活新 SW 并重载页面
const { updateServiceWorker } = useRegisterSW({
  onNeedRefresh() {
    ElMessageBox.confirm(
      '系统已发布新版本，是否立即刷新页面以应用更新？',
      '版本更新提示',
      {
        confirmButtonText: '立即刷新',
        cancelButtonText: '稍后',
        type: 'info',
        closeOnClickModal: false,
      }
    ).then(() => {
      updateServiceWorker(true)
    }).catch(() => {
      // 用户选择稍后，不做任何操作
    })
  },
  onOfflineReady() {
    console.log('App ready to work offline')
  }
})

const route = useRoute()
const appStore = useAppStore()

const isHiddenRoute = computed(() => {
  return route.meta?.hidden === true
})

const isMobileDevice = computed(() => appStore.device === 'mobile')

const checkDevice = () => {
  const mobile = window.innerWidth < 768
  appStore.toggleDevice(mobile ? 'mobile' : 'desktop')
  if (mobile) {
    appStore.closeSidebar()
  }
}

onMounted(() => {
  checkDevice()
  window.addEventListener('resize', checkDevice)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkDevice)
})
</script>
