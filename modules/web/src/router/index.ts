import { createWebHashHistory, createRouter } from 'vue-router'
import { tokenStore } from '@/api/axios'
import { useAuthStore } from '@/store/authStore'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('../views/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'shelf',
        component: () => import('../views/BookShelf.vue'),
        meta: { auth: true },
      },
      {
        path: 'chapter',
        name: 'chapter',
        component: () => import('../views/BookChapter.vue'),
        meta: { auth: true },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('../views/SearchView.vue'),
        meta: { auth: true },
      },
      {
        path: 'reader',
        name: 'reader',
        component: () => import('../views/Reader.vue'),
        meta: { auth: true },
      },
      {
        path: 'ranking',
        name: 'ranking',
        component: () => import('../views/RankingView.vue'),
        meta: { auth: true },
      },
      {
        path: 'me',
        name: 'me',
        component: () => import('../views/MeView.vue'),
        meta: { auth: true },
      },
      {
        path: 'admin/users',
        name: 'adminUsers',
        component: () => import('../views/admin/AdminUsers.vue'),
        meta: { auth: true, admin: true },
      },
      {
        path: 'admin/sources',
        name: 'adminSources',
        component: () => import('../views/admin/AdminSources.vue'),
        meta: { auth: true, admin: true },
      },
      { path: 'admin', redirect: '/admin/users' },
    ],
  },
  {
    path: '/bookSource',
    name: 'bookSource',
    component: () => import('../views/SourceEditor.vue'),
    meta: { auth: true, admin: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach(async to => {
  if (to.meta.public) return true
  if (!tokenStore.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 管理页校验角色
  if (to.meta.admin) {
    const auth = useAuthStore()
    if (!auth.me) await auth.fetchMe()
    if (auth.me && auth.me.role !== 'ADMIN') return { name: 'shelf' }
  }
  return true
})

router.afterEach(to => {
  if (to.name == 'shelf') document.title = '书架'
  else if (to.name == 'search') document.title = '搜索'
  else if (to.name == 'login') document.title = '登录'
  else if (to.name == 'me') document.title = '个人中心'
  else if (to.path.startsWith('/admin')) document.title = '管理后台'
  else if (to.name == 'chapter') document.title = '阅读'
})

export default router
