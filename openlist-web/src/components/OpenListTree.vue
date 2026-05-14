<template>
  <div class="openlist-tree">
    <el-input
      v-model="filterText"
      placeholder="搜索目录..."
      prefix-icon="Search"
      clearable
      class="tree-search"
    />
    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      :filter-node-method="filterNode"
      :expand-on-click-node="false"
      :default-expand-all="defaultExpandAll"
      node-key="id"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <span class="tree-node">
          <el-icon v-if="data.type === 'folder'" class="tree-icon folder-icon"><Folder /></el-icon>
          <el-icon v-else class="tree-icon file-icon"><Document /></el-icon>
          <span>{{ node.label }}</span>
          <span v-if="data.size" class="tree-size">{{ formatSize(data.size) }}</span>
        </span>
      </template>
    </el-tree>
    <div class="tree-footer" v-if="selectedPath">
      <el-tag size="small" type="info">{{ selectedPath }}</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { getOpenlistPathApi } from '@/api/openlist/path'
import { Folder, Document } from '@element-plus/icons-vue'

interface TreeNode {
  id: string | number
  label: string
  type: 'folder' | 'file'
  path?: string
  size?: number
  children?: TreeNode[]
}

const props = withDefaults(defineProps<{
  mode?: 'openlist' | 'local'
  multiple?: boolean
  defaultExpandAll?: boolean
}>(), {
  mode: 'openlist',
  multiple: false,
  defaultExpandAll: false
})

const emit = defineEmits<{
  select: [node: TreeNode]
}>()

const filterText = ref('')
const treeData = ref<TreeNode[]>([])
const selectedPath = ref('')
const treeRef = ref()
const treeProps = {
  label: 'label',
  children: 'children'
}

const filterNode = (value: string, data: any) => {
  if (!value) return true
  return data.label?.includes(value)
}

const loadTree = async (parentId?: number) => {
  try {
    const params: any = { parentId }
    if (props.mode === 'local') {
      const { getLocalPathApi } = await import('@/api/openlist/path')
      const res = await getLocalPathApi(params) as TreeNode[]
      treeData.value = res
    } else {
      const res = await getOpenlistPathApi(params) as TreeNode[]
      treeData.value = res
    }
  } catch (e) {
    console.error(e)
  }
}

const handleNodeClick = (data: TreeNode) => {
  if (data.type === 'folder') {
    loadTree(typeof data.id === 'number' ? data.id : undefined)
  } else {
    selectedPath.value = data.path || ''
    emit('select', data)
  }
}

const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

onMounted(() => {
  loadTree()
})

defineExpose({ loadTree })
</script>

<style scoped lang="scss">
.openlist-tree {
  .tree-search {
    margin-bottom: 12px;
  }

  .tree-node {
    display: flex;
    align-items: center;
    flex: 1;
    padding-right: 8px;

    .tree-icon {
      margin-right: 4px;
      font-size: 16px;

      &.folder-icon {
        color: #E6A23C;
      }

      &.file-icon {
        color: #909399;
      }
    }

    .tree-size {
      margin-left: auto;
      font-size: 12px;
      color: #909399;
    }
  }

  .tree-footer {
    padding: 8px 0;
  }
}
</style>
