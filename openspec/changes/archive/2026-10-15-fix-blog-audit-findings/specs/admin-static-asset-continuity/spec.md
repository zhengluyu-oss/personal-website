## ADDED Requirements

### Requirement: Admin assets resolve through current and previous releases
Nginx SHALL 让后台静态资源进入专用匹配规则，不被 `/admin/` 页面回退规则遮蔽。每个合法静态资源请求 SHALL 按当前版、上一版的顺序查找，最终缺失 MUST 返回 404 而不是后台 HTML。资源响应 SHALL 使用正确 MIME 类型，哈希资源可长期缓存；入口 HTML MUST 不缓存，后台页面深链接 SHALL 保持 SPA 导航能力。发布 SHALL 只保留约定的一版 previous，不无限积累版本。

#### Scenario: Current asset exists
- **WHEN** 请求的后台 JS 或 CSS 存在于当前版
- **THEN** 返回当前文件及正确 MIME 类型，不回退到上一版或入口 HTML

#### Scenario: An open admin tab requests its older chunk
- **WHEN** 已打开的后台页面请求只存在于上一版的哈希分包
- **THEN** 返回上一版资源及正确 MIME 类型，使这次升级期间的分包仍可加载

#### Scenario: Asset is missing from both releases
- **WHEN** JS、CSS 或其他受规则保护的静态资源在当前和上一版均不存在
- **THEN** 返回 404，不返回状态 200 的后台入口 HTML

#### Scenario: Admin deep link and entry caching
- **WHEN** 用户访问后台文章等合法页面深链接或后台入口
- **THEN** 深链接返回当前后台入口以支持 SPA 路由
- **AND** HTML 响应禁用缓存，哈希资源仍使用其资源缓存策略
