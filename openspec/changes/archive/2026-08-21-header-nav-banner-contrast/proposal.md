## Why

首页置顶时桌面导航栏背景完全透明，浅色/高亮轮播图区域会与白色导航文字叠在一起，导致「郑陆宇的个人博客」与菜单几乎不可读。需要按方案 C 提升对比度，同时尽量保留浮在轮播上的沉浸感。

## What Changes

- 首页顶部不再使用完全透明导航：改为弱磨砂/半透明深色底 + `backdrop-filter`
- 首页全屏轮播（`Images`）顶部增加沿导航高度的黑色渐变遮罩
- 导航标题与主菜单项增加轻微文字阴影，增强亮图下可读性
- 移动端顶栏在首页顶部同样保证可读（与桌面策略一致或等价）
- 滚动后已有半透明栏行为可保留或微调，避免回退到「看不见」

## Capabilities

### New Capabilities

- （无）

### Modified Capabilities

- `blog-header-layout`: 增加「导航在首页轮播上方始终可读」的要求；置顶透明策略改为有对比保障的磨砂/遮罩组合

## Impact

- 前台：`Header/Menu/index.vue`、`Home/Images/index.vue`（必要时 `Header/index.vue` 移动端）
- 部署：仅 blog 前端；不改后端与包名
