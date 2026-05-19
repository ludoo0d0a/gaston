#!/usr/bin/env python3
"""Apply string resource replacements to Kotlin sources."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "androidApp/src/main/kotlin"

# Import from generator
import sys
sys.path.insert(0, str(ROOT / "tools"))
from i18n_strings import EN_TO_KEY, STRINGS, EXISTING

# Build full EN -> key from strings.xml too
VALUES = ROOT / "androidApp/src/main/res/values/strings.xml"
xml_pairs = re.findall(r'<string name="([^"]+)">([^<]*)</string>', VALUES.read_text())
for key, en in xml_pairs:
    en_unescaped = en.replace("\\'", "'").replace("&amp;", "&")
    if en_unescaped and "%" not in en_unescaped:
        EN_TO_KEY.setdefault(en_unescaped, key)

# Skip dynamic / technical / previews
SKIP = {
    "Gaston",  # brand
    "error",
    "Body Viewer",
    "Request Details",
    "Fullscreen",
    "Hide on map",
    "Show Logs",
    "Suggest correction",
    "All",
    "Hybrid",
    "Energy",
    "Enseigne",  # same in FR
    "IRVE",
    "OpenTollData",
    "Opérateur",
    "Réseau",
    "Itinéraire",
    "Champ de Mars, 5 Avenue Anatole France, 75007 Paris",
    "Rue de Rivoli, 75001 Paris",
    "Champ de Mars, 5 Avenue Anatole France, 75007 Paris",
}

# Sort by length descending to avoid partial replacements
REPLACEMENTS = sorted(
    [(en, key) for en, key in EN_TO_KEY.items() if en not in SKIP and "${" not in en],
    key=lambda x: -len(x[0]),
)

AUTO_PKG = "fr/geoking/gaston/auto"


def needs_car_import(content: str) -> bool:
    return "carContext.getString" in content and "import fr.geoking.gaston.R" not in content


def needs_compose_import(content: str) -> bool:
    return "stringResource(R.string." in content and "import androidx.compose.ui.res.stringResource" not in content


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text
    rel = str(path.relative_to(KOTLIN_ROOT))
    is_auto = AUTO_PKG in rel.replace("\\", "/")

    for en, key in REPLACEMENTS:
        if is_auto:
            patterns = [
                (f'.setTitle("{en}")', f'.setTitle(carContext.getString(R.string.{key}))'),
                (f'.setHeader(Header.Builder().setTitle("{en}")', f'.setHeader(Header.Builder().setTitle(carContext.getString(R.string.{key})'),
                (f'.addText("{en}")', f'.addText(carContext.getString(R.string.{key}))'),
            ]
        else:
            patterns = [
                (f'contentDescription = "{en}"', f'contentDescription = stringResource(R.string.{key})'),
                (f'title = {{ Text("{en}") }}', f'title = {{ Text(stringResource(R.string.{key})) }}'),
                (f'text = {{ Text("{en}") }}', f'text = {{ Text(stringResource(R.string.{key})) }}'),
                (f'placeholder = {{ Text("{en}") }}', f'placeholder = {{ Text(stringResource(R.string.{key})) }}'),
                (f'label = {{ Text("{en}") }}', f'label = {{ Text(stringResource(R.string.{key})) }}'),
                (f'Text("{en}"', f'Text(stringResource(R.string.{key})'),
                (f'title = "{en}"', f'title = stringResource(R.string.{key})'),
                (f'subtitle = "{en}"', f'subtitle = stringResource(R.string.{key})'),
            ]
        for old, new in patterns:
            text = text.replace(old, new)

    if text == original:
        return False

    if needs_car_import(text):
        if "import fr.geoking.gaston.R" not in text:
            # insert after package
            text = re.sub(
                r'(package [^\n]+\n)',
                r'\1\nimport fr.geoking.gaston.R',
                text,
                count=1,
            )
    if needs_compose_import(text):
        if "stringResource" in text and "import androidx.compose.ui.res.stringResource" not in text:
            text = re.sub(
                r'(import androidx\.compose\.ui\.[^\n]+\n)',
                r'\1import androidx.compose.ui.res.stringResource\n',
                text,
                count=1,
            )
        if "import fr.geoking.gaston.R" not in text and "R.string." in text:
            text = re.sub(
                r'(package [^\n]+\n)',
                r'\1\nimport fr.geoking.gaston.R',
                text,
                count=1,
            )

    path.write_text(text, encoding="utf-8")
    return True


changed = []
for p in KOTLIN_ROOT.rglob("*.kt"):
    if process_file(p):
        changed.append(str(p.relative_to(ROOT)))

print(f"Modified {len(changed)} files")
for c in sorted(changed)[:30]:
    print(" ", c)
if len(changed) > 30:
    print(f"  ... and {len(changed) - 30} more")
