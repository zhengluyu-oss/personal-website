# blog-personal-brand-system Specification

## Purpose
Requirements maintained by OpenSpec changes for blog-personal-brand-system.
## Requirements
### Requirement: 公开页面共享统一品牌系统
系统 SHALL 使用统一的语义色彩、字体层级、间距、圆角、阴影和交互状态呈现全部公开页面，并在明暗主题下保持一致的品牌识别与可读性。语义强调色 SHALL 属于同一暖色 ember 族（明暗主题一致），画布 SHALL 使用近中性纸色/墨色对，且 MUST NOT 将冷灰蓝 SaaS 默认色（如 `#155eef` 主强调与蓝灰画布）作为亮色主题的默认品牌口音。

#### Scenario: 访问任意公开页面
- **WHEN** 访客在明色或暗色主题下打开任意公开路由
- **THEN** 导航、页面背景、内容容器、卡片、按钮和状态反馈使用一致的品牌规则且正文满足可读对比度
- **AND** 主操作与活动指示使用共享 ember 强调色语义而非无关的默认蓝主色

### Requirement: 页面适配求职阅读路径
系统 SHALL 让访客在首页首屏识别站主姓名、后端与全栈职业方向、博客价值和主要行动入口，并从后续内容快速核验代表文章、工作经历、关于与联系方式，同时保持博客阅读为主要用途。

#### Scenario: 招聘者首次访问首页
- **WHEN** 招聘者打开首页并浏览首屏
- **THEN** 页面无需依赖泛化欢迎语或风景图即可明确传达站主身份、职业定位、技术输出以及博客和工作经历入口

#### Scenario: 招聘者继续浏览首页
- **WHEN** 招聘者浏览首屏后的代表内容、最新文章和工作经历
- **THEN** 每个区块使用真实数据和明确详情入口支持快速核验，且不会被无语义装饰或重复大型卡片分散注意力

### Requirement: 全部公开页面具有明确移动布局
系统 MUST 在小于 768px 的视口将多栏和不对称结构折叠为可读单列，保证触控目标、正文行宽、图片、长文本和表单不发生横向溢出。

#### Scenario: 手机浏览公开页面
- **WHEN** 访客以 360px、375px、390px 或 430px 宽度浏览任意公开页面
- **THEN** 页面不存在横向滚动，核心操作无需悬停即可理解，图片和长文本完整收纳在视口内且触控目标可用

### Requirement: 视觉动效尊重性能与辅助偏好
系统 SHALL 仅使用有助于层级、反馈或状态转换的轻量动效，并在减少动态效果偏好下停用非必要动画。

#### Scenario: 用户启用减少动态效果
- **WHEN** 系统报告 `prefers-reduced-motion: reduce`
- **THEN** 自动播放装饰、位移和视差效果停止，但内容和操作保持完整

### Requirement: 内容状态完整
系统 SHALL 为异步内容提供统一且可读的加载、空数据和错误状态。

#### Scenario: 页面数据为空或请求失败
- **WHEN** 任意公开内容接口返回空数据或错误
- **THEN** 页面显示符合当前上下文的友好状态与可恢复入口，而不是空白区域或破碎布局

### Requirement: 重设计保持信息架构和 SEO
系统 MUST 保持既有公开 URL、主要导航语义、固定页面 TDK 和文章后台 TDK 配置能力。

#### Scenario: 改版后访问既有链接
- **WHEN** 访客或搜索引擎访问改版前已存在的公开 URL
- **THEN** 路由与页面内容继续可用且适用的 TDK 按既有配置输出

### Requirement: Hero to content transition stays in one mood
The blog home SHALL provide a visible visual transition from the immersive hero media into the light content canvas so the first scroll does not feel like switching to an unrelated product theme, while keeping the existing hero media and primary CTAs functional.

#### Scenario: Visitor scrolls from hero into home content
- **WHEN** a visitor on the home page scrolls from the hero stage into the first content sections
- **THEN** the handoff SHALL use brand-canvas-aligned shading or a short transition band
- **AND** the first content shell SHALL use the shared brand tokens rather than a hard jump to unrelated blue SaaS chrome

### Requirement: Typography stack stays coherent
Public pages SHALL use one primary UI typeface family for body and section headings, MAY use weight and size for hierarchy, and MUST NOT mix leftover editorial serif rules (such as Georgia section titles) into the default brand path alongside the primary family.

#### Scenario: Home and writing sections share type family
- **WHEN** a visitor views home content sections and typical writing list chrome
- **THEN** headings and body SHALL resolve through the primary brand font stack
- **AND** default styles MUST NOT apply conflicting serif display rules on those section titles

### Requirement: Accent usage is singular and restrained
Interactive emphasis on public pages (links, primary buttons, active nav underline, focus rings) SHALL use the shared brand accent semantic and MUST NOT introduce a second competing accent color for routine chrome (for example a green search glyph against a blue primary).

#### Scenario: Header utilities match accent family
- **WHEN** a visitor views the header search and login controls
- **THEN** those controls SHALL use ink/neutral or shared accent colors
- **AND** they MUST NOT use an unrelated saturated accent that fights the primary brand accent

