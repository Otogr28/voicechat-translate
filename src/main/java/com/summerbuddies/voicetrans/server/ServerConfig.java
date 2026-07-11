package com.summerbuddies.voicetrans.server;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side config. The sidecar (STT + translation) runs on the VPS bound to localhost, so the
 * default points there. Only the server reads this — clients never talk to the sidecar.
 */
public final class ServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> SIDECAR_URL;
    public static final ForgeConfigSpec.IntValue SUBTITLE_TTL_MIN_MS;
    public static final ForgeConfigSpec.IntValue SUBTITLE_TTL_PER_CHAR_MS;
    public static final ForgeConfigSpec.IntValue SUBTITLE_TTL_MAX_MS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Voice Translate — server settings.");
        b.push("sidecar");

        SIDECAR_URL = b
                .comment("Base URL of the STT + translation sidecar (FastAPI). Localhost on the VPS.")
                .define("sidecarUrl", "http://127.0.0.1:8200");

        b.pop();
        b.push("subtitles");

        SUBTITLE_TTL_MIN_MS = b
                .comment("Minimum time (ms) a voice subtitle stays on screen.")
                .defineInRange("ttlMinMs", 2500, 500, 60_000);
        SUBTITLE_TTL_PER_CHAR_MS = b
                .comment("Extra display time (ms) added per character of the subtitle.")
                .defineInRange("ttlPerCharMs", 55, 0, 1000);
        SUBTITLE_TTL_MAX_MS = b
                .comment("Maximum time (ms) a voice subtitle stays on screen.")
                .defineInRange("ttlMaxMs", 9000, 1000, 120_000);

        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {}

    /** Display time for a subtitle of the given length, clamped to the configured bounds. */
    public static int ttlFor(int textLength) {
        int ttl = SUBTITLE_TTL_MIN_MS.get() + textLength * SUBTITLE_TTL_PER_CHAR_MS.get();
        return Math.min(ttl, SUBTITLE_TTL_MAX_MS.get());
    }
}
