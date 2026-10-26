DELETE FROM arbeidstaker_vedtak;

ALTER TABLE arbeidstaker_vedtak
    DROP COLUMN id,
    DROP COLUMN refusjon_utfall_id,
    DROP COLUMN kafka_topic,
    DROP COLUMN kafka_partition,
    DROP COLUMN kafka_offset,
    ADD COLUMN behandling_id UUID PRIMARY KEY;
