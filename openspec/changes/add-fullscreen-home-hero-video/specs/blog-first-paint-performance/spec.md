## MODIFIED Requirements

### Requirement: Banner 首图优先且稳定
首页 SHALL 优先发现和加载当前唯一 Banner 首图，将其作为全屏视频首屏的静态封面，预留稳定展示尺寸，并在图片不可用时呈现与页面风格一致的降级背景。视频文件 MUST NOT 阻塞文案和按钮的初次呈现。

#### Scenario: 正常加载 Banner
- **WHEN** 浏览器打开首页且 Banner 地址有效
- **THEN** 首图得到高优先级加载提示、展示区域在图片到达前已占位，加载过程不产生明显布局偏移；视频稍后可替换视觉层而不改变布局

#### Scenario: Banner 加载失败
- **WHEN** Banner 图片请求失败或超时
- **THEN** 首屏继续显示可读标题和降级背景，用户仍可导航与向下浏览

## ADDED Requirements

### Requirement: 首屏视频有条件加载
首页 SHALL 仅在配置视频、首屏可见、用户未启用减少动态效果且浏览器未提示省流量时请求视频；视频 MUST 独立于首页初始 JS/CSS 资源预算，并 SHALL 在不可见时暂停。

#### Scenario: 省流量或减少动态效果
- **WHEN** 浏览器提示省流量或 `prefers-reduced-motion: reduce`
- **THEN** 首页仅显示静态封面，不为自动播放下载视频主体

#### Scenario: 视频未能开始播放
- **WHEN** 视频请求、解码或自动播放失败
- **THEN** 首屏保留封面与可用文案，不显示空白视频层或永久加载状态
