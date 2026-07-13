# CHD-EPICS: ML Service Guide (Presentation & Supervisor Q&A)

**Use this guide to present the ML component and answer supervisor questions.**

---

## 1. What the ML Service Does (In One Sentence)

The ML service is a **Python FastAPI microservice** that takes an **ECG/medical scan image** (as base64), runs it through a **ConvNeXt image classification model**, and returns a **prediction** (Normal, ASD, or VSD) with **confidence scores** and **per-class probabilities**.

---

## 2. End-to-End Flow: From User to Prediction

```
Doctor (Frontend)  →  Backend (Spring Boot)  →  MinIO (get image)  →  Backend  →  ML Service  →  Model  →  Backend  →  DB  →  Doctor
```

### Step-by-step

1. **Doctor triggers prediction**  
   - Frontend calls: `POST /api/ml/predict/{scanId}` (with JWT).

2. **Backend (MLService.predict)**  
   - Loads the scan from DB, checks doctor has access to the patient.  
   - Calls **ScanService.downloadScan(scanId, doctorId)** to get the image from **MinIO**.  
   - Converts image bytes to **base64**.  
   - Validates size (max 10MB).  
   - Sends **HTTP POST** to ML service: `http://localhost:8000/predict` with body:  
     `{ "scan_id": "<uuid>", "image_data": "<base64>" }`.  
   - Uses **retry logic** (e.g. 3 attempts, exponential backoff) for timeouts/5xx.

3. **ML Service (main.py)**  
   - Receives JSON, decodes base64 → PIL Image.  
   - Converts to RGB if needed.  
   - Preprocesses with **ConvNeXt image processor** (resize, normalize, etc.).  
   - Runs **ConvNeXt** forward pass (PyTorch), gets logits.  
   - Applies **softmax** → probabilities; **argmax** → predicted class.  
   - Reads class names from model config (**id2label**: 0=Normal, 1=ASD, 2=VSD).  
   - Returns JSON:  
     `prediction`, `confidence_score`, `class_probabilities`, `status`.

4. **Backend (continued)**  
   - Parses response, validates `prediction` and `confidence_score`.  
   - Builds **MlResult** entity (patient, scan, modelVersion, predictedLabel, classProbs, threshold, createdBy).  
   - Saves to DB (**ml_result** table), writes **audit log**.  
   - Returns **MlResultResponse** to frontend.

5. **Frontend**  
   - Shows prediction (Normal/ASD/VSD), confidence, and optionally class probabilities.

---

## 3. ML Service Internals (Technical Summary)

### Stack

- **API**: FastAPI  
- **Model**: Hugging Face **Transformers** — **ConvNeXt** for image classification  
- **Runtime**: PyTorch  
- **Image handling**: Pillow (PIL), base64 decode

### Model

- **Type**: ConvNeXt (CNN-style, modern architecture).  
- **Config**: `ml-service/models/chd-classifier/config.json`  
  - **Classes**: `id2label`: 0 → "Normal", 1 → "ASD", 2 → "VSD".  
  - **Input**: 224×224 RGB image (via preprocessor).  
- **Weights**: `model.safetensors` (or `pytorch_model.bin`) in same folder.  
- **Loading**: Uses **AutoImageProcessor** and **AutoModelForImageClassification** (with fallbacks to ConvNeXt/ViT-specific classes).  
- **Config path**: From env `MODEL_PATH` (e.g. `./models/chd-classifier`) or default Hugging Face model.

### Endpoints (ML service)

| Endpoint    | Method | Purpose |
|------------|--------|--------|
| `/`        | GET    | Simple “ML Service is running” message |
| `/health`  | GET    | Health check; includes `model_loaded`, `model_type`, `classes` |
| `/test`    | POST   | Test request body parsing (scan_id, image_data) |
| **`/predict`** | POST | **Main API**: body `{ scan_id?, image_data (base64) }` → prediction + confidence + class_probabilities |

### Request/Response (Backend ↔ ML)

