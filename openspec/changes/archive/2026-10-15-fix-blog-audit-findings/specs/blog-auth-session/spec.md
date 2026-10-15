## MODIFIED Requirements

### Requirement: 无效前台令牌安全失效
博客前台 MUST 将已过期、格式损坏或被可信博客 API 明确判为认证失效的令牌视为无效会话，清理其前台令牌和用户状态，并以未登录状态继续运行。网络故障、超时、取消、服务端 5xx、普通权限拒绝以及第三方接口失败 MUST NOT 单独触发令牌清理。

#### Scenario: 令牌数据无法解析
- **WHEN** 前台读取到无法解析或字段不完整的令牌数据
- **THEN** 前台清理该数据且页面初始化和后续请求不因解析异常而中断

#### Scenario: 用户信息请求拒绝令牌
- **WHEN** 可信博客用户信息接口明确返回认证失效
- **THEN** 前台清除前台令牌和用户信息并展示未登录状态
- **AND** 管理员端独立会话不受影响

#### Scenario: 有效令牌已经过期
- **WHEN** 前台恢复会话时确认令牌有效期已结束
- **THEN** 前台清理过期会话而不将其作为有效身份发送

#### Scenario: 用户信息暂时不可用
- **WHEN** 存储令牌仍在有效期内但用户信息请求发生网络故障、超时、取消或 5xx
- **THEN** 前台保留令牌及原有记住我存储方式，并进入用户信息待恢复状态
- **AND** 前台提供重试入口，不把未经确认的用户权限用于展示特权操作

## ADDED Requirements

### Requirement: Blog credentials are restricted to trusted API destinations
请求客户端 SHALL 使用解析后的 URL origin 和配置的博客 API 路径边界确定凭据目的地；只有可信博客 API 请求 SHALL 携带博客 Authorization。不可信目的地 MUST NOT 携带从 token store、客户端默认值或显式请求头继承的博客认证凭据，其响应 MUST NOT 清理博客会话。

#### Scenario: Relative and absolute trusted API requests
- **WHEN** 已登录用户请求可信 API 范围内的相对 URL 或绝对 URL
- **THEN** 请求携带有效前台身份并沿用现有认证响应处理

#### Scenario: External requests through the shared client
- **WHEN** 请求访问外域、协议相对外域、相似域名或同源非 API 路径
- **THEN** 请求不携带博客 Authorization，包括客户端默认或请求级残留值
- **AND** 外部响应即使包含与博客认证失效相同的业务码也不清理博客令牌

### Requirement: Pending session recovery preserves authorization boundaries
前台 SHALL 区分用户信息待恢复与已确认登录状态，在待恢复期间保留令牌但 MUST NOT 展示依赖未确认身份的特权操作。重试成功 SHALL 恢复用户信息及登录展示，重试确认认证失效 SHALL 按会话失效规则清理。正常登录仍 MUST 在令牌保存及用户信息加载成功后才显示登录完成。

#### Scenario: Retry recovers the session
- **WHEN** 临时失败后用户重试且可信用户信息接口成功
- **THEN** 导航栏恢复已登录状态，无需重新输入密码
- **AND** 用户权限来自本次成功返回的用户信息

#### Scenario: Permission denial does not invalidate authentication
- **WHEN** 已确认登录用户被某操作以普通权限不足拒绝
- **THEN** 前台显示权限提示并保留其登录会话
