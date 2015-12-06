
DROP KEYSPACE IF EXISTS twitter;
CREATE KEYSPACE twitter WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

use twitter;

create table twitter_data (
    feel text,
    weight int,
    primary key(feel)
);