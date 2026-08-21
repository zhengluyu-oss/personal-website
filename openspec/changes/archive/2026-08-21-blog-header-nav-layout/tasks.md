## 1. Header title (no wrap)

- [x] 1.1 Adjust `#blog-info` in `Menu/index.vue`: remove undersized fixed width that forces wrap; use `white-space: nowrap` and auto width
- [x] 1.2 Tune title font-size/max-width for medium desktop so「郑陆宇的个人博客」stays one line without colliding with nav

## 2. Navigation spacing

- [x] 2.1 Restructure left header flex: brand `flex: 0 0 auto`, `.menus_items` `flex: 1` with `space-evenly` or larger gap so items fill space before the right toolbar
- [x] 2.2 Replace per-item rigid `width: 100px` + left clustering with flexible item sizing that looks spacious on desktop
- [x] 2.3 Confirm dropdown children (归档 / 其他) still open and align correctly after spacing changes

## 3. Responsive check

- [x] 3.1 Verify desktop (≥768px or existing breakpoint): title one line, nav spans middle of bar
- [x] 3.2 Verify mobile: existing collapse/hamburger behavior still works
- [x] 3.3 Align `MoveMenu` styles if it shares the same cramped layout on some breakpoints

## 4. Deploy

- [x] 4.1 Build `kuailemao-blog` and deploy static assets to the server blog directory
- [x] 4.2 Hard-refresh production homepage and confirm title + nav visually
