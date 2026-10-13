import type { BookItem } from '@/api/library'

/**
 * 书籍展示相关的纯函数（书架卡片 / 焦点展台 / 换封面弹窗共用）
 */

export type CoverSource = Pick<BookItem, 'coverUrl' | 'name' | 'author'>

export const hashString = (value: string): number =>
  value.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0)

/** 无封面时按书名+作者生成的确定性渐变艺术书封 */
export const getDynamicCover = (name: string, author?: string | null): string => {
  const hash = hashString(name + (author || ''))
  const palettes = [
    ['#1e3c72', '#2a5298'],
    ['#2c3e50', '#3498db'],
    ['#134e5e', '#71b280'],
    ['#4b1248', '#f0c27b'],
    ['#3a1c71', '#d76d77'],
    ['#283048', '#859398'],
  ]
  const [c1, c2] = palettes[hash % palettes.length]
  const title = (name || '无名').slice(0, 8)
  const auth = (author || '佚名').slice(0, 8)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="140" height="196" viewBox="0 0 140 196">
    <defs>
      <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${c1}" />
        <stop offset="100%" stop-color="${c2}" />
      </linearGradient>
    </defs>
    <rect width="100%" height="100%" fill="url(#bg)" rx="6"/>
    <rect x="6" y="6" width="128" height="184" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="1.2" rx="4"/>
    <line x1="15" y1="6" x2="15" y2="190" stroke="rgba(0,0,0,0.25)" stroke-width="2"/>
    <text x="50%" y="42%" font-size="16" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="-apple-system, sans-serif">${title}</text>
    <line x1="40" y1="52%" x2="100" y2="52%" stroke="rgba(255,255,255,0.35)" stroke-width="1"/>
    <text x="50%" y="65%" font-size="11" fill="rgba(255,255,255,0.8)" text-anchor="middle" font-family="-apple-system, sans-serif">${auth}</text>
    <text x="50%" y="88%" font-size="9" fill="rgba(255,255,255,0.55)" text-anchor="middle" font-family="-apple-system, sans-serif">📖 经典典藏</text>
  </svg>`
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}

/** 统一封面地址：外链走服务端代理，本地/数据地址直出 */
export const formatCover = (book: CoverSource): string => {
  if (!book.coverUrl) return getDynamicCover(book.name, book.author)
  if (book.coverUrl.startsWith('data:') || book.coverUrl.startsWith('/api/cover')) {
    return book.coverUrl
  }
  if (book.coverUrl.startsWith('http://') || book.coverUrl.startsWith('https://')) {
    return `/api/cover?path=${encodeURIComponent(book.coverUrl)}`
  }
  return book.coverUrl
}

/** 阅读进度百分比（1~100，未开始为 0） */
export const getProgressPercent = (
  book: Pick<BookItem, 'durChapterIndex' | 'totalChapterNum'>,
): number => {
  if (typeof book.durChapterIndex !== 'number') return 0
  const total = book.totalChapterNum || 1
  return Math.min(100, Math.max(1, Math.round((((book.durChapterIndex || 0) + 1) / total) * 100)))
}

/** 简介：优先书源简介，缺失时按分类/作者/进度生成导读文案 */
export const getBookIntro = (
  book: Pick<BookItem, 'intro' | 'kind' | 'author' | 'durChapterTitle'>,
): string => {
  if (book.intro && book.intro.trim()) {
    return book.intro.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
  }
  const category = book.kind ? `【${book.kind}】` : ''
  const author = book.author ? `${book.author} 著作。` : ''
  const chapter = book.durChapterTitle
    ? `当前已读至「${book.durChapterTitle}」。`
    : '首卷待启，静候品读。'
  return `${category}${author}${chapter}全章节精校排版，沉浸式翻页阅读。`
}
