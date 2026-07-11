# Voice Translate (`voicetrans`)

Real-time translation of **Simple Voice Chat** voice **and** text chat for the *summerBuddies* Minecraft
server (Forge 1.20.1), QSMP-style: a floating subtitle appears above the speaker's head, **already
translated into each listener's chosen language**.

## How it works (server-centric)

All cloud compute (speech-to-text + translation) and all API keys live **on the server side / a sidecar
on the VPS**. Clients never hold keys and never upload audio — Simple Voice Chat already delivers the
audio to the server.

```
SERVER (VPS) — the only place with API keys
 ├─ SVC plugin (server-side): MicrophonePacketEvent → decode Opus→PCM per speaker
 ├─ Savings GATE: if every nearby listener shares the speaker's spoken language → skip STT entirely
 ├─ Sidecar STT (Google) → text; translate once per distinct target language (DeepL, cached)
 └─ S2C SubtitlePacket → each nearby client, already in their language

CLIENT — no keys, no audio upload
 ├─ C2S UpdatePrefsPacket: my spoken language + subtitle language + toggles
 └─ Render floating subtitle above the speaker (F6)

Text chat: server rewrites each recipient's message into their language (DeepL, cached). No STT.
```

The **savings gate** is the core cost control: STT (billed per audio minute) only runs when at least one
nearby listener actually needs a different language. That's why each player declares the language they
*speak* — the server can decide before spending anything.

## Layout

- `src/main/java/com/summerbuddies/voicetrans/` — the Forge mod
  - `voice/` — Simple Voice Chat plugin (capture/decode) · `server/` — prefs + savings gate + chat
  - `net/` — packets · `client/` — config + subtitle rendering · `backend/` — sidecar client
- `sidecar/` — Python FastAPI service (STT + translation), runs on the VPS bound to `127.0.0.1`

## Build & deploy

```bash
./gradlew build          # -> build/libs/voicetrans-<version>.jar
# copy the jar into the modpack and deploy:
cp build/libs/voicetrans-*.jar ~/MCserver/mods/
# then in MCserver: git add/commit/push && ssh mcserver mc-update
```

Players need this jar **and** Simple Voice Chat in their Forge 1.20.1 client.

## Status

Phased build — see the project plan. **F1 (scaffold) in progress**: project structure, networking,
client config, and a **mock** sidecar. STT/translation and rendering come in later phases.
