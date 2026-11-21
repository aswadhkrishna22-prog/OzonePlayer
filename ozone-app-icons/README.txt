Ozone Player icon pack
======================
app/src/main/res/   adaptive launcher icons (4 variants) + vector wave + colours  -> copy into the app module
kotlin/appicon/     AppIconVariant, AppIconManager (alias switching), OzoneLogo composable (intro animation / settings previews)
manifest/           activity-alias snippet (default = lime + black wave)
branding/           PNG (512, 1024) and SVG of each icon
store/              512px Play Store / full-bleed icons
web/                favicon set + head snippet for the GitHub Pages site (default icon)
readme/             README header + icon
optional-legacy/    PNG launcher icons, only if minSdk < 26
ANTIGRAVITY_PROMPT.md  paste this into Antigravity to wire everything up (CI builds only)

Variants: lime (default, black wave) | lime_white | midnight (black bg, white wave) | snow (white bg, black wave)
