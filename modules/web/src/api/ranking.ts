import ajax from './axios'

export interface ReadingRecordPayload {
  bookUrl: string
  bookName: string
  author: string
  durationSeconds: number
}

export interface ReadingStats {
  todayDuration: number // seconds
  totalDuration: number // seconds
  bookCount: number
  rank: number
  totalReaders: number
  participateRanking?: boolean
}

export interface UserRankItem {
  rank: number
  userId: number
  username: string
  nickname?: string
  displayName?: string
  role: string
  totalDuration: number // seconds
  bookCount: number
}

export interface BookRankItem {
  rank: number
  bookUrl: string
  name: string
  author: string
  coverUrl: string
  type: number
  latestChapterTitle?: string
  totalDuration: number // seconds
  readerCount: number
  shelfCount: number
  onShelf: boolean
}

export const recordReadingTime = (payload: ReadingRecordPayload) =>
  ajax.post('/reading/record', payload)

export const getReadingStats = async (): Promise<ReadingStats> => {
  const { data } = await ajax.get('/reading/stats')
  return data.data as ReadingStats
}

export const getUserRankings = async (period: 'today' | 'week' | 'total' = 'today'): Promise<UserRankItem[]> => {
  const { data } = await ajax.get('/ranking/users', { params: { period } })
  return data.data as UserRankItem[]
}

export const getBookRankings = async (type: 'duration' | 'popularity' = 'duration'): Promise<BookRankItem[]> => {
  const { data } = await ajax.get('/ranking/books', { params: { type } })
  return data.data as BookRankItem[]
}

export const resetRankings = async () => {
  const { data } = await ajax.post('/ranking/reset')
  return data
}

