// Downloads the standalone yt-dlp binary into ./bin on `npm install`.
// This runs automatically as a postinstall hook -- Pterodactyl runs
// `npm install` on every startup by default, so this keeps yt-dlp
// present (and lets you delete /bin to force a fresh re-download).

const https = require("https");
const fs = require("fs");
const path = require("path");

const BIN_DIR = path.join(__dirname, "..", "bin");
const BIN_PATH = path.join(BIN_DIR, "yt-dlp");
const URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";

function download(url, dest, redirects) {
  redirects = redirects || 0;
  if (redirects > 5) {
    console.error("[install-ytdlp] Too many redirects, aborting.");
    process.exit(1);
  }

  https
    .get(url, (res) => {
      if (
        res.statusCode >= 300 &&
        res.statusCode < 400 &&
        res.headers.location
      ) {
        return download(res.headers.location, dest, redirects + 1);
      }

      if (res.statusCode !== 200) {
        console.error(
          `[install-ytdlp] Failed to download yt-dlp: HTTP ${res.statusCode}`
        );
        process.exit(1);
      }

      const file = fs.createWriteStream(dest);
      res.pipe(file);
      file.on("finish", () => {
        file.close(() => {
          fs.chmodSync(dest, 0o755);
          console.log("[install-ytdlp] yt-dlp is ready at", dest);
        });
      });
    })
    .on("error", (err) => {
      console.error("[install-ytdlp] Download error:", err.message);
      process.exit(1);
    });
}

if (!fs.existsSync(BIN_DIR)) fs.mkdirSync(BIN_DIR, { recursive: true });

if (fs.existsSync(BIN_PATH)) {
  console.log("[install-ytdlp] yt-dlp already present, skipping download.");
} else {
  console.log("[install-ytdlp] Fetching latest yt-dlp binary...");
  download(URL, BIN_PATH);
}
