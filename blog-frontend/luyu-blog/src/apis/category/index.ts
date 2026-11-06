import http, { type ApiResponse } from "@/utils/http.ts";

export interface CategoryItem {
    id: number;
    categoryName: string;
    articleCount?: number;
    heroEyebrow?: string | null;
    heroTitleAccent?: string | null;
    heroTitle?: string | null;
    heroDescription?: string | null;
}

// 查询分类列表
export function categoryList() {
    return http.get<ApiResponse<CategoryItem[]>>("/category/list", {
        method: "get"
    });
}
