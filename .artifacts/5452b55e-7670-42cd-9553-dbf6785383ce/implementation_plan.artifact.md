# Implementation Plan - Consolidated Audit Reports & Date Labeling

Enhance the Audit reporting feature to consolidate identical items into single totals on printed audit reports, print the specific audit date/range clearly on the receipt, and ensure clear viewing of sales by user-defined dates and payment categories.

## User Review Required

> [!IMPORTANT]
> **Consolidated Audit Items**: On the printed audit report, multiple purchases of the same product on the audited day/period (e.g., buying 3 Bullets earlier and 2 Bullets later) will be automatically combined into a single entry with total quantity (e.g., `Bullet x5`) and total sales amount, instead of printing them as separate lines.
> **Audit Date Labeling**: The printed audit receipt will explicitly show the audited date or date range at the top so it's instantly clear which period the report covers.

## Proposed Changes

### 1. Audit ViewModel & Printing Logic
#### [MODIFY] [AuditViewModel.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/viewmodel/AuditViewModel.kt)
- **Item Consolidation**: In `printAudit()`, group filtered audit records by item name, summing their quantities and sales prices so identical items appear once with their total quantity.
- **Audit Date Range Header**: Format the active audit period (e.g. Daily date or Date Range) and include it in the printed audit report metadata (e.g., passing formatted date info to the order/header).

### 2. Printer Helper Support
#### [MODIFY] [BluetoothPrinterHelper.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/printing/BluetoothPrinterHelper.kt)
- Ensure `printReceipt()` prints the audit period/date clearly in the header section.

---

## Verification Plan

### Manual Verification
1. **Consolidated Print Test**:
   - Make multiple sales of the same item (e.g., Bullet x3, then Bullet x2) on the same day.
   - Go to Admin > Audit -> Tap "Print Audit".
   - Verify the printed receipt shows `Bullet x5` grouped together once rather than separate lines.
2. **Audit Date Label Test**:
   - Select a specific date filter (e.g., Daily or Range).
   - Print audit -> Verify the specific date or date range is printed on top of the receipt.
3. **Payment Category View**:
   - Verify audit screen correctly lists payment categories (Cash/Card) per transaction and totals.
