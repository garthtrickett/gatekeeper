# youtube-proxy/surgical_proxy.py
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
import yt_dlp
import requests

app = FastAPI()

def get_stream_url(video_id: str):
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        try:
            # FIX: Pass video_id directly instead of using the broken f-string
            info = ydl.extract_info(video_id, download=False)
            return info['url']
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

@app.get("/stream")
async def stream_video(id: str, request: Request):
    url = get_stream_url(id)
    
    # Extract the Range header from the incoming curl request
    headers = {}
    if "range" in request.headers:
        headers["Range"] = request.headers["range"]
    
    def iterfile():
        # Pass the headers along to the final audio stream
        with requests.get(url, stream=True, headers=headers) as r:
            yield from r.iter_content(chunk_size=1024*64)

    # You might also need to dynamically adjust the status_code/headers 
    # of StreamingResponse for complete 206 Partial Content compliance, 
    # but this will get the upstream server doing the heavy lifting!
    return StreamingResponse(iterfile(), media_type="audio/mpeg")
