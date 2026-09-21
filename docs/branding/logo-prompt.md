# ChatGPT Logo Prompt (SDF Account Status)

Use this prompt in ChatGPT image generation:

---
Design a modern, minimal SVG logo for a JetBrains WebStorm plugin called "NetSuite SDF Account Status".

Context:
- Plugin shows the active NetSuite SDF account in the IDE status bar.
- It highlights environment risk:
  - Sandbox = green
  - Production = red (critical)
  - Unknown = yellow

Visual direction:
- Flat, geometric, clean
- No text in the icon
- Should read clearly at 40x40 px and 80x80 px
- Must work on both dark and light backgrounds
- Primary palette:
  - Deep navy background
  - Green accent
  - Red critical dot/accent
  - Optional subtle yellow accent

Composition ideas:
- Rounded square base
- One central shape that implies "status" or "account" (ring, badge, or gauge)
- Small red corner indicator for production risk

Output requirements:
1. Return valid optimized SVG markup only.
2. Keep vector simple (no filters, no raster effects).
3. Provide two variants:
   - `pluginIcon.svg` (default)
   - `pluginIcon_dark.svg` (dark-theme tuned)
4. Ensure icon fits fully in a 40x40 viewBox with safe padding.

Do not include brand names, trademarked logos, or letters.
---

## Where to place generated files
- `src/main/resources/META-INF/pluginIcon.svg`
- `src/main/resources/META-INF/pluginIcon_dark.svg`
