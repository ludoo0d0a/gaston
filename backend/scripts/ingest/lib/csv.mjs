/** RFC4180-ish CSV line parser (supports quotes and custom delimiter). */
export function parseCsvLine(line, delimiter = ",") {
  const out = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === delimiter) {
      out.push(cur);
      cur = "";
    } else {
      cur += ch;
    }
  }
  out.push(cur);
  return out;
}

export function parseCsv(text, { delimiter = "," } = {}) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length === 0) return { header: [], rows: [] };
  const header = parseCsvLine(lines[0], delimiter).map((h) => h.trim());
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    rows.push(parseCsvLine(lines[i], delimiter));
  }
  return { header, rows };
}

export function headerIndex(header) {
  const idx = Object.create(null);
  header.forEach((name, i) => {
    idx[name] = i;
  });
  return idx;
}

export function col(row, idx, name) {
  const i = idx[name];
  if (i == null) return null;
  const v = row[i];
  if (v == null) return null;
  const t = String(v).trim();
  return t.length ? t : null;
}

/** Parse `[lon, lat]` (Gireve / QualiCharge coordonneesXY). */
export function parseCoordonneesXy(raw) {
  if (raw == null || !String(raw).trim()) return null;
  const cleaned = String(raw).trim().replace(/^"|"$/g, "").trim();
  const inner = cleaned.replace(/^\[/, "").replace(/\]$/, "").trim();
  const parts = inner.split(",").map((p) => p.trim());
  if (parts.length < 2) return null;
  const lon = Number(parts[0]);
  const lat = Number(parts[1]);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  return { lat, lon };
}
