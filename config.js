module.exports = {
  // Bot token is read from the BOT_TOKEN environment variable (never stored in this file).
  // Set it in your panel / Docker / Pterodactyl environment variables.

  // Your numeric Telegram user ID (get it from @userinfobot)
  // Used for /stats and admin-only notifications
  ADMIN_ID: 0,

  // If true, only ADMIN_ID (and anyone in ALLOWED_IDS) can use the bot.
  // If false, anyone can use it.
  RESTRICT_ACCESS: false,
  ALLOWED_IDS: [],

  // Telegram Bot API hard-caps uploads at 50MB unless you run your own
  // local Bot API server (see README). Files bigger than this are rejected
  // with a friendly message instead of silently failing.
  MAX_FILE_SIZE_MB: 50,

  // Where downloads land before being uploaded (auto-created, auto-cleaned)
  DOWNLOAD_DIR: "./downloads",

  // How often (ms) to edit the progress message. Telegram rate-limits
  // edits, so don't go much below 2000.
  PROGRESS_EDIT_INTERVAL_MS: 2500,

  // yt-dlp format selector. "best" (single progressive file) avoids needing
  // ffmpeg to merge separate video/audio streams. Install ffmpeg on your
  // Pterodactyl server and switch to "bv*+ba/best" for max quality.
  FORMAT: "best[ext=mp4]/best",
};
