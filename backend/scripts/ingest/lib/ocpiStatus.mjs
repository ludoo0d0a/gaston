/**
 * OCPI EVSE status → Gaston etat / occupation (Belgium NAP, DOT-NL).
 */

export function mapEtat(statusRaw) {
  const s = String(statusRaw || "").trim().toUpperCase();
  if (s === "AVAILABLE" || s === "CHARGING" || s === "BLOCKED" || s === "RESERVED")
    return "en_service";
  if (s === "INOPERATIVE" || s === "OUTOFORDER") return "hors_service";
  return "inconnu";
}

export function mapOccupation(statusRaw) {
  const s = String(statusRaw || "").trim().toUpperCase();
  if (s === "AVAILABLE") return "libre";
  if (s === "CHARGING" || s === "BLOCKED") return "occupe";
  if (s === "RESERVED") return "reserve";
  return "inconnu";
}

/** Prefer free → occupied → reserved → out-of-order for station-level rollup. */
export function pickStationStatus(evses) {
  let best = null;
  let rank = -1;
  const rankOf = (s) => {
    const u = String(s || "").toUpperCase();
    if (u === "AVAILABLE") return 4;
    if (u === "CHARGING" || u === "BLOCKED") return 3;
    if (u === "RESERVED") return 2;
    if (u === "INOPERATIVE" || u === "OUTOFORDER") return 1;
    return 0;
  };
  for (const evse of evses || []) {
    const statusRaw = String(evse.status || "").trim();
    if (statusRaw.toUpperCase() === "REMOVED") continue;
    const r = rankOf(statusRaw);
    if (r > rank) {
      rank = r;
      best = statusRaw;
    }
  }
  return best || "UNKNOWN";
}

/**
 * One POI per EVSE (Belgium NAP style).
 */
export function locationsToEvsePois(locations, { source, country, now }) {
  const pois = [];
  for (const loc of locations || []) {
    const lat = Number(loc.coordinates?.latitude);
    const lon = Number(loc.coordinates?.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const stationId = loc.id || null;
    const address = [loc.address, loc.city].filter(Boolean).join(", ") || null;
    for (const evse of loc.evses || []) {
      const statusRaw = String(evse.status || "").trim();
      if (statusRaw.toUpperCase() === "REMOVED") continue;
      const evseId =
        (evse.evse_id && String(evse.evse_id).trim()) ||
        (evse.uid && String(evse.uid).trim());
      if (!evseId) continue;
      pois.push({
        id: `${source}:${evseId}`,
        source,
        category: "irve",
        lat,
        lon,
        name: loc.name || stationId || "Charging point",
        station_id: stationId,
        etat: mapEtat(statusRaw),
        occupation: mapOccupation(statusRaw),
        updated_at: now,
        dynamic_updated_at: now,
        props: {
          address,
          status_raw: statusRaw,
          country,
        },
      });
    }
  }
  return pois;
}

/**
 * One POI per location.id (DOT-NL Free write budget).
 */
export function locationsToStationPois(locations, { source, country, now }) {
  const byStation = new Map();
  for (const loc of locations || []) {
    const lat = Number(loc.coordinates?.latitude);
    const lon = Number(loc.coordinates?.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const stationId = loc.id ? String(loc.id) : null;
    if (!stationId || byStation.has(stationId)) continue;
    const evses = (loc.evses || []).filter(
      (e) => String(e.status || "").toUpperCase() !== "REMOVED",
    );
    if (evses.length === 0) continue;
    const statusRaw = pickStationStatus(evses);
    const address = [loc.address, loc.city].filter(Boolean).join(", ") || null;
    byStation.set(stationId, {
      id: `${source}:${stationId}`,
      source,
      category: "irve",
      lat,
      lon,
      name: loc.name || stationId || "Charging point",
      station_id: stationId,
      etat: mapEtat(statusRaw),
      occupation: mapOccupation(statusRaw),
      updated_at: now,
      dynamic_updated_at: now,
      props: {
        address,
        status_raw: statusRaw,
        country,
        evse_count: evses.length,
      },
    });
  }
  return [...byStation.values()];
}
