<template>
  <div class="directory-tree-select" ref="containerRef">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      readonly
      @click="handleClick"
      :class="{ 'is-active': dropdownVisible }"
    >
      <template #append>
        <el-button icon="FolderOpened" @click="handleClick" />
      </template>
    </el-input>

    <!-- Dropdown tree -->
    <div v-show="dropdownVisible" class="tree-dropdown" :style="dropdownStyle">
      <el-tree
        ref="treeRef"
        :props="treeProps"
        :expand-on-click-node="false"
        :expand-on-dbl-click-node="false"
        :load="loadNode"
        lazy
        node-key="id"
        highlight-current
        @node-click="handleNodeClick"
      >
        <template #default="{ node }">
          <span class="custom-tree-node">
            <el-icon><Folder /></el-icon>
            <span>{{ node.label }}</span>
          </span>
        </template>
      </el-tree>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Folder } from '@element-plus/icons-vue'
import request from '@/api/request'

const props = withDefaults(defineProps<{
  modelValue?: string
  type?: 'openlist' | 'local'
  placeholder?: string
}>(), {
  modelValue: '',
  type: 'openlist',
  placeholder: '请选择目录'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const containerRef = ref<HTMLElement | null>(null)
const treeRef = ref<any>()
const dropdownVisible = ref(false)
let bodyClickListener: ((e: MouseEvent) => void) | null = null

const treeProps = {
  label: 'name',
  children: 'children',
  isLeaf: (data: any) => !data.isParent
}

const dropdownStyle = computed(() => {
  if (!containerRef.value) return {}
  const rect = containerRef.value.getBoundingClientRect()
  return {
    position: 'fixed' as const,
    left: rect.left + 'px',
    top: (rect.bottom + 4) + 'px',
    width: Math.max(rect.width, 300) + 'px',
    zIndex: 99999
  }
})

async function loadNode(node: any, resolve: any) {
  // Root level (level 0 = the tree itself)
  if (node.level === 0) {
    try {
      const url = props.type === 'openlist'
        ? `/openliststrm/path/openlist`
        : `/openliststrm/path/local`
      const res: any[] = await request.get(url)
      resolve(res)
    } catch (e) {
      console.error('Failed to load root directory:', e)
      resolve([])
    }
    return
  }

  // All nodes from backend are directories (isParent=true), so they can be expanded
  try {
      const url = props.type === 'openlist'
        ? `/openliststrm/path/openlist?id=${encodeURIComponent(node.data.id)}`
        : `/openliststrm/path/local?id=${encodeURIComponent(node.data.id)}`
    const res: any[] = await request.get(url)
    resolve(res)
  } catch (e) {
    console.error('Failed to load children:', e)
    resolve([])
  }
}

function handleClick() {
  dropdownVisible.value = !dropdownVisible.value
  if (!dropdownVisible.value) {
    removeBodyClickListener()
  }
}

function handleNodeClick(data: any) {
  emit('update:modelValue', data.id)
  dropdownVisible.value = false
  removeBodyClickListener()
}

function onBodyClick(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    dropdownVisible.value = false
    removeBodyClickListener()
  }
}

function removeBodyClickListener() {
  if (bodyClickListener) {
    document.removeEventListener('mousedown', bodyClickListener)
    bodyClickListener = null
  }
}

onMounted(() => {
  bodyClickListener = onBodyClick
  document.addEventListener('mousedown', bodyClickListener)
})

onUnmounted(() => {
  removeBodyClickListener()
})
</script>

<style scoped lang="scss">
.directory-tree-select {
  position: relative;
  width: 100%;
}

.tree-dropdown {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.15);
  max-height: 400px;
  overflow: auto;
  padding: 8px 0;

  :deep(.el-tree) {
    background: transparent;
    width: 100%;

    .el-tree-node__content {
      height: 32px;
      padding: 0 8px;

      &:hover {
        background-color: #f5f7fa;
      }
    }

    .el-tree-node__expand-icon {
      // Replace default expand icon with folder icon for directories
      &.is-leaf {
        color: transparent;
      }
    }
  }
}

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;

  .el-icon {
    color: #E6A23C;
  }
}

:deep(.el-input.is-active .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
</style>
