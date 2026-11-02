create table bakgrunnsjobb
(
    jobb_id      uuid primary key,
    type         text        not null,
    behandlet    timestamp,
    opprettet    timestamp   not null,

    status       text        not null,
    kjoeretid    timestamp   not null,

    forsoek      int         not null default 0,
    maks_forsoek int         not null,
    data         jsonb        not null
);

CREATE INDEX idx_bgjobb_kjoeretid_status ON BAKGRUNNSJOBB(KJOERETID, STATUS);


