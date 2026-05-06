# Koyeb Deployment: Surgical Proxy

This directory contains the Infrastructure as Code (Terraform) configuration to deploy the `youtube-proxy` to [Koyeb](https://www.koyeb.com/). 

## Prerequisites
1. A Koyeb account.
2. A Koyeb API Token.
3. Terraform installed locally.

## Deployment

1. Export your Koyeb token:
   ```bash
   export KOYEB_TOKEN="your-api-token"
   ```

2. Initialize Terraform:
   ```bash
   terraform init
   ```

3. Apply the configuration (you will be prompted for your GitHub repository path, e.g., `github.com/my-org/gatekeeper`):
   ```bash
   terraform apply
   ```

4. Add the resulting App URL to your `local.properties` at the root of the Gatekeeper project to point the Android app to the cloud instance instead of localhost:
   ```properties
   SURGICAL_PROXY_URL=https://<your-koyeb-app-domain>.koyeb.app
   ```

## ⚠️ Hetzner VPS Fallback
If YouTube detects the datacenter IP from Koyeb and throttles the stream (yielding "0-byte drops" or "HTTP 403 Forbidden" during ExoPlayer playback), you will need to pivot to deploying this container on a cheap, dedicated Hetzner VPS to secure a clean, residential-adjacent IP address that YouTube hasn't flagged.
