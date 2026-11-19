#!/usr/bin/env bash
# Builds a static docs site from docs/*.md plus a compatibility page from proofstone-report.json files (T-M10-16, ADR-0019).
# Self-contained: bash plus python3 (standard library only), no packages. The same script runs locally and in the `docs` workflow.
# Report format: schema "proofstone-report/1", written by proofstone-core ReportWriter (docs/specs/test-api.md section 7).
#
# Usage: bash scripts/docs-site.sh [--out DIR] [--repo-url URL] [report.json ...]
#   --out DIR       output directory, wiped first (default: build/docs-site)
#   --repo-url URL  base for links to files outside docs/ (default: origin remote, without .git)
# No report arguments: the compatibility page says no reports were given. A report that fails to parse fails the build.
set -euo pipefail
cd "$(dirname "$0")/.."

out="build/docs-site"
repo_url=""
reports=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) out="${2:?--out needs a directory}"; shift 2 ;;
    --repo-url) repo_url="${2:?--repo-url needs a URL}"; shift 2 ;;
    -*) echo "docs-site: unknown option $1" >&2; exit 2 ;;
    *) reports+=("$1"); shift ;;
  esac
done
if [[ -z "$repo_url" ]]; then
  repo_url="$(git remote get-url origin 2>/dev/null | sed -e 's/\.git$//' -e 's#^git@github.com:#https://github.com/#' || true)"
