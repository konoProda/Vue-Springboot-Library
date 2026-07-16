<template>
  <el-config-provider :locale="elLocale">
    <div style="overflow-y:hidden;overflow-x: hidden">
      <router-view />
    </div>
  </el-config-provider>
</template>

<script>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enCn from 'element-plus/es/locale/lang/en'

export default {
  name: "App",
  data() {
    return { elLocale: null }
  },
  created() {
    const elLocales = { zh: zhCn, en: enCn }
    this.elLocale = elLocales[this.$i18n.locale] || zhCn
    // 监听 i18n locale 变化，同步 Element Plus 分页等组件
    this._unwatch = this.$watch('$i18n.locale', (lang) => {
      if (elLocales[lang]) this.elLocale = elLocales[lang]
    })
  },
  beforeUnmount() {
    if (this._unwatch) this._unwatch()
  }
}
</script>

<style>
</style>
