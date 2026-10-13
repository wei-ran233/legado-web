import type { BookItem } from '@/api/library'
import type { AppThemeType } from '@/store/themeStore'
import { getProgressPercent } from '@/utils/bookDisplay'

/**
 * 书架版面编排引擎
 *
 * 设计原则：**确定性编排**，不使用 grid-auto-flow: dense。
 * 每套主题对应一种版面（layout kind）与一个编排函数，书序在编排中保持稳定，
 * 只在"头条/特稿"位置抽出被强调的书，其余书籍严格按原顺序落入编号栏。
 */

/** 卡片规格：决定封面比例与信息密度（视觉在 themes.css 中按主题定制） */
export type ShelfCardSize = 'hero' | 'feature' | 'standard' | 'compact' | 'polaroid'

/** 版面类型：uniform = 统一书架网格；magazine = 画报式分栏编排 */
export type ShelfLayoutKind = 'uniform' | 'magazine' | 'masonry' | 'stage' | 'deck'

/** 书架排序方式 */
export type ShelfSortMode = 'default' | 'recent' | 'added' | 'progress' | 'name'

export interface ShelfStats {
  total: number
  reading: number
  finished: number
  groups: number
}

export type ShelfBlock =
  | { kind: 'masthead'; stats: ShelfStats }
  | { kind: 'headline'; hero: BookItem; side: BookItem[] }
  | { kind: 'duo'; items: BookItem[] }
  | { kind: 'note'; text: string }
  | { kind: 'grid'; items: BookItem[]; numbered: boolean; size: ShelfCardSize }
  | { kind: 'masonry'; items: BookItem[] }
  | { kind: 'stage'; hero: BookItem; stats: ShelfStats }
  | { kind: 'target'; hero: BookItem; stats: ShelfStats }

/** 编号栏每辑容纳的书目数（超出后插入新的辑刊分隔条） */
const INDEX_CHUNK = 8

/** 主题 → 版面类型。经典原生固定使用统一书架网格（不含任何主题化版面） */
export const getShelfLayoutKind = (theme: AppThemeType): ShelfLayoutKind => {
  switch (theme) {
    case 'azure':
      return 'magazine'
    case 'sakura':
      return 'masonry'
    case 'aurora':
      return 'stage'
    case 'obsidian':
      return 'deck'
    case 'classic':
    default:
      return 'uniform'
  }
}

export const isReadingBook = (book: BookItem): boolean =>
  typeof book.durChapterIndex === 'number' &&
  book.durChapterIndex > 0 &&
  getProgressPercent(book) < 100

export const isFinishedBook = (book: BookItem): boolean =>
  typeof book.durChapterIndex === 'number' && getProgressPercent(book) >= 100

export const computeShelfStats = (books: BookItem[]): ShelfStats => ({
  total: books.length,
  reading: books.filter(isReadingBook).length,
  finished: books.filter(isFinishedBook).length,
  groups: new Set(books.map(b => b.groupName || '默认分组')).size,
})

/**
 * 头条书：手动置顶 > 在读 > 书架第一本
 * 规则稳定：只有当置顶集合或在读书变化时才会切换大卡
 */
export const pickProminentBook = (books: BookItem[]): BookItem | null => {
  if (!books.length) return null
  return books.find(b => b.pinned) || books.find(isReadingBook) || books[0]
}

/** 按用户选择重排书架（默认顺序由服务端给出：置顶优先 + 加入时间倒序） */
export const sortShelfBooks = (books: BookItem[], mode: ShelfSortMode): BookItem[] => {
  if (mode === 'default') return books
  const list = [...books]
  switch (mode) {
    case 'recent':
      return list.sort((a, b) => (b.lastReadAt || 0) - (a.lastReadAt || 0))
    case 'added':
      return list.sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0))
    case 'progress':
      return list.sort((a, b) => getProgressPercent(b) - getProgressPercent(a))
    case 'name':
      return list.sort((a, b) => a.name.localeCompare(b.name, 'zh-Hans-CN'))
    default:
      return list
  }
}

const noteText = (stats: ShelfStats): string =>
  `EDITOR'S NOTE · 在架 ${stats.total} 册 · 在读 ${stats.reading} 册 · 已读 ${stats.finished} 册`

/**
 * 蔚蓝画报：杂志头版编排
 * 刊头 → 头条（大特稿 + 竖排小卡）→ 双栏特写 → 分隔条 → 编号栏目（每 8 册一辑）
 */
