<template>
  <div
    class="book-card"
    :class="['size-' + size, variantClass, { 'has-plank': hasPlank }]"
    :style="polaroidStyle"
    @click="onCardClick"
    @contextmenu.prevent="emit('menu', $event)"
    @touchstart.passive="onTouchStart"
    @touchend.passive="onTouchEnd"
    @touchcancel.passive="onTouchEnd"
  >
    <!-- 编号（画报/数据舱等需要索引感的版面） -->
    <span v-if="numbered" class="card-no">№{{ String(index || 0).padStart(2, '0') }}</span>

    <!-- 手动置顶标记 -->
    <div v-if="book.pinned" class="pin-badge" title="已置顶">📌</div>

    <!-- 浅樱落雪专属初樱角标 -->
    <div v-if="themeStore.appTheme === 'sakura'" class="sakura-vertical-label">🌸</div>

    <div class="cover-wrap">
      <img
        class="cover"
        :src="formatCover(book)"
        loading="lazy"
        @error="($event.target as HTMLImageElement).src = getDynamicCover(book.name, book.author)"
      />

      <!-- 移动端/悬浮快捷操作菜单按钮 -->
      <div class="card-more-btn" @click.stop="emit('menu', $event)" title="更多操作">
        <el-icon><MoreFilled /></el-icon>
      </div>

      <!-- 多媒体类型指示角标 -->
      <el-tag size="small" effect="dark" v-if="book.type === 1 || book.type === 32 || ((book.type || 0) & 32) === 32" class="type-badge audio">有声</el-tag>
      <el-tag size="small" effect="dark" v-else-if="book.type === 2 || book.type === 64 || ((book.type || 0) & 64) === 64" class="type-badge comic">漫画</el-tag>
      <el-tag size="small" effect="dark" v-else-if="book.type === 3 || book.type === 4 || ((book.type || 0) & 4) === 4" class="type-badge video">视频</el-tag>

      <!-- 阅读进度胶囊 -->
      <div class="progress-badge" v-if="typeof book.durChapterIndex === 'number'">
        {{ getProgressPercent(book) }}%
      </div>
    </div>

    <div class="book-info">
      <div class="book-kind" v-if="book.kind">{{ book.kind }}</div>
      <div class="book-name" :title="book.name">{{ book.name }}</div>
      <div class="book-author" :title="book.author">{{ book.author || '未知作者' }}</div>
      <div class="book-chapter-row current" :title="book.durChapterTitle || '未读'">
        <span class="ch-label">读至：</span>
        <span class="ch-text">{{
          book.durChapterTitle || (book.durChapterIndex ? `第 ${book.durChapterIndex + 1} 章` : '未读')
        }}</span>
      </div>
      <div
        class="book-chapter-row latest"
        v-if="book.latestChapterTitle"
        :title="book.latestChapterTitle"
      >
        <span class="ch-label">最新：</span>
        <span class="ch-text">{{ book.latestChapterTitle }}</span>
      </div>
      <div class="book-intro">{{ getBookIntro(book) }}</div>
      <div class="book-progress" v-if="typeof book.durChapterIndex === 'number'">
        <span class="book-progress-fill" :style="{ width: getProgressPercent(book) + '%' }"></span>
      </div>
      <div
        class="book-ring"
        v-if="typeof book.durChapterIndex === 'number'"
        :style="{ '--pct': getProgressPercent(book) }"
      >
        <span class="ring-value">{{ getProgressPercent(book) }}%</span>
      </div>
      <div class="book-cta">
        <button type="button" class="cta-btn" @click.stop="emit('open')">继续阅读</button>
      </div>
    </div>

    <!-- 主题专属层板：向左右各延伸半个栅格间距，与相邻卡片无缝拼接成完整书架横板 -->
    <div class="shelf-plank" aria-hidden="true" v-if="hasPlank"></div>

    <!-- 5 大主题专属鼠标悬浮深度展开面板 (Desktop Hover Expanded Panel) -->
    <!-- 1. 浅樱落雪：和风粉白手账展开卡 -->
    <div v-if="themeStore.appTheme === 'sakura'" class="hover-drawer hover-drawer-sakura">
      <div class="drawer-sakura-header">
        <span class="drawer-petal-icon">🌸</span>
        <span class="drawer-quote">落樱手账</span>
        <span class="drawer-badge" v-if="book.kind">{{ book.kind }}</span>
      </div>
      <div class="drawer-book-title" :title="book.name">{{ book.name }}</div>
      <div class="drawer-book-intro" :title="getBookIntro(book)">
        {{ getBookIntro(book) }}
      </div>
      <div class="drawer-progress-box">
        <div class="drawer-progress-row">
          <span class="drawer-progress-label">已读 {{ getProgressPercent(book) }}%</span>
          <span class="drawer-ch-label" :title="book.durChapterTitle || '首卷'">{{
            book.durChapterTitle || '首卷待启'
          }}</span>
        </div>
        <div class="drawer-progress-bar">
          <div class="drawer-progress-fill" :style="{ width: getProgressPercent(book) + '%' }"></div>
        </div>
      </div>
      <div class="drawer-actions">
        <button class="drawer-btn sakura-btn" @click.stop="emit('open')">
          <el-icon><Reading /></el-icon>&nbsp;翻开此卷
        </button>
        <button class="drawer-icon-btn" title="换封面" @click.stop="emit('cover')">
          <el-icon><Picture /></el-icon>
        </button>
        <button class="drawer-icon-btn danger" title="移出书架" @click.stop="emit('remove')">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <!-- 2. 黑曜赛博：全息战术外骨骼 HUD 舱 -->
    <div v-else-if="themeStore.appTheme === 'obsidian'" class="hover-drawer hover-drawer-obsidian">
      <div class="drawer-cyber-header">
        <span class="drawer-cyber-tag">[HUD.ACTV]</span>
        <span class="drawer-cyber-rate">SYNC {{ getProgressPercent(book) }}%</span>
      </div>
      <div class="drawer-book-title" :title="book.name">{{ book.name }}</div>
      <div class="drawer-book-intro cyber-intro" :title="getBookIntro(book)">
        {{ getBookIntro(book) }}
      </div>
      <div class="drawer-cyber-matrix-grid">
        <div class="matrix-cell">CHAP #{{ (book.durChapterIndex || 0) + 1 }}</div>
        <div class="matrix-cell">TOTAL #{{ book.totalChapterNum || '?' }}</div>
      </div>
      <div class="drawer-actions">
        <button class="drawer-btn cyber-btn" @click.stop="emit('open')">
          <el-icon><Reading /></el-icon>&nbsp;INITIALIZE
        </button>
        <button class="drawer-icon-btn cyber-icon" title="换封面" @click.stop="emit('cover')">
          <el-icon><Picture /></el-icon>
        </button>
        <button class="drawer-icon-btn cyber-icon danger" title="移出书架" @click.stop="emit('remove')">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <!-- 3. 星河极光：星际档案折跃卡 -->
    <div v-else-if="themeStore.appTheme === 'aurora'" class="hover-drawer hover-drawer-aurora">
      <div class="drawer-aurora-header">
        <span class="drawer-aurora-sparkle">✦</span>
        <span class="drawer-aurora-title">星际坐标档案</span>
        <span class="drawer-aurora-orbit">{{ getProgressPercent(book) }}%</span>
      </div>
      <div class="drawer-book-title" :title="book.name">{{ book.name }}</div>
      <div class="drawer-book-intro aurora-intro" :title="getBookIntro(book)">
        {{ getBookIntro(book) }}
      </div>
      <div class="drawer-progress-box">
        <div class="drawer-ch-label" :title="book.durChapterTitle || '准备启航'">
          航向：{{ book.durChapterTitle || '准备启航' }}
        </div>
      </div>
      <div class="drawer-actions">
        <button class="drawer-btn aurora-btn" @click.stop="emit('open')">
          <el-icon><Reading /></el-icon>&nbsp;折跃启阅
        </button>
        <button class="drawer-icon-btn aurora-icon" title="换封面" @click.stop="emit('cover')">
          <el-icon><Picture /></el-icon>
        </button>
        <button class="drawer-icon-btn aurora-icon danger" title="移出书架" @click.stop="emit('remove')">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <!-- 4. 蔚蓝画报：瑞士现代艺术导读专栏折页 -->
    <div v-else-if="themeStore.appTheme === 'azure'" class="hover-drawer hover-drawer-azure">
      <div class="drawer-azure-header">
        <span class="drawer-azure-no">№ 0{{ (book.durChapterIndex || 0) + 1 }}</span>
        <span class="drawer-azure-tag">EDITORIAL SYNOPSIS</span>
      </div>
      <div class="drawer-book-title" :title="book.name">{{ book.name }}</div>
      <div class="drawer-book-intro azure-intro" :title="getBookIntro(book)">
        {{ getBookIntro(book) }}
      </div>
      <div class="drawer-azure-meta">
        <div class="azure-meta-val" :title="book.durChapterTitle || 'STARTING POINT'">
          READING // {{ book.durChapterTitle || 'STARTING POINT' }}
        </div>
      </div>
      <div class="drawer-actions">
        <button class="drawer-btn azure-btn" @click.stop="emit('open')">
          <el-icon><Reading /></el-icon>&nbsp;READ COLUMN
        </button>
        <button class="drawer-icon-btn azure-icon" title="换封面" @click.stop="emit('cover')">
          <el-icon><Picture /></el-icon>
        </button>
        <button class="drawer-icon-btn azure-icon danger" title="移出书架" @click.stop="emit('remove')">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <!-- 5. 经典原生：古籍宣纸提要展卷 -->
    <div v-else class="hover-drawer hover-drawer-classic">
      <div class="drawer-classic-tab">
        <span class="classic-seal">提要</span>
        <span class="classic-progress"
          >卷 {{ (book.durChapterIndex || 0) + 1 }} · {{ getProgressPercent(book) }}%</span
        >
      </div>
      <div class="drawer-book-title" :title="book.name">{{ book.name }}</div>
      <div class="drawer-book-intro classic-intro" :title="getBookIntro(book)">
        {{ getBookIntro(book) }}
      </div>
      <div class="drawer-classic-note" :title="book.durChapterTitle || '未读'">
        读至「{{ book.durChapterTitle || '未读' }}」
      </div>
      <div class="drawer-classic-progress" v-if="typeof book.durChapterIndex === 'number'">
        <span class="fill" :style="{ width: getProgressPercent(book) + '%' }"></span>
      </div>
      <div class="drawer-actions">
        <button class="drawer-btn classic-btn" @click.stop="emit('open')">
          <el-icon><Reading /></el-icon>&nbsp;继续品读
        </button>
        <button class="drawer-icon-btn classic-icon" title="换封面" @click.stop="emit('cover')">
          <el-icon><Picture /></el-icon>
        </button>
        <button class="drawer-icon-btn classic-icon danger" title="移出书架" @click.stop="emit('remove')">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ShelfCard',
})

