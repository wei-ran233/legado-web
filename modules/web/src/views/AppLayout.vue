<template>
  <el-container
    class="app-layout"
    :class="{
      'reader-mode': isReader,
      'layout-topbar': isTopLayout,
      'layout-dock': theme.appTheme === 'sakura',
    }"
  >
    <!-- 全屏背景动效图层 -->
    <div class="theme-ambient-canvas-wrap" v-if="!isReader">
      <!-- 1. 星河极光：真实多层立体折叠天幕帷幔 + 钻石繁星闪烁星场 -->
      <template v-if="theme.appTheme === 'aurora'">
        <div class="aurora-sky-curtain">
          <div class="curtain-sheet curtain-cyan"></div>
          <div class="curtain-sheet curtain-purple"></div>
          <div class="curtain-sheet curtain-magenta"></div>
          <div class="curtain-sheet curtain-emerald"></div>
        </div>
        <div class="aurora-starfield">
          <span v-for="s in 24" :key="s" class="aurora-star" :style="getStarStyle(s)">✦</span>
        </div>
      </template>

      <!-- 2. 黑曜赛博：全息数码扫描线 + 纵向激光扫描光束 + 3D空间网格 -->
      <template v-else-if="theme.appTheme === 'obsidian'">
        <div class="cyber-scanlines"></div>
        <div class="cyber-laser-sweep"></div>
        <div class="cyber-grid-overlay"></div>
      </template>

      <!-- 3. 蔚蓝画报：极简弥散流体光晕 + 现代艺术画报水印 -->
      <template v-else-if="theme.appTheme === 'azure'">
        <div class="azure-ambient-glow"></div>
        <div class="azure-editorial-watermark">
          <span class="watermark-bold">AZURE</span>
          <span class="watermark-sub">EDITION // 2026</span>
        </div>
      </template>

      <!-- 4. 经典主题：原生纯净墨绿暗纹质感 -->
      <div v-else-if="theme.appTheme === 'classic'" class="classic-ambient-grain"></div>

      <!-- 5. 浅樱落雪：角落和风樱花树 + 漫天落樱随机飘落花瓣 -->
      <div v-else-if="theme.appTheme === 'sakura'" class="sakura-scenery-container">
        <!-- 角落和风粉樱古树 SVG 艺术 -->
        <div class="sakura-tree-corner">
          <svg viewBox="0 0 500 500" class="sakura-tree-svg" preserveAspectRatio="xMaxYMax meet">
            <defs>
              <linearGradient id="sakuraTrunkGrad" x1="0%" y1="100%" x2="50%" y2="0%">
                <stop offset="0%" stop-color="#4a2c2a" />
                <stop offset="60%" stop-color="#7c4a3e" />
                <stop offset="100%" stop-color="#9a5e4d" />
              </linearGradient>
              <radialGradient id="sakuraBloomGrad1" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stop-color="#ffd5e2" stop-opacity="0.95" />
                <stop offset="50%" stop-color="#ffb3cb" stop-opacity="0.85" />
                <stop offset="100%" stop-color="#ff8dae" stop-opacity="0" />
              </radialGradient>
              <radialGradient id="sakuraBloomGrad2" cx="40%" cy="40%" r="60%">
                <stop offset="0%" stop-color="#ffffff" stop-opacity="0.98" />
                <stop offset="45%" stop-color="#ffc0d5" stop-opacity="0.88" />
                <stop offset="100%" stop-color="#ff759f" stop-opacity="0" />
              </radialGradient>
              <filter id="sakuraSoftGlow" x="-20%" y="-20%" width="140%" height="140%">
                <feGaussianBlur stdDeviation="3" result="blur" />
                <feComposite in="SourceGraphic" in2="blur" operator="over" />
              </filter>
            </defs>
            <!-- 树干与粗主枝干 -->
            <path d="M500,500 L440,500 C430,420 400,380 370,340 C340,300 290,270 250,260 C230,255 200,265 180,250 C170,242 165,225 140,220 C120,216 95,225 80,215 C100,205 130,205 155,212 C185,220 205,235 240,245 C280,255 330,270 375,320 C410,360 440,410 450,500 Z" fill="url(#sakuraTrunkGrad)" opacity="0.88" />
            <!-- 副枝 1 -->
            <path d="M370,340 C360,290 320,240 280,200 C250,170 210,150 170,140 C190,145 230,165 265,190 C300,225 340,270 355,320 Z" fill="url(#sakuraTrunkGrad)" opacity="0.8" />
            <!-- 副枝 2 -->
            <path d="M420,380 C440,320 420,260 390,210 C370,175 330,145 290,130 C325,140 360,165 380,200 C405,245 425,305 410,365 Z" fill="url(#sakuraTrunkGrad)" opacity="0.8" />
            <!-- 副枝 3 -->
            <path d="M330,220 C340,160 370,110 420,70 C400,90 360,135 345,185 C335,220 330,220 330,220 Z" fill="url(#sakuraTrunkGrad)" opacity="0.75" />

            <!-- 盛开的粉樱云团簇 -->
            <circle cx="80" cy="210" r="32" fill="url(#sakuraBloomGrad1)" filter="url(#sakuraSoftGlow)" />
            <circle cx="120" cy="190" r="45" fill="url(#sakuraBloomGrad2)" />
            <circle cx="170" cy="140" r="50" fill="url(#sakuraBloomGrad1)" filter="url(#sakuraSoftGlow)" />
            <circle cx="210" cy="160" r="42" fill="url(#sakuraBloomGrad2)" />
            <circle cx="240" cy="120" r="48" fill="url(#sakuraBloomGrad1)" />
            <circle cx="280" cy="150" r="60" fill="url(#sakuraBloomGrad2)" filter="url(#sakuraSoftGlow)" />
            <circle cx="320" cy="110" r="52" fill="url(#sakuraBloomGrad1)" />
            <circle cx="360" cy="140" r="58" fill="url(#sakuraBloomGrad2)" />
            <circle cx="390" cy="90" r="46" fill="url(#sakuraBloomGrad1)" />
            <circle cx="430" cy="70" r="40" fill="url(#sakuraBloomGrad2)" filter="url(#sakuraSoftGlow)" />
            <circle cx="350" cy="190" r="65" fill="url(#sakuraBloomGrad1)" />
            <circle cx="290" cy="220" r="55" fill="url(#sakuraBloomGrad2)" />
            <circle cx="230" cy="240" r="45" fill="url(#sakuraBloomGrad1)" />
            <circle cx="180" cy="230" r="38" fill="url(#sakuraBloomGrad2)" />
            <circle cx="390" cy="230" r="48" fill="url(#sakuraBloomGrad1)" />
            <circle cx="430" cy="280" r="50" fill="url(#sakuraBloomGrad2)" filter="url(#sakuraSoftGlow)" />
            
            <!-- 精巧樱花瓣簇 -->
            <g fill="#ff537e" opacity="0.85">
              <circle cx="140" cy="160" r="5" /><circle cx="145" cy="164" r="5" /><circle cx="138" cy="167" r="5" /><circle cx="134" cy="162" r="5" />
              <circle cx="260" cy="130" r="6" /><circle cx="266" cy="135" r="6" /><circle cx="258" cy="139" r="6" /><circle cx="253" cy="133" r="6" />
              <circle cx="350" cy="160" r="6" /><circle cx="357" cy="165" r="6" /><circle cx="349" cy="170" r="6" /><circle cx="343" cy="164" r="6" />
              <circle cx="400" cy="120" r="5" /><circle cx="406" cy="124" r="5" /><circle cx="398" cy="128" r="5" /><circle cx="393" cy="123" r="5" />
            </g>
          </svg>
        </div>

        <!-- 漫天落樱随机飘落花瓣群 -->
        <div class="falling-petals-wrap">
          <div
            v-for="n in 20"
            :key="n"
            :class="['petal', 'petal-' + (n % 4 + 1)]"
            :style="getPetalStyle(n)"
          ></div>
        </div>
      </div>
    </div>

    <!-- 1. 桌面端顶部特色导航栏（针对极光、赛博、画报） -->
    <template v-if="!isReader">
      <!-- 1.1 星河极光：顶部中央悬浮双翼流光星核飞梭 (Aurora Star Core Shuttle) -->
      <div v-if="theme.appTheme === 'aurora'" class="nav-aurora-wrap desktop-only">
        <div class="aurora-shuttle-container">
          <!-- 左翼舱：核心阅览与排行榜 -->
          <nav class="shuttle-wing shuttle-wing-left">
            <router-link to="/" class="aurora-link" :class="{ active: activeMenu === '/' }">
              <el-icon><Reading /></el-icon><span>书架展台</span>
            </router-link>
            <router-link to="/ranking" class="aurora-link" :class="{ active: activeMenu === '/ranking' }">
              <el-icon><Trophy /></el-icon><span>极光星榜</span>
            </router-link>
          </nav>

          <!-- 中央静谧星核微标 (无旋转光带) -->
          <div class="shuttle-star-core" title="AURORA // 星核坐标">
            <div class="core-nucleus">✦</div>
          </div>

          <!-- 右翼舱：探索、个人与管理中枢 -->
          <nav class="shuttle-wing shuttle-wing-right">
            <router-link to="/search" class="aurora-link" :class="{ active: activeMenu === '/search' }">
              <el-icon><Search /></el-icon><span>星海探索</span>
            </router-link>
            <router-link to="/me" class="aurora-link" :class="{ active: activeMenu === '/me' }">
              <el-icon><User /></el-icon><span>星际坐标</span>
            </router-link>
            <router-link v-if="auth.isAdmin" to="/admin/users" class="aurora-link" :class="{ active: activeMenu.startsWith('/admin') }">
              <el-icon><Setting /></el-icon><span>中枢</span>
            </router-link>
          </nav>

          <!-- 飞梭右舷操作舱 -->
          <div class="shuttle-action-pod">
            <el-button circle size="small" class="theme-btn" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'" @click="theme.toggleDark">
              <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
            </el-button>
            <span class="uname">{{ auth.displayName || auth.username }}</span>
            <el-button type="danger" plain size="small" @click="onLogout">
              <el-icon><SwitchButton /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 1.2 黑曜赛博：太空 HUD 战备控制台 (Obsidian Cyber HUD Bar) -->
      <header v-else-if="theme.appTheme === 'obsidian'" class="nav-cyber-hud-bar desktop-only">
        <div class="cyber-hud-left">
          <div class="cyber-hud-title">
            <span class="cyber-blinker">●</span>
            <span class="cyber-sys-label">[SYS.NET] MATRIX_V2</span>
          </div>
          <div class="cyber-hud-telemetry">
            <span class="telemetry-item">STATUS // OPERATIONAL</span>
            <span class="telemetry-ping">LATENCY // 8.4ms</span>
          </div>
        </div>
        <nav class="cyber-hud-links">
          <router-link to="/" class="cyber-hud-item" :class="{ active: activeMenu === '/' }">
            <el-icon><Reading /></el-icon><span>DATA.LIB</span>
          </router-link>
          <router-link to="/ranking" class="cyber-hud-item" :class="{ active: activeMenu === '/ranking' }">
            <el-icon><Trophy /></el-icon><span>APEX.LADDER</span>
          </router-link>
          <router-link to="/search" class="cyber-hud-item" :class="{ active: activeMenu === '/search' }">
            <el-icon><Search /></el-icon><span>DEEP.SCAN</span>
          </router-link>
          <router-link to="/me" class="cyber-hud-item" :class="{ active: activeMenu === '/me' }">
            <el-icon><User /></el-icon><span>OPERATOR</span>
          </router-link>
          <router-link v-if="auth.isAdmin" to="/admin/users" class="cyber-hud-item" :class="{ active: activeMenu.startsWith('/admin') }">
            <el-icon><Setting /></el-icon><span>ROOT.SYS</span>
          </router-link>
        </nav>
        <div class="top-actions">
          <el-button circle size="small" class="theme-btn cyber-theme-btn" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'" @click="theme.toggleDark">
            <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
          </el-button>
          <span class="cyber-uname">{{ auth.displayName || auth.username }}</span>
          <el-button type="danger" plain size="small" class="cyber-logout-btn" @click="onLogout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </header>

      <!-- 1.3 蔚蓝画报：双层瑞士现代艺术画报刊头 (Azure Two-Tier Editorial Header) -->
      <header v-else-if="theme.appTheme === 'azure'" class="nav-azure-two-tier desktop-only">
        <!-- 上层：极细参考线、发行号与期号信息 -->
        <div class="azure-tier-meta">
          <div class="tier-meta-left">
            <span class="azure-issue-tag">VOL.26 // 2026</span>
            <span class="azure-editorial-sub">CURATION ARCHIVE &amp; LITERATURE REPOSITORY</span>
          </div>
          <div class="tier-meta-right">
            <span class="azure-edition-stamp">EDITION // ZURICH - TOKYO</span>
          </div>
        </div>
        <!-- 下层：主画报刊名与大号艺术超链接 -->
        <div class="azure-tier-nav">
          <div class="azure-brand-box">
            <span class="azure-brand-mark">A</span>
            <span class="azure-title-text">AZURE EDITORIAL</span>
          </div>
          <nav class="azure-nav-links">
            <router-link to="/" class="azure-link-item" :class="{ active: activeMenu === '/' }">
              <span class="link-no">01</span><span>COLLECTION / 书架</span>
            </router-link>
            <router-link to="/ranking" class="azure-link-item" :class="{ active: activeMenu === '/ranking' }">
              <span class="link-no">02</span><span>RANKING / 榜单</span>
            </router-link>
            <router-link to="/search" class="azure-link-item" :class="{ active: activeMenu === '/search' }">
              <span class="link-no">03</span><span>EXPLORE / 探索</span>
            </router-link>
            <router-link to="/me" class="azure-link-item" :class="{ active: activeMenu === '/me' }">
              <span class="link-no">04</span><span>PROFILE / 我的</span>
            </router-link>
            <router-link v-if="auth.isAdmin" to="/admin/users" class="azure-link-item" :class="{ active: activeMenu.startsWith('/admin') }">
              <span class="link-no">05</span><span>CONSOLE / 管理</span>
            </router-link>
          </nav>
          <div class="top-actions">
            <el-button circle size="small" class="theme-btn" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'" @click="theme.toggleDark">
              <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
            </el-button>
            <span class="uname">{{ auth.displayName || auth.username }}</span>
            <el-button type="danger" plain size="small" @click="onLogout">
              <el-icon><SwitchButton /></el-icon>
            </el-button>
          </div>
        </div>
      </header>

      <!-- 1.4 浅樱落雪：macOS 屏幕正底部悬浮毛玻璃 Dock 栏（桌面与移动均优雅呈现） -->
      <div v-else-if="theme.appTheme === 'sakura'" class="macos-dock-container">
        <div class="macos-dock">
          <router-link to="/" class="dock-item" :class="{ active: activeMenu === '/' }" data-label="书架">
            <div class="dock-icon">
              <el-icon><Reading /></el-icon>
            </div>
            <span class="dock-tooltip">书架</span>
            <span class="dock-dot"></span>
          </router-link>

          <router-link to="/ranking" class="dock-item" :class="{ active: activeMenu === '/ranking' }" data-label="排行榜">
            <div class="dock-icon">
              <el-icon><Trophy /></el-icon>
            </div>
            <span class="dock-tooltip">排行榜</span>
            <span class="dock-dot"></span>
          </router-link>

          <router-link to="/search" class="dock-item" :class="{ active: activeMenu === '/search' }" data-label="搜索探索">
            <div class="dock-icon">
              <el-icon><Search /></el-icon>
            </div>
            <span class="dock-tooltip">搜索探索</span>
            <span class="dock-dot"></span>
          </router-link>

          <router-link to="/me" class="dock-item" :class="{ active: activeMenu === '/me' }" data-label="个人中心">
            <div class="dock-icon">
              <el-icon><User /></el-icon>
            </div>
            <span class="dock-tooltip">个人中心</span>
            <span class="dock-dot"></span>
          </router-link>

          <router-link v-if="auth.isAdmin" to="/admin/users" class="dock-item" :class="{ active: activeMenu.startsWith('/admin') }" data-label="系统管理">
            <div class="dock-icon">
              <el-icon><Setting /></el-icon>
            </div>
            <span class="dock-tooltip">系统管理</span>
            <span class="dock-dot"></span>
          </router-link>

          <div class="dock-divider"></div>

          <button class="dock-item" @click="theme.toggleDark" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'">
            <div class="dock-icon">
              <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
            </div>
            <span class="dock-tooltip">{{ theme.isDark ? '日间模式' : '夜间模式' }}</span>
          </button>

          <button class="dock-item" @click="onLogout" title="退出登录">
            <div class="dock-icon" style="color: #ef4444;">
              <el-icon><SwitchButton /></el-icon>
            </div>
            <span class="dock-tooltip">退出登录</span>
          </button>
        </div>
      </div>

      <!-- 1.5 经典主题：原生 200px 树状侧边栏 (Classic Left Sidebar) -->
      <el-aside v-else width="200px" class="desktop-only aside">
        <div class="logo-row">
          <div class="logo">📚 Legado</div>
          <el-button circle size="small" class="theme-btn" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'" @click="theme.toggleDark">
            <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
          </el-button>
        </div>
        <el-menu :default-active="activeMenu" router class="menu">
          <el-menu-item index="/">
            <el-icon><Reading /></el-icon><template #title><span>书架</span></template>
          </el-menu-item>
          <el-menu-item index="/ranking">
            <el-icon><Trophy /></el-icon><template #title><span>排行榜</span></template>
          </el-menu-item>
          <el-menu-item index="/search">
            <el-icon><Search /></el-icon><template #title><span>搜索</span></template>
          </el-menu-item>
          <el-menu-item index="/me">
            <el-icon><User /></el-icon><template #title><span>我的</span></template>
          </el-menu-item>
          <template v-if="auth.isAdmin">
            <el-divider style="margin: 8px 16px; width: auto" />
            <el-menu-item index="/admin/users">
              <el-icon><UserFilled /></el-icon><template #title><span>用户管理</span></template>
            </el-menu-item>
            <el-menu-item index="/admin/sources">
              <el-icon><Files /></el-icon><template #title><span>书源管理</span></template>
            </el-menu-item>
          </template>
        </el-menu>
        <div class="footer">
          <div class="user-chip" :title="auth.displayName || auth.username">
            <el-avatar :size="28">{{ (auth.displayName || auth.username || 'U').slice(0, 1).toUpperCase() }}</el-avatar>
            <div class="user-meta">
              <span class="uname" :title="auth.displayName || auth.username">{{ auth.displayName || auth.username }}</span>
              <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'info'" effect="plain" class="role-tag">
                {{ auth.isAdmin ? '管理员' : '普通用户' }}
              </el-tag>
            </div>
          </div>
          <el-button type="danger" plain size="small" class="logout-btn" :title="'退出登录'" @click="onLogout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出登录</span>
          </el-button>
        </div>
      </el-aside>
    </template>

    <!-- 2. 主体内容容器 -->
    <el-container class="content-container" :class="{ 'reader-content-container': isReader }">
      <!-- 移动端顶部标题栏（阅读页隐藏） -->
      <header v-if="!isReader" class="mobile-header mobile-only">
        <div class="mobile-logo">
          <span v-if="theme.appTheme === 'sakura'">🌸 浅樱落雪</span>
          <span v-else-if="theme.appTheme === 'obsidian'">⚡ [MATRIX] // ONLINE</span>
          <span v-else-if="theme.appTheme === 'aurora'">✨ 星河极光</span>
          <span v-else-if="theme.appTheme === 'azure'">AZURE ✦</span>
          <span v-else>📚 Legado</span>
        </div>
        <div class="mobile-actions">
          <el-button circle size="small" class="theme-btn" :title="theme.isDark ? '切换至日间模式' : '切换至黑夜模式'" @click="theme.toggleDark">
            <el-icon><Moon v-if="theme.isDark" /><Sunny v-else /></el-icon>
          </el-button>
          <span class="mobile-uname">{{ auth.displayName || auth.username }}</span>
          <el-button text size="small" type="danger" @click="onLogout">
            <el-icon><SwitchButton /></el-icon>&nbsp;退出
          </el-button>
        </div>
      </header>

      <el-main class="main" :class="{ 'reader-main': isReader }">
        <div :class="{ 'app-main-content-wrap': !isReader }">
          <router-view v-slot="{ Component, route }">
            <keep-alive include="SearchView,BookShelf">
              <component :is="Component" :key="route.name === 'reader' ? route.fullPath : undefined" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>

    <!-- 3. 移动端常规底部 TabBar（阅读页隐藏，浅樱落雪已由 macOS Dock 代替） -->
    <nav v-if="!isReader && theme.appTheme !== 'sakura'" class="mobile-tabbar mobile-only" :class="'tabbar-' + theme.appTheme">
      <router-link to="/" class="tab" :class="{ active: activeMenu === '/' }">
        <el-icon :size="20"><Reading /></el-icon><span>书架</span>
      </router-link>
      <router-link to="/ranking" class="tab" :class="{ active: activeMenu === '/ranking' }">
        <el-icon :size="20"><Trophy /></el-icon><span>排行</span>
      </router-link>
      <router-link to="/search" class="tab" :class="{ active: activeMenu === '/search' }">
        <el-icon :size="20"><Search /></el-icon><span>搜索</span>
      </router-link>
      <router-link v-if="auth.isAdmin" to="/admin/users" class="tab"
        :class="{ active: activeMenu.startsWith('/admin') }">
        <el-icon :size="20"><Setting /></el-icon><span>管理</span>
      </router-link>
      <router-link to="/me" class="tab" :class="{ active: activeMenu === '/me' }">
        <el-icon :size="20"><User /></el-icon><span>我的</span>
      </router-link>
    </nav>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Reading, Search, User, UserFilled, Files, Setting, SwitchButton, Sunny, Moon, Trophy } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'
