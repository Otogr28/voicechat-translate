package com.summerbuddies.voicetrans;

import com.mojang.logging.LogUtils;
import com.summerbuddies.voicetrans.client.ClientConfig;
import com.summerbuddies.voicetrans.net.Net;
import com.summerbuddies.voicetrans.server.ServerConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point of Voice Translate.
 *
 * <p>Real-time translation of Simple Voice Chat audio (and text chat), QSMP-style: subtitles float
 * above the speaker's head in each listener's chosen language. All cloud compute (speech-to-text and
 * translation) is centralized on the server side / a sidecar on the VPS — clients never hold API keys.
 *
 * <p>See the project plan for the full architecture. This class only wires up config and networking;
 * the heavy lifting lives in the {@code voice}, {@code server}, {@code net}, {@code client} and
 * {@code backend} packages.
 */
@Mod(VoiceTransMod.MODID)
public class VoiceTransMod {

    public static final String MODID = "voicetrans";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VoiceTransMod(FMLJavaModLoadingContext context) {
        // Client-only preferences (spoken language, subtitle language, toggles).
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Server-only settings (sidecar URL, subtitle timing).
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        context.getModEventBus().addListener(this::commonSetup);

        LOGGER.info("[voicetrans] constructed");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register the mod's network channel + packets on both sides.
        event.enqueueWork(Net::register);
        LOGGER.info("[voicetrans] common setup done");
    }
}
