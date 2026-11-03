# Agent workflow: card to merged PR

1. **Pick:** the lowest-numbered open card in the current milestone whose `depends:` are all merged and
   whose `tier` matches your model.
2. **Load context:**
   - [AGENTS.md](../../AGENTS.md);
   - the card;
   - the ADRs and spec sections the card links;
   - the README of each module in `modules:`.
3. **Branch:** `card/<ID>-<slug>`.
4. **Implement the smallest change that meets the acceptance criteria.** Write the test or golden file
   **before** the implementation where the card says *test-first*.
5. **Run the gates:** the card's `Verify` commands, plus `./gradlew check`.
6. **Open a PR** using the PR template. Include:
   - the card ID;
   - the acceptance checklist, ticked with evidence;
   - the gate output;
   - any gotchas learned, which go into `docs/platforms/*.md` in the same PR.
7. **Review** according to [model-tiering.md](model-tiering.md).
8. **Merge.** Mark the card done: tick its checkbox in `docs/plan/tasks/M*.md`, or close the issue.

## Card format (required; `docsCheck` validates it)

```markdown
### T-M1-04 · Short imperative title
- tier: haiku | sonnet | opus
- depends: T-M1-01, T-M1-02
- modules: platforms/bukkit
- adrs: 0002
- specs: specs/agent-protocol.md §2
- test-first: yes | no

**Goal.** One or two sentences.

**Do.**
- Bullet steps, concrete.

**Don't.**
- Explicit exclusions (scope guard).

**Acceptance.**
- [ ] Verifiable statement 1
- [ ] Verifiable statement 2

**Verify.**
`./gradlew :platforms:bukkit:test`
```
