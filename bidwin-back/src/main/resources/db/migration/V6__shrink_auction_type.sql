ALTER TABLE auction
    MODIFY COLUMN auction_type VARCHAR(4)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;
