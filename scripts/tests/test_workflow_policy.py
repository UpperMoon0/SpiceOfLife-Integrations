"""Static safety contract for the checked-in publishing workflow (stdlib only)."""
from pathlib import Path
import re
import unittest

WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/build.yml'


class WorkflowPolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text()
        cls.before_release, cls.release = cls.text.split('\n  release:\n', 1)

    def test_release_requires_successful_build_and_entire_gametest_matrix(self):
        self.assertIn('    needs: [build, gametest]\n', self.release)
        self.assertNotIn('always()', self.release)
        self.assertNotIn('continue-on-error', self.text)
        for profile in ('none', 'withTfc', 'withCore', 'withBackpacks', 'withTfc+withBackpacks'):
            self.assertIn('          - ' + profile + '\n', self.before_release)

    def test_legacy_combined_profile_is_also_required(self):
        self.assertIn('        dependencies: [current]', self.before_release)
        self.assertIn('            dependencies: legacy', self.before_release)
        self.assertIn('-PtfcTestVersion=3.2.25 -PcoreTestFile=8705513 -PbackpacksTestFile=8687830', self.before_release)
        self.assertIn("matrix.dependencies == 'legacy'", self.before_release)

    def test_only_requested_repository_tag_push_can_publish(self):
        self.assertIn("if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') && github.repository == 'UpperMoon0/SpiceOfLife-Integrations'", self.release)
        self.assertNotIn('pull_request_target', self.text)
        self.assertNotIn('workflow_run:', self.text)
        self.assertIn('    tags: ["v*"]', self.text)

    def test_write_permission_is_confined_to_release(self):
        self.assertIn('permissions:\n  contents: read\n', self.before_release)
        self.assertNotIn('contents: write', self.before_release)
        self.assertIn('    permissions:\n      contents: write\n      actions: read\n', self.release)
        self.assertNotIn('write-all', self.text)
        self.assertNotIn('id-token:', self.text)

    def test_download_is_bound_to_same_run_attempt_and_commit(self):
        name = 'solintegrations-${{ github.sha }}-${{ github.run_id }}-${{ github.run_attempt }}'
        self.assertIn('      artifact_name: ' + name, self.before_release)
        self.assertIn('          name: ' + name, self.before_release)
        self.assertIn('ARTIFACT_NAME: ${{ needs.build.outputs.artifact_name }}', self.release)
        self.assertIn('gh run download "$GITHUB_RUN_ID" --repo "$GITHUB_REPOSITORY"', self.release)
        self.assertIn('--name "$ARTIFACT_NAME" --dir build/ci-package', self.release)
        self.assertNotIn('--pattern', self.release)

    def test_provenance_is_produced_and_checked_with_build_sha(self):
        self.assertIn('python3 scripts/release_package.py prepare --tested-sha "$GITHUB_SHA"', self.before_release)
        self.assertIn('tested_sha: ${{ steps.package.outputs.tested_sha }}', self.before_release)
        self.assertIn('TESTED_SHA: ${{ needs.build.outputs.tested_sha }}', self.release)
        self.assertIn('python3 scripts/release_publish.py --tested-sha "$TESTED_SHA"', self.release)
        self.assertIn('fetch-depth: 0', self.release)

    def test_actions_are_pinned_and_credentials_not_persisted(self):
        uses = re.findall(r'^\s+uses: (.+)$', self.text, re.MULTILINE)
        self.assertTrue(uses)
        for action in uses:
            self.assertRegex(action, r'^[\w-]+/[\w-]+@[0-9a-f]{40}(?:\s|$)')
        self.assertNotIn('persist-credentials: true', self.text)
        self.assertEqual(self.text.count('uses: actions/checkout@'), self.text.count('persist-credentials: false'))

    def test_only_ephemeral_github_token_no_curseforge(self):
        self.assertNotIn('secrets.', self.text)
        self.assertNotIn('curseforge', self.text.lower())
        self.assertIn('GITHUB_TOKEN: ${{ github.token }}', self.release)
        self.assertIn('GH_TOKEN: ${{ github.token }}', self.release)

    def test_tag_validation_and_release_are_not_cancelled_as_prs(self):
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", self.text)
        self.assertNotIn('cancel-in-progress: true', self.text)


if __name__ == '__main__':
    unittest.main()
