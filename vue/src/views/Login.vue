<template>
<div class="login-wrapper">
    <!-- 白色置顶导航栏：左侧 logo + 标题，右侧语言切换 -->
    <div class="login-topbar">
      <div class="topbar-left">
        <img :src="logoUrl" class="topbar-logo" />
        <span class="topbar-title">图书馆管理系统</span>
      </div>
      <div class="topbar-right">
        <span :class="{ active: $i18n.locale === 'zh' }" @click="switchLang('zh')">简中</span>
        <span class="sep">/</span>
        <span :class="{ active: $i18n.locale === 'en' }" @click="switchLang('en')">EN</span>
      </div>
    </div>

    <!-- 背景图 + 登录表单 -->
    <div class="login-container">
      <el-form ref="form" :model="form" :rules="rules" class="login-page">
      <h2 class="title" style="margin-bottom: 20px">{{ $t('login.title') }}</h2>
      <el-form-item prop="username" >
        <el-input v-model="form.username" :placeholder="$t('login.username')" clearable>
          <template #prefix>
            <el-icon class="el-input__icon"><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" :placeholder="$t('login.password')" clearable show-password>
          <template #prefix>
            <el-icon class="el-input__icon"><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <el-input v-model="form.validCode" style="width: 60%;" :placeholder="$t('login.captcha')"></el-input>
          <ValidCode @input="createValidCode" style="flex-shrink: 0;"/>
        </div>
      </el-form-item>
      <el-form-item >
        <el-button type="primary"  style=" width: 100%" @click="login">{{ $t('login.loginBtn') }}</el-button>
      </el-form-item>
      <el-form-item><el-button type="text" @click="$router.push('/register')">{{ $t('login.register') }}</el-button></el-form-item>
    </el-form>
</div>
</div>

</template>

<script>
import request from "../utils/request";
import {ElMessage} from "element-plus";
import ValidCode from "../components/Validate";

export default {
  name: "Login",
  components:{
    ValidCode
  },
  mounted() {
    document.documentElement.classList.remove('dark')
  },
  computed: {
    rules() {
      return {
        username: [
          { required: true, message: this.$t('login.rules.usernameRequired'), trigger: 'blur' }
        ],
        password: [
          { required: true, message: this.$t('login.rules.passwordRequired'), trigger: 'blur' }
        ]
      }
    }
  },
  data() {
    return {
      validCode: '',
      form: {},
      logoUrl: require('@/assets/icon/login.png'),
    }
  },
  methods: {
    createValidCode(data){
      this.validCode = data
    },
    switchLang(lang) {
      this.$i18n.locale = lang
      localStorage.setItem('lang', lang)
    },
    login(){
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (!this.form.validCode) {
            ElMessage.error("请填写验证码")
            return
          }
          if(this.form.validCode.toLowerCase() !== this.validCode.toLowerCase()) {
            ElMessage.error("验证码错误")
            return
          }

          request.post("user/login", this.form).then(res => {
            if (res.code == 0) {
              ElMessage.success("登录成功")
              sessionStorage.setItem("user",JSON.stringify(res.data))//缓存用户信息
              this.$router.push("/dashboard")
            } else {
              ElMessage.error(res.msg)
            }
          })
        }
      })

    }
  }
}

</script>

<style scoped>
.login-wrapper {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* 白色置顶导航栏，与登录后 Header 保持一致 */
.login-topbar {
  height: 50px;
  line-height: 50px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #ccc;
  padding: 0 30px;
  position: relative;
  z-index: 100;
}
.topbar-left {
  display: flex;
  align-items: center;
  font-weight: bold;
  color: dodgerblue;
  font-size: 1rem;
}
.topbar-logo {
  width: 40px;
  height: 40px;
  margin-right: 10px;
}
.topbar-right {
  font-size: 0.9rem;
  color: #333;
  cursor: pointer;
  user-select: none;
}
.topbar-right .sep {
  margin: 0 4px;
  cursor: default;
}
.topbar-right .active {
  font-weight: 700;
  color: #409eff;
}

/* 背景 + 登录表单 */
.login-container {
  width: 100%;
  height: calc(100vh - 50px);  /* 顶栏下方 */
  background: url('../assets/login-bg.png') center / cover no-repeat;
  overflow: hidden;
}

.login-page {
  border-radius: 5px;
  margin: 120px auto;
  width: 350px;
  padding: 35px 35px 15px;
  background: #fff;
  border: 1px solid #eaeaea;
  box-shadow: 0 0 25px #cac6c6;
}
</style>
