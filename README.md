# SpenIt AICore

SpenIt AICore is an intelligent Android application for tracking your finances, powered by Google's on-device AI (Gemini Nano) via AICore and ML Kit.

## Features

- **Dashboard:** Get a quick overview of your finances, including recent income and expenses.
- **Income & Expense Tracking:** Easily manage and log your daily transactions.
- **AI-Powered Scanning:**
  - **Receipt Scanner:** Use the camera to scan receipts. The app uses ML Kit and Gemini Nano to automatically extract information.
  - **PaySlip Scanner:** Scan your payslips to seamlessly log your income.
- **Insights:** Visualize your spending habits and track your financial trends over time.
- **Shared Imports:** Import financial data shared from other applications or sources.
- **Settings:** Customize your preferences, including currency formatting and salary cycles.
- **Security:** Built-in biometric authentication support.

## Tech Stack

- **UI Framework:** Jetpack Compose (Material 3)
- **Language:** Kotlin
- **Database:** Room
- **Preferences:** DataStore
- **Camera Integration:** CameraX
- **Image Loading:** Coil
- **AI & ML:** 
  - AICore (GenAI Prompt / Gemini Nano)
  - Google ML Kit (Text Recognition Fallback)
- **Concurrency:** Kotlin Coroutines
- **Navigation:** Navigation Compose

## Requirements

- **Minimum SDK:** 31 (Android 12)
- **Target SDK:** 35
- Android device compatible with Google AICore for advanced on-device AI features.

## Setup & Installation

1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Build and run the app on an Android device or emulator running API 31+.

## Architecture

The project follows a modern Android architecture:
- **UI Layer:** Jetpack Compose, ViewModels (`androidx.lifecycle.ViewModel`)
- **Data Layer:** Repositories, Room DAOs, DataStore Preferences
- **AI Layer:** Integrated AI Core Services for processing scanned documents
