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
