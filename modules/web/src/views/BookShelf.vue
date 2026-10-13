<template>
  <div class="shelf-view" :class="'shelf-layout-' + themeStore.appTheme" v-loading="loading">
    <div class="shelf-toolbar">
      <div class="shelf-title-wrap">
        <h3 class="shelf-title">我的书架</h3>
        <span class="shelf-count" v-if="filtered.length > 0">共 {{ filtered.length }} 本</span>
      </div>
      <el-dropdown class="sort-dropdown" trigger="click" @command="onSortChange">
        <el-button size="default" class="sort-btn">
          <el-icon><Sort /></el-icon>&nbsp;{{ sortLabel }}
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="opt in sortOptions" :key="opt.value" :command="opt.value">
              {{ opt.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-input v-model="filterWord" class="filter-input" size="default"
        placeholder="过滤书名 / 作者" :prefix-icon="SearchIcon" clearable />
      <el-button circle size="default" class="refresh-btn" title="刷新书架" @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <!-- 主题专属书架铭牌：5 套主题各自独立的材质、铭文与副标题 -->
    <div class="shelf-nameplate" v-if="!loading || books.length > 0">
      <span class="plate-emblem">{{ shelfPlate.emblem }}</span>
      <span class="plate-main">{{ shelfPlate.title }}</span>
      <span class="plate-sub">{{ shelfPlate.sub }}</span>
      <span class="plate-count" v-if="filtered.length > 0">{{ filtered.length }} 册在架</span>
    </div>

    <el-empty v-if="!loading && filtered.length === 0" description="书架空空如也，去搜索添加一本书吧">
      <el-button type="primary" @click="$router.push('/search')">去搜索</el-button>
    </el-empty>

    <!-- 主题化版面：蔚蓝画报（杂志分栏）/ 星河极光（星核展台）/ 浅樱落雪（拍立得瀑布流）/ 黑曜赛博（数据舱） -->
    <template v-if="shelfLayoutKind !== 'uniform'">
      <template v-for="(block, blockIndex) in shelfBlocks" :key="'shelf-block-' + blockIndex">
        <header v-if="block.kind === 'masthead'" class="mag-masthead">
          <div class="mag-masthead-main">
            <span class="mag-brand">AZURE EDITORIAL</span>
            <span class="mag-vol">VOL.26 // 2026</span>
          </div>
          <div class="mag-masthead-meta">
            <span>在架 {{ block.stats.total }} 册</span>
            <span>在读 {{ block.stats.reading }}</span>
            <span>分组 {{ block.stats.groups }}</span>
          </div>
        </header>

        <section v-else-if="block.kind === 'headline'" class="mag-headline">
          <ShelfCard
            class="mag-hero-card"
            :book="block.hero"
            size="hero"
            :index="numberOf(block.hero)"
            numbered
            @open="toReader(block.hero)"
            @cover="openCoverDialog(block.hero)"
            @remove="onRemove(block.hero)"
            @menu="openActionMenu(block.hero, $event)"
          />
          <div class="mag-side" v-if="block.side.length">
            <ShelfCard
              v-for="item in block.side"
              :key="item.bookUrl"
              class="mag-side-card"
              :book="item"
              size="compact"
              :index="numberOf(item)"
              numbered
              @open="toReader(item)"
              @cover="openCoverDialog(item)"
              @remove="onRemove(item)"
              @menu="openActionMenu(item, $event)"
            />
          </div>
        </section>

        <section v-else-if="block.kind === 'duo'" class="mag-duo">
          <ShelfCard
            v-for="item in block.items"
            :key="item.bookUrl"
            class="mag-feature-card"
            :book="item"
            size="feature"
            :index="numberOf(item)"
            numbered
            @open="toReader(item)"
            @cover="openCoverDialog(item)"
            @remove="onRemove(item)"
            @menu="openActionMenu(item, $event)"
          />
        </section>

        <div v-else-if="block.kind === 'note'" class="mag-note">
          <span class="mag-note-text">{{ block.text }}</span>
        </div>

        <!-- 星河极光：星核展台 -->
        <section v-else-if="block.kind === 'stage'" class="stage-deck">
          <div class="stage-caption">
            <span class="stage-tag">✦ STELLAR CORE // 星核展台</span>
            <span class="stage-stat">
              在架 {{ block.stats.total }} 册 · 在读 {{ block.stats.reading }} 册
            </span>
          </div>
          <ShelfCard
            class="stage-hero"
            :book="block.hero"
            size="hero"
            :index="numberOf(block.hero)"
            @open="toReader(block.hero)"
            @cover="openCoverDialog(block.hero)"
            @remove="onRemove(block.hero)"
            @menu="openActionMenu(block.hero, $event)"
          />
        </section>

        <!-- 黑曜赛博：作战目标舱 -->
        <section v-else-if="block.kind === 'target'" class="deck-target">
          <div class="deck-head">
            <span class="deck-tag">[TARGET.LOCK]</span>
            <span class="deck-sub">PRIMARY READING OBJECTIVE</span>
          </div>
          <ShelfCard
            class="deck-hero"
            :book="block.hero"
            size="hero"
            :index="numberOf(block.hero)"
            @open="toReader(block.hero)"
            @cover="openCoverDialog(block.hero)"
            @remove="onRemove(block.hero)"
            @menu="openActionMenu(block.hero, $event)"
          />
          <div class="deck-telemetry">
            <div class="tele">
              <span class="tele-k">CHAP</span>
              <span class="tele-v">
                {{ (block.hero.durChapterIndex || 0) + 1 }} / {{ block.hero.totalChapterNum || '??' }}
              </span>
            </div>
            <div class="tele">
              <span class="tele-k">SYNC</span>
              <span class="tele-v">{{ getProgressPercent(block.hero) }}%</span>
            </div>
            <div class="tele">
              <span class="tele-k">MEDIA</span>
              <span class="tele-v">{{ mediaLabel(block.hero.type) }}</span>
            </div>
            <div class="tele">
              <span class="tele-k">SHELF</span>
              <span class="tele-v">{{ block.stats.total }} 册</span>
            </div>
          </div>
        </section>

        <!-- 浅樱落雪：拍立得瀑布流 -->
        <section v-else-if="block.kind === 'masonry'" class="masonry-board">
          <ShelfCard
            v-for="item in block.items"
            :key="item.bookUrl"
            :book="item"
            size="polaroid"
            @open="toReader(item)"
            @cover="openCoverDialog(item)"
            @remove="onRemove(item)"
            @menu="openActionMenu(item, $event)"
          />
        </section>

        <!-- 其余版面区块（画报编号栏 / 极光星图 / 赛博数据格） -->
        <section
          v-else
          class="shelf-block-grid"
          :class="[
            'grid-theme-' + shelfLayoutKind,
            'grid-size-' + block.size,
            { 'mag-grid': shelfLayoutKind === 'magazine' },
          ]"
        >
          <ShelfCard
            v-for="item in block.items"
            :key="item.bookUrl"
            :book="item"
            :size="block.size"
            :index="numberOf(item)"
            :numbered="block.numbered"
            @open="toReader(item)"
            @cover="openCoverDialog(item)"
            @remove="onRemove(item)"
            @menu="openActionMenu(item, $event)"
          />
        </section>
      </template>
    </template>

    <!-- 统一书架网格（经典原生：无主题化版面，保持纯净卡片网格） -->
    <div v-else class="shelf-grid" :class="'grid-' + themeStore.appTheme">
      <ShelfCard
        v-for="book in filtered"
        :key="book.bookUrl"
        :book="book"
        size="standard"
        @open="toReader(book)"
        @cover="openCoverDialog(book)"
        @remove="onRemove(book)"
        @menu="openActionMenu(book, $event)"
      />
    </div>

    <!-- 桌面端右键上下文菜单（严格防视口越界坐标限制） -->
    <teleport to="body">
      <div v-if="menuVisible && !isMobile" class="ctx-menu" :style="{ left: menuX + 'px', top: menuY + 'px' }">
        <div class="ctx-item" v-if="menuBook" @click="toReader(menuBook); closeMenu()">
          <el-icon><Reading /></el-icon>&nbsp;立即阅读
        </div>
        <div class="ctx-item" v-if="menuBook" @click="onTogglePin(menuBook)">
          <el-icon><StarFilled v-if="menuBook.pinned" /><Star v-else /></el-icon>&nbsp;{{
            menuBook.pinned ? '取消置顶' : '置顶展示'
          }}
        </div>
        <div class="ctx-item" v-if="menuBook" @click="openCoverDialog(menuBook); closeMenu()">
          <el-icon><Picture /></el-icon>&nbsp;更换封面
        </div>
        <div class="ctx-item danger" v-if="menuBook" @click.stop="onRemove(menuBook)">
          <el-icon><Delete /></el-icon>&nbsp;移出书架
        </div>
      </div>
    </teleport>

    <!-- 移动端专属安全操作面板（底部抽屉，彻底杜绝坐标越界） -->
    <el-drawer
      v-model="actionSheetVisible"
      direction="btt"
      size="auto"
      :with-header="false"
      class="mobile-action-sheet"
    >
      <div class="sheet-content" v-if="menuBook">
        <div class="sheet-book-header">
          <div class="sheet-cover-box">
            <img class="sheet-cover" :src="formatCover(menuBook)"
              @error="($event.target as HTMLImageElement).src = getDynamicCover(menuBook.name, menuBook.author)" />
          </div>
          <div class="sheet-info">
            <div class="sheet-title">{{ menuBook.name }}</div>
            <div class="sheet-author">{{ menuBook.author || '未知作者' }}</div>
            <div class="sheet-progress" v-if="menuBook.durChapterTitle">读至：{{ menuBook.durChapterTitle }}</div>
          </div>
        </div>
        <div class="sheet-actions">
          <button class="sheet-btn primary" @click="toReader(menuBook); actionSheetVisible = false">
            <el-icon><Reading /></el-icon>&nbsp;继续阅读
          </button>
          <button class="sheet-btn" @click="onTogglePin(menuBook)">
            <el-icon><StarFilled v-if="menuBook.pinned" /><Star v-else /></el-icon>&nbsp;{{
              menuBook.pinned ? '取消置顶' : '置顶展示'
            }}
          </button>
          <button class="sheet-btn" @click="openCoverDialog(menuBook); actionSheetVisible = false">
            <el-icon><Picture /></el-icon>&nbsp;更换封面
          </button>
          <button class="sheet-btn danger" @click.stop="onRemove(menuBook)">
            <el-icon><Delete /></el-icon>&nbsp;移出书架
          </button>
        </div>
      </div>
    </el-drawer>

    <!-- 更换封面对话框（移动端自适应宽度，防弹窗与图片越界） -->
    <el-dialog v-model="coverDialogVisible" title="更换书籍封面" :width="dialogWidth" destroy-on-close class="cover-dialog">
      <div class="cover-dialog-body" v-if="editingBook">
        <div class="preview-area">
          <div class="dialog-cover-wrap">
            <img class="dialog-cover-preview" :src="coverInput || formatCover(editingBook)"
              @error="($event.target as HTMLImageElement).src = getDynamicCover(editingBook.name, editingBook.author)" />
          </div>
          <div class="preview-hint">《{{ editingBook.name }}》封面预览</div>
        </div>
        <el-form label-position="top">
          <el-form-item label="封面网络链接 (URL)">
            <el-input v-model="coverInput" placeholder="输入 http/https 图片地址" clearable />
          </el-form-item>
          <el-form-item label="全网在线搜书封（推书君/有谱说）">
            <el-button size="small" type="success" :loading="searchingCovers" @click="doSearchCovers">
              <el-icon><Search /></el-icon>&nbsp;智能搜索正版高清书封
            </el-button>
            <div class="candidate-cover-list" v-if="candidateCovers.length > 0">
              <div
                v-for="(cand, idx) in candidateCovers"
                :key="idx"
                class="cand-item"
                :class="{ active: coverInput === cand.coverUrl }"
                @click="coverInput = cand.coverUrl"
              >
                <img :src="cand.coverUrl" class="cand-img" />
                <span class="cand-src">{{ cand.source }}</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="本地图片上传或文字艺术书封">
            <input type="file" ref="fileInputRef" accept="image/*" style="display: none" @change="onFileSelected" />
            <el-button @click="fileInputRef?.click()"><el-icon><Upload /></el-icon>&nbsp;选择本地图片</el-button>
            <el-button text type="primary" @click="useDynamicArtCover">使用艺术文字书封</el-button>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="coverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCover" @click="onSaveCover">保存封面</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'BookShelf',
})

