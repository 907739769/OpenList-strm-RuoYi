<template>
  <div class="page-container">
    <!-- 搜索 -->
    <el-card class="search-card" v-if="showSearch">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="mediaType">
          <el-select v-model="queryParams.mediaType" placeholder="类型" clearable :style="{ width: '120px' }">
            <el-option label="剧集" value="TV" />
            <el-option label="电影" value="MOVIE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="状态" clearable :style="{ width: '130px' }">
            <el-option label="订阅中" value="ACTIVE" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已暂停" value="PAUSED" />
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

    <!-- 列表 -->
    <el-card class="table-card">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openSubscribeDialog">
            <el-icon><Plus /></el-icon> 新增订阅
          </el-button>
        </div>
        <el-button text @click="showSearch = !showSearch">
          <el-icon><Filter /></el-icon>
          {{ showSearch ? '隐藏搜索' : '显示搜索' }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="taskList" class="modern-table">
        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.title }}
            <span v-if="scope.row.year" class="sub-year">({{ scope.row.year }})</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            {{ scope.row.mediaType === 'MOVIE' ? '电影' : '剧集' }}
          </template>
        </el-table-column>
        <el-table-column label="季" width="70" align="center">
          <template #default="scope">
            {{ scope.row.mediaType === 'MOVIE' ? '-' : 'S' + scope.row.season }}
          </template>
        </el-table-column>
        <el-table-column label="总集数" prop="totalEpisodes" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">订阅中</el-tag>
            <el-tag v-else-if="scope.row.status === 'COMPLETED'" type="info">已完成</el-tag>
            <el-tag v-else type="warning">已暂停</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次命中" prop="lastMatchTime" width="170" align="center">
          <template #default="scope">
            {{ scope.row.lastMatchTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="300" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showProgress(scope.row)">进度</el-button>
            <el-button link type="primary" @click="handleRefresh(scope.row)">对账</el-button>
            <el-button v-if="scope.row.status !== 'PAUSED'" link type="warning" @click="handlePause(scope.row)">暂停</el-button>
            <el-button v-else link type="success" @click="handleResume(scope.row)">恢复</el-button>
            <el-button link type="danger" @click="handleRemove(scope.row)">删除</el-button>
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

    <!-- 新增订阅：TMDb 选片 -->
    <el-dialog v-model="subscribeOpen" title="新增订阅" width="720px" append-to-body class="modern-dialog">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="searchForm.mediaType" :style="{ width: '110px' }">
            <el-option label="剧集" value="TV" />
            <el-option label="电影" value="MOVIE" />
          </el-select>
        </el-form-item>
        <el-form-item label="片名">
          <el-input v-model="searchForm.keyword" placeholder="输入片名后回车" :style="{ width: '280px' }" @keyup.enter="doSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searchLoading" @click="doSearch">搜索 TMDb</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="searchLoading"
        :data="searchResults"
        height="300"
        highlight-current-row
        @current-change="pick"
      >
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.title }}
            <span v-if="scope.row.originalTitle && scope.row.originalTitle !== scope.row.title" class="sub-year">
              / {{ scope.row.originalTitle }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="年份" prop="year" width="80" align="center">
          <template #default="scope">{{ scope.row.year || '-' }}</template>
        </el-table-column>
        <el-table-column label="TMDb ID" prop="tmdbId" width="100" align="center" />
      </el-table>

      <div v-if="picked" class="picked-bar">
        已选：<strong>{{ picked.title }}</strong>
        <template v-if="searchForm.mediaType !== 'MOVIE'">
          &nbsp;第
          <el-input-number v-model="pickedSeason" :min="0" :max="99" size="small" :style="{ width: '110px' }" />
          季
          <span class="sub-year">（第 0 季是特别篇）</span>
        </template>
      </div>

      <template #footer>
        <el-button @click="subscribeOpen = false">取消</el-button>
        <el-button type="primary" :loading="subscribeLoading" :disabled="!picked" @click="confirmSubscribe">
          订阅
        </el-button>
      </template>
    </el-dialog>

    <!-- 进度 -->
    <el-dialog v-model="progressOpen" title="订阅进度" width="520px" append-to-body class="modern-dialog">
      <div v-loading="progressLoading">
        <template v-if="progress">
          <p class="progress-title">{{ progress.title }}</p>
          <el-progress
            :percentage="progress.totalEpisodes ? Math.round((progress.inLibraryCount / progress.totalEpisodes) * 100) : 0"
          />
          <p>已入库 <strong>{{ progress.inLibraryCount }}</strong> / {{ progress.totalEpisodes }} 集</p>
          <p v-if="progress.inFlightCount">在途 {{ progress.inFlightCount }} 集（已推送下载器，尚未入库）</p>
          <p v-if="progress.missingEpisodes && progress.missingEpisodes.length">
            仍缺第 {{ progress.missingEpisodes.join('、') }} 集
          </p>
          <p v-else class="all-done">全部集已入库</p>
        </template>
      </div>
      <template #footer>
        <el-button @click="progressOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePtSubscription } from '@/composables/usePtSubscription'

const showSearch = ref(window.innerWidth >= 768)

const {
  taskList, loading, total, queryParams, getList, handleQuery, resetQuery, queryRef,
  subscribeOpen, searchLoading, subscribeLoading, searchResults, searchForm,
  picked, pickedSeason, openSubscribeDialog, doSearch, pick, confirmSubscribe,
  progressOpen, progressLoading, progress, showProgress,
  handleRefresh, handlePause, handleResume, handleRemove
} = usePtSubscription()
</script>

<style scoped>
.sub-year {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.picked-bar {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.progress-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.all-done {
  color: var(--el-color-success);
}
</style>
