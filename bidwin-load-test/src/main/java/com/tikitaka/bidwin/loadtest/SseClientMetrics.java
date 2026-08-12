package com.tikitaka.bidwin.loadtest;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SseClientMetrics {

    public enum Consumer {
        NORMAL("normal"),
        SLOW("slow");

        private final String label;

        Consumer(String label) {
            this.label = label;
        }
    }

    public static final class SubscriberState {
        private final Set<Long> receivedBidIds = new HashSet<>();
        private long highestBidId = -1;
    }

    private static final Pattern EVENT = Pattern.compile(
            "\\\"event\\\"\\s*:\\s*\\\"bid-created\\\""
    );
    private static final Pattern BID_ID = Pattern.compile(
            "\\\"entryId\\\"\\s*:\\s*\\\"BID:(\\d+)\\\""
    );
    private static final double[] LATENCY_BUCKETS = {
            0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30
    };

    private final Map<Consumer, LongAdder> received = adders();
    private final Map<Consumer, LongAdder> duplicates = adders();
    private final Map<Consumer, LongAdder> outOfOrder = adders();
    private final Map<Consumer, Histogram> latencies = histograms();
    // ponytail: 한 번의 실행 동안 입찰 시각을 보관한다. 초고속 장시간 테스트가 필요해질 때만 만료를 추가한다.
    private final Map<Long, BidTiming> bidTimings = new ConcurrentHashMap<>();
    private HttpServer server;

    public void startServer(int port) {
        if (server != null) {
            throw new IllegalStateException("SSE 클라이언트 메트릭 서버가 이미 실행 중입니다.");
        }
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "SSE 클라이언트 메트릭 포트 " + port + "를 열 수 없습니다.",
                    exception
            );
        }
        server.createContext("/metrics", exchange -> {
            byte[] body = render().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/plain; version=0.0.4; charset=utf-8"
            );
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool(Thread.ofVirtual().factory()));
        server.start();
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void recordBidSucceeded(long bidId, long succeededAtMillis) {
        BidTiming timing = bidTimings.computeIfAbsent(bidId, ignored -> new BidTiming());
        synchronized (timing) {
            timing.succeededAtMillis = succeededAtMillis;
            PendingReceipt receipt;
            while ((receipt = timing.pendingReceipts.poll()) != null) {
                recordLatency(receipt.consumer(), receipt.receivedAtMillis(), succeededAtMillis);
            }
        }
    }

    public void recordInbound(
            Consumer consumer,
            SubscriberState subscriber,
            String message,
            long receivedAtMillis
    ) {
        OptionalLong parsedBidId = bidId(message);
        if (parsedBidId.isEmpty()) {
            return;
        }

        long bidId = parsedBidId.getAsLong();
        received.get(consumer).increment();
        if (!subscriber.receivedBidIds.add(bidId)) {
            duplicates.get(consumer).increment();
        } else {
            if (bidId < subscriber.highestBidId) {
                outOfOrder.get(consumer).increment();
            }
            subscriber.highestBidId = Math.max(subscriber.highestBidId, bidId);
        }

        BidTiming timing = bidTimings.computeIfAbsent(bidId, ignored -> new BidTiming());
        synchronized (timing) {
            if (timing.succeededAtMillis >= 0) {
                recordLatency(consumer, receivedAtMillis, timing.succeededAtMillis);
            } else {
                timing.pendingReceipts.add(new PendingReceipt(consumer, receivedAtMillis));
            }
        }
    }

    String render() {
        StringBuilder output = new StringBuilder(2_048);
        output.append("# HELP bidwin_sse_client_events_received_total "
                + "Gatling subscribers received bid-created events.\n");
        output.append("# TYPE bidwin_sse_client_events_received_total counter\n");
        for (Consumer consumer : Consumer.values()) {
            metric(output, "bidwin_sse_client_events_received_total", consumer,
                    received.get(consumer).sum());
        }

        output.append("# HELP bidwin_sse_client_anomalies_total "
                + "Duplicate or out-of-order bid-created events observed per connection.\n");
        output.append("# TYPE bidwin_sse_client_anomalies_total counter\n");
        for (Consumer consumer : Consumer.values()) {
            anomaly(output, consumer, "duplicate", duplicates.get(consumer).sum());
            anomaly(output, consumer, "out_of_order", outOfOrder.get(consumer).sum());
        }

        output.append("# HELP bidwin_sse_client_receive_latency_seconds "
                + "Time from successful bid response to SSE receipt.\n");
        output.append("# TYPE bidwin_sse_client_receive_latency_seconds histogram\n");
        for (Consumer consumer : Consumer.values()) {
            Histogram histogram = latencies.get(consumer);
            for (int index = 0; index < LATENCY_BUCKETS.length; index++) {
                output.append("bidwin_sse_client_receive_latency_seconds_bucket{consumer=\"")
                        .append(consumer.label)
                        .append("\",le=\"")
                        .append(LATENCY_BUCKETS[index])
                        .append("\"} ")
                        .append(histogram.buckets[index].sum())
                        .append('\n');
            }
            output.append("bidwin_sse_client_receive_latency_seconds_bucket{consumer=\"")
                    .append(consumer.label)
                    .append("\",le=\"+Inf\"} ")
                    .append(histogram.count.sum())
                    .append('\n');
            output.append("bidwin_sse_client_receive_latency_seconds_count{consumer=\"")
                    .append(consumer.label)
                    .append("\"} ")
                    .append(histogram.count.sum())
                    .append('\n');
            output.append("bidwin_sse_client_receive_latency_seconds_sum{consumer=\"")
                    .append(consumer.label)
                    .append("\"} ")
                    .append(histogram.sum.sum())
                    .append('\n');
        }
        return output.toString();
    }

    private OptionalLong bidId(String message) {
        if (!EVENT.matcher(message).find()) {
            return OptionalLong.empty();
        }
        Matcher matcher = BID_ID.matcher(message);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private void recordLatency(
            Consumer consumer,
            long receivedAtMillis,
            long succeededAtMillis
    ) {
        double seconds = Math.max(0, receivedAtMillis - succeededAtMillis) / 1_000.0;
        latencies.get(consumer).record(seconds);
    }

    private static Map<Consumer, LongAdder> adders() {
        Map<Consumer, LongAdder> values = new EnumMap<>(Consumer.class);
        for (Consumer consumer : Consumer.values()) {
            values.put(consumer, new LongAdder());
        }
        return values;
    }

    private static Map<Consumer, Histogram> histograms() {
        Map<Consumer, Histogram> values = new EnumMap<>(Consumer.class);
        for (Consumer consumer : Consumer.values()) {
            values.put(consumer, new Histogram());
        }
        return values;
    }

    private static void metric(
            StringBuilder output,
            String name,
            Consumer consumer,
            long value
    ) {
        output.append(name)
                .append("{consumer=\"")
                .append(consumer.label)
                .append("\"} ")
                .append(value)
                .append('\n');
    }

    private static void anomaly(
            StringBuilder output,
            Consumer consumer,
            String type,
            long value
    ) {
        output.append("bidwin_sse_client_anomalies_total{consumer=\"")
                .append(consumer.label)
                .append("\",type=\"")
                .append(type)
                .append("\"} ")
                .append(value)
                .append('\n');
    }

    private static final class Histogram {
        private final LongAdder[] buckets = new LongAdder[LATENCY_BUCKETS.length];
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();

        private Histogram() {
            for (int index = 0; index < buckets.length; index++) {
                buckets[index] = new LongAdder();
            }
        }

        private void record(double value) {
            count.increment();
            sum.add(value);
            for (int index = 0; index < LATENCY_BUCKETS.length; index++) {
                if (value <= LATENCY_BUCKETS[index]) {
                    buckets[index].increment();
                }
            }
        }
    }

    private static final class BidTiming {
        private long succeededAtMillis = -1;
        private final Queue<PendingReceipt> pendingReceipts = new ArrayDeque<>();
    }

    private record PendingReceipt(Consumer consumer, long receivedAtMillis) {
    }
}
