## ADDED Requirements

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
