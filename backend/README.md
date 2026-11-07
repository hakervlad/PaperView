# PaperView Backend

Single-user FastAPI service. SQLite stores typed JSON objects (profile, concepts, documents, page-referenced chunks/evidence, papers) plus transactional daily leases and seen identifiers. Services separate extraction, retrieval, sources, LLM and personalization. No separate cloud/graph infrastructure.

## Local start

```sh
cd backend
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export APP_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
# Keep this token private and enter it as Backend-Zugriffsschlüssel in Android.
export PAPERVIEW_DATA_DIR=/absolute/private/path/paperview-data
export LLM_API_KEY= # set privately in your shell, never commit
export LLM_MODEL= # provider's exact model ID
export LLM_BASE_URL=https://api.openai.com/v1
export LLM_ALLOWED_HOSTS=api.openai.com
uvicorn paperview.main:app --host 127.0.0.1 --port 8000 --no-access-log --no-proxy-headers
```

For DeepSeek configure its documented compatible HTTPS URL, exact model and hostname allowlist. No model is silently substituted. Empty APP_TOKEN fails closed. Model requests validate public DNS and pin the validated address while retaining TLS SNI; redirects and environment proxies are disabled. Private provider endpoints are intentionally unsupported.

Android Debug can reach loopback through `adb reverse tcp:8000 tcp:8000` and URL `http://127.0.0.1:8000/`, or emulator alias `http://10.0.2.2:8000/`. HTTP supports only an operator-owned server key, never a user model key. For user-key mode terminate HTTPS with a valid system-trusted certificate and pass to loopback; set TRUSTED_PROXIES only to that proxy's exact IP. Do not let uvicorn trust arbitrary forwarded headers. Never expose a plaintext network listener for private data. Keys from X-LLM-Key are request-scoped and not saved/logged. External provider calls can cost money.

`/health` is public. All `/v1/*` endpoints and `/openapi.json` require the APP_TOKEN bearer header. Obtain the OpenAPI schema with an authenticated client. No public Swagger endpoint is exposed. API paths/models are defined in `../docs/IMPLEMENTATION.md`; Android configuration is in `../docs/ANDROID_INTEGRATION.md`.

## Processing and privacy

Upload at most 10 MB, 100 pages, 120000 extracted characters and 60 page-referenced chunks. Extraction uses a time-limited, resource-limited subprocess, random private filenames and removes the PDF original after extracting text. Text/evidence remain in the private SQLite directory until deletion. No OCR. Encrypted/malformed/image-only files receive explicit errors. External concept extraction requires separate consent. Only the first 12 chunks are sent, and this partial coverage is disclosed. Candidate concepts never become known automatically. Confirmation and correction are explicit. Document deletion removes chunks/evidence and unconfirmed document-only candidates; independently confirmed knowledge is retained without document evidence.

Retrieval is bounded lexical overlap plus directly linked confirmed concepts. Search combines profile and confirmed-topic terms. OpenAlex uses publication_date (not indexing time), optional OPENALEX_API_KEY bearer; arXiv uses original published dates, HTTPS and a cross-process 3-second request gate. Both sources must succeed before an empty daily issue is accepted. Identifier deduplication uses DOI/arXiv IDs; title fallback is heuristic when identifiers are absent.

All explanations are abstract-based. The LLM receives only the paper abstract, bounded relevant confirmed concepts and profile context. Returned concept IDs must belong to that retrieval. No connection is allowed without referenced known concepts. An understood paper saves the generated takeaway, source and prior concept links, then participates in later retrieval. Unique per-day SQLite leases prevent parallel duplicate successful issues. Failed attempts preserve the previous feed, have five-minute cooldown and a three-attempt daily cap. Network work never holds SQLite transactions.

## Tests and limits

```sh
.venv/bin/python -m pytest -q
```

18 tests passed at integration review, including PDF/consent, authentication, concept state/deduplication/deletion, provider failures, source dedupe, daily reuse/concurrency/retry and full PDF-to-next-day knowledge reuse. Tests use HTTP/model doubles, not paid APIs. A successful mock test is not proof of provider balance, live source availability or scientific correctness.

Known limits: no OCR, embeddings, full-paper analysis or independent fact checker. JSON/ID validation prevents invalid references but cannot prove a model's scientific claims. English/German keyword mismatch can reduce retrieval quality. Source schema/quotas may change; current official sources checked: https://help.openalex.org/api/ , https://help.openalex.org/api/authentication/ , https://info.arxiv.org/help/api/user-manual.html and https://info.arxiv.org/help/api/tou.html . No scheduled background daemon: the app requests its daily issue when opened. This is a protected single-user service; multi-user identity/tenant isolation is not implemented.
