from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
import yt_dlp
import requests
import logging

# Set up basic logging so you can see extractions in the terminal
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Gatekeeper Surgical Proxy")

@app.get("/stream")
def stream_video(id: str):
    """
    Extracts the best audio stream URL for a given YouTube ID and 
    tunnels the byte stream back to the client to bypass IP-locking.
    """
    if not id:
        raise HTTPException(status_code=400, detail="Video ID is required")

    youtube_url = f"https://www.youtube.com/watch?v={id}"
    logger.info(f"Extracting info for: {youtube_url}")

    # Configure yt-dlp to only extract metadata for the best audio
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'noplaylist': True,
        'no_warnings': True,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # download=False ensures we only get the URL, we don't save to disk
            info = ydl.extract_info(youtube_url, download=False)
            stream_url = info.get('url')
            
            if not stream_url:
                raise HTTPException(status_code=404, detail="Could not extract stream URL")
                
    except yt_dlp.utils.DownloadError as e:
        logger.error(f"yt-dlp error: {str(e)}")
        raise HTTPException(status_code=400, detail="Failed to process video (might be age-restricted or private)")

    # The Generator that tunnels the data
    def iterfile():
        # We forward the request to YouTube's raw media server with a standard User-Agent.
        # stream=True ensures we don't load the whole file into the proxy's RAM.
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
        
        try:
            with requests.get(stream_url, stream=True, headers=headers) as r:
                r.raise_for_status() # Ensure we got a 200 OK from YouTube
                
                # Yield the audio data in 64KB chunks
                for chunk in r.iter_content(chunk_size=1024 * 64):
                    if chunk:
                        yield chunk
        except requests.exceptions.RequestException as e:
            logger.error(f"Streaming error: {str(e)}")
            # If the stream breaks mid-way, the generator will just stop yielding

    logger.info("Starting stream tunnel...")
    return StreamingResponse(iterfile(), media_type="audio/mpeg")

@app.get("/health")
def health_check():
    """Simple health check endpoint for Koyeb/Railway deployment."""
    return {"status": "ok"}
