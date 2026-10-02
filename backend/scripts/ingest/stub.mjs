#!/usr/bin/env node
/**
 * Phase 0 stub — real CSV ingest lands in Phase 1 (Gireve, QualiCharge, radars…).
 * Invoked by GitHub Actions workflow_dispatch / schedule later.
 *
 * Do NOT parse large dumps inside the Worker (Free = 10 ms CPU).
 */
console.log(
  JSON.stringify({
    ok: true,
    phase: 0,
    message:
      "Ingest stub only. Implement scripts/ingest/<source>.mjs and write to D1/R2 in Phase 1.",
    next: ["gireve", "qualicharge", "france-radars", "luxembourg-radars"],
  }),
);
