/**
 * 本地缓存工具（基于浏览器原生 IndexedDB）
 * 缓存书籍章节正文内容与换源搜索结果，提供 0ms 秒级即时加载与离线/低网速保障。
 */

const DB_NAME = 'legado_reader_db'
const DB_VERSION = 1
const STORE_CHAPTERS = 'chapter_content'
const STORE_SOURCES = 'book_sources'

export interface CachedChapter {
  url?: string
  bookUrl: string
  index: number
  title: string
  content: string
  cachedAt: number
}

export interface CachedSources {
  cacheKey: string
  bookName: string
  author: string
  sources: any[]
  updatedAt: number
}

// 内存级降级缓存（当浏览器处于隐身模式或 IndexedDB 被禁用时使用）
const memoryChapters = new Map<string, CachedChapter>()
const memorySources = new Map<string, CachedSources>()

let dbPromise: Promise<IDBDatabase | null> | null = null

function getDb(): Promise<IDBDatabase | null> {
  if (typeof window === 'undefined' || !window.indexedDB) {
    return Promise.resolve(null)
  }
  if (!dbPromise) {
    dbPromise = new Promise(resolve => {
      try {
        const req = window.indexedDB.open(DB_NAME, DB_VERSION)
        req.onupgradeneeded = () => {
          const db = req.result
          if (!db.objectStoreNames.contains(STORE_CHAPTERS)) {
            const store = db.createObjectStore(STORE_CHAPTERS, { keyPath: 'id' })
            store.createIndex('bookUrl', 'bookUrl', { unique: false })
          }
          if (!db.objectStoreNames.contains(STORE_SOURCES)) {
            db.createObjectStore(STORE_SOURCES, { keyPath: 'cacheKey' })
          }
        }
        req.onsuccess = () => resolve(req.result)
        req.onerror = () => {
          console.warn('[LocalCache] 打开 IndexedDB 失败，使用内存缓存降级')
          resolve(null)
        }
      } catch (err) {
        console.warn('[LocalCache] IndexedDB 初始化异常:', err)
        resolve(null)
      }
    })
  }
  return dbPromise
}

/** ===== 1. 书籍章节正文缓存与字体反混淆适配 ===== */

const PUA_START = 0xE800
const PUA_MAPPING =
  '的一是了我不人在他有这个上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美乳阴液茎欲呻肉交性胸私穴淫臀舔射脱裸骚唇'
const PUA_END = PUA_START + PUA_MAPPING.length - 1

export function isAntiBotText(text: string): boolean {
  if (!text) return false
  return (
    text.includes('为了使用完整的阅读功能') ||
    text.includes('章节内容不支持该浏览器显示') ||
    text.includes('请考虑使用〔Chrome') ||
    text.includes('请考虑使用〔Safari') ||
    text.includes('请考虑使用〔Edge')
  )
}

export function adaptFontText(text: string): string {
  if (!text) return ''
  let hasPua = false
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    if (code >= PUA_START && code <= PUA_END) {
      hasPua = true
      break
    }
  }
  let converted = text
  if (hasPua) {
    const arr: string[] = []
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i)
      if (code >= PUA_START && code <= PUA_END) {
        arr.push(PUA_MAPPING[code - PUA_START])
      } else {
        arr.push(text[i])
      }
    }
    converted = arr.join('')
  }
  if (
    converted.includes('为了使用完整的阅读功能') ||
    converted.includes('章节内容不支持该浏览器显示') ||
    converted.includes('(ò﹏ò)') ||
    converted.includes('（内容加载失败！）')
  ) {
    converted = converted
      .replace(/^.*(为了使用完整的阅读功能|章节内容不支持该浏览器显示|请考虑使用.*?(Chrome|Safari|Edge|浏览器)|原生浏览器阅读).*$/gm, '')
      .replace(/^.*\(ò﹏ò\).*$/gm, '')
      .replace(/^.*（内容加载失败！\).*$/gm, '')
      .replace(/^.*谢谢!!!.*$/gm, '')
      .trimEnd()
  }
  return converted
}

export async function saveCachedChapter(
  bookUrl: string,
  index: number,
  title: string,
  content: string,
  url?: string,
): Promise<void> {
  const cleanContent = adaptFontText(content)
  const id = `${bookUrl}__${index}`
  const entry: CachedChapter = {
    url,
    bookUrl,
    index,
    title,
    content: cleanContent,
    cachedAt: Date.now(),
  }

  memoryChapters.set(id, entry)

  const db = await getDb()
  if (!db) return

  return new Promise(resolve => {
    try {
      const tx = db.transaction(STORE_CHAPTERS, 'readwrite')
      const store = tx.objectStore(STORE_CHAPTERS)
      store.put({ ...entry, id })
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
    } catch {
      resolve()
    }
  })
}

