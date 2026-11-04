CREATE TABLE refusjon_utfall
(
    behandling_id      UUID        PRIMARY KEY,
    refusjon_utfall_id UUID        NOT NULL UNIQUE,
    vedtaksperiode_id  UUID        NOT NULL,
    utfall             JSONB       NOT NULL,
    opprettet          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refusjon_utfall_vedtaksperiode_idx ON refusjon_utfall (vedtaksperiode_id);
