package com.summerbuddies.voicetrans.voice;

import com.summerbuddies.voicetrans.VoiceTransMod;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Turns the stream of per-frame {@code MicrophonePacketEvent}s into discrete speech segments.
 *
 * <p>Simple Voice Chat delivers ~20 ms Opus frames while a player talks but gives no explicit
 * "stopped talking" signal, so we detect end-of-speech as a gap in frames. Each speaker has its own
 * stateful {@link OpusDecoder} (Opus is a continuous codec — one decoder per stream, reused across
 * frames). PCM is accumulated as 16-bit little-endian and flushed to {@link VoicePipeline}:
 * <ul>
 *   <li>on silence (no frame for {@value #SILENCE_MS} ms) — flush and close the stream, or</li>
 *   <li>on reaching {@value #MAX_SEGMENT_MS} ms while still talking — flush but keep the decoder
 *       going (so long monologues still produce timely subtitles).</li>
 * </ul>
 *
 * <p>All work happens off the MC server thread (on the SVC voice thread + one scheduler thread).
 */
public final class SpeechSegmenter {

    private static final int SILENCE_MS = 250;        // gap that ends a segment (lower = snappier)
    private static final int MAX_SEGMENT_MS = 1_200;  // force a flush during long speech (lower = phrases show sooner)
    private static final int TICK_MS = 80;            // how often we scan for idle speakers
    private static final int MAX_BYTES = MAX_SEGMENT_MS * VoicePipeline.SAMPLE_RATE / 1000 * 2;

    private final VoicechatServerApi serverApi;
    private final VoicechatApi api;
    private final Map<UUID, Buffer> buffers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "voicetrans-segmenter");
                t.setDaemon(true);
                return t;
            });

    public SpeechSegmenter(VoicechatServerApi serverApi, VoicechatApi api) {
        this.serverApi = serverApi;
        this.api = api;
        scheduler.scheduleAtFixedRate(this::scanIdle, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** Per-speaker accumulation state. Guarded by its own monitor. */
    private final class Buffer {
        final OpusDecoder decoder = api.createDecoder();
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long lastFrameMs = System.currentTimeMillis();
        ServerLevel level;
        double x, y, z;
    }

    /** Called from the SVC voice thread for each decoded microphone frame. */
    public void onFrame(UUID speaker, byte[] opus, ServerLevel level, Position pos) {
        Buffer buf = buffers.computeIfAbsent(speaker, k -> new Buffer());
        synchronized (buf) {
            short[] samples = buf.decoder.decode(opus);
            for (short s : samples) {
                buf.pcm.write(s & 0xFF);
                buf.pcm.write((s >> 8) & 0xFF);
            }
            buf.lastFrameMs = System.currentTimeMillis();
            buf.level = level;
            if (pos != null) {
                buf.x = pos.getX();
                buf.y = pos.getY();
                buf.z = pos.getZ();
            }
            if (buf.pcm.size() >= MAX_BYTES) {
                emit(speaker, buf);   // flush but keep the stream going
            }
        }
    }

    /** Scheduler thread: flush and close any speaker idle longer than {@link #SILENCE_MS}. */
    private void scanIdle() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Buffer> e : buffers.entrySet()) {
            Buffer buf = e.getValue();
            synchronized (buf) {
                if (now - buf.lastFrameMs >= SILENCE_MS) {
                    emit(e.getKey(), buf);
                    buf.decoder.close();
                    buffers.remove(e.getKey());
                }
            }
        }
    }

    /** Emit the accumulated PCM as a segment and clear the accumulator. Caller holds buf's monitor. */
    private void emit(UUID speaker, Buffer buf) {
        if (buf.pcm.size() == 0) {
            return;
        }
        byte[] pcm = buf.pcm.toByteArray();
        buf.pcm.reset();
        try {
            VoicePipeline.onSegment(serverApi, speaker, buf.level, buf.x, buf.y, buf.z, pcm);
        } catch (Throwable t) {
            VoiceTransMod.LOGGER.error("[voicetrans] pipeline error for {}", speaker, t);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
        buffers.values().forEach(b -> {
            synchronized (b) {
                b.decoder.close();
            }
        });
        buffers.clear();
    }
}
