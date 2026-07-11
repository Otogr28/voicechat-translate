package com.summerbuddies.voicetrans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.summerbuddies.voicetrans.VoiceTransMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Draws floating subtitles above speakers, QSMP-style: camera-facing text just above the player's
 * name tag, fading out as each line's TTL runs out. Lines are stacked newest-at-bottom and limited
 * per speaker to avoid clutter.
 *
 * <p>The text in {@link SubtitleStore} is already in this client's language (translated server-side).
 */
@Mod.EventBusSubscriber(modid = VoiceTransMod.MODID, value = Dist.CLIENT)
public final class SubtitleRenderer {

    private static final int MAX_LINES_PER_SPEAKER = 3;
    private static final float SCALE = 0.025f;          // same scale vanilla uses for name tags
    private static final double HEIGHT_ABOVE_NAME = 0.45;
    private static final double LINE_SPACING = 0.28;    // world units between stacked lines
    private static final int FADE_MS = 800;             // fade window at end of life

    private SubtitleRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        // hideGui (F1) hides the in-world voice subtitles too — also lets a cinematic (StoryKit sets
        // hideGui=true) suppress them so only its frame shows.
        if (level == null || mc.options.hideGui || !ClientConfig.VOICE_ENABLED.get()) {
            return;
        }
        List<SubtitleStore.Entry> active = SubtitleStore.active();
        if (active.isEmpty()) {
            return;
        }

        float partial = event.getPartialTick();
        var camPos = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = System.currentTimeMillis();

        // Group lines by speaker, most recent last.
        for (UUID speaker : distinctSpeakers(active)) {
            Player player = level.getPlayerByUUID(speaker);
            if (player == null || player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            List<SubtitleStore.Entry> lines = linesFor(active, speaker);

            double px = Mth.lerp(partial, player.xo, player.getX());
            double py = Mth.lerp(partial, player.yo, player.getY());
            double pz = Mth.lerp(partial, player.zo, player.getZ());
            double baseY = py + player.getBbHeight() + HEIGHT_ABOVE_NAME
                    + (player instanceof AbstractClientPlayer ? 0.25 : 0.0);

            for (int i = 0; i < lines.size(); i++) {
                SubtitleStore.Entry entry = lines.get(i);
                // oldest highest, newest just above the head
                double y = baseY + (lines.size() - 1 - i) * LINE_SPACING;
                int alpha = alphaFor(entry, now);
                if (alpha <= 4) {
                    continue;
                }
                drawLine(mc, pose, buffers, entry.text(), alpha,
                        px - camPos.x, y - camPos.y, pz - camPos.z);
            }
        }
        buffers.endBatch();
    }

    private static void drawLine(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                                 String text, int alpha, double dx, double dy, double dz) {
        Font font = mc.font;
        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SCALE, -SCALE, SCALE);

        Matrix4f matrix = pose.last().pose();
        // Text: configured RGB, modulated by the fade alpha.
        int color = (alpha << 24) | ClientConfig.textColorRGB();
        // Background: configured ARGB, with its own alpha scaled by the fade.
        int baseBg = ClientConfig.backgroundColorARGB();
        int bgAlpha = ((baseBg >>> 24) * alpha) / 0xFF;
        int bg = (bgAlpha << 24) | (baseBg & 0xFFFFFF);
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, color, false, matrix, buffers,
                Font.DisplayMode.NORMAL, bg, 0xF000F0);
        pose.popPose();
    }

    private static int alphaFor(SubtitleStore.Entry entry, long now) {
        long remaining = entry.expiresAtMillis() - now;
        if (remaining <= 0) {
            return 0;
        }
        if (remaining >= FADE_MS) {
            return 0xFF;
        }
        return (int) (0xFF * remaining / FADE_MS);
    }

    private static List<UUID> distinctSpeakers(List<SubtitleStore.Entry> active) {
        List<UUID> ids = new ArrayList<>();
        for (SubtitleStore.Entry e : active) {
            if (!ids.contains(e.speaker())) {
                ids.add(e.speaker());
            }
        }
        return ids;
    }

    private static List<SubtitleStore.Entry> linesFor(List<SubtitleStore.Entry> active, UUID speaker) {
        List<SubtitleStore.Entry> lines = new ArrayList<>();
        for (SubtitleStore.Entry e : active) {
            if (e.speaker().equals(speaker)) {
                lines.add(e);
            }
        }
        lines.sort(Comparator.comparingLong(SubtitleStore.Entry::expiresAtMillis));
        // keep the newest MAX_LINES_PER_SPEAKER (largest expiry)
        if (lines.size() > MAX_LINES_PER_SPEAKER) {
            lines = new ArrayList<>(lines.subList(lines.size() - MAX_LINES_PER_SPEAKER, lines.size()));
        }
        return lines;
    }
}
