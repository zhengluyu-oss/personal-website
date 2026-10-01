<script setup lang="ts">
import 'md-editor-v3/lib/style.css'
import { MdEditor } from 'md-editor-v3'
import type { Ref, UnwrapRef } from 'vue'
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
  experienceProjectList, getExperienceProject, addExperienceProject, updateExperienceProject, deleteExperienceProjects,
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
  companyIntroduction?: string
  mainBusiness?: string
  projectSummary?: string
  coverImage?: string
  techStack?: string
  responsibilities?: string
  metrics?: string
  content?: string
  orderNum: number
  status: number
}

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
  { title: '操作', key: 'operation', align: 'center', width: 230 },
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
  companyIntroduction: undefined,
  mainBusiness: undefined,
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
  try {
    const res = await experienceList()
    if (res?.code === 200) tabData.value = res.data || []
  } finally {
    loading.value = false
  }
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
      companyIntroduction: undefined,
      mainBusiness: undefined,
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
  if (formData.value.isCurrent !== 1 && formData.value.endDate
    && dayjs(formData.value.endDate).isBefore(dayjs(formData.value.startDate), 'day')) {
    message.warn('结束日期不能早于开始日期')
    return
  }
  modalInfo.loading = true
  const payload = {
    ...formData.value,
    startDate: formData.value.startDate ? dayjs(formData.value.startDate).format('YYYY-MM-DD') : undefined,
    endDate: formData.value.isCurrent === 1
      ? null
      : (formData.value.endDate ? dayjs(formData.value.endDate).format('YYYY-MM-DD') : null),
  }
  const req = formData.value.id ? updateExperience(payload) : addExperience(payload)
  try {
    const res = await req
    if (res?.code === 200) {
      message.success(formData.value.id ? '修改成功' : '添加成功')
      modalInfo.open = false
      await refreshFunc()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : String(error || '保存失败'))
  } finally {
    modalInfo.loading = false
  }
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

function formatPeriod(record: Record<string, any>) {
  if (record.isCurrent === 1)
    return '至今'
  return record.endDate || '-'
}

