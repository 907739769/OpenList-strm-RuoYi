<template>
  <div class="dashboard">
    <!-- Stat Cards -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="6" v-for="(stat, index) in statCards" :key="index">
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
      <el-col :xs="24" :sm="24" :md="8" v-for="(chart, index) in chartData" :key="index">
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
          <div :ref="chart.refKey" class="echarts-container" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, type Ref } from 'vue'
import * as echarts from 'echarts'
import { getCopyStatsApi, getStrmStatsApi, getRenameStatsApi } from '@/api/openlist/dashboard'
import { Files, VideoCamera, EditPen, CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import type { Component } from 'vue'

interface StatCard {
  label: string
  value: number | string
  icon: Component
  type: 'primary' | 'success' | 'warning' | 'info'
}

interface ChartData {
  title: string
  range: string
  refKey: string
  load: () => Promise<void>
  chart: echarts.ECharts | null
}

const statCards = ref<StatCard[]>([])
let copyChart: echarts.ECharts | null = null
let strmChart: echarts.ECharts | null = null
let renameChart: echarts.ECharts | null = null

const chartRefs: Record<string, Ref<HTMLElement | null>> = {
  copyChartRef: ref(null),
  strmChartRef: ref(null),
  renameChartRef: ref(null)
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

function renderChart(chart: echarts.ECharts, data: Record<string, number>, range: string) {
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

async function initChart(chartRef: Ref<HTMLElement | null>, chartInstance: echarts.ECharts | null, loadFn: () => Promise<void>): Promise<ChartData> {
  await nextTick()
  const el = chartRef.value
  if (el && !chartInstance) {
    const chart = echarts.init(el)
    await loadFn()
    return {
      title: '',
      range: 'today',
      refKey: '',
      load: loadFn,
      chart
    }
  }
  return {
    title: '',
    range: 'today',
    refKey: '',
    load: loadFn,
    chart: chartInstance
  }
}

const loadCopyChart = async () => {
  if (!chartRefs.copyChartRef.value) return
  if (!copyChart) {
    await nextTick()
    copyChart = echarts.init(chartRefs.copyChartRef.value)
  }
  try {
    const data: any = await getCopyStatsApi('today')
    if (copyChart) renderChart(copyChart, data || {}, 'today')
  } catch (e) {
    console.error('Failed to load copy stats:', e)
    if (copyChart) renderChart(copyChart, {}, 'today')
  }
}

const loadStrmChart = async () => {
  if (!chartRefs.strmChartRef.value) return
  if (!strmChart) {
    await nextTick()
    strmChart = echarts.init(chartRefs.strmChartRef.value)
  }
  try {
    const data: any = await getStrmStatsApi('today')
    if (strmChart) renderChart(strmChart, data || {}, 'today')
  } catch (e) {
    console.error('Failed to load strm stats:', e)
    if (strmChart) renderChart(strmChart, {}, 'today')
  }
}

const loadRenameChart = async () => {
  if (!chartRefs.renameChartRef.value) return
  if (!renameChart) {
    await nextTick()
    renameChart = echarts.init(chartRefs.renameChartRef.value)
  }
  try {
    const data: any = await getRenameStatsApi('today')
    if (renameChart) renderChart(renameChart, data || {}, 'today')
  } catch (e) {
    console.error('Failed to load rename stats:', e)
    if (renameChart) renderChart(renameChart, {}, 'today')
  }
}

onMounted(async () => {
  // Initialize charts
  const copyTask = initChart(chartRefs.copyChartRef, copyChart, loadCopyChart)
  const strmTask = initChart(chartRefs.strmChartRef, strmChart, loadStrmChart)
  const renameTask = initChart(chartRefs.renameChartRef, renameChart, loadRenameChart)

  const [copy, strm, rename] = await Promise.all([copyTask, strmTask, renameTask])

  copyChart = copy.chart
  strmChart = strm.chart
  renameChart = rename.chart

  chartData.value = [
    { title: 'COPY 任务', range: 'today', refKey: 'copyChartRef', load: loadCopyChart, chart: copyChart },
    { title: 'STRM 任务', range: 'today', refKey: 'strmChartRef', load: loadStrmChart, chart: strmChart },
    { title: 'Rename 任务', range: 'today', refKey: 'renameChartRef', load: loadRenameChart, chart: renameChart }
  ]

  // Update stat cards
  statCards.value = [
    { label: 'COPY 任务', value: '0', icon: Files, type: 'primary' },
    { label: 'STRM 任务', value: '0', icon: VideoCamera, type: 'success' },
    { label: 'Rename 任务', value: '0', icon: EditPen, type: 'warning' },
    { label: '成功率', value: '--', icon: CircleCheck, type: 'info' },
    { label: '失败数', value: '0', icon: CircleClose, type: 'warning' },
    { label: '处理中', value: '0', icon: Loading, type: 'primary' }
  ]

  resizeHandler = () => {
    copyChart?.resize()
    strmChart?.resize()
    renameChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

onUnmounted(() => {
  resizeHandler && window.removeEventListener('resize', resizeHandler)
  copyChart?.dispose()
  strmChart?.dispose()
  renameChart?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard {
  padding: 0;
}

/* ============================================
   Stat Cards
   ============================================ */
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
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
