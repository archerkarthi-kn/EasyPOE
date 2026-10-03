# Tasks: Customer Receipt Header on Two Slips Printing

- [x] **1. Receipt Header Title Parameter (`BitmapHelper.kt`)**
    - [x] Added `slipTitle` parameter to `drawFancyReceipt()` to render `*** CUSTOMER RECEIPT ***` or `*** CASHIER COPY ***` headers centered on receipts
- [x] **2. Multi-Pass Printing Router (`CartViewModel.kt` & `BluetoothPrinterHelper.kt`)**
    - [x] Updated two-slip printing to pass `CASHIER COPY` on pass 1 and `CUSTOMER RECEIPT` on pass 2
- [x] **3. Build & Verification**
    - [x] Built the APK and verified zero compilation errors
