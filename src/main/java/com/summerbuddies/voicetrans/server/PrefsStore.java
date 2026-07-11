package com.summerbuddies.voicetrans.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side cache of every connected player's {@link Prefs}, keyed by UUID.
 *
 * <p>Populated by {@link com.summerbuddies.voicetrans.net.UpdatePrefsPacket} when a client connects
 * or changes settings. Read by the savings gate and the voice/chat translation pipelines. Accessed
 * from both the SVC audio thread and the server thread, hence the concurrent map.
 */
public final class PrefsStore {

    private static final Map<UUID, Prefs> PREFS = new ConcurrentHashMap<>();

    private PrefsStore() {}

    public static void put(UUID player, Prefs prefs) {
        PREFS.put(player, prefs);
    }

    public static Prefs get(UUID player) {
        return PREFS.getOrDefault(player, Prefs.DEFAULT);
    }

    public static void remove(UUID player) {
        PREFS.remove(player);
    }
}
