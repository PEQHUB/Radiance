package com.radiance.client.texture.v4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * Pixel staging happens synchronously from the mixin hook (which has NativeImage access).
 * The scheduler tracks generations, provides the upload entry point, and commits.
 */
public final class TextureLoadScheduler {

    private static final ConcurrentLinkedQueue<ActiveSchedule> ACTIVE_SCHEDULES = new ConcurrentLinkedQueue<>();
    private static final AtomicLong TOTAL_SCHEDULED = new AtomicLong(0L);
    private static final AtomicLong TOTAL_COMPLETED = new AtomicLong(0L);
    private static final AtomicLong TOTAL_FAILED = new AtomicLong(0L);

    /** Per-tier upload statistics for the current generation. */
    private static volatile TierUploadStats currentTierStats;

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
        currentTierStats = new TierUploadStats();

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

    /**
     * Upload a single tier's pixel data to native. Called from the mixin hook
     * which has access to NativeImage pixel data.
     *
     * @param generation active texture load generation
     * @param tierIndex  tier index (0=T16, 1=T32, ... 6=T1024)
     * @param pageHint   page hint (-1 for native allocation)
     * @param startLayerHint start layer hint (-1 for native allocation)
     * @param layerCount number of layers in this upload
     * @param tierSize   pixel dimension of the tier (16, 32, 64, ...)
     * @param albedo     direct ByteBuffer with albedo RGBA8 pixel data
     * @param specular   direct ByteBuffer with specular data (or null)
     * @param normal     direct ByteBuffer with normal data (or null)
     * @param flag       direct ByteBuffer with flag/emissive data (or null)
     * @param visible    whether this upload is visible (affects first-frame priority)
     * @return true if the upload was accepted by native
     */
    public static boolean uploadTierPage(long generation, int tierIndex, int pageHint,
                                          int startLayerHint, int layerCount, int tierSize,
                                          ByteBuffer albedo, ByteBuffer specular,
                                          ByteBuffer normal, ByteBuffer flag,
                                          boolean visible) {
        if (!TextureLoadGeneration.isActive(generation)) return false;
        if (albedo == null || layerCount <= 0 || tierSize <= 0) return false;

        int channelMask = TextureArrayBridgeV4.CHANNEL_ALBEDO;
        long specPtr = 0, normPtr = 0, flagPtr = 0;
        if (specular != null && specular.isDirect()) {
            channelMask |= TextureArrayBridgeV4.CHANNEL_SPECULAR;
            specPtr = org.lwjgl.system.MemoryUtil.memAddress(specular);
        }
        if (normal != null && normal.isDirect()) {
            channelMask |= TextureArrayBridgeV4.CHANNEL_NORMAL;
            normPtr = org.lwjgl.system.MemoryUtil.memAddress(normal);
        }
        if (flag != null && flag.isDirect()) {
            channelMask |= TextureArrayBridgeV4.CHANNEL_FLAG;
            flagPtr = org.lwjgl.system.MemoryUtil.memAddress(flag);
        }

        long albedoPtr = org.lwjgl.system.MemoryUtil.memAddress(albedo);
        long bytesPerLayer = (long) tierSize * tierSize * 4;

        try {
            boolean ok = TextureArrayBridgeV4.nativeUploadTexturePageV4(
                generation,
                1, // NAMESPACE_VANILLA
                tierIndex,
                pageHint,
                startLayerHint,
                layerCount,
                tierSize,
                tierSize,
                channelMask,
                albedoPtr,
                specPtr,
                normPtr,
                flagPtr,
                bytesPerLayer,
                visible);

            // Track stats
            TierUploadStats stats = currentTierStats;
            if (stats != null) {
                stats.addLayers(tierIndex, layerCount, bytesPerLayer * layerCount);
            }

            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Get and reset the current tier upload stats (for logging). */
    public static TierUploadStats consumeTierStats() {
        TierUploadStats stats = currentTierStats;
        currentTierStats = null;
        return stats;
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
            long generation = schedule.generation;

            if (!TextureLoadGeneration.isActive(generation)) {
                schedule.fail("generation no longer active at upload start");
                return;
            }

            // Per-tier uploads are driven synchronously from the mixin hook.
            // The scheduler just waits for them to complete and then commits.

            // Commit
            if (TextureLoadGeneration.isActive(generation)) {
                boolean committed = TextureArrayBridgeV4.nativeCommitTextureLoaderV4(generation);
                if (committed) {
                    TierUploadStats stats = consumeTierStats();
                    if (stats != null) {
                        stats.logSummary(generation);
                    }
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

    /** Per-tier upload statistics. */
    public static final class TierUploadStats {
        private final long[] layerCounts = new long[7];
        private final long[] byteCounts = new long[7];

        void addLayers(int tierIndex, int layers, long bytes) {
            if (tierIndex >= 0 && tierIndex < 7) {
                layerCounts[tierIndex] += layers;
                byteCounts[tierIndex] += bytes;
            }
        }

        public long layers(int tierIndex) {
            return tierIndex >= 0 && tierIndex < 7 ? layerCounts[tierIndex] : 0;
        }

        public long bytes(int tierIndex) {
            return tierIndex >= 0 && tierIndex < 7 ? byteCounts[tierIndex] : 0;
        }

        public void logSummary(long generation) {
            String[] tierNames = {"T16", "T32", "T64", "T128", "T256", "T512", "T1024"};
            StringBuilder sb = new StringBuilder();
            sb.append("[TextureLoaderV4] Vanilla tier upload generation=").append(generation).append("\n");
            for (int i = 0; i < 7; i++) {
                if (layerCounts[i] > 0) {
                    sb.append("  ").append(tierNames[i])
                      .append(" layers=").append(layerCounts[i])
                      .append(" bytes=").append(byteCounts[i]).append("\n");
                }
            }
            System.out.println(sb.toString());
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
