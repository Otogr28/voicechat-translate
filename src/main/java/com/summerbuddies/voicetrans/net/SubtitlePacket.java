package com.summerbuddies.voicetrans.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C: a floating subtitle (and transcript line) for {@link #speaker}. The {@link #text} is ALREADY
 * translated into the receiving client's chosen language by the server (clients hold no API keys);
 * the same text feeds both the head subtitle and the right-side transcript — never translated twice.
 *
 * @param speaker     UUID of the speaking player to anchor the subtitle to.
 * @param speakerName display name of the speaker (for the transcript panel).
 * @param text        text to display, already in the receiver's language.
 * @param srcLang     source language code (for an optional "translated from" hint).
 * @param ttlMillis   how long the line should stay on screen before fading out.
 */
public record SubtitlePacket(UUID speaker, String speakerName, String text, String srcLang, int ttlMillis) {

    public static void encode(SubtitlePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.speaker);
        buf.writeUtf(msg.speakerName);
        buf.writeUtf(msg.text);
        buf.writeUtf(msg.srcLang);
        buf.writeVarInt(msg.ttlMillis);
    }

    public static SubtitlePacket decode(FriendlyByteBuf buf) {
        return new SubtitlePacket(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(SubtitlePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                // Only touch client-only classes when actually on the client.
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.summerbuddies.voicetrans.client.SubtitleStore.add(msg);
                    com.summerbuddies.voicetrans.client.TranscriptHud.add(msg);
                }));
        context.setPacketHandled(true);
    }
}
