## ADDED Requirements

### Requirement: 独立首页首屏内容
系统 SHALL 使用独立于网站名称的字段展示首页首屏眉题、主标题、副标题和简介。

#### Scenario: 配置完整首屏文案
- **WHEN** 站长在后台保存首页首屏文案
- **THEN** 首页 SHALL 展示保存后的各层级内容，且左上角网站名称保持不变

#### Scenario: 核心字段为空
- **WHEN** 首页主标题尚未配置或为空
- **THEN** 首页 SHALL 使用独立默认标题且 MUST NOT 回退为网站名称

### Requirement: 可选行动按钮
系统 SHALL 支持两组可独立配置文字和链接的首屏行动按钮。

#### Scenario: 按钮配置完整
- **WHEN** 某组按钮同时具有文字和链接
- **THEN** 首页 SHALL 显示可访问该链接的按钮

#### Scenario: 按钮配置不完整
- **WHEN** 某组按钮缺少文字或链接
- **THEN** 首页 SHALL 隐藏该按钮且不保留空白占位

### Requirement: 可选侧边说明组
系统 SHALL 支持由标签和正文组成的右侧说明组，并在小屏幕上保持可读布局。

#### Scenario: 说明组有内容
- **WHEN** 标签或正文至少一项已配置
- **THEN** 首页 SHALL 在桌面端右侧区域展示说明组并在移动端并入主内容流

#### Scenario: 说明组为空
- **WHEN** 标签和正文均为空
- **THEN** 首页 SHALL 不渲染说明组

### Requirement: 后台统一管理
系统 SHALL 在现有网站信息页面提供首页首屏字段的编辑、校验、保存和重置能力。

#### Scenario: 保存合法配置
- **WHEN** 管理员提交符合长度限制的首屏配置
- **THEN** 后端 SHALL 持久化配置并通过前台网站信息接口返回

#### Scenario: 提交超长配置
- **WHEN** 管理员提交超过字段限制的内容
- **THEN** 后台或后端 SHALL 拒绝提交并给出可理解的提示
