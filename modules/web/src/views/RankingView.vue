<template>
  <div class="ranking-view" v-loading="loading">
    <!-- 顶部个人阅读成就卡片 -->
    <div class="achievement-card">
      <div class="achievement-header">
        <div class="user-welcome">
          <span class="greeting">📖 阅读成就 · 坚持成就卓越</span>
          <span class="sub-greeting">记录每一次指尖翻页的美好时光</span>
        </div>
        <div class="rank-badge-box" v-if="userStats">
          <span class="rank-badge-label">全站读者榜</span>
          <span class="rank-badge-val" v-if="userStats.participateRanking !== false && userStats.rank > 0">第 {{ userStats.rank }} 名</span>
          <span class="rank-badge-val opted-out" v-else-if="userStats.participateRanking === false" title="您已在【我的】页面关闭榜单参与">已隐藏 (未上榜)</span>
          <span class="rank-badge-val opted-out" v-else>暂未上榜</span>
        </div>
      </div>

      <div class="stats-grid" v-if="userStats">
        <div class="stat-item">
          <div class="stat-icon-wrap today">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-label">今日阅读</div>
            <div class="stat-val">{{ formatDuration(userStats.todayDuration) }}</div>
          </div>
        </div>

        <div class="stat-item">
          <div class="stat-icon-wrap total">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-label">累计阅读</div>
            <div class="stat-val">{{ formatDuration(userStats.totalDuration) }}</div>
          </div>
        </div>

        <div class="stat-item">
          <div class="stat-icon-wrap books">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-label">读过书籍</div>
            <div class="stat-val">{{ userStats.bookCount }} <span class="unit">本</span></div>
          </div>
        </div>

        <div class="stat-item">
          <div class="stat-icon-wrap community">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-label">全站读者</div>
            <div class="stat-val">{{ userStats.totalReaders }} <span class="unit">位</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 排行榜主体切换 -->
    <div class="ranking-tabs-wrap">
      <div class="tab-switch-bar">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'readers' }"
          @click="activeTab = 'readers'"
        >
          <el-icon><Trophy /></el-icon>&nbsp;读者阅读时长榜
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'books' }"
          @click="activeTab = 'books'"
        >
          <el-icon><Reading /></el-icon>&nbsp;热门阅读书籍榜
        </button>
      </div>

      <!-- 1. 读者时长榜 -->
      <div v-if="activeTab === 'readers'" class="ranking-panel" v-loading="usersLoading">
        <div class="filter-row">
          <el-radio-group v-model="period" size="default" @change="loadUsers">
            <el-radio-button value="today" label="today">今日榜</el-radio-button>
            <el-radio-button value="week" label="week">本周榜</el-radio-button>
            <el-radio-button value="total" label="total">总时长榜</el-radio-button>
          </el-radio-group>
          <div class="filter-actions">
            <span class="refresh-hint" @click="loadUsers">
              <el-icon><Refresh /></el-icon>&nbsp;刷新榜单
            </span>
            <el-button
              v-if="authStore.isAdmin"
              size="small"
              type="danger"
              plain
              class="reset-btn"
              :loading="resetting"
              @click="onResetRanking"
            >
              <el-icon><Delete /></el-icon>&nbsp;重置全站榜单
            </el-button>
          </div>
        </div>

        <el-empty v-if="!loading && userRankings.length === 0" description="暂无阅读记录，快去开启阅读吧！" />

        <template v-else>
          <!-- 3D 阶梯领奖台 (Aurora, Azure & Sakura) -->
          <div v-if="showPodium" class="ranking-podium-wrap">
            <!-- 第二名 -->
            <div v-if="topThreeUsers[1]" class="podium-stand podium-second">
              <div class="podium-avatar-wrap">
                <el-avatar :size="48">
                  {{ (topThreeUsers[1].displayName || topThreeUsers[1].username || 'U').slice(0, 1).toUpperCase() }}
                </el-avatar>
              </div>
              <div class="podium-name">{{ topThreeUsers[1].displayName || topThreeUsers[1].username }}</div>
              <div class="podium-score">{{ formatDuration(topThreeUsers[1].totalDuration) }}</div>
              <span class="podium-rank-badge badge-silver">🥈 亚军</span>
            </div>

            <!-- 第一名 -->
            <div v-if="topThreeUsers[0]" class="podium-stand podium-first">
              <div class="podium-crown">👑</div>
              <div class="podium-avatar-wrap">
                <el-avatar :size="56" style="border: 2px solid #fbbf24;">
                  {{ (topThreeUsers[0].displayName || topThreeUsers[0].username || 'U').slice(0, 1).toUpperCase() }}
                </el-avatar>
              </div>
              <div class="podium-name">{{ topThreeUsers[0].displayName || topThreeUsers[0].username }}</div>
              <div class="podium-score">{{ formatDuration(topThreeUsers[0].totalDuration) }}</div>
              <span class="podium-rank-badge badge-gold">🥇 榜首魁首</span>
            </div>

            <!-- 第三名 -->
            <div v-if="topThreeUsers[2]" class="podium-stand podium-third">
              <div class="podium-avatar-wrap">
                <el-avatar :size="44">
                  {{ (topThreeUsers[2].displayName || topThreeUsers[2].username || 'U').slice(0, 1).toUpperCase() }}
                </el-avatar>
              </div>
              <div class="podium-name">{{ topThreeUsers[2].displayName || topThreeUsers[2].username }}</div>
              <div class="podium-score">{{ formatDuration(topThreeUsers[2].totalDuration) }}</div>
              <span class="podium-rank-badge badge-bronze">🥉 季军</span>
            </div>
          </div>

          <div class="rank-list" v-if="remainingUsers.length > 0">
            <div
              v-for="item in remainingUsers"
              :key="item.userId"
              class="rank-item user-item"
              :class="{ 'top-one': item.rank === 1, 'top-two': item.rank === 2, 'top-three': item.rank === 3 }"
            >
              <!-- 排名标识 -->
              <div class="rank-number-box">
                <template v-if="themeStore.appTheme === 'sakura'">
                  <span v-if="item.rank === 1" class="medal gold" style="font-size: 13px; font-weight: 700; color: #e11d48;">🌸 魁首</span>
                  <span v-else-if="item.rank === 2" class="medal silver" style="font-size: 13px; font-weight: 700; color: #f43f5e;">🌸 榜眼</span>
                  <span v-else-if="item.rank === 3" class="medal bronze" style="font-size: 13px; font-weight: 700; color: #fb7185;">🌸 探花</span>
                  <span v-else class="rank-num" style="color: #e11d48;">{{ item.rank }}</span>
                </template>
                <template v-else-if="themeStore.appTheme === 'obsidian'">
                  <span v-if="item.rank === 1" class="medal gold" style="font-family: monospace; font-size: 12px; color: #fbbf24;">[APEX]</span>
                  <span v-else-if="item.rank === 2" class="medal silver" style="font-family: monospace; font-size: 12px; color: #06b6d4;">[ALPHA]</span>
                  <span v-else-if="item.rank === 3" class="medal bronze" style="font-family: monospace; font-size: 12px; color: #a855f7;">[BETA]</span>
                  <span v-else class="rank-num" style="font-family: monospace;">#0{{ item.rank }}</span>
                </template>
                <template v-else>
                  <span v-if="item.rank === 1" class="medal gold" title="冠军">🥇</span>
                  <span v-else-if="item.rank === 2" class="medal silver" title="亚军">🥈</span>
                  <span v-else-if="item.rank === 3" class="medal bronze" title="季军">🥉</span>
                  <span v-else class="rank-num">{{ item.rank }}</span>
                </template>
              </div>

            <!-- 用户头像与信息 -->
            <div class="user-avatar-wrap">
              <el-avatar :size="42" class="user-avatar">
                {{ (item.displayName || item.nickname || item.username || 'U').slice(0, 1).toUpperCase() }}
              </el-avatar>
            </div>

            <div class="user-info-meta">
              <div class="user-name-row">
                <span class="username">{{ item.displayName || item.nickname || item.username }}</span>
                <span v-if="item.nickname && item.nickname.trim() && item.nickname.trim() !== item.username" class="user-account-tag">@{{ item.username }}</span>
                <el-tag size="small" :type="item.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
                  {{ item.role === 'ADMIN' ? '管理员' : '书友' }}
                </el-tag>
              </div>
              <div class="user-sub-desc">已读过 {{ item.bookCount }} 本书籍</div>
            </div>

            <!-- 阅读时长展示 -->
            <div class="rank-stat-data">
              <span class="stat-num">{{ formatDuration(item.totalDuration) }}</span>
              <span class="stat-caption">阅读时长</span>
            </div>
          </div>
        </div>
      </template>
    </div>

      <!-- 2. 热门书籍榜 -->
      <div v-else-if="activeTab === 'books'" class="ranking-panel" v-loading="booksLoading">
        <div class="filter-row">
          <el-radio-group v-model="bookRankType" size="default" @change="loadBooks">
            <el-radio-button value="duration" label="duration">阅读时长榜</el-radio-button>
            <el-radio-button value="popularity" label="popularity">在读书友榜</el-radio-button>
          </el-radio-group>
          <div class="filter-actions">
            <span class="refresh-hint" @click="loadBooks">
              <el-icon><Refresh /></el-icon>&nbsp;刷新榜单
            </span>
            <el-button
              v-if="authStore.isAdmin"
              size="small"
              type="danger"
              plain
              class="reset-btn"
              :loading="resetting"
              @click="onResetRanking"
            >
              <el-icon><Delete /></el-icon>&nbsp;重置全站榜单
            </el-button>
          </div>
        </div>

        <el-empty v-if="!loading && bookRankings.length === 0" description="暂无热门书籍统计" />

        <div class="rank-list" v-else>
          <div
            v-for="book in bookRankings"
            :key="book.bookUrl"
            class="rank-item book-item"
            :class="{ 'top-one': book.rank === 1, 'top-two': book.rank === 2, 'top-three': book.rank === 3 }"
          >
            <!-- 排名角标 -->
            <div class="rank-number-box">
              <span v-if="book.rank === 1" class="medal gold">🥇</span>
              <span v-else-if="book.rank === 2" class="medal silver">🥈</span>
              <span v-else-if="book.rank === 3" class="medal bronze">🥉</span>
              <span v-else class="rank-num">{{ book.rank }}</span>
            </div>

            <!-- 书籍封面（严格尺寸约束） -->
            <div class="rank-book-cover-box" @click="toReader(book)">
              <img
                class="rank-book-cover"
                :src="book.coverUrl || getDynamicCover(book.name, book.author)"
                loading="lazy"
                @error="($event.target as HTMLImageElement).src = getDynamicCover(book.name, book.author)"
              />
              <el-tag size="small" effect="dark" v-if="book.type === 1 || book.type === 32 || ((book.type || 0) & 32) === 32" class="cover-tag audio">有声</el-tag>
              <el-tag size="small" effect="dark" v-else-if="book.type === 2 || book.type === 64 || ((book.type || 0) & 64) === 64" class="cover-tag comic">漫画</el-tag>
              <el-tag size="small" effect="dark" v-else-if="book.type === 3 || book.type === 4 || ((book.type || 0) & 4) === 4" class="cover-tag video">视频</el-tag>
            </div>

            <!-- 书籍详情 -->
            <div class="book-details" @click="toReader(book)">
              <div class="book-title-row">
                <span class="book-title" :title="book.name">{{ book.name }}</span>
              </div>
              <div class="book-author" :title="book.author">{{ book.author || '未知作者' }}</div>
              <div class="book-stats-row">
                <span class="stat-badge duration">
                  <el-icon><Clock /></el-icon>&nbsp;{{ formatDuration(book.totalDuration) }}
                </span>
                <span class="stat-badge readers" v-if="book.readerCount > 0">
                  <el-icon><User /></el-icon>&nbsp;{{ book.readerCount }} 位在读
                </span>
                <span class="stat-badge shelf" v-if="book.shelfCount > 0">
                  <el-icon><Collection /></el-icon>&nbsp;{{ book.shelfCount }} 人在架
                </span>
              </div>
              <div class="book-latest" v-if="book.latestChapterTitle" :title="book.latestChapterTitle">
                最新：{{ book.latestChapterTitle }}
              </div>
            </div>

            <!-- 快捷操作按钮 -->
            <div class="book-actions">
              <el-button
                v-if="!book.onShelf"
                size="small"
                type="primary"
                plain
                :loading="addingMap[book.bookUrl]"
                @click.stop="onAddShelf(book)"
              >
                + 书架
              </el-button>
              <el-button
                size="small"
                type="success"
                @click.stop="toReader(book)"
              >
                阅读
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'RankingView',
})

