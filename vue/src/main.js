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

// Element Plus 分页等组件的语言同步
const elLocales = { zh: zhCn, en: enCn }
import { locale as elLocale } from 'element-plus'

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

// 监听 i18n locale 变化，同步 Element Plus locale
import { watch } from 'vue'
watch(
  () => i18n.global.locale,
  (lang) => {
    if (elLocales[lang]) elLocale.value = elLocales[lang]
  }
)
