package com.summerbuddies.voicetrans.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.summerbuddies.voicetrans.VoiceTransMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keybind to open the Voice Translate settings screen. Unbound by default (to avoid clashing with
 * Simple Voice Chat's keys) — the player assigns it in Controls.
 */
public final class Keybinds {

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.voicetrans.open_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            "key.categories.voicetrans");

    private Keybinds() {}

    @Mod.EventBusSubscriber(modid = VoiceTransMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SETTINGS);
        }
    }

    @Mod.EventBusSubscriber(modid = VoiceTransMod.MODID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                while (OPEN_SETTINGS.consumeClick()) {
                    mc.setScreen(new ConfigScreen(null));
                }
            }
        }
    }
}