import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Trophy, Reading, Clock, TrendCharts, Collection, User, Refresh, Delete,
} from '@element-plus/icons-vue'
import {
  getReadingStats, getUserRankings, getBookRankings, resetRankings,
  type ReadingStats, type UserRankItem, type BookRankItem,
} from '@/api/ranking'
import { addToShelf } from '@/api/library'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const loading = ref(false)
const usersLoading = ref(false)
const booksLoading = ref(false)
const resetting = ref(false)
const activeTab = ref<'readers' | 'books'>('readers')
const period = ref<'today' | 'week' | 'total'>('today')
const bookRankType = ref<'duration' | 'popularity'>('duration')

const userStats = ref<ReadingStats | null>(null)
const userRankings = ref<UserRankItem[]>([])
const bookRankings = ref<BookRankItem[]>([])
const addingMap = ref<Record<string, boolean>>({})

const topThreeUsers = computed(() => userRankings.value.slice(0, 3))

// 仅在支持 3D 领奖台的主题下，且榜单至少有 3 位读者时展示完整领奖台
const showPodium = computed(() => {
  const isPodiumTheme = themeStore.appTheme === 'aurora' || themeStore.appTheme === 'azure' || themeStore.appTheme === 'sakura'
  return isPodiumTheme && userRankings.value.length >= 3
})

