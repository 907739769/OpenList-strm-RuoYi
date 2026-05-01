<template>
  <div class="realtime-log-container">
    <el-card shadow="never" class="log-card">
      <!-- Header toolbar -->
      <div class="log-header">
        <div class="header-left">
          <el-select v-model="logType" placeholder="日志类型" style="width: 120px" @change="handleLogTypeChange">
            <el-option label="Info" value="info" />
            <el-option label="Debug" value="debug" />
            <el-option label="Error" value="error" />
          </el-select>
          <el-tag :type="(connectionStatus.tagType as any)" size="small" style="margin-left: 12px">
            <i :class="connectionStatus.icon" /> {{ connectionStatus.text }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-checkbox v-model="autoScroll" border size="small">自动滚动</el-checkbox>
          <el-checkbox v-model="filterDebug" border size="small">Debug</el-checkbox>
          <el-checkbox v-model="filterInfo" border size="small">Info</el-checkbox>
          <el-checkbox v-model="filterWarn" border size="small">Warn</el-checkbox>
          <el-checkbox v-model="filterError" border size="small">Error</el-checkbox>
          <el-button size="small" icon="Delete" @click="clearLog">清屏</el-button>
          <el-button size="small" icon="Refresh" @click="reconnect">重连</el-button>
        </div>
      </div>

      <!-- Log content -->
      <div ref="logContentRef" class="log-content" @scroll="handleScroll">
        <div
          v-for="(line, index) in displayLines"
          :key="index"
          class="log-line"
          :class="getLineClass(line)"
          v-html="escapeHtml(line.raw)"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const logType = ref('info')
const autoScroll = ref(true)
const filterDebug = ref(true)
const filterInfo = ref(true)
const filterWarn = ref(true)
const filterError = ref(true)
const logContentRef = ref<HTMLElement | null>(null)
const logLines = ref<{ raw: string }[]>([])
const isUserScrolled = ref(false)
const connectionState = ref<'disconnected' | 'connecting' | 'connected' | 'closed'>('disconnected')

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const connectionStatus = computed(() => {
  switch (connectionState.value) {
    case 'connected': return { text: '已连接', icon: 'el-icon-success', tagType: 'success' }
    case 'connecting': return { text: '连接中', icon: 'el-icon-loading', tagType: 'warning' }
    case 'closed': return { text: '已断开', icon: 'el-icon-warning', tagType: 'danger' }
    default: return { text: '未连接', icon: 'el-icon-warning', tagType: 'danger' }
  }
})

// Filter out lines based on level checkboxes
const displayLines = computed(() => {
  if (!filterDebug.value && !filterInfo.value && !filterWarn.value && !filterError.value) return []
  return logLines.value.filter((line) => {
    const raw = line.raw
    if (raw.includes('DEBUG')) return filterDebug.value
    if (raw.includes('ERROR')) return filterError.value
    if (raw.includes('WARN')) return filterWarn.value
    // Default: show INFO and other lines
    return filterInfo.value
  })
})

function getLineClass(line: { raw: string }): string {
  if (line.raw.includes('ERROR')) return 'log-error'
  if (line.raw.includes('WARN')) return 'log-warn'
  if (line.raw.includes('DEBUG')) return 'log-debug'
  return 'log-info'
}

function escapeHtml(text: string): string {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

function scrollToBottom() {
  if (autoScroll.value && !isUserScrolled.value && logContentRef.value) {
    logContentRef.value.scrollTop = logContentRef.value.scrollHeight
  }
}

function handleScroll() {
  if (logContentRef.value) {
    const { scrollTop, scrollHeight, clientHeight } = logContentRef.value
    isUserScrolled.value = scrollHeight - scrollTop - clientHeight > 50
  }
}

function clearLog() {
  logLines.value = []
}

function reconnect() {
  disconnect()
  connectWebSocket()
}

function handleLogTypeChange() {
  clearLog()
  reconnect()
}

function disconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  connectionState.value = 'disconnected'
}

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://'
  const host = window.location.host
  // WebSocket path - LogWebSocket uses /websocket/log/ (no /api prefix)
  const url = `${protocol}${host}/websocket/log/${logType.value}`

  if (typeof WebSocket === 'undefined') {
    ElMessage.error('浏览器不支持 WebSocket')
    return
  }

  ws = new WebSocket(url)
  connectionState.value = 'connecting'

  ws.onopen = () => {
    isUserScrolled.value = false
    connectionState.value = 'connected'
  }

  ws.onmessage = (event) => {
    const rawLine = event.data
    // The backend sends HTML-formatted lines like <div class='log-item log-info'>...</div>
    // We strip HTML tags for clean display and re-apply our own styling
    const tempDiv = document.createElement('div')
    tempDiv.innerHTML = rawLine
    const textContent = tempDiv.textContent || tempDiv.innerText || rawLine

    logLines.value.push({ raw: textContent })

    // Performance: keep max 5000 lines
    if (logLines.value.length > 5000) {
      logLines.value = logLines.value.slice(-4000)
    }

    scrollToBottom()
  }

  ws.onerror = () => {
    ElMessage.error('WebSocket 连接错误')
    connectionState.value = 'closed'
  }

  ws.onclose = () => {
    connectionState.value = 'closed'
    // Auto-reconnect after 3 seconds
    reconnectTimer = setTimeout(() => {
      connectWebSocket()
    }, 3000)
  }
}

onMounted(() => {
  connectWebSocket()
})

onUnmounted(() => {
  disconnect()
})
</script>

<style scoped lang="scss">
.realtime-log-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.log-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 4px;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
  }
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fafafa;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #1e1e1e;
  padding: 12px 16px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-all;
}

.log-content::-webkit-scrollbar {
  width: 6px;
}

.log-content::-webkit-scrollbar-thumb {
  background-color: #555;
  border-radius: 3px;
}

.log-line {
  padding: 1px 0;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.log-error {
  color: #f44747 !important;
}

.log-warn {
  color: #cca700 !important;
}

.log-debug {
  color: #6a9955 !important;
}

.log-info {
  color: #d4d4d4 !important;
}

@media (max-width: 768px) {
  .log-header {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .header-right {
    width: 100%;
    justify-content: flex-start;
  }

  .log-content {
    font-size: 11px;
    padding: 8px;
  }
}
</style>
