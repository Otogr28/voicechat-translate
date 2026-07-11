# Sidecar image. Runs on Crafty's Docker network so the MC server (also containerized) can reach it
# by name (http://voicetrans-sidecar:8200). Pinned Python avoids host wheel issues.
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .

EXPOSE 8200
# Bound to all interfaces INSIDE the container only — not published to the host/internet.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8200"]
