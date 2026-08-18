package com.tikitaka.bidwinback.auction.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AuctionListFullTextQueryTest {

    @Test
    void 검색어를_Boolean_phrase로_변환한다() {
        assertThat(AuctionListFullTextQuery.from("가격").value())
                .isEqualTo("\"가격\"");
    }

    @Test
    void 따옴표와_역슬래시를_escape한다() {
        assertThat(AuctionListFullTextQuery.from("a\"\\b").value())
                .isEqualTo("\"a\\\"\\\\b\"");
    }

    @Test
    void 두_글자와_서른_글자는_허용한다() {
        assertThat(AuctionListFullTextQuery.from("가나").value())
                .isEqualTo("\"가나\"");
        assertThat(AuctionListFullTextQuery.from("가".repeat(30)).value())
                .isEqualTo("\"" + "가".repeat(30) + "\"");
    }

    @Test
    void 한_글자와_서른한_글자는_거절한다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AuctionListFullTextQuery.from("가"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AuctionListFullTextQuery.from("가".repeat(31)));
    }
}
