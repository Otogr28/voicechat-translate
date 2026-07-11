package com.summerbuddies.voicetrans.voice;

import com.summerbuddies.voicetrans.VoiceTransMod;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

import java.util.UUID;

/**
 * Simple Voice Chat plugin entry point. Auto-discovered by SVC via {@link ForgeVoicechatPlugin}.
 *
 * <p>Listens to {@link MicrophonePacketEvent} (fires server-side for every microphone frame a player
 * sends) and forwards the Opus frames to {@link SpeechSegmenter}, which decodes and segments them.
 */
@ForgeVoicechatPlugin
public class VoicechatPluginImpl implements VoicechatPlugin {

    private VoicechatApi api;
    private volatile SpeechSegmenter segmenter;

    @Override
    public String getPluginId() {
        return VoiceTransMod.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        VoiceTransMod.LOGGER.info("[voicetrans] Simple Voice Chat plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return; // e.g. audio injected by another plugin
        }
        ServerPlayer player = sender.getPlayer();
        if (player == null) {
            return;
        }
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return; // keepalive / end-of-voice marker
        }

        UUID speaker = player.getUuid();
        ServerLevel level = player.getServerLevel();
        Position pos = player.getPosition();

        segmenter(event.getVoicechat()).onFrame(speaker, opus, level, pos);
    }

    private SpeechSegmenter segmenter(de.maxhenkel.voicechat.api.VoicechatServerApi serverApi) {
        SpeechSegmenter seg = segmenter;
        if (seg == null) {
            synchronized (this) {
                if (segmenter == null) {
                    segmenter = new SpeechSegmenter(serverApi, api);
                }
                seg = segmenter;
            }
        }
        return seg;
    }
}
