# Android integration and verification log

The existing Compose design, cards, theme and two primary tabs are retained. A settings route and a compact status/action row are added. Runtime feed/knowledge now come from the REST API, not DemoContent; fixtures remain exclusively for previews. No invented content is used as an offline fallback.

## Data flow

`Compose -> PaperViewModel (StateFlow) -> PaperViewRepository -> Retrofit -> FastAPI`.
SQLite in the backend is authoritative. Android caches a bounded snapshot using an atomic encrypted private file instead of duplicating the backend's relational schema in Room. Connection settings use DataStore; **all** settings including both tokens are AES-GCM encrypted with a non-exportable Android Keystore key. Backup is disabled. API keys are never placed in SavedInstanceState, logs or source code. A backend URL/token change discards the old cache to prevent mixing profiles.

On launch/resume, load the private cache and query the backend asynchronously. Request generation only when today's successful feed is absent. A local short cooldown suppresses rapid lifecycle retries; server persistence/unique day constraints remain authoritative across restarts. A failed call leaves the cached data visible. The refresh button permits an explicit retry. Confirmed knowledge alone appears in the knowledge collection; candidates and learning goals are managed separately in settings.

## Configuration

1. Start the protected backend using its README; record its application access token.
2. Android Studio: open the existing root project, sync and run `app`.
3. Open **Einstellungen**, enter the trusted backend URL and application token, and save.
4. Development: a Debug APK may use `http://10.0.2.2:8000/` for the Android emulator (or localhost with `adb reverse tcp:8000 tcp:8000`). Release builds forbid all cleartext. This local mode supports a server-owned model key.
5. For a user-owned model key use **HTTPS**, including during local development. Configure a valid trusted TLS certificate/reverse proxy. Enter provider label, exact model name, optional allowlisted model base URL and API key. Save, then test the saved connection. No automatic paid test call is made when typing/saving.
6. Save Bachelorfach, comma-separated interests/focus/explicit prior knowledge, count and publication window.
7. Select your own permitted text PDF using the system document picker. Confirm upload/extraction. Review filename/status, then explicitly consent to external model analysis. Confirm or correct suggested concepts separately.
8. Generate the feed and mark an article understood; confirmed knowledge and source association persist on the backend. Source links are opened externally only for HTTPS URLs.

Do not commit keys, tokens, PDFs or local databases. Deleting a model key removes the encrypted local value. It does not delete the provider's key/account; revoke with the provider if needed. Imported PDFs and knowledge are deleted via the backend endpoints, not merely hidden locally.

## Error log and checks

- First compile: Retrofit's transitive OkHttp 3 did not provide Kotlin extension methods. Fixed by explicitly declaring OkHttp 4.12.0.
- First compile: missing DemoArticle import in the navigation adapter. Fixed.
- First lint: debug loopback domains required an explicit `includeSubdomains` attribute. Fixed to `false`; unrelated hosts/subdomains remain blocked.
- Existing AGP 9 compatibility flags emit deprecation warnings; they were already in the project and were not migrated as part of this feature.
- Automated Android tests cover HTTPS, debug loopback, release cleartext rejection, remote cleartext rejection, embedded URL credentials, missing authentication and header injection.
- Final build/test/lint results and backend/Android end-to-end coverage must be recorded after integration; this document is not evidence of unexecuted checks.

## Known verification boundaries

Live paid model calls require operator/user credentials and adequate provider balance. Mocked transport tests do not establish scientific correctness or real provider availability. The app must not claim that any supplied scientific statement is independently verified by its JSON validation. Abstract-only explanations are explicitly labelled. OCR is not implemented.

## Android checkpoint (2026-11-07)

Executed `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with the existing Gradle 9.6.0/AGP 9.5.1 configuration, temporary JDK 21 and SDK 35: BUILD SUCCESSFUL. Nine unit tests passed, zero failures/errors. Lint has no blocking errors; dependency/target/deprecated custom-check warnings remain. Scientific UI mapping tests preserve the paper ID, source URL, abstract-only marker and actual supplied prior concepts, and do not substitute demo knowledge. Backend integration and emulator end-to-end verification are still pending; issue #1 remains open.
