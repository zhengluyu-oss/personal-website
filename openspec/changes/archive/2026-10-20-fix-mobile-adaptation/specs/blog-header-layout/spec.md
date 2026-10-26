## MODIFIED Requirements

### Requirement: Header adapts to narrow screens without horizontal overflow
The blog frontend header MUST collapse the desktop navigation into the existing mobile entry at narrow widths and SHALL keep the brand and essential actions legible without horizontal overflow. On mobile widths, the fixed top bar SHALL expose only the navigation opener, brand, search, and account/login controls; secondary destinations such as diary and knowledge MUST remain reachable from the mobile drawer menu rather than crowding the top bar. Interactive brand and login controls SHALL expose a minimum touch target of 44×44 CSS pixels (including padding).

#### Scenario: Mobile header is displayed
- **WHEN** the viewport width is 375px
- **THEN** the brand and mobile navigation entry remain visible, touch targets are usable, and the page does not scroll horizontally

#### Scenario: Mobile top bar stays uncrowded
- **WHEN** a visitor views the mobile header at approximately 390px width
- **THEN** the fixed top bar does not place diary or knowledge as persistent top-bar links beside the brand
- **AND** those destinations remain reachable after opening the mobile navigation drawer
- **AND** the brand and login/account controls each expose at least a 44×44 CSS-pixel hit area
