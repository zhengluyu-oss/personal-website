## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: 公开页面共享统一品牌系统
系统 SHALL 使用统一的语义色彩、字体层级、间距、圆角、阴影和交互状态呈现全部公开页面，并在明暗主题下保持一致的品牌识别与可读性。语义强调色 SHALL 属于同一暖色 ember 族（明暗主题一致），画布 SHALL 使用近中性纸色/墨色对，且 MUST NOT 将冷灰蓝 SaaS 默认色（如 `#155eef` 主强调与蓝灰画布）作为亮色主题的默认品牌口音。

#### Scenario: 访问任意公开页面
- **WHEN** 访客在明色或暗色主题下打开任意公开路由
- **THEN** 导航、页面背景、内容容器、卡片、按钮和状态反馈使用一致的品牌规则且正文满足可读对比度
- **AND** 主操作与活动指示使用共享 ember 强调色语义而非无关的默认蓝主色
