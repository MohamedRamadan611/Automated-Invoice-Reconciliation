# Project Rules & Skill Routing Instructions

> Source of Truth: [PPROJECT_SPEC.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/PPROJECT_SPEC.md)
> Skills Location: [.agent/skills/](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/)

## Prime Directive
For every user prompt, you MUST:
1. Cross-reference the requirements in [PPROJECT_SPEC.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/PPROJECT_SPEC.md).
2. Automatically route and consult the relevant domain skills from `.agent/skills/`:
   - Backend & Spring AI -> `spring-backend.md`, `api-design-principles`, `api-patterns`
   - Reconciliation & Auditing -> `financial-audit.md`
   - MySQL Database & JPA -> `database-design`, `database-optimizer`
   - Frontend & Dashboard -> `nextjs-ui.md`, `nextjs-app-router-patterns`, `react-best-practices`, `tailwind-patterns`
   - Testing -> `test-driven-development`
   - Debugging -> `systematic-debugging`
   - Quality & Code Review -> `code-review-and-quality`

## Architectural Invariants
- The LLM must NEVER perform math or calculations. All reconciliation tolerances (> 0.01 EGP) must be executed in pure Java.
- Spring AI output must map strictly into immutable Java Records.
- Adhere strictly to the MySQL 8.4 schema defined in `PPROJECT_SPEC.md`.
