## 1. Desktop header contrast

- [x] 1.1 调整 `Header/Menu/index.vue`：`transparent` 态改为弱磨砂/半透明深色底，保留 blur
- [x] 1.2 为站点名与主菜单项增加轻微 `text-shadow`（或等价可读性处理）
- [x] 1.3 确认滚动后非 transparent 态仍可读，且滚动隐藏逻辑不变

## 2. Banner top gradient

- [x] 2.1 在 `Home/Images/index.vue` 增加顶部渐变遮罩（约导航高度），增强顶区对比
- [x] 2.2 确认渐变层级在图片之上、导航之下，不挡住点击与动画

## 3. Mobile & verify

- [x] 3.1 检查/补强移动端顶栏（`Header/index.vue`）在首页顶部的可读性
- [x] 3.2 本地或线上用亮色 Banner 验收置顶桌面/移动可读；部署 blog
