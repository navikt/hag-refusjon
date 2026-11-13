CREATE TABLE refusjonskrav_fra_im
(
    inntektsmelding_id UUID        PRIMARY KEY,
    refusjon           JSONB       NULL,
    opprettet          TIMESTAMPTZ NOT NULL DEFAULT now()
);
