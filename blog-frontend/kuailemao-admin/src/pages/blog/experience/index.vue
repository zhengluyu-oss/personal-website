<script setup lang="ts">
import 'md-editor-v3/lib/style.css'
import { MdEditor } from 'md-editor-v3'
import type { Ref, UnwrapRef } from 'vue'
import type { BasicColorMode, UseColorModeReturn } from '@vueuse/core'
import { Modal, message } from 'ant-design-vue'
import { createVNode } from 'vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import {
  addExperience,
  deleteExperienceByIds,
  experienceList,
  getExperienceById,
  updateExperience,
} from '~/api/blog/experience'
import { uploadArticleImage } from '~/api/blog/article'
import { compressImage } from '~/utils/CompressedImage.ts'

interface DataType {
  id: string | number
  company: string
  roleTitle: string
  startDate: string
  endDate?: string
  isCurrent: number
  highlights?: string
  projectSummary?: string
  coverImage?: string
  techStack?: string
  responsibilities?: string
  metrics?: string
  content?: string
  orderNum: number
  status: number
}

const mode: UseColorModeReturn<BasicColorMode> = useColorMode()
const loading = ref(false)
const tabData: Ref<UnwrapRef<DataType[]>> = ref([])
const state = reactive<{ selectedRowKeys: Array<string | number> }>({
  selectedRowKeys: [],
})

const columns: any = [
  { title: '编号', dataIndex: 'id', align: 'center', width: 70 },
  { title: '公司', dataIndex: 'company', align: 'center' },
  { title: '岗位', dataIndex: 'roleTitle', align: 'center' },
  { title: '开始', dataIndex: 'startDate', align: 'center' },
  { title: '结束', dataIndex: 'endDate', align: 'center' },
  { title: '排序', dataIndex: 'orderNum', align: 'center', width: 70 },
  { title: '状态', dataIndex: 'status', align: 'center', width: 90 },
  { title: '操作', key: 'operation', align: 'center', width: 160 },
]

const modalInfo = reactive({
  open: false,
  title: '添加工作经历',
  loading: false,
})

const formData = ref<any>({
  company: undefined,
  roleTitle: undefined,
  startDate: undefined as Dayjs | undefined,
  endDate: undefined as Dayjs | undefined,
  isCurrent: 0,
  highlights: undefined,
  projectSummary: undefined,
  coverImage: undefined,
  techStack: undefined,
  responsibilities: undefined,
  metrics: undefined,
  content: '',
  orderNum: 1,
  status: 1,
})

const toolbars = [
  'bold',
  'underline',
  'italic',
  '-',
  'title',
  'strikeThrough',
  'quote',
  'unorderedList',
  'orderedList',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  '=',
  'pageFullscreen',
  'fullscreen',
  'preview',
]

onMounted(() => {
  refreshFunc()
})

async function refreshFunc() {
  loading.value = true
  const { data } = await experienceList()
  tabData.value = data || []
  loading.value = false
}

function onSelectChange(selectedRowKeys: Array<string | number>) {
  state.selectedRowKeys = selectedRowKeys
}

async function openModal(id?: string | number) {
  if (id) {
    const { data } = await getExperienceById(id)
    formData.value = {
      ...data,
      startDate: data?.startDate ? dayjs(data.startDate) : undefined,
      endDate: data?.endDate ? dayjs(data.endDate) : undefined,
      isCurrent: data?.isCurrent ?? 0,
      status: data?.status ?? 1,
      content: data?.content ?? '',
    }
    modalInfo.title = '修改工作经历'
  }
  else {
    formData.value = {
      company: undefined,
      roleTitle: undefined,
      startDate: undefined,
      endDate: undefined,
      isCurrent: 0,
      highlights: undefined,
      projectSummary: undefined,
      coverImage: undefined,
      techStack: undefined,
      responsibilities: undefined,
      metrics: undefined,
      content: '',
      orderNum: 1,
      status: 1,
    }
    modalInfo.title = '添加工作经历'
  }
  modalInfo.open = true
}

async function onUploadImg(files: any, callback: any) {
  const res = await Promise.all(
    files.map(async (file: File) => {
      const compressedFile = await compressImage(file)
      const imgFile = compressedFile instanceof File
        ? compressedFile
        : new File([compressedFile], file.name || 'image.jpg', { type: compressedFile.type || 'image/jpeg' })
      const form = new FormData()
      form.append('articleImage', imgFile, imgFile.name)
      const uploadRes = await uploadArticleImage(form)
      if (!uploadRes || uploadRes.code !== 200)
        throw new Error(uploadRes?.msg || '上传图片失败')
      return uploadRes.data
    }),
  )
  callback(res)
}

async function modelOk() {
  if (!formData.value.company || !formData.value.roleTitle || !formData.value.startDate) {
    message.warn('请填写公司、岗位和开始日期')
    return
  }
  modalInfo.loading = true
  const payload = {
    ...formData.value,
    startDate: formData.value.startDate ? dayjs(formData.value.startDate).format('YYYY-MM-DD') : undefined,
    endDate: formData.value.isCurrent === 1
      ? undefined
      : (formData.value.endDate ? dayjs(formData.value.endDate).format('YYYY-MM-DD') : undefined),
  }
  const req = formData.value.id ? updateExperience(payload) : addExperience(payload)
  await req.then((res) => {
    if (res.code === 200) {
      message.success(formData.value.id ? '修改成功' : '添加成功')
      modalInfo.open = false
      refreshFunc()
    }
  }).finally(() => {
    modalInfo.loading = false
  })
}

