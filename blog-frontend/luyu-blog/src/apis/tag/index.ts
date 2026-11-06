import http, { type ApiResponse } from "@/utils/http.ts";

// 所有标签
export function tagList() {
    return http.get<ApiResponse<{ id: number; tagName: string; articleCount?: number }[]>>("/tag/list", {
        method: "get"
    });
}
