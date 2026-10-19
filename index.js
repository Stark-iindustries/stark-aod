// AnyLink by BotifyX™
// Telegram bot: send a link, get the video back.

const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const TelegramBot = require("node-telegram-bot-api");
const config = require("./config");
const forcejoin = require("./forcejoin");

if (!config.BOT_TOKEN || config.BOT_TOKEN === "PUT_YOUR_BOT_TOKEN_HERE") {
  console.error(
    "[anylink] Set BOT_TOKEN in config.js before starting the bot."
  );
  process.exit(1);
}

const YTDLP_PATH = path.join(__dirname, "bin", "yt-dlp");
const DOWNLOAD_DIR = path.resolve(__dirname, config.DOWNLOAD_DIR);
if (!fs.existsSync(DOWNLOAD_DIR)) fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });

const bot = new TelegramBot(config.BOT_TOKEN, { polling: true });

const URL_REGEX = /https?:\/\/[^\s]+/i;

// simple in-memory stats + one-job-per-user lock
const stats = { started: Date.now(), downloads: 0, users: new Set() };
const busy = new Set();

// admin "add channel/group" wizard state: userId -> { type, step, data }
const adminSessions = new Map();

function isAllowed(userId) {
  if (!config.RESTRICT_ACCESS) return true;
  if (userId === config.ADMIN_ID) return true;
  return config.ALLOWED_IDS.includes(userId);
}

// ---------- termux-style progress rendering ----------

function bar(percent, width) {
  width = width || 22;
  const filled = Math.max(0, Math.min(width, Math.round((percent / 100) * width)));
  return "█".repeat(filled) + "░".repeat(width - filled);
}

function renderProgress({ percent, size, speed, eta, stage }) {
  const pct = isFinite(percent) ? percent : 0;
  return [
    "```",
    "┌─[anylink@botifyx]",
    `│ target : ${stage || "fetching stream"}`,
    `│ [${bar(pct)}] ${pct.toFixed(1)}%`,
    `│ size   : ${size || "—"}`,
    `│ speed  : ${speed || "—"}`,
    `│ eta    : ${eta || "—"}`,
    "└─ working...",
    "```",
  ].join("\n");
}

function renderDone({ size, elapsed, title }) {
  return [
    "```",
    "┌─[anylink@botifyx]",
    `│ ${title ? title.slice(0, 48) : "download"}`,
    `│ [${bar(100)}] 100.0%`,
    `│ size   : ${size || "—"}`,
    `│ time   : ${elapsed || "—"}`,
    "└─ done. uploading to telegram...",
    "```",
  ].join("\n");
}

// ---------- yt-dlp progress line parsing ----------
// yt-dlp --newline prints lines like:
// [download]  45.2% of   10.00MiB at    1.23MiB/s ETA 00:05
const PROGRESS_RE =
  /\[download\]\s+([\d.]+)% of\s+~?\s*([\d.]+\w+)\s+at\s+([\d.]+\w+\/s|Unknown speed)\s+ETA\s+([\d:]+|Unknown)/;

function downloadVideo(url, outTemplate, onProgress) {
  return new Promise((resolve, reject) => {
    const args = [
      "-f",
      config.FORMAT,
      "--no-playlist",
      "--newline",
      "--no-warnings",
      "--restrict-filenames",
      "-o",
      outTemplate,
      url,
    ];

    const proc = spawn(YTDLP_PATH, args);
    let lastLine = "";
    let stderrBuf = "";

    proc.stdout.on("data", (chunk) => {
      const text = chunk.toString();
      lastLine = text.trim().split("\n").pop() || lastLine;
      const match = PROGRESS_RE.exec(text);
      if (match) {
        onProgress({
          percent: parseFloat(match[1]),
          size: match[2],
          speed: match[3],
          eta: match[4],
          stage: "downloading",
        });
      } else if (/\[Merger\]|Merging formats/.test(text)) {
        onProgress({ percent: 99, stage: "merging audio/video" });
      } else if (/\[ExtractAudio\]/.test(text)) {
        onProgress({ percent: 99, stage: "extracting audio" });
      }
    });

    proc.stderr.on("data", (chunk) => {
      stderrBuf += chunk.toString();
    });

    proc.on("error", (err) => reject(err));

    proc.on("close", (code) => {
      if (code === 0) resolve();
      else reject(new Error(stderrBuf.trim() || lastLine || `yt-dlp exited with code ${code}`));
    });
  });
}

