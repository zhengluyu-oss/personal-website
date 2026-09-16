# single-banner-hero Specification

## Purpose
Requirements maintained by OpenSpec changes for single-banner-hero.
## Requirements
### Requirement: 首页只展示一张固定 Banner
系统 SHALL 从 Banner 接口选择第一张非空图片作为首页唯一静态介绍图，MUST NOT 轮播或持续缩放。

#### Scenario: 接口返回多张图片
- **WHEN** Banner 接口返回多张有效图片
- **THEN** 首页仅展示第一张有效图片

#### Scenario: 接口暂时无图片
- **WHEN** Banner 接口为空或失败
- **THEN** 显示浅色品牌占位，介绍和导航仍可使用，不产生未定义变量异常

### Requirement: 单图英雄区具有稳定视觉层级
系统 SHALL 将 Banner 作为浅色个人介绍区中的辅助媒体，标题、职业定位、说明和按钮位于独立可读文字区，预留图片比例，不使用电影化整屏遮罩、暗角或装饰框，且图片 MUST NOT 成为比站主身份更强的视觉中心。

#### Scenario: 桌面端查看首页
- **WHEN** 用户在桌面视口打开首页
- **THEN** 文字与图片并排，姓名、职业方向和行动入口先于图片被识别，正常配置介绍区无需占满一屏，标题和按钮无需等待图片

#### Scenario: 移动端查看首页
- **WHEN** 用户在 360px、390px、430px 或 640px 视口打开首页
- **THEN** 图文自然堆叠，图片宽度不超过内容容器并具有稳定比例，标题、正文与按钮完整可用，页面不产生横向滚动

#### Scenario: Banner 尺寸或比例异常
- **WHEN** Banner 图片具有超宽、超高或未知固有尺寸
- **THEN** 媒体容器使用受控比例和明确适配规则显示图片，不撑开页面且不造成明显布局跳动

### Requirement: 动效克制且尊重辅助偏好
系统 SHALL 保持首页 Banner 静态，只保留按钮必要的交互反馈，不渲染粒子、呼吸按钮或滚动提示装饰。

#### Scenario: 用户启用减少动画
- **WHEN** 系统报告 `prefers-reduced-motion: reduce`
- **THEN** 关闭位移动画，保留静态图片与可操作入口

