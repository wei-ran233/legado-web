<template>
  <div class="search-view">
    <!-- 多媒体分类选择器 -->
    <div class="category-tabs-bar">
      <div
        v-for="cat in categoryOptions"
        :key="cat.value"
        class="cat-tab-chip"
        :class="{ active: selectedCategory === cat.value }"
        @click="onSelectCategory(cat.value)"
      >
        <span class="cat-icon">{{ cat.icon }}</span>
        <span class="cat-label">{{ cat.label }}</span>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="keyword"
        size="large"
        :placeholder="searchPlaceholder"
        :prefix-icon="Search"
        clearable
        @keyup.enter="doSearch"
      >
        <template #append>
          <el-button :loading="searching" @click="doSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- 搜索状态与模式切换栏 -->
    <div class="toolbar-line" v-if="searching || finished || aggregatedList.length > 0">
      <div class="status-info">
        <template v-if="searching">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>
            正在搜索书源 <b>{{ doneSources }}</b> / {{ totalSources || '?' }} 个，已聚合 <b>{{ aggregatedCount }}</b> 本书籍…
          </span>
          <el-button size="small" type="danger" text @click="stopSearch" class="stop-btn">停止</el-button>
        </template>
        <template v-else-if="finished">
          <span>搜索完成，扫描 <b>{{ totalSources }}</b> 个书源，已聚合 <b>{{ aggregatedCount }}</b> 本书籍</span>
        </template>
      </div>

      <div class="toolbar-controls">
        <el-checkbox v-model="precision" @change="onPrecisionChange" size="small" class="precision-check">
          精准搜索
        </el-checkbox>

        <el-select v-model="sortBy" size="small" style="width: 105px" class="sort-select">
          <el-option value="smart" label="综合排序" />
          <el-option value="sources" label="书源最多" />
          <el-option value="latest" label="最新章节" />
        </el-select>

        <div class="view-switch">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="aggregate">聚合模式 ({{ aggregatedCount }})</el-radio-button>
            <el-radio-button value="flat">平铺模式 ({{ filteredRawResults.length }})</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <el-progress
      v-if="searching && totalSources > 0"
      :percentage="Math.min(100, Math.round((doneSources / totalSources) * 100))"
      :stroke-width="3"
      :show-text="false"
      class="search-progress"
    />

    <!-- 聚合视图 -->
    <div v-if="viewMode === 'aggregate'" class="result-list" v-loading="searching && aggregatedList.length === 0">
      <el-empty
        v-if="!searching && aggregatedList.length === 0 && finished"
        description="没有找到匹配结果，可尝试关闭“精准搜索”或换个书名/作者试试"
      />

      <div v-for="book in aggregatedList" :key="book.key" class="result-item">
        <img
          class="cover"
          :src="proxyCover(book.coverUrl)"
          loading="lazy"
          @error="($event.target as HTMLImageElement).src = fallbackCover"
        />
        <div class="info">
          <div class="title-row">
            <span class="title" @click="startReading(book.sources[0].rawBook, book)">{{ book.name }}</span>
            <el-tag
              v-if="book.type !== undefined && book.type !== 0"
              size="small"
              type="warning"
              effect="plain"
              class="media-type-tag"
            >
              {{ mediaTypeLabel(book.type) }}
            </el-tag>
            <el-tag
              v-if="isExactMatch(book)"
              size="small"
              type="danger"
              effect="plain"
              class="exact-match-tag"
            >
              精确匹配
            </el-tag>
            <span class="author">{{ book.author }}</span>
            <el-tag size="small" type="success" effect="plain" class="source-count-tag" @click="openSourceDrawer(book)">
              {{ book.sources.length }} 个书源
            </el-tag>
          </div>

          <div class="meta-row">
            <el-tag
              v-if="book.kind"
              size="small"
              effect="plain"
              v-for="k in book.kind.split(',').slice(0, 3)"
              :key="k"
            >
              {{ k }}
            </el-tag>
            <el-tag
              size="small"
              :type="hasValidChapter(book.latestChapter) ? 'success' : 'info'"
              effect="plain"
              class="chapter-status-tag"
            >
              {{ hasValidChapter(book.latestChapter) ? '有最新章节' : '无最新章节' }}
            </el-tag>
            <span class="latest-chapter" v-if="hasValidChapter(book.latestChapter)" :title="book.latestChapter">
              最新：{{ book.latestChapter }}
              <span v-if="book.latestChapterSource" class="latest-source-hint">（{{ book.latestChapterSource }}）</span>
            </span>
          </div>

          <div class="intro">{{ book.intro || '暂无简介' }}</div>

          <div class="source-preview">
            <span class="muted-label">可用源：</span>
            <el-tag
              v-for="src in book.sources.slice(0, 4)"
              :key="src.origin"
              size="small"
              type="info"
              effect="light"
              class="origin-chip"
              @click="openSourceDrawer(book)"
            >
              {{ src.originName }}
            </el-tag>
            <span v-if="book.sources.length > 4" class="more-sources" @click="openSourceDrawer(book)">
              +{{ book.sources.length - 4 }}个源…
            </span>
          </div>
        </div>

        <div class="actions">
          <el-button
            type="primary"
            size="small"
            :loading="isAdding(book.sources[0].rawBook)"
            :disabled="isAdded(book.sources[0].rawBook)"
            @click="onAdd(book.sources[0].rawBook)"
          >
            {{ isAdded(book.sources[0].rawBook) ? '已加书架' : '加入书架' }}
          </el-button>
          <el-button size="small" @click="openSourceDrawer(book)">
            对比书源
          </el-button>
          <el-button
            type="success"
            plain
            size="small"
            :loading="readingLoading && readingTargetKey === keyOf(book.sources[0].rawBook)"
            @click="startReading(book.sources[0].rawBook, book)"
          >
            立即阅读
          </el-button>
        </div>
      </div>
    </div>

    <!-- 平铺视图 -->
    <div v-else class="result-list" v-loading="searching && filteredRawResults.length === 0">
      <el-empty
        v-if="!searching && filteredRawResults.length === 0 && finished"
        description="没有找到匹配结果，可尝试关闭“精准搜索”或换个关键字试试"
      />
      <div v-for="(item, idx) in filteredRawResults" :key="idx" class="result-item">
        <img
          class="cover"
          :src="proxyCover(item.coverUrl)"
          loading="lazy"
          @error="($event.target as HTMLImageElement).src = fallbackCover"
        />
        <div class="info">
          <div class="title-row">
            <span class="title" @click="startReading(item)">{{ item.name }}</span>
            <el-tag
              v-if="item.sourceType !== undefined && item.sourceType !== 0"
              size="small"
              type="warning"
              effect="plain"
              class="media-type-tag"
            >
              {{ mediaTypeLabel(item.sourceType) }}
            </el-tag>
            <span class="author">{{ item.author }}</span>
          </div>
          <div class="meta-row">
            <el-tag size="small" type="info" effect="plain">{{ item.originName }}</el-tag>
            <el-tag
              v-if="item.kind"
              size="small"
              effect="plain"
              v-for="k in item.kind.split(',').slice(0, 3)"
              :key="k"
            >
              {{ k }}
            </el-tag>
            <el-tag
              size="small"
              :type="hasValidChapter(item.latestChapter) ? 'success' : 'info'"
              effect="plain"
              class="chapter-status-tag"
            >
              {{ hasValidChapter(item.latestChapter) ? '有最新章节' : '无最新章节' }}
            </el-tag>
            <span class="latest-chapter" v-if="hasValidChapter(item.latestChapter)">
              最新：{{ item.latestChapter }}
            </span>
          </div>
          <div class="intro">{{ item.intro || item.latestChapter || '暂无简介' }}</div>
        </div>
        <div class="actions">
          <el-button
            type="primary"
            size="small"
            :loading="addingKeys.has(keyOf(item))"
            :disabled="isAdded(item)"
            @click="onAdd(item)"
          >
            {{ isAdded(item) ? '已加入' : '加入书架' }}
          </el-button>
          <el-button
            type="success"
            plain
            size="small"
            :loading="readingLoading && readingTargetKey === keyOf(item)"
            @click="startReading(item)"
          >
            阅读
          </el-button>
        </div>
      </div>
    </div>

    <!-- 查看该书各源对比抽屉 -->
    <el-drawer
      v-model="sourceDrawerVisible"
      :title="`《${selectedBook?.name || ''}》书源聚合列表`"
      :size="drawerSize"
      direction="rtl"
    >
      <div v-if="selectedBook" class="source-drawer-content">
        <div class="drawer-header-meta">
          <span>作者：{{ selectedBook.author }}</span>
          <el-tag size="small" type="success">{{ selectedBook.sources.length }} 个源可用</el-tag>
        </div>
        <el-divider style="margin: 12px 0" />

        <div class="source-card-list">
          <div v-for="(src, i) in selectedBook.sources" :key="i" class="source-sub-card">
            <div class="sub-card-top">
              <div class="src-title-group">
                <span class="src-name">{{ src.originName }}</span>
                <el-tag
                  v-if="hasValidChapter(src.latestChapter) && src.latestChapter === selectedBook.latestChapter"
                  size="small"
                  type="danger"
                  effect="dark"
                  class="source-badge"
                >
                  最新源
                </el-tag>
                <el-tag
                  v-else-if="hasValidChapter(src.latestChapter)"
                  size="small"
                  type="success"
                  effect="plain"
                  class="source-badge"
                >
                  有最新章节
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="info"
                  effect="plain"
                  class="source-badge"
                >
                  无最新章节
                </el-tag>
              </div>
              <div class="sub-card-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="addingKeys.has(keyOf(src.rawBook))"
                  :disabled="isAdded(src.rawBook)"
                  @click="onAdd(src.rawBook)"
                >
                  {{ isAdded(src.rawBook) ? '已在书架' : '加书架' }}
                </el-button>
                <el-button
                  size="small"
                  type="success"
                  plain
                  :loading="readingLoading && readingTargetKey === keyOf(src.rawBook)"
                  @click="startReading(src.rawBook, selectedBook || undefined)"
                >
                  阅读
                </el-button>
              </div>
            </div>
            <div class="sub-card-chapter" v-if="hasValidChapter(src.latestChapter)">
              <span class="muted-label">最新章节：</span>{{ src.latestChapter }}
            </div>
            <div class="sub-card-chapter empty-chapter" v-else>
              <span class="muted-label">最新章节：</span><span class="no-ch-hint">暂无最新章节记录</span>
            </div>
            <div class="sub-card-url">{{ src.bookUrl }}</div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SearchView',
})

