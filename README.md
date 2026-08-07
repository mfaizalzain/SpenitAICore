# SpenIt AICore

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blueviolet)](https://kotlinlang.org)
![License: All Rights Reserved](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)

SpenIt AICore is a **private-first Android finance tracker** built with Jetpack Compose and Kotlin. It helps you track expenses, income, receipts, bank statements and tax relief — with AI-powered document scanning that runs **on-device** by default.

Your financial data stays on your phone. AI extraction works through Google AICore (Gemini Nano) on supported devices, and manual tracking works everywhere. Optionally, you can configure your own API key for Gemini, OpenAI, or any OpenAI-compatible endpoint.

> ⚠️ SpenIt AICore is a personal record-keeping tool. It does **not** connect to your bank accounts and does **not** provide financial, tax or investment advice. Always review AI-extracted records before relying on them.

---

## ✨ Features

- **Dashboard** — salary-cycle-aware overview: safe-to-spend, today / week / cycle totals, income context, tax-deductible total, recent expenses and an AI-generated summary.
- **Expense & Income tracking** — log transactions manually, search, filter by category/period, and use smart queries like `tax 2024`, `>500`, `#groceries` or `jan-mar`.
- **AI-powered scanning** — receipt scanner (camera + document scanner with auto-crop) and payslip scanner that extract merchant, totals, dates, categories and line items.
- **Shared imports** — share images, PDFs or bank statements into the app from any source; files are auto-classified, imported and checked for duplicates.
- **Bank statement import** — Malaysian bank statement parser that turns credits/debits into income and expense entries.
- **Insights** — spending trends, category breakdowns, AI summaries, saving tips, anomaly alerts and **likely recurring expense** detection.
- **Tax Relief** — mark expenses tax-deductible, group them by relief category, and export a year's receipts as a ZIP (CSV summary + document images).
- **Budgets** — set a monthly limit per category and watch progress with near-limit and over-budget warnings.
- **Data export** — export every receipt and income entry as CSV (ZIP) at any time.
- **Home-screen widget** — a glanceable widget showing your current cycle spend, today's spend and safe-to-spend (Glance).
- **Multi-currency** — amounts stored in any currency are converted into your default currency for totals, and conversion hints appear on records in foreign currencies.
- **Google Drive backup & restore** — opt-in nightly backups (WorkManager) with one-tap restore.
- **Security** — biometric app lock, Google sign-in and passkeys via Credential Manager, and remote AI API keys encrypted at rest with the Android Keystore.
- **Dark theme** — day / night / follow-system modes.

## 🧱 Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.3 |
| UI | Jetpack Compose, Material 3 (Compose BOM 2026.06) |
| Architecture | MVVM — ViewModels + StateFlow |
| Database | Room (SQLite) |
| Preferences | DataStore |
| Navigation | Navigation Compose |
| Camera | CameraX + ML Kit Document Scanner |
| Images | Coil |
| Background work | WorkManager (nightly backups) |
| Home-screen widget | Glance |
| Networking | OkHttp |
| AI / ML | ML Kit GenAI (AICore / Gemini Nano), ML Kit text recognition, optional remote providers (Gemini, OpenAI, custom) |
| Auth | Credential Manager (Google Sign-In, passkeys), BiometricPrompt |
| Ads | AdMob (banner + native) |
| Tests | JUnit 4 — 47 unit tests |

## 📱 Requirements

- **Minimum SDK:** 31 (Android 12)
- **Target SDK:** 36 · **Compile SDK:** 37
- An Android device with **Google AICore** is recommended for on-device AI, but not required — manual entry, remote AI providers, and ML Kit fallback work on any device.

## 🚀 Getting Started

1. **Clone the repository**

   ```bash
   git clone https://github.com/mfaizalzain/SpenitAICore.git
   ```

2. **Open in Android Studio** (or build from the command line)

   ```bash
   ./gradlew :app:assembleDebug
   ```

3. **Configure your own keys** (before releasing):
   - `app/src/main/res/values/strings.xml` → replace `google_client_id` with your Google Cloud / Firebase OAuth client ID (required for Google sign-in).
   - Replace the AdMob `admob_app_id` and ad-unit IDs with your own, or remove the ad components.

4. **Run** the `app` configuration on a device or emulator running API 31+.

> 🔐 The release signing config is read from environment variables (`SPENIT_UPLOAD_KEYSTORE`, `SPENIT_UPLOAD_STORE_PASSWORD`, `SPENIT_UPLOAD_KEY_ALIAS`, `SPENIT_UPLOAD_KEY_PASSWORD`) and is skipped when they are absent. See [publish/play](publish/play) for the Play Store publishing pack.

## 🧪 Testing

```bash
./gradlew :app:testDebugUnitTest
```

Unit tests cover the pure business logic: salary cycles, date utilities, currency formatting & conversion, category suggestions, the bank-statement parser, recurring-expense analysis, and shared-import state.

## 🏗️ Architecture

```
app/src/main/java/com/fmz/spenitaicore/
├── ui/          # Compose screens, navigation, theme, reusable components
├── viewmodel/   # ViewModels exposing StateFlow state to the UI
├── data/        # Room (entities/DAOs), DataStore preferences, repositories,
│                # auth, backup, export, exchange rates, notifications
├── ai/          # AICore/ML Kit extraction, bank-statement parser, remote AI
├── util/        # Currency, dates, categories, salary cycles, crypto helpers
└── widget/      # Glance home-screen widget
```

The app uses a lightweight **manual DI** container (`SpenItApp.AppContainer`) rather than a DI framework.

## 🔒 Security & Privacy

- All financial records live **on-device** (Room). Nothing is uploaded unless you explicitly enable Google Drive backup.
- Remote AI (Gemini/OpenAI) is used **only if you enter your own API key**; the key is encrypted at rest with the Android Keystore.
- Optional biometric app lock protects the app on cold launch.
- Backup restore validates archive paths to prevent zip-slip style attacks.

## 🤝 Contributing

Contributions are welcome! Open an issue for bugs or feature ideas, and feel free to submit pull requests. Please keep changes focused and add unit tests where the change touches business logic.

## ☕ Support

If SpenIt AICore is useful to you, consider supporting development via [Buy Me a Coffee](https://buymeacoffee.com/faizalmzain).

## 📄 License

All rights reserved. This repository is public for reference and collaboration, but no open-source license is applied yet — you may not redistribute or republish the code without permission. If you'd like to use it under a specific license, please open an issue to discuss it.
