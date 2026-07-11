package com.summerbuddies.voicetrans.client;

import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.net.SubtitlePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Right-side conversation transcript, Twitch-chat style: each translated voice line is shown as
 * "Name: text", newest at the bottom, older lines stacking upward and fading out. Uses the SAME
 * translated text the server already sent for the floating subtitle — no second translation.
 */
@Mod.EventBusSubscriber(modid = VoiceTransMod.MODID, value = Dist.CLIENT)
public final class TranscriptHud {

    private record Line(String name, String text, long expiresAtMillis) {}

    private static final int MAX_LINES = 10;
    private static final long TTL_MS = 12_000;
    private static final long FADE_MS = 1_500;
    private static final int NAME_COLOR = 0xFFD45F;   // gold-ish
    private static final int MARGIN = 4;

    private static final Deque<Line> LINES = new ArrayDeque<>();

    private TranscriptHud() {}

    public static void add(SubtitlePacket packet) {
        synchronized (LINES) {
            LINES.addLast(new Line(packet.speakerName(), packet.text(), System.currentTimeMillis() + TTL_MS));
            while (LINES.size() > MAX_LINES) {
                LINES.removeFirst();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // Draw once per frame: piggyback on a single vanilla overlay.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.VOICE_ENABLED.get() || mc.options.hideGui) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Line> visible = new ArrayList<>();
        synchronized (LINES) {
            LINES.removeIf(l -> l.expiresAtMillis() <= now);
            visible.addAll(LINES);
        }
        if (visible.isEmpty()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        // Wider area so long lines fit; wrap onto multiple rows instead of truncating.
        int maxWidth = Math.max(200, screenW * 2 / 5);
        int rightX = screenW - MARGIN;
        int lineHeight = font.lineHeight + 1;

        // Build wrapped rows for every visible line first so we can stack them by real height.
        List<FormattedCharSequence> rows = new ArrayList<>();
        List<Integer> rowAlpha = new ArrayList<>();
        for (Line line : visible) {
            int alpha = alphaFor(line, now);
            if (alpha <= 8) {
                continue;
            }
            MutableComponent component = Component.literal(line.name() + ": ")
                    .withStyle(Style.EMPTY.withColor(NAME_COLOR))
                    .append(Component.literal(line.text())
                            .withStyle(Style.EMPTY.withColor(ClientConfig.textColorRGB() & 0xFFFFFF)));
            for (FormattedCharSequence row : font.split(component, maxWidth)) {
                rows.add(row);
                rowAlpha.add(alpha);
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        // Bottom of the block sits around mid-screen; newest rows at the bottom, older above.
        int bottomY = screenH / 2;
        for (int i = rows.size() - 1; i >= 0; i--) {
            FormattedCharSequence row = rows.get(i);
            int a = rowAlpha.get(i) << 24;
            int y = bottomY - (rows.size() - i) * lineHeight;
            int x = rightX - font.width(row);
            g.drawString(font, row, x, y, a | 0xFFFFFF, true);
        }
    }

    private static int alphaFor(Line line, long now) {
        long remaining = line.expiresAtMillis() - now;
        if (remaining <= 0) {
            return 0;
        }
        if (remaining >= FADE_MS) {
            return 0xFF;
        }
        return (int) (0xFF * remaining / FADE_MS);
    }
}