function getTitle(url) {
  return new Promise((resolve) => {
    const proc = spawn(YTDLP_PATH, ["--no-playlist", "--print", "%(title)s", url]);
    let out = "";
    proc.stdout.on("data", (d) => (out += d.toString()));
    proc.on("close", () => resolve(out.trim().split("\n")[0] || "video"));
    proc.on("error", () => resolve("video"));
  });
}

function humanSize(bytes) {
  if (!bytes) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let n = bytes;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i++;
  }
  return `${n.toFixed(2)}${units[i]}`;
}

// ---------- force-join admin panel ----------

function adminPanelKeyboard() {
  return {
    reply_markup: {
      inline_keyboard: [
        [
          { text: "➕ Add Channel", callback_data: "fj:addchannel" },
          { text: "➕ Add Group", callback_data: "fj:addgroup" },
        ],
        [{ text: "📋 List Required", callback_data: "fj:list" }],
        [{ text: "✖️ Close", callback_data: "fj:close" }],
      ],
    },
  };
}

async function sendForceJoinList(chatId) {
  const entries = forcejoin.list();
  if (entries.length === 0) {
    await bot.sendMessage(chatId, "No required channels/groups yet.", adminPanelKeyboard());
    return;
  }
  const rows = entries.map((e) => [
    {
      text: `🗑 ${e.type === "channel" ? "📢" : "👥"} ${e.name}`,
      callback_data: `fj:remove:${e.id}`,
    },
  ]);
  rows.push([{ text: "⬅️ Back", callback_data: "fj:panel" }]);
  await bot.sendMessage(chatId, "Required to use the bot — tap one to remove it:", {
    reply_markup: { inline_keyboard: rows },
  });
}

async function handleAdminWizardStep(userId, chatId, text) {
  const session = adminSessions.get(userId);
  if (!session) return;

  if (session.step === "name") {
    session.data.name = text;
    session.step = "username";
    await bot.sendMessage(
      chatId,
      `Now send the ${session.type} *username* (e.g. @example or example):`,
      { parse_mode: "Markdown" }
    );
    return;
  }

  if (session.step === "username") {
    session.data.username = text.replace(/^@/, "").trim();
    session.step = "link";
    await bot.sendMessage(chatId, "Now send the invite *link* (e.g. https://t.me/example):", {
      parse_mode: "Markdown",
    });
    return;
  }

  if (session.step === "link") {
    session.data.link = text;
    const entry = forcejoin.add({
      type: session.type,
      name: session.data.name,
      username: session.data.username,
      link: session.data.link,
    });
    adminSessions.delete(userId);
    await bot.sendMessage(
      chatId,
      `✅ Added ${session.type}: *${entry.name}* (@${entry.username})\n\n` +
        `Make sure the bot is an *admin* in it — membership checks fail silently otherwise.`,
      Object.assign({ parse_mode: "Markdown" }, adminPanelKeyboard())
    );
  }
}

// Checks force-join requirements for a regular user. Returns true if they
// can proceed; otherwise prompts them to join and returns false. Admin is
// always exempt.
async function requireJoin(chatId, userId) {
  if (userId === config.ADMIN_ID) return true;

  const missing = await forcejoin.unjoined(bot, userId);
  if (missing.length === 0) return true;

  const rows = missing.map((e) => [
    { text: `${e.type === "channel" ? "📢" : "👥"} Join ${e.name}`, url: e.link },
  ]);
  rows.push([{ text: "✅ I've Joined", callback_data: "fj:checkjoin" }]);

  await bot.sendMessage(chatId, "Join these first, then tap the button below:", {
    reply_markup: { inline_keyboard: rows },
  });
  return false;
}

