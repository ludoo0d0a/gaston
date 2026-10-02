/** Approximate degrees for a bounding box around a point (haversine-friendly). */
export function bboxAround(
  lat: number,
  lon: number,
  radiusKm: number,
): { minLat: number; maxLat: number; minLon: number; maxLon: number } {
  const latDelta = radiusKm / 111.32;
  const lonDelta =
    radiusKm / (111.32 * Math.max(0.2, Math.cos((lat * Math.PI) / 180)));
  return {
    minLat: lat - latDelta,
    maxLat: lat + latDelta,
    minLon: lon - lonDelta,
    maxLon: lon + lonDelta,
  };
}

export function haversineKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(a)));
}

export type GeoJsonFeatureCollection = {
  type: "FeatureCollection";
  features: GeoJsonFeature[];
  meta?: Record<string, unknown>;
};

export type GeoJsonFeature = {
  type: "Feature";
  geometry: { type: "Point"; coordinates: [number, number] };
  properties: Record<string, unknown>;
};

export function emptyFeatureCollection(
  meta?: Record<string, unknown>,
): GeoJsonFeatureCollection {
  return { type: "FeatureCollection", features: [], meta };
}

export function parseSearchParams(url: URL): {
  lat: number;
  lon: number;
  radiusKm: number;
  source: string | null;
  limit: number;
} | { error: string } {
  const lat = Number(url.searchParams.get("lat"));
  const lon = Number(url.searchParams.get("lon"));
  const radiusKm = Number(url.searchParams.get("radius_km") ?? "15");
  const source = url.searchParams.get("source");
  const limit = Math.min(
    500,
    Math.max(1, Number(url.searchParams.get("limit") ?? "100")),
  );

  if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
    return { error: "lat must be a number between -90 and 90" };
  }
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) {
    return { error: "lon must be a number between -180 and 180" };
  }
  if (!Number.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 100) {
    return { error: "radius_km must be between 0 and 100" };
  }
  return { lat, lon, radiusKm, source, limit };
}
