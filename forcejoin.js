// Force-join list storage + membership checking.

const fs = require("fs");
const path = require("path");

const DATA_DIR = path.join(__dirname, "data");
const DATA_FILE = path.join(DATA_DIR, "forcejoin.json");

function ensure() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, "[]");
}

function list() {
  ensure();
  return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
}

function save(entries) {
  ensure();
  fs.writeFileSync(DATA_FILE, JSON.stringify(entries, null, 2));
}

function add(entry) {
  const entries = list();
  const withId = Object.assign(
    { id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6) },
    entry
  );
  entries.push(withId);
  save(entries);
  return withId;
}

function remove(id) {
  save(list().filter((e) => e.id !== id));
}

// Returns the subset of required channels/groups the user hasn't joined.
// If a membership check errors out (bot isn't admin there, bad username,
// etc.) that entry is skipped rather than blocking everyone — a
// misconfigured entry shouldn't lock the whole bot.
async function unjoined(bot, userId) {
  const entries = list();
  const missing = [];
  for (const entry of entries) {
    try {
      const uname = entry.username.startsWith("@")
        ? entry.username
        : `@${entry.username}`;
      const member = await bot.getChatMember(uname, userId);
      if (!["member", "administrator", "creator"].includes(member.status)) {
        missing.push(entry);
      }
    } catch (err) {
      console.error(
        `[forcejoin] Couldn't verify membership for ${entry.username}: ${err.message}`
      );
    }
  }
  return missing;
}

module.exports = { list, add, remove, unjoined };
