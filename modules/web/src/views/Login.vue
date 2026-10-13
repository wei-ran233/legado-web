<template>
  <div class="login-page">
    <!-- 顶部独立浮动栏：日夜模式切换 -->
    <div class="login-topbar">
      <el-button
        class="theme-toggle-btn"
        circle
        :icon="themeStore.isDark ? Moon : Sunny"
        @click="themeStore.toggleDark"
        :title="themeStore.isDark ? '切换至明亮模式' : '切换至暗黑模式'"
      />
    </div>

    <!-- 极光呼吸流光光斑装饰层 -->
    <div class="aurora-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="login-container">
      <el-card class="login-card" shadow="never">
        <!-- 品牌标识与标题 -->
        <div class="brand-header">
          <div class="brand-badge">
            <el-icon class="brand-icon"><Reading /></el-icon>
          </div>
          <h1 class="brand-title">Legado 阅 读</h1>
          <p class="brand-desc">多源聚合 · 云端书架 · 沉浸阅读</p>
        </div>

        <!-- 第一步：账号密码登录 -->
        <template v-if="step === 1">
          <el-form @submit.prevent="onLogin" label-position="top" size="large" class="login-form">
            <el-form-item label="登录账号">
              <el-input
                v-model="username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                autofocus
                clearable
                @keyup.enter="onLogin"
                class="login-input"
              />
            </el-form-item>
            <el-form-item label="登录密码">
              <el-input
                v-model="password"
                type="password"
                show-password
                placeholder="请输入登录密码"
                :prefix-icon="Lock"
                @keyup.enter="onLogin"
                class="login-input"
              />
            </el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="onLogin"
            >
              登 录 书 架
            </el-button>
          </el-form>
        </template>

        <!-- 第二步：两步验证（TOTP） -->
        <template v-else>
          <div class="totp-step">
            <div class="totp-header">
              <el-icon class="totp-icon"><Iphone /></el-icon>
              <h3>安全两步验证</h3>
              <p class="totp-subtitle">请输入身份验证器中的 6 位动态码，或使用备用恢复码</p>
            </div>
            <el-form @submit.prevent="onVerify" label-position="top" size="large" style="width: 100%">
              <el-form-item>
                <el-input
                  v-model="code"
                  placeholder="6 位动态码 / 8 位恢复码"
                  maxlength="8"
                  size="large"
                  class="totp-code-input"
                  autofocus
                  @input="onCodeInput"
                  @keyup.enter="onVerify"
                >
                  <template #suffix>
                    <el-icon><Iphone /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-button
                type="primary"
                class="submit-btn"
                :loading="loading"
                @click="onVerify"
              >
                验 证 并 登 录
              </el-button>
              <div class="back-wrap">
                <el-button link type="info" @click="backToLogin">← 返回使用其他账号</el-button>
              </div>
            </el-form>
          </div>
        </template>

        <!-- 底部特色特性胶囊 -->
        <div class="features-footer">
          <div class="feature-chip">📚 全网书源</div>
          <div class="feature-chip">☁️ 云端同步</div>
          <div class="feature-chip">📱 移动翻页</div>
          <div class="feature-chip">🎨 6套主题</div>
        </div>
      </el-card>

      <!-- 底部版权说明 -->
      <div class="login-copyright">
        Powered by Legado Web · 开源自建阅读书架
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Iphone, Reading, Moon, Sunny } from '@element-plus/icons-vue'
import { login, verify2fa } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'

const router = useRouter()
const auth = useAuthStore()
const themeStore = useThemeStore()

const step = ref(1)
const username = ref('')
const password = ref('')
const code = ref('')
const tmpToken = ref('')
const loading = ref(false)

