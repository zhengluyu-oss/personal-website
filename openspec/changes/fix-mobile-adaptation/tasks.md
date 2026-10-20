## 1. Mobile header declutter

- [ ] 1.1 Remove diary and knowledge persistent links from the mobile top bar; keep them in the drawer menu only
- [ ] 1.2 Expand brand and login/account hit areas to at least 44×44 CSS pixels without causing header overflow at 390px
- [ ] 1.3 Verify at 375px/390px: no document horizontal scroll; drawer still opens experience, blog, diary, and knowledge destinations

## 2. High-priority page overflow and empty states

- [ ] 2.1 Fix tree-hole compose row: replace fixed input/button widths; support wrap/stack; remove layout that forces page-level overflow at 320px when submit appears
- [ ] 2.2 Music page: when music frontend URL is blank, show empty/unavailable state instead of an empty-src full-viewport iframe
- [ ] 2.3 Admin login: make card fluid (`max-width` within viewport) and raise input touch height for ≤320px

## 3. Blog category strip and photos

- [ ] 3.1 Keep category chips reachable on ~390px; add a discoverability cue when the strip overflows; ensure the strip scrollport does not widen `document`
- [ ] 3.2 Photo album titles: contain long names (wrap/ellipsis); remove fragile gallery min-width that risks overflow

## 4. Auth and share detail polish

- [ ] 4.1 Public login: use ≥16px input font size, touch-friendly primary buttons, and stop welcome copy from overlapping form controls on phone widths
- [ ] 4.2 Website share detail metadata: allow wrap/stack so long external-link labels do not force horizontal page scroll

## 5. Verification

- [ ] 5.1 Manually or with Playwright re-check `/`, `/blog`, `/tree-hole`, `/music`, `/photos`, `/auth/login`, `/admin/login`, and one `/website-shares/:id` at 390×844 and 320×700
- [ ] 5.2 Optional: if `/knowledge` is routed in the build under test, spot-check Workspace/Connections at 390px; otherwise note as follow-up
