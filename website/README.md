# Gaston landing page

Static site for **https://gaston.geoking.fr** (Netlify).

## Local preview

```bash
cd website
python3 -m http.server 8080
# open http://localhost:8080
```

Or with Netlify CLI:

```bash
npx netlify-cli dev --dir website
```

## Deploy on Netlify

1. Create a site linked to this repository (or drag-and-drop the `website/` folder).
2. **Build settings**
   - **Publish directory:** `website`
   - **Build command:** *(leave empty — static site)*
3. **Domain:** add custom domain `gaston.geoking.fr` and configure DNS (CNAME to your Netlify subdomain).
4. Enable HTTPS (automatic with Netlify).

`netlify.toml` inside `website/` sets cache headers for assets.

## Contents

| Path | Description |
|------|-------------|
| `index.html` | Landing page (FR/EN toggle) |
| `privacy.html` | Privacy policy |
| `terms.html` | Terms of service |
| `assets/` | Icon, OG image, Play Store screenshots |
| `css/styles.css` | Styles (brand colors from `playstore-assets/README.md`) |
| `js/main.js` | i18n, slideshow, scroll effects |

Screenshots are generated from real app Compose UI (see `scripts/regenerate_screenshots.sh`). After updating store assets:

```bash
./scripts/regenerate_screenshots.sh   # updates playstore-assets/ and website/assets/screenshots/
cp playstore-assets/icon-512.png website/assets/
cp playstore-assets/feature-graphic-1024x500.png website/assets/og-image.png
```
