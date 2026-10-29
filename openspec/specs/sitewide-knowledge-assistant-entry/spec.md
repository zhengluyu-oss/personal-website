# sitewide-knowledge-assistant-entry Specification

## Purpose
定义全站客服悬浮入口、临时对话、站内来源导航以及移动端和键盘交互。

## Requirements

### Requirement: Authorized visitors can open a sitewide assistant
The blog frontend SHALL offer a right-bottom assistant entry across ordinary site pages only after the account's knowledge access and assistant availability are confirmed. It MUST not expose a usable question interface or protected content while authorization is unknown, denied or unavailable.

#### Scenario: Authorized visitor navigates the site
- **WHEN** an active signed-in visitor with confirmed knowledge access visits the home, blog, about or knowledge pages
- **THEN** the same assistant entry remains discoverable without replacing the site's navigation

#### Scenario: Visitor is not authorized
- **WHEN** access has not been confirmed or is denied
- **THEN** the question composer and any prior private assistant content are absent from the visible interface

### Requirement: Assistant interaction is usable and transient
The assistant SHALL provide a bounded question composer, clear waiting, error and no-evidence states, a way to close or clear the conversation, and safe in-site source links. The first version SHALL keep conversation turns only in current browser memory and MUST clear them on logout, permission loss or reload. The panel SHALL work with keyboard navigation and narrow screens without blocking the site's primary controls.

#### Scenario: Visitor asks and follows a source
- **WHEN** the assistant returns an answer with verified citations
- **THEN** the visitor can read the answer, open a citation in the existing knowledge reader, and continue browsing within the site layout

#### Scenario: Narrow screen or keyboard use
- **WHEN** the assistant is used at 360px, 390px or 430px width or only with a keyboard
- **THEN** controls remain reachable, the panel fits the viewport, focus is managed when opening and closing, and essential site navigation remains usable

#### Scenario: Session ends or access is revoked
- **WHEN** the visitor logs out, reloads, or loses knowledge access
- **THEN** the transient conversation and source excerpts are cleared and cannot be reopened from stale frontend state

### Requirement: Assistant panel supports genuine transient follow-ups
The assistant panel SHALL let a visitor ask a follow-up using only a bounded recent portion of the current in-memory conversation. It MUST clearly show waiting, no-evidence and failure states for each submitted turn, prevent duplicate submission while busy, and clear all turn context on manual clear, logout, permission loss or reload.

#### Scenario: Follow-up after an answer
- **WHEN** a visitor asks a question and then refers to that answer in a follow-up
- **THEN** the panel sends the bounded recent context and displays the new answer as a separate turn

#### Scenario: Session is cleared
- **WHEN** the visitor clears the panel, logs out, loses permission or reloads the page
- **THEN** no earlier turn is available for display or future follow-up context

### Requirement: Visitor can inspect evidence beside each claim
The assistant panel SHALL render each factual claim as text with its corresponding current source excerpt, source title, available page or line location, and safe in-site link. It MUST NOT render model text or excerpts as executable markup.

#### Scenario: Answer has multiple cited claims
- **WHEN** the assistant returns multiple claims with different excerpts
- **THEN** each claim shows the excerpt that supports it and a link to the existing knowledge reader

#### Scenario: No evidence or source becomes unavailable
- **WHEN** the service reports no evidence or access loss
- **THEN** the panel presents that state clearly and does not leave protected excerpts visible
