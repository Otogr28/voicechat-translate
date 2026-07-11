package com.summerbuddies.voicetrans.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game settings: languages, voice/chat toggles, subtitle colours, and the single-player test mode.
 *
 * <p>The form is scrollable (mouse wheel) and the Done button is pinned to the bottom so it is always
 * reachable regardless of screen size or how many fields there are.
 */
public class ConfigScreen extends Screen {

    private static final int CONTENT_TOP = 36;
    private static final int FIELD_W = 200;

    private final Screen parent;

    private EditBox spokenBox;
    private EditBox subtitleBox;
    private EditBox textColorBox;
    private EditBox bgColorBox;
    private boolean voiceEnabled;
    private boolean chatEnabled;

    // Scrollable content: each widget/label remembers its Y in content-space (pre-scroll).
    private final List<Tracked> tracked = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private record Tracked(AbstractWidget widget, int contentY) {}
    private record Label(String key, int contentY) {}

    public ConfigScreen(Screen parent) {
        super(Component.translatable("screen.voicetrans.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tracked.clear();
        labels.clear();
        scrollOffset = 0;

        int cx = this.width / 2;
        voiceEnabled = ClientConfig.VOICE_ENABLED.get();
        chatEnabled = ClientConfig.CHAT_ENABLED.get();

        spokenBox = field(cx, 12, ClientConfig.SPOKEN_LANGUAGE.get(), "screen.voicetrans.spoken");
        subtitleBox = field(cx, 50, ClientConfig.SUBTITLE_LANGUAGE.get(), "screen.voicetrans.subtitle");
        textColorBox = field(cx, 88, ClientConfig.TEXT_COLOR.get(), "screen.voicetrans.textColor");
        bgColorBox = field(cx, 126, ClientConfig.BACKGROUND_COLOR.get(), "screen.voicetrans.bgColor");

        toggle(cx, 158, this::voiceLabel, () -> voiceEnabled = !voiceEnabled);
        toggle(cx, 182, this::chatLabel, () -> chatEnabled = !chatEnabled);

        // Pinned bottom bar — always visible, not part of the scrollable content.
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx - 100, this.height - 28, FIELD_W, 20).build());

        int contentBottom = 182 + 20;
        int viewport = (this.height - 32) - CONTENT_TOP;
        maxScroll = Math.max(0, contentBottom - viewport);
        reposition();
    }

    private EditBox field(int cx, int contentY, String value, String labelKey) {
        EditBox box = new EditBox(this.font, cx - 100, CONTENT_TOP + contentY, FIELD_W, 20,
                Component.translatable(labelKey));
        box.setValue(value);
        box.setMaxLength(16);
        addRenderableWidget(box);
        tracked.add(new Tracked(box, contentY));
        labels.add(new Label(labelKey, contentY - 10));
        return box;
    }

    private void toggle(int cx, int contentY, java.util.function.Supplier<Component> label, Runnable onClick) {
        Button button = Button.builder(label.get(), b -> {
            onClick.run();
            b.setMessage(label.get());
        }).bounds(cx - 100, CONTENT_TOP + contentY, FIELD_W, 20).build();
        addRenderableWidget(button);
        tracked.add(new Tracked(button, contentY));
    }

    private void reposition() {
        for (Tracked t : tracked) {
            t.widget().setY(CONTENT_TOP + t.contentY() + scrollOffset);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset + (int) (delta * 12), -maxScroll, 0);
            reposition();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private Component voiceLabel() {
        return Component.translatable("screen.voicetrans.voice", onOff(voiceEnabled));
    }

    private Component chatLabel() {
        return Component.translatable("screen.voicetrans.chat", onOff(chatEnabled));
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        for (Label label : labels) {
            graphics.drawString(this.font, Component.translatable(label.key()),
                    cx - 100, CONTENT_TOP + label.contentY() + scrollOffset, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientConfig.SPOKEN_LANGUAGE.set(spokenBox.getValue().trim().toLowerCase(Locale.ROOT));
        ClientConfig.SUBTITLE_LANGUAGE.set(subtitleBox.getValue().trim().toLowerCase(Locale.ROOT));
        ClientConfig.VOICE_ENABLED.set(voiceEnabled);
        ClientConfig.CHAT_ENABLED.set(chatEnabled);
        ClientConfig.TEXT_COLOR.set(sanitizeHex(textColorBox.getValue(), "FFFFFF"));
        ClientConfig.BACKGROUND_COLOR.set(sanitizeHex(bgColorBox.getValue(), "40000000"));
        ClientConfig.SPEC.save();
        ClientPrefsSync.send();

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    /** Keep only valid hex digits; fall back if the user typed something unparseable. */
    private static String sanitizeHex(String value, String fallback) {
        String hex = value.trim().replace("#", "").toUpperCase(Locale.ROOT);
        if (hex.isEmpty() || !hex.matches("[0-9A-F]{1,8}")) {
            return fallback;
        }
        return hex;
    }
}