- **Request to ML**:  
  `{ "scan_id": "uuid", "image_data": "base64-string" }`  
- **Response from ML**:  
  `{ "scan_id", "prediction": "Normal|ASD|VSD", "confidence_score": float, "class_probabilities": { "Normal": ..., "ASD": ..., "VSD": ... }, "status": "COMPLETED" }`

---

## 4. Model Architecture, CNN Layers & Accuracy Metrics

This section gives you the technical detail for presenting the **model design** and **evaluation** (accuracy, etc.).

### 4.1 What Architecture Is Used?

We use **ConvNeXt** (Hugging Face `ConvNextForImageClassification`): a **modern convolutional neural network (CNN)** designed for image classification. It’s CNN-based (conv layers, not transformer attention), with a clear **stage-wise** structure and strong performance on vision tasks.

- **Model type**: `convnext` (from `config.json`)  
- **Task**: **Single-label classification** over 3 classes: **Normal**, **ASD**, **VSD**.  
- **Input**: RGB image, **224×224** pixels, 3 channels.  
- **Output**: 3 logits → softmax → class probabilities; predicted class = argmax.

### 4.2 High-Level Structure (Stages)

ConvNeXt is built as a **stem** plus **4 sequential stages**. Each stage is a stack of **ConvNeXt blocks**; resolution goes down and channel count goes up (like ResNet/VGG).

| Component | Role |
|-----------|------|
| **Stem** | Initial conv (e.g. 4×4 patch) to turn image into feature map. |
| **Stage 1–4** | Repeated ConvNeXt blocks; each stage typically halves spatial size and doubles (or more) channels. |
| **Classifier head** | Global pooling → linear layer → 3 logits (Normal, ASD, VSD). |

### 4.3 Our Model’s CNN Layers (From config.json)

Our fine-tuned model’s layout is defined in `ml-service/models/chd-classifier/config.json`:

| Parameter | Value | Meaning |
|-----------|--------|---------|
| **num_stages** | 4 | Four main stages (stage1–stage4). |
| **depths** | [3, 3, 27, 3] | Number of **ConvNeXt blocks** per stage: Stage1 = 3 blocks, Stage2 = 3, Stage3 = **27**, Stage4 = 3. Stage3 is the “deep” part. |
| **hidden_sizes** | [128, 256, 512, 1024] | **Channel dimensions** per stage: 128 → 256 → 512 → 1024. Feature maps get semantically richer and spatially smaller. |
| **patch_size** | 4 | Stem uses 4×4 patches (initial conv kernel/patch size). |
| **image_size** | 224 | Input image is resized to 224×224. |
| **num_channels** | 3 | RGB input. |
| **hidden_act** | gelu | GELU activation inside blocks. |
| **layer_scale_init_value** | 1e-6 | Layer scaling (ConvNeXt design). |
| **drop_path_rate** | 0 | No stochastic depth at inference. |

So in short:

- **Stem**: 224×224×3 → initial feature map (patch_size 4).  
- **Stage 1**: 3 blocks, 128 channels.  
- **Stage 2**: 3 blocks, 256 channels.  
- **Stage 3**: **27 blocks**, 512 channels (main depth).  
- **Stage 4**: 3 blocks, 1024 channels.  
- **Head**: global pooling → linear(1024 → 3) → logits.

Total blocks = 3 + 3 + 27 + 3 = **36 ConvNeXt blocks**. Each block typically includes: **depthwise conv**, **pointwise conv**, **LayerNorm**, **GELU**, and **layer scale** (and residual connection).

### 4.4 Image Preprocessing (Before the CNN)

Before the image is fed to the model, the **ConvNextImageProcessor** (from `preprocessor_config.json`) does:

| Step | Config | Effect |
|------|--------|--------|
| Resize | `size.shortest_edge = 224` | Shortest edge resized to 224 (aspect ratio preserved, then center crop to 224×224). |
| Rescale | `rescale_factor ≈ 1/255` | Pixel values from [0,255] to [0,1]. |
| Normalize | `image_mean`, `image_std` | ImageNet-style normalization: mean [0.485, 0.456, 0.406], std [0.229, 0.224, 0.225]. |

