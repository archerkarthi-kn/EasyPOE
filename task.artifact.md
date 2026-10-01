# Task List - POS App Fixes & Enhancements

- [x] **Data Layer & Sorting Fix**
    - [x] Add `swapProductOrder` transaction to `ProductDao.kt`.
    - [x] Implement `orderIndex` initialization logic in `AdminViewModel.kt`.
    - [x] Fix `moveProductUp` and `moveProductDown` in `AdminViewModel.kt`.
- [x] **Checkout Logic (Printer Bypass)**
    - [x] Refactor/Verify `checkout` in `CartViewModel.kt` to follow the 4-step protocol.
- [x] **Hardware & Permissions**
    - [x] Add Bluetooth permission request logic to `MainActivity.kt`.
    - [x] Refine iMin SDK status mapping in `PrinterHelper.kt`.
- [x] **UI Refinement**
    - [x] Verify/Update status indicator in `AdminDashboard.kt` and `POSScreen.kt`.
    - [x] Refine `CustomerScreen.kt` layout for 1280x800 resolution.
- [x] **Verification & Build**
    - [x] Run `app:assembleDebug` and verify sorting/bypass manually.
