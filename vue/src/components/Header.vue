<template>
<div>
  <!-- 第一行：Logo + 系统名 | 深色模式 + 语言切换 + 用户 -->
  <div class="header-row1">
    <div class="header-left">
      <img :src="imgUrl" class="header-logo" />
      <span class="header-title">图书馆管理系统</span>
    </div>
    <div class="header-right">
      <!-- 深色模式 -->
      <span class="header-widget" @click="toggleDark" style="cursor:pointer; user-select:none;">
        {{ isDark ? '🌙' : '☀️' }} {{ isDark ? '深色' : '浅色' }}
      </span>
      <span class="header-divider">|</span>
      <!-- 语言切换 -->
      <span class="header-widget lang-switch">
        <span :class="{ active: $i18n.locale === 'zh' }" @click="switchLang('zh')">简中</span>
        <span>/</span>
        <span :class="{ active: $i18n.locale === 'en' }" @click="switchLang('en')">EN</span>
      </span>
      <span class="header-divider">|</span>
      <!-- 用户 -->
      <el-dropdown>
        <span class="header-widget" style="cursor:pointer;">
          {{ user.nickName || user.username }} <el-icon><arrow-down /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="exit">退出系统</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>

  <!-- 第二行：水平导航菜单 -->
  <div class="header-row2">
    <div class="nav-item" :class="{ active: isActive('/dashboard') }" @click="$router.push('/dashboard')">
      <svg class="nav-icon"><use xlink:href="#icondashboard" /></svg>
      展示板
    </div>
    <div class="nav-item" :class="{ active: isActive('/person') }" @click="$router.push('/person')">
      <svg class="nav-icon"><use xlink:href="#icon-mingpian" /></svg>
      个人信息
    </div>
    <div class="nav-item" v-if="user.role == 1" :class="{ active: isActive('/user') }" @click="$router.push('/user')">
      <svg class="nav-icon"><use xlink:href="#iconreader" /></svg>
      读者管理
    </div>
    <div class="nav-item" :class="{ active: isActive('/book') }" @click="$router.push('/book')">
      <svg class="nav-icon"><use xlink:href="#iconbook" /></svg>
      {{ user.role == 1 ? '书籍管理' : '图书查询' }}
    </div>
    <div class="nav-item" :class="{ active: isActive('/lendrecord') }" @click="$router.push('/lendrecord')">
      <svg class="nav-icon"><use xlink:href="#iconlend-record" /></svg>
      {{ user.role == 1 ? '借阅管理' : '借阅信息' }}
    </div>
    <div class="nav-item" :class="{ active: isActive('/bookwithuser') }" @click="$router.push('/bookwithuser')">
      <el-icon><grid /></el-icon>
      借阅状态
    </div>
    <div class="nav-item" v-if="user.role == 1" :class="{ active: isActive('/log') }" @click="$router.push('/log')">
      <svg class="nav-icon"><use xlink:href="#iconlend-record" /></svg>
      操作日志
    </div>
  </div>
</div>
</template>

<script>
import { ElMessage } from "element-plus";

const THEME_PREFIX = 'theme_'

export default {
  name: "Header",
  data() {
    return {
      user: {},
      imgUrl: require("../assets/icon/login.png"),
      isDark: false
    }
  },
  computed: {
    themeKey() {
      return THEME_PREFIX + (this.user.id || 'anon')
    }
  },
  created() {
    let userStr = sessionStorage.getItem("user") || "{}"
    this.user = JSON.parse(userStr)
    this.isDark = localStorage.getItem(this.themeKey) === 'dark'
    this.applyTheme()
  },
  methods: {
    isActive(path) {
      return this.$route.path === path
    },
    toggleDark() {
      this.isDark = !this.isDark
      localStorage.setItem(this.themeKey, this.isDark ? 'dark' : 'light')
      this.applyTheme()
    },
    applyTheme() {
      if (this.isDark) {
        document.documentElement.classList.add('dark')
      } else {
        document.documentElement.classList.remove('dark')
      }
    },
    switchLang(lang) {
      this.$i18n.locale = lang
      localStorage.setItem('lang', lang)
    },
    exit() {
      document.documentElement.classList.remove('dark')
      sessionStorage.removeItem("user")
      this.$router.push("/login")
      ElMessage.success("退出系统成功")
    }
  }
}
</script>

<style scoped>
/* ===== 第一行 ===== */
.header-row1 {
  height: 50px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e0e0e0;
  padding: 0 24px;
}
.header-left {
  display: flex;
  align-items: center;
  font-weight: 700;
  color: dodgerblue;
  font-size: 16px;
}
.header-logo {
  width: 36px;
  height: 36px;
  margin-right: 10px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #555;
}
.header-widget {
  white-space: nowrap;
}
.header-divider {
  color: #ddd;
}
.lang-switch span { cursor: pointer; }
.lang-switch .active { font-weight: 700; color: #409eff; }

/* ===== 第二行 ===== */
.header-row2 {
  display: flex;
  align-items: center;
  background: #30333c;
  padding: 0 16px;
  height: 46px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 16px;
  height: 100%;
  color: #ccc;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  white-space: nowrap;
}
.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}
.nav-item.active {
  background: rgba(255,255,255,0.12);
  color: #fff;
  font-weight: 600;
}
.nav-icon {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

/* ===== 深色模式 ===== */
html.dark .header-row1 {
  background-color: #1d1e1f;
  border-bottom-color: #363637;
  color: #e5eaf3;
}
html.dark .header-left { color: #7eb2e6; }
html.dark .header-right { color: #cfd3dc; }
</style>
