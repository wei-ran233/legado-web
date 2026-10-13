import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router'
import store, { useThemeStore } from '@/store'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/assets/bookshelf.css'
import '@/assets/themes.css'

const app = createApp(App).use(store).use(router)
app.mount('#app')

// 初始化全局黑夜/日间模式
useThemeStore().applyTheme()

window.addEventListener('vite:preloadError', event => {
  event.preventDefault()
})
