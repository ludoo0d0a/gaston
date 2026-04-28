#!/usr/bin/env python3
"""
Fetch free/official-ish EV charging "day prices" for France.

This script intentionally uses only public pages / public JSON:
- Fastned tariffs (FR)
- Allego tariffs (FR)
- Electra pricing page (FR, may be dynamic/ranges)
- TotalEnergies published prices (FR, by power tier)
- IONITY subscriptions page (FR "from/minimum" only; station prices vary)
- Tesla Superchargers open-to-all pricing (subset), via public GitHub JSON

Output:
- tmp/ev-prices-fr-YYYY-MM-DD.json
- tmp/ev-prices-fr-YYYY-MM-DD.csv
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import sys
import urllib.request
from dataclasses import dataclass, asdict
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Optional


@dataclass(frozen=True)
class PriceSnapshot:
    provider: str
    country: str
    currency: str
    retrieved_at_utc: str
    source_url: str
    price_model: str
    prices: dict[str, Any]
    notes: Optional[str] = None


UA = "gaston/ev-prices-fr (+https://github.com) python-urllib"


def _http_get(url: str, timeout_s: int = 30) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        charset = resp.headers.get_content_charset() or "utf-8"
        return resp.read().decode(charset, errors="replace")


def _http_get_json(url: str, timeout_s: int = 30) -> Any:
    return json.loads(_http_get(url, timeout_s=timeout_s))


def _now_utc_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def _to_decimal(s: str) -> Decimal:
    s = s.strip()
    s = s.replace("\xa0", " ")
    s = s.replace("€", "").replace("/kWh", "").replace("kWh", "")
    s = s.replace(",", ".")
    s = re.sub(r"[^0-9.]+", "", s)
    try:
        return Decimal(s)
    except (InvalidOperation, ValueError):
        raise ValueError(f"Could not parse decimal from: {s!r}")


def _find_first(pattern: str, text: str, flags: int = 0) -> re.Match[str]:
    m = re.search(pattern, text, flags)
    if not m:
        raise ValueError(f"Pattern not found: {pattern}")
    return m


def fetch_fastned_fr(retrieved_at: str) -> PriceSnapshot:
    url = "https://fastned.nl/fr/recharge/tarifs"
    html = _http_get(url)

    # Fastned shows a "Nouveaux prix au kWh en France" section.
    section = _find_first(r"Nouveaux prix au kWh en France([\s\S]{0,1200})", html, re.IGNORECASE).group(1)
    numbers = re.findall(r"(\d{1,2},\d{2})\s*€", section)
    # Expected: 0,61 (standard), 0,55 (app), 0,43 (subscriber)
    if len(numbers) < 3:
        raise ValueError(f"Fastned FR: expected >=3 prices, got {numbers}")

    standard = _to_decimal(numbers[0])
    app = _to_decimal(numbers[1])
    subscriber = _to_decimal(numbers[2])

    return PriceSnapshot(
        provider="Fastned",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="kwh_fixed_by_offer",
        prices={
            "standard_eur_per_kwh": str(standard),
            "app_eur_per_kwh": str(app),
            "subscription_eur_per_kwh": str(subscriber),
        },
        notes="Tariffs are published as fixed EUR/kWh for France; roaming providers may add fees.",
    )


def fetch_allego_fr(retrieved_at: str) -> PriceSnapshot:
    url = "https://www.allego.eu/fr/tarifs/"
    html = _http_get(url)

    # Allego FR page contains many countries; match the France block explicitly by headings.
    m = re.search(
        r"France[\s\S]*?Chargement ultra-rapide[\s\S]*?€\s*0,(\d{3})/kWh"
        r"[\s\S]*?Chargement rapide[\s\S]*?€\s*0,(\d{3})/kWh"
        r"[\s\S]*?Chargement régulier[\s\S]*?€\s*0,(\d{3})/kWh",
        html,
        re.IGNORECASE,
    )
    if not m:
        raise ValueError("Allego FR: could not match France ultra/fast/regular block")

    ultra_fast = _to_decimal(f"0,{m.group(1)}")
    fast = _to_decimal(f"0,{m.group(2)}")
    regular = _to_decimal(f"0,{m.group(3)}")
    return PriceSnapshot(
        provider="Allego",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="kwh_fixed_by_power_category",
        prices={
            "regular_eur_per_kwh": str(regular),
            "fast_eur_per_kwh": str(fast),
            "ultra_fast_eur_per_kwh": str(ultra_fast),
        },
        notes="Published as default Allego direct-pay tariffs for France; eMSP/roaming may differ.",
    )


def fetch_electra_fr(retrieved_at: str) -> PriceSnapshot:
    url = "https://www.go-electra.com/en/price/"
    html = _http_get(url)

    # Electra states in France: dynamic pricing range shown on page.
    # The raw HTML contains CSS with "between", so anchor on the actual sentence.
    anchor = "Price varies with demand"
    idx = html.lower().find(anchor.lower())
    window = html[idx : idx + 5000] if idx != -1 else html
    window_plain = re.sub(r"<[^>]+>", " ", window)

    # Prefer the "between X-Y€/ kWh" form, else accept a plain "X-Y€/ kWh".
    m = re.search(
        r"between\s*([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*€\s*/\s*kWh",
        window_plain,
        re.IGNORECASE,
    )
    if not m:
        m = re.search(
            r"([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*€\s*/\s*kWh",
            window_plain,
            re.IGNORECASE,
        )
    if not m:
        raise ValueError("Electra: could not find pricing range near 'Price varies with demand'")

    lo = _to_decimal(m.group(1))
    hi = _to_decimal(m.group(2))

    return PriceSnapshot(
        provider="Electra",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="kwh_range_dynamic",
        prices={
            "dynamic_range_eur_per_kwh": {"min": str(lo), "max": str(hi)},
        },
        notes="Electra mentions dynamic pricing in France/Belgium; exact price is displayed/locked in the app at session start.",
    )


def fetch_totalenergies_fr(retrieved_at: str) -> PriceSnapshot:
    url = "https://chargeplus.totalenergies.com/fr/conseils-recharge-electrique/cout-recharge-voiture-electrique/"
    html = _http_get(url)

    # The article includes the tiered prices (<=50 kW / >50 kW) as decimals with commas.
    p1 = _find_first(r"0,(\d{2})\s*€\s*TTC/kWh", html).group(0)
    # Next price after the first
    after = html[html.find(p1) + len(p1) :]
    p2 = _find_first(r"0,(\d{2})\s*€\s*TTC/kWh", after).group(0)

    low = _to_decimal(p1)
    high = _to_decimal(p2)

    return PriceSnapshot(
        provider="TotalEnergies",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="kwh_fixed_by_power_threshold",
        prices={
            "lte_50kw_eur_per_kwh": str(low),
            "gt_50kw_eur_per_kwh": str(high),
        },
        notes="Published station-service charging prices in France (article). Some stations may also have time/occupancy fees.",
    )


def fetch_ionity_fr(retrieved_at: str) -> PriceSnapshot:
    # IONITY does not expose a stable free tariff API; their pages show "from/minimum" and warn station pricing varies.
    url = "https://www.ionity.eu/fr/abonnements"
    html = _http_get(url)

    # Pull first displayed "À partir de 0,xx €/kWh" if present, else fallback to any 0,xx €/kWh.
    m = re.search(r"À partir de\s*([0-9]+,[0-9]{2})\s*€/kWh", html, re.IGNORECASE)
    if not m:
        m = re.search(r"([0-9]+,[0-9]{2})\s*€/kWh", html, re.IGNORECASE)
    if not m:
        raise ValueError("IONITY: could not find any €/kWh on subscriptions page")

    from_price = _to_decimal(m.group(1))
    return PriceSnapshot(
        provider="IONITY",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="kwh_from_minimum",
        prices={"from_eur_per_kwh": str(from_price)},
        notes="IONITY states charging prices vary by charging point; this is a published minimum, not per-station pricing.",
    )


def fetch_tesla_open_to_all_fr(retrieved_at: str) -> PriceSnapshot:
    url = "https://raw.githubusercontent.com/Niek/tesla-superchargers/main/superchargers-with-pricing.json"
    data = _http_get_json(url)

    # The upstream file historically included pricing, but may change over time.
    # It also does not include an explicit country field; we use a coarse France bounding box filter.
    fr_sites: list[dict[str, Any]] = []
    items: list[dict[str, Any]]
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        # Upstream currently publishes a dict keyed by UUID.
        items = [v for v in data.values() if isinstance(v, dict)]
    else:
        raise ValueError(f"Unexpected Tesla pricing JSON top-level type: {type(data)}")

    # Mainland-ish France bounding box (rough): lat [41, 51.6], lon [-5.5, 10.5]
    def in_fr_bbox(lat: float, lon: float) -> bool:
        return 41.0 <= lat <= 51.6 and -5.5 <= lon <= 10.5

    for item in items:
        loc = item.get("location") or {}
        lat = loc.get("latitude")
        lon = loc.get("longitude")
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            continue
        if not in_fr_bbox(float(lat), float(lon)):
            continue
        name = (item.get("name") or "").strip()
        # Extra guard: the upstream dataset does not ship a country field; the name usually includes ", France".
        if "France" not in name:
            continue
        fr_sites.append(
            {
                "name": name,
                "id": item.get("id"),
                "location": {"latitude": float(lat), "longitude": float(lon)},
                "stalls": item.get("stalls"),
                "power_kw": item.get("power"),
                "access": item.get("access"),
                "type": item.get("type"),
                "prices": item.get("prices") or item.get("pricing") or item.get("rates"),
            }
        )

    return PriceSnapshot(
        provider="Tesla (open-to-all Superchargers subset)",
        country="FR",
        currency="EUR",
        retrieved_at_utc=retrieved_at,
        source_url=url,
        price_model="per_site_json_subset",
        prices={"sites_in_fr_bbox": fr_sites, "count_in_fr_bbox": len(fr_sites)},
        notes=(
            "Subset only: Superchargers open to non-Teslas. "
            "France filtering is a rough bounding box (no country field upstream). "
            "Upstream pricing fields may be missing; when present they are passed through."
        ),
    )


def write_json(path: Path, snapshots: list[PriceSnapshot]) -> None:
    payload = {
        "schema": "gaston.ev_prices.fr.v1",
        "generated_at_utc": _now_utc_iso(),
        "snapshots": [asdict(s) for s in snapshots],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_csv(path: Path, snapshots: list[PriceSnapshot]) -> None:
    # Flatten the most common fields; store complex provider-specific pricing in JSON column.
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(
            f,
            fieldnames=[
                "provider",
                "country",
                "currency",
                "retrieved_at_utc",
                "price_model",
                "source_url",
                "notes",
                "prices_json",
            ],
        )
        w.writeheader()
        for s in snapshots:
            w.writerow(
                {
                    "provider": s.provider,
                    "country": s.country,
                    "currency": s.currency,
                    "retrieved_at_utc": s.retrieved_at_utc,
                    "price_model": s.price_model,
                    "source_url": s.source_url,
                    "notes": s.notes or "",
                    "prices_json": json.dumps(s.prices, ensure_ascii=False),
                }
            )


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description="Fetch free EV charging day prices for France.")
    p.add_argument("--out-dir", default="tmp", help="Output directory (default: tmp)")
    p.add_argument("--date", default=None, help="Override date for filenames (YYYY-MM-DD)")
    p.add_argument("--json-only", action="store_true", help="Only write JSON output")
    p.add_argument("--csv-only", action="store_true", help="Only write CSV output")
    args = p.parse_args(argv)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.date:
        day = dt.date.fromisoformat(args.date)
    else:
        day = dt.datetime.now(dt.timezone.utc).date()

    retrieved_at = _now_utc_iso()

    snapshots: list[PriceSnapshot] = []
    failures: list[str] = []

    def run(name: str, fn):
        nonlocal snapshots, failures
        try:
            snapshots.append(fn(retrieved_at))
        except Exception as e:
            failures.append(f"{name}: {e}")

    run("Fastned", fetch_fastned_fr)
    run("Allego", fetch_allego_fr)
    run("Electra", fetch_electra_fr)
    run("TotalEnergies", fetch_totalenergies_fr)
    run("IONITY", fetch_ionity_fr)
    run("Tesla", fetch_tesla_open_to_all_fr)

    base = out_dir / f"ev-prices-fr-{day.isoformat()}"
    json_path = base.with_suffix(".json")
    csv_path = base.with_suffix(".csv")

    if not args.csv_only:
        write_json(json_path, snapshots)
    if not args.json_only:
        write_csv(csv_path, snapshots)

    if failures:
        sys.stderr.write("Some providers failed:\n")
        for f in failures:
            sys.stderr.write(f"- {f}\n")
        # Non-zero exit to make failures visible in CI/cron, but still writes partial output.
        return 2

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

