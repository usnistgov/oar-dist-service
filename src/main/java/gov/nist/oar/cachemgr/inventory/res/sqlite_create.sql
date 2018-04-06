CREATE TABLE IF NOT EXISTS algorithms (
   id   integer PRIMARY KEY,
   name text NOT NULL
);

CREATE TABLE IF NOT EXISTS volumes (
   id        integer PRIMARY KEY,
   name      text NOT NULL,
   priority  integer, 
   capacity  integer
);

CREATE TABLE IF NOT EXISTS objects (
   name      text NOT NULL,
   size      integer,
   checksum  text,
   algorithm integer FOREIGN KEY,
   priority  integer,
   volume    integer FOREIGN KEY,
   since     integer,
   metadata  text,
   FOREIGN KEY (volume)    REFERENCES volumes(id),
   FOREIGN KEY (algorithm) REFERENCES algorithms(id)
);
