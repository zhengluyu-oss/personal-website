## Why

用户端首页 Hero 已有影像感与个人站气质，但正文区与导航仍是冷灰蓝 SaaS / 文档站口音（`#155eef`、图标密导航、组件库默认观感），管理端 workspace 又沿用另一套蓝系 token，整站上下半页与前后台割裂。用户目标是「美观大气、一眼有记忆点、不花哨」——先统一视觉气质，再谈首页模块扩容。

## What Changes

- 为用户端与管理端定义并落地一套共享的「陆屿」视觉 token（色、字、圆角、阴影、线），替换当前偏企业蓝的默认强调色与杂色残留。
- 打通 Hero（暗色影像）到正文（浅色画布）的过渡，避免硬切成另一套产品。
- 收敛顶栏视觉噪音（图标/密度），强化品牌字标，搜索与登录降为次要操作样式；不改路由与信息架构本身。
- 清理与新气质冲突的遗留样式（粉紫 footer 渐变、彩虹滚动条、搜索图标异色、局部 Georgia / 系统字体混用等）。
- 管理端登录页与 workspace 口音对齐用户端品牌（色与字），不改后台信息架构与表格交互逻辑。
- 不在本变更中重排首页模块、不改权限门控规则、不引入花哨动效堆栈。

## Capabilities

### New Capabilities

- `site-visual-identity`: 跨用户端与管理端的共享视觉口音：token 映射、管理端 workspace/登录页品牌对齐、遗留杂色与冲突字体清理、气质验收清单。

### Modified Capabilities

- `blog-personal-brand-system`: 强化「影像感个人站」气质——统一强调色与画布、Hero 到正文过渡、字体层级；纠正偏 SaaS 蓝与花哨遗留装饰。
- `blog-header-layout`: 强化品牌字标层级，收敛导航图标噪音与次要操作（搜索/登录）视觉权重，保持现有目的地可达与可读性。

## Impact

- 用户端：`blog-frontend/luyu-blog` 的 `theme.scss` / 全局样式、Header、Home Brand→Main 过渡、与 brand token 强耦合的公共组件与少数页面局部色。
- 管理端：`blog-frontend/luyu-admin` 的 `workspace.scss`、登录页表面样式；Ant Design 主题色映射到新 accent。
- 无后端 API / 权限 / 路由变更；无 **BREAKING** 行为变更。
- 可能新增或调整自托管字体引用（避免生产环境外链 Google Fonts）。
