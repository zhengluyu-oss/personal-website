# authorized-knowledge-answers Specification

## Purpose
定义获授权访客的知识库提问、当前证据检索、可核验来源和独立模型额度边界。

## Requirements

### Requirement: Knowledge questions require current authorization
The assistant SHALL accept questions only from an active signed-in account whose current module access permits reading the knowledge base. It MUST recheck authorization before retrieval, before sending source text to the model, and before returning an answer. Anonymous, disabled, revoked, or unavailable access MUST fail without exposing knowledge content.

#### Scenario: Authorized visitor asks a question
- **WHEN** a signed-in active visitor with knowledge access submits a valid question
- **THEN** the assistant may retrieve only knowledge content that the same visitor can currently read

#### Scenario: Access changes during generation
- **WHEN** the visitor loses access, becomes disabled, or the module becomes unavailable before the answer is returned
- **THEN** the assistant discards the result and returns a controlled access or availability state without source text

### Requirement: Answers use bounded current knowledge evidence
The assistant SHALL retrieve a bounded set of current non-deleted knowledge entries and valid segments from the current READY file version. It MUST exclude diaries, unrelated accounts, deleted entries, stale revisions, unavailable file versions and untrusted external webpages. Insufficient retrieval MUST produce an explicit no-evidence response rather than a fabricated answer.

#### Scenario: Relevant current entries exist
- **WHEN** a visitor asks about a topic covered by currently readable entries
- **THEN** only bounded relevant snippets and their version identifiers are used to form the answer

#### Scenario: No reliable evidence exists
- **WHEN** the current knowledge search finds no suitable evidence
- **THEN** the assistant states that the knowledge base cannot confirm an answer and does not invoke a generation request

### Requirement: Published answers have verifiable sources
The assistant SHALL attach server-validated in-site source references to factual answers. It MUST reject fabricated, foreign, deleted or stale citation identifiers and MUST NOT render model-provided HTML, executable content or arbitrary links as trusted sources. Follow-up questions SHALL repeat current retrieval and validation.

#### Scenario: Model selects a valid citation
- **WHEN** the model cites a snippet included in the current question's evidence set
- **THEN** the server verifies its entry and revision and returns a link that opens the existing knowledge reader

#### Scenario: Model invents a citation or source changes
- **WHEN** a cited identifier was not supplied for this question or its entry or file version has changed
- **THEN** no factual answer with that citation is published and the visitor receives a safe failure or insufficient-evidence message

### Requirement: Model use is independently controlled
Knowledge assistant generation SHALL be disabled by default and enabled only with separate validated configuration, bounded prompt and output sizes, request timeout, concurrency limit, per-account and sitewide daily quotas. Failed or uncertain calls MUST NOT be automatically retried. Ordinary logs MUST exclude questions, knowledge snippets, prompts, full answers and provider secrets; long-term conversation storage MUST be absent in the first version.

#### Scenario: Quota or configuration blocks a request
- **WHEN** the feature is disabled, not fully configured, or a quota is exhausted
- **THEN** the request fails before sending any knowledge text to a model and reports a clear non-sensitive state

#### Scenario: Provider times out after submission
- **WHEN** the generation provider times out or returns an uncertain result
- **THEN** the assistant does not issue a second paid request automatically and does not claim an answer succeeded

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
