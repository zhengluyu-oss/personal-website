## 1. Baseline and Content Resolution

- [x] 1.1 Add or update focused tests that reproduce the homepage identity fallback, configured Hero precedence, default action links, featured/latest deduplication, and mobile overflow conditions before changing the presentation.
- [x] 1.2 Refine the existing homepage content resolver so configured Hero values remain authoritative while missing core copy resolves to the existing site identity, backend/full-stack positioning, and meaningful introduction without changing any API or database field.
- [x] 1.3 Resolve Hero actions so complete administrator-configured buttons are preserved, incomplete buttons leave no placeholder, and a wholly unconfigured state provides existing blog and work-experience routes as defaults.

## 2. Hero and Responsive Foundation

- [x] 2.1 Recompose the homepage Hero into a restrained editorial split that prioritizes name, role, value statement and actions while retaining the first valid Banner as secondary media and the current no-image placeholder.
- [x] 2.2 Correct page, grid/flex child, text and media sizing so 360px, 390px, 430px and 640px layouts collapse to one column without fixed-width overflow, clipping or horizontal scrolling.
- [x] 2.3 Give the Banner a reserved responsive ratio and explicit fit behavior for unusual source dimensions, keep primary copy usable before image load, and preserve reduced-motion behavior.

## 3. Professional Content Hierarchy

- [x] 3.1 Implement a deterministic homepage professional-proof selection derived only from existing valid featured articles, latest articles and enabled work experiences, with no duplicate article placement or invented content.
- [x] 3.2 Redesign the featured article presentation to reduce cover dominance and emphasize title, real metadata, summary or recommendation context, fallback media and the existing unique article detail route.
- [x] 3.3 Present up to five non-featured recent articles in a tighter, scannable hierarchy with stable image fallbacks, real categories and accessible detail links.
- [x] 3.4 Align homepage work-experience items with the professional-proof hierarchy, showing available company, position, time and real result summary while preserving mouse, touch and keyboard navigation to existing detail routes.
- [x] 3.5 Replace the low-information closing area with a compact site introduction and existing about/contact destinations, hiding unavailable content rather than rendering an empty decorative section.

## 4. Brand, Resilience and Accessibility

- [x] 4.1 Normalize homepage labels and copy tone, using the existing light cool-gray palette, blue accent, spacing, radius and shadow variables without adding a UI or animation dependency.
- [x] 4.2 Ensure Hero, featured articles, recent articles and experiences each retain readable loading, empty and request-error states so one failed data source cannot blank the homepage.
- [x] 4.3 Verify semantic links, visible keyboard focus, touch target usability, meaningful image alternatives and `prefers-reduced-motion` handling across the revised homepage.

## 5. Verification and Release Readiness

- [x] 5.1 Run the focused frontend test suites and production build, fixing only regressions introduced by this change.
- [x] 5.2 Perform visual checks at 1440px desktop and 360px, 390px and 430px mobile widths, confirming no horizontal overflow and validating configured, default, loading, empty, failed-image and failed-request states.
- [x] 5.3 Regression-check existing navigation, public URLs, the single article detail route, work-experience links, login entry, TDK output and filing information, confirming no route exceeds the existing three-level constraint.
- [x] 5.4 Document the deployment and rollback verification for the existing static frontend release flow, confirming that no backend API, database migration or new dependency is required.
