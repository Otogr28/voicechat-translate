package com.summerbuddies.voicetrans.server;

import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.backend.SidecarClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Translates text chat per recipient. Cancels the vanilla broadcast and re-sends each online player
 * a copy in their own language (only the translation — decision: no original shown).
 *
 * <p>Savings, mirroring the voice gate: a recipient who shares the sender's declared spoken language
 * (or has chat translation off, or is the sender) gets the original and triggers no API call.
 *
 * <p>NoChatReports is in the pack, so dropping signed chat in favour of system messages is fine here.
 */
@Mod.EventBusSubscriber(modid = VoiceTransMod.MODID)
public final class ChatTranslator {

    private ChatTranslator() {}

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String raw = event.getRawText();
        String name = sender.getGameProfile().getName();
        String senderLang = normalize(PrefsStore.get(sender.getUUID()).spokenLanguage());
        MinecraftServer server = sender.server;

        // We deliver per-recipient copies ourselves.
        event.setCanceled(true);

        Component original = Component.literal("<" + name + "> " + raw);

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            Prefs prefs = PrefsStore.get(recipient.getUUID());
            String target = normalize(prefs.subtitleLanguage());

            if (recipient == sender || !prefs.chatEnabled() || target.equals(senderLang)) {
                recipient.sendSystemMessage(original);
                continue;
            }

            SidecarClient.translate(raw, senderLang.isEmpty() ? null : senderLang, target)
                    .thenAccept(translated -> {
                        String shown = (translated == null || translated.isBlank()) ? raw : translated;
                        Component msg = Component.literal("<" + name + "> " + shown);
                        server.execute(() -> {
                            if (!recipient.hasDisconnected()) {
                                recipient.sendSystemMessage(msg);
                            }
                        });
                    });
        }
    }

    private static String normalize(String lang) {
        return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
    }
}
