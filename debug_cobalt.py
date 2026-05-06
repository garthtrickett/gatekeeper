import requests
import json
import sys

# CONFIGURATION (Matching your local.properties)
COBALT_URL = "https://cobalt-production-08c4.up.railway.app"
API_KEY = "lPjrNzgTQBECJsdsOwlVzhcwzhJuMiDM"
VIDEO_ID = "niTcQZcOhZY"

def debug_cobalt():
    print(f"🚀 PHASE 1: Resolving Stream for {VIDEO_ID}...")
    
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Api-Key": API_KEY
    }
    
    payload = {
        "url": f"https://www.youtube.com/watch?v={VIDEO_ID}",
        "downloadMode": "audio",
        "audioFormat": "best"
    }

    try:
        # Step 1: POST to get the tunnel URL
        response = requests.post(COBALT_URL, headers=headers, json=payload, timeout=10)
        print(f"ST1 Status: {response.status_code}")
        
        if response.status_code != 200:
            print(f"❌ Error: {response.text}")
            return

        data = response.json()
        print(f"ST1 JSON: {json.dumps(data, indent=2)}")
        
        stream_url = data.get("url")
        if not stream_url:
            print("❌ No URL in response.")
            return

        print(f"\n🚀 PHASE 2: Connecting to Tunnel...")
        print(f"URL: {stream_url}")
        
        # Step 2: Connect to the tunnel URL
        # We simulate ExoPlayer headers here
        tunnel_headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
            "Accept": "*/*",
            "Api-Key": API_KEY # Testing if key is needed here
        }
        
        # Perform GET with stream=True so we don't download the whole file
        with requests.get(stream_url, headers=tunnel_headers, stream=True, timeout=15) as r:
            print(f"ST2 Status: {r.status_code}")
            print(f"ST2 Headers: {json.dumps(dict(r.headers), indent=2)}")
            
            if r.status_code == 200:
                print("\n🚀 PHASE 3: Reading first 512 bytes...")
                # Read just the start of the file
                chunk = next(r.iter_content(chunk_size=512))
                if chunk:
                    print(f"✅ Successfully read {len(chunk)} bytes.")
                    print(f"HEX PEEK: {chunk[:32].hex()}")
                    
                    # Detect common file signatures
                    if chunk.startswith(b'\x1a\x45\xdf\xa3'):
                        print("DETECTED: WebM Container")
                    elif chunk.startswith(b'ID3') or chunk.startswith(b'\xff\xfb'):
                        print("DETECTED: MP3 Container")
                    elif b'ftyp' in chunk:
                        print("DETECTED: MP4/M4A Container")
                    elif b'<!DOCTYPE html>' in chunk or b'<html' in chunk:
                        print("❌ DETECTED: HTML (This is an error page, not audio!)")
                else:
                    print("❌ Connection opened but 0 bytes received.")
            else:
                print(f"❌ Tunnel request failed with status {r.status_code}")

    except Exception as e:
        print(f"🚨 Script Crash: {str(e)}")

if __name__ == "__main__":
    debug_cobalt()
