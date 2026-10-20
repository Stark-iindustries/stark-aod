# AnyLink by BotifyX™

Telegram bot: send it a link, it sends back the video.

## Setup (Pterodactyl)

1. Create a server on the **Node.js** egg (Node 16+).
2. Upload this zip's contents to the server root (no nested folder — files
   should sit directly under `/home/container/`).
3. Set the environment variable **`BOT_TOKEN`** (or `TELEGRAM_BOT_TOKEN`)
   to the token from [@BotFather](https://t.me/BotFather).  
   Do **not** put the token in any file — it is read only from the environment.
4. Optionally open `config.js` and set:
   - `ADMIN_ID` — your numeric Telegram user ID (e.g. from @userinfobot)
5. Startup command: `npm install && npm start` (or install once, then
   `node index.js` — check your egg's startup settings).
6. Start the server. On first install, `postinstall` downloads the yt-dlp
   binary into `./bin/yt-dlp` automatically. This needs outbound internet
   access from the container.

## Notes

- **Upload limit:** Telegram's Bot API caps uploads at 50MB per file. Bigger
  files are rejected with a message instead of failing silently. To go
  higher, run your own [local Bot API server](https://github.com/tdlib/telegram-bot-api)
  and point `node-telegram-bot-api` at it — that's a bigger infra change,
  not something this zip sets up for you.
- **ffmpeg:** the default format selector (`best[ext=mp4]/best`) picks a
  single progressive stream so ffmpeg isn't required. If you install ffmpeg
  on the box, switch `FORMAT` in `config.js` to `bv*+ba/best` for
  higher-quality merged downloads.
- **Access control:** `RESTRICT_ACCESS` in `config.js` is off by default
  (anyone who finds the bot can use it). Flip it on and list allowed IDs in
  `ALLOWED_IDS` to lock it down.
- **Site coverage:** downloading works through yt-dlp, so coverage matches
  [yt-dlp's supported sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).
  Some sites change their layout often enough that yt-dlp needs updating —
  delete `./bin/yt-dlp` and restart to re-download the latest build.
- You're responsible for how this bot is used — respect the terms of
  service of whatever sites people link, and copyright law in your
  jurisdiction.

## Commands

- Just paste a link — no command needed.
- `/start`, `/help`
- `/stats` — admin only, basic usage counters.
- `/admin` — admin only, Force Join settings.

## Force Join

`/admin` opens a panel with **Add Channel** / **Add Group** buttons. Tapping
one starts a step-by-step prompt: it asks for the display name, then the
username (e.g. `@mychannel`), then the invite link — send each as a plain
message when asked. Once saved, anyone who isn't a member of every required
channel/group gets a "join these first" prompt (with buttons straight to
each one, plus an "I've Joined" recheck button) instead of being able to use
the bot. The admin (`ADMIN_ID`) is always exempt.

**Important:** the bot must be an **admin** in each channel/group you add —
without that, Telegram won't let it check who's a member, and that entry is
skipped (fails open, so a misconfigured entry doesn't lock out everyone).

Required entries are stored in `data/forcejoin.json`. Use **📋 List
Required** in `/admin` to remove one.
