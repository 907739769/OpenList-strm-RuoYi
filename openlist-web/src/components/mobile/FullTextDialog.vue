<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="85%"
    :close-on-click-modal="true"
    class="full-text-dialog"
  >
    <div class="full-text-content">{{ content }}</div>
    <template #footer>
      <el-button size="small" @click="copy">
        <el-icon><CopyDocument /></el-icon> 复制
      </el-button>
      <el-button size="small" type="primary" @click="visible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'

// 移动端路径 / 文件名会被截断，点开看全并支持复制。
// 状态自管，页面通过 ref 调 show()，不必各自维护 visible/title/content。
const visible = ref(false)
const title = ref('')
const content = ref('')

function show(text: string, dialogTitle: string) {
  content.value = text
  title.value = dialogTitle
  visible.value = true
}

async function copy() {
  try {
    await navigator.clipboard.writeText(content.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // navigator.clipboard 需要安全上下文，HTTP 下走降级方案
    const textarea = document.createElement('textarea')
    textarea.value = content.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

defineExpose({ show })
</script>

<style scoped lang="scss">
:deep(.full-text-dialog) {
  .el-dialog__body {
    padding: 16px;
  }
}

.full-text-content {
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: var(--osr-text-primary);
  max-height: 400px;
  overflow-y: auto;
  background: var(--osr-bg-page);
  border-radius: var(--osr-radius-sm);
  padding: 12px;
}
</style>