So the **actual input to the CNN** is a 224×224×3 tensor, normalized (zero-mean, unit variance per channel).

### 4.5 Accuracy & Evaluation Metrics

For a **3-class medical image classifier** (Normal, ASD, VSD), these are the metrics that are typically reported and that a supervisor may ask about.

**Metrics to mention:**

| Metric | Definition | Why it matters |
|--------|-------------|----------------|
| **Accuracy** | (Correct predictions) / (Total samples) | Overall correctness. |
| **Precision (per class)** | TP / (TP + FP) | Of all predicted “ASD”, how many were truly ASD. |
| **Recall (per class)** | TP / (TP + FN) | Of all true “ASD”, how many we detected. |
| **F1 score (per class)** | 2 × (Precision × Recall) / (Precision + Recall) | Balance of precision and recall. |
| **Macro F1** | Mean of F1 over Normal, ASD, VSD | Class-balanced performance (good when classes are imbalanced). |
| **Confusion matrix** | Rows = true class, columns = predicted | Shows where the model confuses classes (e.g. ASD vs VSD). |

**Suggested wording for the presentation:**

- “The model was evaluated on a held-out test set with **accuracy**, **per-class precision/recall/F1**, and **macro F1**. We also use a **confusion matrix** to see misclassifications (e.g. Normal vs ASD, ASD vs VSD).”

**If you have actual numbers** (from your training/evaluation script or report), fill them in here and use this table in your slides:

| Metric | Value (fill in from your evaluation) |
|--------|--------------------------------------|
| Test accuracy | e.g. 85.2% |
| Macro F1 | e.g. 0.84 |
| Normal – Precision / Recall / F1 | e.g. 0.88 / 0.90 / 0.89 |
| ASD – Precision / Recall / F1 | e.g. 0.82 / 0.79 / 0.80 |
| VSD – Precision / Recall / F1 | e.g. 0.85 / 0.86 / 0.85 |

**If you don’t have exact numbers:**

- Say: “Evaluation was done with accuracy, precision, recall, and F1 on a separate test set; the exact numbers are in our training/evaluation report.”  
- Or: “Typical targets for such a 3-class medical classifier are **accuracy > 80%** and **macro F1 > 0.75**; our model was tuned to balance precision and recall across Normal, ASD, and VSD.”

**Dataset / split (if asked):**

- “We used a train/validation/test split (e.g. 70/15/15 or 80/10/10). Metrics are reported on the **test set** only. Data was augmented (e.g. flips, rotation, brightness) during training to improve generalization.”

Adding this to your slides or talking points will cover **model architecture**, **CNN layers (ConvNeXt stages and blocks)**, and **accuracy metrics** in one place.

---

## 5. How the ML Service Fits With Other Services

| Service    | Role relative to ML |
|-----------|------------------------|
| **Backend (Spring Boot)** | Orchestrator: auth, access control, loads image from MinIO, calls ML, saves result and audit. Never sends raw images to frontend; only sends scan IDs and later prediction results. |
| **MinIO** | Object storage for scan images. Backend downloads the image by scan ID (after checking doctor access), then sends it to ML as base64. |
| **Database (H2/PostgreSQL)** | Stores `ecg_scan` (metadata + MinIO key), `ml_result` (prediction, confidence, class_probs, model_version, threshold, created_by, etc.). |
| **Frontend** | Calls backend `/api/ml/predict/{scanId}` and `/api/patients/{patientId}/predictions`; never talks to ML service directly. |

So: **ML is an internal microservice**. Only the backend talks to it; the frontend only talks to the backend.

---

## 6. Configuration (Quick Reference)

- **Backend** (`application.yml`):  
  `ml.service-url`, `ml.predict-endpoint` (`/predict`), timeouts, retries, `max-image-size-bytes`.  