bot.on("callback_query", async (query) => {
  const data = query.data || "";
  const chatId = query.message.chat.id;
  const userId = query.from.id;

  try {
    if (data === "fj:checkjoin") {
      const missing = await forcejoin.unjoined(bot, userId);
      if (missing.length === 0) {
        await bot.answerCallbackQuery(query.id, { text: "You're in!" });
        await bot.editMessageText("✅ Verified — you're all set. Send me a link.", {
          chat_id: chatId,
          message_id: query.message.message_id,
        });
      } else {
        await bot.answerCallbackQuery(query.id, {
          text: "Still missing one or more. Join them first.",
          show_alert: true,
        });
      }
      return;
    }

    // everything below is admin-only
    if (userId !== config.ADMIN_ID) {
      await bot.answerCallbackQuery(query.id, { text: "Admins only.", show_alert: true });
      return;
    }

    if (data === "fj:addchannel") {
      adminSessions.set(userId, { type: "channel", step: "name", data: {} });
      await bot.answerCallbackQuery(query.id);
      await bot.sendMessage(chatId, "Send the channel *name* (display name):", {
        parse_mode: "Markdown",
      });
    } else if (data === "fj:addgroup") {
      adminSessions.set(userId, { type: "group", step: "name", data: {} });
      await bot.answerCallbackQuery(query.id);
      await bot.sendMessage(chatId, "Send the group *name* (display name):", {
        parse_mode: "Markdown",
      });
    } else if (data === "fj:list") {
      await bot.answerCallbackQuery(query.id);
      await sendForceJoinList(chatId);
    } else if (data === "fj:panel") {
      await bot.answerCallbackQuery(query.id);
      await bot.sendMessage(chatId, "*AnyLink Admin* — Force Join settings", {
        parse_mode: "Markdown",
        ...adminPanelKeyboard(),
      });
    } else if (data.startsWith("fj:remove:")) {
      forcejoin.remove(data.slice("fj:remove:".length));
      await bot.answerCallbackQuery(query.id, { text: "Removed." });
      await sendForceJoinList(chatId);
    } else if (data === "fj:close") {
      await bot.answerCallbackQuery(query.id);
      await bot.deleteMessage(chatId, query.message.message_id).catch(() => {});
    }
  } catch (err) {
    console.error("[anylink] callback error:", err.message);
  }
});

// ---------- bot handlers ----------

bot.onText(/^\/start$/, async (msg) => {
  const ok = await requireJoin(msg.chat.id, msg.from.id);
  if (!ok) return;
  bot.sendMessage(
    msg.chat.id,
    "*AnyLink* by BotifyX™\n\nSend me a link and I'll grab the video from it.\n\n" +
      "Just paste a URL — no command needed.",
    { parse_mode: "Markdown" }
  );
});

bot.onText(/^\/admin$/, (msg) => {
  if (msg.from.id !== config.ADMIN_ID) return;
  bot.sendMessage(msg.chat.id, "*AnyLink Admin* — Force Join settings", {
    parse_mode: "Markdown",
    ...adminPanelKeyboard(),
  });
});

bot.onText(/^\/help$/, (msg) => {
  bot.sendMessage(
    msg.chat.id,
    "Paste any video link. I'll download it and send it back here.\n" +
      "One job at a time per chat — wait for the current one to finish."
  );
});

bot.onText(/^\/stats$/, (msg) => {
  if (msg.from.id !== config.ADMIN_ID) return;
  const uptimeMin = Math.floor((Date.now() - stats.started) / 60000);
  bot.sendMessage(
    msg.chat.id,
    `Uptime: ${uptimeMin}m\nDownloads served: ${stats.downloads}\nUnique users: ${stats.users.size}`
  );
});

