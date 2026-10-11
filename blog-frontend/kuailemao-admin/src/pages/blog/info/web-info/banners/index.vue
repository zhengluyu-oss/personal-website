<script setup lang="ts">
import type {
  DraggableEvent,
  UseDraggableReturn,
} from 'vue-draggable-plus'
import {
  VueDraggable,
} from 'vue-draggable-plus'
import {ref} from 'vue'
import axios from 'axios'
import {message} from 'ant-design-vue'
import type {FileType} from 'ant-design-vue/es/upload/interface'
import {backGetBanners, confirmHeroVideo, deleteBanner, getHeroVideo, issueHeroVideoTicket, removeHeroVideo, updateOrder, uploadBanner} from "~/api/blog/banners";
import type {HeroVideoState, HeroVideoTicket} from "~/api/blog/banners";
import {compressImage} from "~/utils/CompressedImage.ts";
import {toImageUploadFile} from '~/utils/upload-file'

// 图片类型
interface FileItem {
  id: string,
  path: string,
  size: string,
  type: string,
  userId: string,
  sortOrder: number,
  createTime: string
}

const fileList = ref<FileItem[]>()

const el = ref<UseDraggableReturn>()
const disabled = ref(false)


const previewVisible = ref(false)
const tempImage = ref()
const previewTitle = ref('')

function getFileList() {
  backGetBanners().then(res => {
    fileList.value = res.data.length > 0 ? res.data : []
  })
}

onMounted(() => {
  getFileList()
  void refreshVideo()
})

function handleCancel() {
  previewVisible.value = false
  previewTitle.value = ''
}

function handlePreview(file: FileItem) {
  tempImage.value = file
  previewVisible.value = true
  previewTitle.value = file.path.substring(file.path.lastIndexOf('/') + 1) || '轮播图'
}

const uploading = ref(false)
const progress = ref(0)
const heroVideo = ref<HeroVideoState>({url: null, objectKey: null})
const videoBusy = ref(false)
const videoProgress = ref(0)
const videoError = ref('')
const videoInput = ref<HTMLInputElement>()

async function refreshVideo() {
  try { heroVideo.value = (await getHeroVideo()).data as HeroVideoState }
  catch { videoError.value = '无法读取当前首页视频，请稍后重试' }
}

async function uploadVideo(file: File) {
  videoError.value = ''
  if (file.type !== 'video/mp4' || !file.name.toLowerCase().endsWith('.mp4')) {
    videoError.value = '仅支持 MP4 文件'
    return
  }
  if (file.size < 1 || file.size > 8 * 1024 * 1024) {
    videoError.value = '视频大小不能超过 8 MiB'
    return
  }
  videoBusy.value = true
  videoProgress.value = 0
  try {
    const ticket = (await issueHeroVideoTicket()).data as HeroVideoTicket
    const data = new FormData()
    data.append('key', ticket.objectKey)
    data.append('policy', ticket.policy)
    data.append('OSSAccessKeyId', ticket.accessKeyId)
    data.append('Signature', ticket.signature)
    data.append('success_action_status', '200')
    data.append('Content-Type', 'video/mp4')
    data.append('file', file, file.name)
    await axios.post(ticket.uploadUrl, data, {
      onUploadProgress: event => {
        if (event.total) videoProgress.value = Math.round(event.loaded * 95 / event.total)
      },
      timeout: 120_000,
    })
    const saved = await confirmHeroVideo(ticket.objectKey)
    if (saved.code !== 200) throw new Error(saved.msg || '视频确认失败')
    heroVideo.value = saved.data as HeroVideoState
    videoProgress.value = 100
    message.success('首页视频已更新')
  } catch (error) {
    videoError.value = axios.isAxiosError(error)
      ? !error.response
        ? '上传网络或 OSS 跨域配置失败，请检查网络并重试'
        : error.response.status === 403
          ? '上传授权已过期或 OSS 拒绝请求，请重新选择视频'
          : `OSS 上传失败（${error.response.status}），请重试`
      : error instanceof Error ? error.message : '视频上传失败，请重试'
    message.error(videoError.value)
  } finally {
    videoBusy.value = false
    if (videoInput.value) videoInput.value.value = ''
  }
}

function chooseVideo(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file && !videoBusy.value) void uploadVideo(file)
}

async function clearVideo() {
  if (videoBusy.value) return
  videoBusy.value = true
  videoError.value = ''
  try {
    const result = await removeHeroVideo()
    if (result.code !== 200) throw new Error(result.msg || '移除失败')
    heroVideo.value = {url: null, objectKey: null}
    message.success('首页视频已移除，访客将看到图片封面')
  } catch (error) {
    videoError.value = error instanceof Error ? error.message : '移除失败，请重试'
  } finally { videoBusy.value = false }
}

// 上传前
async function beforeUpload(file: FileType) {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp'
  if (!isJpgOrPng) {
    message.error('文件格式必须是jpg或png或webp')
    return
  }
  const compressedFile = await compressImage(file)

  const isLt03MB = compressedFile.size / 1024 / 1024 < 0.3
  if (!isLt03MB) {
    message.error('图片压缩后大小大于 0.3MB')
    return
  }

  // 手动上传
  const formData = new FormData();
  const bannerFile = toImageUploadFile(compressedFile, file.name)
  formData.append('bannerImage', bannerFile, bannerFile.name);
  uploading.value = true;
  uploadBanner(formData, handleProgress).then(async (res) => {
    if (res.code === 200) {
      getFileList()
      message.success('上传成功')
    }
  }).catch(msg => {
    message.warn(msg)
  }).finally(() => {
    uploading.value = false
  })

  return false;
}

