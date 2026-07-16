<template>
  <div>
    <el-card style="width: 500px; margin-left: 120px; margin-top: 40px" >
      <el-form ref="form" :model="form" status-icon :rules="rules" label-width="180px" class="demo-ruleForm">
        <el-form-item :label="$t('password.oldPwd')" prop="password2">
          <el-input v-model="form.password2" type="password" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item :label="$t('password.newPwd')" prop="password">
          <el-input v-model="form2.password" type="password" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item :label="$t('password.confirmPwd')" prop="checkpassword">
          <el-input v-model="form.checkpassword" type="password" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitForm" style="text-align: center">{{ $t('password.submit') }}</el-button>
          <el-button type="danger" @click="resetForm('form')" style="text-align: center">{{ $t('password.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import request from "../utils/request";
import {ElMessage} from "element-plus";

export default {
  name: "Password",
  data() {
    const validatePass2 = (rule, value, callback) => {
      if (value == '') { callback(new Error(this.$t('password.rules.oldRequired'))); return }
      if (this.form.password2 !== this.form.truepassword) { callback(new Error(this.$t('password.rules.oldWrong'))); return }
      callback()
    }
    const validatePass = (rule, value, callback) => {
      if (value === '') { callback(new Error(this.$t('password.rules.newRequired'))); return }
      callback()
    }
    const validatePass3 = (rule, value, callback) => {
      if (value === '') { callback(new Error(this.$t('password.rules.confirmRequired'))); return }
      if (value !== this.form2.password) { callback(new Error(this.$t('password.rules.mismatch'))); return }
      callback()
    }
    return { form: { password2: '', checkpassword: '', truepassword: '' }, form2: { password: '', id: 0 },
      rules: { password: [{ validator: validatePass, trigger: 'blur', required: true }],
        checkpassword: [{ validator: validatePass3, trigger: 'blur', required: true }],
        password2: [{ validator: validatePass2, trigger: 'blur', required: true }] } }
  },
  created() {
    let user = JSON.parse(sessionStorage.getItem("user"))
    this.form.truepassword = user.password
    this.form2.id = user.id
  },
  methods: {
    submitForm() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          request.put("/user/password", null, { params: { id: this.form2.id, password2: this.form2.password } }).then(res => {
            if (res.code == 0) {
              ElMessage.success(this.$t('password.success'))
              sessionStorage.removeItem("user")
              this.$router.push("/login")
            } else { ElMessage.error(res.msg) }
          })
        }
      })
    },
    resetForm(formName) { this.$refs[formName].resetFields() }
  }
}
</script>

<style scoped></style>