- **ML service**:  
  `.env`: `MODEL_PATH=./models/chd-classifier` (or path to your model).  
  Optional: `CLASS_LABELS=Normal,ASD,VSD` if not in model config.  
- **Model files**:  
  `config.json`, `preprocessor_config.json`, `model.safetensors` (or `pytorch_model.bin`) under `MODEL_PATH`.

---

## 7. Supervisor Q&A: Common Questions and Answers

### General / Architecture

**Q: What is the role of the ML service in this project?**  
A: It is a separate microservice that performs **image classification** on ECG/scan images. It receives an image (as base64), runs a **ConvNeXt** model, and returns a label (Normal, ASD, or VSD) with confidence and class probabilities. The main backend uses it only for predictions and stores results in the database.

**Q: Why is the ML part a separate service and not inside the Java backend?**  
A: (1) **Stack**: ML is Python/PyTorch/Transformers; the main app is Java/Spring Boot. (2) **Scaling**: We can scale the ML service independently (e.g. more GPU instances). (3) **Deployment**: Model updates can be done without redeploying the whole backend. (4) **Separation of concerns**: Backend handles security, storage, and business rules; ML handles only inference.

**Q: How does the backend communicate with the ML service?**  
A: Over **HTTP REST**. The backend sends a **POST** to the ML service’s `/predict` endpoint with JSON body containing `scan_id` and base64-encoded `image_data`. The ML service responds with JSON containing `prediction`, `confidence_score`, and `class_probabilities`. Configuration (URL, timeouts, retries) is in `application.yml` under the `ml` prefix.

### Data and Security

**Q: Where does the image come from when we run a prediction?**  
A: The image is stored in **MinIO** (object storage). The backend looks up the scan in the DB, checks that the doctor has access to the patient, then uses **ScanService.downloadScan()** to get the image from MinIO, converts it to base64, and sends it to the ML service. The ML service does not access MinIO or the DB.

**Q: Is the image sent to the frontend?**  
A: For the **prediction API**, the frontend only sends a **scan ID**. The backend fetches the image from MinIO and sends it to the ML service. The frontend receives only the **result** (prediction, confidence, class probabilities), not the raw image. (Image download for viewing may be a separate endpoint.)

**Q: How is access control enforced for predictions?**  
A: In the backend. Before downloading the scan or calling the ML service, the backend checks that the doctor has access to the patient (e.g. via **PatientAccessRepository**). If not, it returns 403 Forbidden. The ML service does not perform any auth; it is assumed to be internal/trusted.

### Model and ML

**Q: What model architecture is used and why?**  
A: **ConvNeXt** (from Hugging Face Transformers). It’s a modern CNN-style architecture that works well for image classification. We use it for **single-label classification** over three classes: Normal, ASD, VSD. The exact variant and size are defined in the model’s `config.json` (e.g. depths, hidden sizes).

**Q: What are ASD and VSD?**  
A: **ASD** = Atrial Septal Defect, **VSD** = Ventricular Septal Defect — both are types of congenital heart defects. The model is trained to classify scan images into Normal, ASD, or VSD.

**Q: How is the model loaded in the ML service?**  
A: At startup we load the model from `MODEL_PATH` (env). We use **AutoImageProcessor.from_pretrained()** and **AutoModelForImageClassification.from_pretrained()** (with fallbacks to ConvNeXt/ViT-specific classes). Class labels come from the model’s **id2label** in `config.json`. The model is set to **eval()** for inference.

**Q: What preprocessing is applied to the image?**  
A: The image is decoded from base64 to a PIL Image, converted to **RGB** if needed, then passed to the **ConvNeXt image processor** (from the same model repo). It typically resizes to 224×224 and normalizes pixel values. The processor is the one that matches the pretrained/fine-tuned model.

**Q: What does the service return besides the predicted class?**  
A: **confidence_score** (probability of the predicted class), **class_probabilities** (probability for Normal, ASD, VSD), and **status** (e.g. "COMPLETED"). The backend stores these in **ml_result** (e.g. `predicted_label`, `class_probs` JSON) and can show them in the UI.