import { computed, onMounted, onActivated, ref, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Search as SearchIcon,
  Reading,
  Picture,
  Delete,
  Upload,
  Search,
  Sort,
  Star,
  StarFilled,
} from '@element-plus/icons-vue'
import {
  getBookshelf,
  removeFromShelf,
  setBookPinned,
  updateBookCover,
  searchCoverCandidates,
  type BookItem,
  type CoverCandidate,
} from '@/api/library'
import { useThemeStore } from '@/store/themeStore'
import ShelfCard from '@/components/shelf/ShelfCard.vue'
import { formatCover, getDynamicCover, getProgressPercent } from '@/utils/bookDisplay'
import {
  buildNumberMap,
  buildShelfBlocks,
  getShelfLayoutKind,
  sortShelfBooks,
  type ShelfSortMode,
} from '@/utils/shelfLayout'

const router = useRouter()
const themeStore = useThemeStore()
const loading = ref(false)
const books = ref<BookItem[]>([])
const filterWord = ref('')
const isMobile = ref(false)

// ===== 书架排序（本地记忆） =====
const SORT_STORAGE_KEY = 'legado_shelf_sort'
const savedSort = (
  typeof localStorage !== 'undefined' ? localStorage.getItem(SORT_STORAGE_KEY) : null
) as ShelfSortMode | null
const sortMode = ref<ShelfSortMode>(savedSort || 'default')
const sortOptions: Array<{ value: ShelfSortMode; label: string }> = [
  { value: 'default', label: '置顶优先' },
  { value: 'recent', label: '最近阅读' },
  { value: 'added', label: '最近添加' },
  { value: 'progress', label: '阅读进度' },
  { value: 'name', label: '书名排序' },
]
const sortLabel = computed(
  () => sortOptions.find(o => o.value === sortMode.value)?.label || '排序',
)
const onSortChange = (value: ShelfSortMode) => {
  sortMode.value = value
  localStorage.setItem(SORT_STORAGE_KEY, value)
}