const onLogin = async () => {
  const u = username.value.trim()
  const p = password.value
  if (!u || !p) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(u, p)
    if (!res.isSuccess) {
      ElMessage.error(res.errorMsg || '登录失败')
      return
    }
    if (res.data.status === '2FA_REQUIRED') {
      tmpToken.value = res.data.tmp_token || ''
      step.value = 2
      return
    }
    await afterLogin()
  } catch (err: any) {
    const msg = err?.response?.data?.errorMsg || err?.message || '登录失败，请检查用户名或密码'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

const onCodeInput = (val: string) => {
  const clean = val.trim()
  if ((clean.length === 6 && /^\d{6}$/.test(clean)) || clean.length === 8) {
    if (!loading.value) {
      onVerify()
    }
  }
}

const onVerify = async () => {
  const cleanCode = code.value.trim()
  if (!cleanCode) {
    ElMessage.warning('请输入动态码或恢复码')
    return
  }
  loading.value = true
  try {
    const res = await verify2fa(tmpToken.value, cleanCode)
    if (!res.isSuccess) {
      ElMessage.error(res.errorMsg || '验证失败')
      return
    }
    await afterLogin()
  } catch (err: any) {
    const msg = err?.response?.data?.errorMsg || err?.message || '验证失败，请重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

const afterLogin = async () => {
  await auth.fetchMe()
  ElMessage.success(`欢迎回来，${auth.username}`)
  router.replace('/')
}

const backToLogin = () => {
  step.value = 1
  code.value = ''
  tmpToken.value = ''
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 24px 16px;
  background: var(--app-bg-gradient, linear-gradient(135deg, #f0f7f4 0%, #e6f3ee 50%, #d8ece4 100%));
  transition: background 0.4s ease;
}

:global(.dark) .login-page {
  background: radial-gradient(circle at 50% 10%, #171b26 0%, #0d0f17 100%) !important;
}

/* 顶部独立工具栏 */
.login-topbar {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}

.theme-toggle-btn {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.25s ease;
}

:global(.dark) .theme-toggle-btn {
  background: rgba(30, 34, 46, 0.7);
  border-color: rgba(255, 255, 255, 0.12);
  color: #fbbf24;
}

.theme-toggle-btn:hover {
  transform: scale(1.08) rotate(15deg);
}

/* 极光流光光斑 */
.aurora-blobs {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.55;
  animation: float-blob 16s ease-in-out infinite alternate;
}

.blob-1 {
  width: 380px;
  height: 380px;
  background: var(--el-color-primary-light-3, #5cdbd3);
  top: -10%;
  left: 15%;
  animation-duration: 18s;
}

.blob-2 {
  width: 420px;
  height: 420px;
  background: #93c5fd;
  bottom: -15%;
  right: 10%;
  animation-duration: 22s;
  animation-delay: -5s;
}

.blob-3 {
  width: 300px;
  height: 300px;
  background: #fbcfe8;
  top: 40%;
  right: 35%;
  opacity: 0.35;
  animation-duration: 20s;
  animation-delay: -10s;
}

:global(.dark) .blob-1 {
  background: #0284c7;
  opacity: 0.3;
}
:global(.dark) .blob-2 {
  background: #6366f1;
  opacity: 0.25;
}
:global(.dark) .blob-3 {
  background: #a855f7;
  opacity: 0.2;
}

@keyframes float-blob {
  0% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(30px, 40px) scale(1.1);
  }
  100% {
    transform: translate(-25px, 20px) scale(0.95);
  }
}

/* 登录主体容器 */
.login-container {
  width: 100%;
  max-width: 420px;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login-card {
  width: 100%;
  border-radius: var(--app-border-radius, 16px);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.08), 0 2px 6px rgba(0, 0, 0, 0.04);
  padding: 12px 10px;
  transition: all 0.3s ease;
}

:global(.dark) .login-card {
  background: rgba(22, 26, 36, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.05);
}

/* 品牌头部 */
.brand-header {
  text-align: center;
  padding: 10px 0 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-badge {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(0, 150, 136, 0.25);
  margin-bottom: 14px;
  transition: transform 0.3s ease;
}

.brand-badge:hover {
  transform: translateY(-2px) rotate(4deg);
}

.brand-icon {
  font-size: 30px;
  color: #ffffff;
}

.brand-title {
  margin: 0 0 6px 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--el-text-color-primary);
}

.brand-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.5px;
}

/* 表单细节 */
.login-form {
  margin-top: 6px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 14px;
  background: rgba(255, 255, 255, 0.6);
  transition: all 0.2s ease;
}

:global(.dark) .login-input :deep(.el-input__wrapper) {
  background: rgba(15, 18, 25, 0.6);
}

.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-top: 10px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  border: none;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
  transition: all 0.25s cubic-bezier(0.2, 0, 0, 1);
}

.submit-btn:hover {
  transform: translateY(-1.5px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.18);
  filter: brightness(1.05);
}

.submit-btn:active {
  transform: translateY(0);
}

/* TOTP 两步验证 */
.totp-step {
  text-align: center;
  padding: 6px 0;
}

.totp-header {
  margin-bottom: 18px;
}

.totp-icon {
  font-size: 40px;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.totp-header h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.totp-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.totp-code-input :deep(input) {
  font-family: monospace;
  font-size: 22px;
  letter-spacing: 6px;
  text-align: center;
  font-weight: bold;
}

.back-wrap {
  margin-top: 14px;
  text-align: center;
}

/* 特性胶囊 */
.features-footer {
  margin-top: 24px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.feature-chip {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: rgba(0, 0, 0, 0.03);
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

:global(.dark) .feature-chip {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.06);
}

.login-copyright {
  margin-top: 20px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  opacity: 0.8;
}

@media (max-width: 480px) {
  .login-card {
    border-radius: 14px;
    padding: 6px 4px;
  }
  .brand-badge {
    width: 50px;
    height: 50px;
  }
  .brand-icon {
    font-size: 26px;
  }
  .brand-title {
    font-size: 21px;
  }
  .features-footer {
    gap: 6px;
  }
  .feature-chip {
    font-size: 10px;
    padding: 3px 8px;
  }
}
</style>

