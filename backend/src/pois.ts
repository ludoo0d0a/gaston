import {
  bboxAround,
  emptyFeatureCollection,
  haversineKm,
  type GeoJsonFeature,
  type GeoJsonFeatureCollection,
} from "./geo";

type PoiRow = {
  id: string;
  source: string;
  category: string;
  lat: number;
  lon: number;
  name: string | null;
  station_id: string | null;
  props_json: string;
  etat: string | null;
  occupation: string | null;
  updated_at: string;
  dynamic_updated_at: string | null;
};

export async function searchPois(
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
    SELECT id, source, category, lat, lon, name, station_id, props_json,
           etat, occupation, updated_at, dynamic_updated_at
    FROM pois
    WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
  `;
  if (source) {
    sql += ` AND source = ?`;
    params.push(source);
  }
  sql += ` LIMIT ?`;
  // Over-fetch then filter by haversine (bbox is approximate).
  params.push(Math.min(2000, limit * 8));

  const result = await env.DB.prepare(sql)
    .bind(...params)
    .all<PoiRow>();

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
        category: row.category,
        name: row.name,
        station_id: row.station_id,
        etat: row.etat,
        occupation: row.occupation,
        updated_at: row.updated_at,
        dynamic_updated_at: row.dynamic_updated_at,
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
      note: "empty — run ingest (GitHub Actions) to populate D1",
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