import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import { Loading, Search } from '@element-plus/icons-vue'
import { searchStream, addToShelf, prepareBook, type SearchBookItem } from '@/api/library'

export interface AggregatedSourceItem {
  origin: string
  originName: string
  bookUrl: string
  latestChapter?: string | null
  intro?: string | null
  coverUrl?: string | null
  kind?: string | null
  rawBook: SearchBookItem
  sourceType?: number
  availabilityScore?: number
}

export interface AggregatedBook {
  key: string
  name: string
  author: string
  coverUrl: string
  intro: string
  kind: string
  latestChapter: string
  latestChapterSource?: string
  sources: AggregatedSourceItem[]
  type?: number
  availabilityScore?: number
}

const router = useRouter()
const keyword = ref('')
const currentSearchKey = ref('')
const searching = ref(false)
const finished = ref(false)
const totalSources = ref(0)
const doneSources = ref(0)
const precision = ref(true)
const sortBy = ref<'smart' | 'sources' | 'latest'>('smart')

// ===== 媒体分类（0: 书籍, 2: 漫画, 1: 音乐, 4: 视频, -1: 全部） =====
const selectedCategory = ref<number>(0)
const categoryOptions = [
  { value: 0, label: '书籍', icon: '📚' },
  { value: 2, label: '漫画', icon: '🎨' },
  { value: 1, label: '音乐', icon: '🎵' },
  { value: 4, label: '视频', icon: '🎬' },
  { value: -1, label: '全部', icon: '🌐' },
]

