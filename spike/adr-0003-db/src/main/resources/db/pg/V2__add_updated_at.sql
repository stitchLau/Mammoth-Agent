ALTER TABLE knowledge_document ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
