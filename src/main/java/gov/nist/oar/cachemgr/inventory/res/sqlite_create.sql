CREATE TABLE IF NOT EXISTS algorithms (
   id   integer PRIMARY KEY,
   name text NOT NULL
);

CREATE TABLE IF NOT EXISTS volumes (
   id        integer PRIMARY KEY,
   name      text NOT NULL,
   priority  integer, 
   capacity  integer,
   metadata  text
);

CREATE TABLE IF NOT EXISTS objects (
   objid     text NOT NULL,
   size      integer,
   checksum  text,
   algorithm integer,
   priority  integer,
   name      text NOT NULL,
   volume    integer,
   since     integer,
   metadata  text,
   FOREIGN KEY (volume)    REFERENCES volumes(id),
   FOREIGN KEY (algorithm) REFERENCES algorithms(id)
);