const searchPlaceholder = computed(() => {
  switch (selectedCategory.value) {
    case 2: return '搜索漫画名 / 作者，全网动漫图集聚合'
    case 1: return '搜索歌曲 / 歌手 / 有声书 / 广播电台'
    case 4: return '搜索影视 / 剧集 / 短剧 / 视频'
    case -1: return '全网全品类多媒体聚合检索（书籍/漫画/音乐/视频）'
    default: return '搜索书名 / 作者，全网小说聚合'
  }
})

const mediaTypeLabel = (type?: number) => {
  if (!type) return '书籍'
  if (type === 1 || type === 32 || (type & 32) === 32) return '音乐'
  if (type === 2 || type === 64 || (type & 64) === 64) return '漫画'
  if (type === 3 || type === 128 || (type & 128) === 128) return '网盘'
  if (type === 4 || (type & 4) === 4) return '视频'
  return '书籍'
}

const getScoreTagType = (score?: number) => {
  if (score === undefined) return 'info'
  if (score >= 85) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

export interface CategorySearchSnapshot {
  category: number
  keyword: string
  currentSearchKey: string
  totalSources: number
  doneSources: number
  finished: boolean
  searching: boolean
  aggregatedEntries: [string, AggregatedBook][]
  rawResults: SearchBookItem[]
}

const rawResults = ref<SearchBookItem[]>([])
const aggregatedMap = ref<Map<string, AggregatedBook>>(new Map())
const viewMode = ref<'aggregate' | 'flat'>('aggregate')

const addingKeys = ref(new Set<string>())
const addedKeys = ref(new Set<string>())
const readingLoading = ref(false)
const readingTargetKey = ref('')

const sourceDrawerVisible = ref(false)
const selectedBook = ref<AggregatedBook | null>(null)

// ===== 多分类检索缓存（支持书籍/漫画/音乐/视频分类切换秒开与页面刷新恢复） =====
const SEARCH_CACHE_KEY = 'legado_search_cache'
const MULTI_CAT_CACHE_KEY = 'legado_category_search_cache'
const categoryCache = ref<Map<string, CategorySearchSnapshot>>(new Map())

const getCategoryCacheKey = (cat: number, kw: string) => `${cat}__${cleanTitle(kw).toLowerCase()}`

function saveCurrentToCategoryCache() {
  const kw = keyword.value.trim()
  if (!kw && !currentSearchKey.value) return
  const effectiveKw = currentSearchKey.value.trim() || kw
  const key = getCategoryCacheKey(selectedCategory.value, effectiveKw)
  categoryCache.value.set(key, {
    category: selectedCategory.value,
    keyword: effectiveKw,
    currentSearchKey: currentSearchKey.value,
    totalSources: totalSources.value,
    doneSources: doneSources.value,
    finished: finished.value,
    searching: searching.value,
    aggregatedEntries: Array.from(aggregatedMap.value.entries()),
    rawResults: rawResults.value.slice(0, 500),
  })
  persistMultiCatCache()
}

function persistMultiCatCache() {
  try {
    const list = Array.from(categoryCache.value.entries()).slice(-15)
    sessionStorage.setItem(MULTI_CAT_CACHE_KEY, JSON.stringify(list))
  } catch {}
}

function restoreMultiCatCache() {
  try {
    const raw = sessionStorage.getItem(MULTI_CAT_CACHE_KEY)
    if (!raw) return
    const list = JSON.parse(raw)
    if (Array.isArray(list)) {
      categoryCache.value = new Map(list)
    }
  } catch {}
}

function saveSearchCache() {
  try {
    saveCurrentToCategoryCache()
    if (!currentSearchKey.value && aggregatedMap.value.size === 0) return
    const data = {
      keyword: keyword.value,
      currentSearchKey: currentSearchKey.value,
      totalSources: totalSources.value,
      doneSources: doneSources.value,
      finished: finished.value,
      precision: precision.value,
      sortBy: sortBy.value,
      viewMode: viewMode.value,
      selectedCategory: selectedCategory.value,
      aggregatedEntries: Array.from(aggregatedMap.value.entries()),
      rawResults: rawResults.value.slice(0, 500),
    }
    sessionStorage.setItem(SEARCH_CACHE_KEY, JSON.stringify(data))
  } catch {
    /* ignore storage quota */
  }
}

function restoreSearchCache() {
  try {
    restoreMultiCatCache()
    const raw = sessionStorage.getItem(SEARCH_CACHE_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (data && data.currentSearchKey) {
      keyword.value = data.keyword || ''
      currentSearchKey.value = data.currentSearchKey || ''
      totalSources.value = data.totalSources || 0
      doneSources.value = data.doneSources || 0
      finished.value = data.finished ?? true
      precision.value = data.precision ?? true
      sortBy.value = data.sortBy || 'smart'
      viewMode.value = data.viewMode || 'aggregate'
      if (data.selectedCategory !== undefined) {
        selectedCategory.value = data.selectedCategory
      }
      if (Array.isArray(data.aggregatedEntries)) {
        aggregatedMap.value = new Map(data.aggregatedEntries)
      }
      if (Array.isArray(data.rawResults)) {
        rawResults.value = data.rawResults
      }
    }
  } catch {
    /* ignore */
  }
}

restoreSearchCache()
watch([precision, sortBy, viewMode], () => saveSearchCache())

const onSelectCategory = (val: number) => {
  if (selectedCategory.value === val) return
  const oldVal = selectedCategory.value

  // 1. 保存旧分类当前检索状态到分类缓存池
  saveCurrentToCategoryCache()

  // 2. 终止旧分类的搜索网络流，避免在后台竞争
  if (searching.value) {
    controller?.abort()
    searching.value = false
    const oldKey = getCategoryCacheKey(oldVal, currentSearchKey.value || keyword.value)
    const oldSnap = categoryCache.value.get(oldKey)
    if (oldSnap) {
      oldSnap.searching = false
      oldSnap.finished = true
    }
  }

  selectedCategory.value = val

  // 3. 优先检查目标分类是否已有当前关键词的缓存快照
  const curKw = keyword.value.trim()
  if (curKw) {
    const targetKey = getCategoryCacheKey(val, curKw)
    const cachedSnap = categoryCache.value.get(targetKey)
    if (cachedSnap && cachedSnap.aggregatedEntries.length > 0) {
      // 0ms 瞬间恢复该分类上次搜索结果，无闪烁
      keyword.value = cachedSnap.keyword
      currentSearchKey.value = cachedSnap.currentSearchKey
      totalSources.value = cachedSnap.totalSources
      doneSources.value = cachedSnap.doneSources
      finished.value = cachedSnap.finished
      searching.value = cachedSnap.searching
      aggregatedMap.value = new Map(cachedSnap.aggregatedEntries)
      rawResults.value = [...cachedSnap.rawResults]
      saveSearchCache()
      return
    }

    // 未命中该分类的关键词缓存，触发针对该分类的新搜索
    doSearch()
  } else {
    aggregatedMap.value = new Map()
    rawResults.value = []
    totalSources.value = 0
    doneSources.value = 0
    finished.value = false
    searching.value = false
    currentSearchKey.value = ''
    saveSearchCache()
  }
}

const fallbackCover =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="90" height="126"><rect width="100%" height="100%" fill="#e8eee9"/><text x="50%" y="52%" font-size="34" text-anchor="middle" fill="#6b8a76">📖</text></svg>',
  )

const cleanTitle = (s?: string | null) =>
  (s || '')
    .replace(/^[《〈【〔(\[]+|[》〉】〕)\]]+$/g, '')
    .trim()

const cleanAuthor = (s?: string | null) =>
  (s || '')
    .replace(/^(作\s*者|著)\s*[:：]\s*/, '')
    .trim()

/**
 * 规范化书名（去除前后括号、书名号、空格，以及常见附加修饰词如 [漫画]、(漫画版)、[网游] 等）
 */
const normalizeTitle = (s?: string | null): string => {
  if (!s) return ''
  return s
    .trim()
    .replace(/^[《〈【〔(\[]+|[》〉】〕)\]]+$/g, '')
    .replace(/[\(（\[【](?:漫画|漫画版|有声|精修|精校|网络版|连载|全集|全本|完结|同人|改编)[\)）\]】]/gi, '')
    .replace(/\s*(?:漫画版|有声版|网游版)$/gi, '')
    .trim()
    .toLowerCase()
}

/**
 * 判断作者是否为无意义占位符、空值、连载状态或与书名重复
 */
const isPlaceholderAuthor = (author?: string | null, bookName?: string | null): boolean => {
  if (!author) return true
  const a = author.trim().toLowerCase()
  if (!a || a === 'null' || a === 'undefined' || a === '空' || a === '未知' || a === '暂无' || a === '佚名' || a === '连载' || a === '连载中' || a === '完结') {
    return true
  }
  if (bookName) {
    const normName = normalizeTitle(bookName)
    if (a === normName || normalizeTitle(a) === normName) {
      return true
    }
  }
  return false
}

const studioKeywords = /(漫画|工作室|文化|动漫|阅文|腾讯|快看|角川|汉化|制作|哔哩|爱奇艺|极光|漫客|网易)/i

/**
 * 智能判断两个书源结果是否应该合并至同一本聚合书籍：
 * 解决用户反馈的同一本书（如《超神机械师》）因作者分别为“鲜漫文化”、“空”、“超神机械师”、“阅文漫画”而被切分为多个选项的问题
 */
const shouldMergeBook = (
  existing: AggregatedBook,
  incoming: SearchBookItem,
  currentCategory: number
): boolean => {
  const normTitle1 = normalizeTitle(existing.name)
  const normTitle2 = normalizeTitle(incoming.name)
  if (normTitle1 !== normTitle2) return false

  const auth1 = cleanAuthor(existing.author)
  const auth2 = cleanAuthor(incoming.author)

  // 1. 任一方作者是占位符、空或等于书名 -> 100% 同名合并
  if (isPlaceholderAuthor(auth1, existing.name) || isPlaceholderAuthor(auth2, incoming.name)) {
    return true
  }

  // 2. 作者字符串相同或存在包含关系（例如 "齐佩甲" 与 "齐佩甲/鲜漫文化"）
  const a1 = auth1.toLowerCase()
  const a2 = auth2.toLowerCase()
  if (a1 === a2 || a1.includes(a2) || a2.includes(a1)) {
    return true
  }

  // 3. 漫画场景（或作者包含制作团队/发行平台特征词）：同名直接聚合
  const isComic = currentCategory === 2 || existing.type === 2 || incoming.type === 2 || (incoming as any).sourceType === 2
  if (isComic || studioKeywords.test(auth1) || studioKeywords.test(auth2)) {
    return true
  }

  // 4. 最新章节比对：若最新章节一致或章节序号相同
  if (hasValidChapter(existing.latestChapter) && hasValidChapter(incoming.latestChapter)) {
    if (existing.latestChapter.trim() === incoming.latestChapter.trim()) {
      return true
    }
    const num1 = extractChapterNum(existing.latestChapter)
    const num2 = extractChapterNum(incoming.latestChapter)
    if (num1 > 0 && num1 === num2) {
      return true
    }
  }

  return false
}

const keyOf = (item: { name: string; author: string }) =>
  `${cleanTitle(item.name).toLowerCase()}__${cleanAuthor(item.author).toLowerCase()}`

const isAdded = (item: SearchBookItem) => addedKeys.value.has(keyOf(item)) || addedKeys.value.has(item.bookUrl)
const isAdding = (item: SearchBookItem) => addingKeys.value.has(keyOf(item)) || addingKeys.value.has(item.bookUrl)

const proxyCover = (url?: string | null) => {
  if (!url) return ''
  return `/api/cover?path=${encodeURIComponent(url)}`
}

const hasValidChapter = (ch?: string | null): ch is string => {
  if (!ch) return false
  const s = ch.trim()
  if (!s || s === 'null' || s === 'undefined' || s === '暂无' || s === '无' || s === '暂无最新章节') return false
  return true
}

/**
 * 提取章节序号或特征，用于比对最新章节的新鲜度
 */
const extractChapterNum = (title?: string | null): number => {
  if (!title) return -1
  const m = title.match(/第\s*([0-9]+)\s*[章节回卷]/)
  if (m) {
    const n = parseInt(m[1], 10)
    if (!isNaN(n)) return n
  }
  return -1
}

/**
 * 评估书源质量分数：
 * 1. 有无最新章节：+1000 分（原版 App 核心逻辑：有最新章节代表爬虫规则有效且正常工作）
 * 2. 是否命中最新章节：+500 分
 * 3. 协议安全性：HTTPS 优先（+50 分），避免 HTTP 流意外截断（如 api.lfdapengu.com）
 * 4. 封面与简介完整度：+20 分
 * 5. 章节标题详细度（长度）：+0~50 分
 */
const getSourceScore = (src: AggregatedSourceItem, bestChapter?: string): number => {
  let score = 0
  if (hasValidChapter(src.latestChapter)) {
    score += 1000
    if (bestChapter && src.latestChapter?.trim() === bestChapter.trim()) {
      score += 500
    }
    score += Math.min((src.latestChapter || '').length, 50)
  }
  if (src.bookUrl.startsWith('https://')) {
    score += 50
  }
  if (src.coverUrl) score += 10
  if (src.intro && src.intro.length > 20) score += 10
  return score
}

const sortSources = (sources: AggregatedSourceItem[], bestChapter?: string) => {
  sources.sort((a, b) => getSourceScore(b, bestChapter) - getSourceScore(a, bestChapter))
}

/**
 * 还原原版 Legado App 搜索相关度分层体系：
 * Tier 1 (equalData): 书名或作者完全精确匹配
 * Tier 2: 书名或作者以关键词为前缀
 * Tier 3 (containsData): 书名或作者包含关键词
 * Tier 4 (tagsData): 标签/分类包含关键词
 * Tier 5 (otherData): 其他弱相关/推荐书籍
 */
const getBookTier = (book: { name: string; author: string; kind?: string | null }, kw: string): number => {
  if (!kw) return 1
  const cleanKw = cleanTitle(kw).toLowerCase()
  const name = cleanTitle(book.name).toLowerCase()
  const author = cleanAuthor(book.author).toLowerCase()
  const kind = (book.kind || '').toLowerCase()

  if (name === cleanKw || author === cleanKw) return 1
  if (name.startsWith(cleanKw) || author.startsWith(cleanKw)) return 2
  if (name.includes(cleanKw) || author.includes(cleanKw)) return 3
  if (kind.includes(cleanKw)) return 4
  return 5
}

const isExactMatch = (book: AggregatedBook) => {
  return !!currentSearchKey.value && getBookTier(book, currentSearchKey.value) === 1
}

const aggregatedList = computed(() => {
  const kw = currentSearchKey.value.trim()
  let list = Array.from(aggregatedMap.value.values())

  if (precision.value && kw) {
    list = list.filter(b => getBookTier(b, kw) <= 4)
  }

  if (sortBy.value === 'smart') {
    // 智能综合排序（原版 App 策略）：分层优先，层内按聚合书源数量倒序排列
    list.sort((a, b) => {
      const tierA = getBookTier(a, kw)
      const tierB = getBookTier(b, kw)
      if (tierA !== tierB) return tierA - tierB
      if (b.sources.length !== a.sources.length) return b.sources.length - a.sources.length
      return a.name.length - b.name.length
    })
  } else if (sortBy.value === 'sources') {
    // 书源数量优先
    list.sort((a, b) => {
      if (b.sources.length !== a.sources.length) return b.sources.length - a.sources.length
      return getBookTier(a, kw) - getBookTier(b, kw)
    })
  } else if (sortBy.value === 'latest') {
    // 最新章节优先
    list.sort((a, b) => {
      const hasA = a.latestChapter ? 1 : 0
      const hasB = b.latestChapter ? 1 : 0
      if (hasB !== hasA) return hasB - hasA
      return b.sources.length - a.sources.length
    })
  }

  return list
})

const aggregatedCount = computed(() => aggregatedList.value.length)

const filteredRawResults = computed(() => {
  const kw = currentSearchKey.value.trim()
  let list = [...rawResults.value]

  if (precision.value && kw) {
    list = list.filter(b => getBookTier(b, kw) <= 4)
  }

  if (kw) {
    list.sort((a, b) => {
      const tierA = getBookTier(a, kw)
      const tierB = getBookTier(b, kw)
      if (tierA !== tierB) return tierA - tierB
      return a.name.length - b.name.length
    })
  }

  return list
})

let controller: AbortController | null = null

const stopSearch = () => {
  controller?.abort()
  searching.value = false
  finished.value = true
  saveSearchCache()
}

const onPrecisionChange = () => {
  if (keyword.value.trim()) {
    doSearch()
  }
}

const doSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  controller?.abort()

  currentSearchKey.value = kw
  rawResults.value = []
  aggregatedMap.value = new Map()
  doneSources.value = 0
  totalSources.value = 0
  finished.value = false
  searching.value = true
  saveSearchCache()

  controller = searchStream(
    kw,
    {
      onStart: count => {
        totalSources.value = count
      },
      onBooks: (originName, books) => {
        doneSources.value++
        for (const b of books) {
          b.originName = originName
          rawResults.value.push(b)

          const k = keyOf(b)
          // 查找同名兼容聚合项（解决漫画或改编因各源作者字段不一，导致同一本书被切分为多个同名选项的问题）
          let existing = aggregatedMap.value.get(k)
          if (!existing) {
            for (const cand of aggregatedMap.value.values()) {
              if (shouldMergeBook(cand, b, selectedCategory.value)) {
                existing = cand
                break
              }
            }
          }

          const sourceEntry: AggregatedSourceItem = {
            origin: b.origin,
            originName,
            bookUrl: b.bookUrl,
            latestChapter: b.latestChapter,
            intro: b.intro,
            coverUrl: b.coverUrl,
            kind: b.kind,
            rawBook: b,
            sourceType: b.sourceType ?? b.type,
            availabilityScore: b.availabilityScore,
          }

          if (existing) {
            // 作者字段智能择优：如果已有作者是占位符，而新源作者有效，提升显示为有效作者
            const existingPlaceholder = isPlaceholderAuthor(existing.author, existing.name)
            const newPlaceholder = isPlaceholderAuthor(b.author, b.name)
            if (existingPlaceholder && !newPlaceholder) {
              existing.author = b.author
            } else if (!existingPlaceholder && !newPlaceholder) {
              if (!existing.author.includes(b.author) && !b.author.includes(existing.author)) {
                if (b.author.length > existing.author.length && !studioKeywords.test(b.author)) {
                  existing.author = b.author
                }
              }
            }

            if (!existing.coverUrl && b.coverUrl) existing.coverUrl = b.coverUrl
            if ((!existing.intro || existing.intro.length < 20) && b.intro) existing.intro = b.intro
            if (!existing.kind && b.kind) existing.kind = b.kind
            if (b.type !== undefined && !existing.type) existing.type = b.type
            if (b.availabilityScore && (!existing.availabilityScore || b.availabilityScore > existing.availabilityScore)) {
              existing.availabilityScore = b.availabilityScore
            }

            // 智能评判并更新最新章节
            if (hasValidChapter(b.latestChapter)) {
              if (!hasValidChapter(existing.latestChapter)) {
                existing.latestChapter = b.latestChapter!
                existing.latestChapterSource = originName
              } else {
                const numOld = extractChapterNum(existing.latestChapter)
                const numNew = extractChapterNum(b.latestChapter)
                if (numNew > numOld || (numNew === numOld && (b.latestChapter?.length || 0) > existing.latestChapter.length)) {
                  existing.latestChapter = b.latestChapter!
                  existing.latestChapterSource = originName
                }
              }
            }

            const originB = (b.origin || originName || (b as any).bookSourceUrl || '').trim()
            const existingSrcIdx = existing.sources.findIndex(s => {
              const originA = (s.origin || s.originName || (s.rawBook as any)?.bookSourceUrl || '').trim()
              return (originA && originB && originA === originB) || s.bookUrl === b.bookUrl
            })

            if (existingSrcIdx >= 0) {
              const existingSrc = existing.sources[existingSrcIdx]
              const bScore = getSourceScore(sourceEntry, existing.latestChapter) +
                (normalizeTitle(b.name) === normalizeTitle(existing.name) ? 2000 : 0)
              const exScore = getSourceScore(existingSrc, existing.latestChapter) +
                (normalizeTitle(existingSrc.rawBook?.name) === normalizeTitle(existing.name) ? 2000 : 0)
              if (bScore > exScore) {
                existing.sources[existingSrcIdx] = sourceEntry
              }
            } else {
              existing.sources.push(sourceEntry)
            }
            // 重新按书源质量（是否有最新章节、HTTPS、匹配度等）排序，保证 sources[0] 始终是最优最新源
            sortSources(existing.sources, existing.latestChapter)
          } else {
            const hasCh = hasValidChapter(b.latestChapter)
            aggregatedMap.value.set(k, {
              key: k,
              name: b.name,
              author: b.author,
              coverUrl: b.coverUrl || '',
              intro: b.intro || '',
              kind: b.kind || '',
              latestChapter: hasCh ? (b.latestChapter || '') : '',
              latestChapterSource: hasCh ? originName : undefined,
              sources: [sourceEntry],
              type: b.type ?? b.sourceType,
              availabilityScore: b.availabilityScore,
            })
          }
        }
      },
      onEmpty: () => {
        doneSources.value++
      },
      onError: () => {
        doneSources.value++
      },
      onDone: () => {
        searching.value = false
        finished.value = true
        saveSearchCache()
      },
    },
    {
      precision: precision.value,
      category: selectedCategory.value === -1 ? undefined : selectedCategory.value,
    },
  )
}

