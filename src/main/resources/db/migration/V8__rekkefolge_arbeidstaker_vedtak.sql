-- Sekvensnummer gir rekkefølgen arbeidstakervedtakene ble mottatt i, og brukes for å behandle vedtak per vedtaksperiode i rekkefølge.
-- Eksisterende rader får sekvensnummer i vilkårlig rekkefølge.
ALTER TABLE arbeidstaker_vedtak
    ADD COLUMN sekvensnummer BIGSERIAL UNIQUE,
    ADD COLUMN status        TEXT NOT NULL DEFAULT 'MOTTATT';

-- Eksisterende vedtak regnes som ferdig behandlet.
UPDATE arbeidstaker_vedtak
SET status = 'BEHANDLET';
