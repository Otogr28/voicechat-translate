# voicetrans sidecar

STT + translation microservice for the Voice Translate mod. Runs on the VPS, bound to `127.0.0.1`
(never exposed). The Minecraft server mod calls it over localhost.

## Dev (mock — no keys, no cost)

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8200 --reload
curl localhost:8200/health        # {"ok":true,"mock":true}
```

Mock mode tags text (`[en] hola`) instead of really translating, so the whole mod ↔ sidecar flow is
testable without accounts.

## Real mode (VPS)

1. Uncomment `google-cloud-speech` and `deepl` in `requirements.txt` and reinstall.
2. Provide credentials via an EnvironmentFile (chmod 600 root), e.g. `/etc/mcserver/voicetrans.env`:
   ```
   VOICETRANS_MOCK=0
   GOOGLE_STT_API_KEY=xxxxxxxx          # API key restricted to "Cloud Speech-to-Text API"
   DEEPL_AUTH_KEY=xxxxxxxx:fx           # DeepL Free key
   ```
   (Alternatively use a service account: `GOOGLE_APPLICATION_CREDENTIALS=/etc/mcserver/google-stt.json`.)
3. Install the systemd unit (`voicetrans-sidecar.service.example`) and `systemctl enable --now`.

## Endpoints

| Method | Path        | Body                              | Returns            |
|--------|-------------|-----------------------------------|--------------------|
| GET    | `/health`   | —                                 | `{ok, mock}`       |
| POST   | `/stt`      | raw PCM 16-bit LE mono @ 48kHz; `?lang_hint=es` | `{text, lang}` |
| POST   | `/translate`| `{text, source?, target}`         | `{text, source}`   |

Translations are LRU-cached by `(text, source, target)`.