// axios请求，图片上传进度回调
function handleProgress(progressEvent: ProgressEvent) {
  progress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
}


function deleteBannerFunc(bannerId: string) {
  deleteBanner(bannerId).then(() => {
    getFileList()
    message.success('删除成功')
  })
}

// 排序
function sortOrderFunc(_: DraggableEvent) {
  updateOrder(fileList.value).then((req) => {
    if (req.code === 200) {
      message.success('排序成功')
    }
  })
}

</script>

<template>
  <a-card title="首页背景视频" class="hero-video-card">
    <p>上传 MP4，最大 8 MiB。推荐 H.264、无音轨、5–12 秒循环素材。首页会先显示下方第一张轮播图作为封面。</p>
    <video v-if="heroVideo.url" :src="heroVideo.url" controls preload="metadata" class="hero-video-preview" aria-label="当前首页背景视频" />
    <p v-else>目前未配置视频，首页将显示静态封面。</p>
    <div class="hero-video-actions">
      <input ref="videoInput" type="file" accept="video/mp4,.mp4" :disabled="videoBusy" aria-label="选择首页背景视频" @change="chooseVideo">
      <a-button v-if="heroVideo.url" danger :disabled="videoBusy" @click="clearVideo">移除视频</a-button>
    </div>
    <a-progress v-if="videoBusy" :percent="videoProgress" size="small" />
    <p v-if="videoError" role="alert" class="hero-video-error">{{ videoError }}</p>
  </a-card>
  <a-divider>首页轮播图</a-divider>
  <div class="carousel">
    <div class="flex">
      <VueDraggable :disabled="disabled" ref="el" v-if="fileList" v-model="fileList" :onEnd="sortOrderFunc"
                    style="display: flex" animation="150">
        <template v-for="(file) in fileList" :key="file.id">
          <div class="preview_list">
            <div class="m-2 w-[100px] h-[100px]">
              <img :src="file.path" alt="banners" class="w-full h-full rounded-2xl">
            </div>
            <!-- 灰色遮盖层 -->
            <div class="grey_cover m-2 w-[100px] h-[100px] bg-gray-300 rounded-2xl relative">
              <div class="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 text-black flex">
                <div style="font-size: 20px;" class="text-gray-400 hover:text-black duration-300"
                     @click="handlePreview(file)">
                  <ZoomInOutlined/>
                </div>
                <div class="mx-1"></div>
                <div style="font-size: 20px;" class="text-gray-400 hover:text-black duration-300"
                     @click="deleteBannerFunc(file.id)">
                  <DeleteOutlined/>
                </div>
              </div>
            </div>
          </div>
        </template>
        <div class="flex flex-col justify-center items-center">
          <div v-if="uploading"
               class="m-2 w-[100px] h-[100px] text-gray-400 flex justify-center items-center rounded-2xl bg-gray-300">
            上传中...
          </div>
          <a-progress :show-info="false" v-if="uploading" :percent="progress ?? 0" size="small" class="ml-1"/>
        </div>
      </VueDraggable>
      <!-- 上传按钮 -->
      <a-upload
          class="ant-upload ant-upload-picture-card m-2"
          v-model="fileList"
          list-type="picture-card"
          :showUploadList="false"
          :before-upload="beforeUpload"
          v-show="fileList && fileList.length < 5"
      >
        <div class="el-upload el-upload--picture-card">
          <div class="icon">
            <PlusOutlined/>
          </div>
        </div>
      </a-upload>
    </div>
    <a-modal :open="previewVisible" :footer="null" @cancel="handleCancel">
      <template #title>
        <span style="font-size: 1rem">名称：{{ previewTitle }}</span> <br>
        <div>大小： {{ (tempImage.size / 1024).toFixed(2) }} KB</div>
        <div>上传时间： {{ tempImage.createTime }}</div>
        <div>格式： {{ tempImage.type }}</div>
        <div>上传人id： {{ tempImage.userId }}</div>
      </template>
      <img alt="example" style="width: 100%" :src="tempImage.path">
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.hero-video-card { margin-bottom: 1.5rem; }
.hero-video-preview { display: block; width: min(100%, 640px); max-height: 360px; margin: 1rem 0; background: #111; }
.hero-video-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; }
.hero-video-error { color: #c62828; }

.preview_list {
  position: relative;

  .grey_cover {
    position: absolute;
    top: 0;
    left: 0;
    width: 100px;
    height: 100px;
    opacity: 0;
  }

  &:hover .grey_cover {
    opacity: 0.8;
    transition: all 0.3s ease;
  }

}

.carousel {
  display: flex;
  justify-content: center;
}

/* 基本样式 */
.el-upload {
  display: inline-block;
  text-align: center;
  cursor: pointer;
  outline: 0;

  .icon {
    font-size: 30px;
    color: #c0ccda;
  }
}

.el-upload--picture-card {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #FAFAFA;
  border: 5px dashed #D9D9D9;
  border-radius: 6px;
  box-sizing: border-box;
  width: 100px;
  height: 100px;
  line-height: 146px;
  vertical-align: top;
  transition: all 0.3s ease;

  &:hover {
    border-color: #409EFF;
  }
}

.el-upload__input {
  display: none;
}

/* 图标样式 */
.el-icon-plus {
  width: 30px;
  height: 30px;
  font-size: 30px;
  color: #c0ccda;
}
</style>
