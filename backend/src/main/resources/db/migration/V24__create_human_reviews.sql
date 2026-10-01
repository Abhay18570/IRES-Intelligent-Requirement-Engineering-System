ALTER TABLE user_stories DROP CONSTRAINT ck_user_stories_status;
ALTER TABLE user_stories ADD CONSTRAINT ck_user_stories_status CHECK (status IN (
    'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'READY', 'IN_PROGRESS', 'COMPLETED'
));

ALTER TABLE acceptance_criteria DROP CONSTRAINT ck_acceptance_criteria_status;
ALTER TABLE acceptance_criteria ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE acceptance_criteria ADD CONSTRAINT ck_acceptance_criteria_status CHECK (status IN (
    'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'READY', 'PASSED', 'FAILED'
));

ALTER TABLE project_srs_documents DROP CONSTRAINT ck_project_srs_documents_status;
ALTER TABLE project_srs_documents ADD CONSTRAINT ck_project_srs_documents_status CHECK (status IN (
    'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'
));

CREATE TABLE human_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_type VARCHAR(30) NOT NULL,
    artifact_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    artifact_status VARCHAR(20) NOT NULL,
    reviewer_id UUID NOT NULL,
    reason TEXT,
    previous_content JSONB NOT NULL,
    reviewed_content JSONB NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_human_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_human_reviews_artifact_type CHECK (artifact_type IN ('USER_STORY', 'ACCEPTANCE_CRITERIA', 'SRS_DOCUMENT')),
    CONSTRAINT ck_human_reviews_action CHECK (action IN ('ACCEPT', 'MODIFY', 'REJECT')),
    CONSTRAINT ck_human_reviews_status CHECK (artifact_status IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX idx_human_reviews_artifact_history
    ON human_reviews (artifact_type, artifact_id, reviewed_at DESC);