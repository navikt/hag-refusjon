DELETE FROM arbeidstaker_vedtak;

ALTER TABLE arbeidstaker_vedtak DROP CONSTRAINT arbeidstaker_vedtak_pkey;
ALTER TABLE arbeidstaker_vedtak DROP COLUMN id;
ALTER TABLE arbeidstaker_vedtak DROP COLUMN refusjon_utfall_id;
ALTER TABLE arbeidstaker_vedtak DROP COLUMN kafka_topic;
ALTER TABLE arbeidstaker_vedtak DROP COLUMN kafka_partition;
ALTER TABLE arbeidstaker_vedtak DROP COLUMN kafka_offset;
ALTER TABLE arbeidstaker_vedtak ADD COLUMN behandling_id UUID NOT NULL;
ALTER TABLE arbeidstaker_vedtak ADD PRIMARY KEY (behandling_id);
