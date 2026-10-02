import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  computeMarketScore,
  marketHorizonPredictions,
  projectNationalTrend,
} from "./fuelForecast.ts";

function closes(...values: number[]) {
  return values.map((close, i) => ({
    day: `2026-04-${String(i + 1).padStart(2, "0")}`,
    close,
  }));
}

describe("fuelForecast", () => {
  it("projectNationalTrend needs min history and projects forward", () => {
    assert.deepEqual(projectNationalTrend([], "2026-04-10"), []);
    const history = [
      { day: "2026-04-01", priceEurPerL: 1.7 },
      { day: "2026-04-02", priceEurPerL: 1.8 },
      { day: "2026-04-03", priceEurPerL: 1.9 },
    ];
    const out = projectNationalTrend(history, "2026-04-03", 3, 2);
    assert.equal(out.length, 2);
    assert.equal(out[0].day, "2026-04-04");
    assert.equal(out[1].day, "2026-04-05");
    assert.ok(out[0].priceEurPerL > 1.9);
  });

  it("computeMarketScore is positive when crude rises", () => {
    const brent = closes(100, 102, 104, 106);
    const ho = closes(2.5, 2.5, 2.5, 2.5);
    const fx = closes(1.1, 1.1, 1.1, 1.1);
    const { score } = computeMarketScore(brent, ho, fx);
    assert.ok(score > 0);
  });

  it("marketHorizonPredictions marks up when score exceeds threshold", () => {
    const preds = marketHorizonPredictions("gazole", 1.8, 0.01, 0.001);
    assert.equal(preds.length, 3);
    assert.equal(preds[0].predictedUp, true);
    assert.ok(preds[0].predictedPriceEurPerL > 1.8);
  });
});
