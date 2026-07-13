# CHD-EPICS — Issues Found (Exploration Notes)

Consolidated list of everything found while auditing the project, component by component. Nothing in this document has been fixed yet — this is the list to work through, one item at a time.

Severity key: 🔴 Critical &nbsp; 🟠 Significant &nbsp; 🟡 Minor / cleanup &nbsp; ✅ Verified working (no action needed)

---

## 0. Security — rotate immediately

🔴 **Hugging Face token exposed in cleartext in a git remote URL**
- Where: `CHD-EPICS/chd-ml/.git/config` (nested git repo, HF Space clone)
- The remote URL was `https://Ektah:[REDACTED-TOKEN]@huggingface.co/spaces/Ektah/chd-ml`
- The token sat in plaintext on disk with push access to the HF Space.
- **Status**: the local `chd-ml/` clone has been deleted (2026-07-13). The token itself still needs to be rotated at huggingface.co/settings/tokens — deleting the folder does not invalidate it. Use a credential helper instead of embedding tokens in remote URLs going forward.

---

## 1. Database

🔴 **Production has no persistent database.**
- `SPRING_DATASOURCE_URL` on Render = `jdbc:h2:file:./data/ecgcare;...` — an H2 *file* database living inside the container's own filesystem, not a real external database server.
- Render's container filesystem is ephemeral. Every restart, redeploy, or free-tier idle spin-down wipes it — every doctor account, patient, session, audit log, and ML result.
- A past attempt to switch to Supabase Postgres exists in an old crash log but was never completed correctly (see next item).
- **Path forward**: pick a free persistent Postgres (Supabase, Render's free Postgres, etc.), set 4 env vars correctly (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`, username, password). No code changes needed — `pom.xml` already has the Postgres driver and `flyway-database-postgresql`, and the migration SQL is written to be Postgres-compatible.

🟠 **Root-level `backend/application.yml` silently overrides the real config during local runs.**
- Two files exist: `backend/application.yml` (hardcoded values, `h2.console.enabled: true` unconditionally) and `backend/src/main/resources/application.yml` (the intended one, env-var driven, console disabled by default).
- Spring Boot's config precedence (`file:./` beats `classpath:/`) means the root copy wins whenever the working directory is `backend/` (e.g. `mvnw spring-boot:run`, `mvn test`). Confirmed empirically — H2 console came up enabled in a test run with no env vars set.
- Doesn't affect Render/Docker (only the jar is shipped there), but makes local dev behave differently from production and is generally confusing/redundant.

🟡 **H2 console exposed by default in local runs** — direct consequence of the above.

🟡 **`flyway.validate-on-migrate: false`** — schema drift on the DB wouldn't be caught. Low risk today (only one migration file), worth enabling once on a real persistent DB.

🟡 **No explicit connection pool size set** — fine for H2, but free-tier Postgres providers cap concurrent connections low. Should set `maximum-pool-size` explicitly (e.g. 3–5) once migrated.

✅ All repository queries use parameterized JPQL — no SQL injection risk anywhere in the data layer.

---

## 2. Backend APIs

🔴 **Patient data "encryption" doesn't actually protect anything from someone with DB access.**
- Design intent: each patient's data key (DEK) is wrapped with each doctor's personal key pair, so only that doctor can unlock it.
- Reality (`EncryptionService.java`):
  - The doctor's **private key is stored in the database completely unencrypted** (`AuthService.register()` — comment admits *"simplified — storing raw bytes for now... In production, this should use proper encryption"*).
  - The **wrap/unwrap logic doesn't use the private key at all** — it hashes the doctor's *public* key (non-secret by definition) and uses that hash as a symmetric key. Comment admits *"Simplified... In production, use RSA-OAEP with the private key."*
- Net effect: anyone with direct database read access can recompute the same hash from data sitting in the same table and decrypt every patient's medical record — no login, password, or private key required.
- Files: `backend/src/main/java/com/ecgcare/backend/service/EncryptionService.java`, `AuthService.java` (register method, ~line 62-89)

🟠 **"Share patient with another doctor" is broken — it 500-errors for the recipient.**
- `PatientAccessController.shareAccess()` only inserts a `PatientAccess` permission row; it explicitly skips creating the matching `PatientKey` entry (comment: *"simplified — key wrapping would happen here"*).
- When the recipient doctor tries to view the patient, they pass the permission check, then `PatientService.getPatient()` fails to find their `PatientKey` row, throws a generic `RuntimeException`, and the global handler turns it into a 500 Internal Server Error.
- Files: `PatientAccessController.java` (shareAccess), `PatientService.java` (getPatient, ~line 104-150)

🟡 **A few `.orElseThrow()` calls with no exception type** throw a generic Java error instead of the app's own `NotFoundException`, surfacing as a vague 500 instead of a clean 404. (e.g. `PatientAccessController.java` lines using bare `.orElseThrow()`)

🟡 **`AuthController.logout()` has no try/catch.** If the access token has already expired when the user clicks logout, token parsing throws and — unlike every other endpoint — this one isn't wrapped in a safety net, so it likely surfaces as a 500 instead of just quietly ending the session.

✅ ML-service call handling (`MLService.java`) is well built — retries with backoff, distinguishes timeouts vs. 5xx vs. 4xx, clear custom exceptions.

✅ No SQL injection risk — all queries parameterized.

---

## 3. ML Service

🔴 **The service has no authentication and is reachable directly from the public internet.**
- Confirmed live: `https://ektah-chd-ml.hf.space/predict` answers with no login, token, or backend involved at all.
- Docs assume it's "internal/trusted," but it's actually a public URL — anyone can call it directly, bypassing the Java backend's entire permission/auth system.

🟠 **If no image is sent, it silently substitutes its own bundled test image instead of erroring.**
```python
else:
    image = Image.open("test_image.jpg")   # fallback if image_data missing
```
A caller could get back a confident-looking prediction for a scan that was never actually analyzed, with no indication anything went wrong. Concerning for a medical-adjacent tool.

🟠 **If the real trained model can't load, it silently falls back to an unrelated generic model.**
```python
MODEL_PATH_ENV = os.getenv("MODEL_PATH", "google/vit-base-patch16-224-in21k")
```
In that fallback state the class list is also incomplete (defaults to just "ASD, VSD", missing "Normal"). **Currently not happening** — verified live `/health` shows the correct ConvNeXt model with all 3 classes loaded — but it's a silent failure mode waiting for the next misconfigured deploy.

🟡 Error responses include raw internal Python exception text/type names — minor information disclosure (low severity, service holds no sensitive data itself).

🟡 `requirements.txt` pins older library versions (`transformers==4.35.0`, `torch==2.1.0`) than what's actually installed/running (`transformers 4.41.0`, `torch 2.3.0`) — reproducibility gap, not an active bug.

🟡 **Duplicate service**: `ml-service/main.py` and `chd-ml/main.py` are byte-identical — every bug above exists in both copies.

✅ The model itself is legitimate — a real, fully trained 350MB ConvNeXt model, correctly configured with all 3 classes (Normal/ASD/VSD). Verified with a live end-to-end prediction call.

✅ Request/response contract between backend and ML service matches exactly — no integration mismatch.

---

## 4. Frontend

🔴 **Stored XSS via patient fields — can steal another doctor's login session.**
```js
content.innerHTML = `... <p>${p.name}</p> ... <p>${p.notes}</p> ...`;
```
Patient name/medical history/diagnosis/notes (free text a doctor types in) are inserted directly as raw HTML with no escaping. Since patients can be shared between doctors, a crafted value in any of these fields would silently execute in the browser of every doctor who later opens that patient's file. Login tokens live in `sessionStorage` (JS-readable), so this is a realistic path to session/account takeover.
- File: `frontend/main.js`, `viewPatientDetails()` (~line 223-269)

🟠 **Reflected XSS in the patient search box** — same category of bug, lower severity (self-only):
```js
'No patients found matching "<strong>' + query + '</strong>".'
```
- File: `frontend/main.js`, `filterPatients()` (~line 107-108)

🟡 `frontend/main-integrated.html` is a completely empty file (0 bytes) — dead leftover.

🟡 `frontend/test.html` appears to be an alternate/scratch copy of the dashboard (428 lines, same "Doctor Dashboard" title), pulling a font-icon library from a public CDN. Unclear if still in use anywhere.

🟡 `getScanDownloadUrl()` in `api.js` builds an unauthenticated image URL intended for `<img>` tags — but the backend requires a login token for that endpoint, which a plain `<img src>` can't provide. Not currently used anywhere (the real scan viewer correctly fetches via authenticated request + blob), but it's a trap for future use.

✅ The actual scan viewer (`viewScanImage`) does auth correctly — fetches with the login token, then displays.

✅ Login/token-refresh flow in `api.js` is solid — automatically retries once with a refreshed token before forcing re-login.

---

## 5. MinIO / File Storage

🔴 **MinIO credentials likely don't match between the storage server and the app.**
- `backend/Dockerfile` starts the bundled MinIO server with hardcoded, non-overridable values: `MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin` (typed literally, not read from any env var).
- The Spring app's own default (`application.yml`) is `MINIO_ACCESS_KEY:minio` / `MINIO_SECRET_KEY:minio12345` — **different values**.
- Unless someone has manually set Render env vars to override the app's side to match (`minioadmin`/`minioadmin`), every upload/download/delete call would fail with a login error against its own storage server.
- There's also an orphaned, unused twin script (`backend/start.sh`) that does the same job differently (its version *does* respect env var overrides) but isn't actually called by the Dockerfile at all.

🔴 **Uploaded scan files are not persisted in production** (established earlier) — MinIO's data directory (`/tmp/minio-data`) lives inside the same ephemeral Render container as the app, so files are lost on every restart even if the credential issue above is fixed.

🟠 **The stored "checksum" for each scan is fake.**
```java
String checksum = "sha256:" + UUID.randomUUID().toString();
```
Not an actual hash of the file — just a random ID dressed up to look like one. No real way to detect file corruption/tampering despite the column existing for that purpose.
- File: `backend/.../service/ScanService.java`, `uploadScan()`

🟠 **Deleting a scan can silently leave the real file behind forever.**
```java
try {
    minioClient.removeObject(...);
} catch (Exception e) {
    log.error(...);   // logged, then continues anyway
}
scanRepository.delete(scan);
```
If MinIO deletion fails, the app deletes the DB record anyway and reports success — the orphaned file stays in storage indefinitely with nothing left pointing to it.
- File: `ScanService.java`, `deleteScan()`

🟡 File-type validation only trusts the browser-supplied Content-Type header, never inspects actual file bytes — a spoofed upload would be accepted as an "image."

🟡 Uploaded filenames are used as-is in the MinIO object path with no sanitization.

---

## Repo hygiene (low priority, noted while exploring)

- ~~`CHD-EPICS/backend/CHD-EPICS/ml-service/test_image.jpg` — a stray nested path, tracked in git~~ — removed 2026-07-13.
- ~~`ml-service/` and `chd-ml/` are near-duplicate ML services~~ — `chd-ml/` deleted 2026-07-13; `ml-service/` is now the single copy. Production's `ML_SERVICE_URL` still needs to be confirmed against whatever it's actually pointed at (Render-hosted `ml-service/` vs. redeploying to the HF Space from it).
- ~~`train_gan.py`, `filter_and_merge.py`, `synthetic/`, `synthetic_filtered/`, `data_final/`, `checkpoints/`, `quick_clf.pth`~~ — deleted 2026-07-13 (uncommitted GAN/synthetic-data experiment, not integrated into either ML service).
- `COMPLETE_DOCUMENTATION.md` has some stale claims: says Argon2 password hashing (code actually uses BCrypt), lists the wrong GitHub repo URL (`Ekta-9/Epics-CHD` vs. actual `pranavdubey1725/CHD-For-EPICS`), and its "ALL SYSTEMS OPERATIONAL" status table describes manual testing, not an automated test suite (only one real automated test exists — a context-load smoke test).

---

## Current hosting map (for reference)

| Component | Where it lives | Status |
|---|---|---|
| Backend (Spring Boot) | Render, built from `backend/Dockerfile` | Live, responds 200 on `/api/health` |
| Database | H2 file *inside* the Render container (not Supabase, despite an earlier attempt) | Not persistent — wiped on restart |
| Object storage (MinIO) | Bundled inside the same Render container, `/tmp/minio-data` | Not persistent, and likely credential-mismatched (see above) |
| ML service | Two copies: `ml-service/` (Docker) and `chd-ml/` (deployed to Hugging Face Space) | HF Space (`ektah-chd-ml.hf.space`) confirmed live and working |
| Frontend | Vercel, proxies `/api/*` to Render via `vercel.json` rewrite | URL not recorded in repo |
