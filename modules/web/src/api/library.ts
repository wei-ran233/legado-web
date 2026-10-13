import ajax from './axios'

/**
 * 多用户书城业务 API（书架 / 搜索 / 目录 / 正文 / 进度 / 书源）
 */

export interface BookItem {
  bookUrl: string
  name: string
  author: string
  coverUrl: string
  kind?: string | null
  intro?: string | null
  totalChapterNum: number
  latestChapterTitle?: string | null
  groupName?: string
  durChapterIndex?: number
  durChapterTitle?: string
  type?: number
  /** 是否手动置顶（置顶书籍优先占据版面大卡位置） */
  pinned?: boolean
  /** 加入书架时间（epoch 秒） */
  addedAt?: number
  /** 最近阅读时间（epoch 秒，取自个人阅读进度） */
  lastReadAt?: number
}

export interface SearchBookItem {
  name: string
  author: string
  kind?: string | null
  intro?: string | null
  coverUrl?: string | null
  latestChapter?: string | null
  latestChapterTitle?: string | null
  bookUrl: string
  origin: string
  originName: string
  added?: boolean
  type?: number
  sourceType?: number
  availabilityScore?: number
}

export interface ChapterItem {
  index: number
  title: string
  isVolume: boolean
  wordCount?: string | null
}

export interface ContentPayload {
  index: number
  title: string
  content: string
}

// ===== 书架 =====
export const getBookshelf = async (): Promise<BookItem[]> => {
  const { data } = await ajax.get('/bookshelf')
  return data.data ?? []
}

/** 搜索结果加入书架（会自动抓取详情与目录入全局馆藏并关联用户个人书架） */
export const addToShelf = async (book: SearchBookItem | { bookUrl: string }) => {
  const { data } = await ajax.post('/bookshelf', book)
  return data
}

/** 准备阅读（预解析目录与正文，存入全局馆藏，但不加入个人书架） */
export const prepareBook = async (book: SearchBookItem | { bookUrl: string }) => {
  const { data } = await ajax.post('/book/prepare', book)
  return data
}

/** 检查书籍是否在当前用户书架上 */
export const checkOnShelf = async (bookUrl: string): Promise<boolean> => {
  try {
    const { data } = await ajax.get(`/bookshelf/has?bookUrl=${encodeURIComponent(bookUrl)}`)
    return !!data.data?.onShelf
  } catch {
    return false
  }
}

/** 修改书籍自定义封面 */
export const updateBookCover = async (bookUrl: string, coverUrl: string) => {
  const { data } = await ajax.post('/book/cover', { bookUrl, coverUrl })
  return data
}

export const removeFromShelf = async (bookUrl: string) => {
  const { data } = await ajax.delete('/bookshelf', { params: { bookUrl } })
  return data
}

/** 手动置顶 / 取消置顶 */
export const setBookPinned = async (bookUrl: string, pinned: boolean) => {
  const { data } = await ajax.post('/bookshelf/pin', { bookUrl, pinned })
  return data
}

// ===== 搜索（SSE 流式） =====
export function searchStream(
  key: string,
  handlers: {
    onStart?: (sourceCount: number) => void
    onBooks?: (originName: string, books: SearchBookItem[]) => void
    onEmpty?: (originName: string) => void
    onError?: (originName: string, error: string) => void
    onDone?: () => void
  },
  options?: {
    precision?: boolean
    category?: number
  },
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('legado_access_token') || ''
  fetch('/api/search/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      key,
      precision: options?.precision ?? true,
      category: options?.category,
    }),
    signal: controller.signal,
  })
    .then(async resp => {
      if (!resp.ok || !resp.body) throw new Error(`HTTP ${resp.status}`)
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''
        for (const evt of events) {
          const lines = evt.split('\n')
          const event = lines.find(l => l.startsWith('event:'))?.slice(6).trim()
          const dataLine = lines.find(l => l.startsWith('data:'))?.slice(5).trim()
          if (!event || !dataLine) continue
          try {
            const payload = JSON.parse(dataLine)
            if (event === 'start') handlers.onStart?.(payload.sourceCount ?? 0)
            else if (event === 'books') handlers.onBooks?.(payload.originName, payload.books)
            else if (event === 'empty') handlers.onEmpty?.(payload.originName)
            else if (event === 'error') handlers.onError?.(payload.originName, payload.error)
            else if (event === 'done') handlers.onDone?.()
          } catch {
            /* 忽略坏帧 */
          }
        }
      }
      handlers.onDone?.()
    })
    .catch(err => {
      if (err.name !== 'AbortError') handlers.onDone?.()
    })
  return controller
}

// ===== 换源 =====
export interface ChangeSourcePayload {
  oldBookUrl?: string
  newBook: SearchBookItem
  currentChapterIndex?: number
  currentChapterTitle?: string
}

export interface ChangeSourceResult {
  newBookUrl: string
  name: string
  author: string
  chapterIndex: number
  chapterTitle: string
  type?: number
}

export const changeSource = async (payload: ChangeSourcePayload): Promise<ChangeSourceResult> => {
  const { data } = await ajax.post('/book/changeSource', payload)
  return data.data
}

// ===== 目录 / 正文 =====
export const getToc = async (bookUrl: string): Promise<ChapterItem[]> => {
  const { data } = await ajax.get(`/book/${encodeURIComponent(bookUrl)}/toc`)
  return data.data ?? []
}

export const getBookContent = async (bookUrl: string, index: number): Promise<ContentPayload> => {
  const { data } = await ajax.get('/book/content', { params: { bookUrl, index } })
  return data.data
}

// ===== 进度 =====
export interface ProgressPayload {
  bookUrl: string
  durChapterIndex: number
  durChapterPos: number
  durChapterTitle: string
  durChapterTime: number
}

export const saveProgress = (payload: ProgressPayload) =>
  ajax.post('/progress', payload)

/** 页面关闭时保底提交 */
export const saveProgressWithBeacon = (payload: ProgressPayload) => {
  const token = localStorage.getItem('legado_access_token') || ''
  const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' })
  return navigator.sendBeacon(`/api/progress?token=${encodeURIComponent(token)}`, blob)
}

export const getProgress = async (bookUrl: string) => {
  const { data } = await ajax.get('/progress', { params: { bookUrl } })
  return data.data as ProgressPayload | null
}

// ===== 书源（只读） =====
export interface SourcePart {
  bookSourceUrl: string
  bookSourceName: string
  bookSourceGroup: string | null
  enabled: boolean
  customOrder: number
  respondTime: number
}

export const listSources = async (enabledOnly = true): Promise<SourcePart[]> => {
  const { data } = await ajax.get('/sources', { params: { enabled: enabledOnly } })
  return data.data ?? []
}

// ===== 在线封面检索（推书君/有谱说） =====
export interface CoverCandidate {
  title: string
  author: string
  coverUrl: string
  source: string
}

export const searchCoverCandidates = async (name: string, author = ''): Promise<CoverCandidate[]> => {
  const { data } = await ajax.get('/book/cover/search', { params: { name, author } })
  return data.data ?? []
}