function deleteRows(ids: Array<string | number>) {
  Modal.confirm({
    title: '确认删除选中的工作经历？',
    icon: createVNode(ExclamationCircleOutlined),
    onOk: async () => {
      const res = await deleteExperienceByIds(ids)
      if (res.code === 200) {
        message.success('删除成功')
        state.selectedRowKeys = []
        refreshFunc()
      }
    },
  })
}

function formatPeriod(record: DataType) {
  if (record.isCurrent === 1)
    return '至今'
  return record.endDate || '-'
}
</script>

<template>
  <page-container>
    <a-card title="工作经历" :bordered="false">
      <a-space style="margin-bottom: 16px">
        <a-button type="primary" @click="openModal()">
          新增
        </a-button>
        <a-button danger :disabled="!state.selectedRowKeys.length" @click="deleteRows(state.selectedRowKeys)">
          删除
        </a-button>
        <a-button @click="refreshFunc">
          刷新
        </a-button>
      </a-space>
      <a-table
        :columns="columns"
        :data-source="tabData"
        :loading="loading"
        :row-selection="{ selectedRowKeys: state.selectedRowKeys, onChange: onSelectChange }"
        :row-key="(record: DataType) => record.id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'endDate'">
            {{ formatPeriod(record) }}
          </template>
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'operation'">
            <a-button type="link" style="padding: 0" @click="openModal(record.id)">
              修改
            </a-button>
            <a-button type="link" danger style="padding: 0; margin-left: 8px" @click="deleteRows([record.id])">
              删除
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalInfo.open"
      :title="modalInfo.title"
      :confirm-loading="modalInfo.loading"
      width="920px"
      :body-style="{ maxHeight: '70vh', overflowY: 'auto' }"
      @ok="modelOk"
    >
      <a-form layout="vertical">
        <a-form-item label="公司" required>
          <a-input v-model:value="formData.company" placeholder="公司名称" :maxlength="100" />
        </a-form-item>
        <a-form-item label="岗位" required>
          <a-input v-model:value="formData.roleTitle" placeholder="岗位名称" :maxlength="100" />
        </a-form-item>
        <a-form-item label="开始日期" required>
          <a-date-picker v-model:value="formData.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="是否至今">
          <a-switch
            :checked="formData.isCurrent === 1"
            checked-children="至今"
            un-checked-children="已结束"
            @change="(checked: boolean) => formData.isCurrent = checked ? 1 : 0"
          />
        </a-form-item>
        <a-form-item v-if="formData.isCurrent !== 1" label="结束日期">
          <a-date-picker v-model:value="formData.endDate" style="width: 100%" />
        </a-form-item>
        <a-divider orientation="left">案例展示信息</a-divider>
        <a-form-item label="案例定位">
          <a-textarea v-model:value="formData.projectSummary" :rows="2" :maxlength="500" show-count placeholder="一句话说清这段经历解决了什么问题、创造了什么价值" />
        </a-form-item>
        <a-form-item label="案例封面图">
          <a-input v-model:value="formData.coverImage" :maxlength="500" placeholder="填写已上传图片的完整地址，建议比例 16:10" />
          <img v-if="formData.coverImage" :src="formData.coverImage" alt="案例封面预览" style="width: 220px; max-height: 140px; object-fit: cover; margin-top: 12px; border-radius: 6px">
        </a-form-item>
        <a-form-item label="技术栈（每行一项）">
          <a-textarea v-model:value="formData.techStack" :rows="3" placeholder="Vue 3&#10;Spring Boot&#10;MySQL" />
        </a-form-item>
        <a-form-item label="核心职责（每行一项）">
          <a-textarea v-model:value="formData.responsibilities" :rows="4" placeholder="负责核心架构设计&#10;推进接口性能治理" />
        </a-form-item>
        <a-form-item label="量化成果（每行：数值|说明）">
          <a-textarea v-model:value="formData.metrics" :rows="4" placeholder="40%|接口平均响应时间降低&#10;99.9%|核心服务可用性" />
        </a-form-item>
        <a-form-item label="列表摘要（每行一条，展示在时间线）">
          <a-textarea v-model:value="formData.highlights" :rows="4" placeholder="例：&#10;负责后端接口开发&#10;参与系统性能优化" />
        </a-form-item>
        <a-form-item label="详情正文（Markdown，可插图）">
          <MdEditor
            v-model="formData.content"
            :theme="mode"
            style="height: 360px"
            :toolbars="toolbars as []"
            @onUploadImg="onUploadImg"
          />
        </a-form-item>
        <a-form-item label="排序（越小越靠前）">
          <a-input-number v-model:value="formData.orderNum" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status" style="width: 100%">
            <a-select-option :value="1">
              启用
            </a-select-option>
            <a-select-option :value="0">
              停用
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </page-container>
</template>
