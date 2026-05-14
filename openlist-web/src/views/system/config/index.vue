<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-header-icon">
          <el-icon><Setting /></el-icon>
        </div>
        <div>
          <h2 class="page-title">参数设置</h2>
          <p class="page-desc">系统全局参数配置总览 — 点击 ✏️ 编辑，修改后保存生效</p>
        </div>
      </div>
      <el-button type="primary" plain :icon="Refresh" @click="handleRefreshCache" :loading="refreshing">
        刷新缓存
      </el-button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="page-loading">
      <el-icon class="is-loading" :size="36"><Loading /></el-icon>
      <p>正在加载参数配置...</p>
    </div>

    <!-- Config Sections -->
    <template v-else>
      <div
        v-for="section in configSections"
        :key="section.key"
        class="config-section"
      >
        <div class="section-header">
          <div class="section-icon">
            <el-icon :size="18"><component :is="section.icon" /></el-icon>
          </div>
          <h3>{{ section.title }}</h3>
          <span class="section-count">{{ section.items.length }}</span>
        </div>
        <div class="section-cards">
          <div
            v-for="item in section.items"
            :key="item.configId"
            class="config-item"
            :class="{ 'config-item--editing': editingId === item.configId }"
          >
            <!-- Display Mode -->
            <template v-if="editingId !== item.configId">
              <div class="config-item__header">
                <span class="config-item__name">{{ item.configName }}</span>
                <div class="config-item__actions">
                  <el-tooltip content="复制键名" placement="top" :show-after="300">
                    <el-icon class="action-btn action-btn--copy" :size="16" @click="copyText(item.configKey)"><DocumentCopy /></el-icon>
                  </el-tooltip>
                  <el-tooltip content="编辑" placement="top" :show-after="300">
                    <el-icon class="action-btn action-btn--edit" :size="16" @click="startEdit(item)"><EditPen /></el-icon>
                  </el-tooltip>
                </div>
              </div>
              <div class="config-item__value">
                <span class="value-text">{{ displayValue(item) }}</span>
                <el-tooltip v-if="isSensitive(item.configKey)" :content="item.configValue" placement="top" :show-after="300">
                  <el-icon class="value-expand" :size="14"><ZoomIn /></el-icon>
                </el-tooltip>
              </div>
              <!-- Mobile edit button (visible only on small screens) -->
              <div class="config-item__mobile-edit">
                <el-button type="primary" plain :icon="EditPen" class="mobile-edit-btn" @click="startEdit(item)">
                  编辑参数
                </el-button>
              </div>
            </template>

            <!-- Edit Mode -->
            <template v-else>
              <div class="config-item__header">
                <span class="config-item__name">{{ item.configName }}</span>
                <el-tag size="small" type="warning">编辑中</el-tag>
              </div>
              <el-input
                v-model="editForm.configValue"
                type="textarea"
                :rows="2"
                placeholder="请输入参数值"
                class="edit-input"
                :class="{ 'edit-input--error': editError }"
              />
              <el-tooltip v-if="editError" :content="editError" placement="top" :show-after="200">
                <el-text class="edit-error" size="small" type="danger">{{ editError }}</el-text>
              </el-tooltip>
              <div class="edit-actions">
                <el-button size="default" @click="cancelEdit">取消</el-button>
                <el-button type="primary" size="default" :loading="saving" @click="saveEdit(item)">保存</el-button>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <el-empty v-if="configSections.length === 0" description="暂无参数配置" :image-size="160" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Setting, Loading, DocumentCopy, ZoomIn, EditPen, Refresh,
  Monitor, Connection, ChatDotRound,
  ChatLineSquare, Lightning
} from '@element-plus/icons-vue'
import { getConfigListApi, updateConfigApi } from '@/api/system/config'
import type { SysConfig } from '@/types/system'

interface ConfigSection {
  key: string
  title: string
  icon: any
  items: SysConfig[]
}

const loading = ref(true)
const refreshing = ref(false)
const saving = ref(false)
const configList = ref<SysConfig[]>([])

// Editing state
const editingId = ref<number | null>(null)
const editForm = ref<Partial<SysConfig>>({})
const editError = ref('')

