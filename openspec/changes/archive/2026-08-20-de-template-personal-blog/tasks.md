## 1. Site identity constants

- [x] 1.1 Extend `blog-frontend/kuailemao-blog/src/config/site.ts` with About intro fields (headline, short bio, tagline) for 郑陆宇求职向文案
- [x] 1.2 Grep frontend for leftover template owner strings (`小张`, `ruyu-blog`, `kuailemao.xyz` cursor, 修仙 About 段) and list files to touch

## 2. Copy cleanup (A)

- [x] 2.1 Rewrite `views/About/index.vue` to use site constants; remove cultivation template body; reduce confetti or make non-blocking
- [x] 2.2 Fix About nav: one primary GitHub profile tile + optional repo text link (no twin identical icons)
- [x] 2.3 Update greeting helper in `utils/tool.ts` (and Welcome copy if needed) to personal brand strings

## 3. Feature trim (B)

- [x] 3.1 Stop mounting `ChargingList` in SideBar (or gate with `SHOW_CHARGING=false`); clear hardcoded donation sample array
- [x] 3.2 Remove `image.kuailemao.xyz` custom cursor rules from `styles/cursor.scss` (system default)
- [x] 3.3 Confirm music player remains hidden when `VITE_MUSIC_SERVE` empty; no new template music promo UI

## 4. Verify and ship

- [x] 4.1 Local smoke: About, home greeting, sidebar without donation, no cursor CDN requests
- [x] 4.2 Deploy blog only: `.\scripts\deploy-to-server.ps1 -Only blog`
- [x] 4.3 Checklist for admin「网站信息」: site name, avatar, intro, record number (manual, not code)