import { setupGlobalClickEffects } from '@/utils/clickEffects'

const route = useRoute()
const auth = useAuthStore()
const theme = useThemeStore()

onMounted(() => {
  if (!auth.me) auth.fetchMe()
  setupGlobalClickEffects(() => theme.appTheme, () => theme.clickEffectsEnabled)
})

const isReader = computed(() => route.path.startsWith('/reader') || route.name === 'reader')

// 顶栏类主题包括：aurora, obsidian, azure
const isTopLayout = computed(() => {
  return theme.appTheme === 'aurora' || theme.appTheme === 'obsidian' || theme.appTheme === 'azure'
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin')) return '/admin/users'
  if (route.path.startsWith('/ranking')) return '/ranking'
  if (route.path.startsWith('/search')) return '/search'
  if (route.path.startsWith('/me')) return '/me'
  if (route.path.startsWith('/reader')) return '/'
  return '/'
})

// 计算漫天落樱各花瓣的随机位置与动力学参数
const getPetalStyle = (n: number) => {
  const left = ((n * 17) % 96) + 2 // 2% ~ 98%
  const duration = 7 + ((n * 3) % 7) // 7s ~ 13s
  const delay = -((n * 2.3) % 10) // 0s ~ -10s
  const sizeScale = 0.75 + ((n * 7) % 6) * 0.1 // 0.75 ~ 1.25
  return {
    left: left + '%',
    animationDuration: duration + 's',
    animationDelay: delay + 's',
    transform: 'scale(' + sizeScale + ')',
  }
}