import { computed } from 'vue'
import { Delete, MoreFilled, Picture, Reading } from '@element-plus/icons-vue'
import type { BookItem } from '@/api/library'
import { useThemeStore } from '@/store/themeStore'
import {
  formatCover,
  getBookIntro,
  getDynamicCover,
  getProgressPercent,
  hashString,
} from '@/utils/bookDisplay'
import type { ShelfCardSize } from '@/utils/shelfLayout'

const props = withDefaults(
  defineProps<{
    book: BookItem
    /** 卡片规格：决定封面比例与信息密度（样式在 themes.css 中按主题定制） */
    size?: ShelfCardSize
    /** 版面序号，用于画报/数据舱式编号 */
    index?: number
    /** 是否显示编号角标 */
    numbered?: boolean
  }>(),
  {
    size: 'standard',
    index: 0,
    numbered: false,
  },
)

const emit = defineEmits<{
  (e: 'open'): void
  (e: 'cover'): void
  (e: 'remove'): void
  (e: 'menu', event?: MouseEvent): void
}>()

const themeStore = useThemeStore()

// 层板仅在"书架式"规格出现，画报式大卡改用细线分隔
const hasPlank = computed(() => props.size === 'standard' || props.size === 'compact')

// 拍立得规格：按书址哈希决定相纸比例与倾角，保证每次渲染稳定不抖动
const polaroidVariant = computed(() => {
  if (props.size !== 'polaroid') return ''
  return ['a', 'b', 'c'][hashString(props.book.bookUrl) % 3]
})
const variantClass = computed(() =>
  polaroidVariant.value ? 'variant-' + polaroidVariant.value : '',
)
const polaroidStyle = computed(() => {
  if (props.size !== 'polaroid') return undefined
  const tilt = ((hashString(props.book.bookUrl) % 7) - 3) * 0.6
  return { '--tilt': `${tilt}deg` }
})

