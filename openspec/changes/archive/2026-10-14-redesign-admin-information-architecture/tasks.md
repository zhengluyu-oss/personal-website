## 1. Audit and Navigation Structure

- [x] 1.1 Inventory existing dynamic menus, route paths, role/menu authorization links, and layout components; document the current-to-new business-group mapping.
- [x] 1.2 Implement presentation-level business groups for content management, website configuration, users and permissions, and system operations while preserving all existing menu items and route behavior.
- [x] 1.3 Verify unknown or newly returned dynamic menus remain visible with their backend-provided title and route.

## 2. Permission Discoverability

- [x] 2.1 Make role, menu, user, and permission pages discoverable together under the users-and-permissions navigation area.
- [x] 2.2 Clarify the role/menu permission descriptions and provide direct navigation between related role and menu configuration screens without changing authorization semantics.
- [x] 2.3 Verify unauthorized users remain blocked by the existing route, component, and server-side permission checks.

## 3. Visual and Responsive Consistency

- [x] 3.1 Apply consistent layout-level hierarchy for page title/context, section spacing, content width, tables, forms, and operation feedback using existing Ant Design Vue components and theme tokens.
- [x] 3.2 Ensure collapsed or narrow-screen navigation preserves current location context and management tables/forms remain usable.
- [x] 3.3 Align empty, loading, error, permission-denied, and successful-save states on the navigation and permission-management paths.

## 4. Verification

- [x] 4.1 Run admin frontend tests, type checks, and production build; fix regressions caused by this change.
- [ ] 4.2 Verify the user-to-role-to-menu-permission workflow and representative content, website configuration, and system pages at desktop and narrow viewport widths.