const projectDrawer=reactive({open:false,experienceId:0,company:'',loading:false})
const projectModal=reactive({open:false,loading:false,title:'新增项目'})
const projectRows=ref<any[]>([])
const emptyProject=()=>({projectName:'',summary:'',coverImage:'',startDate:undefined,endDate:undefined,roleTitle:'',techStack:'',contributions:'',outcomes:'',content:'',orderNum:1,status:0})
const projectForm=ref<any>(emptyProject())
async function openProjects(record:Record<string,any>){projectDrawer.open=true;projectDrawer.experienceId=Number(record.id);projectDrawer.company=String(record.company || '');await refreshProjects()}
function setCurrent(checked:boolean|string|number){formData.value.isCurrent=checked===true?1:0}
async function refreshProjects(){projectDrawer.loading=true;try{const res=await experienceProjectList(projectDrawer.experienceId);if(res?.code===200)projectRows.value=res.data||[]}finally{projectDrawer.loading=false}}
async function openProjectModal(id?:string|number){projectForm.value=emptyProject();if(id){const res=await getExperienceProject(projectDrawer.experienceId,id);projectForm.value={...res.data,startDate:res.data?.startDate?dayjs(res.data.startDate):undefined,endDate:res.data?.endDate?dayjs(res.data.endDate):undefined,content:res.data?.content||''};projectModal.title='修改项目'}else projectModal.title='新增项目';projectModal.open=true}
async function saveProject(){
  if(!projectForm.value.projectName||!projectForm.value.summary){message.warn('请填写项目名称和摘要');return}
  if(projectForm.value.startDate&&projectForm.value.endDate&&dayjs(projectForm.value.endDate).isBefore(dayjs(projectForm.value.startDate),'day')){message.warn('结束日期不能早于开始日期');return}
  projectModal.loading=true
  try{
    const payload={...projectForm.value,startDate:projectForm.value.startDate?dayjs(projectForm.value.startDate).format('YYYY-MM-DD'):null,endDate:projectForm.value.endDate?dayjs(projectForm.value.endDate).format('YYYY-MM-DD'):null}
    const res=projectForm.value.id?await updateExperienceProject(projectDrawer.experienceId,payload):await addExperienceProject(projectDrawer.experienceId,payload)
    if(res?.code!==200)throw new Error(res?.msg||'保存失败')
    message.success('保存成功')
    projectModal.open=false
    await refreshProjects()
  }catch(error){message.error(error instanceof Error?error.message:String(error||'保存失败'))}
  finally{projectModal.loading=false}
}
async function removeProject(id:string|number){Modal.confirm({title:'确认删除该项目？',onOk:async()=>{const res=await deleteExperienceProjects(projectDrawer.experienceId,[id]);if(res?.code===200){message.success('删除成功');refreshProjects()}}})}
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
            <a-button type="link" style="padding: 0" @click="openProjects(record)">管理项目</a-button>
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
            @change="setCurrent"
          />
        </a-form-item>
        <a-form-item v-if="formData.isCurrent !== 1" label="结束日期">
          <a-date-picker v-model:value="formData.endDate" style="width: 100%" />
        </a-form-item>
        <a-divider orientation="left">案例展示信息</a-divider>
        <a-form-item label="公司介绍"><a-textarea v-model:value="formData.companyIntroduction" :rows="4" placeholder="简要介绍公司背景与团队环境" /></a-form-item>
        <a-form-item label="主营业务"><a-textarea v-model:value="formData.mainBusiness" :rows="4" placeholder="说明公司的核心产品和服务领域" /></a-form-item>
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
            theme="light"
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

    <a-drawer v-model:open="projectDrawer.open" :title="`${projectDrawer.company} · 项目管理`" width="min(960px, 92vw)">
      <a-space style="margin-bottom:16px"><a-button type="primary" @click="openProjectModal()">新增项目</a-button><a-button @click="refreshProjects">刷新</a-button></a-space>
      <a-table :loading="projectDrawer.loading" :data-source="projectRows" :row-key="(r:any)=>r.id" size="small" :pagination="false">
        <a-table-column title="项目" data-index="projectName" /><a-table-column title="角色" data-index="roleTitle" /><a-table-column title="排序" data-index="orderNum" width="70" />
        <a-table-column title="状态" width="90"><template #default="{record}"><a-tag :color="record.status===1?'green':'default'">{{ record.status===1?'已发布':'草稿' }}</a-tag></template></a-table-column>
        <a-table-column title="操作" width="130"><template #default="{record}"><a-button type="link" @click="openProjectModal(record.id)">编辑</a-button><a-button type="link" danger @click="removeProject(record.id)">删除</a-button></template></a-table-column>
      </a-table>
    </a-drawer>
    <a-modal v-model:open="projectModal.open" :title="projectModal.title" width="960px" :confirm-loading="projectModal.loading" :body-style="{maxHeight:'72vh',overflowY:'auto'}" @ok="saveProject">
      <a-form layout="vertical"><a-form-item label="项目名称" required><a-input v-model:value="projectForm.projectName" :maxlength="150" /></a-form-item><a-form-item label="项目摘要" required><a-textarea v-model:value="projectForm.summary" :rows="3" :maxlength="500" show-count /></a-form-item>
      <a-row :gutter="16"><a-col :span="12"><a-form-item label="项目角色"><a-input v-model:value="projectForm.roleTitle" :maxlength="100" /></a-form-item></a-col><a-col :span="6"><a-form-item label="开始日期"><a-date-picker v-model:value="projectForm.startDate" style="width:100%" /></a-form-item></a-col><a-col :span="6"><a-form-item label="结束日期"><a-date-picker v-model:value="projectForm.endDate" style="width:100%" /></a-form-item></a-col></a-row>
      <a-form-item label="封面地址（建议 16:10）"><a-input v-model:value="projectForm.coverImage" :maxlength="500" /><img v-if="projectForm.coverImage" :src="projectForm.coverImage" style="width:240px;aspect-ratio:16/10;object-fit:contain;margin-top:10px"></a-form-item>
      <a-form-item label="技术栈（每行一项）"><a-textarea v-model:value="projectForm.techStack" :rows="3" /></a-form-item><a-form-item label="个人贡献（每行一项）"><a-textarea v-model:value="projectForm.contributions" :rows="4" /></a-form-item><a-form-item label="项目成果（每行一项）"><a-textarea v-model:value="projectForm.outcomes" :rows="4" /></a-form-item>
      <a-alert message="写作建议" description="可按业务背景、职责范围、关键问题、方案取舍、成果证据和复盘组织内容；提示不会自动写入或发布。" type="info" show-icon style="margin-bottom:16px" />
      <a-form-item label="项目详情（Markdown）"><MdEditor v-model="projectForm.content" theme="light" style="height:360px" :toolbars="toolbars as []" @onUploadImg="onUploadImg" /></a-form-item>
      <a-row :gutter="16"><a-col :span="12"><a-form-item label="排序"><a-input-number v-model:value="projectForm.orderNum" :min="0" style="width:100%" /></a-form-item></a-col><a-col :span="12"><a-form-item label="状态"><a-select v-model:value="projectForm.status"><a-select-option :value="0">草稿</a-select-option><a-select-option :value="1">发布</a-select-option></a-select></a-form-item></a-col></a-row></a-form>
    </a-modal>
  </page-container>
</template>