const checkIsMobile = () => {
  isMobile.value = window.innerWidth <= 768 || ('ontouchstart' in window && window.innerWidth <= 1024)
}

const shelfBooks = computed(() => sortShelfBooks(books.value, sortMode.value))

// 每套主题对应的书架铭牌（材质与铭文由 themes.css 按主题渲染）
const shelfPlate = computed(() => {
  switch (themeStore.appTheme) {
    case 'sakura':
      return { emblem: '🌸', title: '落樱书阁', sub: 'SAKURA BLOSSOM SHELF' }
    case 'aurora':
      return { emblem: '✦', title: '星河书架', sub: 'AURORA STARLIB SHELF' }
    case 'obsidian':
      return { emblem: '⚡', title: '矩阵典藏舱', sub: '[DATA.CORE] ARCHIVE RACK' }
    case 'azure':
      return { emblem: '◈', title: '编辑部陈列架', sub: 'AZURE EDITORIAL COLLECTION' }
    case 'classic':
    default:
      return { emblem: '📚', title: '藏书阁', sub: 'LEGADO CLASSIC LIBRARY' }
  }
})

const dialogWidth = computed(() => (isMobile.value ? '92vw' : '440px'))

const filtered = computed(() => {
  const kw = filterWord.value.trim()
  if (!kw) return shelfBooks.value
  return shelfBooks.value.filter(b => b.name.includes(kw) || b.author.includes(kw))
})

