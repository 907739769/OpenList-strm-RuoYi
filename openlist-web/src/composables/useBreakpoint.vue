<template>
  <div class="use-breakpoint">
    <el-switch
      v-model="isMobile"
      active-text="移动端"
      inactive-text="PC端"
      @change="handleBreakpointChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const isMobile = ref(false)
const breakpoints = {
  mobile: 768,
  tablet: 1024,
  desktop: 1920
}

const handleBreakpointChange = (value: string | number | boolean) => {
  isMobile.value = value === true || value === 1 || value === 'true'
  window.dispatchEvent(new CustomEvent('breakpoint-change', { detail: { isMobile: value } }))
}

const checkBreakpoint = () => {
  isMobile.value = window.innerWidth < breakpoints.mobile
}

onMounted(() => {
  checkBreakpoint()
  window.addEventListener('resize', checkBreakpoint)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkBreakpoint)
})

defineExpose({ isMobile })
</script>

<style scoped lang="scss">
.use-breakpoint {
  padding: 8px;
}
</style>
