<script setup lang="ts">
import 'md-editor-v3/lib/style.css'
import { MdEditor } from 'md-editor-v3'
import type { UploadFile } from 'ant-design-vue'
import type { FileType } from 'ant-design-vue/es/upload/interface'
import { message } from 'ant-design-vue'
import type { Ref, UnwrapRef } from 'vue'
import {
  addCategory,
  addTag,
  articleCategory,
  articleTag,
  deleteCover,
  getArticle,
  publishArticle,
  uploadArticleImage,
  uploadCover,
} from '~/api/blog/article'
import type { CategoryType, TagType } from '~/pages/blog/essay/publish/type.ts'
import { ARTICLE_COVER_LABEL, ARTICLE_COVER_SPEC_EXTRA } from '~/pages/blog/essay/publish/article-cover-spec.ts'
import { ADMIN_PATHS } from '~/router/paths'
import { useMultiTab } from '~/stores/multi-tab.ts'
import {compressImage} from "~/utils/CompressedImage.ts";

const route = useRoute()
const router = useRouter()
const multiTab = useMultiTab()
const publishing = ref(false)
const isNarrowEditor = useMediaQuery('(max-width: 767px)')
const editorRef = ref<{ togglePreview: (visible: boolean) => void }>()
watch(isNarrowEditor, narrow => editorRef.value?.togglePreview?.(!narrow), { flush: 'post' })

const fileList = ref<UploadFile[]>([])
// 预览Base64
const previewBase64 = ref<string>()
const formData = ref({
  categoryId: undefined,
  tagId: undefined,
  articleCover: undefined,
  articleTitle: undefined,
  seoTitle: undefined,
  seoDescription: undefined,
  seoKeywords: undefined,
  articleContent: undefined,
  articleType: 1,
  isTop: 0,
  status: 1,
})

function getBase64(img: Blob, callback: (base64Url: string) => void) {
  const reader = new FileReader()
  reader.addEventListener('load', () => callback(reader.result as string))
  reader.readAsDataURL(img)
}

const VNodes = defineComponent({
  props: {
    vnodes: {
      type: Object,
      required: true,
    },
  },
  render() {
    return this.vnodes
  },
})

const inputRef = ref()
const categoryName = ref()
const tagName = ref()

// 分类
const categoryList: Ref<UnwrapRef<CategoryType[]>> = ref([])
// 标签
const tagList: Ref<TagType[]> = ref([])

onMounted(async () => {
  getFormData()
  await getCategory()
  await getTag()
})

async function getCategory() {
  const { data } = await articleCategory()
  categoryList.value = data
}
async function getTag() {
  const { data } = await articleTag()
  tagList.value = data
}

const categoryLoading = ref(false)

async function addCategoryFunc(e: MouseEvent) {
  e.preventDefault()
  if (categoryLoading.value) return
  const name = String(categoryName.value || '').trim()
  if (!name) {
    message.warn('请检查分类内容是否填写完整')
    return
  }
  categoryLoading.value = true
  try {
    const res = await addCategory({ categoryName: name })
    const id = Number(res?.data)
    if (res?.code !== 200 || !Number.isSafeInteger(id) || id <= 0) throw new Error('新增分类未返回有效编号，请刷新列表')
    categoryList.value.push({ id, categoryName: name })
    categoryName.value = ''
    inputRef.value?.focus()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '新增分类失败')
  } finally {
    categoryLoading.value = false
  }
}

const tagLoading = ref(false)

async function addTagFunc(e: MouseEvent) {
  e.preventDefault()
  if (tagLoading.value) return
  const name = String(tagName.value || '').trim()
  if (!name) {
    message.warn('请检查标签内容是否填写完整')
    return
  }
  tagLoading.value = true
  try {
    const res = await addTag({ tagName: name })
    const id = Number(res?.data)
    if (res?.code !== 200 || !Number.isSafeInteger(id) || id <= 0) throw new Error('新增标签未返回有效编号，请刷新列表')
    tagList.value.push({ id, tagName: name })
    tagName.value = ''
    inputRef.value?.focus()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '新增标签失败')
  } finally {
    tagLoading.value = false
  }
}

