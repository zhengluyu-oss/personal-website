import http, { type ApiResponse } from "@/utils/http.ts";

export interface TreeHoleItem { avatar: string; nickname: string; content: string }

// 添加树洞
export const addTreeHole = (content: String) => {
    return http.post("/treeHole/auth/addTreeHole", JSON.stringify({content}), {
        method: 'post'
    });
};

// 获取树洞列表
export const getTreeHoleList = () => {
    return http.get<ApiResponse<TreeHoleItem[]>>("/treeHole/getTreeHoleList", {
        method: 'get'
    });
};
