<template>
  <div class="me-view" :class="'me-theme-' + themeStore.appTheme">
    <el-row :gutter="16">
      <!-- 个人信息卡片 -->
      <el-col :xs="24" :sm="10" :md="8">
        <!-- 账户信息 -->
        <el-card class="card profile-main-card">
          <template #header>
            <div class="card-header-flex">
              <b>{{ getAccountHeaderTitle }}</b>
              <span class="theme-identity-pill" :class="'pill-' + themeStore.appTheme">
                {{ getThemeIdentityTag }}
              </span>
            </div>
          </template>
          <div class="me-info">
            <div class="avatar-wrap" :class="'avatar-frame-' + themeStore.appTheme">
              <el-avatar :size="58" class="user-main-avatar">
                {{ (auth.displayName || auth.username || 'U').slice(0, 1).toUpperCase() }}
              </el-avatar>
            </div>
            <div class="info-lines">
              <div class="uname-row">
                <span class="uname">{{ auth.displayName || auth.username }}</span>
                <span v-if="auth.me?.nickname && auth.me.nickname.trim()" class="user-handle">@{{ auth.username }}</span>
              </div>
              <div class="tag-row">
                <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'primary'">
                  {{ auth.isAdmin ? '管理员' : '普通用户' }}
                </el-tag>
              </div>
              <div class="muted">
                {{ auth.me?.totp_enabled ? '🔒 已开启两步验证' : '🔓 未开启两步验证' }}
              </div>
              <el-button type="danger" plain size="small" class="logout-btn"
                @click="onLogout" style="margin-top: 4px">
                <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- 自定义展示名称与排行榜设置 -->
        <el-card class="card">
          <template #header><b>展示名称与排行榜设置</b></template>
          <el-form label-position="top" size="default" @submit.prevent="onSaveProfile">
            <el-form-item label="展示名称（最多 32 字符）">
              <el-input
                v-model="profileForm.nickname"
                placeholder="设置个性化昵称（留空则展示登录名）"
                maxlength="32"
                clearable
                show-word-limit
              />
            </el-form-item>
            <div class="form-tip">用于在个人中心、顶部栏、侧边栏及读者榜中展示，不影响账号登录。</div>

            <el-divider style="margin: 16px 0 12px 0" />

            <el-form-item label="参与读者阅读时长排行榜">
              <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
                <span class="muted" style="font-size: 13px;">
                  {{ profileForm.participateRanking ? '✅ 已开启（计入全站读者榜并公开排名）' : '🔒 已关闭（全站榜单隐藏自身，仅自己可见数据）' }}
                </span>
                <el-switch
                  v-model="profileForm.participateRanking"
                  active-text="公开"
                  inactive-text="隐藏"
                  inline-prompt
                />
              </div>
            </el-form-item>
            <div class="form-tip">关闭后，您的阅读时长不再计入全站公开读者榜；热门阅读书籍榜全员持续正常参与统计。</div>

            <el-button type="primary" :loading="profileLoading" @click="onSaveProfile" style="margin-top: 12px">
              保存设置
            </el-button>
          </el-form>
        </el-card>

        <!-- 修改登录账号 -->
        <el-card class="card">
          <template #header><b>修改登录用户名</b></template>
          <el-form label-position="top" size="default" @submit.prevent="onChangeUsername">
            <el-form-item label="新登录用户名（3-32 位）">
              <el-input
                v-model="usernameForm.newUsername"
                placeholder="字母、数字、中文、下划线"
                maxlength="32"
                clearable
              />
            </el-form-item>
            <el-form-item label="当前账号密码">
              <el-input
                v-model="usernameForm.password"
                type="password"
                show-password
                placeholder="输入当前密码以验证身份"
              />
            </el-form-item>
            <div class="form-tip">修改后需使用新用户名登录。当前系统将自动更新会话，无需重新登录。</div>
            <el-button type="warning" plain :loading="usernameLoading" @click="onChangeUsername" style="margin-top: 10px">
              确认修改账号
            </el-button>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card class="card">
          <template #header><b>修改密码</b></template>
          <el-form label-position="top" size="default">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.old" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码（至少 8 位）">
              <el-input v-model="pwdForm.new1" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwdForm.new2" type="password" show-password />
            </el-form-item>
            <el-button type="primary" @click="onChangePwd" :loading="pwdLoading">保存新密码</el-button>
          </el-form>
        </el-card>
      </el-col>

      <!-- 外观与个性化主题、2FA 与会话 -->
      <el-col :xs="24" :sm="14" :md="16">
        <!-- 整站外观与个性化主题 -->
        <el-card class="card theme-settings-card">
          <template #header>
            <div class="theme-card-header">
              <span><b>🎨 全站视觉主题与个性化</b></span>
              <div class="header-actions">
                <el-button
                  size="small"
                  :type="themeStore.isDark ? 'warning' : 'info'"
                  plain
                  @click="themeStore.toggleDark"
                >
                  <el-icon><Moon v-if="themeStore.isDark" /><Sunny v-else /></el-icon>&nbsp;
                  {{ themeStore.isDark ? '暗黑模式' : '明亮模式' }}
                </el-button>
              </div>
            </div>
          </template>

          <div class="theme-section">
            <div class="section-title">色彩主题风格（{{ themeList.length }} 套深度定制，适配移动与桌面端）</div>
            <div class="theme-grid">
              <div
                v-for="item in themeList"
                :key="item.key"
                class="theme-item"
                :class="{ active: themeStore.appTheme === item.key }"
                @click="themeStore.setAppTheme(item.key)"
              >
                <div class="theme-item-preview" :style="{ backgroundColor: item.bgColor }">
                  <span class="preview-dot primary-dot" :style="{ backgroundColor: item.primaryColor }" />
                  <span class="preview-dot accent-dot" :style="{ backgroundColor: item.accentColor }" />
                  <span v-if="themeStore.appTheme === item.key" class="check-badge">
                    <el-icon><Check /></el-icon>
                  </span>
                </div>
                <div class="theme-item-info">
                  <div class="theme-name">
                    {{ item.name }}
                    <span class="theme-sub">{{ item.sub }}</span>
                  </div>
                  <div class="theme-desc">{{ item.desc }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="card">
          <template #header><b>两步验证（TOTP）</b></template>

          <template v-if="!auth.me?.totp_enabled">
            <p class="muted">使用 Google Authenticator / Aegis / 1Password 等验证器扫描二维码绑定。</p>
            <el-button type="primary" :loading="setupLoading" @click="startSetup">
              开始绑定
            </el-button>

            <div v-if="setupData" class="setup-area">
              <el-divider />
              <div class="qr-wrap">
                <img v-if="qrDataUrl" :src="qrDataUrl" class="qr" alt="2FA QRCode" />
                <div class="secret-box">
                  <div class="muted">手动输入密钥：</div>
                  <el-text tag="code" class="secret-code">{{ setupData.secret }}</el-text>
                  <div style="margin-top: 8px">
                    <el-button size="small" type="primary" plain @click="copySecret">
                      复制密钥
                    </el-button>
                  </div>
                </div>
              </div>
              <el-form label-position="top" style="max-width: 360px" @submit.prevent="confirmSetup">
                <el-form-item label="输入验证器中的 6 位动态码确认">
                  <el-input
                    ref="codeInputRef"
                    v-model="verifyCode"
                    maxlength="6"
                    placeholder="000000"
                    size="large"
                    class="totp-input"
                    clearable
                    @input="onCodeInput"
                    @keyup.enter="confirmSetup"
                  />
                </el-form-item>
                <div style="display: flex; gap: 10px; margin-top: 8px;">
                  <el-button
                    type="primary"
                    @click="confirmSetup"
                    :loading="confirmLoading"
                    :disabled="verifyCode.trim().length !== 6"
                  >
                    确认启用
                  </el-button>
                  <el-button @click="cancelSetup">取消</el-button>
                </div>
              </el-form>
            </div>
          </template>

          <template v-else>
            <p class="muted">两步验证已启用，剩余恢复码：{{ auth.me?.recovery_codes_left }} 个</p>
            <el-button type="danger" plain @click="onDisable2fa">解除绑定</el-button>
          </template>
        </el-card>

        <el-card class="card">
          <template #header>
            <b>登录设备</b>
            <el-button text size="small" style="float: right" @click="loadSessions">刷新</el-button>
          </template>
          <el-table :data="sessions" size="small">
            <el-table-column prop="device" label="设备" min-width="140" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="130" />
            <el-table-column label="最近活跃" width="160">
              <template #default="{ row }">{{ new Date(row.lastUsedAt * 1000).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="onRevoke(row)">踢出</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SwitchButton, Check, Sunny, Moon } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { useAuthStore } from '@/store/authStore'
import {
  useThemeStore,
  type AppThemeType,
} from '@/store/themeStore'
import {
  changePassword, setup2fa, enable2fa, disable2fa,
  listSessions, revokeSession, updateProfile, updateUsername,
  type SessionInfo,
} from '@/api/auth'

const auth = useAuthStore()
const themeStore = useThemeStore()

const getAccountHeaderTitle = computed(() => {
  switch (themeStore.appTheme) {
    case 'sakura':
      return '🌸 浅樱书斋档案'
    case 'obsidian':
      return '⚡ [SYS.OPERATOR] 战备终端'
    case 'aurora':
      return '✨ 星际领航者坐标'
    case 'azure':
      return '◈ THE READER // DOSSIER'
    case 'classic':
    default:
      return '📚 读者账户信息'
  }
})

const getThemeIdentityTag = computed(() => {
  switch (themeStore.appTheme) {
    case 'sakura':
      return '浅樱知己'
    case 'obsidian':
      return 'ONLINE // LVL.1'
    case 'aurora':
      return 'STARFARER'
    case 'azure':
      return 'VOL.26'
    case 'classic':
    default:
      return 'LEGADO'
  }
})

const themeList: Array<{
  key: AppThemeType
  name: string
  sub: string
  desc: string
  primaryColor: string
  accentColor: string
  bgColor: string
}> = [
  {
    key: 'aurora',
    name: '星河极光',
    sub: 'Motion Aurora',
    desc: '全屏流动极光网格与彩虹悬浮胶囊',
    primaryColor: '#a855f7',
    accentColor: '#38bdf8',
    bgColor: '#f8f6ff',
  },
  {
    key: 'sakura',
    name: '浅樱落雪',
    sub: 'Sakura Blossom',
    desc: '和风樱花树、漫天落樱与 macOS 底部 Dock',
    primaryColor: '#e11d48',
    accentColor: '#fb7185',
    bgColor: '#fff5f7',
  },
  {
    key: 'classic',
    name: '经典主题',
    sub: 'Legado Classic',
    desc: '原生墨绿质感与经典 200px 树状侧边栏',
    primaryColor: '#10b981',
    accentColor: '#34d399',
    bgColor: '#f8fafc',
  },
  {
    key: 'azure',
    name: '蔚蓝画报',
    sub: 'Azure Editorial',
    desc: '现代杂志大画报横栏与模块化 Bento 网格',
    primaryColor: '#0284c7',
    accentColor: '#38bdf8',
    bgColor: '#f0f6fc',
  },
  {
    key: 'obsidian',
    name: '黑曜赛博',
    sub: 'Cyber Matrix',
    desc: '全息矩阵扫描线与太空 HUD 战备控制台',
    primaryColor: '#8b5cf6',
    accentColor: '#06b6d4',
    bgColor: '#090c14',
  },
]

// 自定义展示名称与排行榜参与设置
const profileForm = ref({ nickname: '', participateRanking: true })
const profileLoading = ref(false)

// 修改登录账号
const usernameForm = ref({ newUsername: '', password: '' })
const usernameLoading = ref(false)

watch(
  () => auth.me,
  (me) => {
    if (me) {
      if (profileForm.value.nickname === '' && me.nickname) {
        profileForm.value.nickname = me.nickname
      }
      profileForm.value.participateRanking = me.participate_ranking !== false
    }
  },
  { immediate: true },
)

const onSaveProfile = async () => {
  profileLoading.value = true
  try {
    const res = await updateProfile({
      nickname: profileForm.value.nickname.trim(),
      participate_ranking: profileForm.value.participateRanking,
    })
    if (res.isSuccess) {
      ElMessage.success('个人设置已成功更新')
      auth.setNickname(profileForm.value.nickname.trim())
      auth.setParticipateRanking(profileForm.value.participateRanking)
      await auth.fetchMe()
    } else {
      ElMessage.error(res.errorMsg || '更新失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.errorMsg || '更新失败')
  } finally {
    profileLoading.value = false
  }
}

const onChangeUsername = async () => {
  const newU = usernameForm.value.newUsername.trim()
  if (!newU) {
    ElMessage.warning('请输入新的登录用户名')
    return
  }
  if (!usernameForm.value.password) {
    ElMessage.warning('请输入当前账号密码以验证身份')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定将登录账号从 "${auth.username}" 修改为 "${newU}" 吗？`,
      '修改登录账号提示',
      {
        confirmButtonText: '确定修改',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  usernameLoading.value = true
  try {
    const res = await updateUsername(newU, usernameForm.value.password)
    if (res.isSuccess) {
      ElMessage.success(`登录账号已成功修改为 "${newU}"`)
      auth.setUsername(newU)
      usernameForm.value = { newUsername: '', password: '' }
      await auth.fetchMe()
    } else {
      ElMessage.error(res.errorMsg || '修改失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.errorMsg || '修改失败')
  } finally {
    usernameLoading.value = false
  }
}

const pwdForm = ref({ old: '', new1: '', new2: '' })
const pwdLoading = ref(false)

const setupData = ref<{ secret: string; otpauth_uri: string } | null>(null)
const qrDataUrl = ref('')
const verifyCode = ref('')
const setupLoading = ref(false)
const confirmLoading = ref(false)
const codeInputRef = ref<any>(null)

const sessions = ref<SessionInfo[]>([])

const onLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出当前账号登录吗？', '提示', {
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

const loadSessions = async () => {
  sessions.value = await listSessions()
}

onMounted(loadSessions)

const onChangePwd = async () => {
  if (pwdForm.value.new1 !== pwdForm.value.new2) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (pwdForm.value.new1.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  pwdLoading.value = true
  try {
    const res = await changePassword(pwdForm.value.old, pwdForm.value.new1)
    if (res.isSuccess) {
      ElMessage.success('密码已修改，请重新登录')
      setTimeout(() => auth.logout(), 800)
    } else {
      ElMessage.error(res.errorMsg || '修改失败')
    }
  } finally {
    pwdLoading.value = false
  }
}

const startSetup = async () => {
  setupLoading.value = true
  try {
    setupData.value = await setup2fa()
    qrDataUrl.value = await QRCode.toDataURL(setupData.value.otpauth_uri, { width: 180 })
    verifyCode.value = ''
    setTimeout(() => {
      codeInputRef.value?.focus()
    }, 100)
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.errorMsg || '获取绑定信息失败')
  } finally {
    setupLoading.value = false
  }
}

const cancelSetup = () => {
  setupData.value = null
  qrDataUrl.value = ''
  verifyCode.value = ''
}

const copySecret = async () => {
  if (!setupData.value?.secret) return
  try {
    await navigator.clipboard.writeText(setupData.value.secret)
    ElMessage.success('密钥已复制到剪贴板')
  } catch {
    ElMessage.info(`密钥：${setupData.value.secret}`)
  }
}

const onCodeInput = (val: string) => {
  const digits = val.replace(/\D/g, '').slice(0, 6)
  verifyCode.value = digits
  if (digits.length === 6 && !confirmLoading.value) {
    confirmSetup()
  }
}

const confirmSetup = async () => {
  if (!setupData.value) return
  const code = verifyCode.value.trim()
  if (code.length !== 6) {
    ElMessage.warning('请输入完整的 6 位动态码')
    return
  }
  confirmLoading.value = true
  try {
    const res = await enable2fa(setupData.value.secret, code)
    const codes = res.recovery_codes
    await auth.fetchMe()
    setupData.value = null
    verifyCode.value = ''
    ElMessageBox.alert(
      `<div style="margin-bottom: 10px; font-weight: bold; color: #67c23a;">两步验证（TOTP）已成功启用！</div>
       <p style="margin-bottom: 8px; color: #606266;">请妥善保存以下一次性恢复码（当手机丢失时可凭恢复码登录，仅显示一次）：</p>
       <pre style="background: #f4f4f5; padding: 12px; border-radius: 6px; columns: 2; font-family: monospace; font-size: 13px; line-height: 1.8; margin-top: 8px;">${codes.join('\n')}</pre>`,
      '2FA 恢复码（请立即备份）',
      { dangerouslyUseHTMLString: true, confirmButtonText: '我已保存' },
    )
  } catch (err: any) {
    const msg = err?.response?.data?.errorMsg || err?.message || '验证失败，请确认动态码是否正确'
    ElMessage.error(msg)
  } finally {
    confirmLoading.value = false
  }
}

const onDisable2fa = async () => {
  const { value } = await ElMessageBox.prompt('输入登录密码以解除 2FA', '解除两步验证', {
    inputType: 'password',
  })
  const res = await disable2fa(value)
  if (res.isSuccess) {
    ElMessage.success('已解除')
    await auth.fetchMe()
  } else {
    ElMessage.error(res.errorMsg || '解除失败')
  }
}

const onRevoke = async (row: SessionInfo) => {
  await revokeSession(row.id)
  ElMessage.success('已踢出该设备')
  loadSessions()
}
</script>

<style scoped>
.me-view {
  padding: 16px;
}

.card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.me-info {
  display: flex;
  gap: 14px;
  align-items: center;
}

.info-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.uname-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.uname {
  font-size: 18px;
  font-weight: 700;
}

.user-handle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.user-main-avatar {
  background: var(--el-color-primary);
  color: #fff;
  font-weight: bold;
  font-size: 22px;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: -8px;
  line-height: 1.5;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.setup-area {
  margin-top: 8px;
}

.qr-wrap {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.qr {
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

.secret-box code {
  word-break: break-all;
}

.secret-code {
  display: inline-block;
  font-size: 14px;
  background: var(--el-fill-color-light);
  padding: 4px 8px;
  border-radius: 4px;
  user-select: all;
}

.totp-input :deep(input) {
  font-family: monospace;
  font-size: 20px;
  letter-spacing: 6px;
  text-align: center;
  font-weight: bold;
}

/* 主题与个性化卡片样式 */
.theme-settings-card {
  transition: box-shadow 0.25s ease;
}

.theme-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.theme-section .section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.theme-item {
  border: 1.5px solid var(--el-border-color-light);
  border-radius: var(--app-border-radius, 10px);
  padding: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.2, 0, 0, 1);
  background: var(--el-fill-color-blank);
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
  user-select: none;
}

.theme-item:hover {
  transform: translateY(-2px);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
}

.theme-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1.5px var(--el-color-primary), 0 4px 14px rgba(0, 0, 0, 0.1);
}

.theme-item-preview {
  height: 46px;
  border-radius: calc(var(--app-border-radius, 8px) - 2px);
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.06);
}

.preview-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.25);
}

.check-badge {
  margin-left: auto;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: bold;
}

.theme-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.theme-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.theme-sub {
  font-size: 11px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.theme-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.theme-chips-box {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 4px;
}

.chip-item {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1.3;
}

.chip-item.motion {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 500;
}

.chip-item.layout {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.setting-item .item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.setting-item .item-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.custom-radio-group {
  display: flex;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
