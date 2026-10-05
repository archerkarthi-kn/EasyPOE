# Tasks: Category Resolution Fix for Audit Reports

- [x] **1. Order Item Category ID Propagation**
    - [x] Updated `CartViewModel.kt` to pass `categoryId = cartItem.product.categoryId` when saving order items to SQLite database
- [x] **2. Historical Audit Record Category Lookup**
    - [x] Updated `AuditViewModel.kt` to dynamically resolve category names for existing records by matching product names with the products table in Room DB
- [x] **3. Build & Delivery**
    - [x] Built the APK and verified zero compilation errors
