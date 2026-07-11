package com.summerbuddies.voicetrans.net;

import com.summerbuddies.voicetrans.server.Prefs;
import com.summerbuddies.voicetrans.server.PrefsStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: a client declares the language it speaks, the language it wants to read, and its toggles.
 * The server stores these in {@link PrefsStore} for the savings gate and the translation pipelines.
 */
public record UpdatePrefsPacket(Prefs prefs) {

    public static void encode(UpdatePrefsPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.prefs.spokenLanguage());
        buf.writeUtf(msg.prefs.subtitleLanguage());
        buf.writeBoolean(msg.prefs.voiceEnabled());
        buf.writeBoolean(msg.prefs.chatEnabled());
    }

    public static UpdatePrefsPacket decode(FriendlyByteBuf buf) {
        return new UpdatePrefsPacket(new Prefs(
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean()));
    }

    public static void handle(UpdatePrefsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                PrefsStore.put(sender.getUUID(), msg.prefs);
            }
        });
        context.setPacketHandled(true);
    }
}
