-- This milestone has no persisted business entities yet.
-- Keep the migration history ready for the first real LogicScope aggregate.
CREATE TABLE IF NOT EXISTS logicscope_schema_metadata (
    key VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL
);

INSERT INTO logicscope_schema_metadata (key, value)
VALUES ('schema_version', '1')
ON CONFLICT (key) DO NOTHING;
