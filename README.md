# SheetForge

SheetForge is a native Android spreadsheet app scaffolded as an Excel-style workbook experience.

## Current features

- Touch spreadsheet grid with row and column headers
- Formula bar and in-cell editing
- Multiple sheets with bottom tabs
- Formatting ribbon for bold, italic, fill color, alignment, currency, percent, and general number formats
- Formula support for arithmetic, cell references, ranges, `SUM`, `AVG`, `AVERAGE`, `MIN`, `MAX`, and `COUNT`
- Selection ranges, sort, filter, undo, redo, quick chart preview, and CSV import/export dialog
- Seed workbook data so the app opens to a useful spreadsheet immediately

## Project layout

- `app/src/main/java/com/sheetforge/app/MainActivity.kt` contains the app shell, custom grid view, workbook model, formula engine, undo stack, chart view, and CSV helpers.
- `app/src/main/AndroidManifest.xml` declares the Android entry point.
- `build.gradle.kts`, `settings.gradle.kts`, and `app/build.gradle.kts` configure the Android/Kotlin build.

## Build

Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on an emulator or Android device.

From a configured terminal with Android SDK and Gradle available:

```powershell
gradle :app:assembleDebug
```

The current sandbox did not expose a usable Gradle/Android SDK toolchain, so the project has been created but not compiled in this environment.