**Q: What is the model’s accuracy / how was it evaluated?**  
A: The model was evaluated with **accuracy**, **per-class precision/recall/F1**, and **macro F1** on a held-out test set. We also use a **confusion matrix** to analyse misclassifications (e.g. ASD vs VSD). Exact numbers depend on the dataset and split; see **Section 4.5** for the metrics table and how to report them. For medical use, we aim for good recall on defect classes (ASD, VSD) and balance with precision to limit false positives.

**Q: How many layers does the model have?**  
A: The model has **4 stages** with **depths [3, 3, 27, 3]** ConvNeXt blocks per stage — **36 blocks** in total. Channel dimensions are 128 → 256 → 512 → 1024 across stages. The deepest part is stage 3 (27 blocks at 512 channels). See **Section 4.3** for the full layout.

### Reliability and Errors

**Q: What if the ML service is down or slow?**  
A: The backend uses **retry logic**: multiple attempts (e.g. 3) with **exponential backoff**. On **timeout** it throws **MLServiceTimeoutException**; on **connection/5xx** it throws **MLServiceUnavailableException**. These are mapped to appropriate HTTP status codes and messages in the **GlobalExceptionHandler**, so the frontend gets a clear error.

**Q: How are validation errors handled?**  
A: In the ML service, invalid or missing image data returns **400** with a clear message. Invalid request body is handled by FastAPI/Pydantic (e.g. **422**). In the backend, invalid or missing fields in the ML response (e.g. no `prediction` or `confidence_score`) cause **MLServiceException** and are not saved to the DB.

### Database and Audit

**Q: What is stored in the database after a prediction?**  
A: In **ml_result**: result_id, patient_id, scan_id, model_version, predicted_label, class_probs (JSON), threshold, created_by (doctor), created_at. Optionally explanation_uri for future use. An **audit log** entry is also created for the predict action.

**Q: Can a doctor see past predictions for a patient?**  
A: Yes. The backend exposes **GET /api/patients/{patientId}/predictions** (with pagination). It returns only results for patients the doctor has access to. Each item includes result_id, scan_id, predicted_label, confidence, created_at, etc.

### Deployment and Running

**Q: What do I need to run the ML service?**  
A: Python 3.8+, install deps with `pip install -r requirements.txt` (FastAPI, uvicorn, transformers, torch, pillow, etc.). Set `MODEL_PATH` (e.g. in `.env`) to the folder containing config, preprocessor config, and model weights. Run with `python main.py` or `uvicorn main:app --host 0.0.0.0 --port 8000`. Backend must have `ml.service-url` pointing to this (e.g. http://localhost:8000).

**Q: What if the model file is missing?**  
A: If `MODEL_PATH` points to a local directory that doesn’t exist or is invalid, the ML service logs a warning and may fall back to a default (e.g. a Hugging Face model). For CHD classification you should have the correct `chd-classifier` model files in place; otherwise predictions won’t match the intended Normal/ASD/VSD setup.

---

## 8. One-Page Cheat Sheet for the Presentation

- **What**: Python FastAPI microservice for **image classification** (Normal / ASD / VSD).
- **Model**: ConvNeXt, 3 classes, 224×224 input; loaded from `models/chd-classifier`.
- **Input**: Base64 image + optional scan_id (POST `/predict`).
- **Output**: prediction, confidence_score, class_probabilities, status.
- **Who calls ML**: Only the **Spring Boot backend** (after loading image from MinIO and checking access).
- **Backend flow**: Doctor → POST `/api/ml/predict/{scanId}` → load scan from MinIO → base64 → POST to ML → parse response → save MlResult + audit → return to client.
- **Reliability**: Retries with backoff, timeouts, and specific exceptions (timeout vs unavailable vs bad response).
- **Config**: Backend: `application.yml` (`ml.*`). ML: `.env` (`MODEL_PATH`, optional `CLASS_LABELS`).

Use this guide to walk through the flow on a diagram and to answer supervisor questions confidently. Good luck with your presentation.
