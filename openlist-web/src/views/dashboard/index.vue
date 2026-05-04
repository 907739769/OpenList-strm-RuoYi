<template>
  <div class="dashboard">
    <!-- Stat Cards - 3 per row on desktop -->
    <el-row :gutter="16" class="stat-row">
      <el-col :md="8" v-for="(stat, index) in statCards" :key="index">
        <el-card class="stat-card" :class="stat.type">
          <div class="stat-icon">
            <el-icon :size="28"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts -->
    <el-row :gutter="16" class="chart-row">
      <el-col :md="8" v-for="(chart, index) in chartData" :key="index">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <span class="chart-title">{{ chart.title }}</span>
              <el-select v-model="chart.range" size="small" style="width: 90px" @change="chart.load()">
                <el-option label="今日" value="today" />
                <el-option label="昨日" value="yesterday" />
                <el-option label="全部" value="all" />
              </el-select>
            </div>
          </template>
          <div :ref="el => setChartContainer(el, index)" class="echarts-container" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCopyStatsApi, getStrmStatsApi, getRenameStatsApi } from '@/api/openlist/dashboard'
import { Files, VideoCamera, EditPen, CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import type { Component, Ref } from 'vue'

interface StatCard {
  label: string
  value: number | string
  icon: Component
  type: 'primary' | 'success' | 'warning' | 'info'
}

interface ChartData {
  title: string
  range: string
  load: () => Promise<void>
  chart: any
}

const statCards = ref<StatCard[]>([])
const copyChart = ref<any>(null)
const strmChart = ref<any>(null)
const renameChart = ref<any>(null)

// Store container refs by index
const chartContainers: Ref<HTMLElement | null>[] = [ref(null), ref(null), ref(null)]

function setChartContainer(el: unknown, index: number) {
  if (el instanceof HTMLElement) {
    chartContainers[index].value = el
  }
}

const chartData = ref<ChartData[]>([])

let resizeHandler: (() => void) | null = null

const colorMap: Record<string, string> = {
  '成功': '#22c55e',
  '失败': '#ef4444',
  '未知': '#f59e0b',
  '处理中': '#0d9488'
}

const defaultColors = ['#0d9488', '#22c55e', '#f59e0b', '#ef4444', '#6366f1', '#8b5cf6', '#ec4899', '#14b8a6']

function getColor(name: string): string {
  if (colorMap[name]) return colorMap[name]
  const idx = Object.keys(colorMap).findIndex(k => name.includes(k))
  return idx >= 0 ? colorMap[Object.keys(colorMap)[idx]] : defaultColors[Object.keys(colorMap).length + idx % defaultColors.length]
}

function renderChart(chart: any, data: Record<string, number>, range: string) {
  const rangeTextMap: Record<string, string> = { today: '今日数据', yesterday: '昨日数据', all: '全部数据' }
  const title = rangeTextMap[range] || '全部数据'

  const chartData = Object.entries(data).map(([name, value]) => ({
    value,
    name,
    itemStyle: { color: getColor(name) }
  }))

  if (chartData.length > 0) {
    chart.setOption({
      title: { text: title, left: 'center', top: 'center', textStyle: { fontSize: 13, fontWeight: 'normal', color: '#64748b' } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['50%', '55%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{c}', fontSize: 11 },
        emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
        labelLine: { length: 15, length2: 10 },
        minAngle: 5,
        data: chartData
      }]
    }, true)
  } else {
    chart.clear()
    chart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#94a3b8' } },
      series: []
    }, true)
  }
}

const loadCopyChart = async () => {
  const range = chartData.value[0].range
  const container = chartContainers[0].value
  if (!container) {
    console.warn('copyChart container not found')
    return
  }
  if (!copyChart.value) {
    copyChart.value = echarts.init(container)
  }
  try {
    const data: any = await getCopyStatsApi(range)
    if (copyChart.value) renderChart(copyChart.value, data || {}, range)
  } catch (e) {
    console.error('Failed to load copy stats:', e)
    if (copyChart.value) renderChart(copyChart.value, {}, range)
  }
}

const loadStrmChart = async () => {
  const range = chartData.value[1].range
  const container = chartContainers[1].value
  if (!container) {
    console.warn('strmChart container not found')
    return
  }
  if (!strmChart.value) {
    strmChart.value = echarts.init(container)
  }
  try {
    const data: any = await getStrmStatsApi(range)
    if (strmChart.value) renderChart(strmChart.value, data || {}, range)
  } catch (e) {
    console.error('Failed to load strm stats:', e)
    if (strmChart.value) renderChart(strmChart.value, {}, range)
  }
}

