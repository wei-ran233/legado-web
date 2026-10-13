import { defineStore } from 'pinia'
import { ref } from 'vue'

export type AppThemeType = 'aurora' | 'sakura' | 'classic' | 'azure' | 'obsidian'
export type AppLayoutType = 'sidebar' | 'topbar' | 'slim'
export type AppDensityType = 'normal' | 'wide'
export type AppRadiusType = 'standard' | 'round' | 'sharp'

export const useThemeStore = defineStore('theme', () => {
  const savedMode = typeof localStorage !== 'undefined' ? localStorage.getItem('legado_theme_mode') : null
  const prefersDark = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
  const isDark = ref(savedMode ? savedMode === 'dark' : prefersDark)

  const savedTheme = (typeof localStorage !== 'undefined' ? localStorage.getItem('legado_app_theme') : null) as AppThemeType | null
  const appTheme = ref<AppThemeType>(savedTheme || 'classic')

  const savedLayout = (typeof localStorage !== 'undefined' ? localStorage.getItem('legado_app_layout') : null) as AppLayoutType | null
  const appLayout = ref<AppLayoutType>(savedLayout || 'sidebar')

  const savedDensity = (typeof localStorage !== 'undefined' ? localStorage.getItem('legado_app_density') : null) as AppDensityType | null
  const appDensity = ref<AppDensityType>(savedDensity || 'normal')

  const savedRadius = (typeof localStorage !== 'undefined' ? localStorage.getItem('legado_app_radius') : null) as AppRadiusType | null
  const appRadius = ref<AppRadiusType>(savedRadius || 'standard')

  // 全局交互点击动效开关
  const savedClickEffects = typeof localStorage !== 'undefined' ? localStorage.getItem('legado_click_effects') : null
  const clickEffectsEnabled = ref(savedClickEffects !== 'false')

  const applyTheme = () => {
    if (typeof document === 'undefined') return
    const root = document.documentElement

    // 1. 暗色/日间模式切换
    if (isDark.value) {
      root.classList.add('dark')
      localStorage.setItem('legado_theme_mode', 'dark')
    } else {
      root.classList.remove('dark')
      localStorage.setItem('legado_theme_mode', 'light')
    }

    // 2. 整站主题样式
    root.setAttribute('data-theme', appTheme.value)
    localStorage.setItem('legado_app_theme', appTheme.value)

    // 3. 布局形态
    root.setAttribute('data-layout', appLayout.value)
    localStorage.setItem('legado_app_layout', appLayout.value)

    // 4. 内容排版宽度
    root.setAttribute('data-density', appDensity.value)
    localStorage.setItem('legado_app_density', appDensity.value)

    // 5. 圆角质感
    root.setAttribute('data-radius', appRadius.value)
    localStorage.setItem('legado_app_radius', appRadius.value)
  }

  const toggleDark = () => {
    isDark.value = !isDark.value
    applyTheme()
  }

  const setAppTheme = (t: AppThemeType) => {
    appTheme.value = t
    applyTheme()
  }

  const setAppLayout = (l: AppLayoutType) => {
    appLayout.value = l
    applyTheme()
  }

  const setAppDensity = (d: AppDensityType) => {
    appDensity.value = d
    applyTheme()
  }

  const setAppRadius = (r: AppRadiusType) => {
    appRadius.value = r
    applyTheme()
  }

  const toggleClickEffects = () => {
    clickEffectsEnabled.value = !clickEffectsEnabled.value
    localStorage.setItem('legado_click_effects', clickEffectsEnabled.value ? 'true' : 'false')
  }

  // 初始化应用主题
  applyTheme()

  return {
    isDark,
    toggleDark,
    appTheme,
    appLayout,
    appDensity,
    appRadius,
    clickEffectsEnabled,
    toggleClickEffects,
    setAppTheme,
    setAppLayout,
    setAppDensity,
    setAppRadius,
    applyTheme,
  }
})
