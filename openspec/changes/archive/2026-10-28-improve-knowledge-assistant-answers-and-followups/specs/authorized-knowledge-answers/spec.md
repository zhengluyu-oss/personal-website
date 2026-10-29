## ADDED Requirements

### Requirement: Assistant retrieval finds and ranks bounded current evidence
The assistant SHALL search using the current question and bounded phrase or term variants, rank candidate snippets by relevance, and select only currently readable non-deleted entries and READY segments from current file versions. Retrieval MUST have fixed candidate, query, snippet and character limits. Weak or absent evidence MUST yield an explicit no-evidence result without a generation call.

#### Scenario: Different wording finds an existing answer
- **WHEN** a visitor asks a Chinese question whose wording differs from a relevant entry title but whose key terms or phrases occur in its body or valid file segments
- **THEN** the assistant selects the relevant current snippet within its evidence limits

#### Scenario: Multiple relevant passages in one entry
- **WHEN** separate current passages in one entry support different parts of a question
- **THEN** the assistant may select both passages and preserves each passage's source location

#### Scenario: No relevant current evidence
- **WHEN** only unrelated, deleted or stale passages match candidate terms
- **THEN** the assistant returns no evidence and does not call the model

### Requirement: Factual claims map to verifiable current excerpts
The assistant SHALL publish factual answers as bounded claims with server-validated citations to the current request's evidence. Every published claim MUST identify at least one supporting excerpt and the response MUST include the excerpt, current version identity and in-site source location. Unknown citation identifiers, invalid response structure, stale sources and unsupported or empty claims MUST NOT be published as factual answers.

#### Scenario: Answer with distinct sourced claims
- **WHEN** current evidence supports two distinct conclusions and the model returns valid claim-to-source mappings
- **THEN** the response exposes each conclusion with its corresponding excerpt and source link

#### Scenario: Citation exists but evidence does not support a claim
- **WHEN** the model attaches an existing source number to a conclusion that cannot be supported by that excerpt under the assistant's validation rules
- **THEN** the assistant withholds the factual claim and returns a controlled insufficient-evidence outcome

#### Scenario: Source changes during generation
- **WHEN** a cited entry revision, file version or visitor authorization changes before publication
- **THEN** the assistant discards the result without exposing the stale excerpt or answer

### Requirement: Follow-ups use bounded context and fresh evidence
The assistant SHALL accept optional bounded recent-turn context for a follow-up while preserving single-question requests. It MUST treat client-provided history as untrusted context for resolving references, not as evidence; every turn MUST independently recheck authorization, retrieve current evidence, validate citations and use the existing quotas and one-call paid-request rule. It MUST NOT persist conversation content server-side.

#### Scenario: Visitor asks a referential follow-up
- **WHEN** an authorized visitor asks “第二种方式有什么限制” with a valid recent turn identifying the subject
- **THEN** the assistant resolves the subject, searches current knowledge again and answers only from current validated excerpts

#### Scenario: History contains stale or fabricated answer text
- **WHEN** submitted history mentions a claim absent from current knowledge
- **THEN** that history does not become a source and the assistant refuses unsupported conclusions

#### Scenario: History is absent or excessive
- **WHEN** history is absent
- **THEN** the assistant processes the question as a single turn
- **WHEN** history exceeds the configured shape, turn or character limits
- **THEN** the assistant rejects it before a paid model call

#### Scenario: Access revoked before follow-up returns
- **WHEN** access is revoked while a follow-up is being generated
- **THEN** the assistant discards the answer and excerpts and reports controlled access failure
