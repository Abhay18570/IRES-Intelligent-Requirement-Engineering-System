CREATE TABLE project_srs_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    content JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_srs_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_srs_documents_status CHECK (status IN ('DRAFT'))
);

CREATE INDEX idx_project_srs_documents_project_created
    ON project_srs_documents (project_id, created_at DESC);