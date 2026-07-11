package com.summerbuddies.voicetrans.client;

import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.net.SubtitlePacket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side holding area for active floating subtitles. {@link SubtitlePacket} adds entries here;
 * the renderer (F6) reads and draws them above the matching entity, then drops expired ones.
 */
public final class SubtitleStore {

    /** One subtitle line anchored to a speaking player. */
    public record Entry(UUID speaker, String text, String srcLang, long expiresAtMillis) {}

    private static final List<Entry> ACTIVE = new CopyOnWriteArrayList<>();

    private SubtitleStore() {}

    public static void add(SubtitlePacket packet) {
        long expiry = System.currentTimeMillis() + packet.ttlMillis();
        ACTIVE.add(new Entry(packet.speaker(), packet.text(), packet.srcLang(), expiry));
        // F6 replaces this log with actual rendering.
        VoiceTransMod.LOGGER.info("[voicetrans] subtitle for {}: \"{}\" ({})",
                packet.speaker(), packet.text(), packet.srcLang());
    }

    /** Active, non-expired subtitles. Prunes expired entries as a side effect. */
    public static List<Entry> active() {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(e -> e.expiresAtMillis() <= now);
        return ACTIVE;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
