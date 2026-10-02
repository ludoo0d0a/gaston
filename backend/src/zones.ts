import {
  bboxAround,
  emptyFeatureCollection,
  haversineKm,
  type GeoJsonFeature,
  type GeoJsonFeatureCollection,
} from "./geo";

type ZoneRow = {
  id: string;
  source: string;
  kind: string;
  lat: number;
  lon: number;
  radius_m: number | null;
  vma: number | null;
  props_json: string;
  updated_at: string;
};

export async function searchZones(
  env: Env,
  lat: number,
  lon: number,
  radiusKm: number,
  source: string | null,
  limit: number,
): Promise<GeoJsonFeatureCollection> {
  const box = bboxAround(lat, lon, radiusKm);
  const params: unknown[] = [
    box.minLat,
    box.maxLat,
    box.minLon,
    box.maxLon,
  ];
  let sql = `
    SELECT id, source, kind, lat, lon, radius_m, vma, props_json, updated_at
    FROM zones
    WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
  `;
  if (source) {
    sql += ` AND source = ?`;
    params.push(source);
  }
  sql += ` LIMIT ?`;
  params.push(Math.min(2000, limit * 8));

  const result = await env.DB.prepare(sql)
    .bind(...params)
    .all<ZoneRow>();

  const features: GeoJsonFeature[] = [];
  for (const row of result.results ?? []) {
    const dist = haversineKm(lat, lon, row.lat, row.lon);
    if (dist > radiusKm) continue;
    let props: Record<string, unknown> = {};
    try {
      props = JSON.parse(row.props_json) as Record<string, unknown>;
    } catch {
      /* keep empty */
    }
    features.push({
      type: "Feature",
      geometry: { type: "Point", coordinates: [row.lon, row.lat] },
      properties: {
        id: row.id,
        source: row.source,
        kind: row.kind,
        radius_m: row.radius_m,
        vma: row.vma,
        updated_at: row.updated_at,
        distance_km: Math.round(dist * 1000) / 1000,
        ...props,
      },
    });
    if (features.length >= limit) break;
  }

  features.sort(
    (a, b) =>
      Number(a.properties.distance_km ?? 0) -
      Number(b.properties.distance_km ?? 0),
  );

  if (features.length === 0) {
    return emptyFeatureCollection({
      query: { lat, lon, radius_km: radiusKm, source, limit },
      note: "empty — run radar ingest to populate zones",
    });
  }

  return {
    type: "FeatureCollection",
    features,
    meta: {
      count: features.length,
      query: { lat, lon, radius_km: radiusKm, source, limit },
    },
  };
}