// Section definitions with categorization logic
const configSections = computed<ConfigSection[]>(() => {
  const sections: Record<string, ConfigSection> = {}

  const addSection = (key: string, title: string, icon: any) => {
    if (!sections[key]) {
      sections[key] = { key, title, icon, items: [] }
    }
  }

  const categorize = (config: SysConfig) => {
    const key = config.configKey || ''
    const name = config.configName || ''

    // OpenAI 相关
    if (key.includes('openai') || name.includes('openai') || name.includes('OpenAI') || name.includes('gpt') || name.includes('GPT')) {
      addSection('openai', 'OpenAI 配置', ChatDotRound)
      sections['openai'].items.push(config)
      return
    }

    // TMDB 相关
    if (key.includes('tmdb') || name.includes('TMDB') || name.includes('tmdb')) {
      addSection('tmdb', 'TMDB 影视配置', Lightning)
      sections['tmdb'].items.push(config)
      return
    }

    // Telegram 相关
    if (key.includes('tg.') || name.includes('tg') || name.includes('TG') || name.includes('Telegram') || name.includes('telegram')) {
      addSection('tg', 'Telegram 机器人', ChatLineSquare)
      sections['tg'].items.push(config)
      return
    }

    // Copy / STRM 相关
    if (key.includes('copy') || key.includes('strm') || name.includes('复制') || name.includes('STRM') || name.includes('strm')) {
      addSection('copy', '复制 & STRM 任务', Connection)
      sections['copy'].items.push(config)
      return
    }

    // Openlist 基础配置（默认归入此类）
    addSection('openlist', 'Openlist 基础配置', Monitor)
    sections['openlist'].items.push(config)
  }

  configList.value.forEach(categorize)

  // Define display order
  const order = ['openlist', 'copy', 'tg', 'openai', 'tmdb']
  return order
    .filter(k => sections[k])
    .map(k => sections[k])
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getConfigListApi({ pageNum: 1, pageSize: 500 }) as any
    configList.value = res.records || []
  } catch (error) {
    console.error(error)
    ElMessage.error('加载参数配置失败')
  } finally {
    loading.value = false
  }
}

// Sensitive keys that should be masked
const sensitiveKeys = ['token', 'apikey', 'api_key', 'secret', 'password', 'passwd']

const isSensitive = (key: string): boolean => {
  if (!key) return false
  const lower = key.toLowerCase()
  return sensitiveKeys.some(s => lower.includes(s))
}

const displayValue = (config: SysConfig): string => {
  if (!config.configValue) return '—'
  if (isSensitive(config.configKey)) {
    const v = config.configValue
    if (v.length <= 6) return v
    return v.slice(0, 4) + '•'.repeat(Math.min(v.length - 4, 12))
  }
  return config.configValue
}

// Edit functions
const startEdit = (config: SysConfig) => {
  editingId.value = config.configId
  editForm.value = { ...config }
  editError.value = ''
}

const cancelEdit = () => {
  editingId.value = null
  editForm.value = {}
  editError.value = ''
}

const saveEdit = async (original: SysConfig) => {
  if (!editForm.value.configValue && editForm.value.configValue !== '') {
    editError.value = '参数值不能为空'
    return
  }

  saving.value = true
  editError.value = ''

  try {
    await updateConfigApi({
      configId: original.configId,
      configName: original.configName,
      configKey: original.configKey,
      configValue: editForm.value.configValue || '',
      configType: original.configType,
      createTime: original.createTime,
      updateTime: original.updateTime,
      remark: original.remark
    })
    ElMessage.success('保存成功')
    editingId.value = null
    await getList()
  } catch (error: any) {
    editError.value = error.msg || error.message || '保存失败'
  } finally {
    saving.value = false
  }
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

const handleRefreshCache = async () => {
  refreshing.value = true
  try {
    const { refreshCacheApi } = await import('@/api/system/config')
    await refreshCacheApi()
    ElMessage.success('缓存已刷新')
  } catch (error: any) {
    ElMessage.error(error.msg || error.message || '刷新缓存失败')
  } finally {
    refreshing.value = false
  }
}

getList()
</script>

<style scoped lang="scss">
/* ============================================
    Page Container
    ============================================ */
.page-container {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 960px;
  margin: 0 auto;
  padding: 0 4px;
}

/* ============================================
    Page Header
    ============================================ */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;

  .page-header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .page-header-icon {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: linear-gradient(135deg, #0d9488, #14b8a6);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      box-shadow: 0 4px 14px rgba(13, 148, 136, 0.35);
    }

    .page-title {
      margin: 0;
      font-size: 22px;
      font-weight: 700;
      color: var(--osr-text-primary);
      letter-spacing: 0.3px;
    }

    .page-desc {
      margin: 4px 0 0;
      font-size: 13px;
      color: var(--osr-text-secondary);
    }
  }
}

/* ============================================
    Loading
    ============================================ */