async function beforeUpload(file: FileType) {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp'
  if (!isJpgOrPng) {
    message.error('文件格式必须是jpg或png或webp')
    return false
  }

  // 压缩图片
  const compressedFile = await compressImage(file)
  const isLt03M = compressedFile.size / 1024 / 1024 < 0.3
  if (!isLt03M) {
    message.error('图片压缩后大于 0.3MB')
    return false
  }

  const coverFile = compressedFile instanceof File
    ? compressedFile
    : new File([compressedFile], file.name || 'cover.jpg', { type: compressedFile.type || 'image/jpeg' })

  // 使用 Ant Design UploadFile 结构，避免列表一直显示转圈
  fileList.value = [{
    uid: `${Date.now()}`,
    name: coverFile.name,
    status: 'done',
    originFileObj: Object.assign(coverFile, { uid: file.uid, lastModifiedDate: file.lastModifiedDate }),
  }]
  getBase64(coverFile, (base64Url: string) => {
    previewBase64.value = base64Url
  })

  return false
}

async function goToArticleListAfterPublish() {
  message.success('发布成功')
  const editingPath = route.fullPath
  await router.push(ADMIN_PATHS.articles)
  multiTab.close(editingPath)
}

async function cancelTemporaryCover(url: string) {
  try {
    const result = await deleteCover(url)
    if (result?.code !== 200) throw new Error(result?.msg || '临时封面清理失败')
    if (formData.value.articleCover === url) formData.value.articleCover = undefined
  } catch {
    message.warning('临时封面未确认清理，可能已被文章引用；请先检查文章列表，勿重复删除已保存封面')
  }
}

async function onFinish() {
  if (publishing.value)
    return

  if (!formData.value.articleTitle || !formData.value.categoryId || !formData.value.tagId || !formData.value.articleContent) {
    message.warn('请检查是否填写完整')
    return
  }

  if (!formData.value.articleCover && !fileList.value[0]) {
    message.warn('请上传文章封面')
    return
  }

  publishing.value = true
  try {
    if (!fileList.value[0] && formData.value.articleCover) {
      const res = await publishArticle(formData.value)
      if (res && res.code === 200)
        await goToArticleListAfterPublish()
      else
        message.error('发布失败')
      return
    }

    const coverOrigin = fileList.value[0]?.originFileObj
    if (!coverOrigin) {
      message.warn('请重新选择文章封面')
      return
    }
    const articleCover = new FormData()
    articleCover.append('articleCover', coverOrigin, coverOrigin.name)
    const res = await uploadCover(articleCover)
    if (!res || res.code !== 200) {
      message.error('上传文章封面失败')
      return
    }
    const uploadedCoverUrl = res.data
    formData.value.articleCover = uploadedCoverUrl
    try {
      const pubRes = await publishArticle(formData.value)
      if (pubRes && pubRes.code === 200)
        await goToArticleListAfterPublish()
      else {
        message.error(pubRes?.msg || '发布失败')
        await cancelTemporaryCover(uploadedCoverUrl)
      }
    }
    catch {
      await cancelTemporaryCover(uploadedCoverUrl)
    }
  }
  catch {
    message.error('发布失败')
  }
  finally {
    publishing.value = false
  }
}

async function onUploadArticleImg(files: File[], callback: (urls: string[]) => void) {
  const res = await Promise.all(
    files.map(async (file) => {
      const compressedFile = await compressImage(file)
      const imgFile = compressedFile instanceof File
        ? compressedFile
        : new File([compressedFile], file.name || 'image.jpg', { type: compressedFile.type || 'image/jpeg' })
      const form = new FormData()
      form.append('articleImage', imgFile, imgFile.name)
      const uploadRes = await uploadArticleImage(form)
      if (!uploadRes || uploadRes.code !== 200)
        throw new Error(uploadRes?.msg || '上传文章图片失败')
      return uploadRes.data
    }),
  )
  callback(res)
}

