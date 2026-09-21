## ADDED Requirements

### Requirement: Successful publish navigates to article list

After a successful article publish or update, the admin UI SHALL leave the publish/edit page and show the article list.

#### Scenario: Author publishes a new article

- **WHEN** the author submits a valid new article and the publish API returns success
- **THEN** the UI SHALL navigate to the article list path
- **AND** SHALL close the publish page tab if more than one tab exists

#### Scenario: Author updates an existing article

- **WHEN** the author submits a valid edit of an existing article and the publish API returns success
- **THEN** the UI SHALL navigate to the article list path
- **AND** SHALL close the edit page tab if more than one tab exists

### Requirement: Failed publish stays on the current page

Validation errors, cover upload failures, and publish API failures SHALL keep the author on the current publish/edit page with the form content intact.

#### Scenario: Publish API fails

- **WHEN** the author submits a valid form and the publish API does not succeed
- **THEN** the UI SHALL remain on the publish/edit page
- **AND** SHALL show a failure message
- **AND** SHALL NOT clear the article title or body

### Requirement: Repeat publish cannot overwrite with a blank form after success

The admin UI SHALL NOT remain on a cleared publish form after success in a way that allows a second publish to overwrite the saved article with empty content.

#### Scenario: Author clicks publish twice quickly

- **WHEN** the author clicks publish while a publish request is already in flight
- **THEN** the UI SHALL ignore the extra click or disable the publish button until the request finishes
