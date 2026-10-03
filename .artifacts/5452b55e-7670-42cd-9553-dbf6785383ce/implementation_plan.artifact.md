# Implementation Plan - Web Admin Management, Dual Slips/Token Numbering & Redesigned Admin Settings

Implement Web Admin Product & Category Management on the PC Web Dashboard, Dual-Slip (Two Printers / Duplicate Slip) Printing with configurable Token Numbers, and a completely refreshed, elegant Admin Settings UI.

## User Review Required

> [!IMPORTANT]
> **Web Admin PC Management**: You will be able to manage products, edit prices, create categories, and upload product images directly from your PC/laptop browser by visiting `http://<POS_IP>:8080`!
> **Dual Slips & Token Numbers**:
> - **Two Slips Toggle**: When enabled, the app prints two consecutive slips for every order (one for cashier/kitchen, one for customer).
> - **Token Number Toggle**: Enable or disable printing `TOKEN #001` prominently on customer slips. Includes a "Reset Token Number" button.
> **Settings Redesign**: Fresh, organized Material 3 card layout for easier navigation and setup.

## Proposed Changes

### 1. Web Server Product & Category API
#### [MODIFY] [PosWebServer.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/server/PosWebServer.kt)
- Add endpoints:
  - `GET /api/products`: Returns all products with category names.
  - `GET /api/categories`: Returns all categories.
  - `POST /api/products/save`: Saves or updates product details (Name, Price, Category, Barcode, Image).
  - `POST /api/products/delete`: Deletes a product.
- **Web UI Update**:
  - Add a **Products & Pricing** tab to the Web Dashboard allowing full PC management of products, prices, and image uploads from any computer browser on the Wi-Fi network.

---

### 2. Dual Slips & Token Number Settings
#### [MODIFY] [SettingsRepository.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/data/SettingsRepository.kt)
- Add preferences:
  - `PRINT_TWO_SLIPS` (Boolean)
  - `ENABLE_TOKEN_NUMBER` (Boolean)
  - `TOKEN_COUNTER` (Int)
- Add methods: `setPrintTwoSlips()`, `setEnableTokenNumber()`, `getNextTokenNumber()`, `resetTokenCounter()`.

#### [MODIFY] [BitmapHelper.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/util/BitmapHelper.kt)
- Render `TOKEN #XXX` in large bold font at the top header of receipts when token numbers are enabled.

#### [MODIFY] [CartViewModel.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/viewmodel/CartViewModel.kt)
- Handle dual-slip printing: if `PRINT_TWO_SLIPS` is enabled, send print job twice or send Customer Slip + Cashier Slip.

---

### 3. Redesigned Admin Settings UI
#### [MODIFY] [AdminViewModel.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/viewmodel/AdminViewModel.kt)
- Expose `printTwoSlips`, `enableTokenNumber`, `tokenCounter` flows and management methods.

#### [MODIFY] [AdminDashboard.kt](file:///C:/APPLICATION/Android/POS/app/src/main/java/fyi/copiercode/easypos/ui/screens/AdminDashboard.kt) (`SettingsScreen`)
- Redesign Admin Settings with a sleek, modern card-based layout grouped by categories:
  - **Printer & Slips Configuration**: Connection Type (Bluetooth, Wi-Fi, USB), Two Slips toggle, Token Number toggle, Reset Token Counter.
  - **Shop Branding**: Shop Name, Welcome Banner, Logo, Footer Contacts.
  - **Terminal & Security**: Dark Mode, Payment Screen Toggle, Manual Billing PIN, Wi-Fi Bridge Settings.
  - **System Backup & Updates**: Backup, Restore, Version Check.

## Verification Plan

### Manual Verification
1. **Web PC Product Editing**: Open `http://<POS_IP>:8080` on PC browser -> go to Products tab -> edit a product price -> verify it updates instantly on the Android POS screen.
2. **Two Slips Test**: Enable "Two Slips" in Admin Settings -> complete a sale -> verify 2 receipt slips are printed.
3. **Token Number Test**: Enable "Token Number" -> complete a sale -> verify `TOKEN #001` appears on receipt. Tap "Reset Token Number" -> next sale shows `TOKEN #001`.
4. **Admin UI**: Verify the redesigned Admin Settings screen is clean, organized, and easy to use.