// 计算极光繁星闪烁粒子坐标与动态节奏
const getStarStyle = (s: number) => {
  const top = ((s * 23) % 92) + 4
  const left = ((s * 41) % 94) + 3
  const duration = 2.5 + ((s * 1.7) % 3)
  const delay = -((s * 0.8) % 4)
  const size = 9 + ((s * 5) % 8)
  return {
    top: top + '%',
    left: left + '%',
    fontSize: size + 'px',
    animationDuration: duration + 's',
    animationDelay: delay + 's',
  }
}

const onLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await auth.logout()
    ElMessage.success('已退出登录')
  } catch {
    /* 取消退出 */
  }
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  min-height: 100dvh;
  position: relative;
  background: var(--app-bg-page, var(--el-bg-color-page));
  transition: background 0.35s ease;
}

.app-layout.layout-topbar,
.app-layout.layout-dock {
  flex-direction: column;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--app-sidebar-bg, var(--el-bg-color));
  position: relative;
  z-index: 10;
}

.logo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 14px 12px;
}

.logo {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
}

.theme-btn {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
}

.menu {
  border-right: none;
  background: transparent;
  flex: 1;
}

.footer {
  padding: 12px 14px;
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.uname {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  align-self: flex-start;
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
}

.logout-btn {
  width: 100%;
}

.content-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.app-main-content-wrap {
  padding: 20px;
  box-sizing: border-box;
  width: 100%;
}

.mobile-header {
  display: none;
  height: 48px;
  padding: 0 14px;
  align-items: center;
  justify-content: space-between;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  position: sticky;
  top: 0;
  z-index: 99;
}

.mobile-logo {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
}

.mobile-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mobile-uname {
  font-size: 13px;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-tabbar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);
  z-index: 99;
  justify-content: space-around;
  align-items: center;
  padding-bottom: env(safe-area-inset-bottom, 0);
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.tab.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

@media (max-width: 768px) {
  .desktop-only {
    display: none !important;
  }
  .mobile-only {
    display: flex !important;
  }
  .main {
    padding: 10px !important;
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0)) !important;
  }
  .main.reader-main {
    padding: 0 !important;
    padding-bottom: 0 !important;
    height: 100vh !important;
    height: 100dvh !important;
  }
  .app-main-content-wrap {
    padding: 6px 4px !important;
  }
}
</style>
