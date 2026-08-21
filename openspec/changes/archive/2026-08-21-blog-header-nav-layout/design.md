## Context

博客前台顶栏在 `kuailemao-blog` 的 `Header/Menu` 中实现。当前 `#blog-info` 固定约 `120px`，站名「郑陆宇的个人博客」易换行；`.menus_items` 使用 `justify-content: left` 且每项固定约 `100px`，导航整体贴在左侧，视觉上只占顶栏左侧约三分之一。右侧为主题切换与搜索等工具区。

约束：只改展示层布局/样式；不改菜单项与路由；不引入后台 CMS 导航。

## Goals / Non-Goals

**Goals:**

- 桌面宽度下站名单行显示
- 导航在站名与右侧工具区之间舒展分布，间距更大气
- 小屏不破坏现有移动菜单行为

**Non-Goals:**

- 不增删导航入口、不做后台可配菜单
- 不改首页 Hero、粒子背景、毒鸡汤打字机
- 不改管理端布局

## Decisions

1. **三区 flex 布局（站名 | 导航弹性区 | 工具区）**  
   - 站名：`flex: 0 0 auto` + `white-space: nowrap`，必要时略减字号，去掉过窄的固定宽度导致换行。  
   - 导航：`flex: 1`，`justify-content: space-evenly` 或增大 `gap`/`padding`，让项分布填满中间区域。  
   - 工具区保持右对齐。  
   - 备选曾考虑「导航整体居中绝对定位」——易与站名重叠，弃用。

2. **仅改 CSS/少量结构 class，不改菜单数据**  
   - 改动集中在 `Menu/index.vue`（及若共享样式的 `MoveMenu`）。  
   - 比抽配置文件更快、风险更低。

3. **断点策略**  
   - `≥768px`（或现有桌面断点）应用舒展布局。  
   - 更窄宽度沿用现有汉堡/抽屉，不强行挤满一行。

## Risks / Trade-offs

- [站名过长 + 窄桌面] → 单行可能挤压导航 → Mitigation：中等宽度降低站名字号或限制最大宽度并用省略号，优先保证不换行。  
- [space-evenly 在项很少时过散] → Mitigation：用合理 `min/max gap` 或 `space-around` 微调。  
- [仅本地 CSS 未部署] → 需重新 build 博客前台并发布到服务器。

## Migration Plan

1. 改前台 Menu 样式并本地预览桌面/平板宽度。  
2. `pnpm build` 博客前台，部署到服务器 `blog` 目录。  
3. 回滚：还原该 Vue/SCSS 并重新部署上一版静态资源。

## Open Questions

- 无阻塞项。实现时若站名仍略挤，可再决定「略缩字号」还是「超长省略」。
