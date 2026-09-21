## ADDED Requirements

### Requirement: Search highlighting cannot execute result content
The blog frontend SHALL render search titles, excerpts, and highlight fragments as escaped text and MUST NOT inject API-provided strings as executable HTML.

#### Scenario: Search result contains markup payload
- **WHEN** an article title or excerpt contains HTML, an event handler, or a script-like payload
- **THEN** the search interface SHALL display the value as inert text
- **AND** no script, handler, navigation, or network request from that payload SHALL execute

#### Scenario: Search term matches ordinary text
- **WHEN** a search term matches part of a safe title or excerpt
- **THEN** the interface SHALL visually distinguish the matched text using separately rendered text segments
- **AND** it SHALL preserve the original text content

### Requirement: User-generated public content is inert by default
The frontend and backend SHALL treat comments and tree-hole content as plain user-generated text across submission, moderation, and public display unless a separately specified trusted publishing path applies sanitization.

#### Scenario: User submits markup in a comment
- **WHEN** a user submits HTML or script-like text in a comment or tree-hole entry
- **THEN** the stored and displayed value SHALL remain non-executable

#### Scenario: Administrator reviews a malicious-looking value
- **WHEN** the moderation interface displays user-generated markup text
- **THEN** the value SHALL remain inert and MUST NOT execute in the administrator's browser

### Requirement: Trusted article Markdown has an explicit sanitization boundary
The system SHALL keep administrator-authored article Markdown rendering separate from untrusted user-generated content and SHALL sanitize any produced HTML before public display when raw HTML is permitted by the Markdown renderer.

#### Scenario: Article Markdown contains unsafe raw HTML
- **WHEN** rendered article Markdown includes a disallowed script, event handler, or unsafe URL scheme
- **THEN** the public article page SHALL remove or neutralize the unsafe construct before inserting the result into the document
