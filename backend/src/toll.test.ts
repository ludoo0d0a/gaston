import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  estimateToll,
  mergeOpenTollData,
  type OpenTollData,
} from "./toll.ts";

function twoBoothData(): OpenTollData {
  return {
    networks: [
      {
        network_name: "n1",
        tolls: ["A", "B"],
        connection: {
          A: {
            B: { distance: "10", price: { class_1: "2.5" } },
          },
        },
      },
    ],
    toll_description: {
      A: { lat: "45.0", lon: "5.0", type: "close" },
      B: { lat: "45.01", lon: "5.01", type: "close" },
    },
    open_toll_price: {},
    version: "test",
  };
}

describe("toll estimate", () => {
  it("returns entry→exit for two booths", () => {
    const route: Array<[number, number]> = [
      [44.99, 4.99],
      [45.0, 5.0],
      [45.005, 5.005],
      [45.01, 5.01],
      [45.02, 5.02],
    ];
    const out = estimateToll(twoBoothData(), route, 1);
    assert.ok(out);
    assert.equal(out!.amount_eur, 2.5);
    assert.equal(out!.segments.length, 1);
    assert.equal(out!.segments[0].type, "close");
  });

  it("three close booths uses entry→exit not hop sum", () => {
    const data: OpenTollData = {
      networks: [
        {
          network_name: "n1",
          tolls: ["A", "B", "C"],
          connection: {
            A: {
              B: { distance: "10", price: { class_1: "2.0" } },
              C: { distance: "25", price: { class_1: "4.0" } },
            },
            B: {
              C: { distance: "15", price: { class_1: "3.0" } },
            },
          },
        },
      ],
      toll_description: {
        A: { lat: "45.00", lon: "5.00", type: "close" },
        B: { lat: "45.01", lon: "5.01", type: "close" },
        C: { lat: "45.02", lon: "5.02", type: "close" },
      },
    };
    const route: Array<[number, number]> = [
      [44.99, 4.99],
      [45.0, 5.0],
      [45.01, 5.01],
      [45.02, 5.02],
      [45.03, 5.03],
    ];
    const out = estimateToll(data, route, 1);
    assert.ok(out);
    assert.equal(out!.amount_eur, 4.0);
  });

  it("two networks sum two lookups", () => {
    const data: OpenTollData = {
      networks: [
        {
          network_name: "n1",
          tolls: ["A", "B"],
          connection: {
            A: { B: { distance: "10", price: { class_1: "2.5" } } },
          },
        },
        {
          network_name: "n2",
          tolls: ["C", "D"],
          connection: {
            C: { D: { distance: "20", price: { class_1: "3.5" } } },
          },
        },
      ],
      toll_description: {
        A: { lat: "45.00", lon: "5.00", type: "close" },
        B: { lat: "45.01", lon: "5.01", type: "close" },
        C: { lat: "45.02", lon: "5.02", type: "close" },
        D: { lat: "45.03", lon: "5.03", type: "close" },
      },
    };
    const route: Array<[number, number]> = [
      [44.99, 4.99],
      [45.0, 5.0],
      [45.01, 5.01],
      [45.02, 5.02],
      [45.03, 5.03],
      [45.04, 5.04],
    ];
    const out = estimateToll(data, route, 1);
    assert.ok(out);
    assert.equal(out!.amount_eur, 6.0);
    assert.equal(out!.segments.length, 2);
  });

  it("open + close sums both", () => {
    const data: OpenTollData = {
      networks: [
        {
          network_name: "n1",
          tolls: ["A", "B"],
          connection: {
            A: { B: { distance: "10", price: { class_1: "2.5" } } },
          },
        },
      ],
      toll_description: {
        OPEN1: { lat: "44.995", lon: "4.995", type: "open" },
        A: { lat: "45.00", lon: "5.00", type: "close" },
        B: { lat: "45.01", lon: "5.01", type: "close" },
      },
      open_toll_price: {
        OPEN1: { distance: "0", price: { class_1: "1.2" } },
      },
    };
    const route: Array<[number, number]> = [
      [44.99, 4.99],
      [44.995, 4.995],
      [45.0, 5.0],
      [45.01, 5.01],
      [45.02, 5.02],
    ];
    const out = estimateToll(data, route, 1);
    assert.ok(out);
    assert.equal(out!.amount_eur, 3.7);
  });

  it("mergeOpenTollData concatenates networks", () => {
    const merged = mergeOpenTollData([
      {
        networks: [{ network_name: "a", tolls: ["X"] }],
        toll_description: { X: { lat: "1", lon: "2", type: "close" } },
      },
      {
        networks: [{ network_name: "b", tolls: ["Y"] }],
        toll_description: { Y: { lat: "3", lon: "4", type: "open" } },
        open_toll_price: { Y: { price: { class_1: "1" } } },
      },
    ]);
    assert.equal(merged.networks?.length, 2);
    assert.ok(merged.toll_description?.X);
    assert.ok(merged.toll_description?.Y);
    assert.ok(merged.open_toll_price?.Y);
  });
});
