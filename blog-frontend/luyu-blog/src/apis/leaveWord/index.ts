import http from "@/utils/http.ts";
import type { ApiResponse } from '@/utils/http.ts';

export interface LeaveWord {
    id: number;
    userId: number;
    content: string;
    createTime: string;
    nickname: string;
    avatar: string;
    commentCount: number;
    likeCount: number;
    favoriteCount: number;
}

// 查询留言板列表
export function getLeaveWordList(id?: string | string[]) {
    return http<ApiResponse<LeaveWord[]>>({
        url: '/leaveWord/list',
        method: 'get',
        params: {
            id
        }
    })
}

// 新增留言板
export function userLeaveWord(content: string) {
    return http.post<ApiResponse<void>>('/leaveWord/auth/userLeaveWord',JSON.stringify(content))
}
