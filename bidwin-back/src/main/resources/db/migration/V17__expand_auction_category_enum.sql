ALTER TABLE auction
    MODIFY COLUMN category ENUM(
        'HOUSEHOLD',
        'FOOD',
        'FURNITURE',
        'ELECTRONICS',
        'FASHION',
        'SPORTS',
        'HOBBY',
        'BOOK',
        'OTHER'
    ) NOT NULL;
