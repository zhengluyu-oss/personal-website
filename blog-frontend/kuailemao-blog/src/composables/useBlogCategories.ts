import { ref } from 'vue'
import { categoryList } from '@/apis/category'

export interface BlogNavCategory {
  id: number
  categoryName: string
  articleCount?: number
}

const categories = ref<BlogNavCategory[]>([])
const loaded = ref(false)
let pending: Promise<void> | null = null

export function useBlogCategories() {
  const loadCategories = () => {
    if (loaded.value) return Promise.resolve()
    if (pending) return pending
    pending = categoryList()
      .then((res: any) => {
        categories.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
      })
      .catch(() => { categories.value = [] })
      .finally(() => {
        loaded.value = true
        pending = null
      })
    return pending
  }

  return { categories, loadCategories }
}
