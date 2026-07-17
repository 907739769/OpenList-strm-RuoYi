<template>
  <div class="pagination-bar" v-if="total > 0">
    <div class="pagination-info">
      <span class="total-text">共 {{ total }} 条</span>
    </div>
    <div class="pagination-controls">
      <el-button
        :icon="ArrowLeft"
        text
        size="small"
        :disabled="pageNum <= 1"
        class="page-btn"
        @click="$emit('prev')"
      />
      <div class="page-num-box">
        <span class="current-page">{{ pageNum }}</span>
        <span class="page-divider">/</span>
        <span class="total-pages">{{ totalPages }}</span>
      </div>
      <el-button
        :icon="ArrowRight"
        text
        size="small"
        :disabled="pageNum >= totalPages"
        class="page-btn"
        @click="$emit('next')"
      />
      <el-select
        :model-value="pageSize"
        size="small"
        class="page-size-select"
        @update:model-value="onSizeChange"
      >
        <el-option :label="10" :value="10" />
        <el-option :label="20" :value="20" />
        <el-option :label="50" :value="50" />
      </el-select>
      <span class="page-size-label">条/页</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

defineProps<{
  pageNum: number
  pageSize: number
  total: number
  totalPages: number
}>()

const emit = defineEmits<{
  prev: []
  next: []
  'update:pageSize': [value: number]
  'size-change': []
}>()

function onSizeChange(value: number) {
  emit('update:pageSize', value)
  emit('size-change')
}
</script>

<style scoped lang="scss">
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--osr-surface);
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  gap: 12px;

  .pagination-info {
    flex-shrink: 0;

    .total-text {
      font-size: 13px;
      font-weight: 600;
      color: var(--osr-text-secondary);
    }
  }

  .pagination-controls {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    justify-content: flex-end;

    .page-btn {
      padding: 4px;
      min-width: unset;
      height: unset;

      :deep(.el-icon) {
        font-size: 18px;
        color: var(--osr-text-primary);
      }

      &:disabled :deep(.el-icon) {
        color: var(--osr-text-disabled);
      }
    }

    .page-num-box {
      display: flex;
      align-items: center;
      gap: 2px;
      padding: 4px 10px;
      background: var(--osr-bg-page);
      border-radius: var(--osr-radius-sm);
      border: 1px solid var(--osr-border-light);

      .current-page {
        font-size: 16px;
        font-weight: 700;
        color: var(--osr-primary);
        line-height: 1;
      }

      .page-divider {
        font-size: 12px;
        color: var(--osr-text-disabled);
        margin: 0 2px;
      }

      .total-pages {
        font-size: 13px;
        color: var(--osr-text-secondary);
        line-height: 1;
      }
    }

    .page-size-select {
      width: 64px;

      :deep(.el-input__wrapper) {
        padding: 0 8px;
        height: 28px;
        border-radius: var(--osr-radius-sm);
        box-shadow: 0 0 0 1px var(--osr-border-light) inset;
      }

      :deep(.el-input__inner) {
        font-size: 13px;
        text-align: center;
        color: var(--osr-text-primary);
      }
    }

    .page-size-label {
      font-size: 12px;
      color: var(--osr-text-secondary);
      flex-shrink: 0;
      white-space: nowrap;
    }
  }
}
</style>
