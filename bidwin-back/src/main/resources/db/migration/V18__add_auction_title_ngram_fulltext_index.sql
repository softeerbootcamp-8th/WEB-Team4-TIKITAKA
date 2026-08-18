SET @previous_innodb_ft_enable_stopword =
    @@SESSION.innodb_ft_enable_stopword;

SET SESSION innodb_ft_enable_stopword = OFF;

CREATE FULLTEXT INDEX idx_auction_title_ngram
    ON auction (title)
    WITH PARSER ngram;

SET SESSION innodb_ft_enable_stopword =
    @previous_innodb_ft_enable_stopword;
