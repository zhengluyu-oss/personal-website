<script setup lang="ts">
import vueDanmaku from 'vue3-danmaku'
import {addTreeHole, getTreeHoleList} from "@/apis/treeHole";
import {ElMessage} from "element-plus";
import { ossUrl } from '@/config/site'

const treeHoleBg = ossUrl('blog/TreeHole/TreeHole-back_compressed.webp')
const treeHoleList = ref([])
const isShowSubmit = ref(false)
const content = ref('')

onMounted(() => {
  getTreeHole()
})

function addTreeHoleBtn() {

  if (content.value === '') {
    ElMessage.warning('请输入内容')
    return
  }

  addTreeHole(content.value).then(res => {

    if (res.code === 200) {
      ElMessage.success('添加成功')
      getTreeHole()
      content.value = ''
    } else {
      ElMessage.error(res.msg)
    }
  })
}

function getTreeHole() {
  getTreeHoleList().then(res => {
    if (res.code === 200) {
      treeHoleList.value = res.data
    }
  })
}
</script>
<template>
  <div class="container" :style="{ backgroundImage: `url(${treeHoleBg})` }">
    <div class="content_container">
      <div>树洞</div>
      <div class="compose-row">
        <input v-model="content" @focus="isShowSubmit = true" type="text" placeholder="在这里留下自己的足迹吧...">
        <button v-show="isShowSubmit" type="button" @click="addTreeHoleBtn"><span>提交</span></button>
      </div>
    </div>
    <vue-danmaku :debounce="3000"
                 random-channel
                 :speeds="80"
                 :channels="5"
                 is-suspend
                 v-model:danmus="treeHoleList"
                 use-slot loop
                 class="danmaku-stage">
      <template v-slot:dm="{ danmu }">
        <div class="barrage_container">
          <div>
            <el-avatar :src="danmu.avatar"/>
          </div>
          <div><span>{{ danmu.nickname }}：</span><span>{{ danmu.content }}</span></div>
        </div>
      </template>
    </vue-danmaku>
  </div>
</template>

<style scoped lang="scss">
.container {
  background-size: cover;
  background-position: center;
  width: 100%;
  max-width: 100vw;
  height: 100vh;
  overflow-x: hidden;
  box-sizing: border-box;

  .content_container {
    position: absolute;
    top: 40%;
    left: 50%;
    z-index: 2;
    width: min(22rem, calc(100vw - 2rem));
    transform: translate(-50%, -50%);
    box-sizing: border-box;

    & > div:first-child {
      color: white;
      font-size: clamp(1.5rem, 6vw, 2rem);
      font-weight: bold;
      text-shadow: 0 0 10px #000;
      text-align: center;
      text-transform: uppercase;
      letter-spacing: 0.2rem;
    }

    .compose-row {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      align-items: center;
      gap: 0.5rem;
      margin-top: 1rem;
      width: 100%;

      input {
        flex: 1 1 12rem;
        min-width: 0;
        width: 100%;
        max-width: 100%;
        height: 2.75rem;
        border: #409EFF solid 1px;
        border-radius: 1rem;
        outline: none;
        padding: 0 1rem;
        font-size: 1rem;
        background-color: rgba(255, 255, 255, 0.2);
        box-sizing: border-box;
        color: #fff;
      }

      input::placeholder {
        color: white;
        font-style: italic;
      }

      button {
        flex: 0 0 auto;
        min-width: 4.5rem;
        min-height: 2.75rem;
        padding: 0 1rem;
        border-radius: 1rem;
        outline: none;
        background-color: rgba(255, 255, 255, 0.2);
        border: #409EFF solid 1px;
        color: white;
        font-size: 1rem;
        cursor: pointer;

        span {
          color: #e8e8e8;
          font-style: italic;
          font-weight: bold;
        }

        &:hover {
          background-color: rgba(255, 255, 255, 0.5);
        }
      }
    }
  }

  .danmaku-stage {
    width: 100%;
    height: 100vh;
    max-width: 100%;
  }

  .barrage_container {
    display: flex;
    align-items: center;
    position: relative;
    max-width: min(90vw, 28rem);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      bottom: 0;
      width: 0;
      height: 0.2em;
      border-radius: 0.1em;
      background: linear-gradient(to right, #00c6ff, #0072ff);
      transition: width 0.3s ease;
    }

    &:hover::after {
      width: 100%;
    }

    & div:last-child span:first-child {
      margin-left: 0.5rem;
      color: white;
      font-weight: bold;
    }

    & div:last-child span:last-child {
      font-size: 1.2rem;
      overflow-wrap: anywhere;
    }

    & div:last-child {
      border-bottom: 1px solid #ebebeb;
      padding: 0.5rem;
      margin-left: 0.5rem;
      border-radius: $border-radius;
      background-color: rgba(255, 255, 255, 0.2);
      min-width: 0;
    }
  }
}
</style>
