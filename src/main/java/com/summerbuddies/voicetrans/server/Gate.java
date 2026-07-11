package com.summerbuddies.voicetrans.server;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The savings gate — the core cost control.
 *
 * <p>Given a speaker and where they are, it returns which target languages are actually needed by
 * nearby listeners. If the result is empty, the caller must NOT run speech-to-text (nobody nearby
 * needs a translation, e.g. two players who share the speaker's language). This is why each player
 * declares the language they SPEAK: we can decide before spending any STT.
 *
 * <p>Must be called on the server thread — {@link VoicechatServerApi#getPlayersInRange} reads live
 * level state.
 */
public final class Gate {

    private Gate() {}

    /**
     * @return map of {@code targetLanguage -> listeners who want it}; empty means "skip transcription".
     */
    public static Map<String, List<net.minecraft.server.level.ServerPlayer>> evaluate(
            VoicechatServerApi api, UUID speaker, ServerLevel level,
            double x, double y, double z, String spokenLanguage) {

        Map<String, List<net.minecraft.server.level.ServerPlayer>> targets = new HashMap<>();
        if (level == null) {
            return targets;
        }

        double range = api.getBroadcastRange();
        if (range <= 0) {
            range = 48.0; // SVC default proximity distance fallback
        }

        // SVC requires its OWN Position implementation (it casts to PositionImpl internally),
        // so build it via the API instead of a custom Position.
        Collection<de.maxhenkel.voicechat.api.ServerPlayer> nearby =
                api.getPlayersInRange(level, api.createPosition(x, y, z), range,
                        p -> !p.getUuid().equals(speaker));

        String spoken = normalize(spokenLanguage);
        for (de.maxhenkel.voicechat.api.ServerPlayer svcPlayer : nearby) {
            Prefs prefs = PrefsStore.get(svcPlayer.getUuid());
            if (!prefs.voiceEnabled()) {
                continue; // listener doesn't want voice subtitles
            }
            String target = normalize(prefs.subtitleLanguage());
            if (target.equals(spoken)) {
                continue; // same language → no translation, no STT needed for this listener
            }
            if (svcPlayer.getPlayer() instanceof net.minecraft.server.level.ServerPlayer mcPlayer) {
                targets.computeIfAbsent(target, k -> new ArrayList<>()).add(mcPlayer);
            }
        }
        return targets;
    }

    private static String normalize(String lang) {
        return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
    }
}
