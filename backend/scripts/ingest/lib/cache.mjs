import { mkdirSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = join(__dirname, "../../..");
const DUMP_DIR = join(BACKEND_ROOT, ".cache/dumps");

/** Always persist raw dumps under backend/.cache/dumps/{source}/ */
export function saveRawDump(source, filename, body) {
  const dir = join(DUMP_DIR, source);
  mkdirSync(dir, { recursive: true });
  const path = join(dir, filename);
  writeFileSync(path, body, typeof body === "string" ? "utf8" : undefined);
  return path;
}

/**
 * Optional R2 upload (remote only). Requires wrangler auth + bucket.
 * Skipped when local=true.
 */
export function uploadRawToR2(source, filename, localPath, { local = true } = {}) {
  if (local) return { skipped: true, reason: "local" };
  const key = `raw/${source}/${filename}`;
  const result = spawnSync(
    "npx",
    [
      "wrangler",
      "r2",
      "object",
      "put",
      `gaston-dumps/${key}`,
      "--file",
      localPath,
      "--remote",
    ],
    { cwd: BACKEND_ROOT, encoding: "utf8", maxBuffer: 10 * 1024 * 1024 },
  );
  if (result.status !== 0) {
    const err = (result.stderr || result.stdout || "").slice(0, 1000);
    throw new Error(`R2 put failed ${key}: ${err}`);
  }
  return { key };
}
