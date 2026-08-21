## Why

个人站目前仍大量保留开源模板（Ruyu/kuailemao）的文案、假数据与演示向功能，求职场景下一眼像「刚从 GitHub 拷下来的博客」。需要在前台去模板化，突出郑陆宇的个人品牌与真实内容入口。

## What Changes

- 替换关于页、欢迎/问候等硬编码模板文案为个人求职向文案（可配置常量）
- 清空或移除打赏榜假数据与「开源/群主」类话术；默认隐藏打赏侧栏模块
- 去掉仍指向旧图床 `image.kuailemao.xyz` 的自定义鼠标样式等模板资源
- 隐藏或降级明显演示向入口（如音乐播放器在无配置时已隐藏的基础上，进一步收敛菜单/侧栏中的空壳模块展示策略）
- 统一站点展示名、外链与 About 导航，避免双 GitHub 图标等模板拼凑感
- **不**改 Java 包名、目录名 `kuailemao-*`（保持 Plan 1 品牌层改造范围）
- **不**强制清空数据库种子；后台「网站信息」需运维在管理端自行改成个人资料（任务中给出检查清单）

## Capabilities

### New Capabilities
- `personal-site-identity`: 前台个人站身份与去模板化展示（文案、假数据清理、演示模块可见性）

### Modified Capabilities
- （无）现有 `file-storage` 等能力无需求变更

## Impact

- 主要影响：`blog-frontend/kuailemao-blog`（About、Welcome、Footer 相关文案工具、侧栏 ChargingList、cursor 样式、部分菜单/布局）
- 可能触及：`src/config/site.ts` 扩展字段；部署后需重新 `deploy` 博客前端
- 管理端可不改或仅文档提示更新网站信息；后端 API 无 **BREAKING** 变更