fi
# The output directory is wiped, so only a build/ or _site path is accepted.
[[ "$out" == build/* || "$out" == _site || "$out" == _site/* ]] || { echo "docs-site: refusing --out '$out' (use build/... or _site)" >&2; exit 2; }
PY=python3
command -v python3 >/dev/null 2>&1 || PY=python
command -v "$PY" >/dev/null 2>&1 || { echo "docs-site: python3 not found" >&2; exit 2; }

"$PY" -I - "$out" "$repo_url" "${reports[@]+"${reports[@]}"}" <<'PYEOF'
import html, json, os, re, shutil, sys
from pathlib import Path

out, repo_url, report_args = Path(sys.argv[1]), sys.argv[2].rstrip("/"), sys.argv[3:]
root = Path.cwd()
docs = root / "docs"
VERDICTS = ["PASS", "FAIL", "INVALID", "VACUOUS", "FLAKY", "DISAGREE", "SKIPPED"]
SCHEMA = "proofstone-report/1"
esc = html.escape

# ---- markdown subset: headings, paragraphs, fences, tables, lists, quotes, rules, inline code/bold/italic/links ----
def slug(text):
    return re.sub(r"[^a-z0-9 -]", "", re.sub(r"<[^>]+>", "", text).lower()).strip().replace(" ", "-")

def rewrite(url, page_dir):
    m = re.match(r"^([a-z][a-z0-9+.-]*):", url, re.I)
    if m:
        # Only these schemes may reach the generated HTML. javascript:, data: and the like become a dead link.
        return url if m.group(1).lower() in ("http", "https", "mailto") else "#"
    if url.startswith("#") or url.startswith("//"):
        return url
    path, _, frag = url.partition("#")
    if not path:
        return url
    frag = f"#{frag}" if frag else ""
    base = (docs / page_dir).resolve()
    target = (base / path).resolve()
    try:
        rel = target.relative_to(docs.resolve())
    except ValueError:
        try:
            rel_repo = target.relative_to(root.resolve()).as_posix()
        except ValueError:
            return url
        return f"{repo_url}/{'tree' if target.is_dir() else 'blob'}/main/{rel_repo}{frag}"
    if target.is_dir():
        rel = rel / "README.md"
    if rel.suffix == ".md":
        dest = docs.resolve() / rel.with_suffix(".html")
        return os.path.relpath(dest, base).replace(os.sep, "/") + frag
    return f"{repo_url}/blob/main/docs/{rel.as_posix()}{frag}"

def inline(text, page_dir):
    codes = []
    def stash(m):
        codes.append(f"<code>{esc(m.group(1))}</code>")
        return f"\x00{len(codes) - 1}\x00"
    text = re.sub(r"`([^`]+)`", stash, text)
    text = esc(text, quote=False)
    text = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)",
                  lambda m: f'<a href="{esc(rewrite(html.unescape(m.group(2)), page_dir))}">{m.group(1)}</a>', text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", text)
    return re.sub(r"\x00(\d+)\x00", lambda m: codes[int(m.group(1))], text)

def split_row(line):
    return [c.strip().replace("\\|", "|") for c in re.split(r"(?<!\\)\|", line.strip().strip("|"))]

def convert(md, page_dir):
    lines, body, i, title = md.splitlines(), [], 0, None
    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            fence, i, buf = line[:3], i + 1, []
            while i < len(lines) and not lines[i].startswith(fence):
                buf.append(lines[i])
                i += 1
            i += 1
            body.append("<pre><code>" + esc("\n".join(buf)) + "</code></pre>")
        elif (m := re.match(r"^(#{1,6})\s+(.*)$", line)):
            n, text = len(m.group(1)), m.group(2).strip()
            if n == 1 and title is None:
                title = re.sub(r"[`*]", "", text)
            body.append(f'<h{n} id="{slug(text)}">{inline(text, page_dir)}</h{n}>')
            i += 1
        elif re.match(r"^\s*(---+|\*\*\*+)\s*$", line):
            body.append("<hr>")
            i += 1
        elif line.lstrip().startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|?[\s:|-]+\|[\s:|-]*$", lines[i + 1]):
            head = split_row(line)
            i += 2
            rows = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            t = "<table><thead><tr>" + "".join(f"<th>{inline(c, page_dir)}</th>" for c in head) + "</tr></thead><tbody>"
            for r in rows:
                t += "<tr>" + "".join(f"<td>{inline(c, page_dir)}</td>" for c in r) + "</tr>"
            body.append(t + "</tbody></table>")
        elif line.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(lines[i][1:].lstrip())
                i += 1
            body.append("<blockquote>" + convert("\n".join(buf), page_dir)[1] + "</blockquote>")
        elif re.match(r"^\s*([-*]|\d+\.)\s+", line):
            ordered = bool(re.match(r"^\s*\d+\.", line))
            items = []  # (text, [sub items])
            while i < len(lines) and lines[i].strip() != "":
                m = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", lines[i])
                if m and (len(m.group(1)) < 2 or not items):
                    items.append([m.group(3), []])
                elif m and items:
                    items[-1][1].append(m.group(3))
                elif items:
                    if items[-1][1]:
                        items[-1][1][-1] += " " + lines[i].strip()
                    else:
                        items[-1][0] += " " + lines[i].strip()
                else:
                    break
                i += 1
            li = ""
            for text, subs in items:
                li += "<li>" + inline(text, page_dir)
                if subs:
                    li += "<ul>" + "".join(f"<li>{inline(s, page_dir)}</li>" for s in subs) + "</ul>"
                li += "</li>"
            tag = "ol" if ordered else "ul"
            body.append(f"<{tag}>{li}</{tag}>")
        elif line.strip() == "":
            i += 1
        else:
            buf = []
            while i < len(lines) and lines[i].strip() != "" and not re.match(r"^(#{1,6}\s|```|>|\s*([-*]|\d+\.)\s)", lines[i]):
                buf.append(lines[i].strip())
                i += 1
            body.append("<p>" + inline(" ".join(buf), page_dir) + "</p>")
    return title, "\n".join(body)

CSS = """body{font:16px/1.55 system-ui,sans-serif;margin:0;color:#1b1f24;background:#fff}
nav{background:#14213d;padding:.6rem 1rem}nav a{color:#fff;margin-right:1rem;text-decoration:none}
main{max-width:62rem;margin:1rem auto;padding:0 1rem}table{border-collapse:collapse;display:block;overflow-x:auto}
td,th{border:1px solid #c8ccd0;padding:.25rem .5rem;text-align:left;vertical-align:top}th{background:#f1f3f5}
pre{background:#f6f8fa;padding:.75rem;overflow-x:auto}code{background:#f6f8fa;padding:0 .2rem}pre code{padding:0}
blockquote{border-left:4px solid #c8ccd0;margin-left:0;padding-left:1rem;color:#444}
.PASS{background:#d4edda}.SKIPPED{background:#e9ecef}.FAIL,.INVALID,.DISAGREE{background:#f8d7da}.VACUOUS,.FLAKY{background:#fff3cd}"""

def page(title, depth, body):
    up = "../" * depth
    return ('<!doctype html><html lang="en"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            f"<title>{esc(title)} - Proofstone</title><style>{CSS}</style></head><body>"
            f'<nav><a href="{up}README.html">Proofstone docs</a><a href="{up}compatibility-matrix.html">Compatibility matrix</a>'
            f'<a href="{esc(repo_url)}">Repository</a></nav><main>{body}</main></body></html>')

# ---- compatibility matrix from report files (ReportWriter, schema proofstone-report/1) ----
def load_report(path):
    p = Path(path)
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        sys.exit(f"docs-site: cannot read report {path}: {e}")
    if not isinstance(data, dict) or data.get("schema") != SCHEMA:
        sys.exit(f"docs-site: {path}: schema is not {SCHEMA!r}")
    results = data.get("results")
    if not isinstance(results, list) or not results:
        sys.exit(f"docs-site: {path}: no results")
    summary = data.get("summary", {})
    if not isinstance(summary, dict) or not all(isinstance(v, int) and not isinstance(v, bool) and v >= 0 for v in summary.values()):
        sys.exit(f"docs-site: {path}: summary must map names to non-negative integers")
    for r in results:
        if not isinstance(r, dict) or not all(isinstance(r.get(k), str) for k in ("cell", "test", "verdict")) or r["verdict"] not in VERDICTS:
            sys.exit(f"docs-site: {path}: unknown or missing field in result {r!r}")
    return p.name, data

def matrix_page(paths):
    if not paths:
        return ("<h1>Compatibility matrix</h1><p>No <code>proofstone-report.json</code> files were given to the build, so there is no data. "
                "Real-server runs are local. Run <code>bash scripts/docs-site.sh report.json ...</code> with the reports to publish.</p>")
    b = ["<h1>Compatibility matrix</h1>",
         "<p>Generated from <code>proofstone-report.json</code> files of local runs (schema <code>proofstone-report/1</code>). "
         "Each field is the verdict of one test on one cell (server and version). "
         "<code>SKIPPED</code> is a skip, not a pass. A report is only as current as the run that wrote it.</p>"]
    for path in paths:
        name, data = load_report(path)
        results = data["results"]
        cells = sorted({r["cell"] for r in results})
        tests = sorted({r["test"] for r in results})
        by = {(r["cell"], r["test"]): r for r in results}
        summary = data.get("summary") or {}
        b.append(f"<h2>{esc(name)}</h2>")
        b.append("<p>Overall: <strong>" + ("green" if data.get("green") is True else "not green") + "</strong>. " +
                 ", ".join(f"{v} {esc(str(summary.get(v, 0)))}" for v in VERDICTS) + ".</p>")
        t = "<table><thead><tr><th>Test</th>" + "".join(f"<th>{esc(c)}</th>" for c in cells) + "</tr></thead><tbody>"
        for test in tests:
            t += f"<tr><td>{esc(test)}</td>"
            for c in cells:
                r = by.get((c, test))
                if r is None:
                    t += "<td>-</td>"
                    continue
                note = r.get("reason") or r.get("skipReason") or ""
                skip = f" ({esc(r['skipReason'])})" if r.get("skipReason") else ""
                t += f'<td class="{r["verdict"]}" title="{esc(str(note))}">{r["verdict"]}{skip}</td>'
            t += "</tr>"
        b.append(t + "</tbody></table>")
    return "\n".join(b)

# ---- build ----
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
count = 0
for md in sorted(docs.rglob("*.md")):
    rel = md.relative_to(docs)
    page_dir = "" if rel.parent.as_posix() == "." else rel.parent.as_posix()
    title, body = convert(md.read_text(encoding="utf-8"), page_dir)
    dest = out / rel.with_suffix(".html")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(page(title or rel.stem, len(rel.parts) - 1, body), encoding="utf-8")
    count += 1
(out / "compatibility-matrix.html").write_text(page("Compatibility matrix", 0, matrix_page(report_args)), encoding="utf-8")
(out / "index.html").write_text('<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=README.html"><a href="README.html">Documentation</a>', encoding="utf-8")
print(f"docs-site: {count} pages and the matrix ({len(report_args)} report(s)) written to {out}")
PYEOF
