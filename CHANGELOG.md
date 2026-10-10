# Changelog

This file tracks Spice of Life Integrations, independently of upstream Forge and mod release histories.

## 0.1.0 — Unreleased

### Added

- Standalone Forge 1.20.1 addon targeting Spice of Life Classic 2.13, without patching or repackaging upstream mod jars.
- Optional TerraFirmaCraft 3.2.25 consumption bridge that preserves native food handling and food-data identity while applying Classic depletion to hunger and resulting saturation.
- Native handling for water, nutrients, extended TFG food metadata, and rotten-food behavior, with TFG Core 0.9.23 as a staging target.
- Optional depletion-aware Sophisticated feeding order targeting Core 1.3.84.2308 and Backpacks 3.24.67.2109, with stable slot-order ties, current-history ranking, and native eligibility checks.
- Basket-aware scoring and consumption handling, including the direct-basket path.
- Server configuration switches for both integrations and an opt-in positive-multiplier hunger floor, disabled by default.
- Exact-version optional integration gates, pure Java policy tests, a staged validation checklist, and a JDK 17 build workflow with tag-gated GitHub JAR releases after build and all five runtime profiles pass.

- Release artifact provenance/checksum checks, immutable collision handling, draft-first publication, and same-run retry safety. No CurseForge publishing.

### Validation status

- Current build, unit-test, artifact, and headless development-server evidence is recorded in [docs/VALIDATION.md](docs/VALIDATION.md).
- Optional-profile GameTests contain deliberate no-op cases. Only the active cases in each profile establish behavior.
- Packaged-server, client, and full TFG-pack checks remain separate staging requirements. Test-only configuration bootstrapping is not evidence of normal combined startup.
- No production installation, restart, world migration, or deployment is part of this release preparation.

Do not promote this entry to a verified release based only on a build or unit-test pass.
