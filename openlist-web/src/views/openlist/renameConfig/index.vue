<template>
  <div class="page-container">
    <el-card class="table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件名模板" name="template">
          <div v-loading="templateLoading" class="template-tab">
            <div class="template-editor">
              <el-input
                ref="templateInputRef"
                v-model="template"
                type="textarea"
                :rows="6"
                placeholder="Pebble 语法，例如 {{ title }} ({{ year }}).{{ extension }}"
                @input="doPreview"
              />
              <div class="template-actions">
                <el-button type="primary" :loading="templateSaving" @click="saveTemplate">保存模板</el-button>
              </div>
              <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" style="margin-top:12px" />
              <el-alert v-else :title="previewResult || '（预览为空）'" type="success" :closable="false" style="margin-top:12px">
                <div style="font-family:Consolas,monospace;word-break:break-all;white-space:pre-wrap">{{ previewResult }}</div>
              </el-alert>
            </div>
            <div class="template-variables">
              <div class="variables-title">可用变量（点击插入）</div>
              <el-tag
                v-for="v in TEMPLATE_VARIABLES"
                :key="v"
                class="variable-tag"
                @click="insertVariable(v)"
              >{{ v }}</el-tag>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="分类规则" name="rules">
          <div v-loading="rulesLoading">
            <el-divider content-position="left">电影</el-divider>
            <RuleTable
              :rules="movieRules" media-type="movie"
              @add="addRule" @remove="removeRule" @move="moveRule"
            />
            <div class="rules-actions">
              <el-button type="primary" :loading="rulesSaving" @click="saveRules('movie')">保存电影分类规则</el-button>
            </div>

            <el-divider content-position="left">剧集</el-divider>
            <RuleTable
              :rules="tvRules" media-type="tv"
              @add="addRule" @remove="removeRule" @move="moveRule"
            />
            <div class="rules-actions">
              <el-button type="primary" :loading="rulesSaving" @click="saveRules('tv')">保存剧集分类规则</el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import RuleTable from './RuleTable.vue'
import { useRenameConfig, TEMPLATE_VARIABLES } from '@/composables/useRenameConfig'

const activeTab = ref('template')
const templateInputRef = ref()

const {
  template, templateLoading, templateSaving, previewResult, previewError,
  doPreview, saveTemplate,
  movieRules, tvRules, rulesLoading, rulesSaving,
  addRule, removeRule, moveRule, saveRules
} = useRenameConfig()

/**
 * 插入到光标位置而不是简单追加到末尾：ElInput(textarea) 把底层 <textarea> DOM
 * 暴露在组件实例的 .textarea 上，取不到时（理论上不会发生）退化为追加到末尾。
 */
const insertVariable = (varName: string) => {
  const snippet = `{{ ${varName} }}`
  const textarea: HTMLTextAreaElement | undefined = templateInputRef.value?.textarea
  if (!textarea) {
    template.value += snippet
    doPreview()
    return
  }
  const start = textarea.selectionStart ?? template.value.length
  const end = textarea.selectionEnd ?? template.value.length
  template.value = template.value.slice(0, start) + snippet + template.value.slice(end)
  doPreview()
  nextTick(() => {
    const cursor = start + snippet.length
    textarea.focus()
    textarea.setSelectionRange(cursor, cursor)
  })
}
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border: none;
  border-radius: var(--osr-radius-lg);
  box-shadow: var(--osr-shadow-base);
}

.template-tab {
  display: flex;
  gap: 20px;

  .template-editor {
    flex: 1;
    min-width: 0;
  }

  .template-variables {
    width: 220px;
    flex-shrink: 0;

    .variables-title {
      font-size: 13px;
      color: var(--osr-text-secondary);
      margin-bottom: 8px;
    }

    .variable-tag {
      margin: 0 6px 6px 0;
      cursor: pointer;
    }
  }
}

.template-actions,
.rules-actions {
  margin-top: 12px;
}
</style>
