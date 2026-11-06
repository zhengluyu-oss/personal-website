## ADDED Requirements

### Requirement: 管理后台入口仅预加载首屏依赖
管理后台生产构建 SHALL 保持页面级按需加载，入口 HTML MUST NOT 预加载全部异步页面、编辑器语言包和仅在特定功能使用的模块。构建检查 SHALL 报告入口预加载数量与初始压缩资源体积，并在预加载脚本超过 10 个或初始 JavaScript gzip 总量超过 850 KB 时失败。

#### Scenario: 构建管理后台入口
- **WHEN** 完成管理后台生产构建并检查入口 HTML
- **THEN** `modulepreload` 脚本不超过 10 个，初始 JavaScript gzip 总量不超过 850 KB，且检查给出资源清单

#### Scenario: 首次打开登录页
- **WHEN** 浏览器首次请求管理后台登录页
- **THEN** 编辑器语言包和其他未进入的业务页面脚本不在入口预加载列表中

#### Scenario: 进入编辑页面
- **WHEN** 管理员进入需要富文本编辑器的页面
- **THEN** 对应编辑器依赖按页面加载，编辑能力仍可正常使用

### Requirement: 管理后台页面标记实际使用的语言
管理后台 HTML 文档 SHALL 声明中文为页面主要语言。

#### Scenario: 浏览器读取后台入口
- **WHEN** 浏览器或辅助阅读工具读取管理后台入口 HTML
- **THEN** 根元素的 `lang` 为 `zh-CN`
