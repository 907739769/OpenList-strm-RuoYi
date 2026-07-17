<template>
  <div class="search-panel" :class="{ collapsed }">
    <div class="search-panel-header" @click="collapsed = !collapsed">
      <span class="search-panel-title">
        <el-icon><Search /></el-icon>
        筛选查询
      </span>
      <el-icon class="collapse-icon" :class="{ expanded: !collapsed }">
        <ArrowDown />
      </el-icon>
    </div>
    <div class="search-panel-body">
      <!-- 各页放自己的 el-form 表单字段 -->
      <slot />
      <div class="search-actions">
        <el-button type="primary" icon="Search" :loading="loading" @click="$emit('search')">
          搜索
        </el-button>
        <el-button icon="Refresh" @click="$emit('reset')">
          重置
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, ArrowDown } from '@element-plus/icons-vue'

defineProps<{
  loading?: boolean
}>()

defineEmits<{
  search: []
  reset: []
}>()

// 折叠状态双向绑定，默认收起由页面决定
const collapsed = defineModel<boolean>('collapsed', { default: true })
</script>

<style scoped lang="scss">
.search-panel {
  background: var(--osr-surface);
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  overflow: hidden;
  transition: all var(--osr-transition-base);

  &.collapsed .search-panel-body {
    display: none;
  }

  .search-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    cursor: pointer;
    user-select: none;
    transition: background var(--osr-transition-fast);

    &:active {
      background: var(--osr-bg-page);
    }

    .search-panel-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: var(--osr-text-primary);

      .el-icon {
        color: var(--osr-primary);
        font-size: 16px;
      }
    }

    .collapse-icon {
      font-size: 16px;
      color: var(--osr-text-secondary);
      transition: transform var(--osr-transition-base);

      &.expanded {
        transform: rotate(180deg);
      }
    }
  }

  .search-panel-body {
    padding: 0 14px 14px;

    :deep(.el-form) {
      .el-form-item {
        margin-bottom: 12px;
        margin-right: 0;

        .el-form-item__label {
          font-size: 13px;
          color: var(--osr-text-secondary);
          padding-bottom: 4px;
        }
      }

      .el-input__wrapper,
      .el-select__wrapper {
        border-radius: var(--osr-radius-sm);
        box-shadow: 0 0 0 1px var(--osr-border-base) inset;
      }
    }

    .search-actions {
      display: flex;
      gap: 8px;
      margin-top: 4px;

      .el-button {
        flex: 1;
        border-radius: var(--osr-radius-sm);
      }
    }
  }
}
</style>
