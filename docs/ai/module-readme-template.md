# Module README template

Copy into `<module>/README.md`. Keep the four headings in this order. Link to [AGENTS.md](../../AGENTS.md)
rather than copying it.

```markdown
# <module-name>

## Purpose

One or two sentences: what the module is for and which layer it belongs to ([architecture.md §4](../architecture.md)).

## Boundaries

What the module must not do: forbidden dependencies and the AGENTS.md §3 rules that apply here.

## Public surface

The types, endpoints or tasks other modules or users rely on. Link the spec section that defines them.
Write "None yet" if the module is empty.

## How to test

Exact commands, for example `./gradlew :<module>:test`.
```
