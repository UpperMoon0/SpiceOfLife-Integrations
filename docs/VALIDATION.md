# Staged validation

## Current evidence and limits

Local record, 2026-10-10. Tested source revision: `4d401e47d5a60471b9856379cd1efa1602263770` (subsequent documentation-only commits do not alter tested code).

| Layer | Status | What it establishes |
| --- | --- | --- |
| Clean JDK 17 Forge build | PASS | `clean test build compileGameTestJava`; all 17 tasks executed |
| JUnit policy/helper tests | 46 passed, zero failures/errors/skips | Arithmetic, scope isolation, version gates, ordering and handler mapping |
| Runtime-evidence checker tests | 29 passed | Rejects startup-only success, stale/mismatched logs, incorrect counts, failed processes and reused test directories |
| Reobfuscated Jar-in-Jar artifact | Inspected | Correct metadata, seven mixins/refmap, bundled MixinExtras 0.5.0; no GameTest fixtures or upstream mod implementations bundled |
| Five isolated userdev GameTest profiles | PASS, all five | See profile-specific active coverage below; no-op cases are not feature evidence |
| GitHub Actions execution | Not run | The checked-in workflow has not executed on GitHub |
| Packaged-jar dedicated server and client | Not run | Userdev uses a different runtime/mapping path; artifact inspection is not packaged runtime validation |
| Full TFG Core 0.9.23 pack | Not run | TFG identity preservation is structurally reviewed, not full-pack execution evidence |
| Production | Not performed | No production installation or restart |

Packaged artifact: `solintegrations-0.1.0.jar`, 674610 bytes, SHA-256 `ee4180ec10d5c242941f605268d1e649f364dfe750741e6423407ee86282c92d`.

A source inspection, a successful build, and a successful gameplay test are different kinds of evidence. Rerun affected checks after code changes. The development launcher can exit zero and print `BUILD SUCCESSFUL` even after server startup fails; this project's runner therefore requires a fresh, correctly counted GameTest completion sequence as well as successful process exits.

### Inspected dependency provenance

The following downloaded dependency artifacts were SHA-256 checked and their relevant bytecode API signatures inspected during implementation. These are upstream input hashes, not addon build hashes or gameplay results.

| Input artifact | Maven coordinate | SHA-256 |
| --- | --- | --- |
| Classic 2.13 | `curse.maven:foodvariations-1109288:8832188` | `c7f1fe7f1d183ca69d69ae1e37284632ef997dff18bfbfce27de343f83dcc336` |
| TFC 3.2.25 | `maven.modrinth:terrafirmacraft:3.2.25` | `de698428dc69ec31a5e3140d5b46c9d6b1f531fd1317eacd0cdc644fe5c56b92` |
| Sophisticated Core 1.3.84.2308 | `curse.maven:sophisticated-core-618298:8705513` | `b9262af063edc20459262d3e99890f0a3ea68c86eeddebe63c806ce386079a96` |

## 1. Prepare an isolated staging environment

- Use Java 17, Minecraft 1.20.1, Forge 47.4.13, and the exact mod versions listed in the README.
- Use a disposable dedicated-server instance and a matching client. Do not point the test server at a production world or production paths.
- Copy any representative world only after taking a verified backup. Use separate copies for baseline and addon measurements so food history, time, decay, and nutrition start consistently.
- Record the complete mod list, configs, datapacks, scripts, addon commit, and SHA-256 hashes of all relevant jars. Include dependencies required by TFC, TFG, and Sophisticated mods.
- Keep original mod jars unchanged. Do not replace Classic with a modified jar or install multiple addon versions.
- Complete any Minecraft server terms acceptance yourself before starting the staging server.
- Disable unrelated automated feeding for controlled hand/basket tests. Keep natural hunger, hydration loss, time passage, decay, health, and food-history state controlled or record their effects.
- Use clean test-player histories and sufficient headroom below hunger, saturation, hydration, and nutrient caps. Cap-limited observations cannot prove the intended uncapped gain.

