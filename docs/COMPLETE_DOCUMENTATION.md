# CHD-EPICS: Complete Documentation

**Chronic Heart Defects - ECG Prediction and Image Classification System**

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture & Design](#architecture--design)
3. [Quick Start Guides](#quick-start-guides)
4. [Setup Instructions](#setup-instructions)
5. [API Documentation](#api-documentation)
6. [Integration Guides](#integration-guides)
7. [Frontend Documentation](#frontend-documentation)
8. [Backend Documentation](#backend-documentation)
9. [ML Service Documentation](#ml-service-documentation)
10. [Testing & Results](#testing--results)
11. [Project Status](#project-status)
12. [Troubleshooting](#troubleshooting)

---

# Project Overview

## What is CHD-EPICS?

CHD-EPICS (Chronic Heart Defects - ECG Prediction and Image Classification System) is a healthcare application that:
- Manages doctor authentication and authorization
- Stores and manages encrypted patient data
- Handles ECG scan uploads and storage
- Integrates with ML service for heart defect prediction
- Maintains audit logs for compliance
- Implements role-based access control for patient data

## Technology Stack

### Backend (Java/Spring Boot)
- **Framework**: Spring Boot 3.5.7
- **Java Version**: 21
- **Security**: Spring Security + JWT (JJWT 0.11.5)
- **Database**: PostgreSQL with Flyway migrations (H2 for development)
- **ORM**: Spring Data JPA / Hibernate
- **Object Storage**: MinIO 8.5.9
- **Caching**: Redis (Spring Data Redis)
- **Password Hashing**: Argon2
- **Build Tool**: Maven

### ML Service (Python)
- **Framework**: FastAPI
- **ML Library**: Transformers (Hugging Face)
- **Model**: ConvNeXt (Vision Transformer)
- **Image Processing**: PIL (Pillow)
- **Deep Learning**: PyTorch

### Frontend
- **Technology**: HTML, CSS, JavaScript (Vanilla JS)
- **Architecture**: Modular JavaScript with API wrapper
- **Features**: Patient management, scan upload, ML prediction display, report generation

---

# Architecture & Design

## High-Level Architecture

```
┌─────────────────┐
│   Web Client    │
│  (Frontend)     │
└────────┬────────┘
         │ HTTPS/REST API
         │
┌────────▼─────────────────────────────────────┐
│         Spring Boot Backend                   │
│  ┌─────────────────────────────────────────┐ │
│  │  Controllers (REST Endpoints)           │ │
│  └──────────────┬──────────────────────────┘ │
│                 │                            │
│  ┌──────────────▼──────────────────────────┐ │
│  │  Service Layer (Business Logic)         │ │
│  └──────────────┬──────────────────────────┘ │
│                 │                            │
│  ┌──────────────▼──────────────────────────┐ │
│  │  Repository Layer (Data Access)         │ │
│  └──────────────┬──────────────────────────┘ │
│                 │                            │
└─────────────────┼────────────────────────────┘
                  │
    ┌─────────────┼─────────────┬──────────────┐
    │             │             │              │
┌───▼───┐   ┌─────▼─────┐  ┌───▼────┐  ┌─────▼────┐
│PostgreSQL│ │   Redis   │  │ MinIO  │  │ML Service│
│Database │ │  (Cache)  │  │(Storage)│  │ (FastAPI)│
└─────────┘ └───────────┘  └────────┘  └──────────┘
```

## Database Design

### Core Entities

1. **Doctor Management**
   - `doctor` - Basic doctor information
   - `doctor_auth` - Authentication credentials
   - `doctor_crypto` - Cryptographic keys for encryption

2. **Patient Management**
   - `patient` - Encrypted patient data
   - `patient_key` - Data encryption keys (per doctor-patient pair)
   - `patient_access` - Role-based access control

3. **ECG Scan Management**
   - `ecg_scan` - Scan metadata and MinIO storage references

4. **ML Results**
   - `ml_result` - Prediction results with confidence scores

5. **Audit & Session**
   - `session` - User session tracking
   - `audit_log` - Comprehensive audit trail

## Security Architecture

### Multi-Layer Encryption
- **Patient Data**: Encrypted with AES-GCM using DEK (Data Encryption Key)
- **Key Management**: Each doctor has encrypted copy of DEK wrapped with their public key
- **Password Security**: Argon2 hashing
- **Authentication**: JWT tokens (15 min access, 7 day refresh)

### Access Control
- **RBAC**: Owner, Editor, Viewer roles
- **Access Tracking**: Records who granted access and when
- **Audit Logging**: All operations logged for compliance

---

# Quick Start Guides

## Quick Start - All Services in 4 Steps

### Step 1: Start MinIO (Docker)
```powershell
docker start minio
# Or create if doesn't exist:
docker run -d --name minio -p 9000:9000 -p 9001:9001 -e "MINIO_ROOT_USER=minioadmin" -e "MINIO_ROOT_PASSWORD=minioadmin" minio/minio server /data --console-address ":9001"
```

### Step 2: Start ML Service
```powershell
cd CHD-EPICS\ml-service
python main.py
```
**Wait**: 10-30 seconds for model to load

### Step 3: Start Backend
```powershell
cd CHD-EPICS\backend
.\mvnw.cmd spring-boot:run
```
**Wait**: 30-60 seconds for Spring Boot to start

### Step 4: Start Frontend
```powershell
cd CHD-EPICS\frontend
python -m http.server 3000
```

## Service URLs

- **Frontend**: http://localhost:3000/main.html
- **Backend API**: http://localhost:8080/api
- **ML Service**: http://localhost:8000
- **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin)

## Quick Health Checks

```powershell
# Backend
Invoke-RestMethod -Uri "http://localhost:8080/api/health"

# ML Service
Invoke-RestMethod -Uri "http://localhost:8000/health"

# MinIO
Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live"
```

---

# Setup Instructions

## Backend Setup

### Prerequisites
1. **Java 21** - Install JDK 21
2. **PostgreSQL** - Version 12 or higher (or use H2 for development)
3. **Maven** - Or use included `mvnw` wrapper

### Database Setup

**For PostgreSQL:**
```sql
CREATE DATABASE ecgcare;
CREATE USER ecguser WITH PASSWORD 'ecgpass';
GRANT ALL PRIVILEGES ON DATABASE ecgcare TO ecguser;
```

**For H2 (Development):**
- Automatically created at: `backend/data/ecgcare.mv.db`
- Access via: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/ecgcare`
- Username: `sa`, Password: (empty)

### Configuration

Update `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecgcare
    username: ecguser
    password: ecgpass

ml:
  service-url: http://localhost:8000
  predict-endpoint: /predict

minio:
  endpoint: http://localhost:9000
  accessKey: minio
  secretKey: minio12345
  bucket: ecg-bucket
```

### Running the Application

```bash
cd backend
./mvnw spring-boot:run
```

## ML Service Setup

### Prerequisites
- Python 3.8+
- pip

### Install Dependencies

```bash
cd ml-service
pip install -r requirements.txt
```

### Model Setup

Place your model files in:
```
ml-service/models/chd-classifier/
├── config.json
├── model.safetensors
├── preprocessor_config.json
└── training_args.bin
```

### Configuration

Create `.env` file:
```env
MODEL_PATH=./models/chd-classifier
```

### Start Service

```bash
cd ml-service
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## MinIO Setup

### Using Docker (Recommended)

```powershell
docker run -d --name minio -p 9000:9000 -p 9001:9001 `
  -e "MINIO_ROOT_USER=minio" `
  -e "MINIO_ROOT_PASSWORD=minio12345" `
  minio/minio server /data --console-address ":9001"
```

### Access Console
- URL: http://localhost:9001
- Login: minio / minio12345

### Create Bucket
1. Log into MinIO Console
2. Click "Buckets" → "Create Bucket"
3. Bucket Name: `ecg-bucket`

**Note**: Backend will auto-create bucket on first startup if configured.

## Frontend Setup

### Prerequisites
- Python 3.x (for HTTP server)

### Start Frontend Server

```powershell
cd frontend
python -m http.server 3000
```

### Access
- URL: http://localhost:3000/main.html

---

# API Documentation

## Base URL
- **Backend**: `http://localhost:8080/api`
- **ML Service**: `http://localhost:8000`

## Authentication

All endpoints (except auth endpoints) require JWT Bearer token:
```
Authorization: Bearer <access_token>
```

## Authentication APIs

### Register Doctor
```
POST /api/auth/register
Content-Type: application/json

{
  "email": "doctor@example.com",
  "password": "SecurePassword123!",
  "fullName": "Dr. John Smith",
  "phone": "+1234567890"
}
```

### Login
```
POST /api/auth/login
Content-Type: application/json

{
  "email": "doctor@example.com",
  "password": "SecurePassword123!"
}
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 900,
    "tokenType": "Bearer"
  }
}
```

### Refresh Token
```
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

## Patient Management APIs

### Create Patient
```
POST /api/patients
Authorization: Bearer <token>
Content-Type: application/json

{
  "patientData": {
    "name": "Jane Doe",
    "age": 45,
    "gender": "F",
    "dateOfBirth": "1979-05-20",
    "medicalHistory": "Hypertension, Diabetes",
    "diagnosis": "Suspected CHD",
    "notes": "Patient requires further examination"
  }
}
```

### List Patients
```
GET /api/patients?page=0&size=20&sort=createdAt&order=desc
Authorization: Bearer <token>
```

### Get Patient
```
GET /api/patients/{patientId}
Authorization: Bearer <token>
```

### Update Patient
```
PUT /api/patients/{patientId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "patientData": { ... }
}
```

### Delete Patient
```
DELETE /api/patients/{patientId}
Authorization: Bearer <token>
```

## ECG Scan APIs

### Upload Scan
```
POST /api/scans/upload?patientId={patientId}
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: [binary image data]
```

### Get Scan
```
GET /api/scans/{scanId}
Authorization: Bearer <token>
```

### Download Scan
```
GET /api/scans/{scanId}/download
Authorization: Bearer <token>
```

### List Patient Scans
```
GET /api/patients/{patientId}/scans?page=0&size=20
Authorization: Bearer <token>
```

## ML Prediction APIs

### Predict from Scan
```
POST /api/ml/predict/{scanId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "modelVersion": "v1.0",
  "threshold": 0.5
}
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "resultId": "uuid",
    "scanId": "uuid",
    "patientId": "uuid",
    "modelVersion": "v1.0",
    "predictedLabel": "ASD",
    "confidenceScore": 0.9234,
    "classProbabilities": {
      "Normal": 0.1234,
      "ASD": 0.9234,
      "VSD": 0.0766
    },
    "createdAt": "2024-01-15T11:20:00Z"
  }
}
```

### Get Prediction Result
```
GET /api/ml/results/{resultId}
Authorization: Bearer <token>
```

### List Patient Predictions
```
GET /api/patients/{patientId}/predictions?page=0&size=20
Authorization: Bearer <token>
```

## Access Management APIs

### Share Patient Access
```
POST /api/patients/{patientId}/share
Authorization: Bearer <token>
Content-Type: application/json

{
  "recipientDoctorId": "uuid",
  "role": "viewer"
}
```

### Update Access Role
```
PUT /api/patients/{patientId}/access/{doctorId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "role": "editor"
}
```

### Revoke Access
```
DELETE /api/patients/{patientId}/access/{doctorId}
Authorization: Bearer <token>
```

## ML Service APIs

### Health Check
```
GET http://localhost:8000/
```

### Predict
```
POST http://localhost:8000/predict
Content-Type: application/json

{
  "scan_id": "uuid",
  "image_data": "base64-encoded-image-string"
}
```

**Response:**
```json
{
  "scan_id": "uuid",
  "prediction": "ASD",
  "confidence_score": 0.7816,
  "class_probabilities": {
    "Normal": 0.1234,
    "ASD": 0.7816,
    "VSD": 0.0950
  },
  "status": "COMPLETED"
}
```

## Error Responses

All error responses follow this format:
```json
{
  "status": "error",
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {}
  },
  "timestamp": "2024-01-15T11:45:00Z",
  "path": "/api/patients/123"
}
```

### Common Error Codes

| HTTP Status | Error Code | Description |
|------------|------------|-------------|
| 400 | `BAD_REQUEST` | Invalid request data |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 404 | `NOT_FOUND` | Resource not found |
| 409 | `CONFLICT` | Resource conflict (e.g., duplicate email) |
| 500 | `INTERNAL_ERROR` | Server error |

---

# Integration Guides

## Backend-ML Service Integration

### Architecture
```
Java Backend (Spring Boot) → HTTP POST → ML Service (FastAPI) → ConvNeXt Model
```

### Integration Flow

1. Doctor requests prediction via `/api/ml/predict/{scanId}`
2. Backend downloads scan from MinIO object storage
3. Backend converts image to base64 encoding
4. Backend calls ML service at `http://localhost:8000/predict`
5. ML service processes image using ConvNeXt model
6. ML service returns prediction with confidence scores
7. Backend saves result to database and returns to client

### Configuration

**Backend (`application.yml`):**
```yaml
ml:
  service-url: http://localhost:8000
  predict-endpoint: /predict
  connect-timeout-seconds: 5
  read-timeout-seconds: 60
  max-retries: 3
  retry-delay-seconds: 2
  max-image-size-bytes: 10485760  # 10MB
```

**ML Service (`.env`):**
```env
MODEL_PATH=./models/chd-classifier
```

### Model Classes

The ConvNeXt model classifies images into **3 classes**:
1. **Normal** (Index: 0) - No congenital heart defect detected
2. **ASD** (Index: 1) - Atrial Septal Defect
3. **VSD** (Index: 2) - Ventricular Septal Defect

### Retry Logic

The backend implements automatic retry with exponential backoff:
- **Max retries**: 3 attempts
- **Base delay**: 2 seconds
- **Backoff**: Exponential (2s, 4s, 8s)
- **Retries on**: 5xx errors, timeouts, connection errors

## Frontend-Backend Integration

### API Configuration

The frontend uses `api.js` module with base URL:
```javascript
const API_BASE_URL = 'http://localhost:8080/api';
```

### Authentication Flow

1. **Login**: Store tokens in sessionStorage
2. **API Requests**: Automatically include Bearer token
3. **Token Refresh**: Automatic refresh on 401 errors

### Example Usage

```javascript
// Login
const result = await API.login({ email, password });
sessionStorage.setItem('accessToken', result.data.accessToken);

// Create Patient
const patient = await API.createPatient({
  name: "John Doe",
  age: 45,
  gender: "M"
});

// Upload Scan
const scan = await API.uploadScan(file, patientId);

// Get Prediction
const prediction = await API.predictFromScan(scan.scanId);
```

---

# Frontend Documentation

## File Structure

```
frontend/
├── main.html          # Main dashboard
├── login.html         # Login page
├── signup.html        # Registration page
├── thankyou.html      # Logout page
├── styles.css         # Styling
├── main.js            # Main orchestrator
├── api.js             # API communication wrapper
├── INTEGRATION_GUIDE.md
└── QUICK_REFERENCE.md
```

## Features

### Authentication
- Login/Signup with backend integration
- JWT token management
- Automatic token refresh
- Session persistence

### Patient Management
- Create, Read, Update, Delete patients
- Patient list with pagination
- Patient details modal
- Search and filter

### Scan Management
- Upload ECG scans (JPG, PNG)
- View scan images
- Download scans
- Delete scans

### ML Predictions
- Trigger predictions on scans
- Display prediction results
- Show confidence scores
- Display class probabilities

### Report Generation
- Generate medical reports
- Include patient data, scans, and predictions
- Export functionality

## API Quick Reference

```javascript
// Authentication
await API.register({ email, password, fullName, phone });
await API.login({ email, password });
await API.logout();
const user = await API.getCurrentUser();

// Patients
const patient = await API.createPatient(patientData);
const patients = await API.listPatients({ page: 0, size: 20 });
const patient = await API.getPatient(patientId);
await API.updatePatient(patientId, patientData);
await API.deletePatient(patientId);

// Scans
const scan = await API.uploadScan(file, patientId);
const scans = await API.getPatientScans(patientId);
const blob = await API.downloadScan(scanId);

// Predictions
const result = await API.predictFromScan(scanId);
const predictions = await API.getPatientPredictions(patientId);
```

---

# Backend Documentation

## Project Structure

```
backend/
├── src/
│   └── main/
│       ├── java/com/ecgcare/backend/
│       │   ├── BackendApplication.java
│       │   ├── config/          # Configuration classes
│       │   ├── controller/      # REST controllers
│       │   ├── service/          # Business logic
│       │   ├── repository/      # Data access
│       │   ├── entity/           # JPA entities
│       │   ├── dto/              # Data transfer objects
│       │   └── exception/       # Exception handling
│       └── resources/
│           ├── application.yml   # Configuration
│           └── db/migration/     # Flyway migrations
├── data/                         # H2 database files
└── pom.xml                       # Maven dependencies
```

## Key Components

### Controllers
- `AuthController` - Authentication endpoints
- `PatientController` - Patient CRUD operations
- `ScanController` - Scan upload/download
- `MLController` - ML prediction endpoints
- `PatientAccessController` - Access management

### Services
- `AuthService` - Authentication and session management
- `PatientService` - Patient business logic with encryption
- `ScanService` - File handling and MinIO operations
- `MLService` - ML service integration
- `EncryptionService` - AES-GCM encryption/decryption

### Security
- JWT-based authentication
- Spring Security configuration
- CORS enabled for frontend
- Role-based access control

## Database Schema

All tables are automatically created by Flyway migrations on first startup.

### Key Tables
- `doctor` - Doctor accounts
- `doctor_auth` - Authentication credentials
- `doctor_crypto` - Cryptographic keys
- `patient` - Encrypted patient data
- `patient_key` - Encryption keys
- `patient_access` - Access permissions
- `ecg_scan` - Scan metadata
- `ml_result` - Prediction results
- `session` - User sessions
- `audit_log` - Audit trail

---

# ML Service Documentation

## Model Integration

### Model Files Required

```
ml-service/models/chd-classifier/
├── config.json              # Model configuration
├── model.safetensors        # Trained weights
├── preprocessor_config.json # Image preprocessing
└── training_args.bin        # Training arguments (optional)
```

### Model Setup

1. Place model files in `models/chd-classifier/` directory
2. Set `MODEL_PATH` environment variable or in `.env` file
3. Start service - model loads automatically

### Model Classes

- **Normal** (Class 0) - No congenital heart defect
- **ASD** (Class 1) - Atrial Septal Defect
- **VSD** (Class 2) - Ventricular Septal Defect

### API Endpoints

**Health Check:**
```
GET http://localhost:8000/
```

**Predict:**
```
POST http://localhost:8000/predict
Content-Type: application/json

{
  "scan_id": "uuid",
  "image_data": "base64-encoded-image"
}
```

### Response Format

```json
{
  "scan_id": "uuid",
  "prediction": "ASD",
  "confidence_score": 0.7816,
  "class_probabilities": {
    "Normal": 0.1234,
    "ASD": 0.7816,
    "VSD": 0.0950
  },
  "status": "COMPLETED"
}
```

---

# Testing & Results

## Test Results Summary

### Backend Tests
- ✅ Health Check - PASSED
- ✅ Registration - PASSED
- ✅ Login - PASSED
- ✅ Patient CRUD - PASSED
- ✅ Scan Upload - PASSED (with MinIO)
- ✅ ML Prediction - PASSED

### ML Service Tests
- ✅ Health Endpoint - PASSED
- ✅ Model Loading - PASSED
- ✅ Prediction Endpoint - PASSED

### Integration Tests
- ✅ End-to-End Flow - PASSED
- ✅ Data Persistence - PASSED
- ✅ Error Handling - PASSED

## End-to-End Test Flow

1. **Register Doctor** → Success
2. **Login** → JWT tokens received
3. **Create Patient** → Encrypted data stored
4. **Upload Scan** → Stored in MinIO
5. **Request Prediction** → ML service processes
6. **View Results** → Displayed in frontend
7. **Data Persistence** → Verified in database

---

# Project Status

## Current Status: ✅ ALL SYSTEMS OPERATIONAL

### Completed Components

#### Backend (Spring Boot) - ✅ COMPLETE
- All 22 API endpoints implemented
- Authentication with JWT
- Patient data encryption
- MinIO integration
- ML service integration
- Database schema with Flyway
- Comprehensive error handling

#### ML Service (FastAPI) - ✅ COMPLETE
- ConvNeXt model integration
- Model loading from local files
- Image preprocessing
- Prediction endpoint
- Health check endpoint
- Error handling

#### Frontend - ✅ COMPLETE
- Modern UI/UX
- Authentication flow
- Patient management
- Scan upload and management
- ML prediction display
- Report generation
- Account details

#### MinIO - ✅ COMPLETE
- Object storage configured
- Bucket auto-creation
- Integration with backend

### Integration Status

- ✅ Frontend ↔ Backend - Working
- ✅ Backend ↔ ML Service - Working
- ✅ Backend ↔ MinIO - Working
- ✅ Backend ↔ Database - Working

### File Sizes

- **Total Project Size**: 475.39 MB
- **Files to Commit**: 173 files, 335 MB
- **Largest File**: ML model (334.09 MB)

---

# Troubleshooting

## Common Issues

### Backend Not Starting

**Check:**
- Java 21+ installed: `java -version`
- Port 8080 available: `netstat -ano | findstr :8080`
- Database connection in `application.yml`

**Solution:**
```powershell
cd backend
.\mvnw.cmd clean install
.\mvnw.cmd spring-boot:run
```

### ML Service Not Responding

**Check:**
- Python installed: `python --version`
- Dependencies installed: `pip install -r requirements.txt`
- Model files exist in `models/chd-classifier/`
- Port 8000 available

**Solution:**
```powershell
cd ml-service
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

### MinIO Connection Issues

**Check:**
- Docker Desktop running
- Container running: `docker ps | findstr minio`
- Ports 9000/9001 available

**Solution:**
```powershell
docker start minio
# Or create new:
docker run -d --name minio -p 9000:9000 -p 9001:9001 `
  -e "MINIO_ROOT_USER=minio" `
  -e "MINIO_ROOT_PASSWORD=minio12345" `
  minio/minio server /data --console-address ":9001"
```

### CORS Errors

**Check:**
- Backend CORS configuration in `SecurityConfig.java`
- Frontend API URL: `http://localhost:8080/api`
- Browser console for specific errors

**Solution:**
- Backend CORS is already configured to allow all origins for development

### 401 Unauthorized

**Check:**
- Token stored in sessionStorage
- Token not expired (15 minutes for access token)
- Token included in Authorization header

**Solution:**
- Logout and login again
- Check backend logs for JWT errors

### File Upload Fails

**Check:**
- File size < 10MB
- File is image format (JPG, PNG)
- MinIO is running
- Patient selected

**Solution:**
- Check `application.yml` for `max-file-size: 10MB`
- Verify MinIO is accessible
- Check backend logs

### Prediction Fails

**Check:**
- ML Service is running
- Model loaded correctly
- Backend can reach ML service
- Scan uploaded successfully

**Solution:**
- Check ML service logs
- Verify ML service URL in `application.yml`
- Test ML service directly: `http://localhost:8000/health`

---

# Data Flow Diagrams

## User Registration Flow

```
1. Doctor registers → Password hashed with Argon2
2. RSA key pair generated → Private key encrypted with KEK
3. Keys stored in doctor_crypto table
4. Login → JWT tokens issued (access + refresh)
5. Session created in session table
```

## Patient Data Creation Flow

```
1. Doctor creates patient record
2. Generate random DEK (AES-256)
3. Encrypt patient data with DEK using AES-GCM
4. Wrap DEK with doctor's public key
5. Store encrypted payload and keys
6. Create access record (role: 'owner')
```

## ML Prediction Flow

```
1. Doctor uploads ECG scan
2. File stored in MinIO → storage_uri generated
3. Metadata saved in ecg_scan table
4. Backend calls ML Service (/predict endpoint)
5. ML Service processes with ConvNeXt model
6. Returns prediction (ASD/VSD) + confidence
7. Result stored in ml_result table
8. Response sent to doctor
```

---

# Configuration Reference

## Backend Configuration (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/ecgcare
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

server:
  port: 8080

jwt:
  secret: your-secret-key
  access-token-ttl: 900  # 15 minutes
  refresh-token-ttl: 604800  # 7 days

ml:
  service-url: http://localhost:8000
  predict-endpoint: /predict
  connect-timeout-seconds: 5
  read-timeout-seconds: 60
  max-retries: 3

minio:
  endpoint: http://localhost:9000
  accessKey: minio
  secretKey: minio12345
  bucket: ecg-bucket
```

## ML Service Configuration (`.env`)

```env
MODEL_PATH=./models/chd-classifier
```

## Frontend Configuration (`api.js`)

```javascript
const API_BASE_URL = 'http://localhost:8080/api';
```

---

# Where Results Are Stored

## Database Storage

**Location**: `backend/data/ecgcare.mv.db` (H2 Database)

**Table**: `ml_result`
- Stores prediction metadata
- Includes confidence scores
- Links to patient and scan

**Access**: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/ecgcare`
- Username: `sa`
- Password: (empty)

## MinIO Object Storage

**Location**: Docker container, data in `backend/minio-data/`

**Bucket**: `ecg-bucket`

**Storage Path**: `{patient_id}/{scan_id}/{filename}`

**Access**: http://localhost:9001
- Login: minio / minio12345

---

# Git Push Guide

## Repository Information
- **GitHub URL**: https://github.com/Ekta-9/Epics-CHD
- **Remote**: Already configured
- **Branch**: main

## Steps to Push

### 1. Update .gitignore
Ensure sensitive files are excluded:
- Database files (`.db`, `.mv.db`)
- Log files (`*.log`)
- Environment files (`.env`)
- MinIO data (`backend/minio-data/`)
- Build artifacts (`target/`, `__pycache__/`)

### 2. Stage Changes
```bash
git add .
```

### 3. Commit
```bash
git commit -m "Complete CHD-EPICS system with all features"
```

### 4. Push
```bash
git push origin main
```

## What Will Be Committed

✅ **Will be committed:**
- All source code
- Configuration files
- Documentation
- Service scripts

❌ **Will be ignored:**
- Database files
- Log files
- Environment files
- MinIO data
- Build artifacts

---

# Summary

## Project Overview

CHD-EPICS is a complete healthcare application for managing patient data, ECG scans, and ML-based heart defect predictions. The system features:

- ✅ Secure patient data encryption
- ✅ Role-based access control
- ✅ ML-powered predictions
- ✅ Comprehensive audit logging
- ✅ Modern web interface
- ✅ Complete API documentation

## System Status

**ALL SYSTEMS OPERATIONAL** ✅

- Backend: Fully functional
- ML Service: Fully functional
- Frontend: Fully functional
- MinIO: Configured and working
- Integration: Complete and tested

## Quick Start

1. Start MinIO: `docker start minio`
2. Start ML Service: `cd ml-service && python main.py`
3. Start Backend: `cd backend && ./mvnw.cmd spring-boot:run`
4. Start Frontend: `cd frontend && python -m http.server 3000`
5. Open: http://localhost:3000/main.html

## Documentation Files

This document consolidates all documentation from:
- README.md
- ARCHITECTURE_AND_LLD.md
- API_CONTRACTS.md
- DATAFLOW_DIAGRAM.md
- All setup guides
- All integration guides
- All test results
- All status documents

---

**Last Updated**: 2025-01-XX  
**Version**: 1.0  
**Status**: Production Ready ✅



