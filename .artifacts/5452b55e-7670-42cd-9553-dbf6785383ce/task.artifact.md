# Tasks: Consolidated Audit Reports, Date Labeling & Export Options

- [x] **1. ViewModel & Printing Consolidation**
    - [x] Update `AuditViewModel.kt` to consolidate identical items (sum quantities and prices) in `printAudit()`
    - [x] Add formatted audit date/period label to the printed audit receipt header
- [x] **2. Export Options (Download or Share)**
    - [x] Implement `generateCsvFile` in `AuditViewModel.kt` for temporary caching and FileProvider sharing
    - [x] Add Export Dialog in `AuditScreen` (`AdminDashboard.kt`) giving options for "Download" or "Share"
- [x] **3. Build & Verification**
    - [x] Build the APK and verify compilation