const onAdd = async (item: SearchBookItem) => {
  const k = keyOf(item)
  addingKeys.value.add(k)
  addingKeys.value.add(item.bookUrl)
  try {
    const payload: SearchBookItem = {
      bookUrl: item.bookUrl,
      name: item.name,
      author: item.author,
      origin: item.origin || (item as any).bookSourceUrl || '',
      originName: item.originName,
      type: item.type ?? item.sourceType ?? 0,
      coverUrl: item.coverUrl,
      intro: item.intro,
      kind: item.kind,
      latestChapterTitle: item.latestChapterTitle || item.latestChapter || '',
      latestChapter: item.latestChapter || item.latestChapterTitle || '',
    }
    const res = await addToShelf(payload)
    if (res.isSuccess) {
      addedKeys.value.add(k)
      addedKeys.value.add(item.bookUrl)
      ElMessage.success(`《${item.name}》已加入书架`)
    } else {
      ElMessage.error(res.errorMsg || '加入失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.errorMsg || '加入失败')
  } finally {
    addingKeys.value.delete(k)
    addingKeys.value.delete(item.bookUrl)
  }
}

/**
 * 智能打开并阅读书籍：
 * 1. 若书本未入库，先调用 addToShelf 解析目录与正文，避免直达阅读页导致书籍不存在；
 * 2. 如果首选源遇到“目录为空”或接口异常，自动向下尝试聚合列表中的候选备用源；
 * 3. 准备就绪后，平滑跳转到阅读页。
 */
const startReading = async (item: SearchBookItem, parentBook?: AggregatedBook) => {
  const itemKey = keyOf(item)
  if (readingLoading.value) return
  readingLoading.value = true
  readingTargetKey.value = itemKey

  saveSearchCache()

  const loadingInstance = ElLoading.service({
    lock: true,
    text: `正在打开《${item.name}》，解析书源与章节目录中…`,
    background: 'rgba(0, 0, 0, 0.7)',
  })

  try {
    // 1. 如果已添加过（书库中已有该书完整数据），直接跳转阅读
    if (isAdded(item)) {
      await router.push({
        path: '/reader',
        query: {
          bookUrl: item.bookUrl,
          name: item.name,
          author: item.author,
          type: item.type !== undefined ? String(item.type) : undefined,
        },
      })
      return
    }

    // 2. 待尝试书源队列：优先当前选中的 item；若有 parentBook，按最新章节与健康度对候选源排序，最多重试 6 个候选源
    const candidateSources: SearchBookItem[] = [item]
    if (parentBook && parentBook.sources.length > 1) {
      const sorted = [...parentBook.sources].sort((a, b) => {
        return getSourceScore(b, parentBook.latestChapter) - getSourceScore(a, parentBook.latestChapter)
      })
      for (const s of sorted) {
        if (s.rawBook.bookUrl !== item.bookUrl && !candidateSources.some(c => c.bookUrl === s.rawBook.bookUrl)) {
          candidateSources.push(s.rawBook)
        }
      }
    }

    let successBookUrl = ''
    let successBook = item
    let lastErrorMsg = ''

    for (let i = 0; i < Math.min(candidateSources.length, 6); i++) {
      const target = candidateSources[i]
      if (i > 0) {
        loadingInstance.setText(`书源【${candidateSources[i - 1].originName}】响应异常，正在尝试切换至【${target.originName}】…`)
      }
      try {
        // 标准化载荷参数，彻底杜绝多余嵌套响应式属性引发的解析异常
        const payload: SearchBookItem = {
          bookUrl: target.bookUrl,
          name: target.name,
          author: target.author,
          origin: target.origin || (target as any).bookSourceUrl || '',
          originName: target.originName,
          type: target.type ?? target.sourceType ?? 0,
          coverUrl: target.coverUrl,
          intro: target.intro,
          kind: target.kind,
          latestChapterTitle: target.latestChapterTitle || target.latestChapter || '',
          latestChapter: target.latestChapter || target.latestChapterTitle || '',
        }
        const res = await prepareBook(payload)
        if (res.isSuccess) {
          successBookUrl = res.data?.bookUrl || target.bookUrl
          successBook = target
          break
        } else {
          lastErrorMsg = res.errorMsg || '解析目录失败'
        }
      } catch (err: any) {
        lastErrorMsg = err?.response?.data?.errorMsg || err.message || '网络连接超时'
      }
    }

    if (!successBookUrl) {
      // 优雅降级自愈：若首选书籍具备合法 bookUrl，允许平滑直达阅读器，由阅读器原生尝试拉取目录，杜绝硬性阻断
      if (item.bookUrl) {
        ElMessage.warning(`书源预解析响应较慢，正在尝试直接打开阅读…`)
        await router.push({
          path: '/reader',
          query: {
            bookUrl: item.bookUrl,
            name: item.name,
            author: item.author,
            type: item.type !== undefined ? String(item.type) : undefined,
          },
        })
        return
      }
      ElMessage.error(`无法打开该书（${lastErrorMsg || '书源响应失败'}），已为您自动展开“对比书源”挑选`)
      if (parentBook) {
        openSourceDrawer(parentBook)
      }
      return
    }

    // 3. 准备完成，跳转至阅读器
    await router.push({
      path: '/reader',
      query: {
        bookUrl: successBookUrl,
        name: successBook.name,
        author: successBook.author,
        type: successBook.type !== undefined ? String(successBook.type) : undefined,
      },
    })
  } finally {
    loadingInstance.close()
    readingLoading.value = false
    readingTargetKey.value = ''
  }
}

const drawerSize = computed(() => {
  return typeof window !== 'undefined' && window.innerWidth <= 600 ? '100%' : '480px'
})

const openSourceDrawer = (book: AggregatedBook) => {
  if (book && Array.isArray(book.sources)) {
    const seenOriginMap = new Map<string, AggregatedSourceItem>()
    for (const s of book.sources) {
      const originKey = (s.origin || s.originName || (s.rawBook as any)?.bookSourceUrl || '').trim() || s.bookUrl
      if (seenOriginMap.has(originKey)) {
        const existing = seenOriginMap.get(originKey)!
        const sScore = getSourceScore(s, book.latestChapter) + (normalizeTitle(s.rawBook?.name) === normalizeTitle(book.name) ? 2000 : 0)
        const exScore = getSourceScore(existing, book.latestChapter) + (normalizeTitle(existing.rawBook?.name) === normalizeTitle(book.name) ? 2000 : 0)
        if (sScore > exScore) {
          seenOriginMap.set(originKey, s)
        }
        continue
      }
      seenOriginMap.set(originKey, s)
    }
    book.sources = Array.from(seenOriginMap.values())
    sortSources(book.sources, book.latestChapter)
  }
  selectedBook.value = book
  sourceDrawerVisible.value = true
}
</script>

<style scoped>
.search-view {
  max-width: 920px;
  margin: 0 auto;
  padding: 16px;
  padding-bottom: 32px;
}

.category-tabs-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.cat-tab-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  white-space: nowrap;
}

.cat-tab-chip:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}

