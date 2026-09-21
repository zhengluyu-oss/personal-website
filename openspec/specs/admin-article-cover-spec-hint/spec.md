# admin-article-cover-spec-hint Specification

## Purpose
Requirements maintained by OpenSpec changes for admin-article-cover-spec-hint.

## Requirements

### Requirement: Article cover upload shows format specification

The admin article publish and edit page SHALL display visible guidance next to the cover upload control describing the recommended aspect ratio, pixel dimensions, supported file formats, and maximum file size after compression.

#### Scenario: Author opens new article publish page

- **WHEN** the author navigates to the article publish page without an existing article id
- **THEN** the cover upload area SHALL show a label identifying it as the article cover
- **AND** SHALL show text stating ratio **16:10**, recommended size **1600×1000 px**, supported formats JPG/PNG/WebP, and compressed size limit **≤ 0.3MB**

#### Scenario: Author opens article edit page

- **WHEN** the author opens the publish page with an article id and existing cover data is loaded
- **THEN** the same cover specification guidance SHALL remain visible alongside the upload or preview control

### Requirement: Cover specification aligns with blog list layout

The documented cover aspect ratio SHALL match the blog public list card container ratio (**16:10**) used on the blog aggregation page.

#### Scenario: Specification text references list layout

- **WHEN** the author reads the cover specification on the publish page
- **THEN** the guidance SHALL state that covers are displayed in blog list and featured areas using a **16:10** frame
- **AND** SHALL recommend uploading at **16:10** to avoid visible letterboxing on the public site

### Requirement: Upload behavior unchanged

Displaying cover specification guidance SHALL NOT change existing cover upload, compression, validation, or publish API behavior.

#### Scenario: Author uploads a valid cover

- **WHEN** the author selects a JPG, PNG, or WebP file that passes existing compression and size checks
- **THEN** the upload and publish flow SHALL behave the same as before this change