bot.on("message", async (msg) => {
  const text = msg.text || "";
  const userId = msg.from.id;
  const chatId = msg.chat.id;

  // admin is mid-wizard adding a channel/group — consume this message as a
  // wizard answer, not a download link
  if (adminSessions.has(userId) && text && !text.startsWith("/")) {
    await handleAdminWizardStep(userId, chatId, text.trim());
    return;
  }

  if (text.startsWith("/")) return;

  const match = URL_REGEX.exec(text);
  if (!match) return;

  const url = match[0];

  if (!isAllowed(userId)) {
    bot.sendMessage(chatId, "You're not authorized to use this bot.");
    return;
  }

  const joined = await requireJoin(chatId, userId);
  if (!joined) return;

  if (busy.has(chatId)) {
    bot.sendMessage(chatId, "Already working on something in this chat — hang tight.");
    return;
  }
  busy.add(chatId);
  stats.users.add(userId);

  const startTime = Date.now();
  const jobId = `${chatId}_${startTime}`;
  const outTemplate = path.join(DOWNLOAD_DIR, `${jobId}.%(ext)s`);

  let statusMsg;
  let lastEdit = 0;

  try {
    statusMsg = await bot.sendMessage(chatId, renderProgress({ percent: 0, stage: "starting" }), {
      parse_mode: "Markdown",
    });

    const title = await getTitle(url).catch(() => "video");

    await downloadVideo(url, outTemplate, (progress) => {
      const now = Date.now();
      if (now - lastEdit < config.PROGRESS_EDIT_INTERVAL_MS) return;
      lastEdit = now;
      bot
        .editMessageText(renderProgress(progress), {
          chat_id: chatId,
          message_id: statusMsg.message_id,
          parse_mode: "Markdown",
        })
        .catch(() => {}); // ignore "message not modified" etc.
    });

    // find the produced file (extension chosen by yt-dlp)
    const files = fs
      .readdirSync(DOWNLOAD_DIR)
      .filter((f) => f.startsWith(`${jobId}.`));
    if (files.length === 0) throw new Error("yt-dlp finished but no output file was found.");
    const filePath = path.join(DOWNLOAD_DIR, files[0]);
    const sizeBytes = fs.statSync(filePath).size;
    const elapsedSec = ((Date.now() - startTime) / 1000).toFixed(1);

    if (sizeBytes > config.MAX_FILE_SIZE_MB * 1024 * 1024) {
      await bot.editMessageText(
        `File is ${humanSize(sizeBytes)}, which is over Telegram's ${config.MAX_FILE_SIZE_MB}MB bot upload limit. ` +
          `Run your own local Bot API server to raise this (see README).`,
        { chat_id: chatId, message_id: statusMsg.message_id }
      );
      fs.unlinkSync(filePath);
      return;
    }

    await bot.editMessageText(
      renderDone({ size: humanSize(sizeBytes), elapsed: `${elapsedSec}s`, title }),
      { chat_id: chatId, message_id: statusMsg.message_id, parse_mode: "Markdown" }
    );

    try {
      await bot.sendVideo(chatId, filePath, { caption: title.slice(0, 1024) }).catch(async () => {
        // fall back to sendDocument if Telegram rejects it as a video (e.g. odd codec)
        await bot.sendDocument(chatId, filePath, { caption: title.slice(0, 1024) });
      });
      stats.downloads += 1;
    } finally {
      // Always delete the downloaded file after the Telegram upload attempt
      // (success or failure). Prevents disk fill-up from leftover media.
      try {
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
      } catch (_) {}
    }
  } catch (err) {
    const reason = (err && err.message) || "unknown error";
    const safeMsg = "Couldn't get that one — " + reason.slice(0, 300);
    if (statusMsg) {
      bot.editMessageText(safeMsg, { chat_id: chatId, message_id: statusMsg.message_id }).catch(() => {
        bot.sendMessage(chatId, safeMsg);
      });
    } else {
      bot.sendMessage(chatId, safeMsg);
    }
    // clean up any partial files for this job
    try {
      fs.readdirSync(DOWNLOAD_DIR)
        .filter((f) => f.startsWith(`${jobId}`))
        .forEach((f) => {
          try { fs.unlinkSync(path.join(DOWNLOAD_DIR, f)); } catch (_) {}
        });
    } catch (_) {}
  } finally {
    busy.delete(chatId);
  }
});

bot.on("polling_error", (err) => console.error("[anylink] polling error:", err.message));

console.log("[anylink] AnyLink by BotifyX™ is running.");