// ===== 版面编排：按主题生成区块（经典原生为空 → 走统一书架网格） =====
const shelfLayoutKind = computed(() => getShelfLayoutKind(themeStore.appTheme))
const shelfBlocks = computed(() => buildShelfBlocks(filtered.value, themeStore.appTheme))
const numberMap = computed(() => buildNumberMap(shelfBlocks.value))
const numberOf = (book: BookItem): number => numberMap.value.get(book.bookUrl) ?? 0

// 媒体类型标签（黑曜赛博数据舱遥测用）
const mediaLabel = (type?: number): string => {
  switch (type) {
    case 1:
      return 'AUDIO'
    case 2:
      return 'COMIC'
    case 3:
      return 'VIDEO'
    default:
      return 'TEXT'
  }
}

const load = async () => {
  loading.value = true
  try {
    books.value = await getBookshelf()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  checkIsMobile()
  window.addEventListener('resize', checkIsMobile)
  load()
})

onActivated(() => {
  checkIsMobile()
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkIsMobile)
  document.removeEventListener('click', closeMenu)
})

const toReader = (book: BookItem) => {
  router.push({
    path: '/reader',
    query: {
      bookUrl: book.bookUrl,
      name: book.name,
      author: book.author,
      type: book.type !== undefined ? String(book.type) : undefined,
    },
  })
}

