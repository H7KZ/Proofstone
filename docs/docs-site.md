# Docs site and compatibility matrix

The `docs/` folder is also published as a static site on GitHub Pages. A compatibility matrix page comes from
`proofstone-report.json` files of local runs. Decision: [ADR-0019](adr/0019-public-repo-workflows.md), card T-M10-16 (the maintainer approved
GitHub Pages and the `docs` workflow on 2026-11-19).

## Build it locally

```bash
bash scripts/docs-site.sh --out build/docs-site path/to/proofstone-report.json [more reports ...]
```

- Needs bash and `python3` (standard library only). No package is installed.
- Every `docs/**/*.md` becomes a page. Links between pages are rewritten to `.html`. Links to files outside `docs/` point to the
  repository on GitHub.
- `--out` must be under `build/` or `_site`, because the directory is wiped first.
- With no report argument the matrix page says there is no data. A report that is not schema `proofstone-report/1`, or has an unknown
  verdict, fails the build (nothing is skipped silently).
- Sample reports for trying the script are in `scripts/fixtures/docs-site/`. They are fixtures, not run results.

## The matrix

The report is written by the harness at the end of a run ([self-tests spec](specs/self-tests.md), `ReportWriter` in `proofstone-core`):
a `summary` per verdict and one `results` row per test and cell with `verdict`, `reason` and `skipReason`. The page shows one table per
report file: tests down, cells across, the verdict in each field. `SKIPPED` is shown as a skip with its reason, never as a pass.
A report is only as current as the local run that wrote it. There is no nightly: real-server runs are local
([AGENTS.md](../AGENTS.md) section 4).

## Publishing

The `docs` workflow runs on a push to `main` that changes `docs/**` or `scripts/docs-site.sh`. It builds the site with the script
and deploys it with the official Pages actions. It has no schedule and no manual trigger. To put a matrix on the site, commit report
files under `docs/compat-reports/`; the workflow passes `docs/compat-reports/*.json` to the script. Pages must be enabled by the
maintainer in the repository settings (source: GitHub Actions).
