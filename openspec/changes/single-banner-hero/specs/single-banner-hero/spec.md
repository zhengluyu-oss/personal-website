## ADDED Requirements

### Requirement: 首页只展示一张固定 Banner
系统 SHALL 从 Banner 接口返回值中选择第一张有效图片作为首页唯一背景，并且 MUST NOT 对它执行轮播、淡入淡出或持续缩放动画。

#### Scenario: 接口返回多张图片
- **WHEN** Banner 接口返回两张或更多有效图片
- **THEN** 首页只渲染第一张有效图片且长期保持可见

#### Scenario: 接口暂时无图片
- **WHEN** Banner 接口返回空列表或请求失败
- **THEN** 首页展示可读的深色渐变后备背景而不是空白区域

### Requirement: 单图英雄区具有稳定视觉层级
系统 SHALL 使用静态遮罩、暗角、局部光晕和底部渐变建立电影感，并保证网站名称、动态短句和向下按钮清晰可读。

#### Scenario: 桌面端查看首页
- **WHEN** 用户在桌面视口打开首页
- **THEN** 图片覆盖首屏，品牌信息位于明确焦点区域且底部平滑衔接正文，不显示波浪

#### Scenario: 移动端查看首页
- **WHEN** 用户在 640px 或更窄视口打开首页
- **THEN** 图片保持合理构图，标题不溢出，动态短句和向下按钮保持可用

### Requirement: 动效克制且尊重辅助偏好
系统 SHALL 仅保留非干扰性的短句光标、粒子和向下按钮提示，并在减少动画偏好下关闭非必要动画。

#### Scenario: 用户启用减少动画
- **WHEN** 系统报告 `prefers-reduced-motion: reduce`
- **THEN** 首页 Banner 保持静态并关闭按钮呼吸、光标闪烁等非必要动画