// 若展示了领奖台，列表中展示第 4 名及以后的读者；若未展示领奖台（如仅有 1~2 位读者或极简主题），列表中展示全量读者
const remainingUsers = computed(() => {
  if (showPodium.value) {
    return userRankings.value.slice(3)
  }
  return userRankings.value
})

const onResetRanking = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要将全站排行榜清零重置吗？此操作将清空所有用户的阅读时长及榜单数据，且不可撤销。',
      '重置全站排行榜',
      {
        confirmButtonText: '确定重置',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  resetting.value = true
  try {
    await resetRankings()
    ElMessage.success('全站排行榜已成功清零重置！')
    await Promise.all([loadStats(), loadUsers(), loadBooks()])
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.errorMsg || '重置排行榜失败')
  } finally {
    resetting.value = false
  }
}

// 格式化秒数为直观时间字符串
const formatDuration = (totalSeconds: number): string => {
  if (!totalSeconds || totalSeconds <= 0) return '0 分钟'
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`
  }
  return `${Math.max(1, minutes)} 分钟`
}

// 兜底艺术书封
const getDynamicCover = (name: string, author: string): string => {
  const hash = (name + (author || '')).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  const palettes = [
    ['#1e3c72', '#2a5298'],
    ['#2c3e50', '#3498db'],
    ['#134e5e', '#71b280'],
    ['#4b1248', '#f0c27b'],
    ['#3a1c71', '#d76d77'],
  ]
  const [c1, c2] = palettes[hash % palettes.length]
  const title = (name || '书籍').slice(0, 8)
  const auth = (author || '作者').slice(0, 8)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="140" height="196" viewBox="0 0 140 196">
    <defs>
      <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${c1}" />
        <stop offset="100%" stop-color="${c2}" />
      </linearGradient>
    </defs>
    <rect width="100%" height="100%" fill="url(#g)" rx="6"/>
    <rect x="6" y="6" width="128" height="184" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="1.2" rx="4"/>
    <text x="50%" y="42%" font-size="16" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="-apple-system, sans-serif">${title}</text>
    <text x="50%" y="65%" font-size="11" fill="rgba(255,255,255,0.8)" text-anchor="middle" font-family="-apple-system, sans-serif">${auth}</text>
    <text x="50%" y="88%" font-size="9" fill="rgba(255,255,255,0.55)" text-anchor="middle">📖 热门藏书</text>
  </svg>`
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}