// 长按呼出操作面板（移动端），并抑制随后的 click 以免误进阅读页
let touchTimer: ReturnType<typeof setTimeout> | null = null
let suppressClick = false

const onTouchStart = () => {
  if (touchTimer) clearTimeout(touchTimer)
  touchTimer = setTimeout(() => {
    touchTimer = null
    suppressClick = true
    emit('menu')
  }, 450)
}

const onTouchEnd = () => {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

const onCardClick = () => {
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit('open')
}
</script>

<style scoped>
.book-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  position: relative;
  min-width: 0;
  max-width: 100%;
  /* 允许层板向左右探出半个栅格间距以拼接成整条书架横板 */
  overflow: visible;
  box-sizing: border-box;
  transition: transform 0.22s cubic-bezier(0.2, 0.8, 0.2, 1);
}

/* 仅带层板的规格为层板预留底部空间，画报式大卡不预留 */
.book-card.has-plank {
  padding-bottom: calc(var(--plank-h, 10px) + 2px);
}

/* 主题专属层板（材质、厚度、发光均在 themes.css 中按主题定制） */
.shelf-plank {
  position: absolute;
  /* 绝对定位以卡片 padding box 为基准，再额外探出 2px 让相邻层板互相咬合、无缝衔接 */
  left: calc(var(--shelf-gap, 16px) / -2 - 2px);
  right: calc(var(--shelf-gap, 16px) / -2 - 2px);
  bottom: 0;
  height: var(--plank-h, 10px);
  border-radius: var(--plank-radius, 2px);
  background: var(--plank-bg, linear-gradient(180deg, #cbd5e1, #94a3b8));
  box-shadow: var(--plank-shadow, 0 3px 8px rgba(0, 0, 0, 0.18));
  border: var(--plank-border, none);
  clip-path: var(--plank-clip, none);
  z-index: 1;
  pointer-events: none;
  transition: box-shadow 0.25s ease, filter 0.25s ease;
}

/* 封面容器：严格 5:7 黄金比例，开启 contain: paint 防止任何溢出渲染 */
.cover-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 5 / 7;
  max-width: 100%;
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-fill-color);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
  contain: paint;
}

