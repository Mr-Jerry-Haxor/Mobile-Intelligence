# Contributing to Mobile Intelligence

Thanks for helping improve Mobile Intelligence.

## Before You Start

- Read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- Review [SECURITY.md](SECURITY.md) before reporting sensitive issues.
- For local setup, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Ways to Contribute

- Report bugs.
- Propose features.
- Improve docs.
- Submit code fixes and tests.

## Development Workflow

1. Fork the repository.
2. Create a branch from `master`.
3. Make focused changes.
4. Run build, lint, and tests.
5. Open a pull request.

Suggested branch names:

- `feat/short-description`
- `fix/short-description`
- `docs/short-description`
- `chore/short-description`

## Build, Lint, and Test

Run from the repository root.

Windows:

```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat lint
.\gradlew.bat test
```

If you add instrumentation tests:

```powershell
.\gradlew.bat connectedAndroidTest
```

## Coding Guidelines

- Follow Kotlin official style.
- Keep changes small and scoped.
- Avoid unrelated refactors in feature PRs.
- Add or update tests when behavior changes.
- Update docs for user-facing or security-sensitive changes.

## Pull Request Checklist

- I linked a related issue (or explained why none exists).
- I ran build, lint, and tests locally.
- I added/updated tests for behavior changes.
- I updated docs and changelog as needed.
- I did not commit secrets, keys, or personal data.
- I reviewed permission/privacy impact if AndroidManifest or data handling changed.

## Security Reports

Please do not open public issues for vulnerabilities. Follow [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions are licensed under the project license in [LICENSE](LICENSE).
