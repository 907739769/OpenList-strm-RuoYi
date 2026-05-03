<template>
  <DesktopLayout v-if="!isMobileDevice" />
  <MobileLayout v-else />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'
import DesktopLayout from '@/layouts/DesktopLayout.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'

const appStore = useAppStore()

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
