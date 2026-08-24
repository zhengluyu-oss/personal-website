import { SITE_TITLE } from '@/config/site'
import { PUBLIC_PATHS } from '@/router/paths'

export const constantRouter = [
    {
        path: '/',
        name: 'layout',
        component: () => import('@/views/Layout/index.vue'),
        children: [
            // 首页
            {
                path: '',
                component: () => import('@/views/Home/index.vue'),
                name: 'home',
                meta: {
                    title: SITE_TITLE,
                }
            },
            // 工作经历
            {
                path: PUBLIC_PATHS.experience,
                component: () => import('@/views/Experience/index.vue'),
                name: 'experience',
                meta: {
                    title: '工作经历',
                }
            },
            {
                path: PUBLIC_PATHS.experienceDetail,
                component: () => import('@/views/Experience/Detail.vue'),
                name: 'experienceDetail',
                meta: {
                    title: '工作经历详情',
                }
            },
            // 时间轴
            {
                path: PUBLIC_PATHS.archive,
                component: () => import('@/views/Pigeonhole/TimeLine/index.vue'),
                name: 'timeline',
                meta: {
                    title: '时间轴',
                }
            },
            // 分类
            {
                path: PUBLIC_PATHS.blog,
                component: () => import('@/views/Pigeonhole/Category/index.vue'),
                name: 'blog',
                meta: {
                    title: '个人博客',
                }
            },
            {
                path: PUBLIC_PATHS.category,
                component: () => import('@/views/Pigeonhole/Category/index.vue'),
                name: 'category',
                meta: { title: '文章分类' }
            },
            // 标签
            {
                path: PUBLIC_PATHS.tags,
                component: () => import('@/views/Pigeonhole/Tags/index.vue'),
                name: 'tags',
                meta: {
                    title: '文章标签',
                }
            },
            {
                path: PUBLIC_PATHS.tag,
                component: () => import('@/views/Pigeonhole/Tags/index.vue'),
                name: 'tag',
                meta: { title: '文章标签' }
            },
            // 树洞
            {
                path: PUBLIC_PATHS.treeHole,
                component: () => import('@/views/Amusement/TreeHole/index.vue'),
                name: 'treeHole',
                meta: {
                    title: '心灵树洞',
                }
            },
            // 留言版
            {
                path: PUBLIC_PATHS.messages,
                component: () => import('@/views/Amusement/Message/index.vue'),
                name: 'message',
                children: [
                    {
                        path: '',
                        component: () => import('@/views/Amusement/Message/MessageList/index.vue'),
                        name: 'messageList',
                        meta: {
                            title: '留言板',
                        }
                    },
                    {
                        path: PUBLIC_PATHS.message,
                        component: () => import('@/views/Amusement/Message/MessageDetail/index.vue'),
                        name: 'messageDetail',
                        meta: {
                            title: '留言详情',
                        }
                    }
                ]
            },
            // 友链
            {
                path: PUBLIC_PATHS.links,
                component: () => import('@/views/Link/index.vue'),
                name: 'link',
                meta: {
                    title: '博客友链',
                }
            },
            // 音乐
            {
                path: PUBLIC_PATHS.music,
                component: () => import('@/views/Music/index.vue'),
                name: 'music',
                meta: {
                    title: '音乐',
                }
            },
            // 关于
            {
                path: PUBLIC_PATHS.about,
                component: () => import('@/views/About/index.vue'),
                name: 'about',
                meta: {
                    title: '关于网站',
                }
            },
            // 相册
            {
                path: PUBLIC_PATHS.photos,
                component: () => import('@/views/Photo/index.vue'),
                name: 'photo',
                meta: {
                    title: '相册',
                }
            },
        ]
    },
    // 文章
    {
        path: PUBLIC_PATHS.article,
        component:
            () => import('@/views/Article/index.vue'),
        name: 'article',
        meta: {
            title: '文章详情',
        }
    },
    // 登录
    {
        path: '/auth',
        component: () => import('@/views/Welcome/index.vue'),
        name: 'welcome',
        children: [
            {
                path: PUBLIC_PATHS.login,
                component: () => import('@/views/Welcome/Login/index.vue'),
                name: 'welcome-login',
                meta: {
                    title: '用户登录',
                }
            },
            {
                path: PUBLIC_PATHS.register,
                component: () => import('@/views/Welcome/Register/index.vue'),
                name: 'welcome-register',
                meta: {
                    title: '用户注册',
                }
            },
            {
                path: PUBLIC_PATHS.reset,
                component: () => import('@/views/Welcome/Reset/index.vue'),
                name: 'welcome-reset',
                meta: {
                    title: '重置密码',
                }
            }
        ]
    },
    // 设置
    {
        path: PUBLIC_PATHS.account,
        component: () => import('@/views/Setting/index.vue'),
        name: 'setting',
        meta: {
            title: '用户设置',
        }
    },
    // 旧地址不兼容：不存在的页面明确显示 404。
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/NotFound/index.vue'),
        name: 'notFound',
        meta: { title: '页面不存在' },
    }
]
