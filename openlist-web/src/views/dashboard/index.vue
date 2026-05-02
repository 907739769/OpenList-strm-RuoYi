<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-header">
            <strong>COPY任务</strong>
            <el-select v-model="copyRange" size="small" style="width: 100px" @change="loadCopyChart">
              <el-option label="今日" value="today" />
              <el-option label="昨日" value="yesterday" />
              <el-option label="全部" value="all" />
            </el-select>
          </div>
          <div ref="copyChartRef" class="echarts-container"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-header">
            <strong>STRM任务</strong>
            <el-select v-model="strmRange" size="small" style="width: 100px" @change="loadStrmChart">
              <el-option label="今日" value="today" />
              <el-option label="昨日" value="yesterday" />
              <el-option label="全部" value="all" />
            </el-select>
          </div>
          <div ref="strmChartRef" class="echarts-container"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-header">
            <strong>Rename任务</strong>
            <el-select v-model="renameRange" size="small" style="width: 100px" @change="loadRenameChart">
              <el-option label="今日" value="today" />
              <el-option label="昨日" value="yesterday" />
              <el-option label="全部" value="all" />
            </el-select>
          </div>
          <div ref="renameChartRef" class="echarts-container"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCopyStatsApi, getStrmStatsApi, getRenameStatsApi } from '@/api/openlist/dashboard'

const copyRange = ref('today')
const strmRange = ref('today')
const renameRange = ref('today')
const copyChartRef = ref<HTMLElement | null>(null)
const strmChartRef = ref<HTMLElement | null>(null)
const renameChartRef = ref<HTMLElement | null>(null)

let copyChart: echarts.ECharts | null = null
let strmChart: echarts.ECharts | null = null
let renameChart: echarts.ECharts | null = null

let resizeHandler: (() => void) | null = null

const colorMap: Record<string, string> = {
  '成功': '#4BC0C0',
  '失败': '#FF6384',
  '未知': '#FFCE56',
  '处理中': '#36A2EB'
}

const defaultColors = ['#466dbd', '#50c295', '#b98f15', '#b44e36', '#6DC8EC', '#9270CA', '#FF9D4D', '#269A99']

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
      title: { text: title, left: 'left', textStyle: { fontSize: 12, fontWeight: 'normal', color: '#999' } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 'left', data: chartData.map(d => d.name) },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{c}', position: 'inside' },
        emphasis: { label: { show: false } },
        labelLine: { show: false },
        minAngle: 5,
        data: chartData
      }]
    }, true)
  } else {
    chart.clear()
    chart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#888' } },
      series: []
    }, true)
  }
}

const loadCopyChart = async () => {
  if (!copyChartRef.value) return
  if (!copyChart) {
    await nextTick()
    copyChart = echarts.init(copyChartRef.value)
  }
  try {
    const data: any = await getCopyStatsApi(copyRange.value)
    if (copyChart) renderChart(copyChart, data || {}, copyRange.value)
  } catch (e) {
    console.error('Failed to load copy stats:', e)
    if (copyChart) renderChart(copyChart, {}, copyRange.value)
  }
}

const loadStrmChart = async () => {
  if (!strmChartRef.value) return
  if (!strmChart) {
    await nextTick()
    strmChart = echarts.init(strmChartRef.value)
  }
  try {
    const data: any = await getStrmStatsApi(strmRange.value)
    if (strmChart) renderChart(strmChart, data || {}, strmRange.value)
  } catch (e) {
    console.error('Failed to load strm stats:', e)
    if (strmChart) renderChart(strmChart, {}, strmRange.value)
  }
}

const loadRenameChart = async () => {
  if (!renameChartRef.value) return
  if (!renameChart) {
    await nextTick()
    renameChart = echarts.init(renameChartRef.value)
  }
  try {
    const data: any = await getRenameStatsApi(renameRange.value)
    if (renameChart) renderChart(renameChart, data || {}, renameRange.value)
  } catch (e) {
    console.error('Failed to load rename stats:', e)
    if (renameChart) renderChart(renameChart, {}, renameRange.value)
  }
}

onMounted(() => {
  loadCopyChart()
  loadStrmChart()
  loadRenameChart()

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
.dashboard-container {
  padding: 16px;
}

.chart-card {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  padding: 12px;
  height: 280px;
  display: flex;
  flex-direction: column;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.chart-header strong {
  font-size: 14px;
  color: #333;
}

.echarts-container {
  flex: 1;
  min-height: 220px;
}
</style>
