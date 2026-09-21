## MODIFIED Requirements

### Requirement: 首页只展示一张固定 Banner
系统 SHALL 从 Banner 接口选择第一张非空图片作为首页唯一静态介绍图，MUST NOT 轮播或持续缩放。

#### Scenario: 接口返回多张图片
- **WHEN** Banner 接口返回多张有效图片
- **THEN** 首页仅展示第一张有效图片

#### Scenario: 接口暂时无图片
- **WHEN** Banner 接口为空或失败
- **THEN** 显示浅色品牌占位，介绍和导航仍可使用，不产生未定义变量异常

### Requirement: 单图英雄区具有稳定视觉层级
系统 SHALL 将 Banner 作为浅色介绍区中的有限高度媒体，标题与按钮位于独立可读文字区，预留图片比例，不使用电影化整屏遮罩、暗角或装饰框。

#### Scenario: 桌面端查看首页
- **WHEN** 用户在桌面视口打开首页
- **THEN** 文字与图片并排，正常配置介绍区无需占满一屏，标题和按钮无需等待图片

#### Scenario: 移动端查看首页
- **WHEN** 用户在 640px 或更窄视口打开首页
- **THEN** 图文自然堆叠，图片有稳定比例，标题与按钮完整可用

### Requirement: 动效克制且尊重辅助偏好
系统 SHALL 保持首页 Banner 静态，只保留按钮必要的交互反馈，不渲染粒子、呼吸按钮或滚动提示装饰。

#### Scenario: 用户启用减少动画
- **WHEN** 系统报告 `prefers-reduced-motion: reduce`
- **THEN** 关闭位移动画，保留静态图片与可操作入口
