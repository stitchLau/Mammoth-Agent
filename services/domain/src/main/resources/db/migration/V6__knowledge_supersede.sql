-- M3.5 knowledge lifecycle: supersede/deprecate traceability (docs/06 §3/§7).
ALTER TABLE knowledge_document ADD COLUMN superseded_by TEXT;
ALTER TABLE knowledge_document ADD COLUMN deprecation_note TEXT;
