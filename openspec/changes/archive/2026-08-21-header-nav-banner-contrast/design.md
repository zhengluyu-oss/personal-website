## Context

首页轮播由 `views/Home/Images/index.vue` 全屏 `position: fixed; z-index: -9` 呈现；桌面导航在 `Header/Menu/index.vue`，`scrollTop === 0` 时加 `transparent`（背景全透明）。导航文字偏亮色，亮色 Banner 区域会造成不可读。用户选定方案 C：弱磨砂顶栏 + 轮播顶渐变 + 文字阴影。

## Goals / Non-Goals

**Goals:**

- 首页顶部（未滚动）导航标题与菜单在亮/暗轮播图上都可读
- 保留「浮在 Banner 上」的感觉，不做实心不透明白/黑死板顶栏
- 滚动后仍可读；不破坏现有隐藏/显示滚动交互

**Non-Goals:**

- 不更换轮播数据源或强制改 Banner 图
- 不改导航信息架构与路由
- 不做按图片亮度实时算色（过重）

## Decisions

1. **取消置顶「完全透明」**  
   - `&.transparent` 改为弱磨砂：如 `rgba(0,0,0,0.35–0.45)` + `backdrop-filter: blur(...)`，或等价深色半透明。  
   - 备选「永远浅色磨砂」：在深色 Banner 上对比可能不足，弃用。

2. **`Images` 顶部渐变**  
   - 在 `.imgs` 增加顶部渐变层（约 64–96px）：`linear-gradient(to bottom, rgba(0,0,0,0.55), transparent)`，`z-index` 高于图片、低于导航。  
   - 与现有全屏 `::before` 全图 0.2 遮罩并存；顶部渐变更强。

3. **文字阴影**  
   - 站点名与主菜单链接：`text-shadow: 0 1px 2px rgba(0,0,0,0.55)`（或相近），避免糊成一团。

4. **移动端**  
   - `Header/index.vue` 的 `.move_nav` 在首页顶部同样加半透明底或依赖同一视觉层，避免桌面修好、手机仍糊。

## Risks / Trade-offs

- [磨砂过深影响沉浸] → 用半透明而非不透明，可微调 alpha。  
- [渐变过高裁切画面] → 高度贴近导航高度（~50–80px）。  
- [日间/非首页页顶栏过深] → 仅加强 transparent 态与首页 Images；非首页可保持现有半透明白底逻辑。

## Migration Plan

1. 改 Menu / Images（及必要时移动端 Header）样式。  
2. 构建部署 blog。  
3. 用当前亮色 Banner 验收置顶可读性。  
4. 回滚：还原上述组件样式。

## Open Questions

- 无阻塞项。alpha/渐变高度以实现时视觉微调为准。
