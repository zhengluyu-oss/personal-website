## 1. Token foundation

- [x] 1.1 Lock Ink & Ember light/dark `--brand-*` values in blog `theme.scss` (canvas, surface, ink, accent, line, radius, shadow); remove `#155eef` as default light accent
- [x] 1.2 Map Element Plus primary / related CSS variables on the blog to the new accent family
- [x] 1.3 Align admin `workspace.scss` `--workspace-*` and Ant Design primary accent with the same token family

## 2. Hero to content continuity

- [x] 2.1 Add a short Hero→content transition on the home Brand/Main boundary so the first scroll lands on brand-canvas rather than a hard theme cut
- [x] 2.2 Retint home snapshot / first section shells to the new tokens so post-hero chrome matches the brand accent

## 3. Header brand hierarchy

- [x] 3.1 Strengthen desktop brand title weight/size relative to utilities; restyle search and login as secondary neutral/accent-consistent controls (no green search glyph)
- [x] 3.2 Quiet primary nav icons to ink/muted brand color; keep active emphasis on label or shared accent underline

## 4. Typography coherence

- [x] 4.1 Confirm primary UI font stack loads as intended for headings and body; document fallback
- [x] 4.2 Remove or override leftover Georgia/serif display rules on default home/writing brand paths so one family owns hierarchy

## 5. Legacy chrome cleanup

- [x] 5.1 Retint public footer, scrollbar, and other shell gradients still using pink-purple / rainbow template colors to brand or neutral tokens
- [x] 5.2 Spot-fix category chips and other high-visibility `--mao-*` leftovers that fight the new accent; retint without removing features

## 6. Admin surface polish

- [x] 6.1 Restyle admin login page brand mark, canvas, and primary button to shared tokens / 陆屿 identity
- [x] 6.2 Verify sider selected state and workspace chrome use the shared accent (no leftover default blue)

## 7. Acceptance

- [x] 7.1 Spot-check light theme on home, blog list, one article, experience, public login, admin login for shared accent and no `#155eef` primary chrome on shells
- [x] 7.2 Spot-check home + header at ~390px width for no new horizontal overflow from style changes; confirm reduced-motion still stops non-essential hero video autoplay behavior
