package com.skyeshade.skyent.content.explosion.destruction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NuclearRayPlanningExecutor {
    private NuclearRayPlanningExecutor() {
    }

    private static final int MAX_WORKERS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(MAX_WORKERS, new ThreadFactory() {
        private final AtomicInteger nextId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Skyent Nuke Ray Worker-" + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    public static AsyncPlanningHandle submit(NuclearBlastRayPlanner planner, NuclearBlockSnapshot snapshot, int workerCount) {
        int actualWorkers = Math.max(1, Math.min(workerCount, MAX_WORKERS));
        AtomicBoolean canceled = new AtomicBoolean(false);
        List<CompletableFuture<NuclearBlastRayPlanner.WorkerResult>> futures = new ArrayList<>(actualWorkers);
        int totalRays = planner.totalRays();
        int raysPerWorker = Math.max(1, (totalRays + actualWorkers - 1) / actualWorkers);
        long startNs = System.nanoTime();

        for (int worker = 0; worker < actualWorkers; worker++) {
            int startRay = worker * raysPerWorker;
            int endRay = Math.min(totalRays, startRay + raysPerWorker);
            if (startRay >= endRay) {
                break;
            }
            CompletableFuture<NuclearBlastRayPlanner.WorkerResult> future = CompletableFuture.supplyAsync(
                    () -> planner.planRayRange(snapshot, startRay, endRay, canceled),
                    EXECUTOR
            );
            futures.add(future);
        }

        return new AsyncPlanningHandle(snapshot, futures, canceled, startNs);
    }

    public static final class AsyncPlanningHandle {
        private final NuclearBlockSnapshot snapshot;
        private final List<CompletableFuture<NuclearBlastRayPlanner.WorkerResult>> futures;
        private final AtomicBoolean canceled;
        private final long startNs;

        private AsyncPlanningHandle(
                NuclearBlockSnapshot snapshot,
                List<CompletableFuture<NuclearBlastRayPlanner.WorkerResult>> futures,
                AtomicBoolean canceled,
                long startNs
        ) {
            this.snapshot = snapshot;
            this.futures = futures;
            this.canceled = canceled;
            this.startNs = startNs;
        }

        public boolean isDone() {
            for (CompletableFuture<NuclearBlastRayPlanner.WorkerResult> future : futures) {
                if (!future.isDone()) {
                    return false;
                }
            }
            return true;
        }

        public AsyncPlanningResult collect() {
            List<NuclearBlastRayPlanner.WorkerResult> results = new ArrayList<>(futures.size());
            double slowestWorkerMs = 0.0D;
            double fastestWorkerMs = Double.POSITIVE_INFINITY;
            double totalWorkerMs = 0.0D;
            for (CompletableFuture<NuclearBlastRayPlanner.WorkerResult> future : futures) {
                NuclearBlastRayPlanner.WorkerResult result = future.join();
                results.add(result);
                slowestWorkerMs = Math.max(slowestWorkerMs, result.elapsedMs());
                fastestWorkerMs = Math.min(fastestWorkerMs, result.elapsedMs());
                totalWorkerMs += result.elapsedMs();
            }
            results.sort(Comparator.comparingInt(NuclearBlastRayPlanner.WorkerResult::startRayInclusive));
            return new AsyncPlanningResult(
                    snapshot,
                    results,
                    futures.size(),
                    (System.nanoTime() - startNs) / 1_000_000.0D,
                    slowestWorkerMs,
                    fastestWorkerMs == Double.POSITIVE_INFINITY ? 0.0D : fastestWorkerMs,
                    totalWorkerMs,
                    canceled.get()
            );
        }

        public void cancel() {
            canceled.set(true);
        }

        public int workerCount() {
            return futures.size();
        }
    }

    public record AsyncPlanningResult(
            NuclearBlockSnapshot snapshot,
            List<NuclearBlastRayPlanner.WorkerResult> workerResults,
            int workerCount,
            double wallMs,
            double slowestWorkerMs,
            double fastestWorkerMs,
            double totalWorkerMs,
            boolean canceled
    ) {
        public int raysProcessed() {
            int total = 0;
            for (NuclearBlastRayPlanner.WorkerResult result : workerResults) {
                total += result.raysProcessed();
            }
            return total;
        }

        public int stepsProcessed() {
            int total = 0;
            for (NuclearBlastRayPlanner.WorkerResult result : workerResults) {
                total += result.stepsProcessed();
            }
            return total;
        }

        public String rayRanges() {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < workerResults.size(); index++) {
                NuclearBlastRayPlanner.WorkerResult result = workerResults.get(index);
                if (index > 0) {
                    builder.append(',');
                }
                builder.append('[')
                        .append(result.startRayInclusive())
                        .append('-')
                        .append(result.endRayExclusive())
                        .append(')');
            }
            return builder.toString();
        }
    }
}
