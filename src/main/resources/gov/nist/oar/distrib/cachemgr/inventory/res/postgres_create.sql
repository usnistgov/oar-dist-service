CREATE TABLE IF NOT EXISTS algorithms (
   id   SERIAL PRIMARY KEY,
   name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS volumes (
   id        SERIAL PRIMARY KEY,
   name      TEXT NOT NULL UNIQUE,
   priority  INTEGER,
   capacity  BIGINT,
   status    INTEGER NOT NULL,
   metadata  TEXT
);

CREATE TABLE IF NOT EXISTS objects (
   objid     TEXT NOT NULL,
   size      BIGINT,
   checksum  TEXT,
   algorithm INTEGER,
   priority  INTEGER NOT NULL,
   name      TEXT NOT NULL,
   volume    INTEGER,
   since     BIGINT NOT NULL,
   checked   BIGINT NOT NULL,
   cached    BOOLEAN DEFAULT false,
   metadata  TEXT,
   FOREIGN KEY (volume)    REFERENCES volumes(id) ON DELETE CASCADE,
   FOREIGN KEY (algorithm) REFERENCES algorithms(id)
);

CREATE INDEX IF NOT EXISTS idx_objects_objid ON objects(objid);
CREATE INDEX IF NOT EXISTS idx_objects_volume ON objects(volume);
CREATE INDEX IF NOT EXISTS idx_objects_name ON objects(name);
CREATE INDEX IF NOT EXISTS idx_objects_cached ON objects(cached);
CREATE INDEX IF NOT EXISTS idx_objects_checked ON objects(checked);
CREATE INDEX IF NOT EXISTS idx_objects_priority ON objects(priority);
CREATE INDEX IF NOT EXISTS idx_objects_since ON objects(since);
