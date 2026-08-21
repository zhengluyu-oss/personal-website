## 1. Database and permissions

- [x] 1.1 Add `sql/work-experience.sql` for `t_work_experience` (company, role, dates, ongoing flag, highlights, order, status, soft delete)
- [x] 1.2 Insert `sys_menu` / `sys_permission` / role grants for admin「工作经历」(use ids above current MAX)

## 2. Backend API

- [x] 2.1 Add entity/DTO/VO/mapper for work experience
- [x] 2.2 Implement service: public enabled list; admin CRUD + reorder/status
- [x] 2.3 Add `WorkExperienceController` with public `GET /experience/list` and authenticated `/experience/back/*`

## 3. Admin console

- [x] 3.1 Add API module `kuailemao-admin/src/api/blog/experience`
- [x] 3.2 Add admin page list/form for experience entries under website management
- [x] 3.3 Verify dynamic menu appears after SQL (re-login if needed)

## 4. Blog frontend

- [x] 4.1 Add `/experience` route and timeline page with empty state
- [x] 4.2 Wire public list API into the experience page
- [x] 4.3 Update desktop `Menu`: replace「归档」with「工作经历」; move category/tag/timeline into「其他」
- [x] 4.4 Update mobile `MoveMenu` to match

## 5. Deploy and verify

- [x] 5.1 Run SQL on server; deploy backend, admin, blog
- [x] 5.2 Create a sample experience in admin and confirm timeline + nav on production
