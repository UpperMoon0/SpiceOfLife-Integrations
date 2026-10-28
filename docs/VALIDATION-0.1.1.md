# 0.1.1 compatibility repair validation

Local checks on 2026-10-28 use Java 17, Minecraft 1.20.1 and Forge 47.4.13.

The optional integration gates no longer reject new version numbers. Raw-bytecode contracts check required public APIs, staticness, TFC native delegation and the exact restoration/traversal injection targets. These checks establish structure, not future gameplay semantics.

Current runtime inputs are TFC 3.2.28, Sophisticated Core 1.5.16.2433 and Backpacks 3.26.15.2228. The legacy combined input is TFC 3.2.25/Core 1.3.84.2308/Backpacks 3.24.67.2109. All profiles use Classic 2.13.

All six local headless profiles completed 23 registered tests with fresh completion receipts. Active coverage remains 1/10/2/9/16 cases for absent/TFC/Core/Backpacks/combined profiles; dependency-absent no-ops are not feature evidence. The legacy combined profile has 16 active cases. The disclosed development-only Backpacks configuration bootstrap remains in use.

| Profile | Fresh run ID |
| --- | --- |
| none | `9e134407809a431f852b27382d6307ed` |
| withBackpacks | `a5edc59b49d64e10be0fc8b69182dbb2` |
| withCore | `96709e3ca2ef4a81b30c183db757b8cf` |
| withTfc | `00f5c3277aae445b87c7fdf77c1a8ef7` |
| withTfc+withBackpacks | `12771b9803cc435ea3214fa8db9ba8e2` |
| withTfc+withBackpacks | `8c2582590a054e3fad2c5fba71cebfc6` |

Unit checks: 60 Java tests and 82 Python safety tests. Raw-jar contract probes also inspect the actual production TFG Core 0.9.31 bundled TFC classes and current Core jar, plus the original supported TFC/Core jars.

## Packaged full-pack harness

`./gradlew gameTestFixtureJar` builds a separate reobfuscated fixture mod under `build/gametest-fixtures/`. It is never part of the addon release package. Install it only in a disposable dedicated-server copy, with `-Dsolintegrations.packagedTests=true`. It registers/runs the same 23 tests on a normal production-mapping server, records `packaged-test-result.json`, and shuts down that disposable server. It refuses to start without the explicit property.

The full-pack stage copies the live pack files but creates a new disposable world, uses separate ports and a Linux network namespace, and excludes player identity registry data. It does not copy or modify the production world. The first harness attempt failed before mod loading because fixture/main Java packages overlapped; fixtures were moved to separate packages.

Full-pack gameplay results and the final published artifact hash are recorded after execution. Interactive client UI, real-player reconnect behavior and extended TFG nutrient measurements remain separate validation boundaries.

## Packaged full TFG result — 2026-10-28

PASS: the isolated copy of the actual TFG 0.13.17-nstut.1 server stack with
TFG Core 0.9.31 (bundled TFC 3.2.28), Core 1.5.16.2433 and Backpacks
3.26.15.2228 completed all 23 registered tests, with 16 active combined-stack
cases and zero failed required tests. This uses the separate reobfuscated
fixture JAR and the unchanged installable addon JAR on a normal production
Forge server. The harness returned:

```json
{"passed":true,"total":23,"done":23,"failedRequired":0}
```

Both optional API checks activated, and the transformed classes exported to
`.mixin.out` contain the addon handlers. The installable addon is 680597 bytes,
SHA-256 `3b2925cb0efbe22ce2dc1da88d359e1264150644112cfdfb7eb19b8bd0c62761`;
the GitHub Actions build of b92452e produced identical bytes.

Reproduction details: include the server's separate `modules/operations.jar`
code module when copying this pack. Do not copy the production world or player
identity registry. Run in a network namespace, with its loopback interface up.
Production/AE2 structure lookup also needs a staging file
`gameteststructures/empty.snbt`:

```snbt
{DataVersion:3465,size:[3,3,3],palette:[{Name:"minecraft:air"}],blocks:[],entities:[]}
```

Without that staging file the harness stopped before executing any test;
that failed attempt is not gameplay evidence. The final passing log is
`packaged-stage-4.log` in the execution workspace and the result is
`packaged-stage/packaged-test-result.json`.

A review of the first GitHub workflow run caught include-entry merging that
replaced the current combined profile with the legacy entry. The corrected
matrix uses an explicit dependency dimension so both run separately. Release
requires the entire corrected matrix.

No interactive client/UI, real-player reconnect/restart persistence or
measured TFG extended-nutrient case is claimed by these checks. Production
deployment remains a separate receipt.
