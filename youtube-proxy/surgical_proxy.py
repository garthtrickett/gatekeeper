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
            info = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)
            return info['url']
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

@app.get("/stream")
async def stream_video(id: str):
    url = get_stream_url(id)
    
    def iterfile():
        with requests.get(url, stream=True) as r:
            yield from r.iter_content(chunk_size=1024*64)

    return StreamingResponse(iterfile(), media_type="audio/mpeg")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
