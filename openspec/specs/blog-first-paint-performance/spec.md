# blog-first-paint-performance Specification

## Purpose
Requirements maintained by OpenSpec changes for blog-first-paint-performance.

## Requirements

### Requirement: 关键静态资源压缩交付
生产环境 SHALL 对可压缩的 JavaScript、CSS、SVG 和 JSON 静态资源提供 gzip 压缩响应，并 SHALL 保留适用于内容协商和长期缓存的响应头。

#### Scenario: 浏览器请求压缩资源
- **WHEN** 客户端以 `Accept-Encoding: gzip` 请求生产环境的可压缩静态资源
- **THEN** 响应包含 `Content-Encoding: gzip` 与 `Vary: Accept-Encoding`，且解压后的内容与构建产物一致

#### Scenario: 带哈希静态资源被缓存
- **WHEN** 客户端请求文件名带内容哈希的 JavaScript、CSS、字体或图片
- **THEN** 响应使用长期缓存策略，而入口 HTML 使用可重新验证且不会长期固化的缓存策略

### Requirement: 首屏资源预算
博客生产构建 SHALL 对首页首次渲染所需资源设置自动化预算：初始压缩 JavaScript 总传输量不超过 700KB，初始压缩 CSS 总传输量不超过 180KB，任何单个初始 JavaScript 分块不超过 450KB；超限 SHALL 使预算检查失败。

#### Scenario: 构建产物满足预算
- **WHEN** 完成博客生产构建并运行资源预算检查
- **THEN** 检查报告列出初始资源及压缩体积，并在全部阈值内成功结束

#### Scenario: 非首屏依赖未进入初始链路
- **WHEN** 分析首页入口的静态依赖图
- **THEN** 文章编辑器、后台管理、音乐播放器、图表库和反调试逻辑不属于首页初始关键依赖

### Requirement: 首屏渐进可见
博客首页 SHALL 在网站信息、Banner 或文章接口尚未完成时先呈现稳定的页面框架与可辨识的首屏占位，非关键数据请求不得以全屏遮罩阻止访问者看到页面。

#### Scenario: 接口处于慢响应状态
- **WHEN** 首页数据接口在页面入口代码执行后持续等待
- **THEN** 导航、Banner 区域占位和基础页面背景仍可见，并且页面不被不可交互的全屏 Loading 长时间覆盖

#### Scenario: 数据返回后渐进填充
- **WHEN** 首页各数据接口分别成功返回
- **THEN** 对应内容独立替换占位，页面不因较慢的其他接口再次变为空白

### Requirement: 加载状态必然收敛
所有触发进度条或 Loading 状态的请求 SHALL 在成功、失败、取消和超时路径上成对完成清理，多个并发请求 SHALL 分别计数且不得互相提前清除状态。

#### Scenario: 首屏请求失败
- **WHEN** 网站信息或 Banner 请求失败、取消或超时
- **THEN** 该请求对应的计数被释放，进度条结束，页面滚动状态恢复，并显示可用的降级内容

#### Scenario: 并发请求交错结束
- **WHEN** 两个受跟踪请求并发执行且以不同结果和顺序结束
- **THEN** 系统仅在全部受跟踪请求结束后清除共享状态，且计数不会为负或永久残留

### Requirement: Banner 首图优先且稳定
首页 SHALL 优先发现和加载当前唯一 Banner 首图，预留稳定展示尺寸，并在图片不可用时呈现与页面风格一致的降级背景。

#### Scenario: 正常加载 Banner
- **WHEN** 浏览器打开首页且 Banner 地址有效
- **THEN** 首图得到高优先级加载提示、展示区域在图片到达前已占位，加载过程不产生明显布局偏移

#### Scenario: Banner 加载失败
- **WHEN** Banner 图片请求失败或超时
- **THEN** 首屏继续显示可读标题和降级背景，用户仍可导航与向下浏览

### Requirement: 公开站点不受反调试逻辑阻塞
公开生产博客 SHALL 默认禁用会周期性占用主线程、触发 `debugger` 或阻断正常浏览器工具的反调试逻辑；如保留开关，该能力 SHALL 明确启用且异步加载，不得进入默认首屏关键路径。

#### Scenario: 默认生产配置访问首页
- **WHEN** 使用默认生产环境变量构建并访问公开博客
- **THEN** 首屏执行路径不包含反调试定时器或 `debugger` 循环，浏览器主线程可正常完成挂载

### Requirement: 部署后性能验收
部署流程 SHALL 在切换版本后验证首页状态、关键接口、压缩响应头和关键资源预算，并 SHALL 在验收失败时报告失败项而非宣告部署成功。

#### Scenario: 线上压缩配置遗漏
- **WHEN** 新版本部署后关键 JavaScript 响应缺少 gzip 编码或体积超过预算
- **THEN** 部署验收失败并输出资源 URL、实际响应头或体积，供操作者回滚或修复

#### Scenario: 受控网络性能验证
- **WHEN** 在冷缓存、移动端视口和约 3Mbps 下行带宽条件下执行首页检查
- **THEN** 页面首次内容绘制不超过 2.5 秒、最大内容绘制不超过 4 秒且累计布局偏移不超过 0.1
