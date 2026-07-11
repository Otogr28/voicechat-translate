"""
Voice Translate sidecar — STT + translation service.

Runs on the VPS as a Docker container on Crafty's network (so the Minecraft server, which is itself
in a container, reaches it by name: http://voicetrans-sidecar:8200). API keys live ONLY here, never
on game clients. The mod jar stays light; the heavy/blocking cloud calls live here.

Set VOICETRANS_MOCK=0 to use the real providers (Google Cloud Speech-to-Text via REST + DeepL). With
it 1 (default) everything is mocked so the whole flow can be tested without keys or cost.

Speech-to-Text uses the REST API (google-auth + requests) — no grpcio — so it installs cleanly on any
Python. Env for real mode:
    VOICETRANS_MOCK=0
    GOOGLE_APPLICATION_CREDENTIALS=/secrets/google-stt.json   # service-account JSON
    DEEPL_AUTH_KEY=...                                         # DeepL Free key (ends with :fx)
  (Or, instead of the JSON, an API key restricted to Speech-to-Text: GOOGLE_STT_API_KEY=...)

Run (dev):  uvicorn app:app --host 127.0.0.1 --port 8200 --reload
"""
from __future__ import annotations

import base64
import functools
import os

from fastapi import FastAPI, Request
from pydantic import BaseModel

app = FastAPI(title="voicetrans-sidecar", version="0.1.0")

USE_MOCK = os.environ.get("VOICETRANS_MOCK", "1") == "1"

SAMPLE_RATE = 48_000  # Simple Voice Chat decodes to 48kHz mono 16-bit PCM
STT_ENDPOINT = "https://speech.googleapis.com/v1/speech:recognize"
STT_SCOPE = "https://www.googleapis.com/auth/cloud-platform"

# ISO-639-1 -> BCP-47 for Google STT (extend as needed; falls back to the raw code).
_STT_LANG = {
    "en": "en-US", "es": "es-ES", "fr": "fr-FR", "de": "de-DE", "pt": "pt-BR",
    "it": "it-IT", "ja": "ja-JP", "ko": "ko-KR", "zh": "zh", "ru": "ru-RU",
}
# ISO-639-1 -> DeepL target codes (DeepL wants uppercase; some need region).
_DEEPL_TARGET = {
    "en": "EN-US", "es": "ES", "fr": "FR", "de": "DE", "pt": "PT-BR",
    "it": "IT", "ja": "JA", "ko": "KO", "zh": "ZH", "ru": "RU",
}


class TranslateRequest(BaseModel):
    text: str
    source: str | None = None  # None = auto-detect (text chat)
    target: str


class TranslateResponse(BaseModel):
    text: str
    source: str


class SttResponse(BaseModel):
    text: str
    lang: str


# --- providers (only used in real mode) --------------------------------------------------------

@functools.lru_cache(maxsize=1)
def _service_account_credentials():
    from google.oauth2 import service_account  # type: ignore
    path = os.environ["GOOGLE_APPLICATION_CREDENTIALS"]
    return service_account.Credentials.from_service_account_file(path, scopes=[STT_SCOPE])


def _stt_auth():
    """Return (params, headers) for the Speech-to-Text REST call."""
    api_key = os.environ.get("GOOGLE_STT_API_KEY")
    if api_key:
        return {"key": api_key}, {}
    import google.auth.transport.requests as gtr  # type: ignore
    creds = _service_account_credentials()
    if not creds.valid:
        creds.refresh(gtr.Request())
    return {}, {"Authorization": f"Bearer {creds.token}"}


@functools.lru_cache(maxsize=1)
def _deepl_client():
    import deepl  # type: ignore
    return deepl.Translator(os.environ["DEEPL_AUTH_KEY"])


@functools.lru_cache(maxsize=4096)
def _translate_cached(text: str, source: str | None, target: str) -> str:
    translator = _deepl_client()
    result = translator.translate_text(
        text,
        source_lang=(_DEEPL_TARGET.get(source, source).split("-")[0] if source else None),
        target_lang=_DEEPL_TARGET.get(target, target.upper()),
    )
    return result.text


# --- endpoints ---------------------------------------------------------------------------------

@app.get("/health")
def health() -> dict:
    return {"ok": True, "mock": USE_MOCK}


@app.post("/stt", response_model=SttResponse)
async def stt(request: Request, lang_hint: str = "auto") -> SttResponse:
    """Speech-to-text. Body is raw 16-bit LE mono PCM @ 48kHz (what SVC decodes to)."""
    pcm = await request.body()

    if USE_MOCK:
        seconds = len(pcm) / (SAMPLE_RATE * 2)
        return SttResponse(text=f"[mock-stt {seconds:.1f}s]", lang=lang_hint if lang_hint != "auto" else "en")

    import requests  # type: ignore

    primary = _STT_LANG.get(lang_hint, lang_hint if lang_hint != "auto" else "en-US")
    body = {
        "config": {
            "encoding": "LINEAR16",
            "sampleRateHertz": SAMPLE_RATE,
            "audioChannelCount": 1,
            "languageCode": primary,
            "enableAutomaticPunctuation": True,
        },
        "audio": {"content": base64.b64encode(pcm).decode("ascii")},
    }
    params, headers = _stt_auth()
    resp = requests.post(STT_ENDPOINT, params=params, headers=headers, json=body, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    # Some results carry an empty alternative (no speech recognized) with no "transcript"
    # key — guard with .get so a silent/noise segment returns "" instead of 500ing.
    text = " ".join(
        r["alternatives"][0].get("transcript", "")
        for r in data.get("results", [])
        if r.get("alternatives")
    ).strip()
    return SttResponse(text=text, lang=lang_hint if lang_hint != "auto" else primary.split("-")[0])


@app.post("/translate", response_model=TranslateResponse)
def translate(req: TranslateRequest) -> TranslateResponse:
    """Translate `text` from `source` (or auto-detect) to `target`. Cached by (text, source, target)."""
    if req.source and req.source == req.target:
        return TranslateResponse(text=req.text, source=req.source)

    if USE_MOCK:
        return TranslateResponse(text=f"[{req.target}] {req.text}", source=req.source or "auto")

    translated = _translate_cached(req.text, req.source, req.target)
    return TranslateResponse(text=translated, source=req.source or "auto")
