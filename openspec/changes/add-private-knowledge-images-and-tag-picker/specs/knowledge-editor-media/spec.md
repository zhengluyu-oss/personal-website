## ADDED Requirements

### Requirement: Knowledge labels are directly selectable
The management editor SHALL display available tags as directly selectable controls, preserve selected values when editing, and enforce a maximum of 20 tags.

#### Scenario: Select and remove tags
- **WHEN** an owner creates or edits an entry and selects or clears a tag
- **THEN** the selected tag list changes visibly and persists after saving and reopening the entry

#### Scenario: Tag limit
- **WHEN** 20 tags are selected
- **THEN** unselected tags cannot be added while selected tags remain removable

### Requirement: Owner can insert private images into Markdown
The management editor SHALL accept pasted, dropped, and chosen JPEG, PNG, and WebP images through a knowledge-specific upload, insert stable internal references into Markdown, and show progress or actionable failure without losing text.

#### Scenario: Paste into new entry
- **WHEN** the owner pastes a valid image while editing a new entry
- **THEN** the entry is saved before image upload, and a stable image reference is inserted only after private upload is confirmed

#### Scenario: Invalid or failed upload
- **WHEN** an image exceeds limits, has unsupported or mismatched content, or storage fails
- **THEN** no broken reference is inserted and the editor retains its existing text and shows the failure

### Requirement: Knowledge images stay private
The system SHALL store knowledge images as private objects with ownership metadata and SHALL authorize every image read against the current entry and current reader permission.

#### Scenario: Authorized reader
- **WHEN** an authorized reader opens an entry containing an internal image reference
- **THEN** the image renders in the management and public knowledge views without exposing a public object URL or bearer token in the Markdown

#### Scenario: Unauthorized or revoked reader
- **WHEN** an anonymous, unauthorized, or subsequently revoked reader requests the image
- **THEN** the server rejects the request and the client clears previously created temporary image URLs on access loss or navigation

### Requirement: Safe image lifecycle
The system SHALL account for private image bytes, retain images referenced by current or retained historical content, and eventually reclaim unreferenced uploads without deleting live objects.

#### Scenario: Orphan upload
- **WHEN** upload succeeds but the image reference is never saved and the grace period passes
- **THEN** cleanup removes the unreferenced object and records the cleanup outcome for retries

#### Scenario: Historical reference
- **WHEN** an image is removed from current content but remains in a retained entry revision
- **THEN** cleanup preserves the image until that revision is no longer retained

### Requirement: Markdown preview isolates external media
Knowledge Markdown previews SHALL render only authorized internal images and SHALL continue to prevent automatic loading of external images or active embedded content.

#### Scenario: External image URL
- **WHEN** Markdown contains an external image URL or HTML media element
- **THEN** knowledge preview does not request that resource
