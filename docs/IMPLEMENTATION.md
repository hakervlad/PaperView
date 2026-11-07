# PaperView: Backend integration

## Scope and phases
Keep the existing single-module Android UI and visual design. Implement a single-user FastAPI/SQLite service, then connect Android using Retrofit, a repository/ViewModel with StateFlow, encrypted Keystore credentials, settings, SAF import and an offline cache. No fake news fallback. Tests use injected HTTP/model responses, never real credentials. Actual provider calls require separately supplied credentials.

1. Backend persistence, authentication and typed REST contract.
2. Bounded text-PDF import, consent-based concept extraction, confirmation and retrieval.
3. OpenAlex/arXiv search, normalization and identifier deduplication.
4. Validated, source-grounded personalization and durable per-local-day feed generation.
5. Android integration preserving cards/theme, settings/import, durable knowledge and cache.
6. Automated backend tests, Android compile and integration review; document setup and limitations.

## REST contract (worker must implement exactly)
All private endpoints require `Authorization: Bearer <server app token>`. Prefix `/v1`. JSON uses snake_case. Errors: `{ "error": {"code": "...", "message": "German safe explanation"}}`. GET /health is public and exposes no private information.

- GET/PUT `/profile`: {bachelor_subject:string, focus_areas:string[], interests:string[], familiar_topics:string[], paper_count:int(5..10), lookback_days:int(7..30)}. Explicit familiar_topics are confirmed knowledge; document-derived concepts remain candidates until confirmation.
- GET `/knowledge`: Knowledge[]. Knowledge={id:string,name:string,description:string,status:"confirmed"|"candidate"|"learning",connections:string[],source_paper_id:string|null,document_ids:string[]}.
- PUT `/knowledge/{id}` body {name:string,description:string,status:string}, response Knowledge. DELETE same -> 204.
- POST `/papers/{id}/understood` -> Knowledge, idempotent; preserve paper provenance.
- GET `/documents`: Document[]. Document={id:string,filename:string,status:string,message:string|null,concepts:Knowledge[]}.
- POST `/documents` multipart `file`: import/extract locally, NO LLM call. -> Document.
- POST `/documents/{id}/analyze` body {consent:bool} -> Document. External processing only with consent=true. GET `/documents/{id}` -> Document; DELETE ->204, remove document chunks/evidence and update knowledge safely.
- GET `/feed?timezone=Europe%2FBerlin`: latest persisted Feed (or empty); no model calls.
- POST `/feed/generate` body {timezone:string} -> Feed. At most one successful regular issue per local calendar day; persistent unique date constraint/lease, no duplicate parallel runs, bounded retry cooldown after failures, preserve last successful feed on failure. empty actual search can produce successful empty issue, never fabricate news.
- GET `/papers/{id}` -> Paper.
- POST `/llm/test` -> {ok:bool,message:string}.

Feed={date:string,status:string,items:FeedItem[],message:string|null,next_retry_at:string|null}.
FeedItem={id:string,paper_id:string,title:string,summary:string,prior_knowledge:Knowledge[],new_insight:string,connection:string,relevance:string,takeaway:string,abstract_based:bool,source_url:string,published_at:string,category:string}.
Paper={id:string,doi:string|null,arxiv_id:string|null,title:string,authors:string[],abstract:string,published_at:string,field:string,source:string,url:string,pdf_url:string|null,review_status:string}.

For POST /llm/test, /documents/{id}/analyze, /feed/generate optional request headers: `X-LLM-Key`, `X-LLM-Model`, `X-LLM-Base-URL`. Without these use server environment. Never persist or log user keys. Reject user-key transfer on plaintext connections (trust forwarded proto ONLY from explicitly configured trusted proxy). Provider URLs must be HTTPS and in operator-configured allowlist; reject redirects/private network SSRF destinations. Backend HTTPS required from Android whenever transmitting user LLM key. Operator-owned server key permits local loopback debug HTTP development only; app token still required.

## Security and scientific evidence
Bind server to loopback by default; fail closed without configured app token. Store SQLite/uploads outside source tree in ignored private directory, random server-generated IDs/paths, bounded upload size/pages/text/chunks/tokens/concepts/LLM calls. No PDF text or secrets in logs. Text extraction in bounded subprocess with timeout; encrypted/malformed/image-only PDFs receive explicit errors. Delete uploaded originals if not needed; delete all document evidence on removal and do not silently remove independently confirmed knowledge.
Only confirmed relevant knowledge enters claims of prior knowledge. Validate model concept IDs against retrieval; unknown IDs and absent evidence are rejected. Paper text is untrusted data. Abstract-only explanations clearly labelled. No invented results or padding to paper_count. Limit retrieval and request context. Model errors are safe structured responses; rate limits/timeouts/invalid keys/balance handled. Exact scientific truth cannot be proved by JSON validation; preserve source and explain this limitation.

## Sources checked
- https://help.openalex.org/api/authentication/ (anonymous small budget; optional operator API key; authentication via Bearer).
- https://help.openalex.org/api/ (works endpoint and publication-date filtering).
- https://info.arxiv.org/help/api/user-manual.html
- https://info.arxiv.org/help/api/tou.html (one connection, at least 3 seconds between calls).
