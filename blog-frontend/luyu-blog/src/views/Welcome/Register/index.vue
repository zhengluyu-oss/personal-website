<script setup lang="ts">
import {computed, reactive, ref} from "vue";
import {useRouter} from "vue-router";
import {EditPen, Lock, Message, User} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {sendEmail, confirmEmailDelivery} from "@/apis/email";
import {onBeforeUnmount} from "vue";
import {register} from "@/apis/user";

// 邮件发送冷却时间
const coldTime = ref(0)
const formRef = ref()

const router = useRouter()


// 提交表单
const form = reactive({
  username: '',
  password: '',
  password_repeat: '',
  email: '',
  code: ''
})

// 验证用户名
const validateUsername = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请输入用户名'))
  } else if (!/[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(value)) {
    callback(new Error('用户名不能包含特殊字符，只能是中/英文'))
  } else {
    callback()
  }
}

// 验证重复密码
const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    {validator: validateUsername, trigger: ['blur', 'change']}
  ],
  password: [
    {required: true, message: '请输入密码', trigger: 'blur'},
    {min: 6, max: 20, message: '密码的长度必须在 6-20 个字符之间', trigger: ['blur', 'change']}
  ],
  password_repeat: [
    {validator: validatePassword, trigger: ['blur', 'change']}
  ],
  email: [
    {required: true, message: '请输入邮件地址', trigger: 'blur'},
    {type: 'email', message: '请输入合法的电子邮件地址', trigger: ['blur', 'change']}
  ],
  code: [
    {required: true, message: '请输入获取的验证码', trigger: 'blur'},
  ]
}

const sending = ref(false)
const deliveryController = new AbortController()
let cooldownTimer: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => {
  deliveryController.abort()
  if (cooldownTimer) clearInterval(cooldownTimer)
})
async function askCode() {
  if (!isEmailValid.value || sending.value || coldTime.value > 0) return
  const email = form.email
  sending.value = true
  coldTime.value = 60
  cooldownTimer = setInterval(() => {
    coldTime.value = Math.max(0, coldTime.value - 1)
    if (coldTime.value === 0) clearInterval(cooldownTimer)
  }, 1000)
  try {
    const result: any = await sendEmail(email, "register")
    if (result.code !== 200) throw new Error(result.msg || '发送请求未提交，请稍后重试')
    await confirmEmailDelivery(result.data?.taskId, deliveryController.signal)
    ElMessage.success(`验证码已发送到邮箱：${email}，请注意查收`)
  } catch (error) {
    if (!deliveryController.signal.aborted)
      ElMessage.error(error instanceof Error ? error.message : '发送请求未完成，请稍后重试')
  } finally { sending.value = false }
}

// 判断邮箱是否正确
const isEmailValid = computed(() => /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(form.email))

function registerBtn() {
  formRef.value.validate((valid) => {
    if (valid) {
      register(form).then(res => {
        if (res.code === 200) {
          ElMessage.success('注册成功，欢迎加入我们')
          router.push('/auth/login')
        } else {
          ElMessage.warning(res.msg)
        }
      })
    } else {
      ElMessage.warning('请完整填写注册表单内容')
    }
  });
}

</script>

<template>
  <div style="text-align: center;margin: 0 20px">
    <div style="margin-top: 100px">
      <div style="font-size: 25px;font-weight: bold">注册新用户</div>
      <div style="font-size: 14px;color: grey;margin-top: 1rem">欢迎注册我们的学习平台，请在下方填写相关信息</div>
    </div>
    <div style="margin-top: 50px">
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" maxlength="10" type="text" placeholder="用户名">
            <template #prefix>
              <el-icon>
                <User/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" maxlength="20" type="password" placeholder="密码">
            <template #prefix>
              <el-icon>
                <Lock/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password_repeat">
          <el-input v-model="form.password_repeat" maxlength="20" type="password" placeholder="重复密码">
            <template #prefix>
              <el-icon>
                <Lock/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" type="email" placeholder="电子邮件地址">
            <template #prefix>
              <el-icon>
                <Message/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="code">
          <el-row :gutter="10" style="width: 100%">
            <el-col :span="17">
              <el-input v-model="form.code" maxlength="6" placeholder="请输入验证码">
                <template #prefix>
                  <el-icon>
                    <EditPen></EditPen>
                  </el-icon>
                </template>
              </el-input>
            </el-col>
            <el-col :span="5">
              <el-button type="success" @click="askCode" :loading="sending" :disabled="sending || !isEmailValid || coldTime != 0">
                {{ sending ? '正在发送…' : coldTime > 0 ? `请稍后 ${coldTime} 秒` : '获取验证码' }}
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
      </el-form>
    </div>
    <div style="margin-top: 80px">
      <el-button @click="registerBtn" type="warning" style="width: 270px" plain>立即注册</el-button>
    </div>
    <div style="margin-top: 20px">
      <span style="font-size: 14px;line-height: 15px;color: grey">已有账号?</span>
      <el-link style="translate: 0 -1px" @click="$router.push('/auth/login')">立即登录</el-link>
    </div>
  </div>
</template>

<style scoped>

</style>