// ===== 智能搜书封 =====
const candidateCovers = ref<CoverCandidate[]>([])
const searchingCovers = ref(false)

const doSearchCovers = async () => {
  if (!editingBook.value) return
  searchingCovers.value = true
  try {
    const res = await searchCoverCandidates(editingBook.value.name, editingBook.value.author)
    candidateCovers.value = res
    if (!res.length) {
      ElMessage.info('未在网络图库中检索到匹配书封')
    } else {
      ElMessage.success(`找到 ${res.length} 个候选书封`)
    }
  } catch {
    ElMessage.warning('检索书封失败')
  } finally {
    searchingCovers.value = false
  }
}

// ===== 操作菜单控制（长按 / 右键 / 更多按钮统一入口） =====
const menuVisible = ref(false)
const actionSheetVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuBook = ref<BookItem | null>(null)

const closeMenu = () => {
  menuVisible.value = false
}

const openActionMenu = (book: BookItem, e?: MouseEvent) => {
  menuBook.value = book
  checkIsMobile()

  if (isMobile.value) {
    actionSheetVisible.value = true
    return
  }

  const clientX = e?.clientX ?? (window.innerWidth / 2 - 80)
  const clientY = e?.clientY ?? (window.innerHeight / 2 - 60)

  // 严格限制菜单在屏幕安全可视区域内，绝不越界
  menuX.value = Math.max(12, Math.min(clientX, window.innerWidth - 180))
  menuY.value = Math.max(12, Math.min(clientY, window.innerHeight - 150))
  menuVisible.value = true

  setTimeout(() => {
    document.addEventListener('click', closeMenu, { once: true })
  }, 10)
}

// ===== 手动置顶 =====
const pinningUrl = ref('')

const onTogglePin = async (book: BookItem | null) => {
  if (!book || pinningUrl.value) return
  const target = book
  const next = !target.pinned
  closeMenu()
  actionSheetVisible.value = false
  pinningUrl.value = target.bookUrl
  try {
    const res = await setBookPinned(target.bookUrl, next)
    if (res?.isSuccess) {
      target.pinned = next
      ElMessage.success(next ? `已将《${target.name}》置顶` : `已取消置顶《${target.name}》`)
      await load()
    } else {
      ElMessage.error(res?.errorMsg || '置顶操作失败')
    }
  } catch (err) {
    const msg = (err as { response?: { data?: { errorMsg?: string } } })?.response?.data?.errorMsg
    ElMessage.error(msg || '置顶操作失败')
  } finally {
    pinningUrl.value = ''
  }
}

