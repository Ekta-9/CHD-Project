# HeartTrace: Congenital Heart Defect Screening

A web app for doctors to upload a patient's chest X-ray and receive an ML-assisted screening for congenital heart defects (CHD) — classifying each scan as **Normal**, **ASD** (Atrial Septal Defect), or **VSD** (Ventricular Septal Defect), alongside encrypted patient records and printable diagnostic reports.

The classifier is a custom dual-branch ConvNeXt-Base model, achieving **82.6% test accuracy** and a **macro F1 score of 0.83**.

---

## Results

| Metric | Value |
|---|---|
| **Test Accuracy** | **82.6%** |
| Macro F1 | 0.83 |

---

## Architecture

```
Chest X-ray upload (.jpg / .png)
        |
        v
[Frontend]                      Doctor dashboard (HTML/CSS/JS)
        |
        v
[Backend — Spring Boot]  :8080
 JWT auth · encrypted patient
 records · scan storage
        |
        v
[Object Storage]                MinIO (dev) / Supabase Storage (prod)
 Stores the uploaded X-ray
        |
        v  (doctor triggers analysis)
[ML Service — FastAPI]  :8000
 Dual-branch ConvNeXt-Base
 -> Normal / ASD / VSD + confidence
        |
        v
[Backend]                       Persists the prediction
        |
        v
[Patient Report]                Diagnosis history, printable/PDF
```

---

## Component Overview

### Backend — Spring Boot

Handles doctor authentication (JWT access + refresh tokens), encrypted patient records, and orchestrates scan storage and ML analysis requests.

Patient-identifying data is encrypted per record: each patient gets a unique AES data-encryption key, itself wrapped with the doctor's RSA public key — so only that doctor's authenticated session (holding the matching private key) can decrypt it. Patient, scan, and prediction actions are audit-logged.

### ML Service — Dual-Branch ConvNeXt-Base

A FastAPI service wrapping a custom dual-branch ConvNeXt-Base classifier. Each uploaded chest X-ray is classified into one of three categories — Normal, ASD, or VSD — with a confidence score.

### Frontend — Plain HTML/CSS/JS

A doctor-facing dashboard: patient records, X-ray upload, one-click ML analysis with a loading animation, and printable patient reports summarizing diagnosis history.

---

## Project structure

```
.
├── backend/          Spring Boot API — auth, patients, scans, object storage, ML orchestration
├── frontend/          Static HTML/CSS/JS doctor dashboard (no build step)
├── ml-service/        FastAPI service serving the trained classifier
├── docs/              System and ML service documentation
├── start-all-services.ps1   Convenience script to run all three services locally
└── .gitignore
```

## Tech stack

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, H2 (local) / PostgreSQL via Supabase (production) |
| ML service | Python, FastAPI, PyTorch, Hugging Face Transformers (dual-branch ConvNeXt-Base) |
| Frontend | Plain HTML/CSS/JavaScript |
| File storage | S3-compatible object storage (AWS SDK v2) — local MinIO in dev, Supabase Storage in production |
| Auth | JWT (access + refresh tokens) |

## Running locally

**Prerequisites**: JDK 21, Maven, Python 3.10+, and MinIO (`minio.exe`, not included in the repo — download from [min.io](https://min.io/download)) for local object storage.

Quickest way — run everything at once:

```powershell
./start-all-services.ps1
```

This starts MinIO (`:9000`), the ML service (`:8000`), the backend (`:8080`), and a static file server for the frontend (`:3000`).

Or run each service by hand:

```bash
# ML service
cd ml-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

# Backend
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
python -m http.server 3000
```

## Environment variables

Copy the defaults in `backend/src/main/resources/application.yml` and `ml-service/main.py` and override as needed. Key variables:

| Variable | Used by | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | backend | Database connection (H2 locally, Supabase PostgreSQL in production) |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` / `MINIO_REGION` | backend | S3-compatible object storage for uploaded scans (local MinIO or Supabase Storage — variable names kept for backward compatibility) |
| `JWT_SECRET` | backend | Signs access/refresh tokens |
| `ML_SERVICE_URL` | backend | Base URL of the ML service |
| `MODEL_PATH` | ml-service | Path to the trained model directory (defaults to `./models/chd-classifier`) |


## Deployment

| Component | Host |
|---|---|
| Backend | [Render](https://render.com), built from `backend/Dockerfile` |
| Database | [Supabase](https://supabase.com) PostgreSQL |
| Object storage | [Supabase](https://supabase.com) Storage (S3-compatible) |
| Frontend | [Vercel](https://vercel.com), proxies `/api/*` to the Render backend (`frontend/vercel.json`) |
| ML service | Deployable via `ml-service/Dockerfile` (e.g. Render, Hugging Face Spaces) |

## Documentation

- [`docs/COMPLETE_DOCUMENTATION.md`](docs/COMPLETE_DOCUMENTATION.md) — full system documentation
- [`docs/ML_SERVICE_GUIDE.md`](docs/ML_SERVICE_GUIDE.md) — ML service internals and model details

## Documents
