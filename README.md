# Spice of Life Integrations

A standalone compatibility addon for **Minecraft 1.20.1 / Forge**, maintained by **UpperMoon0**. It connects Spice of Life Classic's food depletion with native TerraFirmaCraft consumption and Sophisticated feeding upgrades. Upstream mod jars stay unmodified.

**Development status: experimental.** Build, policy-test, and transformed development-server results are recorded in [the validation report](docs/VALIDATION.md). Those checks do not establish packaged-server, client, or full TFG-pack compatibility. Complete the remaining staging checks before using a build in a real world.

## Intended behavior

### TerraFirmaCraft food

- Restores the native TFC food-consumption path when Classic bypasses it.
- Applies Classic depletion only to hunger and the resulting saturation gain. Classic's integer hunger calculation remains authoritative by default.
- Keeps TFC responsible for water, nutrients, rotten-food effects, limits, and other native food behavior.
- Passes the original food capability and food-data object through native handling instead of rebuilding them. This is important for TFG extensions that attach nutrients or metadata to object identity.
- Leaves Classic responsible for food history, synchronization, and basket selection. Successful consumption should produce one consumption, one applicable history update, and one item decrement, including direct basket feeding.

For native base hunger `H > 0`, native saturation gain `S`, and effective integer hunger `E`, the native saturation modifier is retained. The resulting pre-cap saturation gain is `S * E / H`. Rounding hunger therefore also affects saturation. This addon does not scale water or nutrients by the depletion multiplier.

### Sophisticated automatic feeding

- Tries foods in descending order of their **current Classic multiplier**: least depleted first, independently of base hunger value.
- Recalculates ranking for each feeding attempt. Equal multipliers retain the original slot order.
- Ranks a wicker basket using the meal Classic currently selects from that basket.
- Uses the original feeding callback for consumption, filters, hunger thresholds, ordinary-food containers, and the successful-attempt stop condition. Basket candidates also pass the native eligibility checks before Classic's basket shortcut.
- Traverses a read-only slot view; it does not sort the stored inventory or consume food speculatively while ranking.

Classic's existing backpack-basket shortcut calls `Player.eat` directly. This addon preserves that shortcut, including its omission of item-specific `finishUsingItem` container/effect behavior; it does not repair that upstream limitation. Ordinary backpack foods and hand-used baskets retain their own item-use paths.

The ordering change does not make a food eligible by itself. A zero-multiplier candidate can still be considered if the original feeding logic permits it. Classic-blacklisted foods retain Classic's non-depleting treatment.

## Target versions

These are implementation targets, **not a claim that the runtime matrix has passed**.

| Component | Target | Requirement |
| --- | --- | --- |
| Java | 17 | Build and run |
| Minecraft | 1.20.1 | Required |
| Forge | 47.4.13 | Build target; metadata accepts 47.4.13 through 47.x |
| Spice of Life Classic (`solclassic`) | 2.13 | Required, exact version |
| TerraFirmaCraft (`tfc`) | 3.2.25 and 3.2.28 | Optional native-food integration; API checked |
| Sophisticated Core (`sophisticatedcore`) | 1.3.84.2308 and 1.5.16.2433 | Optional feeding integration; API checked |
| Sophisticated Backpacks | 3.24.67.2109 | Target backpack runtime |
| TFG Core | 0.9.23 | Target extended-nutrient runtime, with its full required stack |

Optional TFC and Sophisticated Core integrations have no version ceiling. Before enabling them, the mixin plugin reads their class files without loading optional classes and checks the API signatures and exact injection-call counts the addon requires. Changed or missing targets disable the affected integration with a specific error. Missing optional mods leave their integrations inactive. Classic remains pinned to 2.13. Structural checks cannot establish future gameplay semantics; the versions above are runtime test targets, and future updates still need gameplay validation. TFG and Backpacks are runtime targets rather than separate version gates.

## Configuration

