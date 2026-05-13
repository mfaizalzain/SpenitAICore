# Play Store Publishing Pack

Refreshed on 2026-05-13 for the current SpenIt AICore UI and feature set.

## Upload Build

- Signed AAB: `app/build/outputs/bundle/release/app-release.aab`
- Application ID: `com.fmz.spenitaicore`
- Version: `1.1.2`
- Version code: `15`
- Track: use Internal testing, Closed testing, or Production depending on release readiness.

## Signing

- Upload keystore: `publish/play/credentials/spenit-upload.jks`
- Local signing env: `publish/play/credentials/signing.env`
- Upload key alias: `spenit-upload`
- SHA-1: `CA:11:08:40:5D:C4:FE:70:0C:9B:4D:6D:EE:1E:54:9C:5F:EA:70:42`
- SHA-256: `2E:80:6D:6E:9D:45:26:FB:79:91:0B:7E:4C:9D:91:0E:7C:68:21:3C:58:AE:E7:68:23:D4:A1:F1:92:46:69:C8`

Use Play App Signing with a Google-generated app signing key for the first Play release, and upload this AAB as the upload-key-signed bundle.

To rebuild the same signed bundle locally:

```sh
set -a
. publish/play/credentials/signing.env
set +a
./gradlew clean bundleRelease
```

## Store Assets

- App icon: `publish/play/assets/play-icon-512.png`
- Feature graphic: `publish/play/assets/feature-graphic-1024x500.png`
- Phone screenshots:
  - `publish/play/assets/phone-dashboard.png`
  - `publish/play/assets/phone-expenses.png`
  - `publish/play/assets/phone-income.png`
  - `publish/play/assets/phone-insights.png`

Recommended refreshed screenshot set for the current UI:

1. Dashboard with safe-to-spend, quick stats, import actions, and recent expenses
2. Expenses with search, filters, categories, and receipt records
3. Shared Imports with receipt/PDF/bank statement queue and processing states
4. Income with payslip scanning, source filters, and totals
5. Insights with AI analysis, top categories, weekly trends, and tax-deductible total
6. Tax Relief with yearly totals, category summary, deductible receipts, and ZIP export

The current checked-in screenshot assets still cover Dashboard, Expenses, Income, and Insights. Regenerate the set above before the next Play submission so the Store listing shows the new Tax Relief tab and Shared Imports workflow.

## Store Copy

- Listing copy: `publish/play/store-listing.md`
- Release notes: `publish/play/release-notes.txt`
- Creative direction: `publish/play/play-store-experience.md`

## Before Submitting For Review

- Add a live privacy policy URL in Play Console.
- Add the developer website URL in Play Console under Store presence > Store settings > Store listing contact details.
- Publish `publish/play/app-ads.txt` at the root of the developer website so it is reachable as `https://spenit.my/app-ads.txt`.
- In AdMob, open Apps > View all apps > app-ads.txt, expand SpenIt AICore, and click Check for updates after the file is live.
- Complete Data Safety based on actual collection, sharing, encryption, and deletion behavior.
- Complete App content declarations, including financial features, permissions, and target audience.
- Add testers to the Internal testing or Closed testing track.
- Check that the Play Console account deletion, data deletion, financial features, ads, and AI/provider disclosures match the production build.

The app-ads.txt seller line for the configured AdMob publisher ID is prepared in `publish/play/app-ads.txt`.
