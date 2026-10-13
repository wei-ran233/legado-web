<template>
  <div class="admin-sources">
    <el-card class="card">
      <template #header>
        <div class="header-row">
          <div>
            <b>书源管理与可用性监控</b>
            <span class="muted" style="margin-left: 8px">
              共 {{ sources.length }} 个，已上架 {{ enabledCount }} 个
            </span>
          </div>
          <div class="actions">
            <el-button type="primary" @click="importVisible = true">
              <el-icon><Upload /></el-icon>&nbsp;导入书源
            </el-button>
          </div>
        </div>
      </template>

      <!-- 分类快速筛选 Tab -->
      <div class="category-filter-bar">
        <el-radio-group v-model="selectedCatTab" size="default">
          <el-radio-button :value="-1">全部 ({{ sources.length }})</el-radio-button>
          <el-radio-button :value="0">📚 书籍 ({{ catCounts[0] || 0 }})</el-radio-button>
          <el-radio-button :value="1">🎵 音乐 ({{ catCounts[1] || 0 }})</el-radio-button>
          <el-radio-button :value="2">🎨 漫画 ({{ catCounts[2] || 0 }})</el-radio-button>
          <el-radio-button :value="4">🎬 视频 ({{ catCounts[4] || 0 }})</el-radio-button>
          <el-radio-button :value="3">📁 文件 ({{ catCounts[3] || 0 }})</el-radio-button>
        </el-radio-group>
      </div>

      <div class="toolbar">
        <el-input v-model="filter" placeholder="按名称 / 分组 / URL 过滤" clearable
          style="max-width: 260px" :prefix-icon="Search" />

        <el-select v-model="sortOption" placeholder="排序方式" size="default" style="width: 130px">
          <el-option value="default" label="默认排序" />
          <el-option value="scoreDesc" label="可用性最高" />
          <el-option value="scoreAsc" label="可用性最低" />
          <el-option value="respondFast" label="响应最快" />
        </el-select>

        <div class="spacer" />

        <!-- 批量修改分类 -->
        <el-dropdown trigger="click" @command="onBatchChangeType" :disabled="!selected.length">
          <el-button size="default" :disabled="!selected.length">
            批量改分类 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="0">设为 📚 书籍 (0)</el-dropdown-item>
              <el-dropdown-item :command="1">设为 🎵 音乐 (1)</el-dropdown-item>
              <el-dropdown-item :command="2">设为 🎨 漫画 (2)</el-dropdown-item>
              <el-dropdown-item :command="4">设为 🎬 视频 (4)</el-dropdown-item>
              <el-dropdown-item :command="3">设为 📁 文件 (3)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button size="default" :disabled="!selected.length" type="success"
          @click="onSetEnabled(true)">上架选中</el-button>
        <el-button size="default" :disabled="!selected.length" type="warning"
          @click="onSetEnabled(false)">下架选中</el-button>
        <el-button size="default" :disabled="!selected.length" type="danger"
          @click="onDelete">删除选中</el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="paginated"
        row-key="bookSourceUrl"
        size="default"
        v-loading="loading"
        @selection-change="(rows: AdminSourceRow[]) => selected = rows"
      >
        <el-table-column type="selection" :reserve-selection="true" width="42" />
        <el-table-column prop="bookSourceName" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="bookSourceGroup" label="分组" min-width="110" show-overflow-tooltip />

        <!-- 分类选择下拉 -->
        <el-table-column label="分类" width="115">
          <template #default="{ row }">
            <el-select v-model="row.bookSourceType" size="small" @change="onRowTypeChange(row)" style="width: 100px">
              <el-option :value="0" label="📚 书籍" />
              <el-option :value="1" label="🎵 音乐" />
              <el-option :value="2" label="🎨 漫画" />
              <el-option :value="4" label="🎬 视频" />
              <el-option :value="3" label="📁 文件" />
            </el-select>
          </template>
        </el-table-column>

        <!-- 可用性健康度评分与指标悬浮 -->
        <el-table-column label="可用性健康度" width="130">
          <template #default="{ row }">
            <el-tooltip placement="top" :show-after="200">
              <template #content>
                <div style="line-height: 1.6; font-size: 12px">
                  <div><b>书源健康指标详情：</b></div>
                  <div>综合可用性得分：<b>{{ row.availabilityScore ?? 80 }} 分</b></div>
                  <div>检索成功次数：{{ row.successCount ?? 0 }} 次</div>
                  <div>失败/超时次数：{{ row.failCount ?? 0 }} 次</div>
                  <div>最新章节解析：{{ row.hasLatestChapter ? '✅ 具备' : '⚪ 暂无' }}</div>
                  <div>文本纯净度：{{ row.isTextClean ? '✅ 纯净' : '⚠️ 含拦截/乱码' }}</div>
                  <div>正文获取能力：{{ row.canFetchArticle ? '✅ 正常' : '❌ 异常' }}</div>
                  <div>平均响应：{{ row.respondTime > 0 ? (row.respondTime + 'ms') : '未测' }}</div>
                </div>
              </template>
              <el-tag
                :type="getScoreTagType(row.availabilityScore ?? 80)"
                size="small"
                effect="light"
                class="score-badge"
              >
                {{ row.availabilityScore ?? 80 }} 分
                <span v-if="(row.availabilityScore ?? 80) >= 85">· 优</span>
                <span v-else-if="(row.availabilityScore ?? 80) >= 60">· 良</span>
                <span v-else>· 差</span>
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="响应" width="80">
          <template #default="{ row }">
            <span :class="{ 'fast-time': row.respondTime > 0 && row.respondTime < 2000, 'slow-time': row.respondTime > 5000 }">
              {{ row.respondTime > 0 ? Math.round(row.respondTime / 100) / 10 + 's' : '-' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="plain">
              {{ row.enabled ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.enabled ? 'warning' : 'success'" link
              @click="onToggle(row)">
              {{ row.enabled ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页控制栏 -->
      <div class="pagination-bar">
        <div class="selection-info">
          <span v-if="selected.length > 0" class="selected-text">
            已跨页勾选 <b>{{ selected.length }}</b> 项
            <el-button link type="primary" size="small" @click="clearSelection">清空已选</el-button>
          </span>
          <span v-else class="muted">
            共 {{ filtered.length }} 条（当前第 {{ page }} / {{ maxPage }} 页）
          </span>
        </div>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[15, 25, 50, 100, 200]"
          :total="filtered.length"
          background
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>

    <!-- 导入对话框 -->
    <el-dialog v-model="importVisible" title="导入书源" width="640px">
      <el-alert type="info" :closable="false"
        title="粘贴书源 JSON（单个对象或数组），或选择本地 JSON 文件，系统将自动识别多媒体类型并配置可用性" style="margin-bottom: 12px" />
      <el-input v-model="importText" type="textarea" :rows="10"
        placeholder='[{"bookSourceName": "示例源", "bookSourceUrl": "https://...", ...}]' />
      <div style="margin-top: 8px">
        <input type="file" accept=".json,.txt" @change="onFilePick" />
      </div>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="onImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Upload, ArrowDown } from '@element-plus/icons-vue'
import {
  listAdminSources, importSources, setSourcesEnabled, deleteSources, updateSourceType,
  type AdminSourceRow,
} from '@/api/admin'

const sources = ref<AdminSourceRow[]>([])
const selected = ref<AdminSourceRow[]>([])
const filter = ref('')
const selectedCatTab = ref<number>(-1)
const sortOption = ref<'default' | 'scoreDesc' | 'scoreAsc' | 'respondFast'>('default')
const loading = ref(false)
const importVisible = ref(false)
const importText = ref('')
const importing = ref(false)

// 分页控制
const page = ref(1)
const pageSize = ref(25)
const tableRef = ref<any>(null)

const enabledCount = computed(() => sources.value.filter(s => s.enabled).length)

const catCounts = computed(() => {
  const counts: Record<number, number> = {}
  for (const s of sources.value) {
    const t = s.bookSourceType ?? 0
    counts[t] = (counts[t] || 0) + 1
  }
  return counts
})

const getScoreTagType = (score: number) => {
  if (score >= 85) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

const filtered = computed(() => {
  let list = sources.value

  // 1. 分类筛选
  if (selectedCatTab.value !== -1) {
    list = list.filter(s => (s.bookSourceType ?? 0) === selectedCatTab.value)
  }

  // 2. 文本过滤
  const kw = filter.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(s =>
      s.bookSourceName.toLowerCase().includes(kw) ||
      (s.bookSourceGroup || '').toLowerCase().includes(kw) ||
      s.bookSourceUrl.toLowerCase().includes(kw),
    )
  }

  // 3. 排序
  const result = [...list]
  if (sortOption.value === 'scoreDesc') {
    result.sort((a, b) => (b.availabilityScore ?? 80) - (a.availabilityScore ?? 80))
  } else if (sortOption.value === 'scoreAsc') {
    result.sort((a, b) => (a.availabilityScore ?? 80) - (b.availabilityScore ?? 80))
  } else if (sortOption.value === 'respondFast') {
    result.sort((a, b) => {
      const ta = a.respondTime > 0 ? a.respondTime : 999999
      const tb = b.respondTime > 0 ? b.respondTime : 999999
      return ta - tb
    })
  }

  return result
})

const maxPage = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))

const paginated = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

watch([filter, selectedCatTab, sortOption], () => {
  page.value = 1
})

watch(filtered, (newVal) => {
  const m = Math.max(1, Math.ceil(newVal.length / pageSize.value))
  if (page.value > m) {
    page.value = m
  }
})

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selected.value = []
}

const load = async () => {
  loading.value = true
  try {
    sources.value = await listAdminSources()
  } finally {
    loading.value = false
  }
}

onMounted(load)

const onFilePick = (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  file.text().then(t => (importText.value = t))
}

const onImport = async () => {
  importing.value = true
  try {
    const res = await importSources(importText.value)
    if (res.isSuccess) {
      ElMessage.success(`成功导入 ${res.data.imported} 个书源`)
      importVisible.value = false
      importText.value = ''
      load()
    } else {
      ElMessage.error(res.errorMsg || '导入失败')
    }
  } finally {
    importing.value = false
  }
}

const onRowTypeChange = async (row: AdminSourceRow) => {
  try {
    const res = await updateSourceType([row.bookSourceUrl], row.bookSourceType)
    if (res.isSuccess) {
      ElMessage.success(`已更新《${row.bookSourceName}》分类`)
    } else {
      ElMessage.error(res.errorMsg || '修改分类失败')
    }
  } catch {
    ElMessage.error('修改分类失败')
  }
}

const onBatchChangeType = async (type: number) => {
  const urls = selected.value.map(s => s.bookSourceUrl)
  if (!urls.length) return
  try {
    const res = await updateSourceType(urls, type)
    if (res.isSuccess) {
      ElMessage.success(`已将选中的 ${urls.length} 个书源分类修改为目标类型`)
      for (const s of selected.value) {
        s.bookSourceType = type
      }
    } else {
      ElMessage.error(res.errorMsg || '批量修改分类失败')
    }
  } catch {
    ElMessage.error('批量修改分类失败')
  }
}

const onToggle = async (row: AdminSourceRow) => {
  const res = await setSourcesEnabled([row.bookSourceUrl], !row.enabled)
  if (res.isSuccess) {
    row.enabled = !row.enabled
    ElMessage.success(row.enabled ? '已上架' : '已下架')
  } else {
    ElMessage.error(res.errorMsg || '操作失败')
  }
}

const onSetEnabled = async (enabled: boolean) => {
  const urls = selected.value.map(s => s.bookSourceUrl)
  const res = await setSourcesEnabled(urls, enabled)
  if (res.isSuccess) {
    ElMessage.success(`已${enabled ? '上架' : '下架'} ${urls.length} 个书源`)
    load()
  } else {
    ElMessage.error(res.errorMsg || '操作失败')
  }
}

const onDelete = async () => {
  const urls = selected.value.map(s => s.bookSourceUrl)
  await ElMessageBox.confirm(
    `确定删除选中的 ${urls.length} 个书源？此操作不可恢复`,
    '删除书源',
    { type: 'warning' },
  )
  const res = await deleteSources(urls)
  if (res.isSuccess) {
    ElMessage.success('已删除')
    clearSelection()
    load()
  } else {
    ElMessage.error(res.errorMsg || '删除失败')
  }
}
</script>

<style scoped>
.admin-sources {
  padding: 16px;
  max-width: 1360px;
  margin: 0 auto;
}

.card {
  border-radius: 10px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.category-filter-bar {
  margin-bottom: 12px;
  overflow-x: auto;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.spacer {
  flex: 1;
}

.score-badge {
  cursor: help;
  font-weight: 500;
}

.fast-time {
  color: var(--el-color-success);
  font-weight: bold;
}

.slow-time {
  color: var(--el-color-warning);
}

.muted {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.selection-info {
  font-size: 13px;
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-text b {
  color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .admin-sources {
    padding: 8px;
  }
  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
