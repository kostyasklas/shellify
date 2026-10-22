# .github/workflows

CI/CD workflow definitions.

| Workflow | Trigger | Purpose |
|---|---|---|
| `pull_request.yml` | Pull request open / synchronize | Runs detekt, lint, unit tests, screenshot verification, and architecture checks on every PR |
| `main.yml` | Push to `main` | Signed release APK/AAB build (requires signing secrets), unit tests, screenshot verification, and emulator tests |
| `build-apk.yml` | Manual dispatch or push to `main` / `feat/http-sites` | Installable debug APK without signing secrets; separate lint, detekt, and unit-test job |
| `release.yml` | Push of a `v*` tag | Signed release APK build, changelog generation via git-cliff, GitHub Release creation |

All workflows use the composite actions in [`../actions/`](../actions/) to avoid duplication.

For a fork without release signing secrets, push `feat/http-sites` to the fork. **Actions → Build APK** runs automatically for that branch without changing `main`. Download the `shellify-debug-apk` artifact from the completed build job; **Re-run jobs** rebuilds the same commit. **Run workflow** becomes available once this workflow also exists on the default branch. The APK is debug-signed and installable; it cannot update an official release signed with a different key. The separate checks job uploads its reports even when checks fail.
