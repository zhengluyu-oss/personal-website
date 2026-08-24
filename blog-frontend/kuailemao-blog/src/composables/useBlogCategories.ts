import { ref } from 'vue'
import { categoryList } from '@/apis/category'
import type { CategorySlugEntry } from '@/utils/category-slug'

export interface BlogNavCategory {
  id: number
  categoryName: string
  articleCount?: number
}

const categories = ref<BlogNavCategory[]>([])
const categoryEntries = ref<CategorySlugEntry<BlogNavCategory>[]>([])
const loaded = ref(false)
let pending: Promise<void> | null = null

export function useBlogCategories() {
  const loadCategories = () => {
    if (loaded.value) return Promise.resolve()
    if (pending) return pending
    pending = categoryList()
      .then(async (res: any) => {
        categories.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
        if (categories.value.length) {
          const { buildCategorySlugEntries } = await import('@/utils/category-slug')
          categoryEntries.value = buildCategorySlugEntries(categories.value)
        } else {
          categoryEntries.value = []
        }
      })
      .catch(() => { categories.value = []; categoryEntries.value = [] })
      .finally(() => {
        loaded.value = true
        pending = null
      })
    return pending
  }

  return { categories, categoryEntries, loadCategories }
}
