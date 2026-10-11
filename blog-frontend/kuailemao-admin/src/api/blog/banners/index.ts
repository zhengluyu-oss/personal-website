// 上传banner
import {message} from "ant-design-vue";

export async function uploadBanner(data: any, handleProgress: any) {
  return usePost('/banners/upload/banner', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: handleProgress,
  })
}

// 查询banner列表
export async function backGetBanners() {
  return useGet('/banners/back/list')
}

// 删除banner
export async function deleteBanner(bannerId: any) {
  return useDelete(`/banners/${bannerId}`)
}

// 修改顺序
export async function updateOrder(data: any) {
  return usePut('/banners/update/sort/order', JSON.stringify(data)).catch(resp => message.error(resp))
}

export interface HeroVideoTicket {
  uploadUrl: string
  objectKey: string
  accessKeyId: string
  policy: string
  signature: string
  contentType: string
  maxBytes: number
}

export interface HeroVideoState { url: string | null; objectKey: string | null }

export async function getHeroVideo() { return useGet('/banners/back/hero-video') }
export async function issueHeroVideoTicket() { return usePost('/banners/back/hero-video/ticket', {}) }
export async function confirmHeroVideo(objectKey: string) {
  return usePost('/banners/back/hero-video/confirm', {objectKey})
}
export async function removeHeroVideo() { return useDelete('/banners/back/hero-video') }
