/**
 * Benchmark test for FilesystemCacheVolume concurrent read performance.
 *
 * This measures the impact of synchronized vs unsynchronized getStream().
 * Run BEFORE and AFTER removing synchronized from getStream() to compare.
 *
 * Usage:
 *   mvn test -pl . -Dtest=FilesystemCacheVolumeConcurrencyBenchmark -Dsurefire.useFile=false
 */
package gov.nist.oar.distrib.cachemgr.storage;

import gov.nist.oar.distrib.StorageVolumeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilesystemCacheVolumeConcurrencyBenchmark {

    private static final int FILE_COUNT = 50;
    private static final int FILE_SIZE_KB = 512;       // 512KB per file
    private static final int[] THREAD_COUNTS = {1, 4, 8, 16, 32};
    private static final int READS_PER_THREAD = 20;

    @TempDir
    File tempDir;

    private FilesystemCacheVolume volume;
    private String[] fileNames;

    @BeforeEach
    void setup() throws IOException {
        File volDir = new File(tempDir, "bench-vol");
        volDir.mkdir();
        volume = new FilesystemCacheVolume(volDir, "bench-vol");

        // Create test files with realistic sizes
        fileNames = new String[FILE_COUNT];
        byte[] data = new byte[FILE_SIZE_KB * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        for (int i = 0; i < FILE_COUNT; i++) {
            fileNames[i] = "dataset-" + i + "/datafile.dat";
            File f = new File(volDir, fileNames[i]);
            f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), data);
        }
    }

    @Test
    void benchmarkConcurrentReads() throws Exception {
        System.out.println();
        System.out.println("=============================================================");
        System.out.println("  FilesystemCacheVolume Concurrent Read Benchmark");
        System.out.println("  Files: " + FILE_COUNT + " x " + FILE_SIZE_KB + "KB");
        System.out.println("  Reads per thread: " + READS_PER_THREAD);
        System.out.println("=============================================================");
        System.out.println();
        System.out.printf("  %-10s  %10s  %10s  %12s  %10s%n",
                "Threads", "Total ms", "Reads/sec", "MB/sec", "Speedup");
        System.out.println("  " + "-".length() + "-----------------------------------------------------------");

        double baselineReadsPerSec = 0;

        for (int threadCount : THREAD_COUNTS) {
            long elapsed = runConcurrentReads(threadCount);

            int totalReads = threadCount * READS_PER_THREAD;
            double seconds = elapsed / 1000.0;
            double readsPerSec = totalReads / seconds;
            double mbPerSec = (totalReads * FILE_SIZE_KB / 1024.0) / seconds;

            if (threadCount == 1) {
                baselineReadsPerSec = readsPerSec;
            }
            double speedup = readsPerSec / baselineReadsPerSec;

            System.out.printf("  %-10d  %8d ms  %10.1f  %10.1f  %9.2fx%n",
                    threadCount, elapsed, readsPerSec, mbPerSec, speedup);
        }

        System.out.println();
        System.out.println("  If synchronized: speedup stays near 1.0x regardless of threads.");
        System.out.println("  Without synchronized: speedup should scale with thread count.");
        System.out.println("=============================================================");
        System.out.println();

        assertTrue(true, "Benchmark completed — compare output before/after the change");
    }

    @Test
    void benchmarkLatencyDistribution() throws Exception {
        int threadCount = 16;
        int totalReads = threadCount * READS_PER_THREAD;

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(totalReads));
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    barrier.await(); // all threads start together
                    byte[] buf = new byte[65536];
                    for (int r = 0; r < READS_PER_THREAD; r++) {
                        String file = fileNames[(threadId * READS_PER_THREAD + r) % FILE_COUNT];
                        long start = System.nanoTime();
                        try (InputStream is = volume.getStream(file)) {
                            while (is.read(buf) != -1) { /* drain */ }
                        }
                        long nanos = System.nanoTime() - start;
                        latencies.add(nanos);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        // Sort and compute percentiles
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        System.out.println();
        System.out.println("=============================================================");
        System.out.println("  Latency Distribution (" + threadCount + " threads, " + totalReads + " reads)");
        System.out.println("=============================================================");
        System.out.printf("  p50:  %8.2f ms%n", percentile(sorted, 50) / 1_000_000.0);
        System.out.printf("  p90:  %8.2f ms%n", percentile(sorted, 90) / 1_000_000.0);
        System.out.printf("  p99:  %8.2f ms%n", percentile(sorted, 99) / 1_000_000.0);
        System.out.printf("  max:  %8.2f ms%n", sorted.get(sorted.size() - 1) / 1_000_000.0);
        System.out.println("=============================================================");
        System.out.println();

        assertTrue(true, "Latency benchmark completed — compare output before/after the change");
    }

    private long runConcurrentReads(int threadCount) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicLong totalBytes = new AtomicLong(0);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    barrier.await(); // all threads start simultaneously
                    byte[] buf = new byte[65536];
                    for (int r = 0; r < READS_PER_THREAD; r++) {
                        // Each thread reads different files to avoid OS page cache bias
                        String file = fileNames[(threadId * READS_PER_THREAD + r) % FILE_COUNT];
                        try (InputStream is = volume.getStream(file)) {
                            int n;
                            while ((n = is.read(buf)) != -1) {
                                totalBytes.addAndGet(n);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        return System.currentTimeMillis() - startTime;
    }

    private static long percentile(List<Long> sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }
}
