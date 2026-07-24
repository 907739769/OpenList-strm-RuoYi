<template>
  <el-sub-menu v-if="menu.children?.length" :index="menu.name || menu.path">
    <template #title>
      <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
      <span>{{ menu.meta?.title }}</span>
    </template>
    <SidebarMenuItem v-for="child in menu.children" :key="child.path" :menu="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.path">
    <el-icon v-if="getIconComponent(menu.meta?.icon)"><component :is="getIconComponent(menu.meta?.icon)" /></el-icon>
    <template #title>{{ menu.meta?.title }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import type { MenuRoute } from '@/stores/user'
import { getIconComponent } from '@/composables/useMenuIcon'

defineProps<{ menu: MenuRoute }>()
</script>
