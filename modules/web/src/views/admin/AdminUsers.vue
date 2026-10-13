<template>
  <div class="admin-users">
    <el-card class="card">
      <template #header>
        <div class="header-row">
          <b>用户管理</b>
          <div class="actions">
            <el-button type="primary" size="default" @click="createVisible = true">
              <el-icon><Plus /></el-icon>&nbsp;新建用户
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="users" size="default" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column label="展示昵称" min-width="120">
          <template #default="{ row }">
            <span>{{ row.nickname || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="两步验证" width="100">
          <template #default="{ row }">
            <el-tag :type="row.totpEnabled ? 'success' : 'info'" size="small">
              {{ row.totpEnabled ? '已启用' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.disabled ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.disabled ? '已禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ new Date(row.createdAt * 1000).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="onToggleDisable(row)">
              {{ row.disabled ? '启用' : '禁用' }}
            </el-button>
            <el-button size="small" type="warning" plain @click="onResetPwd(row)">重置密码</el-button>
            <el-dropdown style="margin-left: 8px" @command="(cmd: string | number | object) => onAction(String(cmd), row)">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="reset_2fa">重置两步验证</el-dropdown-item>
                  <el-dropdown-item command="set_role_admin"
                    :disabled="row.role === 'ADMIN'">设为管理员</el-dropdown-item>
                  <el-dropdown-item command="set_role_user"
                    :disabled="row.role !== 'ADMIN'">设为普通用户</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <span style="color: var(--el-color-danger)">删除（禁用并踢出）</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建用户 -->
    <el-dialog v-model="createVisible" title="新建用户" width="420px">
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="2 位以上" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="USER">普通用户</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listUsers, createUser, userAction, deleteUser, type AdminUserRow,
} from '@/api/admin'

const users = ref<AdminUserRow[]>([])
const loading = ref(false)
const createVisible = ref(false)
const form = ref({ username: '', password: '', role: 'USER' })
const saving = ref(false)

const load = async () => {
  loading.value = true
  try {
    users.value = await listUsers()
  } finally {
    loading.value = false
  }
}

onMounted(load)

const onCreate = async () => {
  saving.value = true
  try {
    const res = await createUser(form.value.username.trim(), form.value.password, form.value.role)
    if (res.isSuccess) {
      ElMessage.success(`用户 ${form.value.username} 创建成功`)
      createVisible.value = false
      form.value = { username: '', password: '', role: 'USER' }
      load()
    } else {
      ElMessage.error(res.errorMsg || '创建失败')
    }
  } finally {
    saving.value = false
  }
}

const onToggleDisable = async (row: AdminUserRow) => {
  const res = await userAction(row.id, row.disabled ? 'enable' : 'disable')
  if (res.isSuccess) {
    ElMessage.success('操作成功')
    load()
  } else {
    ElMessage.error(res.errorMsg || '操作失败')
  }
}

const onResetPwd = async (row: AdminUserRow) => {
  const { value } = await ElMessageBox.prompt(
    `为用户 ${row.username} 设置新密码（至少 8 位，重置后其所有登录会失效）`,
    '重置密码',
    { inputType: 'password' },
  )
  const res = await userAction(row.id, 'reset_password', { password: value })
  if (res.isSuccess) {
    ElMessage.success('密码已重置')
  } else {
    ElMessage.error(res.errorMsg || '重置失败')
  }
}

const onAction = async (cmd: string, row: AdminUserRow) => {
  if (cmd === 'reset_2fa') {
    await ElMessageBox.confirm(`确定重置用户 ${row.username} 的两步验证？`, '提示', { type: 'warning' })
    const res = await userAction(row.id, 'reset_2fa')
    if (res.isSuccess) ElMessage.success('已重置')
    load()
  } else if (cmd.startsWith('set_role')) {
    const role = cmd === 'set_role_admin' ? 'ADMIN' : 'USER'
    const res = await userAction(row.id, 'set_role', { role })
    if (res.isSuccess) {
      ElMessage.success('已修改角色')
      load()
    } else {
      ElMessage.error(res.errorMsg || '修改失败')
    }
  } else if (cmd === 'delete') {
    await ElMessageBox.confirm(
      `将禁用用户 ${row.username} 并踢出其所有会话，确认？`,
      '删除用户',
      { type: 'warning' },
    )
    const res = await deleteUser(row.id)
    if (res.isSuccess) {
      ElMessage.success('已处理')
      load()
    } else {
      ElMessage.error(res.errorMsg || '操作失败')
    }
  }
}
</script>

<style scoped>
.admin-users {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}

.card {
  border-radius: 10px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

@media (max-width: 768px) {
  .admin-users {
    padding: 8px;
  }
}
</style>