.cat-tab-chip.active {
  background: var(--el-color-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.35);
  border-color: var(--el-color-primary);
}

.cat-icon {
  font-size: 15px;
}

.media-type-tag {
  font-weight: 600;
  border-radius: 4px;
}

.score-status-tag {
  font-weight: 500;
  border-radius: 4px;
}

.search-bar {
  margin-bottom: 14px;
}

.toolbar-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 10px;
}

.status-info {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.precision-check {
  font-size: 13px;
  user-select: none;
}

.stop-btn {
  margin-left: 6px;
  font-size: 12px;
  padding: 2px 6px;
  height: auto;
}

.exact-match-tag {
  font-weight: 600;
  font-size: 11px;
}

.search-progress {
  margin-bottom: 14px;
}

.result-item {
  display: flex;
  gap: 14px;
  padding: 14px;
  border-radius: 10px;
  background: var(--el-bg-color);
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.result-item:hover {
  border-color: var(--el-border-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.cover {
  width: 68px;
  height: 94px;
  border-radius: 6px;
  object-fit: cover;
  background: var(--el-fill-color);
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title {
  font-weight: 600;
  font-size: 16px;
  cursor: pointer;
}

.title:hover {
  color: var(--el-color-primary);
}

.author {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.source-count-tag {
  cursor: pointer;
  font-weight: 500;
}

.meta-row {
  margin: 5px 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.latest-chapter {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

.intro {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 6px;
}

.source-preview {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
}

.muted-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.origin-chip {
  cursor: pointer;
}

.more-sources {
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 抽屉样式 */
.source-drawer-content {
  padding: 0 4px;
}

.drawer-header-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.source-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-sub-card {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
  transition: border-color 0.2s;
}

.source-sub-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.sub-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.src-name {
  font-weight: 600;
  font-size: 14px;
}

.sub-card-actions {
  display: flex;
  gap: 6px;
}

.src-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  overflow: hidden;
}

.source-badge {
  flex-shrink: 0;
  font-size: 11px;
}

.chapter-status-tag {
  font-weight: 500;
}

.latest-source-hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.sub-card-chapter {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
  word-break: break-all;
}

.empty-chapter {
  color: var(--el-text-color-placeholder);
}

.no-ch-hint {
  opacity: 0.75;
}

.sub-card-url {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .result-item {
    flex-direction: column;
  }

  .cover {
    width: 56px;
    height: 78px;
  }

  .actions {
    flex-direction: row;
    justify-content: flex-end;
    margin-top: 8px;
  }

  .latest-chapter {
    max-width: 200px;
  }
}
</style>
