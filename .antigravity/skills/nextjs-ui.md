---
name: nextjs-ui
description: UI/UX guidelines for B2B financial dashboards and split-screen layouts.
triggers: ["frontend/**", "*.tsx", "*.ts"]
---

# Next.js 15 & B2B Dashboard Design Skill

1. Layout & Scan Speed:
    - Implement split-screen comparison: Original document preview on the left, structured reconciliation table on the right.
    - High information density: compact table rows, visible column borders, clear status badges.

2. Visual Hierarchy & Status Colors:
    - Matched / Clean: Emerald/Green (`bg-emerald-50 text-emerald-700 border-emerald-200`).
    - Price/Qty Mismatch: Crimson/Red (`bg-rose-50 text-rose-700 border-rose-200`).
    - Unrecognized / Missing: Amber/Orange (`bg-amber-50 text-amber-700 border-amber-200`).

3. Component Hygiene:
    - Use Tailwind CSS and Lucide React icons (`CheckCircle2`, `AlertTriangle`, `FileText`, `Copy`).
    - Client components must declare `"use client"` at the top.
    - Synchronize TypeScript types directly with backend Java Records.