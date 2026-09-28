# PROJECT OPERATING INSTRUCTIONS: AUTOMATED INVOICE RECONCILIATION

> **IMPORTANT:** These instructions are mandatory for EVERY prompt and interaction in this project.
> Project Source of Truth: [PPROJECT_SPEC.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/PPROJECT_SPEC.md)
> Skills Directory: [.agent/skills/](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/)

---

## 1. Prime Directive: Prompt Skill Routing Protocol

Whenever processing ANY prompt or task in this repository:
1. **Analyze Intent & Scope:** Identify whether the task touches Backend, Database, AI Extraction, Financial Audit Rules, Frontend, Testing, Debugging, or Code Review.
2. **Consult [PPROJECT_SPEC.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/PPROJECT_SPEC.md):** Ensure full consistency with project scope, entities, and architecture.
3. **Load & Apply Relevant Skills:** Always read and strictly enforce the guidelines from the corresponding skill files in `.agent/skills/` before generating plans or code.

---

## 2. Skill Routing Matrix

Match the user's prompt topic to the required skills below:

| Prompt Topic / Trigger Area | Primary Skill Files to Load & Apply |
| :--- | :--- |
| **Financial Audit & Reconciliation Logic**<br>Tolerances, line-item matching, PO verification, discrepancies, dispute generation | - [.agent/skills/financial-audit.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/financial-audit.md) |
| **Spring Boot Backend & AI Orchestration**<br>Spring Boot 3.5, Java 21, Spring AI, ChatClient, BeanOutputConverter, REST endpoints | - [.agent/skills/spring-backend.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/spring-backend.md)<br>- [.agent/skills/api-design-principles/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/api-design-principles/SKILL.md)<br>- [.agent/skills/api-patterns/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/api-patterns/SKILL.md) |
| **Database & Persistence**<br>MySQL 8.4, JPA/Hibernate, entities, indexes, schemas, migrations | - [.agent/skills/database-design/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/database-design/SKILL.md)<br>- [.agent/skills/database-optimizer/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/database-optimizer/SKILL.md)<br>- [.agent/skills/database-security/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/database-security/SKILL.md) |
| **Frontend & UI/UX**<br>Next.js 15, React, Tailwind CSS, Lucide icons, split-screen review dashboard | - [.agent/skills/nextjs-ui.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/nextjs-ui.md)<br>- [.agent/skills/nextjs-app-router-patterns/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/nextjs-app-router-patterns/SKILL.md)<br>- [.agent/skills/react-best-practices/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/react-best-practices/SKILL.md)<br>- [.agent/skills/tailwind-patterns/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/tailwind-patterns/SKILL.md)<br>- [.agent/skills/ui-ux-pro-max/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/ui-ux-pro-max/SKILL.md) |
| **Testing & Quality Assurance**<br>Unit tests, integration tests, mock data, assertions | - [.agent/skills/test-driven-development/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/test-driven-development/SKILL.md) |
| **Debugging & Error Investigation**<br>Runtime failures, prompt extraction errors, SQL bugs | - [.agent/skills/systematic-debugging/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/systematic-debugging/SKILL.md) |
| **Code Review & Quality Audits**<br>PR reviews, refactoring, linting | - [.agent/skills/code-review-and-quality/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/code-review-and-quality/SKILL.md)<br>- [.agent/skills/code-reviewer/SKILL.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.agent/skills/code-reviewer/SKILL.md) |

---

## 3. Non-Negotiable Architectural Rules (from PPROJECT_SPEC.md)

1. **Separation of Extraction vs. Math:**
   - **LLM Responsibility:** Multimodal data extraction from supplier invoices (PDF/images) and semantic fuzzy matching (Arabic/English line items mapped to canonical `sku_code`).
   - **Java Responsibility:** **Never let the LLM do math.** All mathematical checks, variance calculations, and tolerance checks (> 0.01 EGP) must be executed in pure Java business logic.
2. **Deterministic Reconciliation Rules:**
   - **Price Variance:** If `Math.abs(invoicedPrice - agreedPrice) > 0.01` -> Flag `PRICE_MISMATCH`.
   - **Quantity Variance:** If `invoicedQty != expectedQty` -> Flag `QUANTITY_MISMATCH`.
   - **Extra Fees:** Any unapproved freight/delivery/surcharge -> Flag `EXTRA_FEE`.
   - **Unrecognized Items:** Invoiced item cannot match PO -> Flag `UNRECOGNIZED_ITEM`.
3. **Structured Output:** Spring AI extraction must deserialize strictly into immutable Java Records.
4. **Database Alignment (MySQL 8.4):**
   - Tables: `purchase_orders`, `purchase_order_items`, `invoices`, `reconciliation_audits`.
5. **Split-Screen UX:**
   - Document preview on the left; structured reconciliation audit table with color-coded status badges on the right.

---

## 4. Per-Prompt Workflow Checklist

Before responding to any prompt, execute this mental checklist:
- [ ] What component is affected? (Backend / DB / Frontend / Reconciliation logic)
- [ ] Have I consulted the respective skill file(s) in `.agent/skills/`?
- [ ] Does the solution strictly uphold the separation of extraction vs. deterministic math?
- [ ] Are entities, endpoints, and DTOs synchronized with [PPROJECT_SPEC.md](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/PPROJECT_SPEC.md)?
- [ ] Are tests and validation steps included where appropriate?
