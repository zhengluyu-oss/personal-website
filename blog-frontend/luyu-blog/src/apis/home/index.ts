import http, { type ApiResponse } from "@/utils/http.ts";

export interface ArticleSummary {
    id: string;
    articleTitle: string;
    articleCover: string;
    articleContent: string;
    categoryName: string;
    tags: string[];
    visitCount: number;
    commentCount: number;
    likeCount: number;
    favoriteCount: number;
    createTime: string;
    updateTime: string;
}

export type ArticlePreview = Pick<ArticleSummary, 'id' | 'articleTitle' | 'articleCover' | 'createTime'> & { visitCount?: number };

// 获取首页文章列表
export function getArticleList(pageNum: number, pageSize: number) {
    return http.get<ApiResponse<{ page: ArticleSummary[]; total: number }>>('/article/list', { params: { pageNum, pageSize } });
}

// 获取推荐文章列表
export function getRecommendArticleList() {
    return http<ApiResponse<ArticleSummary[]>>({
        url: '/article/recommend',
        method: 'get'
    })
}

// 获取随机文章
export function getRandomArticle() {
    return http<ApiResponse<ArticlePreview[]>>({
        url: '/article/random',
        method: 'get'
    })
}

// 相关推荐(按照分类)
export function getRelatedArticle(categoryId: string, articleId: string) {
    return http<ApiResponse<ArticlePreview[]>>({
        url: `/article/related/${categoryId}/${articleId}`,
        method: 'get'
    })
}
