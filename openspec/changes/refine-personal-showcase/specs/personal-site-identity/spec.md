## MODIFIED Requirements

### Requirement: Personal identity copy replaces template copy
The blog frontend SHALL present webmaster-facing copy consistent with 郑陆宇 personal branding and genuine configured professional identity, supporting personal presentation and technical expression. It MUST NOT display template cultivation/xianxia roleplay or “中二Web全栈小白” as the primary self-introduction, and MUST NOT invent or silently change the owner's employment status to suit the visual design.

#### Scenario: About page shows personal intro
- **WHEN** a visitor opens the About page
- **THEN** the headline and body text SHALL use the configured genuine personal introduction and professional direction
- **AND** the page MUST NOT show the legacy cultivation template paragraph as primary content

#### Scenario: Greeting text is personalized
- **WHEN** the site shows time-based or welcome greeting strings from frontend utilities
- **THEN** the text SHALL refer to the personal blog brand (郑陆宇 / configured site name)
- **AND** it MUST NOT say “小张的个人博客” or other leftover template owner names

## ADDED Requirements

### Requirement: About content uses a coherent responsive hierarchy
The About page SHALL share the site's existing light brand, readable type hierarchy and content containers, preserving the genuine introduction, avatar, GitHub profile and source-repository destinations. It SHALL use content-driven sections rather than oversized name typography or fixed viewport-height partitions. Profile and repository links SHALL have distinct labels, and small screens SHALL use a readable single-column flow.

#### Scenario: Visitor reads a long introduction
- **WHEN** the configured personal introduction is long or the viewport height is short
- **THEN** the page grows naturally without clipping text, overlapping links or creating forced empty full-height sections

#### Scenario: Visitor uses About on a phone
- **WHEN** the viewport is 360px or 390px wide
- **THEN** the name, avatar, introduction and clearly labeled navigation fit the content width and remain keyboard and touch accessible

### Requirement: Public contact and resume links are optional and truthful
The frontend site configuration SHALL support optional labeled public contact links and an optional resume link without changing backend fields or APIs. Only explicitly configured, valid destinations SHALL be shown. Contact destinations SHALL allow HTTPS or explicitly supplied mailto addresses, and resume destinations SHALL allow valid site-local paths or HTTPS URLs. Dangerous protocols, protocol-relative addresses and control characters MUST NOT produce active links. The page MUST NOT infer private addresses or fabricate a resume, and known existing GitHub links SHALL remain available when optional fields are empty.

#### Scenario: Optional contact and resume values are absent
- **WHEN** no extra public contact link or resume address is configured
- **THEN** the About page retains known valid public channels without an empty contact tile, broken resume button or claim that a resume is available

#### Scenario: Owner provides valid public destinations
- **WHEN** the owner explicitly configures labeled valid contact links or a resume link
- **THEN** the corresponding entries appear with truthful labels and valid link behavior, and new-window destinations use appropriate safety attributes

#### Scenario: Optional destination is invalid
- **WHEN** an optional link is unlabeled or contains a disallowed address such as a javascript URL
- **THEN** the page does not render it as an active contact or resume entry and other valid links remain usable
