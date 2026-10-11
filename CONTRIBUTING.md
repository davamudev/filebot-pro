# Contributing to FileBot Pro

Thanks for helping! Bug reports, ideas and pull requests are welcome.

## Branches

- `develop` is the default branch: open every pull request against `develop`.
- `master` holds the released code. It only receives merges from `develop`, and every release is a `vX.Y.Z` tag on
  `master` (see [Releases](https://filebotpro.davamu.dev/development/building/#releases)).

## Commit messages: Conventional Commits

Every commit, and the title of every pull request, must follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<optional scope>)<optional !>: <description>

[optional body]

[optional footer(s)]
```

| Type | Use it for |
| --- | --- |
| `feat` | A new feature for users |
| `fix` | A bug fix |
| `docs` | Documentation only (`docs/`, README, …) |
| `style` | Formatting that does not change behaviour |
| `refactor` | Code changes that neither fix a bug nor add a feature |
| `perf` | Performance improvements |
| `test` | Tests |
| `build` | Build system, packaging or dependencies (`build.gradle.kts`, `packaging/`, `Dockerfile`) |
| `ci` | GitHub workflows |
| `chore` | Anything else that does not touch the app (tooling, housekeeping) |
| `revert` | Reverts an earlier commit |

Rules:

- The description is in English, imperative and lower case, without a final period: `fix: keep episode order after a theme switch`.
- The scope is optional and names the area: `rename`, `episodes`, `subtitles`, `sfv`, `filter`, `list`, `cli`, `format`,
  `web`, `ui`, `docs`, `docker`, `synology`, … (e.g. `feat(rename): add a conflict preview`).
- Breaking changes add `!` after the type or scope and a `BREAKING CHANGE:` footer explaining what changes for users.
- One logical change per commit.

Examples:

```
feat(subtitles): download subtitles for every selected file at once
fix(cli): respect --action test when the output folder does not exist
docs: add Plex folder examples to the formats page
build(deps): bump groovy to 4.0.28
ci: build the Linux packages in the release workflow
feat(format)!: rename the {vf} binding to {resolution}

BREAKING CHANGE: format expressions that use {vf} must use {resolution}.
```

The `Commits` workflow checks the pull request title and every commit of the pull request and fails if one of them does
not follow the pattern.

## Before opening a pull request

- `./gradlew compileJava` passes (there is no test suite).
- User-facing changes are documented in `CHANGELOG.md` and, where relevant, in the docs in both languages
  (`docs/src/content/docs/` and `docs/src/content/docs/es/`).
- Follow the conventions in `.editorconfig` (tabs in Java, 4 spaces elsewhere) and never commit `api-keys.properties`.
- New dependencies must have a GPL-3.0-compatible license.

Building, packaging and the documentation site are described in
[Building from source](https://filebotpro.davamu.dev/development/building/).