const toolbars = [
  'bold',
  'underline',
  'italic',
  '-',
  'title',
  'strikeThrough',
  'sub',
  'sup',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  'mermaid',
  'katex',
  '-',
  'revoke',
  'next',
  'save',
  '=',
  'pageFullscreen',
  'fullscreen',
  'preview',
  'htmlPreview',
  'catalog',
]

// 数据回显
function getFormData() {
  if (route.params.id) {
    getArticle(route.params.id as string).then((res) => {
      if (res.data)
        formData.value = res.data
    })
  }
}

function close() {
  multiTab.close(route.fullPath)
}
</script>

<template>
  <page-container :title="route.params.id ? '编辑文章' : '新建文章'">
    <template #extra>
      <a-button :disabled="publishing" @click="close">关闭</a-button>
      <a-button type="primary" :loading="publishing" :disabled="publishing" @click="onFinish">{{ formData.status === 3 ? '保存草稿' : route.params.id ? '保存修改' : '发布文章' }}</a-button>
    </template>
    <a-form layout="vertical" class="article-editor">
      <main class="article-editor__main">
        <section class="article-editor__writing" aria-label="文章内容">
          <a-form-item label="文章标题" required class="article-editor__title"><a-input v-model:value="formData.articleTitle" size="large" placeholder="为这篇文章起一个标题" /></a-form-item>
          <MdEditor ref="editorRef" v-model="formData.articleContent" theme="light" :preview="!isNarrowEditor" class="article-editor__body" :toolbars="toolbars as []" @onUploadImg="onUploadArticleImg" />
        </section>
        <a-collapse :bordered="false" class="article-editor__seo">
          <a-collapse-panel key="seo" header="搜索引擎设置（可选）">
            <a-form-item label="SEO标题" style="margin-right: 1rem">
              <a-input v-model:value="formData.seoTitle" :maxlength="70" show-count placeholder="留空则使用文章标题" style="width: 20em" />
            </a-form-item>
            <a-form-item label="SEO关键词" style="margin-right: 1rem">
              <a-input v-model:value="formData.seoKeywords" :maxlength="200" show-count placeholder="逗号分隔；留空则使用文章标签" style="width: 24em" />
            </a-form-item>
            <a-form-item label="SEO描述" style="margin-right: 1rem">
              <a-textarea v-model:value="formData.seoDescription" :maxlength="200" show-count :rows="2" placeholder="留空则从文章正文自动提取" style="width: 28em" />
            </a-form-item>
          </a-collapse-panel>
        </a-collapse>
      </main>
      <aside class="article-editor__settings" aria-label="发布设置">
        <h2>发布设置</h2>
        <a-form-item label="分类" style="margin-right: 1rem">
          <a-space>
            <a-select
              v-if="categoryList"
              v-model:value="formData.categoryId"
              :loading="categoryLoading"
              placeholder="选择分类"
              style="width: 15em"
              :options="categoryList.map(item => ({ value: item.id, label: item.categoryName }))"
            >
              <template #dropdownRender="{ menuNode: menu }">
                <VNodes :vnodes="menu" />
                <a-divider style="margin: 4px 0" />
                <a-space style="padding: 4px 8px">
                  <a-input ref="inputRef" v-model:value="categoryName" placeholder="添加分类" />
                  <a-button type="text" @click="addCategoryFunc">
                    <template #icon>
                      <plus-outlined />
                    </template>
                    添加
                  </a-button>
                </a-space>
              </template>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="标签" style="margin-right: 1rem">
          <a-select
            v-if="tagList"
            v-model:value="formData.tagId"
            mode="multiple"
            :loading="tagLoading"
            placeholder="选择标签"
            style="width: 15em"
            :options="tagList.map(item => ({ value: item.id, label: item.tagName }))"
          >
            <template #dropdownRender="{ menuNode: menu }">
              <VNodes :vnodes="menu" />
              <a-divider style="margin: 4px 0" />
              <a-space style="padding: 4px 8px">
                <a-input ref="inputRef" v-model:value="tagName" placeholder="添加标签" />
                <a-button type="text" @click="addTagFunc">
                  <template #icon>
                    <plus-outlined />
                  </template>
                  添加
                </a-button>
              </a-space>
            </template>
          </a-select>
        </a-form-item>
        <a-form-item label="类型" style="margin-right: 1rem">
          <a-select
            v-model:value="formData.articleType"
            style="width: 15em"
          >
            <a-select-option :value="1">
              原创
            </a-select-option>
            <a-select-option :value="2">
              转载
            </a-select-option>
            <a-select-option :value="3">
              翻译
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态" style="margin-right: 1rem">
          <a-space>
            <a-select
              v-model:value="formData.status"
              style="width: 120px"
            >
              <a-select-option :value="1">
                公开
              </a-select-option>
              <a-select-option :value="2">
                私密
              </a-select-option>
              <a-select-option :value="3">
                草稿
              </a-select-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="置顶文章" style="margin-right: 1rem">
          <a-select
            v-model:value="formData.isTop"
            style="width: 13em"
          >
            <a-select-option :value="1">
              是
            </a-select-option>
            <a-select-option :value="0">
              否
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="ARTICLE_COVER_LABEL" :extra="ARTICLE_COVER_SPEC_EXTRA" style="margin-right: 1rem">
          <a-space>
            <template v-if="previewBase64 || formData.articleCover">
              <a-popover title="预览">
                <template #content>
                  <a-image
                    :width="200"
                    :src="previewBase64 || formData.articleCover"
                  />
                </template>
                <a-upload
                  :file-list="fileList"
                  :before-upload="beforeUpload"
                  :max-count="1"
                  :show-upload-list="false"
                >
                  <a-button>
                    <PictureOutlined />
                    上传封面
                  </a-button>
                </a-upload>
              </a-popover>
            </template>
            <template v-else>
              <a-upload
                :file-list="fileList"
                :before-upload="beforeUpload"
                :max-count="1"
                :show-upload-list="{ showRemoveIcon: false }"
              >
                <a-button>
                  <PictureOutlined />
                  上传封面
                </a-button>
              </a-upload>
            </template>
          </a-space>
        </a-form-item>
      </aside>
    </a-form>
  </page-container>