const loadRenameChart = async () => {
  const range = chartData.value[2].range
  const container = chartContainers[2].value
  if (!container) {
    console.warn('renameChart container not found')
    return
  }
  if (!renameChart.value) {
    renameChart.value = echarts.init(container)
  }
  try {
    const data: any = await getRenameStatsApi(range)
    if (renameChart.value) renderChart(renameChart.value, data || {}, range)
  } catch (e) {
    console.error('Failed to load rename stats:', e)
    if (renameChart.value) renderChart(renameChart.value, {}, range)
  }
}

onMounted(async () => {
  console.log('[Dashboard] onMounted started')

  // Set stat cards
  statCards.value = [
    { label: 'COPY 任务', value: '0', icon: Files, type: 'primary' },
    { label: 'STRM 任务', value: '0', icon: VideoCamera, type: 'success' },
    { label: 'Rename 任务', value: '0', icon: EditPen, type: 'warning' },
    { label: '成功率', value: '--', icon: CircleCheck, type: 'info' },
    { label: '失败数', value: '0', icon: CircleClose, type: 'warning' },
    { label: '处理中', value: '0', icon: Loading, type: 'primary' }
  ]
  console.log('[Dashboard] statCards set:', statCards.value.length)

  // Set chartData
  chartData.value = [
    { title: 'COPY 任务', range: 'today', load: loadCopyChart, chart: null },
    { title: 'STRM 任务', range: 'today', load: loadStrmChart, chart: null },
    { title: 'Rename 任务', range: 'today', load: loadRenameChart, chart: null }
  ]
  console.log('[Dashboard] chartData set:', chartData.value.length)

  // Wait for DOM to render
  await nextTick()
  console.log('[Dashboard] DOM ready, containers:', chartContainers.map(c => c.value ? 'found' : 'MISSING'))

  // Load charts
  await loadCopyChart()
  await loadStrmChart()
  await loadRenameChart()
  console.log('[Dashboard] All charts loaded')

  resizeHandler = () => {
    copyChart.value?.resize()
    strmChart.value?.resize()
    renameChart.value?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

onUnmounted(() => {
  resizeHandler && window.removeEventListener('resize', resizeHandler)
  copyChart.value?.dispose()
  strmChart.value?.dispose()
  renameChart.value?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard {
  padding: 24px;
}

/* ============================================
   Stat Cards
   ============================================ */
.stat-row {
  margin-bottom: 24px;
}

.stat-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  margin-bottom: 16px;
  cursor: default;
  transition: all var(--osr-transition-base);

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--osr-shadow-md);
  }

  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    padding: 20px;
    gap: 16px;
  }

  .stat-icon {
    width: 52px;
    height: 52px;
    border-radius: var(--osr-radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .stat-info {
    flex: 1;
    min-width: 0;

    .stat-value {
      font-size: 24px;
      font-weight: 700;
      color: var(--osr-text-primary);
      line-height: 1.2;
    }

    .stat-label {
      font-size: 13px;
      color: var(--osr-text-secondary);
      margin-top: 2px;
    }
  }

  /* Color variants */
  &.primary .stat-icon {
    background-color: var(--osr-primary-light-9);
    color: var(--osr-primary);
  }
  &.success .stat-icon {
    background-color: var(--osr-success-light);
    color: var(--osr-success);
  }
  &.warning .stat-icon {
    background-color: var(--osr-warning-light);
    color: var(--osr-warning);
  }
  &.info .stat-icon {
    background-color: var(--osr-info-light);
    color: var(--osr-info);
  }
}

/* ============================================
   Chart Cards
   ============================================ */
.chart-row {
  margin-bottom: -16px;
}

.chart-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
  margin-bottom: 16px;
  transition: box-shadow var(--osr-transition-base);

  &:hover {
    box-shadow: var(--osr-shadow-md);
  }

  :deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid var(--osr-border-light);
    background-color: var(--osr-surface);
  }
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .chart-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--osr-text-primary);
  }
}

.echarts-container {
  height: 260px;
  width: 100%;
}

/* ============================================
   Responsive
   ============================================ */
@media (max-width: 768px) {
  .dashboard {
    padding: 16px;
  }

  .stat-card :deep(.el-card__body) {
    padding: 16px;
  }

  .stat-icon {
    width: 44px !important;
    height: 44px !important;
  }

  .stat-icon .el-icon {
    font-size: 22px !important;
  }

  .stat-value {
    font-size: 20px !important;
  }

  .echarts-container {
    height: 220px !important;
  }
}
</style>