export function buildMagazineBlocks(books: BookItem[]): ShelfBlock[] {
  if (!books.length) return []

  const stats = computeShelfStats(books)
  const blocks: ShelfBlock[] = [{ kind: 'masthead', stats }]

  // 书太少时直接退化为编号栏，避免出现空洞版面
  if (books.length < 3) {
    blocks.push({ kind: 'grid', items: [...books], numbered: true, size: 'standard' })
    return blocks
  }

  const hero = pickProminentBook(books)
  if (!hero) return blocks

  const rest = books.filter(b => b.bookUrl !== hero.bookUrl)
  const side = rest.slice(0, 2)
  blocks.push({ kind: 'headline', hero, side })

  let cursor = side.length
  if (rest.length - cursor >= 2) {
    blocks.push({ kind: 'duo', items: rest.slice(cursor, cursor + 2) })
    cursor += 2
  }

  const tail = rest.slice(cursor)
  if (!tail.length) return blocks

  blocks.push({ kind: 'note', text: noteText(stats) })
  for (let i = 0; i < tail.length; i += INDEX_CHUNK) {
    const chunk = tail.slice(i, i + INDEX_CHUNK)
    blocks.push({ kind: 'grid', items: chunk, numbered: true, size: 'standard' })
    const remain = tail.length - (i + INDEX_CHUNK)
    if (remain > 0) {
      blocks.push({
        kind: 'note',
        text: `CONTINUED · 续见下辑 · 余 ${remain} 册`,
      })
    }
  }

  return blocks
}

/** 版面展示顺序 → 连续编号（画报的 №01、№02 … 与视觉顺序一致） */
export function buildNumberMap(blocks: ShelfBlock[]): Map<string, number> {
  const map = new Map<string, number>()
  let seq = 0
  const assign = (book: BookItem) => {
    if (!map.has(book.bookUrl)) map.set(book.bookUrl, ++seq)
  }
  for (const block of blocks) {
    if (block.kind === 'headline') {
      assign(block.hero)
      block.side.forEach(assign)
    } else if (block.kind === 'stage' || block.kind === 'target') {
      assign(block.hero)
    } else if (block.kind === 'duo' || block.kind === 'grid' || block.kind === 'masonry') {
      block.items.forEach(assign)
    }
  }
  return map
}

/**
 * 浅樱落雪：拍立得瀑布流
 * 不做分栏切块，交给 CSS 多列瀑布流自然错落，卡片高度与倾角由卡片自身决定
 */
export function buildMasonryBlocks(books: BookItem[]): ShelfBlock[] {
  if (!books.length) return []
  return [{ kind: 'masonry', items: [...books] }]
}

/** 星河极光：顶部星核展台 + 常规星图网格 */
export function buildStageBlocks(books: BookItem[]): ShelfBlock[] {
  if (!books.length) return []
  const stats = computeShelfStats(books)
  const hero = pickProminentBook(books)
  if (!hero || books.length < 3) {
    return [{ kind: 'grid', items: [...books], numbered: false, size: 'standard' }]
  }
  const rest = books.filter(b => b.bookUrl !== hero.bookUrl)
  return [
    { kind: 'stage', hero, stats },
    { kind: 'grid', items: rest, numbered: false, size: 'standard' },
  ]
}

/** 黑曜赛博：作战目标舱 + 编号数据格 */
export function buildDeckBlocks(books: BookItem[]): ShelfBlock[] {
  if (!books.length) return []
  const stats = computeShelfStats(books)
  const hero = pickProminentBook(books)
  if (!hero || books.length < 3) {
    return [{ kind: 'grid', items: [...books], numbered: true, size: 'compact' }]
  }
  const rest = books.filter(b => b.bookUrl !== hero.bookUrl)
  return [
    { kind: 'target', hero, stats },
    { kind: 'grid', items: rest, numbered: true, size: 'compact' },
  ]
}

/** 统一入口：按主题版面类型编排 */
export function buildShelfBlocks(books: BookItem[], theme: AppThemeType): ShelfBlock[] {
  switch (getShelfLayoutKind(theme)) {
    case 'magazine':
      return buildMagazineBlocks(books)
    case 'masonry':
      return buildMasonryBlocks(books)
    case 'stage':
      return buildStageBlocks(books)
    case 'deck':
      return buildDeckBlocks(books)
    default:
      return []
  }
}
