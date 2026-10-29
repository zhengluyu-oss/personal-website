## ADDED Requirements

### Requirement: Tree-hole content remains readable without an image background
树洞页面 SHALL 在背景图片缺失、不可访问或未使用图片时，仍以稳定的背景和前景色显示标题、输入框、占位文字、提交按钮及弹幕内容。核心信息 MUST NOT 依赖外部背景图片成功加载才可辨认。

#### Scenario: Background asset is unavailable
- **WHEN** 访客打开 `/tree-hole`，且外部背景图片不可访问
- **THEN** 页面呈现与站点视觉一致的非空白回退背景
- **AND** 标题、输入文字、占位文字和提交控件保持清晰可读

#### Scenario: Visitor enters a tree-hole message
- **WHEN** 访客聚焦输入框并输入内容
- **THEN** 输入文字和出现的提交按钮在背景上清晰可辨
- **AND** 键盘焦点状态可见

### Requirement: Tree-hole readability works at phone and desktop widths
树洞页面 SHALL 在常见手机、平板和电脑视口保持内容可见，且 MUST 遵守现有小屏无整页横向溢出的布局要求。

#### Scenario: Narrow phone viewport
- **WHEN** 访客在 320px 或 375px 宽的视口打开树洞并聚焦输入框
- **THEN** 标题、输入框及提交按钮均位于可达区域内
- **AND** 页面不因这些控件产生整页横向滚动

#### Scenario: Desktop viewport
- **WHEN** 访客在约 1440px 宽的视口打开树洞
- **THEN** 标题、输入框和弹幕内容均可辨认