const onRemove = async (book: BookItem | null) => {
  if (!book) return
  const target = { ...book }
  closeMenu()
  actionSheetVisible.value = false
  await nextTick()
  try {
    await ElMessageBox.confirm(`确定将《${target.name}》移出书架吗？`, '移出书架', {
      confirmButtonText: '确定移出',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
      lockScroll: false,
    })
  } catch {
    return
  }
  const res = await removeFromShelf(target.bookUrl)
  if (res.isSuccess) {
    ElMessage.success(`《${target.name}》已移出书架`)
    await load()
  } else {
    ElMessage.error(res.errorMsg || '操作失败')
  }
}

// ===== 更换封面逻辑 =====
const coverDialogVisible = ref(false)
const editingBook = ref<BookItem | null>(null)
const coverInput = ref('')
const fileInputRef = ref<HTMLInputElement>()
const savingCover = ref(false)

const openCoverDialog = (book: BookItem) => {
  editingBook.value = book
  coverInput.value = book.coverUrl || ''
  coverDialogVisible.value = true
}

const onFileSelected = (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    coverInput.value = reader.result as string
  }
  reader.readAsDataURL(file)
}

const useDynamicArtCover = () => {
  if (!editingBook.value) return
  coverInput.value = getDynamicCover(editingBook.value.name, editingBook.value.author)
}

const onSaveCover = async () => {
  if (!editingBook.value) return
  const finalCover = coverInput.value.trim() || getDynamicCover(editingBook.value.name, editingBook.value.author)
  savingCover.value = true
  try {
    const res = await updateBookCover(editingBook.value.bookUrl, finalCover)
    if (res.isSuccess) {
      editingBook.value.coverUrl = finalCover
      ElMessage.success('书籍封面更新成功')
      coverDialogVisible.value = false
    } else {
      ElMessage.error(res.errorMsg || '封面保存失败')
    }
  } catch (err) {
    const msg = (err as { response?: { data?: { errorMsg?: string } } })?.response?.data?.errorMsg
    ElMessage.error(msg || '封面保存失败')
  } finally {
    savingCover.value = false
  }
}
</script>

<style scoped>
.shelf-view {
  padding: 16px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 60vh;
  box-sizing: border-box;
  overflow-x: hidden;
}

.shelf-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.shelf-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.shelf-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.shelf-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.filter-input {
  max-width: 260px;
  margin-left: 0;
}

/* 排序入口：默认顺序由服务端给出（置顶优先 + 加入时间倒序） */
.sort-dropdown {
  margin-left: auto;
  flex-shrink: 0;
}

.sort-btn {
  font-weight: 500;
}

/* 书架铭牌：主题材质由 themes.css 通过 --plate-* 变量逐套定制 */
.shelf-nameplate {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--plate-pad-y, 9px) var(--plate-pad-x, 16px);
  margin-bottom: 14px;
  border-radius: var(--plate-radius, 12px);
  background: var(--plate-bg, var(--app-card-bg, transparent));
  border: var(--plate-border, var(--app-card-border, 1px solid transparent));
  box-shadow: var(--plate-shadow, none);
  position: relative;
  overflow: hidden;
  min-width: 0;
}

.plate-emblem {
  font-size: var(--plate-emblem-size, 18px);
  line-height: 1;
  flex-shrink: 0;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.18));
}

.plate-main {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: var(--plate-letter, 1px);
  color: var(--plate-text, var(--el-text-color-primary));
  white-space: nowrap;
}

.plate-sub {
  font-size: 11px;
  letter-spacing: 1.4px;
  text-transform: uppercase;
  color: var(--plate-sub-text, var(--el-text-color-secondary));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plate-count {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--plate-accent, var(--el-color-primary));
}

/* 响应式书架容器：桌面 130px+ 自适应列数，间距/内边距/背板由主题变量驱动 */
.shelf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--shelf-col-min, 130px), 1fr));
  gap: var(--shelf-gap, 16px);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  /* 左右内边距 = 层板探出量 + 2px，使首尾层板恰好收在书架边框内 */
  padding: var(--shelf-pad-top, 10px) var(--shelf-pad-x, calc(var(--shelf-gap, 16px) / 2 + 2px))
    var(--shelf-pad-bottom, 6px);
  border-radius: var(--shelf-radius, 12px);
  /* 使用 background-image 而非 background 简写，避免重置主题的 background-size */
  background-color: var(--shelf-bg-color, transparent);
  background-image: var(--shelf-bg, none);
  border: var(--shelf-border, 1px solid transparent);
  box-shadow: var(--shelf-inset, none);
}

