

# CHD-EPICS (ECGCare)

A web app for doctors to upload ECG scans and get an ML-assisted screening for congenital heart defects (CHD) — classifying scans as **Normal**, **ASD**, or **VSD**.

The system has three parts that talk to each other over HTTP:

```
frontend  --->  backend (Spring Boot)  --->  ml-service (FastAPI)
(static)        auth, patients, scans,       image classification
                 storage, business logic       (ConvNeXt model)
```

## Project structure

```
.
├── backend/          Spring Boot API — auth, patients, scans, MinIO storage, ML orchestration
├── frontend/          Static HTML/CSS/JS doctor dashboard (no build step)
├── ml-service/        FastAPI service serving the trained CHD classifier
├── docs/              Full documentation, ML service guide, and audit notes
├── start-all-services.ps1   Convenience script to run all three services locally
└── .gitignore
```

## Tech stack

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, H2 (local) / PostgreSQL (production) |
| ML service | Python, FastAPI, PyTorch, Hugging Face Transformers (ConvNeXt) |
| Frontend | Plain HTML/CSS/JavaScript |
| File storage | MinIO (S3-compatible) |
| Auth | JWT (access + refresh tokens) |

## Running locally

**Prerequisites**: JDK 21, Maven, Python 3.10+, and MinIO (`minio.exe`/`mc.exe`, not included in the repo — download from [min.io](https://min.io/download)).

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
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | backend | Database connection (H2 locally, PostgreSQL in production) |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | backend | Object storage for uploaded scans |
| `JWT_SECRET` | backend | Signs access/refresh tokens |
| `ML_SERVICE_URL` | backend | Base URL of the ML service |
| `MODEL_PATH` | ml-service | Path to the trained model directory (defaults to `./models/chd-classifier`) |

Never commit `.env` files or real secrets — see `.gitignore`.

## Deployment

| Component | Host |
|---|---|
| Backend | [Render](https://render.com), built from `backend/Dockerfile` |
| Frontend | [Vercel](https://vercel.com), proxies `/api/*` to the Render backend (`frontend/vercel.json`) |
| ML service | Deployable via `ml-service/Dockerfile` (e.g. Render, Hugging Face Spaces) |

## Documentation

- [`docs/COMPLETE_DOCUMENTATION.md`](docs/COMPLETE_DOCUMENTATION.md) — full system documentation
- [`docs/ML_SERVICE_GUIDE.md`](docs/ML_SERVICE_GUIDE.md) — ML service internals and model details
- [`docs/fixes.md`](docs/fixes.md) — known issues and cleanup log from the last audit
