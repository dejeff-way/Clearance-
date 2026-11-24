# FORMA — personal fitness & nutrition

Native Android app (Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, Health Connect 1.1.0).

## Install
| File | Notes |
|---|---|
| `dist/forma-release.apk` (5 MB) | **Recommended.** R8-minified. Signed with the local *debug* key because no release keystore was provided. |
| `dist/forma-debug.apk` (74 MB) | Unminified debug build. Same as `app/build/outputs/apk/debug/app-debug.apk` after a build. |

Copy an APK to the phone and open it (allow "install unknown apps" for your file manager). Requires Android 9 (API 28)+.
The two APKs share a signing key, so you can update one with the other.

## Build
Requirements: JDK 17+ (built with 21), Android SDK platform 36 + build-tools 36. Open the folder in Android Studio (Narwhal or newer) or:
```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
./gradlew test               # engine + Robolectric app tests
./gradlew :app:recordRoborazziDebug --tests "*Screenshot*"   # re-render app/screenshots/
```
Real release signing: create `keystore.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); it is git-ignored and used automatically.

## Structure
- `engine/` — pure Kotlin calorie engine (no Android): Mifflin–St Jeor BMR, expenditure estimation with double-count protection, dynamic budget with minimum safeguard, carryover ledger, weight-trend analysis and recommendations, progression, ingredient-based portion scaling. Fully unit tested.
- `app/data` — Room database (profile, food log, recipes + ingredients, daily energy, exercise sessions, carryover, weights, waist, workouts, sets, substitutions, sync metadata), repositories, seed recipes and workout program, JSON backup.
- `app/health` — Health Connect: `HealthDataSource` interface, `HealthConnectSource` (official SDK), `HealthSyncer` (idempotent sync), `SyncWorker` (optional hourly background sync).
- `app/ui` — design system (`theme`, `components`) and screens.

## How the numbers work
1. **Expenditure** — one source per day, never summed with overlapping data:
   - Health Connect *Total calories burned* when it is plausibly complete (≥ 80 % of BMR for past days; pro-rated for today). Today is projected to midnight at 1.1 × BMR for the remaining hours. Steps, device-recorded sessions and FORMA workouts are **not** added on top.
   - Otherwise BMR + Health Connect *Active calories*.
   - Otherwise an estimate: BMR × 1.2 + the **larger** of (steps beyond 5,000) and (net exercise-session energy). Manual sessions marked "not recorded by a device" are the only thing added to measured totals.
2. **Budget** = expenditure − desired deficit (default 500), or a fixed target. Never below the minimum (default 2,000): it is held there with a warning.
3. **Carryover** — overages are repaid from the next day, max 200 kcal/day, never pushing a budget below the minimum; the rest carries forward. Carryover only changes budgets — never intake or the actual deficit.
4. **Actual estimated deficit** = burned − consumed, shown separately from planned deficit and remaining budget.
5. **Trend** — rolling 7-day average; after 14 days of weigh-ins an optional ≤200 kcal recommendation appears. Nothing changes automatically.

Every figure is labelled as an estimate; tap the dashboard summary to see the full calculation.

## Health Connect permissions
All read-only; FORMA never writes to Health Connect.
| Permission | Used for |
|---|---|
| `READ_TOTAL_CALORIES_BURNED` | Preferred daily expenditure |
| `READ_ACTIVE_CALORIES_BURNED` | Fallback expenditure (BMR + active) |
| `READ_STEPS` | Steps card, estimate mode |
| `READ_EXERCISE` | Exercise sessions, de-duplication with logged workouts |
| `READ_WEIGHT` | Import scale weigh-ins |
| `READ_SLEEP` | Sleep card |
| `READ_HEALTH_DATA_IN_BACKGROUND` | Optional; only requested if you turn on background sync |

History beyond 30 days is not requested (`READ_HEALTH_DATA_HISTORY` is not used). The manifest also includes the privacy-rationale activity Health Connect requires. WorkManager adds its standard `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and `ACCESS_NETWORK_STATE` declarations; FORMA makes no network requests.

## Tested (101 automated tests, all passing)
- **Engine (43):** BMR ≈ 2,265 for the default profile; HC-total, HC-active and estimate modes; partial-day projection; rejecting incomplete totals; steps vs sessions (no double count); overlap de-duplication; budget minimum and warnings; fixed mode; remaining vs deficit; carryover 300 → 200 + 100, minimum-limited repayment, multiple overages, edit/cancel; trend smoothing, 14-day recommendation logic; progression; ingredient scaling.
- **Data layer (20, Robolectric + Room):** 17 recipes in 4 collections, no desserts, Creami under Snacks; no dairy in meat recipes; raw-fish warnings; portion scaling; milk choice changes nutrition; favourites survive re-seed; log/edit portion/delete updates the summary; calories-only manual entry; workouts raise estimated (not measured) expenditure; carryover affects budget only; weight → BMR; set logging, completion, remembered substitutions; substitutions stay in the same movement pattern; **persistence after closing and reopening the database file**; backup round-trip.
- **Health Connect sync (9, fake data source):** import; re-sync creates no duplicates; deletions propagate; manual weights untouched; only granted types stored; permission-denied, unavailable and revoked-mid-read states; synced total drives the budget.
- **UI flows (9, real Compose screens):** all five tabs; manual food entry updates "remaining" immediately; recipe Large portion → log → correct kcal; delete from food log; log weight; start workout, check off, substitute; record carryover; HC unavailable / not-granted / partially-granted screens; theme and targets persist.
- **Screenshots (20):** every main screen in light and dark — see `app/screenshots/`.

## Not done / limitations
- **No physical device or emulator was available** (the build machine has no hardware virtualisation), so the app ran under Robolectric rather than on a phone. In particular, the real Health Connect permission dialog and real device data could not be exercised; the SDK calls compile against the official API, and the sync logic is tested with a fake data source. Please check the Health Connect flow on your phone first.
- The release APK uses the debug signing key (no keystore was provided). It is fine for personal sideloading but not for the Play Store.
- Recipe images are generated illustrations drawn from each recipe's ingredients (no stock photos were available offline). You can replace any of them with your own photo from the recipe screen.
- Nutrition values are computed from USDA-style per-ingredient values and typical labels. Where these differ from your original estimates (e.g. egg scramble 248 vs 310 kcal), the recipe screen shows both. Chicken in the bowls is given as cooked weight.
- Kosher notes are general guidance (e.g. kosher "London broil" is normally a shoulder cut), not a psak; follow your rabbi.
