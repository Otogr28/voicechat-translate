package com.summerbuddies.voicetrans.server;

/**
 * Per-player preferences, declared client-side and synced to the server via
 * {@link com.summerbuddies.voicetrans.net.UpdatePrefsPacket}.
 *
 * @param spokenLanguage   ISO code (e.g. "es") of the language the player SPEAKS. Used by the
 *                         savings gate to decide — without running STT — whether any nearby
 *                         listener needs a translation, and as a language hint for STT.
 * @param subtitleLanguage ISO code the player wants to READ (voice subtitles + text chat).
 * @param voiceEnabled     whether the player wants voice subtitles at all.
 * @param chatEnabled      whether the player wants text chat translated.
 */
public record Prefs(
        String spokenLanguage,
        String subtitleLanguage,
        boolean voiceEnabled,
        boolean chatEnabled
) {
    /** Fallback used for players who haven't synced prefs yet. */
    public static final Prefs DEFAULT = new Prefs("en", "en", true, true);
}
