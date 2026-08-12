package com.tikitaka.bidwin.loadtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SseClientMetricsTest {

    @Test
    void 수신_건수와_입찰_성공_이후_수신_지연을_노출한다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();
        metrics.recordBidSucceeded(42, 1_000);

        // when
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                bidCreated(42),
                1_120
        );

        // then
        String output = metrics.render();
        assertTrue(output.contains(
                "bidwin_sse_client_events_received_total{consumer=\"normal\"} 1"
        ));
        assertTrue(output.contains(
                "bidwin_sse_client_receive_latency_seconds_bucket"
                        + "{consumer=\"normal\",le=\"0.25\"} 1"
        ));
        assertTrue(output.contains(
                "bidwin_sse_client_receive_latency_seconds_count{consumer=\"normal\"} 1"
        ));
    }

    @Test
    void 같은_연결에서_같은_입찰을_두번_받으면_중복으로_센다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();
        metrics.recordBidSucceeded(42, 1_000);
        metrics.recordInbound(
                SseClientMetrics.Consumer.SLOW,
                subscriber,
                bidCreated(42),
                1_100
        );

        // when
        metrics.recordInbound(
                SseClientMetrics.Consumer.SLOW,
                subscriber,
                bidCreated(42),
                1_200
        );

        // then
        assertTrue(metrics.render().contains(
                "bidwin_sse_client_anomalies_total"
                        + "{consumer=\"slow\",type=\"duplicate\"} 1"
        ));
    }

    @Test
    void 같은_연결에서_더_낮은_새_입찰_ID를_받으면_역순으로_센다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();
        metrics.recordBidSucceeded(42, 1_000);
        metrics.recordBidSucceeded(41, 1_000);
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                bidCreated(42),
                1_100
        );

        // when
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                bidCreated(41),
                1_200
        );

        // then
        assertTrue(metrics.render().contains(
                "bidwin_sse_client_anomalies_total"
                        + "{consumer=\"normal\",type=\"out_of_order\"} 1"
        ));
    }

    @Test
    void SSE가_HTTP_성공_응답보다_먼저_도착하면_지연을_0으로_기록한다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                bidCreated(42),
                900
        );

        // when
        metrics.recordBidSucceeded(42, 1_000);

        // then
        assertTrue(metrics.render().contains(
                "bidwin_sse_client_receive_latency_seconds_bucket"
                        + "{consumer=\"normal\",le=\"0.005\"} 1"
        ));
    }

    @Test
    void 입찰_생성_외의_SSE는_수신_지표에서_제외한다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();

        // when
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                "{\"event\":\"auction-state\",\"data\":{\"revision\":42}}",
                1_000
        );

        // then
        assertTrue(metrics.render().contains(
                "bidwin_sse_client_events_received_total{consumer=\"normal\"} 0"
        ));
    }

    @Test
    void 숫자_범위를_벗어난_입찰_ID는_수신_지표에서_제외한다() {
        // given
        SseClientMetrics metrics = new SseClientMetrics();
        SseClientMetrics.SubscriberState subscriber =
                new SseClientMetrics.SubscriberState();

        // when
        metrics.recordInbound(
                SseClientMetrics.Consumer.NORMAL,
                subscriber,
                "{\"event\":\"bid-created\",\"data\":{\"entryId\":"
                        + "\"BID:92233720368547758070\"}}",
                1_000
        );

        // then
        assertTrue(metrics.render().contains(
                "bidwin_sse_client_events_received_total{consumer=\"normal\"} 0"
        ));
    }

    private String bidCreated(long bidId) {
        return "{\"event\":\"bid-created\",\"data\":{\"entryId\":\"BID:"
                + bidId + "\"}}";
    }
}
