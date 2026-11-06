# Ozone Player — Together Online Cloudflare Relay Server

100% Free Edge Relay Server for Ozone Player's **Together Online Play** (6-digit Room Codes & Global Music Sync).

---

## 🚀 1-Minute Deployment Options

### Method A: Cloudflare Web Dashboard (Zero Terminal / Zero CLI)
1. Go to [dash.cloudflare.com](https://dash.cloudflare.com) and log in (or create a free account).
2. Click **Compute (Workers & Pages)** -> **Create** -> **Create Worker**.
3. Name it `ozone-together` and click **Deploy**.
4. Click **Edit code** (Quick Edit).
5. Delete the default code, paste the contents of [`worker.js`](worker.js), and click **Save and Deploy**.
6. Copy your public worker URL (e.g. `https://ozone-together.<your-subdomain>.workers.dev`).

### Method B: Terminal via Wrangler CLI
```bash
cd server/cloudflare-worker
npx wrangler deploy
```

---

## 🔗 Connecting to Ozone Player

1. Open `OzoneTogetherServer.txt` at the root of the repository.
2. Put your Worker URL:
   ```
   https://ozone-together.<your-subdomain>.workers.dev
   ```
3. Commit and push to GitHub.
4. All Ozone Player apps will automatically fetch and use this server!
