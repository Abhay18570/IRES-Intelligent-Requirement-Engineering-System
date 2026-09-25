ALTER TABLE requirement_ai_analysis
    ADD COLUMN classification_result JSONB,
    ADD COLUMN ambiguity_result JSONB,
    ADD COLUMN completeness_result JSONB,
    ADD COLUMN quality_result JSONB,
    ADD COLUMN duplicate_result JSONB,
    ADD COLUMN conflict_result JSONB;

