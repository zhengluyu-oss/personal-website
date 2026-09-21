## 1. Homepage Section Order

- [x] 1.1 In the public homepage main content, place the work-experience section immediately after the snapshot and before the featured/latest article section, without changing section copy, data loading, or detail routes.
- [x] 1.2 Move the inter-section divider so it still sits between the experience and article blocks after the reorder, and keep mobile single-column wrapping unchanged.

## 2. Local Verification

- [x] 2.1 Confirm desktop and a representative mobile width show experience above articles, with existing article and experience links still opening the current public routes.
- [x] 2.2 Run the existing blog frontend tests and production build, fixing only regressions introduced by this reorder.

## 3. Production Release

- [x] 3.1 Deploy blog static assets with the existing `scripts/deploy-to-server.ps1 -Only blog` flow and keep the previous blog directory for rollback.
- [x] 3.2 Verify the live homepage order, then spot-check `/blog`, one article detail route, and `/experience`. If validation fails, restore `blog.previous` and reload Nginx.
