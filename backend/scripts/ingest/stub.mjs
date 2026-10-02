# Ignore Phase 0 stub runner — real entrypoint is run.mjs
console.log(
  JSON.stringify({
    ok: true,
    phase: 1,
    message: "Use: npm run ingest -- --source=<id> --local",
    sources: [
      "gireve",
      "qualicharge",
      "france-radars",
      "luxembourg-radars",
      "minetur",
      "mimit",
    ],
  }),
);
