# single-banner-hero Specification

## Purpose
Requirements maintained by OpenSpec changes for single-banner-hero.
## Requirements
### Requirement: 首页只展示一张固定 Banner
系统 SHALL 从 Banner 接口选择第一张非空图片作为首页唯一静态封面。配置有效首页视频时，系统 SHALL 在封面之上展示该视频；MUST NOT 将图片列表轮播或持续缩放。

#### Scenario: 接口返回多张图片
- **WHEN** Banner 接口返回多张有效图片
- **THEN** 首页仅选择第一张有效图片作为首屏封面

#### Scenario: 接口暂时无图片
- **WHEN** Banner 接口为空或失败
- **THEN** 显示品牌占位，介绍和导航仍可使用，不产生未定义变量异常

### Requirement: 单图英雄区具有稳定视觉层级
系统 SHALL 在导航下方提供覆盖视口宽度和可用视口高度的首屏媒体区域，使用封面图片或已配置的视频为背景，标题、职业定位、说明和按钮叠加于可读的前景层。媒体比例异常时 SHALL 使用受控裁切，不得引起明显布局跳动、遮挡文案或横向滚动。

#### Scenario: 桌面端查看首页
- **WHEN** 用户在桌面视口打开首页
- **THEN** 媒体占满首屏背景，姓名、职业方向和行动入口可在视频或封面之上清晰识别，且无需等待媒体加载

#### Scenario: 移动端查看首页
- **WHEN** 用户在 360px、390px、430px 或 640px 视口打开首页
- **THEN** 媒体铺满可用首屏、文案完整可用，页面不产生横向滚动

#### Scenario: Banner 尺寸或比例异常
- **WHEN** Banner 图片或视频具有超宽、超高或未知固有尺寸
- **THEN** 媒体容器预留稳定尺寸并使用明确的裁切规则，不撑开页面且不造成明显布局跳动

### Requirement: 动效克制且尊重辅助偏好
系统 SHALL 保持封面图片静态；仅在配置视频且访客允许动态效果时播放背景视频，不渲染粒子、呼吸按钮或滚动提示装饰。

#### Scenario: 用户启用减少动画
- **WHEN** 系统报告 `prefers-reduced-motion: reduce`
- **THEN** 视频不自动播放、关闭位移动画，保留静态封面与可操作入口
