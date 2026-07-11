package com.summerbuddies.voicetrans.client;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Client-only preferences. These are the source of truth on the player's machine and are pushed to
 * the server via {@link com.summerbuddies.voicetrans.net.UpdatePrefsPacket}. A config GUI (F6) edits
 * the same values.
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> SPOKEN_LANGUAGE;
    public static final ForgeConfigSpec.ConfigValue<String> SUBTITLE_LANGUAGE;
    public static final ForgeConfigSpec.BooleanValue VOICE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CHAT_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BACKGROUND_COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Voice Translate — player preferences (also editable from the in-game config screen).");
        b.push("languages");

        SPOKEN_LANGUAGE = b
                .comment("ISO code of the language YOU speak (e.g. es, en, fr). Used so the server can",
                        "skip transcription entirely when nobody nearby needs a translation.")
                .define("spokenLanguage", "en");

        SUBTITLE_LANGUAGE = b
                .comment("ISO code you want to READ voice subtitles and text chat in (e.g. es, en, fr).")
                .define("subtitleLanguage", "en");

        b.pop();
        b.push("toggles");

        VOICE_ENABLED = b
                .comment("Show translated voice subtitles above speakers.")
                .define("voiceEnabled", true);

        CHAT_ENABLED = b
                .comment("Translate text chat into your language.")
                .define("chatEnabled", true);

        b.pop();
        b.push("appearance");

        TEXT_COLOR = b
                .comment("Subtitle text colour, RGB hex (e.g. FFFFFF white, FFD700 gold, 55FF55 green).")
                .define("textColor", "FFFFFF");

        BACKGROUND_COLOR = b
                .comment("Subtitle background colour, ARGB hex (alpha first; e.g. 40000000 = ~25% black,",
                        "00000000 = fully transparent / no background).")
                .define("backgroundColor", "40000000");

        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}

    /** Convenience for building a sync packet from current config values. */
    public static Pair<String, String> languages() {
        return Pair.of(SPOKEN_LANGUAGE.get(), SUBTITLE_LANGUAGE.get());
    }

    /** Configured text colour as 0xRRGGBB (no alpha); falls back to white on a bad value. */
    public static int textColorRGB() {
        return parseHex(TEXT_COLOR.get(), 0xFFFFFF) & 0xFFFFFF;
    }

    /** Configured background colour as 0xAARRGGBB; falls back to translucent black. */
    public static int backgroundColorARGB() {
        return parseHex(BACKGROUND_COLOR.get(), 0x40000000);
    }

    private static int parseHex(String value, int fallback) {
        try {
            return (int) Long.parseLong(value.trim().replace("#", ""), 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
