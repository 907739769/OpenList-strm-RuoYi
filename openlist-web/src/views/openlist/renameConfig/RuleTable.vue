<template>
  <div>
    <el-table :data="rules" size="small" style="width:100%">
      <el-table-column label="目标目录名" min-width="140">
        <template #default="{ row }">
          <el-input
            v-model="row.targetDir"
            :disabled="row.isFallback === '1'"
            :placeholder="row.isFallback === '1' ? '兜底目录' : '目录名'"
          />
        </template>
      </el-table-column>
      <el-table-column label="Genre IDs（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.genreIds" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="原始语言（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.originalLanguages" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="国家/地区（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.originCountries" :disabled="row.isFallback === '1'" placeholder="不限" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row, $index }">
          <el-button link :disabled="row.isFallback === '1'" @click="$emit('move', mediaType, $index, -1)">上移</el-button>
          <el-button link :disabled="row.isFallback === '1'" @click="$emit('move', mediaType, $index, 1)">下移</el-button>
          <el-button link type="danger" :disabled="row.isFallback === '1'" @click="$emit('remove', mediaType, $index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:8px">
      <el-button @click="$emit('add', mediaType)">+ 新增规则</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CategoryRule } from '@/api/openlist/renameConfig'

defineProps<{
  rules: CategoryRule[]
  mediaType: string
}>()

defineEmits<{
  add: [mediaType: string]
  remove: [mediaType: string, index: number]
  move: [mediaType: string, index: number, direction: -1 | 1]
}>()
</script>
