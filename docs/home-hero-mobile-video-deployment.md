# 首页手机端竖版视频上线说明

本次在现有电脑端视频之外增加手机端独立视频。手机端宽度不超过 767px 时优先播放竖版视频；未配置竖版视频时沿用电脑端视频，均未配置时显示原有 Banner 图片。减少动态效果和省流量模式仍使用现有播放策略。

## 上线顺序

1. 运行 [`scripts/migrate-home-hero-mobile-video.sh`](../scripts/migrate-home-hero-mobile-video.sh)，它先备份 `sys_website_info`，再执行 [`sql/home-hero-mobile-video.sql`](../sql/home-hero-mobile-video.sql)。脚本重复执行安全，新增列可空。
2. 部署后端，再部署管理端和博客前端。旧桌面视频接口保持原路径，新手机端接口位于 `/banners/back/hero-video/mobile`。
3. 在后台“首页轮播图”分别确认电脑端视频和手机端视频。上传一条竖版 MP4 后，在手机和桌面视口检查选源、播放、暂停、失败时图片封面以及移除手机视频后的回退行为。

新手机视频使用 `blog/home/hero-video/mobile/` 对象前缀，与电脑端对象分开。替换或移除其中一端的视频不会清理另一端的对象。上传限制沿用现有 MP4、100 MB 和 OSS 直传策略。

## 回滚

先在后台移除手机端视频，让手机端回退到电脑端视频，然后回滚前后台版本。数据库列可以保留，不影响旧版；若必须删除，先备份并确认手机端对象键为空。
