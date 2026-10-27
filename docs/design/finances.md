# Personal finances (milestone 6.3)

Approved on 2026-10-27.

## Money

- Mexican pesos only. Amounts are stored as **integer cents** (`$85.50` = `8550`), never floating point, and are always positive: the kind (expense/income) gives the direction.
- Typed amounts accept `85`, `85.5`, `85,50` and `$1,234.50` (`parseMoney`); shown as `$1,234.50` (`formatMoney`). Limit per amount: `$99,999,999.99`.

## Model (schema v5)

| Table | Fields |
|---|---|
| `finance_categories` | name, kind (expense/income), optional monthly budget (expense only, same every month), order, archived |
| `finance_movements` | kind, amount, category, date, optional note |
| `finance_debts` | person, direction (I owe / owed to me), amount, optional due date, note, settledAt |
| `finance_debt_payments` | debt, amount, date |

- Categories are seeded (Comida, Transporte, Casa, Servicios, Salud, Entretenimiento, Otros; Sueldo, Otros ingresos) and never deleted, only archived, so movements keep their category.
- A movement takes its kind from its category: an expense cannot land in an income category.
- What is left of a debt is computed from its payments (`DebtWithPaid`). A payment larger than what is left is rejected; paying it off sets `settledAt`, deleting a payment or raising the amount clears it. The amount cannot go below what was paid.
- Payments do not create movements: borrowing and paying back would count twice. Debts stay out of the budget.

## Use

- A card in "Mi día" shows the month's spending (against the total budget, if any) and opens Finanzas. The final navigation is decided in the UX/UI review.
- Finanzas has three tabs: **Resumen** (income, expenses, balance, budget bars per category; going over is said in words, not only in red), **Movimientos** (the month's list, editable) and **Deudas** (I owe / owed to me / settled, with totals). Arrows change the month.
- "Registrar" opens a sheet: amount first with a numeric keyboard, category chips, date defaults to today.
- A debt's detail lists its payments; "Todo: $X" fills in what is left.

## Storage

Four new tables (Room auto-migration 4→5). The backup gains four optional arrays, `categories`, `movements`, `debts` and `debtPayments`; the format stays at version 1.

## Out of scope

Accounts and cards with their own balance, other currencies, charts beyond the bars, receipt photos, bank import, automatic fixed expenses (a recurring task reminds you meanwhile), prices in shopping lists.
