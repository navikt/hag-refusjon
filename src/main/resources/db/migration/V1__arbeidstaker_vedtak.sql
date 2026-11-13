CREATE TABLE arbeidstaker_vedtak
(
    id                 BIGSERIAL   PRIMARY KEY,
    refusjon_utfall_id UUID        NOT NULL UNIQUE,
    kafka_topic        TEXT        NOT NULL,
    kafka_partition    INTEGER     NOT NULL,
    kafka_offset       BIGINT      NOT NULL,
    vedtaksperiode_id   UUID        NOT NULL,
    fnr                TEXT        NOT NULL,
    orgnr              TEXT        NOT NULL,
    melding            JSONB       NOT NULL,
    opprettet          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT arbeidstaker_vedtak_kafka_unique
        UNIQUE (kafka_topic, kafka_partition, kafka_offset)
);

CREATE INDEX arbeidstaker_vedtak_periode_idx ON arbeidstaker_vedtak (vedtaksperiode_id);
