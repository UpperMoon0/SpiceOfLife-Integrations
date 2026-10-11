# GitHub JAR releases

This project builds and publishes **GitHub Release assets only**. It has no CurseForge upload job, project ID, or token requirement.

## What triggers a release

Publication is deliberately tag-gated. A push of `v<mod_version>` to `UpperMoon0/SpiceOfLife-Integrations` starts the same build and five-profile GameTest pipeline used by CI. The tag must match the single `mod_version` in `gradle.properties`, and its tested commit must be reachable from `origin/main`.

Pull requests, ordinary branch pushes, and manual workflow dispatches run checks but never publish. A version change alone does not create a release. No tag or release is created by merging a PR.

After the intended commit is reviewed, merged, and the remaining staging checks are satisfied, a maintainer can create and push a matching release tag. For example, for the committed version `0.1.0`:

```sh
git switch main
git pull --ff-only
git tag -a v0.1.0 -m 'Spice of Life Integrations 0.1.0'
git push origin refs/tags/v0.1.0
```

These are release instructions, not steps performed during implementation. Tags should be created by a maintainer's normal GitHub-authorized Git workflow; tags generated using an Actions `GITHUB_TOKEN` may not trigger another workflow.

## Required gates and exact artifact

The release job waits for the build, Python safety tests, JUnit tests, and **all five** headless GameTest profiles to succeed. The GameTest completion verifier remains mandatory; Gradle exit zero alone is insufficient.

The build prepares only the installable reobfuscated Jar-in-Jar output, `solintegrations-<version>.jar`. Packaging checks reject a wrong version, missing resources/refmap, missing MixinExtras, test fixtures, upstream implementations, or an inappropriate slim/development artifact.

The downloadable package contains:

- The verified addon JAR
- `SHA256SUMS`
- `release-provenance.json`, binding the JAR's size/hash to its repository, tested commit, workflow run and run attempt

The Actions artifact name contains the commit SHA, run ID and attempt. The publisher downloads that exact artifact from the same run, validates the package again, and verifies the live tag still resolves to the tested commit. It never rebuilds a different JAR in the publishing job.

## Permissions and failure handling

Build and PR jobs have read-only repository permissions. Only the tag-gated release job gets `contents: write` and `actions: read`, using the workflow's short-lived `GITHUB_TOKEN`. Checkout does not persist credentials. No personal access token or external publishing secret is required.

Publishing starts with a draft. Required assets are uploaded and verified before it becomes public. Existing unrelated releases, moved tags, conflicting bytes, unexpected assets, or inconsistent provenance fail closed; the workflow does not overwrite or delete release assets.

For a failed release attempt, use **Re-run all jobs** so the build produces a package for the new run attempt. Re-running only the publisher can leave it with a stale package and is intentionally rejected. A same-run earlier-attempt release or draft may retain its original provenance only when that original provenance is already present and still binds the identical version, tested commit and JAR bytes. A draft interrupted before its provenance was uploaded needs manual inspection. Different run IDs or conflicting release contents are not silently adopted.

A failure can leave a draft for inspection. Do not delete a published release or move an existing version tag just to make a retry succeed; fix the problem and choose a new version when appropriate.

## Validation boundary

The release automation is separately tested with local fixtures and mocked GitHub operations. That does not prove a live GitHub publication occurred. Refer to [VALIDATION.md](VALIDATION.md) for actual execution evidence and the remaining packaged-server, client and full TFG-pack staging checks. The combined development-server profile still uses its documented test-only configuration bootstrap.

## Existing mod patterns used for comparison

Read-only comparison used these exact repository revisions:

- [Economy build-and-release workflow](https://github.com/UpperMoon0/Economy/blob/34fdc10c63140f64975f82a559a749cc728da0d8/.github/workflows/build-and-release.yml): release after validation/build, with version/tag/manual gates and uploaded JAR assets.
- [Simply Speakers release workflow](https://github.com/UpperMoon0/Simply-Speakers/blob/ea85b4e2f51a43e51911e551512c006281efb75b/.github/workflows/release.yml): GitHub Releases receive built JAR assets after test/build jobs.

This addon uses the documented tag trigger and narrower publishing permissions. Their CurseForge steps are not copied.

API behavior follows GitHub's [release API documentation](https://docs.github.com/en/rest/releases/releases), [workflow token permissions](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token), and [same-run artifact download CLI](https://cli.github.com/manual/gh_run_download).
