<template>
  <div>
    <el-table :data="rules" size="small" style="width:100%">
      <el-table-column label="目标目录名" min-width="140">
        <template #default="{ row }">
          <el-input
            v-model="row.targetDir"
            :placeholder="row.isFallback === '1' ? '兜底目录' : '目录名'"
          />
        </template>
      </el-table-column>
      <el-table-column label="类型（Genre）" min-width="200">
        <template #default="{ row }">
          <el-select
            :model-value="toArray(row.genreIds)"
            multiple filterable allow-create default-first-option collapse-tags collapse-tags-tooltip
            :disabled="row.isFallback === '1'"
            placeholder="不限"
            @update:model-value="(v: string[]) => { row.genreIds = toCsv(v) }"
          >
            <el-option v-for="opt in genreOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="原始语言" min-width="200">
        <template #default="{ row }">
          <el-select
            :model-value="toArray(row.originalLanguages)"
            multiple filterable allow-create default-first-option collapse-tags collapse-tags-tooltip
            :disabled="row.isFallback === '1'"
            placeholder="不限"
            @update:model-value="(v: string[]) => { row.originalLanguages = toCsv(v) }"
          >
            <el-option v-for="opt in LANGUAGE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="国家/地区" min-width="200">
        <template #default="{ row }">
          <el-select
            :model-value="toArray(row.originCountries)"
            multiple filterable allow-create default-first-option collapse-tags collapse-tags-tooltip
            :disabled="row.isFallback === '1'"
            placeholder="不限"
            @update:model-value="(v: string[]) => { row.originCountries = toCsv(v) }"
          >
            <el-option v-for="opt in COUNTRY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
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
import { computed } from 'vue'
import type { CategoryRule } from '@/api/openlist/renameConfig'
import { MOVIE_GENRE_OPTIONS, TV_GENRE_OPTIONS, LANGUAGE_OPTIONS, COUNTRY_OPTIONS } from '@/constants/categoryRuleOptions'

const props = defineProps<{
  rules: CategoryRule[]
  mediaType: string
}>()

defineEmits<{
  add: [mediaType: string]
  remove: [mediaType: string, index: number]
  move: [mediaType: string, index: number, direction: -1 | 1]
}>()

/** 电影和剧集的 TMDB genre 编号含义不同，按 mediaType 选对应的可选项列表 */
const genreOptions = computed(() => (props.mediaType === 'tv' ? TV_GENRE_OPTIONS : MOVIE_GENRE_OPTIONS))

/** 数据库存的是逗号分隔字符串，下拉多选组件需要数组，两边转换 */
const toArray = (value?: string) => (value ? value.split(',').map(s => s.trim()).filter(Boolean) : [])
const toCsv = (arr: string[]) => arr.join(',')
</script>
