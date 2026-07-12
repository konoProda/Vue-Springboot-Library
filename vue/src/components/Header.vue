<template>
 <div style="height: 50px; line-height:50px; border-bottom: 1px solid #ccc; display: flex" class="header-bar">
   <div style="width: 200px; padding-left:30px; font-weight: bold; color:dodgerblue">
     <img :src="imgUrl" class="icon" >
     图书馆管理系统</div>
   <div style="flex: 1"></div>

   <!-- 深色模式切换 -->
   <div style="width: 100px; display: flex; align-items: center; justify-content: center; cursor: pointer; user-select: none;"
        @click="toggleDark">
     <span style="font-size: 15px; margin-right: 4px;">{{ isDark ? '🌙' : '☀️' }}</span>
     <span style="font-size: 13px;">当前{{ isDark ? '深色' : '浅色' }}</span>
   </div>

   <div style="width: 100px">
     <el-dropdown>
      <span class="el-dropdown-link">
        {{user.nickName}} <el-icon class="el-icon--right">
          <arrow-down />
          </el-icon>
      </span>
       <template #dropdown>
         <el-dropdown-menu>
           <el-dropdown-item @click="exit">退出系统</el-dropdown-item>
         </el-dropdown-menu>
       </template>
     </el-dropdown>
   </div>
 </div>
</template>

<script>
import { ElMessage } from "element-plus";

const THEME_PREFIX = 'theme_'

export default {
  name: "Header",
  props: ['user'],
  data() {
    return {
      user: [],
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
    // 按用户 ID 读取主题偏好
    this.isDark = localStorage.getItem(this.themeKey) === 'dark'
    this.applyTheme()
  },
  methods: {
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
    exit() {
      // 退出时恢复浅色模式
      document.documentElement.classList.remove('dark')
      sessionStorage.removeItem("user")
      this.$router.push("/login")
      ElMessage.success("退出系统成功")
    }
  }
}
</script>

<style scoped>
.icon {
  width: 40px;
  height: 40px;
  padding-top: 5px;
  padding-right: 10px;
}
</style>
