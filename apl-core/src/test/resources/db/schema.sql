SET MAX_MEMORY_ROWS 1000000;
SET CACHE_SIZE 262144;
SET DEFAULT_LOCK_TIMEOUT 1;

CREATE SCHEMA IF NOT EXISTS FTL;
CREATE TABLE IF NOT EXISTS FTL.INDEXES(SCHEMA VARCHAR, "TABLE" VARCHAR, COLUMNS VARCHAR, PRIMARY KEY(SCHEMA, "TABLE"));