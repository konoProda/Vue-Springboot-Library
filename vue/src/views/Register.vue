<template>
<div class="login-wrapper">
  <!-- 白色置顶导航栏 -->
  <div class="login-topbar">
    <div class="topbar-left">
      <img :src="logoUrl" class="topbar-logo" />
      <span>{{ $t('header.systemName') }}</span>
    </div>
    <div class="topbar-right">
      <span :class="{ active: $i18n.locale === 'zh' }" @click="switchLang('zh')">简中</span>
      <span class="sep">/</span>
      <span :class="{ active: $i18n.locale === 'en' }" @click="switchLang('en')">EN</span>
    </div>
  </div>

  <!-- 背景图 + 注册表单 -->
  <div class="login-body">
    <div class="login-form-wrapper">
      <el-form ref="form" :model="form" :rules="rules" class="login-page">
        <h2 class="title" style="margin-bottom: 20px">{{ $t('register.title') }}</h2>
        <el-form-item prop="username" >
          <el-input v-model="form.username" :placeholder="$t('register.username')" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" :placeholder="$t('register.password')" clearable show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" :placeholder="$t('register.confirmPwd')" clearable show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="role">
          <el-radio v-model="form.role" label="2">{{ $t('register.roleReader') }}</el-radio>
          <el-radio v-model="form.role" label="1">{{ $t('register.roleAdmin') }}</el-radio>
        </el-form-item>
        <el-form-item prop="authorize" v-if="form.role==1">
          <el-input v-model="form.authorize" :placeholder="$t('register.authCode')" clearable show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <div style="display: flex">
            <el-input v-model="form.validCode" style="width: 45%;" :placeholder="$t('register.captcha')"></el-input>
            <ValidCode @input="createValidCode" style="width: 50%"/>
          </div>
        </el-form-item>
        <el-form-item >
          <el-button type="primary" style="width: 100%" @click="register">{{ $t('register.btn') }}</el-button>
        </el-form-item>
        <el-form-item><el-button type="text" @click="$router.push('/login')">{{ $t('register.toLogin') }}</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</div>
</template>

<script>
import request from "../utils/request";
import {ElMessage} from "element-plus";
import ValidCode from "../components/Validate";

export default {
  name: "Register",
  components:{ ValidCode },
  data(){
    return {
      form: { role: '2' },
      validCode: '',
      logoUrl: require('@/assets/icon/login.png'),
    }
  },
  computed: {
    rules() {
      return {
        username: [
          { required: true, message: this.$t('register.rules.usernameRequired'), trigger: 'blur' },
          { min: 2, max: 13, message: this.$t('register.rules.usernameLength'), trigger: 'blur' },
        ],
        password: [
          { required: true, message: this.$t('register.rules.passwordRequired'), trigger: 'blur' }
        ],
        confirm: [
          { required: true, message: this.$t('register.rules.confirmRequired'), trigger: 'blur' }
        ],
        authorize: [
          { required: true, message: this.$t('register.rules.authCodeRequired'), trigger: 'blur' }
        ],
      }
    }
  },
  methods: {
    createValidCode(data){ this.validCode = data },
    switchLang(lang) {
      this.$i18n.locale = lang
      localStorage.setItem('lang', lang)
    },
    register(){
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (!this.form.validCode) { ElMessage.error(this.$t('login.fillCaptcha')); return }
          if(this.form.validCode.toLowerCase() !== this.validCode.toLowerCase()) { ElMessage.error(this.$t('login.wrongCaptcha')); return }
          if(this.form.password != this.form.confirm) { ElMessage.error("Passwords do not match"); return }
          if(this.form.role == 1 && this.form.authorize != "1234") { ElMessage.error("Invalid registration code"); return }
          request.post("user/register", this.form).then(res => {
            if(res.code == 0) { ElMessage.success(this.$t('login.loginSuccess')); this.$router.push("/login") }
            else { ElMessage.error(res.msg) }
          })
        }
      })
    }
  }
}
</script>

<style scoped>
.login-wrapper { width: 100%; height: 100vh; overflow: hidden; }

.login-topbar { height: 50px; line-height: 50px; display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-bottom: 1px solid #e0e0e0; padding: 0 30px; position: relative; z-index: 100; }
.topbar-left { display: flex; align-items: center; font-weight: bold; color: dodgerblue; font-size: 1rem; }
.topbar-logo { width: 40px; height: 40px; margin-right: 10px; }
.topbar-right { font-size: 0.9rem; color: #555; cursor: pointer; user-select: none; }
.topbar-right .sep { margin: 0 4px; cursor: default; }
.topbar-right .active { font-weight: 700; color: #409eff; }

.login-body { width: 100%; height: calc(100vh - 50px);
  background: url('../assets/login-bg.png') center / cover no-repeat fixed;
  display: flex; align-items: center; justify-content: flex-end; padding-right: 10%; }
.login-form-wrapper { flex-shrink: 0; }

.login-page { border-radius: 5px; width: 350px; padding: 35px 35px 15px;
  background: #fff; border: 1px solid #eaeaea; box-shadow: 0 0 25px #cac6c6; }
.login-page :deep(.el-input__icon) { line-height: inherit; display: inline-flex; align-items: center; }
.login-page :deep(.el-input__prefix) { display: inline-flex; align-items: center; }

html.dark .login-topbar { background-color: #1d1e1f; border-bottom-color: #363637; }
html.dark .topbar-left { color: #7eb2e6; }
html.dark .login-page { background-color: #1d1e1f !important; border-color: #363637 !important;
  box-shadow: 0 0 25px rgba(0,0,0,0.4) !important; }
</style>
