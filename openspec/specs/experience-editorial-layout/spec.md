# experience-editorial-layout Specification

## Purpose
Requirements maintained by OpenSpec changes for experience-editorial-layout.

## Requirements

### Requirement: 编辑式经历索引
系统 SHALL 将每段工作经历呈现为可快速扫描且可访问的横向索引行。
#### Scenario: 浏览经历列表
- **WHEN** 用户打开工作经历页面
- **THEN** 用户 SHALL 清晰看到每段经历的编号、周期、公司、职位和关键摘要

### Requirement: 案例研究详情
系统 SHALL 使用档案侧栏与正文双栏展示经历详情。
#### Scenario: 阅读完整经历
- **WHEN** 用户进入有 Markdown 内容的经历详情
- **THEN** 页面 SHALL 同时保持核心履历信息可见并提供舒适的长文阅读宽度

### Requirement: 响应式与无障碍
页面 SHALL 支持键盘访问、小屏单列和减少动画偏好。
#### Scenario: 移动端访问
- **WHEN** 视口宽度小于 900px
- **THEN** 信息 SHALL 按自然阅读顺序折叠且无横向溢出