/* 桌面端右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 2000;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 4px 0;
  box-shadow: var(--el-box-shadow-light);
  min-width: 140px;
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.ctx-item:hover {
  background: var(--el-fill-color);
}

.ctx-item.danger {
  color: var(--el-color-danger);
}

/* 移动端专属底部操作抽屉 */
.sheet-content {
  padding: 16px 20px 28px;
}

.sheet-book-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sheet-cover-box {
  width: 52px;
  height: 72px;
  aspect-ratio: 5 / 7;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  background: var(--el-fill-color);
}

.sheet-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sheet-info {
  flex: 1;
  min-width: 0;
}

.sheet-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-author {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.sheet-progress {
  font-size: 12px;
  color: var(--el-color-primary);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sheet-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px 0;
  font-size: 15px;
  font-weight: 500;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  transition: background 0.15s;
}

.sheet-btn.primary {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}

.sheet-btn.danger {
  color: var(--el-color-danger);
  border-color: rgba(245, 108, 108, 0.3);
}

/* 封面修改对话框 */
.preview-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16px;
}

.dialog-cover-wrap {
  width: 110px;
  height: 154px;
  aspect-ratio: 5 / 7;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  background: var(--el-fill-color-light);
}

.dialog-cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

.candidate-cover-list {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 8px 0;
  width: 100%;
  max-width: 100%;
}

.cand-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  border: 2px solid transparent;
  flex-shrink: 0;
}

.cand-item.active {
  border-color: var(--el-color-primary);
}

.cand-img {
  width: 60px;
  height: 84px;
  object-fit: cover;
  border-radius: 4px;
  max-width: 60px;
}

.cand-src {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* 移动端深度优化：严格限制 3 列，杜绝横向滚动条，保证图文在各自边界内 */
@media (max-width: 768px) {
  .shelf-view {
    /* 底部安全区由 AppLayout 的 .main 统一预留，这里只做内容级留白 */
    padding: 12px 10px calc(20px + env(safe-area-inset-bottom, 0px));
  }
  .shelf-toolbar {
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
  }
  .shelf-title {
    font-size: 16px;
  }
  .filter-input {
    max-width: 100%;
    width: 100%;
    margin-left: 0;
    order: 3;
  }
  .sort-dropdown {
    order: 1;
    margin-left: auto;
  }
  .refresh-btn {
    order: 2;
  }
  .shelf-nameplate {
    padding: 8px 12px;
    gap: 8px;
    margin-bottom: 10px;
  }
  .plate-main {
    font-size: 13px;
  }
  .plate-sub {
    font-size: 9px;
    letter-spacing: 0.6px;
  }
  .plate-count {
    font-size: 11px;
  }
  /* 平板/大屏手机：3 列；间距收窄避免层板探出后互相顶到 */
  .shelf-grid {
    --shelf-gap: 10px !important;
    --shelf-pad-top: 8px;
    --shelf-pad-bottom: 6px;
    grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
  }
}

/* 小屏手机：2 列大封面，铭牌副标题与进度条让位给书名 */
@media (max-width: 480px) {
  .shelf-grid {
    --shelf-gap: 8px !important;
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }
  .shelf-nameplate {
    padding: 7px 10px;
  }
  .plate-sub {
    display: none;
  }
  .plate-emblem {
    font-size: 16px;
  }
}

/* 中屏平板：4 列，兼顾竖屏平板与桌面窄窗口 */
@media (min-width: 769px) and (max-width: 1080px) {
  .shelf-grid {
    grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  }
}

/* 超宽桌面：单册封面不被拉伸过大，保持书脊视觉比例 */
@media (min-width: 1560px) {
  .shelf-grid {
    grid-template-columns: repeat(auto-fill, minmax(142px, 1fr));
  }
}
</style>
