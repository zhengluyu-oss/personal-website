<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { websiteShareList, getWebsiteShare, addWebsiteShare, updateWebsiteShare, deleteWebsiteShares } from '~/api/blog/website-share'
import { uploadArticleImage, uploadCover } from '~/api/blog/article'
import { compressImage } from '~/utils/CompressedImage'

interface ShareForm {
  id?: number
  title: string
  siteUrl: string
  summary: string
  coverImage: string
  content: string
  seoTitle: string
  seoDescription: string
  seoKeywords: string
  orderNum: number
  status: number
}
const emptyForm = (): ShareForm => ({ title: '', siteUrl: '', summary: '', coverImage: '', content: '', seoTitle: '', seoDescription: '', seoKeywords: '', orderNum: 1, status: 0 })
const rows = ref<ShareForm[]>([])
const form = ref<ShareForm>(emptyForm())
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const open = ref(false)
const failure = ref('')
const columns = [
  { title: '网站名称', dataIndex: 'title' },
  { title: '网站地址', dataIndex: 'siteUrl', ellipsis: true },
  { title: '排序', dataIndex: 'orderNum', width: 80 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 150 },
]
async function load() {
  loading.value = true
  failure.value = ''
  try {
    const result: any = await websiteShareList()
    if (result?.code !== 200) throw new Error(result?.msg || '加载失败')
    rows.value = result.data || []
  } catch { failure.value = '网站分享加载失败，请重试。' }
  finally { loading.value = false }
}
async function edit(id?: number) {
  form.value = emptyForm()
  if (id) {
    try {
      const result: any = await getWebsiteShare(id)
      if (result?.code !== 200 || !result.data) throw new Error('not found')
      form.value = { ...emptyForm(), ...result.data }
    } catch { message.error('无法读取这条分享'); return }
  }
  open.value = true
}
async function save() {
  if (saving.value || uploading.value) return
  if (![form.value.title, form.value.siteUrl, form.value.summary, form.value.content].every(value => value?.trim())) {
    message.warning('请填写网站名称、网址、简介和正文'); return
  }
  try {
    const url = new URL(form.value.siteUrl)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('invalid url')
  } catch { message.warning('请填写有效的 HTTP 或 HTTPS 网站地址'); return }
  saving.value = true
  try {
    const result: any = await (form.value.id ? updateWebsiteShare(form.value) : addWebsiteShare(form.value))
    if (result?.code !== 200) throw new Error(result?.msg || '保存失败')
    message.success('保存成功')
    open.value = false
    await load()
  } catch (error) { message.error(error instanceof Error ? error.message : '保存失败，请重试') }
  finally { saving.value = false }
}
function remove(id: number) {
  Modal.confirm({ title: '确认删除这条网站分享？', content: '删除后前台将不再显示。', onOk: async () => {
    const result: any = await deleteWebsiteShares([id])
    if (result?.code !== 200) throw new Error(result?.msg || '删除失败')
    message.success('已删除')
    await load()
  } })
}
async function uploadImage(file: File, cover = false) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('请选择 JPG、PNG 或 WebP 图片')
  const compressed = await compressImage(file)
  const image = compressed instanceof File ? compressed : new File([compressed], file.name, { type: compressed.type || file.type })
  if (cover && image.size > 300 * 1024) throw new Error('封面压缩后仍超过 300KB')
  const data = new FormData()
  data.append(cover ? 'articleCover' : 'articleImage', image, image.name)
  const result: any = await (cover ? uploadCover(data) : uploadArticleImage(data))
  if (result?.code !== 200 || !result.data) throw new Error('图片上传失败')
  return String(result.data)
}
async function chooseCover(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try { form.value.coverImage = await uploadImage(file, true) }
  catch (error) { message.error(error instanceof Error ? error.message : '上传失败') }
  finally { uploading.value = false; input.value = '' }
}
async function uploadBodyImages(files: File[], callback: (urls: string[]) => void) {
  uploading.value = true
  try { callback(await Promise.all(files.map(file => uploadImage(file)))) }
  catch (error) { message.error(error instanceof Error ? error.message : '上传失败') }
  finally { uploading.value = false }
}
onMounted(load)
</script>

<template>
  <page-container>
    <a-card title="网站分享" :bordered="false">
      <a-space style="margin-bottom:16px"><a-button type="primary" @click="edit()">新增分享</a-button><a-button @click="load">刷新</a-button></a-space>
      <a-alert v-if="failure" :message="failure" type="error" show-icon style="margin-bottom:16px" />
      <a-table :columns="columns" :data-source="rows" :loading="loading" row-key="id" :scroll="{ x: 720 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'"><a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.status === 1 ? '公开' : '草稿' }}</a-tag></template>
          <template v-if="column.key === 'actions'"><a-button type="link" @click="edit(record.id)">编辑</a-button><a-button type="link" danger @click="remove(record.id)">删除</a-button></template>
        </template>
      </a-table>
    </a-card>
    <a-drawer v-model:open="open" :title="form.id ? '编辑网站分享' : '新增网站分享'" width="min(1100px, 100vw)" :mask-closable="false" :keyboard="!saving">
      <template #extra><a-button type="primary" :loading="saving" :disabled="uploading" @click="save">保存</a-button></template>
      <a-form layout="vertical">
        <a-form-item label="网站名称 / 分享标题" required><a-input v-model:value="form.title" :maxlength="150" /></a-form-item>
        <a-form-item label="网站地址" required><a-input v-model:value="form.siteUrl" placeholder="https://example.com/" :maxlength="500" /></a-form-item>
        <a-form-item label="简介" required><a-textarea v-model:value="form.summary" :rows="3" :maxlength="500" show-count /></a-form-item>
        <a-form-item label="封面（建议 16:10，JPG / PNG / WebP）"><a-input v-model:value="form.coverImage" placeholder="图片地址，也可以在下方上传" :maxlength="500" /><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="uploading" style="margin-top:12px" @change="chooseCover" /><p v-if="uploading">图片上传中…</p><img v-if="form.coverImage" :src="form.coverImage" alt="封面预览" style="display:block;width:240px;max-width:100%;margin-top:12px" /></a-form-item>
        <a-form-item label="完整正文（Markdown）" required><MdEditor v-model="form.content" style="height:520px" @on-upload-img="uploadBodyImages" /></a-form-item>
        <a-row :gutter="20"><a-col :span="12"><a-form-item label="排序（越小越靠前）"><a-input-number v-model:value="form.orderNum" /></a-form-item></a-col><a-col :span="12"><a-form-item label="状态"><a-select v-model:value="form.status" :options="[{ label: '草稿', value: 0 }, { label: '公开', value: 1 }]" /></a-form-item></a-col></a-row>
        <a-collapse><a-collapse-panel key="seo" header="搜索引擎信息（可选）"><a-form-item label="SEO 标题"><a-input v-model:value="form.seoTitle" :maxlength="70" /></a-form-item><a-form-item label="SEO 描述"><a-textarea v-model:value="form.seoDescription" :maxlength="200" /></a-form-item><a-form-item label="SEO 关键词"><a-input v-model:value="form.seoKeywords" :maxlength="200" /></a-form-item></a-collapse-panel></a-collapse>
      </a-form>
    </a-drawer>
  </page-container>
</template>
