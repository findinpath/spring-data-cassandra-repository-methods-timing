CREATE KEYSPACE IF NOT EXISTS demo WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE IF NOT EXISTS demo.config(
    key    VARCHAR,
    value  VARCHAR,
    primary key (key)
);

CREATE TABLE IF NOT EXISTS demo.user_bookmarks (
    user_id UUID,
    timestamp TIMEUUID,
    url VARCHAR,
    PRIMARY KEY (user_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);