## 2. Build and inspect the artifact

Run from a clean checkout of the commit under test:

```sh
java -version
./gradlew --no-daemon test build jarJar
```

If any task fails, retain the complete log and name the failed stage. Do not call a dependency download failure a source compilation failure or a successful unit-test run a package build pass.

Verify the packaged jar in `build/libs/`:

1. It is produced by `jarJar` and reobfuscated, rather than being a development or intermediate jar.
2. It contains `META-INF/mods.toml`, `solintegrations.mixins.json`, the generated refmap, addon classes, and `META-INF/jarjar/metadata.json` with a bundled MixinExtras jar.
3. Mod metadata has the expected addon version, `solintegrations` mod ID, MIT license, and required Classic version.
4. No Classic, TFC, TFG, Sophisticated Core, or Backpacks implementation is accidentally bundled into the addon.
5. Record the final artifact filename and SHA-256. Compare upstream jar checksums again after staging installation.

Archive the Gradle test XML and HTML reports. CI collects packaged-jar and report artifacts and runs the five headless profiles below. It does not run the packaged client/server or full-pack staging matrix. Actions are pinned to verified release commits: [checkout v7.0.0](https://github.com/actions/checkout/releases/tag/v7.0.0), [setup-java v6.0.0](https://github.com/actions/setup-java/releases/tag/v6.0.0), and [upload-artifact v7.0.0](https://github.com/actions/upload-artifact/releases/tag/v7.0.0).

## 3. Dedicated-server startup matrix

Every addon row includes Forge, Classic 2.13, and the addon. Supply each optional mod's ordinary dependencies. Mark rows PASS, FAIL, or NOT RUN and attach logs; none is pre-approved as passing.

| ID | Optional integrations | Required checks |
| --- | --- | --- |
| S0 | Neither TFC nor Sophisticated Core/Backpacks | Server reaches ready state; matching client joins; no optional-class linkage or mixin error; ordinary Classic eating still works |
| S1 | TFC 3.2.25 only | Native-food mixins apply; no Sophisticated class errors; hand and basket consumption tests run |
| S2 | Core 1.3.84.2308 + Backpacks 3.24.67.2109 only | Feeding mixin applies without TFC classes; ordinary-food ordering and filters work |
| S3 | TFC + Core + Backpacks, exact target versions | Both integrations load; mixed hand/basket/backpack tests run |
| S4 | S3 + TFG Core 0.9.23 and its complete required stack | Pack boots with its normal scripts/datapacks; extended food and nutrient tests run |
| S5 | Exact supported mods, addon integrations disabled | Each switch disables its behavior without breaking startup; compare against upstream behavior |
| S6 | Core present without Backpacks, if allowed by that dependency set | No assumed Backpacks class dependency; no crash merely from loading Core |
| S7 | One optional mod at a different, dependency-compatible version | Verify the relevant addon integration is skipped; do not treat this as support for that version |

For S5, test both switches off, then each off independently. For each row inspect server and client logs for missing classes, failed injections, refmap problems, mixin target mismatches, and repeated runtime exceptions. A successful single-player launch is not a substitute for these dedicated-server rows.

## 4. Measurement method

For each consumed meal record:

- Item registry ID, count, relevant stack metadata/NBT, food capability values, decay/rot state, and basket slot where applicable.
- Classic multiplier immediately before consumption, its configured calculation rules, its minimum-nutrition option, and blacklist membership.
- Hunger, saturation, water, every native TFC nutrient, and any TFG extended nutrients immediately before and after.
- Classic history before and after, selected physical storage slot, item and container counts, and whether use was actually accepted.
- The feeding upgrade's filter, hunger threshold, hurt-feeding option, player health, cooldown, and client/server observations.

Use short observation windows and a no-consumption control to distinguish food effects from natural ticking. If UI rounding hides fractional changes, use an appropriate staging-only diagnostic or debugger to read the underlying values. Do not invent a pass from a rounded HUD number.

For native-effect baselines, compare the same food and world/player fixture against TFC without Classic/addon where practical, and against the supported stack with depletion configured for a full multiplier. For addon-disabled behavior, retain a separate supported-stack control. These controls answer different questions; record the precise stack used for each.

## 5. Consumption matrix

Repeat relevant rows through all three routes: **hand eating**, **Classic wicker basket**, and **Sophisticated feeding upgrade**. Include a wicker basket inside the backpack to exercise direct basket feeding. Reset the player fixture when comparing routes.

| ID | Case | Expected result to verify |
| --- | --- | --- |
| F0 | Fresh food, multiplier 1, all native fields nonzero | One native consumption; native hunger/saturation, water, nutrients, effects, and item/container handling match the baseline |
| F1 | Repeated same food through positive depletion steps | Classic history advances once per applicable meal; hunger uses Classic's integer result; saturation follows the effective/base hunger ratio; water and nutrients retain native behavior |
| F2 | Fractional case such as base hunger 4, multiplier 0.95, native saturation 2.4 | With Classic's usual truncation, effective hunger 3 and pre-cap saturation 1.8, not hunger 4 or saturation 2.28; confirm the actual configured Classic calculation |
| F3 | Positive multiplier too small to restore hunger; addon floor off | Classic's result is retained; no addon-imposed minimum; native water/nutrients and item costs still follow the consumption path |
| F4 | Addon floor on, multiplier below / equal to / above threshold | Only a finite, strictly positive multiplier at or above the threshold can turn a zero Classic result into one hunger; resulting saturation uses that integer hunger |
| F5 | Multiplier exactly zero, Classic minimum disabled | Addon floor never revives the food; if native use is accepted, restoration is zero while native non-restoration handling and applicable history/item cost occur once; if the native feeder rejects it, no consumption occurs |
| F6 | Multiplier zero, Classic's own minimum enabled | Preserve Classic's own result; distinguish it from the addon floor; no forced blacklist of zero-multiplier candidates |
| F7 | Native zero-base-hunger food | No invented hunger or division-by-zero; water/nutrient/effect behavior remains native when the original use path accepts it |
| F8 | Rotten food, each route that permits consumption | TFC determines rejection or rotten effects; no accidental fresh-food restoration; no duplicate native call, advancement, history entry, or item loss |
| F9 | Classic-blacklisted food | Classic's depletion/history exemptions remain intact; native food effects still occur; direct-basket item cost is not skipped |
| F10 | Container-returning food | Compare each route with its unchanged upstream control: ordinary foods/hand-used baskets must preserve container handling. Classic backpack-basket shortcut omits item-specific finishUsingItem container/effect behavior; document that pre-existing limitation, not an addon guarantee. |
| F11 | Two stacks of the same item with different food metadata | The consumed stack's actual food values and state are used; no rebuilding or substitution that drops metadata |
| F12 | Full caps / near caps | TFC/vanilla limits remain authoritative; client and server agree after synchronization; no overflow or negative values |
| F13 | Reconnect and server restart on the staging world | History, inventory, nutrition, and configuration persist as the underlying mods intend; no extra consumption on reconnect |

For F5/F9 direct baskets, explicitly count contents before and after: a meal must not provide native benefits without consuming its item. For all routes, canceled or failed use must not be logged as a successful meal. Do not require history for an item Classic deliberately excludes.

## 6. Feeding selection, cycling, and native eligibility

1. Put at least three foods in known physical slots with different Classic multipliers and deliberately different base hunger values. Confirm that the highest-multiplier **eligible** candidate wins, even when it is not first or the most filling.
2. Give two candidates equal multipliers. Confirm the earlier physical slot wins. Ensure stacks did not move and rejected candidates were not consumed.
3. Feed repeatedly across enough attempts to change relative depletion. Record the selected food each time and show that selection follows the newly current multipliers rather than sticking to an initially cached winner. Do not require a fixed cycle when the pack's depletion formula produces different ties.
4. Place the highest-ranked item on the native deny filter, then use an allow filter that excludes it. Confirm it is skipped and the next eligible food can be used. Repeat with a basket as the excluded candidate.
5. Test insufficient hunger, each configured hunger threshold, immediate feeding while hurt on/off, healthy versus injured players, cooldown, nonfood items, rotten food, and full hunger. Compare acceptance to the same upstream settings without the ordering change.
6. Test empty storage, no eligible foods, all equal scores, and all zero scores. Preserve the original no-success/default outcome; never force a meal solely because it was ranked.
7. Mix loose food and baskets. Confirm scoring reflects Classic's current selected inner meal while filters and hunger checks remain the native upgrade's checks. Verify the correct physical slot and inner item are decremented once.
8. Test two players with different Classic histories using equivalent inventories. Ranking must be player-specific and not leak one player's state into another's attempts.
9. Confirm read-only ranking does not alter item metadata, history, inventory order, counts, or containers before a successful original consumption action.

## 7. TFG food-data identity and extended nutrients

Use the complete target TFG stack, not TFG Core alone. Choose at least one food with measurable TFG-specific nutrients or metadata as well as water and native TFC nutrients.

- Compare multiplier 1, a fractional multiplier, a small positive multiplier that rounds to zero, and exactly zero, subject to native eligibility.
- Verify extended and native nutrient updates against native controls while hunger/saturation follow depletion.
- If diagnostics are available, observe that the native consume path receives the same food-data object associated with the actual food capability; equivalent copied field values are insufficient evidence for identity-keyed metadata.
- Repeat using different metadata variants of the same base item, hand eating, basket eating, and backpack eating.
- Check rotten variants and native nutrient caps. Verify there is no duplicate nutrient update from two native consumption calls.

If extended nutrient values cannot be measured reliably, mark this stage inconclusive rather than passed.

## 8. Results and exit criteria

Record a row for every attempted check:

| Commit / jar SHA-256 | Matrix ID and route | Stack/config fixture | Observed values and logs | Status |
| --- | --- | --- | --- | --- |
| To record | To record | To record | To record | NOT RUN |

Staging validation is complete only when the exact final artifact builds, its contents are inspected, all required startup profiles pass, and measured gameplay results satisfy the relevant rows. Document any intentionally omitted profile as a compatibility limit. Attach failing logs and exact reproduction steps rather than editing expected results to match a failure.

No step here authorizes a production install, restart, upstream-jar replacement, or world migration. Promotion to a real server is a separate decision after the staging evidence has been reviewed.


## Automated Forge GameTest profiles

The separate `gameTest` source set is excluded from the shipped addon jar. Run with JDK 17:

```sh
./gradlew clean test build compileGameTestJava
python3 -m unittest discover -s scripts/tests -v
bash scripts/run_gametest_profile.sh none
bash scripts/run_gametest_profile.sh withTfc
bash scripts/run_gametest_profile.sh withCore
bash scripts/run_gametest_profile.sh withBackpacks
bash scripts/run_gametest_profile.sh withTfc+withBackpacks
```

The runner creates a fresh UUID-scoped world/config/log directory under `build/gametest-ci` for every invocation. It excludes optional client asset downloads (`-x downloadAssets`), records the process result, and writes `verification.json` only after checking the exact source-derived test count and ordered completion markers. Retain that JSON and its invocation transcript. This is headless evidence, not client/visual validation.

`withTfc` includes TFC 3.2.25 and Patchouli 1.20.1-84.1-forge; `withBackpacks` includes both pinned Sophisticated mods. Architectury 9.2.14 is present for Classic in every profile.

There are 23 registered tests. Discovery methods deliberately succeed without executing optional feature assertions when their dependencies are absent. Report active coverage, never the aggregate success count alone:

| Profile | Active cases | Coverage |
| --- | ---: | --- |
| none | 1 | Actual vanilla Classic Player.eat; optional-mod-safe discovery/startup |
| withTfc | 10 | Native manual/repeated/rotten consumption, water/nutrients, basket routes and exact raw-NBT removal, blacklist, no-match/scope cleanup, disabled bridge |
| withCore | 2 | Vanilla Player.eat plus actual transformed feeding-wrapper smoke |
| withBackpacks | 9 | Vanilla Player.eat, wrapper smoke, depletion cycling, filter/hunger fallback, backpack-only basket, disabled physical-slot ordering |
| withTfc+withBackpacks | 16 | Ten native TFC cases, wrapper smoke, native cycling/basket/filter fallback, positive floor and exactly-zero multiplier |

The native feeding regression uses hunger 8 with multiplier 0.20: it must remain eligible and restore one hunger, despite TFC's vanilla placeholder hunger 4 otherwise rounding to zero in Classic's check. Floor and zero cases also assert that Classic's calculation outside the scoped eligibility call is unchanged. The original eligibility result and user filters stay authoritative.

### Final local run receipts

Each run below completed all 23 registered required tests and passed the fresh-log verifier. The active assertion counts remain those in the preceding table.

| Profile | Verified run ID |
| --- | --- |
| none | `febde2009a3a4ee2b5aaf49f11492e4e` |
| withTfc | `5873dab346e841e289d98a6ac2a53214` |
| withCore | `b1ff000f285e45eca2fa114ec97d2211` |
| withBackpacks | `e2c2da7f412d4465847a395fd0f34bd2` |
| withTfc+withBackpacks | `587a07875f4441889b1520b7948f567d` |

### Test-harness accommodations and limits

Unmodified combined userdev startup failed before tests: TFC's initial recipe-output capability scan initialized a Backpacks wrapper before its SERVER config was loaded, triggering Forge's development-only assertion. Core's concurrent lazy enabled-item writes also raced its configuration watcher. No addon consumption frame participated in that startup failure; a separate addon-absent baseline was not executed.

`BackpackGameTestBootstrap`, present only in the GameTest source set, seeds missing Backpacks SERVER spec defaults in memory before the recipe scan. It also adds missing registered Backpacks enabled-item defaults in one Core COMMON update, preserving existing entries and explicit values. It logs both accommodations, leaves normal world-config loading enabled, and does not suppress assertions or parse errors. Combined-profile passes establish transformed behavior under this disclosed harness, not normal combined startup.

Policy-changing fixtures temporarily bind detached deep config snapshots and restore the original backing config on close. They do not autosave test blacklist/disable settings into watched world files. This fixes a caught repeat-run contamination problem; fresh run directories also prevent prior test state from affecting later evidence.

TFG Core and the complete pack are absent from these automated profiles. Full TFG extended nutrients, client synchronization, and the reobfuscated jar on a normal dedicated server still require the staging matrix. Classic's existing backpack-basket shortcut continues to bypass item-specific `finishUsingItem` container/effect behavior, as described in the README.

## GitHub release automation extension (2026-10-10)

The original workflow built, tested and retained Actions artifacts; it did not create GitHub Releases. The release-only extension adds a documented matching-version tag trigger, gated publishing, artifact provenance, collision checks and draft-first asset publication. See [RELEASING.md](RELEASING.md) for the exact trigger and recovery behavior. No CurseForge publication is configured.

Local validation of this extension:

- 81 Python tests passed: the original 29 completion-checker tests, 44 release-package/publisher tests, and 8 workflow safety-contract tests.
- The YAML parsed successfully and its release dependencies were checked.
- The archived real reobfuscated JAR above passes the stronger package verifier with its original SHA-256 unchanged.
- GitHub requests in release tests are mocked. Tests cover PR/branch rejection, version and tag mismatch, main ancestry, artifact/provenance mismatch, conflicting assets, interrupted uploads, and same-run cross-attempt draft/published-release retries.
- Java sources and Gradle build logic were unchanged by this extension, so the earlier real Forge build/runtime evidence remains the baseline. A new local Forge build was not claimed for these workflow-only changes.
- Live GitHub workflow execution and real release publication are separate evidence; neither is established merely by these local tests.
