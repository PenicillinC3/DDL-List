# Embedded font licenses

- **Source Han Serif SC** (思源宋体, VF instance-subset, CFF2->glyf): SIL Open Font License 1.1, copyright (c) Adobe. Full text: `LICENSE-SourceHanSerif-OFL.txt` (https://github.com/adobe-fonts/source-han-serif/blob/release/LICENSE.txt)
- **Timeless Serif Text** (Latin glyphs, `timeless_serif_400/600.ttf`): Timeless Free Font License v1.3, copyright (c) Timeless Ventures Private Limited. The files are embedded byte-for-byte as published — no merging, subsetting or renaming — and are combined with the Han face at runtime only. The name table declares v1.2; this project follows v1.3 (the current version), as permitted by section 8.4. Full text: `LICENSE-Timeless.txt` (https://www.timeless.co/type/license).
- The Han subset and the Timeless statics are separate files; nothing in `app/src/main/assets/fonts/` is a derivative of a Timeless font.
- On API 28 and below there is no `Typeface.CustomFallbackBuilder`, so Latin falls back to the Source Han Serif built-in Latin; Chinese is always the Han face.
