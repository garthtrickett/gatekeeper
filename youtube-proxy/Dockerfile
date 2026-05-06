# youtube-proxy/Dockerfile
FROM python:3.11-slim

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN pip install --no-cache-dir fastapi uvicorn yt-dlp requests

COPY surgical_proxy.py .

EXPOSE 8000

CMD ["uvicorn", "surgical_proxy:app", "--host", "0.0.0.0", "--port", "8000"]