export async function getCachedChapter(
  bookUrl: string,
  index: number,
): Promise<Pick<CachedChapter, 'index' | 'title' | 'content' | 'url'> | null> {
  const id = `${bookUrl}__${index}`
  const mem = memoryChapters.get(id)
  if (mem) {
    if (isAntiBotText(mem.content)) {
      memoryChapters.delete(id)
      return null
    }
    return { index: mem.index, title: mem.title, content: adaptFontText(mem.content), url: mem.url }
  }

  const db = await getDb()
  if (!db) return null

  return new Promise(resolve => {
    try {
      const tx = db.transaction(STORE_CHAPTERS, 'readonly')
      const store = tx.objectStore(STORE_CHAPTERS)
      const req = store.get(id)
      req.onsuccess = () => {
        const val = req.result as CachedChapter | undefined
        if (val) {
          if (isAntiBotText(val.content)) {
            // 损坏的反爬缓存，从内存与数据库淘汰，触发重新拉取
            memoryChapters.delete(id)
            try {
              const delTx = db.transaction(STORE_CHAPTERS, 'readwrite')
              delTx.objectStore(STORE_CHAPTERS).delete(id)
            } catch { /* ignore */ }
            resolve(null)
            return
          }
          const adapted = adaptFontText(val.content)
          memoryChapters.set(id, { ...val, content: adapted })
          resolve({ index: val.index, title: val.title, content: adapted, url: val.url })
        } else {
          resolve(null)
        }
      }
      req.onerror = () => resolve(null)
    } catch {
      resolve(null)
    }
  })
}

export async function hasCachedChapter(bookUrl: string, index: number): Promise<boolean> {
  const id = `${bookUrl}__${index}`
  if (memoryChapters.has(id)) return true
  const c = await getCachedChapter(bookUrl, index)
  return c !== null
}

export async function clearBookChapters(bookUrl: string): Promise<void> {
  const prefix = `${bookUrl}__`
  for (const key of memoryChapters.keys()) {
    if (key.startsWith(prefix)) {
      memoryChapters.delete(key)
    }
  }

  const db = await getDb()
  if (!db) return

  return new Promise(resolve => {
    try {
      const tx = db.transaction(STORE_CHAPTERS, 'readwrite')
      const store = tx.objectStore(STORE_CHAPTERS)
      const index = store.index('bookUrl')
      const req = index.getAllKeys(IDBKeyRange.only(bookUrl))
      req.onsuccess = () => {
        const keys = req.result || []
        for (const k of keys) {
          store.delete(k)
        }
        resolve()
      }
      req.onerror = () => resolve()
    } catch {
      resolve()
    }
  })
}

/** ===== 2. 换源候选书源缓存 ===== */

function makeSourceKey(bookName: string, author?: string): string {
  const b = (bookName || '').trim()
  const a = (author || '').trim()
  return a ? `${b}:::${a}` : b
}

export async function saveCachedSources(bookName: string, author: string, sources: any[]): Promise<void> {
  const key = makeSourceKey(bookName, author)
  if (!key || sources.length === 0) return

  const entry: CachedSources = {
    cacheKey: key,
    bookName: (bookName || '').trim(),
    author: (author || '').trim(),
    sources,
    updatedAt: Date.now(),
  }

  memorySources.set(key, entry)

  const db = await getDb()
  if (!db) return

  return new Promise(resolve => {
    try {
      const tx = db.transaction(STORE_SOURCES, 'readwrite')
      const store = tx.objectStore(STORE_SOURCES)
      store.put(entry)
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
    } catch {
      resolve()
    }
  })
}

export async function getCachedSources(bookName: string, author?: string): Promise<any[] | null> {
  const key = makeSourceKey(bookName, author)
  if (!key) return null

  const mem = memorySources.get(key)
  if (mem && mem.sources && mem.sources.length > 0) {
    return mem.sources
  }

  const db = await getDb()
  if (!db) return null

  return new Promise(resolve => {
    try {
      const tx = db.transaction(STORE_SOURCES, 'readonly')
      const store = tx.objectStore(STORE_SOURCES)
      const req = store.get(key)
      req.onsuccess = () => {
        const val = req.result as CachedSources | undefined
        if (val && Array.isArray(val.sources) && val.sources.length > 0) {
          memorySources.set(key, val)
          resolve(val.sources)
        } else {
          // 兜底：如果带 author 没搜到，再尝试纯 bookName
          if (author && author.trim()) {
            const fallbackKey = makeSourceKey(bookName)
            const fallbackReq = store.get(fallbackKey)
            fallbackReq.onsuccess = () => {
              const fbVal = fallbackReq.result as CachedSources | undefined
              if (fbVal && Array.isArray(fbVal.sources) && fbVal.sources.length > 0) {
                memorySources.set(fallbackKey, fbVal)
                resolve(fbVal.sources)
              } else {
                resolve(null)
              }
            }
            fallbackReq.onerror = () => resolve(null)
          } else {
            resolve(null)
          }
        }
      }
      req.onerror = () => resolve(null)
    } catch {
      resolve(null)
    }
  })
}
