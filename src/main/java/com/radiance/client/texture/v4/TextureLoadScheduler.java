package com.radiance.client.texture.v4;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;

/**
 * Schedules and tracks texture upload work for a generation.
 *
 * The scheduler receives a TextureLoadGraph and drives the upload pipeline:
 *   1. Serialize manifest to native via nativeBeginTextureLoaderV4
 *   2. Upload per-tier page chunks via nativeUploadTexturePageV4
 *   3. Commit via nativeCommitTextureLoaderV4
 *
 * Phase 2/3: Vanilla sprite upload path.
 * Phase 5: CTM material upload with coalescing.
 */
public final class TextureLoadScheduler {

    private static final ConcurrentLinkedQueue<ActiveSchedule> ACTIVE_SCHEDULES = new ConcurrentLinkedQueue<>();
    private static final AtomicLong TOTAL_SCHEDULED = new AtomicLong(0L);
    private static final AtomicLong TOTAL_COMPLETED = new AtomicLong(0L);
    private static final AtomicLong TOTAL_FAILED = new AtomicLong(0L);

    private TextureLoadScheduler() {}

    /**
     * Start processing a load graph. Returns a future that completes
     * when the generation's uploads are committed or cancelled.
     */
    public static CompletableFuture<Boolean> start(TextureLoadGraph graph) {
        long generation = graph.generation();
        TextureLoadGeneration.requireActive(generation, "TextureLoadScheduler.start");

        ActiveSchedule schedule = new ActiveSchedule(generation, graph);
        ACTIVE_SCHEDULES.add(schedule);
        TOTAL_SCHEDULED.incrementAndGet();

        // Begin native side
        try {
            boolean began = TextureArrayBridgeV4.nativeBeginTextureLoaderV4(
                generation, 0, 0);
            if (!began) {
                schedule.fail("nativeBeginTextureLoaderV4 returned false");
                return schedule.future;
            }
        } catch (Throwable t) {
            schedule.fail("nativeBeginTextureLoaderV4 threw: " + t.getMessage());
            return schedule.future;
        }

        // Schedule the actual upload work on a worker thread
        Thread worker = new Thread(() -> executeUpload(schedule), "TextureLoadV4-" + generation);
        worker.setDaemon(true);
        worker.start();

        return schedule.future;
    }

    /** Cancel all active schedules for a generation. */
    public static void cancelGeneration(long generation) {
        for (ActiveSchedule schedule : ACTIVE_SCHEDULES) {
            if (schedule.generation == generation && !schedule.done.get()) {
                schedule.cancel();
            }
        }
    }

    public static long totalScheduled() { return TOTAL_SCHEDULED.get(); }
    public static long totalCompleted() { return TOTAL_COMPLETED.get(); }
    public static long totalFailed() { return TOTAL_FAILED.get(); }

    private static void executeUpload(ActiveSchedule schedule) {
        try {
            TextureLoadGraph graph = schedule.graph;
            long generation = schedule.generation;

            if (!TextureLoadGeneration.isActive(generation)) {
                schedule.fail("generation no longer active at upload start");
                return;
            }

            // Phase 3: Upload per-tier page chunks
            // The actual pixel staging happens here, driven by the manifest
            // and the sprite data from the block atlas bypass hook.
            // For now, the upload is driven synchronously from the mixin hook
            // which has access to the NativeImage data.
            // The scheduler's role is generation tracking, cancellation, and
            // future completion.

            // Commit
            if (TextureLoadGeneration.isActive(generation)) {
                boolean committed = TextureArrayBridgeV4.nativeCommitTextureLoaderV4(generation);
                if (committed) {
                    schedule.complete();
                } else {
                    schedule.fail("nativeCommitTextureLoaderV4 returned false");
                }
            } else {
                schedule.cancel();
            }
        } catch (Throwable t) {
            schedule.fail("executeUpload threw: " + t.getMessage());
        }
    }

    private static class ActiveSchedule {
        final long generation;
        final TextureLoadGraph graph;
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final AtomicBoolean done = new AtomicBoolean(false);

        ActiveSchedule(long generation, TextureLoadGraph graph) {
            this.generation = generation;
            this.graph = graph;
        }

        void complete() {
            if (done.compareAndSet(false, true)) {
                future.complete(true);
                TOTAL_COMPLETED.incrementAndGet();
                ACTIVE_SCHEDULES.remove(this);
            }
        }

        void fail(String reason) {
            if (done.compareAndSet(false, true)) {
                future.complete(false);
                TOTAL_FAILED.incrementAndGet();
                ACTIVE_SCHEDULES.remove(this);
                TextureLoadGeneration.cancelActive();
            }
        }

        void cancel() {
            if (done.compareAndSet(false, true)) {
                future.complete(false);
                TOTAL_FAILED.incrementAndGet();
                ACTIVE_SCHEDULES.remove(this);
            }
        }
    }
}