Forge registers a server-owned configuration, synchronized to connected clients. After the first test-world start, edit `<world>/serverconfig/solintegrations-server.toml` while that test server is stopped. For new worlds, the pack can provide defaults through Forge's `defaultconfigs` directory. Restart the test server when changing settings during validation.

```toml
[integrations]
tfc = true
sophisticatedFeeding = true

[rounding]
positiveHungerMinimum = false
minimumMultiplier = 0.0
```

- `integrations.tfc`: enables the native TFC consumption bridge.
- `integrations.sophisticatedFeeding`: enables depletion-aware feeding order.
- `rounding.positiveHungerMinimum`: opt-in floor of one hunger when a positive-base-hunger food has a strictly positive, finite multiplier and Classic's result is zero. Default: disabled.
- `rounding.minimumMultiplier`: minimum positive multiplier eligible for that addon floor, from `0.0` to `1.0`. A multiplier of exactly zero never qualifies, even with a threshold of zero.

The optional floor does not alter Classic's own minimum-nutrition setting. If Classic is configured to grant a minimum at zero multiplier, its result is retained. Test the two settings independently rather than assuming “zero multiplier” always means zero restoration.

## Build

Use JDK 17 and the checked-in Gradle wrapper. Dependency resolution requires access to the repositories declared in `settings.gradle` and `build.gradle`.

```sh
./gradlew --no-daemon test build jarJar
```

On Windows, use `gradlew.bat --no-daemon test build jarJar`.

The intended installable output is the reobfuscated **Jar-in-Jar** artifact under `build/libs/`, containing the addon and its bundled MixinExtras dependency. For version 0.1.1 its name is `solintegrations-0.1.1.jar`; the `-slim.jar` is an intermediate output without the bundled dependency. Use the packaged jar. The CI workflow checks for `META-INF/jarjar/metadata.json` before collecting that artifact. Confirm the exact filename and checksum from the successful build.

Test reports, when generated, are at `build/reports/tests/test/index.html` and `build/test-results/test/`. Pure unit tests cover restoration arithmetic, restoration-scope isolation, and depletion ordering; they do not start Minecraft.

[Build CI](.github/workflows/build.yml) runs on pushes, pull requests, and manual dispatch with JDK 17. It retains test reports, successful packaged build artifacts, and five current-dependency headless GameTest profiles plus the legacy combined profile with explicit completion checks. Matching `v<mod_version>` tags on commits reachable from `main` can publish the tested JAR, checksums, and provenance to GitHub Releases after all checks pass. Pull requests and ordinary pushes cannot publish. See [release instructions](docs/RELEASING.md). CurseForge publishing and server deployment are not configured.

## Test installation

1. Make a disposable staging copy of the pack and world, with separate client and dedicated-server instances. Record the exact dependency versions and original jar checksums.
2. Build successfully, inspect the packaged addon jar, and add it to the staging `mods/` directories. Keep client and server mod sets aligned for initial testing; server-only compatibility has not been established.
3. Keep the original Classic, TFC, TFG, Core, and Backpacks jars unchanged. Install each mod's ordinary required dependencies as well.
4. Run the [validation matrix](docs/VALIDATION.md), including optional integrations absent, individually present, and the complete TFG stack.
5. Retain logs, configuration, checksums, and measured results for the exact commit tested. Treat any failed or unrun row as a remaining validation gap.

This repository does not perform production restarts, mod replacement, world migration, or deployment.

## Reporting a problem

Include the addon commit/version and jar checksum, Forge and Java versions, full relevant mod versions, both addon and Classic configuration, reproduction steps, and the dedicated-server/client logs. For food issues, include the item ID, relevant food metadata, multiplier before consumption, before/after hunger, saturation, water, nutrients, history, and item counts. Remove credentials, addresses, and unrelated player information from shared logs.

## License and upstream notices

New addon code and documentation are available under the [MIT License](LICENSE). Dependencies and Forge-provided tooling retain their respective licenses; this license does not relicense upstream mods or their assets. See [CHANGELOG.md](CHANGELOG.md) for addon changes.
