CREATE TABLE persistence(
    scope varchar not null,
    key varchar not null,
    value varchar not null,
    expiry timestamp not null,
    PRIMARY KEY (scope, key)
);

CREATE INDEX persistence_expiry_idx ON persistence (expiry);