</template>

<style scoped lang="scss">
.article-editor { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 24px; align-items: start; }
.article-editor__main { display: grid; gap: 16px; min-width: 0; }
.article-editor__writing, .article-editor__settings, .article-editor__seo { background: var(--workspace-surface); border: 1px solid var(--workspace-line); border-radius: 8px; }
.article-editor__writing { overflow: hidden; }
.article-editor__title { padding: 20px 20px 0; }
.article-editor__title :deep(input) { font-size: 20px; font-weight: 600; }
.article-editor__body { height: max(460px, calc(100dvh - 330px)); border: 0; border-top: 1px solid var(--workspace-line); }
.article-editor__settings { padding: 20px; min-width: 0; }
.article-editor__settings h2 { margin: 0 0 20px; font-size: 16px; font-weight: 600; }
.article-editor__settings :deep(.ant-form-item), .article-editor__seo :deep(.ant-form-item) { margin-right: 0 !important; }
.article-editor__settings :deep(.ant-select), .article-editor__settings :deep(.ant-input), .article-editor__seo :deep(.ant-input), .article-editor__seo :deep(.ant-input-textarea), .article-editor__seo :deep(.ant-input-affix-wrapper) { width: 100% !important; max-width: 100%; }
.article-editor__settings :deep(.ant-space), .article-editor__settings :deep(.ant-space-item) { min-width: 0; max-width: 100%; }
@media (max-width: 1199px) { .article-editor { grid-template-columns: minmax(0, 1fr); gap: 16px; } }
@media (max-width: 767px) {
  .article-editor__settings { padding: 16px; }
  .article-editor__title { padding: 16px 16px 0; }
  .article-editor__body { height: 480px; }
}
</style>