const loadStats = async () => {
  try {
    userStats.value = await getReadingStats()
  } catch (err) {
    console.error('加载阅读成就失败:', err)
  }
}

const loadUsers = async () => {
  usersLoading.value = true
  try {
    userRankings.value = await getUserRankings(period.value)
  } catch (err) {
    console.error('加载读者榜失败:', err)
  } finally {
    usersLoading.value = false
  }
}

const loadBooks = async () => {
  booksLoading.value = true
  try {
    bookRankings.value = await getBookRankings(bookRankType.value)
  } catch (err) {
    console.error('加载图书榜失败:', err)
  } finally {
    booksLoading.value = false
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadUsers(), loadBooks()])
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
onActivated(loadAll)

const toReader = (book: BookRankItem) => {
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

const onAddShelf = async (book: BookRankItem) => {
  addingMap.value[book.bookUrl] = true
  try {
    const res = await addToShelf({
      name: book.name,
      author: book.author,
      bookUrl: book.bookUrl,
      origin: '',
      originName: '热门榜',
      coverUrl: book.coverUrl,
      type: book.type,
    })
    if (res.isSuccess) {
      book.onShelf = true
      ElMessage.success(`《${book.name}》已加入书架`)
    } else {
      ElMessage.error(res.errorMsg || '加入书架失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.errorMsg || '加入书架失败')
  } finally {
    addingMap.value[book.bookUrl] = false
  }
}
</script>

<style scoped>
.ranking-view {
  padding: 16px;
  max-width: 1100px;
  margin: 0 auto;
  min-height: 80vh;
  box-sizing: border-box;
}

/* 顶部个人成就卡片 */
.achievement-card {
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 60%, #3498db 100%);
  border-radius: 14px;
  padding: 22px 24px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(30, 60, 114, 0.25);
  margin-bottom: 20px;
}

.achievement-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.user-welcome {
  display: flex;
  flex-direction: column;
}

.greeting {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sub-greeting {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 3px;
}

.rank-badge-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  background: rgba(255, 255, 255, 0.16);
  padding: 6px 14px;
  border-radius: 20px;
  backdrop-filter: blur(8px);
}

.rank-badge-label {
  font-size: 10px;
  opacity: 0.8;
}

.rank-badge-val {
  font-size: 17px;
  font-weight: 800;
  color: #ffd700;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-item {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  backdrop-filter: blur(4px);
}

.stat-icon-wrap {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
}

.stat-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-label {
  font-size: 11px;
  opacity: 0.8;
}

.stat-val {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  margin-top: 2px;
}

.stat-val .unit {
  font-size: 11px;
  font-weight: 400;
  opacity: 0.85;
}

/* 标签切换栏 */
.tab-switch-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
  box-shadow: 0 3px 10px rgba(var(--el-color-primary-rgb), 0.3);
}

/* 面板内容 */
.ranking-panel {
  background: var(--el-bg-color);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: var(--el-box-shadow-lighter);
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.refresh-hint {
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
}

.refresh-hint:hover {
  opacity: 0.8;
}

.reset-btn {
  font-size: 12px;
}

/* 榜单列表卡片 */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.rank-item:hover {
  transform: translateX(2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

/* 前三名光彩点缀 */
.rank-item.top-one {
  background: linear-gradient(90deg, rgba(255, 215, 0, 0.08) 0%, transparent 80%);
  border-color: rgba(255, 215, 0, 0.4);
}

.rank-item.top-two {
  background: linear-gradient(90deg, rgba(192, 192, 192, 0.08) 0%, transparent 80%);
  border-color: rgba(192, 192, 192, 0.4);
}

.rank-item.top-three {
  background: linear-gradient(90deg, rgba(205, 127, 50, 0.08) 0%, transparent 80%);
  border-color: rgba(205, 127, 50, 0.4);
}

.rank-number-box {
  width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.medal {
  font-size: 22px;
}

.rank-num {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

/* 读者榜项 */
.user-avatar-wrap {
  flex-shrink: 0;
}

.user-avatar {
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
}

.user-info-meta {
  flex: 1;
  min-width: 0;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-account-tag {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-family: monospace;
}

.user-sub-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.rank-stat-data {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.stat-num {
  font-size: 16px;
  font-weight: 800;
  color: var(--el-color-primary);
}

.stat-caption {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* 图书榜项 */
.rank-book-cover-box {
  width: 50px;
  height: 70px;
  aspect-ratio: 5 / 7;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  background: var(--el-fill-color);
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.rank-book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-tag {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 8px;
  height: 14px;
  line-height: 14px;
  padding: 0 3px;
  border: none;
}

.cover-tag.audio { background: #e6a23c; }
.cover-tag.comic { background: #67c23a; }

.book-details {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.book-title-row {
  display: flex;
  align-items: center;
}

.book-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-details .book-author {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-stats-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.stat-badge {
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}

.stat-badge.duration {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}

.book-latest {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* 移动端深度适配 */
@media (max-width: 768px) {
  .ranking-view {
    padding: 12px 10px calc(70px + env(safe-area-inset-bottom, 16px));
  }

  .achievement-card {
    padding: 16px;
    border-radius: 12px;
    margin-bottom: 14px;
  }

  .greeting {
    font-size: 15px;
  }

  .sub-greeting {
    font-size: 11px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .stat-item {
    padding: 8px 10px;
    gap: 8px;
  }

  .stat-icon-wrap {
    width: 32px;
    height: 32px;
    font-size: 15px;
  }

  .stat-val {
    font-size: 14px;
  }

  .tab-btn {
    padding: 8px 12px;
    font-size: 13px;
  }

  .ranking-panel {
    padding: 12px 8px;
  }

  .rank-item {
    padding: 10px 8px;
    gap: 8px;
  }

  .rank-number-box {
    width: 24px;
    font-size: 14px;
  }

  .medal {
    font-size: 18px;
  }

  .rank-book-cover-box {
    width: 44px;
    height: 62px;
  }

  .book-title {
    font-size: 13px;
  }

  .book-details .book-author {
    font-size: 11px;
  }

  .book-actions {
    flex-direction: column;
    gap: 4px;
  }

  .book-actions .el-button {
    padding: 5px 8px;
    font-size: 11px;
    height: 24px;
  }
}
</style>
