package OrderService;

import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMonitor {

    private static final AtomicLong totalLatency = new AtomicLong(0);
    private static final AtomicLong count = new AtomicLong(0);

    private PerformanceMonitor() {}

    public static void recordLatency(long nanos) {
        if (nanos < 0) {
            return;
        }
        totalLatency.addAndGet(nanos);
        long currentCount = count.incrementAndGet();

        if (currentCount % 1000 == 0) {
            double avgMicros = (totalLatency.get() / (double) currentCount) / 1000.0;
            System.out.printf("Processed %d orders. Avg Latency: %.2f us%n", currentCount, avgMicros);
        }
    }
}

