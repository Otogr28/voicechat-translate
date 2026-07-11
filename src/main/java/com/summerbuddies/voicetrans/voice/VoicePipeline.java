package com.summerbuddies.voicetrans.voice;

import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.backend.SidecarClient;
import com.summerbuddies.voicetrans.net.Net;
import com.summerbuddies.voicetrans.net.SubtitlePacket;
import com.summerbuddies.voicetrans.server.Gate;
import com.summerbuddies.voicetrans.server.Prefs;
import com.summerbuddies.voicetrans.server.PrefsStore;
import com.summerbuddies.voicetrans.server.ServerConfig;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Receives finished speech segments from {@link SpeechSegmenter} and drives the rest of the pipeline:
 * savings gate (F3) → STT + translation via the sidecar (F4) → S2C subtitles.
 *
 * <p>F2 status: just logs the segment so we can confirm capture/decoding works end to end.
 *
 * <p>Threading: invoked on the segmenter's background thread, never the MC server thread. Anything
 * that touches live game state (player positions, sending packets) must be hopped onto the server
 * thread later (F3/F4) via {@code MinecraftServer.execute(...)}.
 */
public final class VoicePipeline {

    /** Simple Voice Chat decodes to 48 kHz, mono, signed 16-bit PCM. */
    public static final int SAMPLE_RATE = 48_000;

    private VoicePipeline() {}

    /**
     * @param api     SVC server API (for proximity queries / sending audio later)
     * @param speaker UUID of the speaking player
     * @param level   the SVC level the speaker is in (for {@code getPlayersInRange})
     * @param x,y,z   speaker position at segment end
     * @param pcm16le finished segment as 16-bit little-endian mono PCM @ 48 kHz
     */
    public static void onSegment(VoicechatServerApi api, UUID speaker, ServerLevel level,
                                 double x, double y, double z, byte[] pcm16le) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Prefs speakerPrefs = PrefsStore.get(speaker);
        String spokenLanguage = speakerPrefs.spokenLanguage();

        // The gate (and player lookups) read live state, so this runs on the server thread.
        server.execute(() -> {
            // Savings gate: only transcribe if at least one nearby listener needs a different
            // language. No nearby listeners (out of hearing range) or everyone shares the speaker's
            // language → empty → we spend zero API.
            Map<String, List<ServerPlayer>> targets =
                    Gate.evaluate(api, speaker, level, x, y, z, spokenLanguage);
            if (targets.isEmpty()) {
                VoiceTransMod.LOGGER.debug("[voicetrans] gate: skipped segment from {}", speaker);
                return;
            }

            ServerPlayer self = server.getPlayerList().getPlayer(speaker);
            String speakerName = self != null ? self.getGameProfile().getName() : "?";
            transcribeAndBroadcast(server, speaker, speakerName, spokenLanguage, pcm16le, targets);
        });
    }

    /** Off-thread STT → per-language translation → S2C subtitles. Called after the gate passes. */
    private static void transcribeAndBroadcast(MinecraftServer server, UUID speaker, String speakerName,
                                               String spokenLanguage, byte[] pcm16le,
                                               Map<String, List<ServerPlayer>> targets) {
        SidecarClient.stt(pcm16le, spokenLanguage).thenAccept(stt -> {
            if (stt.text() == null || stt.text().isBlank()) {
                return;
            }
            String srcLang = stt.lang() == null || stt.lang().isBlank() ? spokenLanguage : stt.lang();

            // One translation per distinct target language, fanned out to its listeners.
            targets.forEach((lang, listeners) ->
                    SidecarClient.translate(stt.text(), srcLang, lang).thenAccept(translated -> {
                        if (translated == null || translated.isBlank()) {
                            return;
                        }
                        SubtitlePacket packet = new SubtitlePacket(
                                speaker, speakerName, translated, srcLang,
                                ServerConfig.ttlFor(translated.length()));
                        // Sending touches connections — hop back to the server thread.
                        server.execute(() -> {
                            for (ServerPlayer listener : listeners) {
                                if (!listener.hasDisconnected()) {
                                    Net.sendSubtitle(listener, packet);
                                }
                            }
                        });
                    }));
        });
    }
}
