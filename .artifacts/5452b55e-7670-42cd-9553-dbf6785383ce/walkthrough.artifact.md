# Walkthrough - Customer Receipt Header for Two Slips Printing

I have updated the duplicate / two-slip printing feature so that when two slips are printed for an order, the second slip explicitly displays **`*** CUSTOMER RECEIPT ***`** in a prominent header below the shop title.

## Changes Made

### 1. Slip Title Header Parameter
- **[BitmapHelper.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/util/BitmapHelper.kt)**:
  - Added `slipTitle: String? = null` parameter to `drawFancyReceipt()`.
  - When provided, renders `*** CUSTOMER RECEIPT ***` or `*** CASHIER COPY ***` in bold text centered below the shop name.

### 2. Multi-Pass Printing Router
- **[BluetoothPrinterHelper.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/printing/BluetoothPrinterHelper.kt)** & **[CartViewModel.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/viewmodel/CartViewModel.kt)**:
  - When "Two Slips" is enabled in Admin Settings:
    - **Slip 1 (First Pass)**: Renders `*** CASHIER COPY ***` (and kicks cash drawer if cash payment).
    - **Slip 2 (Second Pass)**: Renders `*** CUSTOMER RECEIPT ***` prominently.

---

## Verification Results

### Automated Build
- Executed `gradle_build("assembleDebug")` -> **Build finished successfully**.
