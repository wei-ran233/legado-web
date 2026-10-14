<template>
  <div class="reader" :class="[`theme-${theme}`, `font-${fontFamily}`]" @click="onBodyClick">
    <!-- 顶部常驻/呼出栏 -->
    <div class="reader-header" :class="{ show: chromeVisible }">
      <el-button text @click="$router.back()"><el-icon><Back /></el-icon> 返回</el-button>
      <span class="book-title">{{ bookName }}</span>
      <div class="header-actions">
        <!-- 多媒体模式切换 -->
        <el-dropdown trigger="click" @command="setMediaMode">
          <el-button text size="small" class="mode-dropdown-btn">
            <el-icon>
              <Notebook v-if="mediaMode === 'text'" />
              <Picture v-else-if="mediaMode === 'comic'" />
              <Headset v-else-if="mediaMode === 'audio'" />
              <Film v-else-if="mediaMode === 'video'" />
            </el-icon>&nbsp;{{ mediaModeLabel }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="text" :disabled="mediaMode === 'text'">📖 文本模式</el-dropdown-item>
              <el-dropdown-item command="comic" :disabled="mediaMode === 'comic'">🖼️ 漫画模式</el-dropdown-item>
              <el-dropdown-item command="audio" :disabled="mediaMode === 'audio'">🎧 有声/音乐</el-dropdown-item>
              <el-dropdown-item command="video" :disabled="mediaMode === 'video'">🎬 视频模式</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 加书架按钮 -->
        <el-button
          v-if="!isOnShelf"
          type="primary"
          size="small"
          :loading="addingShelf"
          @click.stop="onAddToShelf"
        >
          <el-icon><Plus /></el-icon>&nbsp;加书架
        </el-button>
        <el-tag v-else size="small" type="info" effect="plain">已在书架</el-tag>

        <el-button
          v-if="mediaMode === 'text'"
          text
          size="small"
          @click.stop="toggleReadMode"
          :title="readMode === 'page' ? '当前为分页模式，点击切换为连续滚动' : '当前为滚动模式，点击切换为分页浏览'"
        >
          <el-icon><CopyDocument /></el-icon>&nbsp;{{ readMode === 'page' ? '切至滚动' : '切至分页' }}
        </el-button>

        <el-button
          text
          size="small"
          class="theme-toggle-btn"
          :title="themeStore.isDark ? '切换至日间模式' : '切换至暗色模式'"
          @click.stop="toggleDarkMode"
        >
          <el-icon><Moon v-if="themeStore.isDark" /><Sunny v-else /></el-icon>&nbsp;{{ themeStore.isDark ? '日间' : '暗色' }}
        </el-button>

        <el-button
          text
          size="small"
          :title="isFullscreen ? '退出全屏' : '全屏沉浸阅读'"
          @click.stop="toggleFullscreen"
        >
          <el-icon><FullScreen /></el-icon>&nbsp;{{ isFullscreen ? '还原' : '全屏' }}
        </el-button>

        <el-button text @click="settingsVisible = true"><el-icon><Setting /></el-icon> 设置</el-button>
        <el-button text @click="openChangeSourceDrawer"><el-icon><Refresh /></el-icon> 换源</el-button>
        <el-button text @click="notesVisible = true"><el-icon><CollectionTag /></el-icon> 笔记</el-button>
        <el-button text @click="catalogVisible = true"><el-icon><Menu /></el-icon> 目录</el-button>
      </div>
    </div>

    <!-- TTS 朗读悬浮控制条 -->
    <div v-if="isSpeaking" class="tts-bar" @click.stop>
      <el-icon class="is-loading"><Headset /></el-icon>
      <span>{{ isPaused ? '朗读已暂停' : '正在语音朗读…' }}</span>
      <el-button size="small" circle @click="pauseSpeak">
        <el-icon><VideoPause v-if="!isPaused" /><VideoPlay v-else /></el-icon>
      </el-button>
      <el-button size="small" circle type="danger" plain @click="stopSpeak">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <!-- 加载中状态 -->
    <div v-if="(tocLoading || loadingContent) && !paragraphs.length && !comicImages.length && !audioSrc && !videoSrc" class="reader-state-wrap">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
      <div class="state-title">正在加载《{{ bookName }}》…</div>
      <div class="state-desc">正在拉取章节目录与正文，请稍候</div>
    </div>

    <!-- 加载失败/空状态 -->
    <div v-else-if="tocError && !paragraphs.length && !comicImages.length && !audioSrc && !videoSrc" class="reader-state-wrap">
      <el-empty :description="tocError">
        <el-button type="primary" @click="openChangeSourceDrawer"><el-icon><Refresh /></el-icon>&nbsp;更换书源</el-button>
        <el-button @click="retryLoad"><el-icon><RefreshRight /></el-icon>&nbsp;重试</el-button>
        <el-button @click="$router.back()"><el-icon><Back /></el-icon>&nbsp;返回</el-button>
      </el-empty>
    </div>

    <!-- 1. 漫画瀑布流阅读模式 -->
    <div v-else-if="mediaMode === 'comic'" class="comic-container" ref="comicContainerRef" @scroll.passive="onComicScroll">
      <!-- 漫画控制悬浮条 -->
      <div class="comic-toolbar" @click.stop>
        <span class="comic-page-info">共 {{ comicImages.length }} 张</span>
        <el-button circle size="small" :disabled="comicScale <= 50" @click="comicScale = Math.max(50, comicScale - 15)">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <span class="scale-text">{{ comicScale }}%</span>
        <el-button circle size="small" :disabled="comicScale >= 160" @click="comicScale = Math.min(160, comicScale + 15)">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button size="small" @click="comicScale = 100">
          <el-icon><ScaleToOriginal /></el-icon> 还原
        </el-button>
      </div>

      <div class="comic-content" :style="{ maxWidth: `${Math.min(100, comicScale)}%` }">
        <h2 class="chapter-title" v-if="chapter?.title">{{ chapter.title }}</h2>

        <!-- 加载失败全章节通知 -->
        <div v-if="failedImgCount > 0" class="comic-fail-notice">
          <div class="notice-info">
            <el-icon class="warn-icon"><WarningFilled /></el-icon>
            <span>当前章节有 <b>{{ failedImgCount }}</b> 页图片加载失败（可能原源站图片损坏或防盗链）</span>
          </div>
          <div class="notice-actions">
            <el-button size="small" type="primary" :icon="Switch" @click="openChangeSourceDrawer">立即换源</el-button>
            <el-button size="small" :icon="Refresh" @click="retryAllFailedImgs">全部重试</el-button>
          </div>
        </div>

        <!-- 单张图片提醒（可能为源站防爬或单页限制） -->
        <div v-if="comicImages.length === 1 && failedImgCount === 0" class="comic-single-notice">
          <div class="notice-info">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
            <span>本章仅提取出 1 张图片，若画面不全或包含限制提示，建议【换源】挑选其他漫画源。</span>
          </div>
          <div class="notice-actions">
            <el-button size="small" type="primary" :icon="Switch" @click="openChangeSourceDrawer">立即换源</el-button>
          </div>
        </div>

        <div v-if="comicImages.length > 0" class="comic-list">
          <div v-for="(imgUrl, idx) in comicImages" :key="idx" class="comic-item">
            <div class="comic-item-num">P{{ idx + 1 }}</div>

            <!-- 单页加载失败容错卡片 -->
            <div v-if="comicImgStatus[idx] === 'error'" class="comic-error-card">
              <el-icon class="error-card-icon"><PictureFilled /></el-icon>
              <div class="error-card-title">第 {{ idx + 1 }} 页图片加载失败</div>
              <div class="error-card-desc">源站图片可能已失效、超时或受到防盗链限制</div>
              <div class="error-card-actions">
                <el-button size="small" :icon="Refresh" @click="retrySingleImg(idx)">重试本页</el-button>
                <el-button size="small" type="primary" :icon="Switch" @click="openChangeSourceDrawer">更换书源</el-button>
              </div>
            </div>

            <img
              v-show="comicImgStatus[idx] !== 'error'"
              :src="getComicImgSrc(imgUrl, idx)"
              loading="lazy"
              class="comic-img"
              referrerpolicy="no-referrer"
              :alt="`第 ${idx + 1} 页`"
              @load="onImgLoadSuccess(idx)"
              @error="onImgLoadError(idx, $event, imgUrl)"
            />
          </div>
        </div>

        <div v-else class="comic-empty">
          <el-empty description="当前书源未解析到有效漫画图片（可能源站损坏或需动态解密）">
            <el-button type="primary" :icon="Switch" @click="openChangeSourceDrawer">切换其他漫画源</el-button>
            <el-button @click="setMediaMode('text')">切换为文本模式查看</el-button>
          </el-empty>
        </div>

        <!-- 漫画上一话/下一话导航 -->
        <div class="nav-footer comic-nav">
          <el-button :disabled="chapterIndex <= 0" @click.stop="prevChapter(false)">上一话</el-button>
          <span class="muted">{{ chapter?.title }}</span>
          <el-button type="primary" :disabled="chapterIndex >= toc.length - 1" @click.stop="nextChapter">下一话</el-button>
        </div>
      </div>
    </div>

    <!-- 2. 有声/音乐播放器模式 -->
    <div v-else-if="mediaMode === 'audio'" class="audio-container">
      <div class="audio-player-card">
        <!-- 唱片动效 -->
        <div class="vinyl-box">
          <div class="vinyl-disc" :class="{ spinning: isAudioPlaying }">
            <img :src="bookCover" class="vinyl-cover" />
            <div class="vinyl-center-hole"></div>
          </div>
        </div>

        <div class="audio-info">
          <div class="audio-title">{{ chapter?.title || '正在加载音频…' }}</div>
          <div class="audio-sub">《{{ bookName }}》· {{ bookAuthor || '佚名' }}</div>
        </div>

        <!-- 播放器进度条与时间 -->
        <div class="audio-scrub-wrap">
          <span class="audio-time">{{ formatTime(audioCurrentTime) }}</span>
          <el-slider
            v-model="audioCurrentTime"
            :max="audioDuration || 100"
            :show-tooltip="false"
            class="audio-slider"
            @change="seekAudio"
          />
          <span class="audio-time">{{ formatTime(audioDuration) }}</span>
        </div>

        <!-- 主控按键 -->
        <div class="audio-controls">
          <el-button circle size="default" @click="skipAudio(-15)" title="快退15秒">
            -15s
          </el-button>
          <el-button circle size="default" :disabled="chapterIndex <= 0" @click="prevChapter(false)" title="上一章">
            <el-icon><Back /></el-icon>
          </el-button>
          <el-button
            circle
            type="primary"
            class="audio-play-btn"
            @click="toggleAudioPlay"
          >
            <el-icon :size="26"><VideoPause v-if="isAudioPlaying" /><VideoPlay v-else /></el-icon>
          </el-button>
          <el-button circle size="default" :disabled="chapterIndex >= toc.length - 1" @click="nextChapter" title="下一章">
            <el-icon style="transform: rotate(180deg)"><Back /></el-icon>
          </el-button>
          <el-button circle size="default" @click="skipAudio(15)" title="快进15秒">
            +15s
          </el-button>
        </div>

        <!-- 扩展控制：倍速、定时关闭、选集 -->
        <div class="audio-extra-controls">
          <el-dropdown trigger="click" @command="changeAudioRate">
            <el-button size="small" text>
              {{ audioPlaybackRate }}x 倍速
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="0.75">0.75x</el-dropdown-item>
                <el-dropdown-item command="1.0">1.0x (正常)</el-dropdown-item>
                <el-dropdown-item command="1.25">1.25x</el-dropdown-item>
                <el-dropdown-item command="1.5">1.5x</el-dropdown-item>
                <el-dropdown-item command="2.0">2.0x</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click" @command="setSleepTimer">
            <el-button size="small" text>
              <el-icon><Timer /></el-icon>&nbsp;{{ sleepTimerLabel }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="0">关闭定时</el-dropdown-item>
                <el-dropdown-item command="15">15 分钟后停止</el-dropdown-item>
                <el-dropdown-item command="30">30 分钟后停止</el-dropdown-item>
                <el-dropdown-item command="60">60 分钟后停止</el-dropdown-item>
                <el-dropdown-item command="chapter">本章播完后停止</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-button size="small" text @click="catalogVisible = true">
            <el-icon><Menu /></el-icon>&nbsp;选集
          </el-button>
        </div>

        <!-- 原生 HTML5 Audio 标签 -->
        <audio
          v-if="audioSrc"
          ref="audioRef"
          :src="audioSrc"
          @timeupdate="onAudioTimeUpdate"
          @loadedmetadata="onAudioLoadedMetadata"
          @ended="onAudioEnded"
          @error="onAudioError"
          style="display: none"
        ></audio>

        <!-- 纯文本无直链时的 TTS 融合模式提示 -->
        <div v-else class="audio-tts-fallback">
          <el-alert
            title="当前章节为纯文本内容，支持使用智能语音朗读"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              <div style="margin-top: 6px">
                <el-button size="small" type="success" @click="speakSelection">
                  <el-icon><Headset /></el-icon>&nbsp;启动语音朗读
                </el-button>
                <el-button size="small" @click="setMediaMode('text')">返回文本阅读</el-button>
              </div>
            </template>
          </el-alert>
        </div>
      </div>
    </div>

    <!-- 3. 视频播放模式 -->
    <div v-else-if="mediaMode === 'video'" class="video-container">
      <div class="video-wrapper">
        <div class="video-player-box">
          <iframe
            v-if="isVideoIframe"
            :src="videoSrc"
            class="video-iframe"
            allowfullscreen
            allow="autoplay; encrypted-media; fullscreen"
            frameborder="0"
          ></iframe>
          <video
            v-else-if="videoSrc"
            ref="videoRef"
            :src="videoSrc"
            controls
            autoplay
            playsinline
            class="video-el"
            @ended="onVideoEnded"
            @error="onVideoError"
          ></video>
          <div v-else class="video-empty">
            <el-empty description="当前章节未解析到直接视频源">
              <el-button type="primary" @click="setMediaMode('text')">查看章节原文</el-button>
              <el-button v-if="rawVideoUrl" @click="openExternalVideo(rawVideoUrl)">在新标签页打开</el-button>
            </el-empty>
          </div>
        </div>

        <div class="video-bar">
          <div class="video-meta">
            <span class="video-title">{{ chapter?.title }}</span>
            <span class="video-sub">《{{ bookName }}》</span>
          </div>
          <div class="video-actions">
            <el-button size="small" :disabled="chapterIndex <= 0" @click="prevChapter(false)">上一集</el-button>
            <el-button size="small" type="primary" :disabled="chapterIndex >= toc.length - 1" @click="nextChapter">下一集</el-button>
            <el-button size="small" @click="catalogVisible = true"><el-icon><Menu /></el-icon>&nbsp;选集</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 文本分页阅读模式 -->
    <div
      v-else-if="readMode === 'page'"
      class="page-container"
      ref="pageContainerRef"
      @wheel="onPageWheel"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchCancel"
      @mousedown="onMouseDown"
    >
      <!-- 顶部轻量页眉 -->
      <div class="page-meta-header">
        <span class="meta-book">{{ bookName }}</span>
        <span class="meta-chapter">{{ chapter?.title }}</span>
      </div>

      <!-- 分页可视视口 -->
      <div class="page-viewport" ref="pageViewportRef" :style="{ maxWidth: isMobile ? '100%' : pageWidth + '%' }">
        <div class="page-flow" ref="pageFlowRef" :style="pageFlowStyle">
          <h2 class="chapter-title" v-if="chapter?.title">{{ chapter.title }}</h2>
          <div class="paragraphs">
            <p
              v-for="(para, i) in paragraphs"
              :key="i"
              :style="{ textIndent: '2em' }"
              v-html="renderParagraphHtml(para)"
            />
          </div>
        </div>
      </div>

      <!-- 底部轻量页脚 -->
      <div class="page-meta-footer">
        <span class="meta-progress">第 {{ currentPage + 1 }} / {{ totalPages }} 页</span>
        <span class="meta-percent">{{ pagePercent }}%</span>
      </div>
    </div>

    <!-- 5. 文本连续滚动阅读模式 -->
    <div
      v-else
      ref="scrollContainerRef"
      class="scroll-container"
      @scroll.passive="onScroll"
      @wheel="onScrollWheel"
    >
      <div ref="contentRef" class="content" :style="contentStyle">
        <h2 class="chapter-title" v-if="chapter?.title">{{ chapter.title }}</h2>
        <div class="paragraphs">
          <p
            v-for="(para, i) in paragraphs"
            :key="i"
            :style="{ textIndent: '2em' }"
            v-html="renderParagraphHtml(para)"
          />
        </div>
        <div class="nav-footer">
          <el-button v-if="chapterIndex > 0" @click.stop="prevChapter(false)">上一章</el-button>
          <span class="muted" v-if="chapterIndex === 0">已是第一章</span>
          <el-button type="primary" @click.stop="nextChapter">下一章</el-button>
        </div>
      </div>
    </div>

    <!-- 底部常驻/呼出控制栏（子菜单） -->
    <div class="reader-footer" :class="{ show: chromeVisible }" @click.stop>
      <!-- 进度滑块行 -->
      <div class="footer-slider-row">
        <el-button text size="small" :disabled="chapterIndex <= 0" @click.stop="prevChapter(false)">上一章</el-button>
        <el-slider
          v-model="sliderChapter"
          :min="1"
          :max="toc.length || 1"
          :format-tooltip="(val: number) => toc[val - 1]?.title || `第 ${val} 章`"
          @change="onSliderChange"
          class="chapter-slider"
        />
        <el-button text size="small" :disabled="chapterIndex >= toc.length - 1" @click.stop="nextChapter">下一章</el-button>
      </div>

      <!-- 快捷操作子菜单 -->
      <div class="footer-actions-row">
        <el-button v-if="!isOnShelf" text size="small" :loading="addingShelf" @click.stop="onAddToShelf">
          <el-icon><Plus /></el-icon>&nbsp;加书架
        </el-button>
        <el-button text size="small" @click.stop="catalogVisible = true">
          <el-icon><Menu /></el-icon>&nbsp;目录
        </el-button>
        <el-button text size="small" @click.stop="toggleReadMode">
          <el-icon><CopyDocument /></el-icon>&nbsp;{{ readMode === 'page' ? '切至滚动' : '切至分页' }}
        </el-button>
        <el-button text size="small" @click.stop="openChangeSourceDrawer">
          <el-icon><Refresh /></el-icon>&nbsp;换源
        </el-button>
        <el-button text size="small" @click.stop="notesVisible = true">
          <el-icon><CollectionTag /></el-icon>&nbsp;笔记
        </el-button>
        <el-button text size="small" :title="themeStore.isDark ? '切换至日间模式' : '切换至暗色模式'" @click.stop="toggleDarkMode">
          <el-icon><Moon v-if="themeStore.isDark" /><Sunny v-else /></el-icon>&nbsp;{{ themeStore.isDark ? '日间' : '暗色' }}
        </el-button>
        <el-button text size="small" :title="isFullscreen ? '退出全屏' : '全屏阅读'" @click.stop="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>&nbsp;{{ isFullscreen ? '还原' : '全屏' }}
        </el-button>
        <el-button text size="small" @click.stop="settingsVisible = true">
          <el-icon><Setting /></el-icon>&nbsp;设置
        </el-button>
      </div>
    </div>

    <!-- 文本选中浮动工具条 -->
    <div
      v-if="selVisible"
      class="sel-toolbar"
      :style="{ left: selX + 'px', top: selY + 'px' }"
      @click.stop
    >
      <el-button size="small" @click="copySelection">
        <el-icon><CopyDocument /></el-icon>&nbsp;复制
      </el-button>
      <el-dropdown trigger="click" @command="addHighlight">
        <el-button size="small" type="warning">
          <el-icon><EditPen /></el-icon>&nbsp;划线
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="yellow">🟡 黄色标记</el-dropdown-item>
            <el-dropdown-item command="green">🟢 绿色重点</el-dropdown-item>
            <el-dropdown-item command="pink">🌸 粉色心得</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button size="small" type="success" @click="speakSelection">
        <el-icon><Headset /></el-icon>&nbsp;朗读
      </el-button>
      <el-button size="small" @click="searchWebSelection">
        <el-icon><Search /></el-icon>&nbsp;全网搜
      </el-button>
    </div>

    <!-- 设置面板抽屉 -->
    <el-drawer v-model="settingsVisible" title="阅读设置" size="400px" direction="btt" class="reader-theme-drawer">
      <div class="setting-group">
        <div class="setting-label">阅读模式</div>
        <el-radio-group v-model="mediaMode" size="default" @change="userOverrideMode = true">
          <el-radio-button value="text">📖 文本阅读</el-radio-button>
          <el-radio-button value="comic">🖼️ 漫画阅读</el-radio-button>
          <el-radio-button value="audio">🎧 有声播放</el-radio-button>
          <el-radio-button value="video">🎬 视频播放</el-radio-button>
        </el-radio-group>
      </div>
      <div class="setting-group" v-if="mediaMode === 'text'">
        <div class="setting-label">文本翻页模式</div>
        <el-radio-group v-model="readMode" size="default" @change="onReadModeChange">
          <el-radio-button value="page">📖 分页浏览</el-radio-button>
          <el-radio-button value="scroll">📜 连续滚动</el-radio-button>
        </el-radio-group>
      </div>
      <div class="setting-group" v-if="mediaMode === 'text'">
        <div class="setting-label">字号 {{ fontSize }}px</div>
        <el-slider v-model="fontSize" :min="14" :max="36" :step="1" />
      </div>
      <div class="setting-group" v-if="mediaMode === 'text'">
        <div class="setting-label">行距 {{ lineHeight.toFixed(1) }}</div>
        <el-slider v-model="lineHeight" :min="1.2" :max="2.6" :step="0.1" />
      </div>
      <div class="setting-group" v-if="mediaMode === 'text'">
        <div class="setting-label">
          <span>页宽 {{ pageWidth }}%</span>
          <span v-if="isMobile" class="sub-setting-tip">（手机端默认满宽，电脑端生效）</span>
        </div>
        <el-slider v-model="pageWidth" :min="50" :max="100" :step="5" />
      </div>
      <div class="setting-group" v-if="mediaMode === 'text'">
        <div class="setting-label">字体选择</div>
        <el-radio-group v-model="fontFamily" size="small">
          <el-radio-button v-for="f in fontFamilies" :key="f.value" :value="f.value">
            {{ f.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="setting-group">
        <div class="setting-label">显示与交互</div>
        <div class="setting-row-switch">
          <span class="setting-label-switch">暗色模式</span>
          <el-switch :model-value="themeStore.isDark" @update:model-value="toggleDarkMode" />
        </div>
        <div class="setting-row-switch" style="margin-top: 10px;">
          <span class="setting-label-switch">全屏沉浸阅读</span>
          <el-switch :model-value="isFullscreen" @update:model-value="toggleFullscreen" />
        </div>
        <div class="setting-row-switch" style="margin-top: 10px;" v-if="mediaMode === 'text' || mediaMode === 'comic'">
          <span class="setting-label-switch">音量键翻页</span>
          <el-switch v-model="volumeKeyPage" />
        </div>
        <div v-if="volumeKeyPage" class="volume-key-notice">
          <el-icon><InfoFilled /></el-icon>
          <span>
            <b>手机端硬件音量键翻页已就绪</b>：系统已启用后台静音媒体流监听。首次进入请轻触屏幕任意位置一次激活浏览器音频权限，之后按手机物理音量加（上一页）/ 减（下一页）即可无声翻页。
          </span>
        </div>
      </div>
      <div class="setting-group">
        <div class="setting-label">
          <span>主题背景</span>
          <span class="sub-setting-tip">{{ themeStore.isDark ? '（暗色系主题）' : '（日间系主题）' }}</span>
        </div>
        <div class="theme-row">
          <div
            v-for="t in (themeStore.isDark ? darkThemes : lightThemes)"
            :key="t.value"
            class="theme-dot"
            :class="[t.value, { active: (themeStore.isDark ? userDarkTheme : userLightTheme) === t.value }]"
            @click="selectTheme(t.value)"
            :title="t.label"
          >
            <span class="theme-dot-text">{{ t.label }}</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 目录抽屉 -->
    <el-drawer v-model="catalogVisible" title="目录" size="360px" class="reader-theme-drawer">
      <el-input
        v-model="chapterFilter"
        placeholder="搜索章节…"
        clearable
        prefix-icon="Search"
        style="margin-bottom: 12px"
      />
      <div class="catalog-list" ref="catalogListRef">
        <div
          v-for="item in filteredToc"
          :key="item.index"
          class="catalog-item"
          :class="{ current: item.index === chapterIndex }"
          @click="jumpTo(item.index)"
        >
          <span class="catalog-title">{{ item.title }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- 划线与笔记抽屉 -->
    <el-drawer v-model="notesVisible" title="划线与笔记" size="380px" class="reader-theme-drawer">
      <div class="notes-header">
        <span>共 {{ highlights.length }} 条笔记</span>
        <el-button link type="danger" size="small" :disabled="!highlights.length" @click="clearAllHighlights">
          清空全部
        </el-button>
      </div>
      <el-divider style="margin: 10px 0" />
      <div class="notes-list">
        <el-empty v-if="!highlights.length" description="暂无划线笔记，在阅读时选中文字即可标注" />
        <div
          v-for="hl in highlights"
          :key="hl.id"
          class="note-card"
          :class="`border-${hl.color}`"
        >
          <div class="note-quote">{{ hl.text }}</div>
          <div class="note-meta">
            <span class="note-ch" @click="jumpTo(hl.chapterIndex)">{{ hl.chapterTitle }}</span>
            <span class="note-time">{{ new Date(hl.createdAt).toLocaleDateString() }}</span>
            <el-button link type="danger" size="small" @click="removeHighlight(hl.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 换源抽屉 -->
    <el-drawer
      v-model="changeSourceDrawerVisible"
      :title="`切换书源 - 《${bookName}》`"
      size="440px"
      class="reader-theme-drawer"
    >
      <div class="change-source-header">
        <div class="cs-info">
          <span>当前章节：<b>{{ chapter?.title || '第 ' + (chapterIndex + 1) + ' 章' }}</b></span>
          <el-button
            size="small"
            :loading="changeSourceSearching"
            :icon="Refresh"
            @click="doSearchSources"
          >
            重新检索
          </el-button>
        </div>
        <div v-if="loadedFromCache" class="cs-tip cache-active">
          ⚡ 已即时呈现本地缓存书源（共 {{ sourceResults.length }} 个）
        </div>
        <div v-else-if="changeSourceSearching" class="cs-tip searching-active">
          🔍 正在全网检索可用书源中...
        </div>
        <div v-else class="muted cs-tip">系统将在换源后自动对齐当前阅读进度至新源对应章节</div>
      </div>
      <el-divider style="margin: 10px 0" />

      <div class="source-list" v-loading="changeSourceSearching && sourceResults.length === 0">
        <el-empty
          v-if="!changeSourceSearching && sourceResults.length === 0 && changeSourceDone"
          description="未在其他书源中找到匹配书籍"
        />

        <div v-for="(src, idx) in sourceResults" :key="src.bookUrl || idx" class="source-item">
          <div class="source-item-top">
            <span class="source-origin">{{ src.originName || src.origin }}</span>
            <el-tag v-if="src.bookUrl === bookUrl" size="small" type="success" effect="plain">当前书源</el-tag>
            <el-button
              v-else
              type="primary"
              size="small"
              :loading="switchingSourceUrl === src.bookUrl"
              @click="onSwitchSource(src)"
            >
              切换此源
            </el-button>
          </div>
          <div class="source-chapter">
            <span class="muted">最新章节：</span>
            <span class="latest-ch-text">{{ src.latestChapter || src.latestChapterTitle || '暂无最新章节记录' }}</span>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Back, Menu, Setting, CopyDocument, Refresh, RefreshRight, EditPen, Headset, Search,
  CollectionTag, Delete, VideoPause, VideoPlay, Close, Loading, Plus, Picture,
  Film, Notebook, ZoomIn, ZoomOut, ScaleToOriginal, Timer, Sunny, Moon, FullScreen, InfoFilled,
  WarningFilled, PictureFilled, Switch,
} from '@element-plus/icons-vue'
import {
  getBookContent, getToc, getProgress, saveProgressWithBeacon,
  searchStream, changeSource, checkOnShelf, addToShelf,
  type ChapterItem, type SearchBookItem,
} from '@/api/library'
import { recordReadingTime } from '@/api/ranking'
import { tokenStore } from '@/api/axios'
import { useThemeStore } from '@/store/themeStore'
import { isChapterPageUrl } from '@/utils/comicImage'
import {
  getCachedChapter,
  saveCachedChapter,
  getCachedSources,
  saveCachedSources,
  adaptFontText,
} from '@/utils/localCache'

const route = useRoute()
const router = useRouter()
const bookUrl = computed(() => (route.query.bookUrl as string) || '')
const bookName = computed(() => (route.query.name as string) || '阅读')
const bookAuthor = computed(() => (route.query.author as string) || '')

const toc = ref<ChapterItem[]>([])
const tocLoading = ref(false)
const chapterIndex = ref(0)
const chapter = ref<ChapterItem | null>(null)
const content = ref('')
const loadingContent = ref(false)

// ===== 书架状态与加入书架 =====
const isOnShelf = ref(false)
const addingShelf = ref(false)

const checkShelfStatus = async () => {
  if (!bookUrl.value) return
  try {
    isOnShelf.value = await checkOnShelf(bookUrl.value)
  } catch {
    isOnShelf.value = false
  }
}

const onAddToShelf = async () => {
  if (!bookUrl.value) return
  addingShelf.value = true
  try {
    const res = await addToShelf({
      name: bookName.value,
      author: bookAuthor.value,
      bookUrl: bookUrl.value,
      origin: '',
      originName: '',
    })
    if (res.isSuccess) {
      isOnShelf.value = true
      ElMessage.success(`《${bookName.value}》已加入书架`)
    } else {
      ElMessage.error(res.errorMsg || '加入书架失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.errorMsg || '加入书架失败')
  } finally {
    addingShelf.value = false
  }
}

// ===== 媒体模式（文本 / 漫画 / 有声 / 视频） =====
export type MediaMode = 'text' | 'comic' | 'audio' | 'video'
const mediaMode = ref<MediaMode>('text')
const userOverrideMode = ref(false)

const mediaModeLabel = computed(() => {
  switch (mediaMode.value) {
    case 'comic': return '漫画'
    case 'audio': return '有声'
    case 'video': return '视频'
    default: return '文本'
  }
})

const setMediaMode = (mode: MediaMode) => {
  mediaMode.value = mode
  userOverrideMode.value = true
  ElMessage.success(`已切换为【${mediaModeLabel.value}】模式`)
}

const detectMediaMode = () => {
  if (userOverrideMode.value) return
  const qType = route.query.type !== undefined && route.query.type !== null ? String(route.query.type) : ''
  const numType = Number(qType)
  if (qType === '1' || numType === 32 || (!isNaN(numType) && (numType & 32) === 32)) {
    mediaMode.value = 'audio'
    return
  }
  if (qType === '2' || qType === '64' || (!isNaN(numType) && (numType & 64) === 64)) {
    mediaMode.value = 'comic'
    return
  }
  if (qType === '3' || qType === '4' || (!isNaN(numType) && (numType & 4) === 4)) {
    mediaMode.value = 'video'
    return
  }

  const c = content.value || ''
  // 视频启发式检测
  if (
    /\.(m3u8|mp4|flv|webm)(\?|#|$)/i.test(c) ||
    /<video\b/i.test(c) ||
    /<iframe\b[^>]+(?:video|player|bilibili|m3u8|mp4)/i.test(c)
  ) {
    mediaMode.value = 'video'
    return
  }

  // 音频启发式检测
  if (
    /\.(mp3|m4a|aac|wav|ogg|flac)(\?|#|$)/i.test(c) ||
    /<audio\b/i.test(c)
  ) {
    mediaMode.value = 'audio'
    return
  }

  // 漫画启发式检测（含 <img> 标签、data-src、或者纯图片链接）
  if (
    /<img\b[^>]+(?:src|data-src|data-original)=/i.test(c) ||
    /https?:\/\/[^\s"',>]+\s*,\s*\{/i.test(c)
  ) {
    mediaMode.value = 'comic'
    return
  }
  const lines = c.split(/\n+/).map(s => s.trim()).filter(Boolean)
  const imgLines = lines.filter(l => {
    const cleanL = l.split(/,\s*\{/)[0].trim()
    return /^https?:\/\/.+\.(jpg|jpeg|png|webp|gif|bmp)(\?.*)?$/i.test(cleanL)
  })
  if (imgLines.length >= 2 || (lines.length > 0 && imgLines.length / lines.length >= 0.5)) {
    mediaMode.value = 'comic'
    return
  }

  mediaMode.value = 'text'
}

// ===== 动态艺术封面与图片代理 =====
function getDynamicArtCover(name: string, author: string): string {
  const c1 = '#4f46e5'
  const c2 = '#7c3aed'
  const title = (name || '经典').slice(0, 8)
  const auth = (author || '佚名').slice(0, 8)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
    <defs>
      <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${c1}" />
        <stop offset="100%" stop-color="${c2}" />
      </linearGradient>
    </defs>
    <rect width="100%" height="100%" fill="url(#g)" rx="100"/>
    <circle cx="100" cy="100" r="92" fill="none" stroke="rgba(255,255,255,0.25)" stroke-width="2"/>
    <text x="100" y="94" font-size="20" font-weight="bold" fill="#ffffff" text-anchor="middle" font-family="-apple-system, sans-serif">${title}</text>
    <text x="100" y="126" font-size="13" fill="rgba(255,255,255,0.85)" text-anchor="middle" font-family="-apple-system, sans-serif">${auth}</text>
  </svg>`
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}

const bookCover = computed(() => {
  return getDynamicArtCover(bookName.value, bookAuthor.value)
})

function formatProxyImage(rawUrl: string): string {
  if (!rawUrl) return ''
  if (rawUrl.startsWith('data:')) return rawUrl
  const token = tokenStore.access
  // 净化 bookUrl，剔除后接的 JSON 参数及新行
  const cleanBookUrl = (bookUrl.value || '').split(/,\s*\{/)[0].split(/[\r\n]+/)[0].trim()
  return `/api/image?url=${encodeURIComponent(rawUrl)}&bookUrl=${encodeURIComponent(cleanBookUrl)}${token ? `&token=${encodeURIComponent(token)}` : ''}`
}

// ===== 漫画模式逻辑 =====
const comicScale = ref(100)
const comicContainerRef = ref<HTMLElement>()

function isLikelyDummyOrHtml(rawUrl: string): boolean {
  if (!rawUrl) return true
  const clean = rawUrl.split(/,\s*\{/)[0].trim().toLowerCase()
  if (clean.endsWith('.html') || clean.endsWith('.htm') || clean.endsWith('.php') || clean.endsWith('.aspx') || clean.endsWith('.ashx')) {
    return true
  }
  if (isChapterPageUrl(rawUrl, chapter.value?.url)) return true
  const cleanBook = (bookUrl.value || '').split(/,\s*\{/)[0].trim().toLowerCase()
  if (cleanBook && clean === cleanBook) return true
  return false
}

const comicImages = computed(() => {
  if (!content.value) return []
  const c = content.value
  const urls: string[] = []

  // 1. 带 Legado JSON 请求头参数的 <img> 标签 (例如 <img src="https://...jpg,{"headers":...}">)
  const imgWithOptionRegex = /<img\b[^>]*?\s(?:src|data-src|data-original)=["']?(https?:\/\/[^\s"',>]+\s*,\s*\{[\s\S]*?\})["']?\s*\/?>/gi
  let match
  while ((match = imgWithOptionRegex.exec(c)) !== null) {
    const u = match[1]?.trim()
    if (u && !isLikelyDummyOrHtml(u)) urls.push(u)
  }
  if (urls.length > 0) {
    return urls.map(u => formatProxyImage(u))
  }

  // 2. 常规或懒加载 <img> 标签 (支持 src, data-src, data-original, srcset，安全终止于引号，不吞并后续属性)
  const standardImgRegex = /<img\b[^>]*?\s(?:src|data-src|data-original)=["']([^"'\s>]+)["'][^>]*>/gi
  while ((match = standardImgRegex.exec(c)) !== null) {
    const u = match[1]?.trim()
    if (u && !isLikelyDummyOrHtml(u)) urls.push(u)
  }
  if (urls.length > 0) {
    return urls.map(u => formatProxyImage(u))
  }

  // 3. 按行分割的图片链接 (包含形如 https://...jpg,{"headers":...} 的整行直链)
  const lines = c.split(/\n+/).map(s => s.trim()).filter(Boolean)
  const imgUrlRegex = /^https?:\/\/.+\.(jpg|jpeg|png|webp|gif|bmp)(\?.*)?$/i
  const matchedLines = lines.filter(l => {
    const clean = l.split(/,\s*\{/)[0].replace(/^["']|["']$/g, '').trim()
    return imgUrlRegex.test(clean) && !isLikelyDummyOrHtml(l)
  })
  if (matchedLines.length > 0) {
    return matchedLines.map(l => {
      let clean = l.trim()
      if (clean.startsWith('"') && clean.endsWith('"')) clean = clean.slice(1, -1)
      if (clean.startsWith("'") && clean.endsWith("'")) clean = clean.slice(1, -1)
      return formatProxyImage(clean)
    })
  }

  // 4. 在漫画模式下，提取所有以 http/https 开头的有效行
  if (mediaMode.value === 'comic') {
    const httpLines = lines.filter(l => /^https?:\/\//i.test(l) && !isLikelyDummyOrHtml(l))
    if (httpLines.length > 0) {
      return httpLines.map(l => {
        let clean = l.trim()
        if (clean.startsWith('"') && clean.endsWith('"')) clean = clean.slice(1, -1)
        if (clean.startsWith("'") && clean.endsWith("'")) clean = clean.slice(1, -1)
        return formatProxyImage(clean)
      })
    }
  }

  return []
})

// 漫画单页状态追踪与容错
const comicImgStatus = ref<Record<number, 'loading' | 'loaded' | 'error'>>({})
const comicImgRetryTs = ref<Record<number, number>>({})

const failedImgCount = computed(() => {
  return Object.values(comicImgStatus.value).filter(s => s === 'error').length
})

function getComicImgSrc(baseSrc: string, idx: number): string {
  const ts = comicImgRetryTs.value[idx]
  if (!ts) return baseSrc
  const sep = baseSrc.includes('?') ? '&' : '?'
  return `${baseSrc}${sep}_t=${ts}`
}

const onImgLoadSuccess = (idx: number) => {
  comicImgStatus.value[idx] = 'loaded'
}

const onImgLoadError = (idx: number, e: Event, originalUrl: string) => {
  const target = e.target as HTMLImageElement
  // 第一轮自动降级：若走 /api/image 代理失败，尝试去参直连源站图片
  if (target && !target.dataset.retried) {
    target.dataset.retried = '1'
    if (target.src.includes('/api/image')) {
      try {
        const parsed = new URL(target.src)
        let raw = parsed.searchParams.get('url')
        if (raw) {
          const commaIdx = raw.search(/,\s*\{/)
          if (commaIdx > 0) raw = raw.substring(0, commaIdx).trim()
          target.src = raw
          return
        }
      } catch {
        const match = target.src.match(/[?&]url=([^&]+)/)
        if (match && match[1]) {
          let raw = decodeURIComponent(match[1])
          const commaIdx = raw.search(/,\s*\{/)
          if (commaIdx > 0) raw = raw.substring(0, commaIdx).trim()
          target.src = raw
          return
        }
      }
    }
  }
  // 直连与代理均失败，标记为错误状态以渲染容错卡片
  comicImgStatus.value[idx] = 'error'
}

const retrySingleImg = (idx: number) => {
  comicImgStatus.value[idx] = 'loading'
  comicImgRetryTs.value[idx] = Date.now()
}

const retryAllFailedImgs = () => {
  const now = Date.now()
  Object.keys(comicImgStatus.value).forEach(k => {
    const idx = Number(k)
    if (comicImgStatus.value[idx] === 'error') {
      comicImgStatus.value[idx] = 'loading'
      comicImgRetryTs.value[idx] = now
    }
  })
}

// 切换章节或更换书籍时，清空图片错误状态
watch([chapterIndex, bookUrl], () => {
  comicImgStatus.value = {}
  comicImgRetryTs.value = {}
})

// ===== 音频模式逻辑 =====
const audioRef = ref<HTMLAudioElement>()
const isAudioPlaying = ref(false)
const audioCurrentTime = ref(0)
const audioDuration = ref(0)
const audioPlaybackRate = ref(1.0)
const sleepTimerMinutes = ref(0)
let sleepCountdownTimer: ReturnType<typeof setTimeout> | null = null

const audioSrc = computed(() => {
  const c = content.value || ''
  const tagMatch = c.match(/<audio[^>]+src=["']?([^"'>\s]+)/i)
  if (tagMatch && tagMatch[1]) return tagMatch[1]
  const urlMatch = c.match(/(https?:\/\/[^\s"'<>]+\.(?:mp3|m4a|aac|wav|ogg|flac)(?:\?[^\s"'<>]*)?)/i)
  if (urlMatch && urlMatch[1]) return urlMatch[1]
  if (route.query.type === '1' || mediaMode.value === 'audio') {
    const httpMatch = c.match(/(https?:\/\/[^\s"'<>]+)/i)
    if (httpMatch && httpMatch[1] && !httpMatch[1].match(/\.(jpg|jpeg|png|webp|gif|html|m3u8|mp4)/i)) {
      return httpMatch[1]
    }
  }
  return ''
})

const formatTime = (secs: number) => {
  if (!secs || isNaN(secs) || secs < 0) return '00:00'
  const m = Math.floor(secs / 60)
  const s = Math.floor(secs % 60)
  const mm = m < 10 ? `0${m}` : `${m}`
  const ss = s < 10 ? `0${s}` : `${s}`
  return `${mm}:${ss}`
}

const toggleAudioPlay = () => {
  if (!audioRef.value) {
    if (!audioSrc.value) {
      if (isSpeaking.value) {
        pauseSpeak()
      } else {
        speakSelection()
      }
    }
    return
  }
  if (audioRef.value.paused) {
    audioRef.value.play().then(() => {
      isAudioPlaying.value = true
    }).catch(() => {
      ElMessage.error('音频播放失败，请检查书源网络')
    })
  } else {
    audioRef.value.pause()
    isAudioPlaying.value = false
  }
}

const onAudioTimeUpdate = () => {
  if (audioRef.value) {
    audioCurrentTime.value = audioRef.value.currentTime
  }
}

const onAudioLoadedMetadata = () => {
  if (audioRef.value) {
    audioDuration.value = audioRef.value.duration || 0
    audioRef.value.playbackRate = audioPlaybackRate.value
    if (isAudioPlaying.value) {
      audioRef.value.play().catch(() => {})
    }
  }
}

const seekAudio = (val: number | number[]) => {
  const target = Array.isArray(val) ? val[0] : val
  if (audioRef.value && typeof target === 'number') {
    audioRef.value.currentTime = target
    audioCurrentTime.value = target
  }
}

const skipAudio = (seconds: number) => {
  if (audioRef.value) {
    const cur = audioRef.value.currentTime
    const dur = audioRef.value.duration || cur + 30
    audioRef.value.currentTime = Math.max(0, Math.min(dur, cur + seconds))
  }
}

const changeAudioRate = (val: string) => {
  const rate = parseFloat(val)
  audioPlaybackRate.value = rate
  if (audioRef.value) {
    audioRef.value.playbackRate = rate
  }
  ElMessage.success(`播放倍速已设为 ${rate}x`)
}

const sleepTimerLabel = computed(() => {
  if (sleepTimerMinutes.value === -1) return '本章播完停止'
  if (sleepTimerMinutes.value > 0) return `${sleepTimerMinutes.value} 分钟后停止`
  return '定时关闭'
})

const setSleepTimer = (val: string) => {
  if (sleepCountdownTimer) clearTimeout(sleepCountdownTimer)
  if (val === 'chapter') {
    sleepTimerMinutes.value = -1
    ElMessage.success('已设置：本章播放完成后停止')
    return
  }
  const min = parseInt(val)
  sleepTimerMinutes.value = min
  if (min > 0) {
    ElMessage.success(`已设置：${min} 分钟后停止播放`)
    sleepCountdownTimer = setTimeout(() => {
      if (audioRef.value) {
        audioRef.value.pause()
        isAudioPlaying.value = false
      }
      sleepTimerMinutes.value = 0
      ElMessage.info('定时关闭生效，音频已暂停')
    }, min * 60 * 1000)
  } else {
    ElMessage.info('定时关闭已取消')
  }
}

const onAudioEnded = () => {
  isAudioPlaying.value = false
  if (sleepTimerMinutes.value === -1) {
    sleepTimerMinutes.value = 0
    ElMessage.info('本章播放完毕，定时停止生效')
    return
  }
  if (chapterIndex.value < toc.value.length - 1) {
    ElMessage.success('本章播放完毕，自动播放下一章')
    isAudioPlaying.value = true
    nextChapter()
  } else {
    ElMessage.info('已播放到最后一章')
  }
}

const onAudioError = () => {
  isAudioPlaying.value = false
  ElMessage.error('音频流加载失败，建议尝试换源或使用文本朗读')
}

// ===== 视频模式逻辑 =====
const videoRef = ref<HTMLVideoElement>()

const videoSrc = computed(() => {
  const c = content.value || ''
  const vTag = c.match(/<video[^>]+src=["']?([^"'>\s]+)/i)
  if (vTag && vTag[1]) return vTag[1]
  const vUrl = c.match(/(https?:\/\/[^\s"'<>]+\.(?:m3u8|mp4|flv|webm|avi)(?:\?[^\s"'<>]*)?)/i)
  if (vUrl && vUrl[1]) return vUrl[1]
  const iTag = c.match(/<iframe[^>]+src=["']?([^"'>\s]+)/i)
  if (iTag && iTag[1]) return iTag[1]
  if (route.query.type === '3' || mediaMode.value === 'video') {
    const httpMatch = c.match(/(https?:\/\/[^\s"'<>]+)/i)
    if (httpMatch && httpMatch[1] && !httpMatch[1].match(/\.(jpg|jpeg|png|webp|gif)/i)) {
      return httpMatch[1]
    }
  }
  return ''
})

const isVideoIframe = computed(() => {
  const src = videoSrc.value.toLowerCase()
  return src.includes('<iframe') || src.includes('.html') || src.includes('player') || src.includes('embed') || src.includes('bilibili') || src.includes('youku')
})

const rawVideoUrl = computed(() => {
  const m = content.value?.match(/https?:\/\/[^\s"'<>]+/i)
  return m ? m[0] : ''
})

const onVideoEnded = () => {
  if (chapterIndex.value < toc.value.length - 1) {
    ElMessage.success('当前集播放完毕，即将进入下一集')
    setTimeout(() => {
      nextChapter()
    }, 1200)
  }
}

const onVideoError = () => {
  ElMessage.error('视频解析或播放失败，建议更换书源或在新标签页打开')
}

const openExternalVideo = (url: string) => {
  if (url) window.open(url, '_blank')
}

// ===== 全屏沉浸与移动端视口适配 =====
const isMobile = ref(false)
const checkIsMobile = () => {
  if (typeof window !== 'undefined') {
    isMobile.value = window.innerWidth <= 768
  }
}
checkIsMobile()

const isFullscreen = ref(false)
const toggleFullscreen = async () => {
  try {
    if (!document.fullscreenElement && !(document as any).webkitFullscreenElement) {
      if (document.documentElement.requestFullscreen) {
        await document.documentElement.requestFullscreen()
      } else if ((document.documentElement as any).webkitRequestFullscreen) {
        await (document.documentElement as any).webkitRequestFullscreen()
      }
      isFullscreen.value = true
      if ((navigator as any).keyboard?.lock) {
        try {
          await (navigator as any).keyboard.lock(['AudioVolumeDown', 'AudioVolumeUp'])
        } catch { /* ignore */ }
      }
    } else {
      if (document.exitFullscreen) {
        await document.exitFullscreen()
      } else if ((document as any).webkitExitFullscreen) {
        await (document as any).webkitExitFullscreen()
      }
      isFullscreen.value = false
      if ((navigator as any).keyboard?.unlock) {
        try {
          (navigator as any).keyboard.unlock()
        } catch { /* ignore */ }
      }
    }
  } catch (e) {
    console.warn('Fullscreen toggle failed:', e)
  }
}

const onFullscreenChange = () => {
  const fs = !!(document.fullscreenElement || (document as any).webkitFullscreenElement)
  isFullscreen.value = fs
  if (fs && (navigator as any).keyboard?.lock) {
    try {
      (navigator as any).keyboard.lock(['AudioVolumeDown', 'AudioVolumeUp'])
    } catch { /* ignore */ }
  }
}

// ===== 阅读设置（本地持久化）与深色模式独立联动 =====
const themeStore = useThemeStore()
const SETTINGS_KEY = 'legado_reader_settings'
const SETTINGS_VERSION = 4
const fontSize = ref(20)
const lineHeight = ref(1.8)
const pageWidth = ref(85)
const userLightTheme = ref('paper')
const userDarkTheme = ref('night')
const theme = ref('paper')
const fontFamily = ref('system')
const readMode = ref<'page' | 'scroll'>('scroll')
const volumeKeyPage = ref(true)

// 分页浏览模式状态与平滑拖拽翻页
const currentPage = ref(0)
const totalPages = ref(1)
const viewportWidth = ref(800)
const pageContainerRef = ref<HTMLElement>()
const pageViewportRef = ref<HTMLElement>()
const pageFlowRef = ref<HTMLElement>()
const sliderChapter = ref(1)

// 拖动翻页状态
const isDragging = ref(false)
const dragOffset = ref(0)
let hasDragged = false

const pagePercent = computed(() => {
  if (totalPages.value <= 1) return 100
  return Math.round(((currentPage.value + 1) / totalPages.value) * 100)
})

const pageFlowStyle = computed<CSSProperties>(() => {
  const w = viewportWidth.value || 800
  const gap = 32
  const baseOffset = -(currentPage.value * (w + gap))
  const totalOffset = baseOffset + dragOffset.value
  return {
    fontSize: `${fontSize.value}px`,
    lineHeight: `${lineHeight.value}`,
    columnWidth: `${w}px`,
    columnGap: `${gap}px`,
    transform: `translateX(${totalOffset}px)`,
    transition: isDragging.value ? 'none' : 'transform 0.28s cubic-bezier(0.25, 1, 0.5, 1)',
    width: 'auto',
    height: '100%',
    maxHeight: '100%',
    userSelect: isDragging.value ? 'none' : 'text',
  }
})

const lightThemes = [
  { value: 'paper', label: '羊皮纸' },
  { value: 'white', label: '白昼' },
  { value: 'eye', label: '护眼绿' },
  { value: 'gray', label: '雅致灰' },
  { value: 'warm', label: '暖阳杏' },
]

const darkThemes = [
  { value: 'night', label: '极夜黑' },
  { value: 'navy', label: '深海蓝' },
  { value: 'dark-green', label: '墨绿夜' },
  { value: 'dark-purple', label: '暗夜紫' },
  { value: 'dark-coffee', label: '浓黑咖' },
]

const toggleDarkMode = () => {
  themeStore.toggleDark()
  if (themeStore.isDark) {
    theme.value = userDarkTheme.value || 'night'
    ElMessage.success('已切换至暗色模式')
  } else {
    theme.value = userLightTheme.value || 'paper'
    ElMessage.success('已切换至日间模式')
  }
}

const selectTheme = (tVal: string) => {
  if (themeStore.isDark) {
    userDarkTheme.value = tVal
    theme.value = tVal
  } else {
    userLightTheme.value = tVal
    theme.value = tVal
  }
}

watch(() => themeStore.isDark, (isDark) => {
  if (isDark) {
    theme.value = userDarkTheme.value || 'night'
  } else {
    theme.value = userLightTheme.value || 'paper'
  }
})

const updatePageCount = () => {
  if (readMode.value !== 'page' || mediaMode.value !== 'text') return
  const vp = pageViewportRef.value
  const flow = pageFlowRef.value
  if (!vp || !flow) return
  const w = vp.clientWidth
  if (!w) return
  viewportWidth.value = w
  const gap = 32
  const scrollW = flow.scrollWidth
  const pages = Math.max(1, Math.ceil((scrollW + gap) / (w + gap)))
  totalPages.value = pages
  if (currentPage.value >= pages) {
    currentPage.value = pages - 1
  }
}

const fontFamilies = [
  { value: 'system', label: '默认黑体' },
  { value: 'serif', label: '宋体衬线' },
  { value: 'kaiti', label: '优雅楷体' },
]

const darkThemeValues = ['night', 'navy', 'dark-green', 'dark-purple', 'dark-coffee']
try {
  const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}')
  fontSize.value = saved.fontSize ?? 20
  lineHeight.value = saved.lineHeight ?? 1.8
  pageWidth.value = saved.pageWidth ?? 85
  fontFamily.value = saved.fontFamily ?? 'system'
  volumeKeyPage.value = saved.volumeKeyPage ?? true

  userLightTheme.value = saved.userLightTheme || saved.userDayTheme || (saved.theme && !darkThemeValues.includes(saved.theme) ? saved.theme : 'paper')
  userDarkTheme.value = saved.userDarkTheme || (saved.theme && darkThemeValues.includes(saved.theme) ? saved.theme : 'night')

  if (themeStore.isDark) {
    theme.value = userDarkTheme.value
  } else {
    theme.value = userLightTheme.value
  }

  if (saved.readMode === 'page' || saved.readMode === 'scroll') {
    readMode.value = saved.readMode
  } else {
    readMode.value = 'scroll'
  }
} catch { /* ignore */ }

watch([fontSize, lineHeight, pageWidth, theme, fontFamily, readMode, volumeKeyPage, userLightTheme, userDarkTheme], () => {
  saveSettings()
  nextTick(updatePageCount)
})

function saveSettings() {
  localStorage.setItem(
    SETTINGS_KEY,
    JSON.stringify({
      version: SETTINGS_VERSION,
      fontSize: fontSize.value,
      lineHeight: lineHeight.value,
      pageWidth: pageWidth.value,
      theme: theme.value,
      userLightTheme: userLightTheme.value,
      userDarkTheme: userDarkTheme.value,
      fontFamily: fontFamily.value,
      readMode: readMode.value,
      volumeKeyPage: volumeKeyPage.value,
    }),
  )
}

const onReadModeChange = () => {
  saveSettings()
  nextTick(() => {
    if (readMode.value === 'page') {
      if (pageViewportRef.value && resizeObserver) {
        resizeObserver.observe(pageViewportRef.value)
      }
      updatePageCount()
    }
  })
}

const toggleReadMode = () => {
  readMode.value = readMode.value === 'page' ? 'scroll' : 'page'
  onReadModeChange()
  ElMessage.success(`已切换为${readMode.value === 'page' ? '分页' : '滚动'}浏览模式`)
}

const onSliderChange = (val: number | number[]) => {
  const num = Array.isArray(val) ? val[0] : val
  if (typeof num === 'number' && num >= 1 && num <= toc.value.length) {
    jumpTo(num - 1)
  }
}

const contentStyle = computed(() => ({
  fontSize: `${fontSize.value}px`,
  lineHeight: `${lineHeight.value}`,
  maxWidth: isMobile.value ? '100%' : `${pageWidth.value}%`,
}))

const paragraphs = computed(() =>
  content.value.split(/\n+/).map(s => adaptFontText(s).trim()).filter(Boolean),
)

const filteredToc = computed(() => {
  const kw = chapterFilter.value.trim()
  if (!kw) return toc.value
  return toc.value.filter(c => c.title.includes(kw))
})

// ===== 数据加载 =====
const tocError = ref('')

const loadToc = async () => {
  tocLoading.value = true
  tocError.value = ''
  try {
    const res = await getToc(bookUrl.value)
    toc.value = res
    if (res.length === 0) {
      tocError.value = '目录为空，该书源暂无可读章节'
    }
  } catch (e: any) {
    const msg = e?.response?.data?.errorMsg || e.message || '目录加载失败'
    tocError.value = msg
    ElMessage.error(msg)
  } finally {
    tocLoading.value = false
  }
}

const retryLoad = async () => {
  await loadToc()
  if (toc.value.length > 0) {
    await loadChapter(0)
  }
}

const applyReaderPosition = async (options?: { toLastPage?: boolean; initialPos?: number }) => {
  await nextTick()
  if (readMode.value === 'page' && mediaMode.value === 'text') {
    updatePageCount()
    if (options?.toLastPage) {
      currentPage.value = Math.max(0, totalPages.value - 1)
    } else if (typeof options?.initialPos === 'number' && options.initialPos > 0) {
      currentPage.value = Math.min(totalPages.value - 1, options.initialPos)
    } else {
      currentPage.value = 0
    }
  } else if (mediaMode.value === 'text') {
    await nextTick()
    const sc = scrollContainerRef.value
    if (sc) {
      sc.style.scrollBehavior = 'auto'
      if (options?.toLastPage) {
        sc.scrollTop = Math.max(0, sc.scrollHeight - sc.clientHeight)
      } else if (typeof options?.initialPos === 'number' && options.initialPos > 0) {
        sc.scrollTop = options.initialPos
      } else {
        sc.scrollTop = 0
      }
      sc.style.scrollBehavior = ''
    }
  }
}

const prefetchNextChapters = (currentIndex: number) => {
  if (!bookUrl.value || toc.value.length === 0) return
  const currentUrl = bookUrl.value
  for (let i = currentIndex + 1; i <= Math.min(currentIndex + 3, toc.value.length - 1); i++) {
    getBookContent(currentUrl, i)
      .then(res => {
        if (res && res.content && currentUrl === bookUrl.value) {
          saveCachedChapter(currentUrl, i, res.title, adaptFontText(res.content), res.url)
        }
      })
      .catch(() => {})
  }
}

const loadChapter = async (index: number, options?: { toLastPage?: boolean; initialPos?: number }) => {
  try {
    // 1. 优先读取本地 IndexedDB 缓存（0ms 秒开，自动解码 PUA 字符）
    if (bookUrl.value) {
      const cached = await getCachedChapter(bookUrl.value, index)
      if (cached && cached.content) {
        const cleanContent = adaptFontText(cached.content)
        chapter.value = { index: cached.index, title: cached.title, isVolume: false, url: cached.url ?? toc.value.find(c => c.index === cached.index)?.url }
        content.value = cleanContent
        chapterIndex.value = index
        sliderChapter.value = index + 1
        detectMediaMode()
        loadingContent.value = false
        await applyReaderPosition(options)
        scheduleProgressSave()
        prefetchNextChapters(index)
        return
      }
    }

    // 2. 缓存未命中，发起网络加载
    loadingContent.value = true
    const payload = await getBookContent(bookUrl.value, index)
    const cleanContent = adaptFontText(payload.content)
    chapter.value = { index: payload.index, title: payload.title, isVolume: false, url: payload.url ?? toc.value.find(c => c.index === payload.index)?.url }
    content.value = cleanContent
    chapterIndex.value = index
    sliderChapter.value = index + 1
    detectMediaMode()

    // 异步写入本地缓存并预加载后 3 章
    if (bookUrl.value && cleanContent) {
      saveCachedChapter(bookUrl.value, index, payload.title, cleanContent, chapter.value.url)
    }

    await applyReaderPosition(options)
    scheduleProgressSave()
    prefetchNextChapters(index)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.errorMsg || '正文加载失败')
  } finally {
    loadingContent.value = false
  }
}

// ===== 文本选中浮动工具条 =====
const selVisible = ref(false)
const selX = ref(0)
const selY = ref(0)
let selText = ''

const onSelectionChange = () => {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !selText_get(sel)) {
    selVisible.value = false
    return
  }
  const range = sel.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  selText = sel.toString()
  selX.value = Math.min(Math.max(rect.left + rect.width / 2 - 120, 8), window.innerWidth - 260)
  selY.value = Math.max(rect.top - 48, 8)
  selVisible.value = true
}

function selText_get(sel: Selection): string {
  const anchor = (sel as Selection).anchorNode
  if (!anchor) return ''
  const el = anchor.nodeType === Node.TEXT_NODE ? anchor.parentElement : (anchor as HTMLElement)
  if (!el?.closest('.content, .page-flow')) return ''
  return sel.toString()
}

const copySelection = async () => {
  if (!selText) return
  try {
    await navigator.clipboard.writeText(selText)
    ElMessage.success('已复制选中内容')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = selText
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制选中内容')
  }
  selVisible.value = false
  window.getSelection()?.removeAllRanges()
}

const searchWebSelection = () => {
  if (!selText) return
  window.open(`https://www.baidu.com/s?wd=${encodeURIComponent(selText)}`, '_blank')
  selVisible.value = false
}

// ===== 语音朗读 (TTS) =====
const isSpeaking = ref(false)
const isPaused = ref(false)

const speakSelection = () => {
  const textToSpeak = selText.trim() || paragraphs.value.join('\n')
  if (!textToSpeak) return
  if (!('speechSynthesis' in window)) {
    ElMessage.warning('当前浏览器暂不支持语音朗读')
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(textToSpeak)
  utterance.lang = 'zh-CN'
  utterance.rate = 1.0
  utterance.onstart = () => {
    isSpeaking.value = true
    isPaused.value = false
  }
  utterance.onend = () => {
    isSpeaking.value = false
    isPaused.value = false
  }
  utterance.onerror = () => {
    isSpeaking.value = false
    isPaused.value = false
  }
  window.speechSynthesis.speak(utterance)
  selVisible.value = false
}

const pauseSpeak = () => {
  if (window.speechSynthesis.speaking) {
    if (isPaused.value) {
      window.speechSynthesis.resume()
      isPaused.value = false
    } else {
      window.speechSynthesis.pause()
      isPaused.value = true
    }
  }
}

const stopSpeak = () => {
  window.speechSynthesis.cancel()
  isSpeaking.value = false
  isPaused.value = false
}

// ===== 划线与笔记本地持久化 =====
export interface HighlightItem {
  id: string
  chapterIndex: number
  chapterTitle: string
  text: string
  color: 'yellow' | 'green' | 'pink'
  createdAt: number
}

const notesVisible = ref(false)
const highlights = ref<HighlightItem[]>([])
const HL_KEY_PREFIX = 'legado_hl_'

const loadHighlights = () => {
  if (!bookUrl.value) return
  try {
    highlights.value = JSON.parse(localStorage.getItem(`${HL_KEY_PREFIX}${bookUrl.value}`) || '[]')
  } catch {
    highlights.value = []
  }
}

const saveHighlights = () => {
  if (!bookUrl.value) return
  localStorage.setItem(`${HL_KEY_PREFIX}${bookUrl.value}`, JSON.stringify(highlights.value))
}

const addHighlight = (color: string) => {
  const chosenColor = (['yellow', 'green', 'pink'].includes(color) ? color : 'yellow') as 'yellow' | 'green' | 'pink'
  if (!selText.trim()) return
  const item: HighlightItem = {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    chapterIndex: chapterIndex.value,
    chapterTitle: chapter.value?.title || `第 ${chapterIndex.value + 1} 章`,
    text: selText.trim(),
    color: chosenColor,
    createdAt: Date.now(),
  }
  highlights.value.unshift(item)
  saveHighlights()
  ElMessage.success('已加入划线笔记')
  selVisible.value = false
  window.getSelection()?.removeAllRanges()
}

const removeHighlight = (id: string) => {
  highlights.value = highlights.value.filter(h => h.id !== id)
  saveHighlights()
  ElMessage.success('已移除该划线')
}

const clearAllHighlights = async () => {
  try {
    await ElMessageBox.confirm('确定要清空本书的所有划线笔记吗？', '提示', { type: 'warning' })
    highlights.value = []
    saveHighlights()
    ElMessage.success('已清空划线笔记')
  } catch { /* canceled */ }
}

function renderParagraphHtml(para: string): string {
  const currentChapterHls = highlights.value.filter(h => h.chapterIndex === chapterIndex.value)
  let escaped = escapeHtml(para)
  if (!currentChapterHls.length) return escaped

  for (const hl of currentChapterHls) {
    if (!hl.text) continue
    const target = escapeHtml(hl.text)
    if (escaped.includes(target)) {
      escaped = escaped.replaceAll(target, `<mark class="hl-mark hl-${hl.color}">${target}</mark>`)
    }
  }
  return escaped
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// ===== 换源业务 =====
const changeSourceDrawerVisible = ref(false)
const changeSourceSearching = ref(false)
const changeSourceDone = ref(false)
const loadedFromCache = ref(false)
const sourceResults = ref<SearchBookItem[]>([])
const switchingSourceUrl = ref('')
let csController: AbortController | null = null

const extractChapterNum = (title?: string | null): number => {
  if (!title) return -1
  const m = title.match(/(?:第\s*|总\s*|\b)([0-9]+)\s*(?:[章节回卷话页]|贵客|话)?/i)
  if (m) {
    const n = parseInt(m[1], 10)
    if (!isNaN(n)) return n
  }
  return -1
}

function rateSourceQuality(item: SearchBookItem, targetTitle: string): number {
  let score = 0
  const name = (item.name || '').trim().toLowerCase()
  if (targetTitle) {
    if (name === targetTitle) {
      score += 5000
    } else if (name.includes(targetTitle) || targetTitle.includes(name)) {
      score += 1500
    }
  }
  const ch = item.latestChapter || item.latestChapterTitle || ''
  if (ch && ch.trim() && ch !== '暂无' && ch !== '无' && ch !== '暂无最新章节') {
    score += 2000
    const num = extractChapterNum(ch)
    if (num > 0) score += Math.min(num, 1000)
    score += Math.min(ch.length, 50)
  }
  if (item.coverUrl) score += 50
  if (item.intro && item.intro.length > 20) score += 50
  return score
}

// 去重与智能排序：按 bookUrl 全局唯一，按书源 (origin/originName) 严格唯一去重，择优保留与当前书籍最匹配且章节最新的条目；当前书源置顶
function sortAndDeduplicateSources(items: SearchBookItem[], curBookUrl: string, targetBookName?: string): SearchBookItem[] {
  const seenUrls = new Set<string>()
  const seenOriginMap = new Map<string, SearchBookItem>()
  const target = (targetBookName || bookName.value || '').trim().toLowerCase()

  for (const item of items) {
    if (!item.bookUrl) continue
    if (seenUrls.has(item.bookUrl)) continue
    seenUrls.add(item.bookUrl)

    // 书源唯一标识：确保换源列表中每个书源至多出现一次
    const originKey = (item.origin || item.originName || (item as any).bookSourceUrl || '').trim() || item.bookUrl
    if (seenOriginMap.has(originKey)) {
      const existing = seenOriginMap.get(originKey)!
      // 1. 如果新条目是当前正在阅读的书籍，保留当前书籍
      if (item.bookUrl === curBookUrl) {
        seenOriginMap.set(originKey, item)
        continue
      }
      // 2. 如果已存在的条目是当前阅读书籍，不被替换
      if (existing.bookUrl === curBookUrl) {
        continue
      }
      // 3. 同一书源下不同候选项打分择优
      const itemScore = rateSourceQuality(item, target)
      const existingScore = rateSourceQuality(existing, target)
      if (itemScore > existingScore) {
        seenOriginMap.set(originKey, item)
      }
      continue
    }

    seenOriginMap.set(originKey, item)
  }

  const list = Array.from(seenOriginMap.values())
  return list.sort((a, b) => {
    if (a.bookUrl === curBookUrl) return -1
    if (b.bookUrl === curBookUrl) return 1
    const aScore = rateSourceQuality(a, target)
    const bScore = rateSourceQuality(b, target)
    if (bScore !== aScore) return bScore - aScore
    return 0
  })
}

const openChangeSourceDrawer = async () => {
  changeSourceDrawerVisible.value = true
  const name = bookName.value.trim()
  const author = bookAuthor.value.trim()

  // 1. 优先从本地 IndexedDB 缓存读取候选源（0ms 即时秒开）
  if (name && name !== '阅读') {
    const cached = await getCachedSources(name, author)
    if (cached && cached.length > 0) {
      sourceResults.value = sortAndDeduplicateSources(cached, bookUrl.value, name)
      loadedFromCache.value = true
      changeSourceDone.value = true
      return
    }
  }

  // 2. 无本地缓存时，发起网络搜索
  loadedFromCache.value = false
  doSearchSources()
}

const doSearchSources = () => {
  const name = bookName.value.trim()
  const author = bookAuthor.value.trim()
  if (!name || name === '阅读') return
  csController?.abort()
  loadedFromCache.value = false
  sourceResults.value = []
  changeSourceSearching.value = true
  changeSourceDone.value = false

  const rawList: SearchBookItem[] = []

  csController = searchStream(name, {
    onBooks: (originName, books) => {
      for (const b of books) {
        b.originName = originName
        if (b.name.trim() === name || b.name.includes(name)) {
          rawList.push(b)
        }
      }
      sourceResults.value = sortAndDeduplicateSources(rawList, bookUrl.value, name)
    },
    onDone: () => {
      changeSourceSearching.value = false
      changeSourceDone.value = true
      // 检索完成，保存至本地缓存
      if (sourceResults.value.length > 0) {
        saveCachedSources(name, author, sourceResults.value)
      }
    },
  })
}

const onSwitchSource = async (targetBook: SearchBookItem) => {
  switchingSourceUrl.value = targetBook.bookUrl
  try {
    const res = await changeSource({
      oldBookUrl: bookUrl.value,
      newBook: targetBook,
      currentChapterIndex: chapterIndex.value,
      currentChapterTitle: chapter.value?.title,
    })
    ElMessage.success(`已切换至【${targetBook.originName || targetBook.origin}】，定位到：${res.chapterTitle}`)
    changeSourceDrawerVisible.value = false

    await router.replace({
      query: {
        bookUrl: res.newBookUrl,
        name: res.name,
        author: res.author,
        type: targetBook.type !== undefined ? String(targetBook.type) : undefined,
      },
    })
    loadHighlights()
    await checkShelfStatus()
    await loadToc()
    await loadChapter(res.chapterIndex)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.errorMsg || '换源失败')
  } finally {
    switchingSourceUrl.value = ''
  }
}

// ===== 生命周期与进度恢复 =====
let resizeObserver: ResizeObserver | null = null

onMounted(async () => {
  detectMediaMode()
  initSilentAudio()
  document.addEventListener('touchstart', ensureSilentAudioPlaying, { passive: true })
  document.addEventListener('click', ensureSilentAudioPlaying, { passive: true })
  document.addEventListener('selectionchange', onSelectionChange)
  window.addEventListener('keydown', handleKeyAction, { capture: true })
  window.addEventListener('keyup', handleKeyAction, { capture: true })
  document.addEventListener('keydown', handleKeyAction, { capture: true })
  document.addEventListener('keyup', handleKeyAction, { capture: true })
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  window.addEventListener('resize', checkIsMobile)
  window.addEventListener('resize', updatePageCount)
  checkIsMobile()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (readMode.value === 'page') {
        updatePageCount()
      }
    })
    nextTick(() => {
      if (pageViewportRef.value) {
        resizeObserver?.observe(pageViewportRef.value)
      }
    })
  }
  if (!bookUrl.value) return
  await checkShelfStatus()
  loadHighlights()
  await loadToc()
  if (toc.value.length > 0) {
    let start = 0
    let startPos = 0
    try {
      const progress = await getProgress(bookUrl.value)
      if (progress && typeof progress.durChapterIndex === 'number') {
        start = progress.durChapterIndex
        startPos = progress.durChapterPos || 0
      }
    } catch { /* ignore */ }
    await loadChapter(start, { initialPos: startPos })
  }
  startReadingHeartbeat()
})

const nextPage = () => {
  if (currentPage.value < totalPages.value - 1) {
    currentPage.value++
    scheduleProgressSave()
  } else {
    nextChapter()
  }
}

const prevPage = () => {
  if (currentPage.value > 0) {
    currentPage.value--
    scheduleProgressSave()
  } else {
    prevChapter(true)
  }
}

const nextChapter = () => {
  if (chapterIndex.value < toc.value.length - 1) {
    loadChapter(chapterIndex.value + 1)
  } else {
    ElMessage.info('已经是最新一章')
  }
}

const prevChapter = (toLastPage = false) => {
  if (chapterIndex.value > 0) {
    loadChapter(chapterIndex.value - 1, { toLastPage })
  } else {
    ElMessage.info('已经是第一章')
  }
}

const jumpTo = (index: number) => {
  catalogVisible.value = false
  notesVisible.value = false
  loadChapter(index)
}

// ===== 工具栏与子菜单显隐 =====
const chromeVisible = ref(false)
const settingsVisible = ref(false)
const catalogVisible = ref(false)
const chapterFilter = ref('')
const contentRef = ref<HTMLElement>()
const scrollContainerRef = ref<HTMLElement>()

// 移动端/指针触控平滑拖动翻页
let touchStartX = 0
let touchStartY = 0
let touchStartTime = 0
let isTouchMoving = false

const onTouchStart = (e: TouchEvent) => {
  if (e.touches.length !== 1 || readMode.value !== 'page') return
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchStartTime = Date.now()
  dragOffset.value = 0
  isDragging.value = false
  isTouchMoving = false
  hasDragged = false
}

const onTouchMove = (e: TouchEvent) => {
  if (e.touches.length !== 1 || readMode.value !== 'page') return
  const curX = e.touches[0].clientX
  const curY = e.touches[0].clientY
  let diffX = curX - touchStartX
  const diffY = curY - touchStartY

  // 若垂直位移明显大于水平位移，判断为上下滚动意图，不拦截
  if (!isTouchMoving && Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > 8) return

  if (Math.abs(diffX) > 6) {
    isTouchMoving = true
    isDragging.value = true
    hasDragged = true
    if (e.cancelable) e.preventDefault()

    // 首末页弹性阻尼手感
    if (currentPage.value <= 0 && diffX > 0) {
      diffX = diffX * 0.3
    } else if (currentPage.value >= totalPages.value - 1 && diffX < 0) {
      diffX = diffX * 0.3
    }
    dragOffset.value = diffX
  }
}

const onTouchEnd = (e: TouchEvent) => {
  if (readMode.value !== 'page') return
  if (!isDragging.value) {
    dragOffset.value = 0
    return
  }
  isDragging.value = false
  const diffX = dragOffset.value
  const timeElapsed = Math.max(1, Date.now() - touchStartTime)
  const velocity = Math.abs(diffX) / timeElapsed // px/ms
  const w = viewportWidth.value || window.innerWidth || 800
  const threshold = Math.min(70, w * 0.15)
  dragOffset.value = 0

  if (diffX < -threshold || (diffX < -20 && velocity > 0.35)) {
    nextPage()
  } else if (diffX > threshold || (diffX > 20 && velocity > 0.35)) {
    prevPage()
  }

  setTimeout(() => {
    hasDragged = false
  }, 300)
}

const onTouchCancel = () => {
  isDragging.value = false
  dragOffset.value = 0
  setTimeout(() => {
    hasDragged = false
  }, 300)
}

// 桌面端鼠标按住拖拽翻页支持
let mouseStartX = 0
let mouseStartTime = 0

const onMouseDown = (e: MouseEvent) => {
  if (readMode.value !== 'page' || e.button !== 0) return
  const target = e.target as HTMLElement
  if (target.closest('button, .el-button, a, input, select, textarea, .sel-toolbar, .tts-bar, .el-dropdown-menu')) return

  mouseStartX = e.clientX
  mouseStartTime = Date.now()
  dragOffset.value = 0
  isDragging.value = false
  hasDragged = false

  const onMouseMove = (ev: MouseEvent) => {
    let diffX = ev.clientX - mouseStartX
    if (Math.abs(diffX) > 6) {
      isDragging.value = true
      hasDragged = true
      if (currentPage.value <= 0 && diffX > 0) {
        diffX = diffX * 0.3
      } else if (currentPage.value >= totalPages.value - 1 && diffX < 0) {
        diffX = diffX * 0.3
      }
      dragOffset.value = diffX
    }
  }

  const onMouseUp = () => {
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
    if (!isDragging.value) {
      dragOffset.value = 0
      return
    }
    isDragging.value = false
    const diffX = dragOffset.value
    const timeElapsed = Math.max(1, Date.now() - mouseStartTime)
    const velocity = Math.abs(diffX) / timeElapsed
    const w = viewportWidth.value || window.innerWidth || 800
    const threshold = Math.min(70, w * 0.15)
    dragOffset.value = 0

    if (diffX < -threshold || (diffX < -20 && velocity > 0.35)) {
      nextPage()
    } else if (diffX > threshold || (diffX > 20 && velocity > 0.35)) {
      prevPage()
    }

    setTimeout(() => {
      hasDragged = false
    }, 300)
  }

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

// 鼠标滚轮在分页模式下的翻页处理（带防抖与章节边界保护）
let pageWheelTimer: ReturnType<typeof setTimeout> | null = null
let pageBoundaryDelta = 0
let pageBoundaryTimer: ReturnType<typeof setTimeout> | null = null

const onPageWheel = (e: WheelEvent) => {
  if (mediaMode.value !== 'text' || readMode.value !== 'page') return
  e.preventDefault()
  if (Math.abs(e.deltaY) < 15 && Math.abs(e.deltaX) < 15) return

  // 处于本章末页或首页时，增加滚动阈值防误触跳章
  if (e.deltaY > 0 || e.deltaX > 0) {
    if (currentPage.value >= totalPages.value - 1) {
      pageBoundaryDelta += Math.abs(e.deltaY || e.deltaX)
      if (pageBoundaryTimer) clearTimeout(pageBoundaryTimer)
      pageBoundaryTimer = setTimeout(() => { pageBoundaryDelta = 0 }, 500)
      if (pageBoundaryDelta >= 80) {
        pageBoundaryDelta = 0
        nextChapter()
      }
      return
    }
  } else if (e.deltaY < 0 || e.deltaX < 0) {
    if (currentPage.value <= 0) {
      pageBoundaryDelta += Math.abs(e.deltaY || e.deltaX)
      if (pageBoundaryTimer) clearTimeout(pageBoundaryTimer)
      pageBoundaryTimer = setTimeout(() => { pageBoundaryDelta = 0 }, 500)
      if (pageBoundaryDelta >= 80) {
        pageBoundaryDelta = 0
        prevChapter(true)
      }
      return
    }
  }

  if (pageWheelTimer) return
  pageWheelTimer = setTimeout(() => {
    pageWheelTimer = null
  }, 220)

  if (e.deltaY > 0 || e.deltaX > 0) {
    nextPage()
  } else if (e.deltaY < 0 || e.deltaX < 0) {
    prevPage()
  }
}

// 鼠标滚轮在连续滚动模式下的平滑滚动与章节衔接
let scrollBoundaryDelta = 0
let scrollBoundaryTimer: ReturnType<typeof setTimeout> | null = null

const onScrollWheel = (e: WheelEvent) => {
  if (mediaMode.value !== 'text' || readMode.value !== 'scroll') return
  const sc = scrollContainerRef.value
  if (!sc) return

  const isAtBottom = sc.scrollTop + sc.clientHeight >= sc.scrollHeight - 25
  const isAtTop = sc.scrollTop <= 15

  if (isAtBottom && e.deltaY > 0) {
    scrollBoundaryDelta += e.deltaY
    if (scrollBoundaryTimer) clearTimeout(scrollBoundaryTimer)
    scrollBoundaryTimer = setTimeout(() => {
      scrollBoundaryDelta = 0
    }, 400)
    if (scrollBoundaryDelta >= 80) {
      scrollBoundaryDelta = 0
      nextChapter()
    }
  } else if (isAtTop && e.deltaY < 0) {
    scrollBoundaryDelta += e.deltaY
    if (scrollBoundaryTimer) clearTimeout(scrollBoundaryTimer)
    scrollBoundaryTimer = setTimeout(() => {
      scrollBoundaryDelta = 0
    }, 400)
    if (scrollBoundaryDelta <= -80) {
      scrollBoundaryDelta = 0
      prevChapter(true)
    }
  } else {
    scrollBoundaryDelta = 0
  }
}

const onBodyClick = (e: MouseEvent) => {
  if (hasDragged) {
    hasDragged = false
    return
  }
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !selText_get(sel)) {
    // 点击非划线区
  } else {
    return
  }
  const target = e.target as HTMLElement
  if (target.closest('.el-drawer, .reader-header, .reader-footer, .sel-toolbar, .tts-bar, .el-dropdown-menu, .el-dialog, .el-message-box, .audio-container, .video-container, .comic-toolbar')) return

  const width = window.innerWidth
  const height = window.innerHeight
  const clickX = e.clientX
  const clickY = e.clientY

  // 屏幕正中心区域（水平 30%~70%，垂直 20%~80%）：呼出/收起子菜单
  const isCenter = clickX >= width * 0.3 && clickX <= width * 0.7 && clickY >= height * 0.2 && clickY <= height * 0.8
  if (isCenter) {
    chromeVisible.value = !chromeVisible.value
    return
  }

  // 若菜单当前处于显示状态，点击屏幕其他任意区域优先收起菜单
  if (chromeVisible.value) {
    chromeVisible.value = false
    return
  }

  // 在纯文本模式下响应左右两侧点击翻页
  if (mediaMode.value === 'text') {
    if (clickX < width * 0.3) {
      if (readMode.value === 'page') {
        prevPage()
      } else {
        scrollContainerRef.value?.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' })
      }
      return
    }

    if (clickX > width * 0.7) {
      if (readMode.value === 'page') {
        nextPage()
      } else {
        scrollContainerRef.value?.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' })
      }
      return
    }
  }
}

// ===== 手机端音量键翻页（微静音媒体流监听 + 物理按键捕获） =====
const triggerVolumeFlip = (direction: 'next' | 'prev') => {
  if (direction === 'next') {
    if (mediaMode.value === 'text') {
      if (readMode.value === 'page') nextPage()
      else scrollContainerRef.value?.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' })
    } else if (mediaMode.value === 'comic') {
      comicContainerRef.value?.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' })
    }
  } else {
    if (mediaMode.value === 'text') {
      if (readMode.value === 'page') prevPage()
      else scrollContainerRef.value?.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' })
    } else if (mediaMode.value === 'comic') {
      comicContainerRef.value?.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' })
    }
  }
}

let silentAudio: HTMLAudioElement | null = null
let isResettingAudioVolume = false
let lastAudioVolumeActionTime = 0

// 0.1秒 8kHz 8-bit 单声道静音 WAV Base64 流
const SILENT_WAV_BASE64 =
  'data:audio/wav;base64,UklGRkQDAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YSADAACAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA=='

const onAudioVolumeChange = () => {
  if (isResettingAudioVolume || !volumeKeyPage.value || !silentAudio) return
  const now = Date.now()
  if (now - lastAudioVolumeActionTime < 180) return

  const curVol = silentAudio.volume
  // 基准音量 0.5，大于 0.505 判定为增加音量，小于 0.495 判定为减小音量
  if (curVol > 0.505) {
    lastAudioVolumeActionTime = now
    isResettingAudioVolume = true
    silentAudio.volume = 0.5
    setTimeout(() => { isResettingAudioVolume = false }, 80)
    triggerVolumeFlip('prev')
  } else if (curVol < 0.495) {
    lastAudioVolumeActionTime = now
    isResettingAudioVolume = true
    silentAudio.volume = 0.5
    setTimeout(() => { isResettingAudioVolume = false }, 80)
    triggerVolumeFlip('next')
  }
}

const initSilentAudio = () => {
  if (typeof window === 'undefined') return
  if (!silentAudio) {
    silentAudio = new Audio(SILENT_WAV_BASE64)
    silentAudio.loop = true
    silentAudio.volume = 0.5
    silentAudio.addEventListener('volumechange', onAudioVolumeChange)
  }
  if (volumeKeyPage.value && silentAudio.paused) {
    silentAudio.play().catch(() => {
      // 移动端需用户任意点击一次屏幕后授权播放
    })
  }
}

const ensureSilentAudioPlaying = () => {
  if (volumeKeyPage.value && silentAudio && silentAudio.paused) {
    silentAudio.play().catch(() => {})
  }
}

const handleKeyAction = (e: KeyboardEvent) => {
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return

  // 1. 移动端/外接控制器物理音量键翻页（兼容不同浏览器按键定义与键码）
  const isVolumeDown =
    e.key === 'AudioVolumeDown' ||
    e.key === 'VolumeDown' ||
    e.code === 'AudioVolumeDown' ||
    e.keyCode === 25 ||
    e.keyCode === 174 ||
    (e as any).which === 25

  const isVolumeUp =
    e.key === 'AudioVolumeUp' ||
    e.key === 'VolumeUp' ||
    e.code === 'AudioVolumeUp' ||
    e.keyCode === 24 ||
    e.keyCode === 175 ||
    (e as any).which === 24

  if (volumeKeyPage.value && (isVolumeDown || isVolumeUp)) {
    e.preventDefault()
    e.stopPropagation()
    if (e.stopImmediatePropagation) e.stopImmediatePropagation()

    // 仅在 keydown 事件执行翻页动作，避免 keydown + keyup 重复触发两次翻页
    if (e.type === 'keydown') {
      if (isVolumeDown) {
        triggerVolumeFlip('next')
      } else if (isVolumeUp) {
        triggerVolumeFlip('prev')
      }
    }
    return
  }

  // 2. 键盘常规快捷键翻页（只在 keydown 响应）
  if (e.type === 'keydown') {
    if (e.key === 'ArrowRight' || e.key === 'PageDown' || e.key === 'ArrowDown') {
      if (mediaMode.value === 'text') {
        if (readMode.value === 'page') nextPage()
        else scrollContainerRef.value?.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' })
      }
    } else if (e.key === 'ArrowLeft' || e.key === 'PageUp' || e.key === 'ArrowUp') {
      if (mediaMode.value === 'text') {
        if (readMode.value === 'page') prevPage()
        else scrollContainerRef.value?.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' })
      }
    } else if (e.key === ' ' && !e.shiftKey) {
      if (mediaMode.value === 'audio') {
        e.preventDefault()
        toggleAudioPlay()
      } else if (mediaMode.value === 'text') {
        e.preventDefault()
        if (readMode.value === 'page') nextPage()
        else scrollContainerRef.value?.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' })
      }
    } else if (e.key === 'Escape') {
      chromeVisible.value = false
    }
  }
}

// ===== 进度保存与阅读时长心跳 =====
const lastScrollPos = ref(0)
let saveTimer: ReturnType<typeof setTimeout> | null = null
let readingTimeAccumulated = 0
let readingHeartbeatTimer: ReturnType<typeof setInterval> | null = null
let lastInteractionTimestamp = Date.now()

const onUserActivity = () => {
  lastInteractionTimestamp = Date.now()
}

function flushReadingTime() {
  if (readingTimeAccumulated <= 0 || !bookUrl.value) return
  const secondsToSend = readingTimeAccumulated
  readingTimeAccumulated = 0
  recordReadingTime({
    bookUrl: bookUrl.value,
    bookName: bookName.value,
    author: bookAuthor.value,
    durationSeconds: secondsToSend,
  }).catch(() => {
    readingTimeAccumulated += secondsToSend
  })
}

const onVisibilityChange = () => {
  if (document.visibilityState === 'hidden') {
    flushReadingTime()
  } else {
    lastInteractionTimestamp = Date.now()
  }
}

function startReadingHeartbeat() {
  if (readingHeartbeatTimer) clearInterval(readingHeartbeatTimer)
  window.addEventListener('mousemove', onUserActivity, { passive: true })
  window.addEventListener('touchstart', onUserActivity, { passive: true })
  window.addEventListener('keydown', onUserActivity, { passive: true })
  document.addEventListener('visibilitychange', onVisibilityChange)

  readingHeartbeatTimer = setInterval(() => {
    const isVisible = document.visibilityState === 'visible'
    const isRecentlyActive = Date.now() - lastInteractionTimestamp < 180000
    if (isVisible && isRecentlyActive && bookUrl.value) {
      readingTimeAccumulated += 1
      if (readingTimeAccumulated >= 30) {
        flushReadingTime()
      }
    }
  }, 1000)
}

function stopReadingHeartbeat() {
  if (readingHeartbeatTimer) {
    clearInterval(readingHeartbeatTimer)
    readingHeartbeatTimer = null
  }
  window.removeEventListener('mousemove', onUserActivity)
  window.removeEventListener('touchstart', onUserActivity)
  window.removeEventListener('keydown', onUserActivity)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  flushReadingTime()
}

function scheduleProgressSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(doSaveProgress, 3000)
}

const onScroll = () => {
  lastScrollPos.value = scrollContainerRef.value?.scrollTop ?? 0
  scheduleProgressSave()
}

const onComicScroll = () => {
  lastScrollPos.value = comicContainerRef.value?.scrollTop ?? 0
  scheduleProgressSave()
}

function doSaveProgress() {
  if (!chapter.value) return
  const pos = readMode.value === 'page' ? currentPage.value : Math.round(lastScrollPos.value)
  saveProgressWithBeacon({
    bookUrl: bookUrl.value,
    durChapterIndex: chapterIndex.value,
    durChapterPos: pos,
    durChapterTitle: chapter.value.title,
    durChapterTime: Date.now(),
  })
  flushReadingTime()
}

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (silentAudio) {
    silentAudio.removeEventListener('volumechange', onAudioVolumeChange)
    silentAudio.pause()
    silentAudio.src = ''
    silentAudio = null
  }
  document.removeEventListener('touchstart', ensureSilentAudioPlaying)
  document.removeEventListener('click', ensureSilentAudioPlaying)
  window.removeEventListener('keydown', handleKeyAction, { capture: true } as any)
  window.removeEventListener('keyup', handleKeyAction, { capture: true } as any)
  document.removeEventListener('keydown', handleKeyAction, { capture: true } as any)
  document.removeEventListener('keyup', handleKeyAction, { capture: true } as any)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  window.removeEventListener('resize', checkIsMobile)
  window.removeEventListener('resize', updatePageCount)
  document.removeEventListener('selectionchange', onSelectionChange)
  csController?.abort()
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  if (sleepCountdownTimer) clearTimeout(sleepCountdownTimer)
  stopReadingHeartbeat()
  doSaveProgress()
  if (saveTimer) clearTimeout(saveTimer)
})

watch(volumeKeyPage, (enabled) => {
  if (enabled) {
    initSilentAudio()
  } else if (silentAudio && !silentAudio.paused) {
    silentAudio.pause()
  }
})

watch(toc, t => {
  if (chapter.value) {
    const item = t.find(c => c.index === chapter.value!.index)
    if (item && !chapter.value.url) chapter.value = { ...chapter.value, url: item.url }
  }
})

watch(() => route.query.type, () => {
  detectMediaMode()
})
</script>

<style scoped>
.reader {
  height: 100vh;
  overflow: hidden;
  position: relative;
  transition: background 0.2s, color 0.2s;
  background: var(--reader-bg);
  color: var(--reader-fg);
}

/* 7 套主题定义与抽屉色系变量适配 */
.theme-white {
  --reader-bg: #ffffff;
  --reader-fg: #1f2329;
  --reader-chrome-bg: rgba(255, 255, 255, 0.94);
  --reader-chrome-fg: #1f2329;
  --reader-drawer-bg: #ffffff;
  --reader-drawer-fg: #1f2329;
  --reader-border: #e5e6eb;
  --reader-item-hover: #f2f3f5;
}

.theme-paper {
  --reader-bg: #f5ecd9;
  --reader-fg: #3e3a33;
  --reader-chrome-bg: rgba(245, 236, 217, 0.95);
  --reader-chrome-fg: #3e3a33;
  --reader-drawer-bg: #f5ecd9;
  --reader-drawer-fg: #3e3a33;
  --reader-border: #e2d3be;
  --reader-item-hover: #eadfca;
}

.theme-eye {
  --reader-bg: #cce8cf;
  --reader-fg: #1b3a1f;
  --reader-chrome-bg: rgba(204, 232, 207, 0.95);
  --reader-chrome-fg: #1b3a1f;
  --reader-drawer-bg: #cce8cf;
  --reader-drawer-fg: #1b3a1f;
  --reader-border: #b5d8b9;
  --reader-item-hover: #bde0c1;
}

.theme-gray {
  --reader-bg: #e5e7eb;
  --reader-fg: #2d3748;
  --reader-chrome-bg: rgba(229, 231, 235, 0.95);
  --reader-chrome-fg: #2d3748;
  --reader-drawer-bg: #e5e7eb;
  --reader-drawer-fg: #2d3748;
  --reader-border: #cbd5e1;
  --reader-item-hover: #dbe0e6;
}

.theme-warm {
  --reader-bg: #f8edd9;
  --reader-fg: #4a3828;
  --reader-chrome-bg: rgba(248, 237, 217, 0.95);
  --reader-chrome-fg: #4a3828;
  --reader-drawer-bg: #f8edd9;
  --reader-drawer-fg: #4a3828;
  --reader-border: #e5d4bc;
  --reader-item-hover: #ede1cb;
}

.theme-navy {
  --reader-bg: #182232;
  --reader-fg: #adc0d6;
  --reader-chrome-bg: rgba(24, 34, 50, 0.95);
  --reader-chrome-fg: #adc0d6;
  --reader-drawer-bg: #182232;
  --reader-drawer-fg: #adc0d6;
  --reader-border: #26354a;
  --reader-item-hover: #223046;
}

.theme-dark-green {
  --reader-bg: #132219;
  --reader-fg: #9ec4a6;
  --reader-chrome-bg: rgba(19, 34, 25, 0.95);
  --reader-chrome-fg: #9ec4a6;
  --reader-drawer-bg: #132219;
  --reader-drawer-fg: #9ec4a6;
  --reader-border: #203829;
  --reader-item-hover: #1c3024;
}

.theme-dark-purple {
  --reader-bg: #1c1626;
  --reader-fg: #b8a9cf;
  --reader-chrome-bg: rgba(28, 22, 38, 0.95);
  --reader-chrome-fg: #b8a9cf;
  --reader-drawer-bg: #1c1626;
  --reader-drawer-fg: #b8a9cf;
  --reader-border: #2e233f;
  --reader-item-hover: #281e37;
}

.theme-dark-coffee {
  --reader-bg: #1f1b18;
  --reader-fg: #c2b6a8;
  --reader-chrome-bg: rgba(31, 27, 24, 0.95);
  --reader-chrome-fg: #c2b6a8;
  --reader-drawer-bg: #1f1b18;
  --reader-drawer-fg: #c2b6a8;
  --reader-border: #332b25;
  --reader-item-hover: #2d2621;
}

.theme-night {
  --reader-bg: #121212;
  --reader-fg: #b3b3b3;
  --reader-chrome-bg: rgba(18, 18, 18, 0.95);
  --reader-chrome-fg: #b3b3b3;
  --reader-drawer-bg: #1c1c1c;
  --reader-drawer-fg: #b3b3b3;
  --reader-border: #2e2e2e;
  --reader-item-hover: #292929;
}

/* 字体族设置 */
.font-system .paragraphs p {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.font-serif .paragraphs p {
  font-family: "Songti SC", "Source Han Serif SC", "Noto Serif SC", SimSun, serif;
}

.font-kaiti .paragraphs p {
  font-family: KaiTi, "STKaiti", "BiauKai", serif;
}

.reader-header {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  height: 48px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  background: var(--reader-chrome-bg);
  color: var(--reader-chrome-fg);
  transition: transform 0.25s ease, opacity 0.25s;
  z-index: 50;
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--reader-border);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transform: translateY(-100%);
  opacity: 0;
}

.reader-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 18px 14px;
  background: var(--reader-chrome-bg);
  color: var(--reader-chrome-fg);
  transition: transform 0.25s ease, opacity 0.25s;
  z-index: 50;
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--reader-border);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(100%);
  opacity: 0;
}

.reader-header.show,
.reader-footer.show {
  transform: translateY(0);
  opacity: 1;
}

.footer-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
}

.chapter-slider {
  flex: 1;
}

.footer-actions-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
}

.book-title {
  flex: 1;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 分页浏览模式专用样式 */
.page-container {
  width: 100vw;
  height: 100vh;
  box-sizing: border-box;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  user-select: text;
}

.page-meta-header {
  height: 36px;
  line-height: 36px;
  padding: 0 28px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.55;
  border-bottom: 1px dashed var(--reader-border);
}

.meta-book {
  font-weight: 500;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-chapter {
  max-width: 50%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-viewport {
  flex: 1 1 0;
  min-height: 0;
  height: calc(100vh - 68px);
  max-height: calc(100vh - 68px);
  overflow: hidden;
  position: relative;
  margin: 0 auto;
  width: 100%;
  padding: 16px 0;
  box-sizing: border-box;
}

.page-flow {
  height: 100%;
  max-height: 100%;
  column-fill: auto;
  box-sizing: border-box;
  overflow: visible;
  transition: transform 0.28s cubic-bezier(0.25, 1, 0.5, 1);
}

.page-meta-footer {
  height: 32px;
  line-height: 32px;
  padding: 0 28px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.55;
  border-top: 1px dashed var(--reader-border);
}

.scroll-container {
  width: 100vw;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  scroll-behavior: smooth;
  user-select: text;
}

.scroll-container::-webkit-scrollbar {
  width: 8px;
}

.scroll-container::-webkit-scrollbar-thumb {
  background: var(--reader-border);
  border-radius: 4px;
}

.scroll-container::-webkit-scrollbar-track {
  background: transparent;
}

.content {
  margin: 0 auto;
  padding: 56px 20px 80px;
  min-height: 100vh;
  box-sizing: border-box;
}

.chapter-title {
  font-size: 1.3em;
  margin: 1.2em 0 1.4em;
  text-align: center;
  font-weight: 600;
}

.paragraphs p {
  margin: 0 0 0.8em;
  word-break: break-word;
  user-select: text;
  -webkit-user-select: text;
  cursor: text;
}

.paragraphs ::selection {
  background: rgba(64, 158, 255, 0.35);
}

/* 划线标注样式 */
:deep(.hl-mark) {
  padding: 1px 3px;
  border-radius: 3px;
  cursor: pointer;
}

:deep(.hl-mark.hl-yellow) {
  background: rgba(255, 213, 79, 0.45);
  border-bottom: 2px solid #ffca28;
}

:deep(.hl-mark.hl-green) {
  background: rgba(129, 199, 132, 0.45);
  border-bottom: 2px solid #66bb6a;
}

:deep(.hl-mark.hl-pink) {
  background: rgba(244, 143, 177, 0.45);
  border-bottom: 2px solid #f06292;
}

.nav-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2.6em;
  gap: 12px;
}

.muted {
  opacity: 0.65;
  font-size: 13px;
}

/* 悬浮工具条 */
.sel-toolbar {
  position: fixed;
  z-index: 60;
  border-radius: 8px;
  padding: 6px;
  background: var(--reader-chrome-bg);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: center;
  gap: 6px;
  backdrop-filter: blur(8px);
  border: 1px solid var(--reader-border);
}

.tts-bar {
  position: fixed;
  top: 54px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 40;
  background: var(--reader-chrome-bg);
  color: var(--reader-chrome-fg);
  padding: 6px 14px;
  border-radius: 20px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  border: 1px solid var(--reader-border);
}

/* 漫画瀑布流样式 */
.comic-container {
  width: 100vw;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  position: relative;
  background: #0d0d0d;
  color: #efefef;
}

.comic-toolbar {
  position: fixed;
  right: 20px;
  top: 60px;
  z-index: 45;
  background: rgba(30, 30, 30, 0.88);
  backdrop-filter: blur(8px);
  border-radius: 24px;
  padding: 6px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.comic-page-info {
  font-size: 12px;
  color: #bbb;
}

.scale-text {
  font-size: 12px;
  min-width: 38px;
  text-align: center;
}

.comic-content {
  margin: 0 auto;
  padding: 60px 0 80px;
  transition: max-width 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.comic-fail-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  background: rgba(230, 162, 60, 0.12);
  border: 1px solid rgba(230, 162, 60, 0.35);
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  color: #e6a23c;
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.comic-single-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.35);
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  color: #409eff;
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.comic-single-notice .notice-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comic-single-notice .info-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.comic-fail-notice .notice-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comic-fail-notice .warn-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.comic-fail-notice .notice-actions {
  display: flex;
  gap: 8px;
}

.comic-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.comic-item {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 6px;
  min-height: 280px;
  background-color: rgba(255, 255, 255, 0.04);
  border-radius: 4px;
}

.comic-item-num {
  position: absolute;
  top: 8px;
  right: 12px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
  pointer-events: none;
  z-index: 2;
}

.comic-img {
  max-width: 100%;
  height: auto;
  min-height: 200px;
  display: block;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.comic-error-card {
  width: 92%;
  max-width: 520px;
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  background: rgba(30, 30, 30, 0.7);
  border: 1px dashed rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  text-align: center;
  margin: 12px 0;
  box-sizing: border-box;
}

.error-card-icon {
  font-size: 40px;
  color: #f56c6c;
  margin-bottom: 12px;
}

.error-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #f0f0f0;
  margin-bottom: 6px;
}

.error-card-desc {
  font-size: 13px;
  color: #a8abb2;
  margin-bottom: 16px;
}

.error-card-actions {
  display: flex;
  gap: 12px;
}

.comic-empty {
  padding: 80px 0;
}

.comic-nav {
  width: 90%;
  max-width: 600px;
  margin: 30px auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 有声播放器样式 */
.audio-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 20px 40px;
  box-sizing: border-box;
}

.audio-player-card {
  width: 100%;
  max-width: 440px;
  padding: 28px 24px;
  background: var(--reader-chrome-bg);
  border: 1px solid var(--reader-border);
  border-radius: 20px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.vinyl-box {
  margin: 10px 0 20px;
}

.vinyl-disc {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: repeating-radial-gradient(#111, #111 2px, #262626 3px, #111 4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.vinyl-disc.spinning {
  animation: disc-rotate 12s linear infinite;
}

@keyframes disc-rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.vinyl-cover {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  object-fit: cover;
}

.vinyl-center-hole {
  position: absolute;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f5f5f5;
  border: 3px solid #111;
}

.audio-info {
  text-align: center;
  margin-bottom: 16px;
  width: 100%;
}

.audio-title {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audio-sub {
  font-size: 13px;
  opacity: 0.7;
  margin-top: 4px;
}

.audio-scrub-wrap {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.audio-time {
  font-size: 12px;
  font-family: monospace;
  opacity: 0.75;
  min-width: 40px;
}

.audio-slider {
  flex: 1;
}

.audio-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 16px;
}

.audio-play-btn {
  width: 54px;
  height: 54px;
  font-size: 22px;
}

.audio-extra-controls {
  display: flex;
  align-items: center;
  justify-content: space-around;
  width: 100%;
  border-top: 1px dashed var(--reader-border);
  padding-top: 12px;
}

.audio-tts-fallback {
  margin-top: 14px;
  width: 100%;
}

/* 视频播放器样式 */
.video-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #000;
  padding: 50px 16px 20px;
  box-sizing: border-box;
}

.video-wrapper {
  width: 100%;
  max-width: 960px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.video-player-box {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #111;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-el,
.video-iframe {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(26, 26, 26, 0.9);
  padding: 10px 16px;
  border-radius: 8px;
  color: #eee;
}

.video-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.video-title {
  font-size: 15px;
  font-weight: 600;
}

.video-sub {
  font-size: 12px;
  color: #999;
}

.video-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 抽屉主题全面自适应 */
:deep(.reader-theme-drawer) {
  background: var(--reader-drawer-bg) !important;
  color: var(--reader-drawer-fg) !important;
}

:deep(.reader-theme-drawer .el-drawer__header) {
  color: var(--reader-drawer-fg) !important;
  border-bottom: 1px solid var(--reader-border);
  margin-bottom: 12px;
  padding-bottom: 12px;
}

:deep(.reader-theme-drawer .el-drawer__title) {
  color: var(--reader-drawer-fg) !important;
}

:deep(.reader-theme-drawer .el-drawer__body) {
  color: var(--reader-drawer-fg) !important;
  background: var(--reader-drawer-bg) !important;
}

:deep(.reader-theme-drawer .el-input__wrapper) {
  background-color: var(--reader-drawer-bg) !important;
  box-shadow: 0 0 0 1px var(--reader-border) inset !important;
}

:deep(.reader-theme-drawer .el-input__inner) {
  color: var(--reader-drawer-fg) !important;
}

/* 目录列表 */
.catalog-list {
  height: calc(100% - 40px);
  overflow-y: auto;
}

.catalog-item {
  padding: 8px 10px;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
  color: var(--reader-drawer-fg);
}

.catalog-item:hover {
  background: var(--reader-item-hover);
}

.catalog-item.current {
  color: var(--el-color-primary);
  font-weight: 600;
}

.catalog-title {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 设置抽屉内部 */
.setting-group {
  margin-bottom: 18px;
}

.setting-label {
  font-size: 13px;
  margin-bottom: 6px;
  opacity: 0.8;
}

.theme-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.theme-dot {
  width: 54px;
  height: 38px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s;
}

.theme-dot:hover {
  transform: translateY(-2px);
}

.theme-dot-text {
  font-size: 11px;
}

.theme-dot.white { background: #fff; color: #1f2329; }
.theme-dot.paper { background: #f5ecd9; color: #3e3a33; }
.theme-dot.eye { background: #cce8cf; color: #1b3a1f; }
.theme-dot.gray { background: #e5e7eb; color: #2d3748; }
.theme-dot.warm { background: #f8edd9; color: #4a3828; }
.theme-dot.navy { background: #182232; color: #adc0d6; }
.theme-dot.night { background: #121212; color: #b3b3b3; }
.theme-dot.dark-green { background: #132219; color: #9ec4a6; }
.theme-dot.dark-purple { background: #1c1626; color: #b8a9cf; }
.theme-dot.dark-coffee { background: #1f1b18; color: #c2b6a8; }

.theme-dot.active {
  border-color: var(--el-color-primary);
}

/* 笔记抽屉 */
.notes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-card {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--reader-item-hover);
  border-left: 4px solid #ffca28;
}

.note-card.border-yellow { border-left-color: #ffca28; }
.note-card.border-green { border-left-color: #66bb6a; }
.note-card.border-pink { border-left-color: #f06292; }

.note-quote {
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 6px;
  word-break: break-word;
}

.note-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  opacity: 0.7;
}

.note-ch {
  cursor: pointer;
  color: var(--el-color-primary);
}

/* 换源抽屉 */
.change-source-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cs-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.cs-tip {
  font-size: 12px;
}

.cs-tip.cache-active {
  color: #67c23a;
  font-weight: 500;
}

.cs-tip.searching-active {
  color: var(--el-color-primary, #409eff);
}

.latest-ch-text {
  color: var(--reader-text-color, #333);
  opacity: 0.9;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-item {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--reader-border);
  background: var(--reader-item-hover);
}

.source-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.source-origin {
  font-weight: 600;
  font-size: 14px;
}

.source-chapter {
  font-size: 12px;
  margin-top: 6px;
  word-break: break-all;
}

.reader-state-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  text-align: center;
  color: var(--reader-text-color, #333);
  padding: 32px 16px;
}

.reader-state-wrap .state-title {
  margin-top: 20px;
  font-size: 18px;
  font-weight: 600;
}

.reader-state-wrap .state-desc {
  margin-top: 8px;
  font-size: 14px;
  opacity: 0.7;
}

.setting-row-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}

.setting-label-switch {
  font-size: 13px;
  color: var(--reader-drawer-fg);
  opacity: 0.85;
}

/* 全局暗色模式在阅读页面及相关组件的深度适配 */
html.dark .reader {
  --reader-bg: #121212;
  --reader-fg: #b4b4b4;
  --reader-chrome-bg: rgba(18, 18, 18, 0.96);
  --reader-chrome-fg: #e2e2e2;
  --reader-drawer-bg: #1a1a1a;
  --reader-drawer-fg: #dcdcdc;
  --reader-border: #2c2c2c;
  --reader-item-hover: #262626;
  background: var(--reader-bg) !important;
  color: var(--reader-fg) !important;
}

html.dark .reader.theme-navy {
  --reader-bg: #182232;
  --reader-fg: #adc0d6;
  --reader-chrome-bg: rgba(24, 34, 50, 0.95);
  --reader-chrome-fg: #adc0d6;
  --reader-drawer-bg: #182232;
  --reader-drawer-fg: #adc0d6;
  --reader-border: #26354a;
  --reader-item-hover: #223046;
}

html.dark .reader.theme-dark-green {
  --reader-bg: #132219;
  --reader-fg: #9ec4a6;
  --reader-chrome-bg: rgba(19, 34, 25, 0.95);
  --reader-chrome-fg: #9ec4a6;
  --reader-drawer-bg: #132219;
  --reader-drawer-fg: #9ec4a6;
  --reader-border: #203829;
  --reader-item-hover: #1c3024;
}

html.dark .reader.theme-dark-purple {
  --reader-bg: #1c1626;
  --reader-fg: #b8a9cf;
  --reader-chrome-bg: rgba(28, 22, 38, 0.95);
  --reader-chrome-fg: #b8a9cf;
  --reader-drawer-bg: #1c1626;
  --reader-drawer-fg: #b8a9cf;
  --reader-border: #2e233f;
  --reader-item-hover: #281e37;
}

html.dark .reader.theme-dark-coffee {
  --reader-bg: #1f1b18;
  --reader-fg: #c2b6a8;
  --reader-chrome-bg: rgba(31, 27, 24, 0.95);
  --reader-chrome-fg: #c2b6a8;
  --reader-drawer-bg: #1f1b18;
  --reader-drawer-fg: #c2b6a8;
  --reader-border: #332b25;
  --reader-item-hover: #2d2621;
}

html.dark .reader.theme-night {
  --reader-bg: #121212;
  --reader-fg: #b3b3b3;
  --reader-chrome-bg: rgba(18, 18, 18, 0.95);
  --reader-chrome-fg: #b3b3b3;
  --reader-drawer-bg: #1c1c1c;
  --reader-drawer-fg: #b3b3b3;
  --reader-border: #2e2e2e;
  --reader-item-hover: #292929;
}

html.dark .reader-header,
html.dark .reader-footer {
  background: rgba(18, 18, 18, 0.96) !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5) !important;
  color: #e2e2e2 !important;
}

html.dark .book-title,
html.dark .meta-book,
html.dark .meta-chapter {
  color: #e2e2e2 !important;
}

html.dark .page-meta-header,
html.dark .page-meta-footer {
  color: rgba(255, 255, 255, 0.45) !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
}

html.dark :deep(.reader-theme-drawer) {
  background: #1a1a1a !important;
  color: #dcdcdc !important;
}

html.dark :deep(.reader-theme-drawer .el-drawer__header) {
  background: #1a1a1a !important;
  border-bottom-color: #2c2c2c !important;
  color: #e2e2e2 !important;
}

html.dark :deep(.reader-theme-drawer .el-drawer__title) {
  color: #e2e2e2 !important;
}

html.dark :deep(.reader-theme-drawer .el-drawer__body) {
  background: #1a1a1a !important;
  color: #dcdcdc !important;
}

html.dark :deep(.reader-theme-drawer .el-input__wrapper) {
  background-color: #262626 !important;
  box-shadow: 0 0 0 1px #3a3a3a inset !important;
}

html.dark :deep(.reader-theme-drawer .el-input__inner) {
  color: #e2e2e2 !important;
}

html.dark .catalog-item {
  color: #c4c4c4;
}

html.dark .catalog-item:hover {
  background: #282828 !important;
}

html.dark .source-item {
  background: #222222 !important;
  border-color: #333333 !important;
  color: #dcdcdc !important;
}

html.dark .note-card {
  background: #222222 !important;
  color: #dcdcdc !important;
}

html.dark .comic-container {
  background: #121212 !important;
}

html.dark .comic-toolbar {
  background: rgba(24, 24, 24, 0.92) !important;
  border-color: #333333 !important;
  color: #e2e2e2 !important;
}

html.dark .audio-player-card {
  background: #1c1c1c !important;
  border-color: #2e2e2e !important;
  color: #e2e2e2 !important;
}

html.dark .tts-bar {
  background: rgba(22, 22, 22, 0.95) !important;
  border-color: #333333 !important;
  color: #e2e2e2 !important;
}

html.dark .reader-state-wrap {
  background: #121212 !important;
  color: #b4b4b4 !important;
}

html.dark .latest-ch-text {
  color: #b4b4b4 !important;
}

.sub-setting-tip {
  font-size: 11px;
  opacity: 0.65;
  margin-left: 6px;
  font-weight: normal;
}

.volume-key-notice {
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--reader-item-hover);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--reader-drawer-fg);
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

@media (max-width: 768px) {
  .reader {
    height: 100vh !important;
    height: 100dvh !important;
  }
  .scroll-container {
    width: 100vw !important;
    max-width: 100vw !important;
    height: 100vh !important;
    height: 100dvh !important;
  }
  .content {
    width: 100% !important;
    max-width: 100% !important;
    padding: 36px 14px 72px !important;
    min-height: 100vh !important;
    min-height: 100dvh !important;
  }
  .page-container {
    width: 100vw !important;
    height: 100vh !important;
    height: 100dvh !important;
  }
  .page-viewport {
    width: 100% !important;
    max-width: 100% !important;
    padding: 8px 0 !important;
  }
  .page-flow {
    padding: 0 4px;
  }
}

@media (max-width: 600px) {
  :deep(.reader-theme-drawer.rtl) {
    width: 88vw !important;
  }
  :deep(.reader-theme-drawer.btt) {
    max-height: 85vh !important;
  }
  .reader-header {
    padding: 0 8px;
    height: 44px;
  }
  .book-title {
    font-size: 13px;
  }
  .header-actions {
    gap: 2px;
  }
  .header-actions .el-button {
    padding: 4px 6px;
    font-size: 12px;
  }
  .footer-slider-row {
    gap: 6px;
  }
  .footer-actions-row {
    flex-wrap: wrap;
    gap: 4px;
  }
  .page-meta-header,
  .page-meta-footer {
    padding: 0 14px;
  }
}

</style>