.book-card:hover .cover-wrap {
  transform: translateY(-3px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
}

/* 封面图片：绝对定位充满容器，严格以 cover 裁剪，彻底杜绝原始分辨率超标撑大容器 */
.cover {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  object-fit: cover;
  object-position: center top;
  display: block;
}

.type-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  font-size: 10px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  z-index: 2;
}

.type-badge.audio {
  background: #e6a23c;
}
.type-badge.comic {
  background: #67c23a;
}
.type-badge.video {
  background: #f56c6c;
}

.progress-badge {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.68);
  color: #fff;
  font-size: 10px;
  padding: 2px 0;
  text-align: center;
  backdrop-filter: blur(2px);
  z-index: 2;
}

.card-more-btn {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  cursor: pointer;
  opacity: 0.8;
  transition:
    opacity 0.2s,
    transform 0.2s;
  z-index: 3;
  backdrop-filter: blur(4px);
}

.book-card:hover .card-more-btn {
  opacity: 1;
}

.pin-badge {
  position: absolute;
  left: 6px;
  top: 36px;
  z-index: 3;
  font-size: 12px;
  line-height: 1;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.4));
  pointer-events: none;
}

.card-more-btn:hover {
  transform: scale(1.1);
  background: rgba(0, 0, 0, 0.8);
}

.book-info {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  width: 100%;
  overflow: hidden;
}

.book-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
  min-width: 0;
}

.book-author {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
  min-width: 0;
}

.book-chapter-row {
  font-size: 11px;
  display: flex;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  margin-top: 3px;
  min-width: 0;
}

.book-chapter-row.current {
  color: var(--el-color-primary);
  font-weight: 500;
}

.book-chapter-row.latest {
  color: var(--el-text-color-secondary);
  opacity: 0.85;
}

.ch-label {
  font-size: 10px;
  opacity: 0.75;
  flex-shrink: 0;
}

.ch-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* ===== 大卡（hero / feature）专用元素：默认全部隐藏，仅由主题版面样式按规格启用 ===== */
.card-no,
.book-kind,
.book-intro,
.book-progress,
.book-ring,
.book-cta {
  display: none;
}

/* 移动端深度优化：严格限制列宽，杜绝横向滚动条，保证图文在各自边界内 */
@media (max-width: 768px) {
  .book-name {
    font-size: 12px;
  }
  .book-author {
    font-size: 10px;
  }
  .book-chapter-row {
    font-size: 10px;
  }
  .card-more-btn {
    width: 22px;
    height: 22px;
    font-size: 11px;
    opacity: 0.9;
  }
  .pin-badge {
    left: 4px;
    top: 30px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  /* 小屏隐藏"最新"行，把空间让给书名与作者 */
  .book-chapter-row.latest {
    display: none;
  }
}
</style>
