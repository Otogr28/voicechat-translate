package com.summerbuddies.voicetrans.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.summerbuddies.voicetrans.VoiceTransMod;
import com.summerbuddies.voicetrans.server.ServerConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Async client for the STT + translation sidecar. All calls run off the MC server thread on a small
 * dedicated pool; callers hop results back onto the server thread before touching game state.
 *
 * <p>Endpoints (see {@code sidecar/app.py}):
 * <ul>
 *   <li>{@code POST /stt?lang_hint=xx} — body is 16-bit LE mono PCM @ 48 kHz → {@code {text, lang}}</li>
 *   <li>{@code POST /translate} — {@code {text, source?, target}} → {@code {text, source}}</li>
 * </ul>
 */
public final class SidecarClient {

    public record SttResult(String text, String lang) {}

    private static final Gson GSON = new Gson();

    private static final ThreadFactory THREADS = r -> {
        Thread t = new Thread(r, "voicetrans-sidecar");
        t.setDaemon(true);
        return t;
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            // Force HTTP/1.1: the default HTTP/2 client attempts an h2c upgrade over cleartext that
            // the FastAPI/uvicorn (h11) sidecar can't handle, which mangles POST bodies (422s).
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newFixedThreadPool(4, THREADS))
            .build();

    private SidecarClient() {}

    private static String base() {
        return ServerConfig.SIDECAR_URL.get().replaceAll("/+$", "");
    }

    /** Speech-to-text. {@code langHint} is the speaker's declared language (improves accuracy). */
    public static CompletableFuture<SttResult> stt(byte[] pcm16le, String langHint) {
        String hint = URLEncoder.encode(langHint == null || langHint.isBlank() ? "auto" : langHint,
                StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/stt?lang_hint=" + hint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(pcm16le))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    JsonObject o = GSON.fromJson(resp.body(), JsonObject.class);
                    return new SttResult(getString(o, "text"), getString(o, "lang"));
                })
                .exceptionally(t -> {
                    VoiceTransMod.LOGGER.warn("[voicetrans] STT failed: {}", t.toString());
                    return new SttResult("", "");
                });
    }

    /** Translate {@code text} from {@code source} (null = auto-detect) to {@code target}. */
    public static CompletableFuture<String> translate(String text, String source, String target) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        if (source != null && !source.isBlank()) {
            body.addProperty("source", source);
        }
        body.addProperty("target", target);

        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/translate"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    JsonObject o = GSON.fromJson(resp.body(), JsonObject.class);
                    return getString(o, "text");
                })
                .exceptionally(t -> {
                    VoiceTransMod.LOGGER.warn("[voicetrans] translate failed: {}", t.toString());
                    return text; // fall back to the original text
                });
    }

    private static String getString(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
