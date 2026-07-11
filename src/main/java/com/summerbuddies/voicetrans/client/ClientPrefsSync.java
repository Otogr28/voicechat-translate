package com.summerbuddies.voicetrans.client;

import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.net.Net;
import com.summerbuddies.voicetrans.net.UpdatePrefsPacket;
import com.summerbuddies.voicetrans.server.Prefs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pushes this client's {@link ClientConfig} to the server: once on join, and again whenever the
 * config screen (F6) changes it (call {@link #send()}).
 */
@Mod.EventBusSubscriber(modid = VoiceTransMod.MODID, value = Dist.CLIENT)
public final class ClientPrefsSync {

    private ClientPrefsSync() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        send();
    }

    /** Send current preferences to the server. Safe to call any time after joining. */
    public static void send() {
        try {
            Net.sendPrefsToServer(new UpdatePrefsPacket(new Prefs(
                    ClientConfig.SPOKEN_LANGUAGE.get(),
                    ClientConfig.SUBTITLE_LANGUAGE.get(),
                    ClientConfig.VOICE_ENABLED.get(),
                    ClientConfig.CHAT_ENABLED.get())));
        } catch (Throwable t) {
            // e.g. server doesn't have the mod / channel not ready — harmless.
            VoiceTransMod.LOGGER.debug("[voicetrans] could not send prefs: {}", t.toString());
        }
    }
}
