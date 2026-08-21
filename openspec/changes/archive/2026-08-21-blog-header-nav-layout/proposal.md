## Why

博客顶栏在桌面宽度下显得拥挤：站名「郑陆宇的个人博客」会换行，导航项又挤在左侧约三分之一区域，整体不够大气舒展，削弱个人求职站的第一印象。

## What Changes

- 顶栏站名在常用桌面宽度下**单行显示**，禁止无故换行（过窄屏可缩小字号或保留省略，但不拆成「郑陆宇的个人 / 博客」两行）。
- 顶栏导航在站名与右侧工具区之间**均匀舒展**，增大项间距或采用中间弹性布局，避免导航块只占左侧一小段。
- 不改导航条目文案与路由结构（本次不做 CMS 化菜单），仅调整 Header/Menu 布局与样式。
- 保持移动端现有折叠/抽屉行为可用；重点优化桌面与平板横屏。

## Capabilities

### New Capabilities

- `blog-header-layout`: 前台顶栏站名单行与导航舒展布局的展示要求

### Modified Capabilities

- （无）现有 `personal-site-identity` 管的是文案与去模板，不覆盖顶栏几何布局

## Impact

- 前端：`blog-frontend/kuailemao-blog` 的 Header/Menu（及必要时 MoveMenu）样式与 flex 结构
- 无后端 API / 数据库变更
- 部署后需重新构建并发布博客前台静态资源
