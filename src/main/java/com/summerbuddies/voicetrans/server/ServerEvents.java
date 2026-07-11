package com.summerbuddies.voicetrans.server;

import com.summerbuddies.voicetrans.VoiceTransMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side housekeeping. Drops a player's cached {@link Prefs} when they disconnect so the gate
 * never considers stale data.
 */
@Mod.EventBusSubscriber(modid = VoiceTransMod.MODID)
public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PrefsStore.remove(player.getUUID());
        }
    }
}
