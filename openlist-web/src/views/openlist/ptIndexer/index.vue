<template>
  <div class="page-container">
    <!-- Search Panel -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="状态" clearable :style="{ width: '120px' }">
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button @click="resetQuery">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table Card -->
    <el-card class="table-card">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="handleAdd('新增索引器')">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button type="success" :disabled="single" @click="handleUpdate(undefined, '修改索引器')">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button type="danger" :disabled="multiple" @click="handleDelete(undefined, `是否确认删除编号为“${selectedIds}”的索引器？`)">
            <el-icon><Delete /></el-icon> 批量删除
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange" class="modern-table">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="接口地址" prop="url" min-width="280" show-overflow-tooltip />
        <el-table-column label="分类" prop="categories" width="140" align="center">
          <template #default="scope">
            {{ scope.row.categories || '不限' }}
          </template>
        </el-table-column>
        <el-table-column label="轮询周期" prop="pollInterval" width="100" align="center">
          <template #default="scope">
            {{ scope.row.pollInterval }} 秒
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === '1' ? 'success' : 'danger'">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次轮询" prop="lastPollTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastPollTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="上次结果" prop="lastStatus" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="!scope.row.lastStatus">-</span>
            <el-tag v-else-if="scope.row.lastStatus === 'OK'" type="success">正常</el-tag>
            <el-tag v-else type="danger">{{ scope.row.lastStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row, '修改索引器')">
              <el-icon><Edit /></el-icon> 修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="getList"
          @size-change="getList"
        />
      </div>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="open" :title="dialogTitle" width="600px" append-to-body class="modern-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="接口地址" prop="url">
          <el-input v-model="form.url" placeholder="如 http://jackett:9117/api/v2.0/indexers/xxx/results/torznab/api" />
        </el-form-item>
        <el-form-item label="apikey" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 Torznab apikey" />
        </el-form-item>
        <el-form-item label="分类" prop="categories">
          <el-input v-model="form.categories" placeholder="逗号分隔的分类 ID，如 5000,5030；留空表示不限" />
        </el-form-item>
        <el-form-item label="轮询周期" prop="pollInterval">
          <el-input-number v-model="form.pollInterval" :min="60" :step="60" :style="{ width: '200px' }" />
          <span class="form-tip">秒</span>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="testLoading" @click="handleTest">测试连接</el-button>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePtIndexer } from '@/composables/usePtIndexer'

const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  selectedIds, single, multiple, handleSelectionChange,
  open, dialogTitle, submitLoading, formRef, form, rules,
  handleAdd, handleUpdate, submitForm, handleDelete,
  testLoading, handleTest
} = usePtIndexer()
</script>
