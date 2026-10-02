import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  fuelNameToId,
  parseNationalFrResults,
  parseStooqCsv,
  parseYahooChart,
} from "./fuelParse.mjs";

describe("fuelParse", () => {
  it("fuelNameToId maps FR labels", () => {
    assert.equal(fuelNameToId("E10"), "sp95");
    assert.equal(fuelNameToId("Gazole"), "gazole");
    assert.equal(fuelNameToId("SP98"), "sp98");
    assert.equal(fuelNameToId("unknown"), null);
  });

  it("parseStooqCsv keeps last maxRows closes", () => {
    const csv = [
      "Date,Open,High,Low,Close,Volume",
      "2026-04-01,1,1,1,70.0,0",
      "2026-04-02,1,1,1,71.0,0",
      "2026-04-03,1,1,1,72.0,0",
    ].join("\n");
    const rows = parseStooqCsv(csv, 2);
    assert.equal(rows.length, 2);
    assert.deepEqual(rows[0], { day: "2026-04-02", close: 71 });
    assert.deepEqual(rows[1], { day: "2026-04-03", close: 72 });
  });

  it("parseYahooChart reads timestamp/close pairs", () => {
    const payload = JSON.stringify({
      chart: {
        result: [
          {
            timestamp: [1_704_067_200, 1_704_153_600],
            indicators: { quote: [{ close: [80.5, 81.25] }] },
          },
        ],
      },
    });
    const rows = parseYahooChart(payload, 10);
    assert.equal(rows.length, 2);
    assert.equal(rows[0].close, 80.5);
    assert.equal(rows[1].close, 81.25);
    assert.equal(rows[0].day, "2024-01-01");
  });

  it("parseNationalFrResults skips null year/month/day", () => {
    const rows = parseNationalFrResults({
      results: [
        {
          prix_nom: "E10",
          "year(prix_maj)": null,
          "month(prix_maj)": null,
          "day(prix_maj)": null,
          avg_eur: 1.9,
        },
        {
          prix_nom: "Gazole",
          "year(prix_maj)": 2026,
          "month(prix_maj)": 9,
          "day(prix_maj)": 15,
          avg_eur: 1.8,
        },
      ],
    });
    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0], {
      country: "FR",
      fuel_id: "gazole",
      day: "2026-09-15",
      avg_eur: 1.8,
    });
  });
});
