import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  locationsToEvsePois,
  locationsToStationPois,
  mapEtat,
  mapOccupation,
  pickStationStatus,
} from "./ocpiStatus.mjs";

describe("ocpiStatus", () => {
  it("maps EVSE status to etat/occupation", () => {
    assert.equal(mapEtat("AVAILABLE"), "en_service");
    assert.equal(mapOccupation("AVAILABLE"), "libre");
    assert.equal(mapOccupation("CHARGING"), "occupe");
    assert.equal(mapEtat("OUTOFORDER"), "hors_service");
    assert.equal(mapOccupation("RESERVED"), "reserve");
  });

  it("pickStationStatus prefers AVAILABLE over CHARGING", () => {
    assert.equal(
      pickStationStatus([{ status: "CHARGING" }, { status: "AVAILABLE" }]),
      "AVAILABLE",
    );
    assert.equal(pickStationStatus([{ status: "REMOVED" }]), "UNKNOWN");
  });

  it("locationsToEvsePois emits one row per EVSE and skips REMOVED", () => {
    const pois = locationsToEvsePois(
      [
        {
          id: "st1",
          name: "Station A",
          address: "1 rue",
          city: "Bruxelles",
          coordinates: { latitude: "50.85", longitude: "4.35" },
          evses: [
            { evse_id: "BE*E1", status: "AVAILABLE" },
            { uid: "gone", status: "REMOVED" },
            { uid: "BE*E2", status: "CHARGING" },
          ],
        },
      ],
      { source: "belgium-nap", country: "BE", now: "2026-10-02T00:00:00Z" },
    );
    assert.equal(pois.length, 2);
    assert.equal(pois[0].id, "belgium-nap:BE*E1");
    assert.equal(pois[0].occupation, "libre");
    assert.equal(pois[1].id, "belgium-nap:BE*E2");
    assert.equal(pois[1].occupation, "occupe");
    assert.equal(pois[0].props.country, "BE");
  });

  it("locationsToStationPois dedupes by location id and rolls up status", () => {
    const pois = locationsToStationPois(
      [
        {
          id: "NL-1",
          name: "Amsterdam",
          coordinates: { latitude: 52.37, longitude: 4.89 },
          evses: [
            { evse_id: "a", status: "CHARGING" },
            { evse_id: "b", status: "AVAILABLE" },
          ],
        },
        {
          id: "NL-1",
          name: "dup",
          coordinates: { latitude: 52.38, longitude: 4.9 },
          evses: [{ evse_id: "c", status: "AVAILABLE" }],
        },
      ],
      { source: "dotnl", country: "NL", now: "2026-10-02T00:00:00Z" },
    );
    assert.equal(pois.length, 1);
    assert.equal(pois[0].id, "dotnl:NL-1");
    assert.equal(pois[0].occupation, "libre");
    assert.equal(pois[0].props.evse_count, 2);
  });
});