.page-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
  gap: 16px;
  color: var(--osr-text-secondary);

  p { margin: 0; font-size: 14px; }
}

/* ============================================
    Config Section
    ============================================ */
.config-section {
  .section-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 14px;
    padding-bottom: 10px;
    border-bottom: 2px solid var(--osr-border-light);

    .section-icon {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      background: var(--osr-primary-light-9);
      color: var(--osr-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
    }

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--osr-text-primary);
    }

    .section-count {
      margin-left: auto;
      font-size: 12px;
      color: var(--osr-text-secondary);
      background: var(--osr-bg-page);
      padding: 2px 10px;
      border-radius: 10px;
    }
  }

  .section-cards {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding-top: 10px;
  }
}

/* ============================================
    Config Item
    ============================================ */
.config-item {
  background: var(--osr-surface);
  border-radius: 12px;
  border: 1px solid var(--osr-border-light);
  padding: 14px 18px;
  transition: all var(--osr-transition-base);

  &:hover:not(.config-item--editing) {
    border-color: var(--osr-primary-light-6);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }

  &--editing {
    border-color: var(--osr-warning);
    box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.15);
  }

  .config-item__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;

    .config-item__name {
      font-size: 14px;
      font-weight: 600;
      color: var(--osr-text-primary);
      flex: 1;
      min-width: 0;
    }

    .config-item__actions {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-shrink: 0;
    }
  }

  .config-item__value {
    display: flex;
    align-items: center;
    gap: 8px;

    .value-text {
      flex: 1;
      font-family: 'SF Mono', 'Courier New', monospace;
      font-size: 13px;
      color: var(--osr-text-regular);
      line-height: 1.6;
      word-break: break-all;
      min-width: 0;
    }

    .value-expand {
      color: var(--osr-text-placeholder);
      cursor: pointer;
      flex-shrink: 0;
      padding: 2px;
      border-radius: 4px;
      transition: all var(--osr-transition-fast);

      &:hover {
        color: var(--osr-primary);
        background: var(--osr-primary-light-9);
      }
    }
  }
}

/* Action buttons */
.action-btn {
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all var(--osr-transition-fast);

  &--copy {
    color: var(--osr-text-placeholder);

    &:hover {
      color: var(--osr-primary);
      background: var(--osr-primary-light-9);
    }
  }

  &--edit {
    color: var(--osr-warning);

    &:hover {
      color: #d2972e;
      background: rgba(230, 162, 60, 0.12);
    }
  }
}

/* Mobile edit button - visible on both desktop and mobile */
.config-item__mobile-edit {
  display: block;
  padding-top: 8px;
  border-top: 1px solid var(--osr-border-light);
  margin-top: 8px;
}

.mobile-edit-btn {
  width: 100%;
  --el-button-padding-vertical: 8px;
  --el-button-padding-horizontal: 16px;
}

/* Desktop: smaller edit button */
@media (min-width: 769px) {
  .mobile-edit-btn {
    width: auto;
    --el-button-padding-vertical: 6px;
    --el-button-padding-horizontal: 12px;
    font-size: 12px;
  }
}

.mobile-edit-btn {
  width: 100%;
  --el-button-padding-vertical: 10px;
  --el-button-padding-horizontal: 16px;
}

/* Edit input */
.edit-input {
  margin-bottom: 4px;

  &--error {
    :deep(.el-textarea__inner) {
      border-color: var(--el-color-danger);
    }
  }
}

.edit-error {
  display: block;
  margin-bottom: 8px;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ============================================
    Mobile Responsive
    ============================================ */
@media (max-width: 768px) {
  .page-container {
    gap: 20px;
    padding: 0;
  }

  .page-header {
    padding: 0 16px;

    .page-header-icon {
      width: 42px;
      height: 42px;
      font-size: 20px;
    }

    .page-title { font-size: 19px; }
    .page-desc { display: none; }
  }

  .config-section {
    padding: 0 16px;

    .section-header {
      .section-icon {
        width: 28px;
        height: 28px;
        font-size: 14px;
      }

      h3 { font-size: 15px; }
      .section-count { display: none; }
    }

    .section-cards { gap: 8px; }
  }

  .config-item {
    padding: 12px 14px;

    .config-item__name { font-size: 13px; }

    .value-text {
      font-size: 12px;
    }

    /* Mobile: full width edit button */
    .mobile-edit-btn {
      width: 100%;
    }

    /* Larger action icons on mobile */
    .action-btn {
      padding: 6px;
      border-radius: 8px;
    }
  }
}
</style>
