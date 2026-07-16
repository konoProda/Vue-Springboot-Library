import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import '@/assets/css/global.css'

import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enCn from 'element-plus/es/locale/lang/en'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/assets/icon/iconfont.js' // 图标
import '@/assets/icon/iconfont.css'

// ==================== Vue-i18n ====================
import { createI18n } from 'vue-i18n'
import zh from '@/locales/zh.json'
import en from '@/locales/en.json'

const savedLang = localStorage.getItem('lang') || 'zh'
const i18n = createI18n({
  legacy: true,
  locale: savedLang,
  fallbackLocale: 'zh',
  messages: { zh, en },
})

// Element Plus 配置
const elLocales = { zh: zhCn, en: enCn }

const app = createApp(App)

import * as ElIconModules from '@element-plus/icons'
for (let iconName in ElIconModules) {
  app.component(iconName, ElIconModules[iconName])
}

app.use(store)
  .use(router)
  .use(i18n)
  .use(ElementPlus, { locale: elLocales[savedLang] || zhCn, size: "small" })
  .mount('#app')
