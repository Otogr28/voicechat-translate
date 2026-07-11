package com.summerbuddies.voicetrans.net;

import com.summerbuddies.voicetrans.VoiceTransMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel and packet registry.
 *
 * <ul>
 *   <li>{@link UpdatePrefsPacket} — C2S: client tells the server its language preferences.</li>
 *   <li>{@link SubtitlePacket} — S2C: server pushes a (already-translated) floating subtitle.</li>
 * </ul>
 */
public final class Net {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(VoiceTransMod.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private Net() {}

    /** Called once during common setup (enqueued on the main thread). */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, UpdatePrefsPacket.class,
                UpdatePrefsPacket::encode, UpdatePrefsPacket::decode, UpdatePrefsPacket::handle);
        CHANNEL.registerMessage(id++, SubtitlePacket.class,
                SubtitlePacket::encode, SubtitlePacket::decode, SubtitlePacket::handle);
    }

    /** Send a subtitle to a single player. */
    public static void sendSubtitle(ServerPlayer to, SubtitlePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), packet);
    }

    /** Send this client's preferences to the server. */
    public static void sendPrefsToServer(UpdatePrefsPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
