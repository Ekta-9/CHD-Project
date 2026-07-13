from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel 
from transformers import (
    ViTImageProcessor, 
    ViTForImageClassification, 
    AutoImageProcessor, 
    AutoModelForImageClassification,
    ConvNextImageProcessor,
    ConvNextForImageClassification
)
from PIL import Image
import torch
import base64
import io
import os
from typing import Optional, Dict, List
from pathlib import Path
import traceback

# Get script directory first (needed for .env loading and model path resolution)
script_dir = Path(__file__).parent.absolute()

# Try to load .env file if python-dotenv is available
print("--- Starting .env file loading ---")
try:
    from dotenv import load_dotenv
    # Load .env from the same directory as this script
    env_path = script_dir / ".env"
    print(f"--- Looking for .env at: {env_path} ---")
    print(f"--- .env file exists: {env_path.exists()} ---")
    
    if env_path.exists():
        # Load with override=True to ensure variables are set
        load_dotenv(dotenv_path=env_path, override=True)
        print(f"--- Loaded environment variables from .env file: {env_path} ---")
        # Print what we loaded
        model_path_from_env = os.getenv("MODEL_PATH", "NOT SET")
        print(f"--- MODEL_PATH from .env: {model_path_from_env} ---")
    else:
        print(f"--- .env file not found at: {env_path} ---")
        print("--- Using default MODEL_PATH ---")
except ImportError:
    print("--- python-dotenv not installed, skipping .env loading ---")
except Exception as e:
    print(f"--- Error loading .env file: {e} ---")
    import traceback
    traceback.print_exc()

app = FastAPI()

# Add global exception handler
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    error_msg = f"Unhandled exception: {str(exc)}"
    print(f"--- GLOBAL EXCEPTION HANDLER: {error_msg} ---")
    traceback.print_exc()
    return JSONResponse(
        status_code=500,
        content={"detail": error_msg, "error_type": type(exc).__name__}
    )

# Add validation error handler
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    error_msg = f"Validation error: {str(exc)}"
    print(f"--- VALIDATION ERROR: {error_msg} ---")
    print(f"--- Request body: {await request.body()} ---")
    return JSONResponse(
        status_code=422,
        content={"detail": error_msg, "errors": exc.errors()}
    )

# Get model path from environment variable, or use default
# You can set this to:
# - A Hugging Face model ID (e.g., "username/chd-classifier")
# - A local path (e.g., "./models/chd-classifier" or "C:/path/to/model")
# The local path should contain: config.json, model.safetensors, preprocessor_config.json
MODEL_PATH_ENV = os.getenv("MODEL_PATH", "google/vit-base-patch16-224-in21k")
print(f"--- MODEL_PATH_ENV (from os.getenv): {MODEL_PATH_ENV} ---")

# Convert relative paths to absolute paths (relative to this script's directory)
if MODEL_PATH_ENV.startswith("./") or MODEL_PATH_ENV.startswith("models/"):
    # It's a relative path - make it absolute relative to the script directory
    MODEL_NAME = str(script_dir / MODEL_PATH_ENV.lstrip("./"))
    print(f"--- Converted relative path '{MODEL_PATH_ENV}' to absolute: {MODEL_NAME} ---")
elif not os.path.isabs(MODEL_PATH_ENV) and ":" not in MODEL_PATH_ENV and not MODEL_PATH_ENV.startswith("/"):
    # Relative path without ./ prefix
    MODEL_NAME = str(script_dir / MODEL_PATH_ENV)
    print(f"--- Converted relative path '{MODEL_PATH_ENV}' to absolute: {MODEL_NAME} ---")
else:
    MODEL_NAME = MODEL_PATH_ENV
    print(f"--- Using MODEL_PATH as-is: {MODEL_NAME} ---")

# Verify the path exists if it's a local path
if not MODEL_NAME.startswith("http") and "/" in MODEL_NAME and not MODEL_NAME.startswith("google/"):
    model_path_obj = Path(MODEL_NAME)
    if not model_path_obj.exists():
        print(f"--- WARNING: Model path does not exist: {MODEL_NAME} ---")
        print(f"--- Falling back to default model ---")
        MODEL_NAME = "google/vit-base-patch16-224-in21k"
    else:
        print(f"--- Verified model path exists: {MODEL_NAME} ---")

# Class labels mapping - update this based on your fine-tuned model's class labels
# This should match the order of classes your model was trained on
# Example: If your model has 2 classes: [0: "Normal", 1: "ASD", 2: "VSD"]
CLASS_LABELS = os.getenv("CLASS_LABELS", "ASD,VSD").split(",")  # Default fallback

processor = None
model = None
id2label: Dict[int, str] = {}
label2id: Dict[str, int] = {}

try:
    print(f"--- Loading model from: {MODEL_NAME} (this may take a minute)... ---")
    
    # Check if it's a local path and validate required files exist
    model_path = Path(MODEL_NAME)
    if model_path.exists() and model_path.is_dir():
        print(f"--- Detected local model directory: {MODEL_NAME} ---")
        # Check for required files
        required_files = ["config.json", "preprocessor_config.json"]
        missing_files = [f for f in required_files if not (model_path / f).exists()]
        
        # Check for model weights (either safetensors or pytorch_model.bin)
        has_safetensors = (model_path / "model.safetensors").exists()
        has_pytorch_bin = (model_path / "pytorch_model.bin").exists()
        
        if missing_files:
            print(f"--- WARNING: Missing files: {missing_files} ---")
        if not has_safetensors and not has_pytorch_bin:
            print(f"--- WARNING: No model weights found (model.safetensors or pytorch_model.bin) ---")
        else:
            weight_format = "safetensors" if has_safetensors else "pytorch_model.bin"
            print(f"--- Found model weights in {weight_format} format ---")
    
    # Try to load as Auto classes first (works for fine-tuned models including ConvNeXt)
    # Auto classes automatically handle: config.json, model.safetensors, preprocessor_config.json
    try:
        processor = AutoImageProcessor.from_pretrained(MODEL_NAME)
        model = AutoModelForImageClassification.from_pretrained(MODEL_NAME)
        print("--- Loaded using AutoImageProcessor and AutoModelForImageClassification ---")
        print(f"--- Model type: {type(model).__name__} ---")
    except Exception as e1:
        print(f"--- Auto classes failed, trying specific model classes: {e1} ---")
        # Try ConvNeXt first (since user mentioned ConvNeXt)
        try:
            processor = ConvNextImageProcessor.from_pretrained(MODEL_NAME)
            model = ConvNextForImageClassification.from_pretrained(MODEL_NAME)
            print("--- Loaded using ConvNextImageProcessor and ConvNextForImageClassification ---")
        except Exception as e2:
            print(f"--- ConvNeXt classes failed, trying ViT classes: {e2} ---")
            # Fallback to ViT classes if Auto doesn't work
            processor = ViTImageProcessor.from_pretrained(MODEL_NAME)
            model = ViTForImageClassification.from_pretrained(MODEL_NAME)
            print("--- Loaded using ViTImageProcessor and ViTForImageClassification ---")
    
    # Get class labels from model config if available
    if hasattr(model.config, 'id2label') and model.config.id2label:
        id2label = model.config.id2label
        # Convert string keys to int if needed
        id2label = {int(k): v for k, v in id2label.items()}
        label2id = {v: k for k, v in id2label.items()}
        print(f"--- Model class labels loaded from config: {id2label} ---")
    elif hasattr(model.config, 'label2id') and model.config.label2id:
        label2id = model.config.label2id
        id2label = {v: k for k, v in label2id.items()}
        print(f"--- Model class labels loaded from config: {id2label} ---")
    else:
        # Use default class labels if model doesn't have them configured
        num_labels = getattr(model.config, 'num_labels', len(CLASS_LABELS))
        id2label = {i: CLASS_LABELS[i] if i < len(CLASS_LABELS) else f"Class_{i}" 
                   for i in range(num_labels)}
        label2id = {v: k for k, v in id2label.items()}
        print(f"--- Using default class labels: {id2label} ---")
        print("--- WARNING: Model doesn't have class labels configured. Update CLASS_LABELS env var or model config. ---")
    
    model.eval()  # Set model to evaluation mode
    print("--- Model loaded successfully! ---")
    print(f"--- Model has {len(id2label)} classes: {list(id2label.values())} ---")
    
except Exception as e:
    print(f"--- Error loading model: {e} ---")
    print("--- Make sure MODEL_PATH environment variable points to your fine-tuned model ---")
    processor = None
    model = None

class ScanRequest(BaseModel):
    scan_id: Optional[str] = None
    image_data: Optional[str] = None  # Base64 encoded image
    # Keep mri_scan_id for backward compatibility
    mri_scan_id: Optional[int] = None

@app.get("/")
def read_root():
    return {"message": "Hello! The ML Service is running."}

@app.get("/health")
def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "model_loaded": model is not None and processor is not None,
        "model_type": type(model).__name__ if model else None,
        "classes": list(id2label.values()) if id2label else []
    }

@app.post("/test")
async def test_endpoint(request: ScanRequest):
    """Test endpoint to verify request parsing"""
    print(f"--- TEST ENDPOINT: Received request ---")
    print(f"--- scan_id: {request.scan_id} ---")
    print(f"--- has image_data: {request.image_data is not None} ---")
    if request.image_data:
        print(f"--- image_data length: {len(request.image_data)} ---")
    return {
        "message": "Test endpoint working",
        "scan_id": request.scan_id,
        "has_image_data": request.image_data is not None
    }


# --- 5. Create the REAL "Predict" Endpoint ---
@app.post("/predict")
async def predict_mri(request: ScanRequest):
    """
    This is the main endpoint your Java backend will call.
    Accepts base64 encoded image data from the backend.
    """
    try:
        print(f"--- Received prediction request ---")
        print(f"--- Request scan_id: {request.scan_id} ---")
        print(f"--- Request has image_data: {request.image_data is not None} ---")
        
        # Check if model and processor are loaded first
        if processor is None or model is None:
            error_msg = "ML model not loaded. Check server logs for errors."
            print(f"--- ERROR: {error_msg} ---")
            raise HTTPException(status_code=500, detail=error_msg)

        if not id2label:
            error_msg = "Model class labels not configured. Check model config or CLASS_LABELS env var."
            print(f"--- ERROR: {error_msg} ---")
            raise HTTPException(status_code=500, detail=error_msg)

        # Check if image data is provided
        image = None
        if request.image_data:
            # Decode base64 image data
            try:
                print(f"--- Decoding base64 image data ---")
                image_bytes = base64.b64decode(request.image_data)
                print(f"--- Decoded image size: {len(image_bytes)} bytes ---")
                image = Image.open(io.BytesIO(image_bytes))
                print(f"--- Image opened successfully: {image.size}, mode: {image.mode} ---")
            except Exception as e:
                error_msg = f"Failed to decode image data: {str(e)}"
                print(f"--- ERROR: {error_msg} ---")
                import traceback
                traceback.print_exc()
                raise HTTPException(status_code=400, detail=error_msg)
        else:
            # Fallback: try to use test image (for backward compatibility during development)
            try:
                print("--- No image_data provided, trying fallback test_image.jpg ---")
                image = Image.open("test_image.jpg")
                print("--- Using fallback test_image.jpg ---")
            except FileNotFoundError:
                error_msg = "No image data provided and test_image.jpg not found"
                print(f"--- ERROR: {error_msg} ---")
                raise HTTPException(status_code=400, detail=error_msg)
            except Exception as e:
                error_msg = f"Failed to load test image: {str(e)}"
                print(f"--- ERROR: {error_msg} ---")
                raise HTTPException(status_code=400, detail=error_msg)

        if image is None:
            error_msg = "Failed to load image"
            print(f"--- ERROR: {error_msg} ---")
            raise HTTPException(status_code=400, detail=error_msg)

        # Convert image to RGB if needed (some models require RGB)
        if image.mode != 'RGB':
            print(f"--- Converting image from {image.mode} to RGB ---")
            image = image.convert('RGB')
            print(f"--- Image converted to RGB: {image.size}, mode: {image.mode} ---")

        # Preprocess the image
        try:
            print("--- Preprocessing image with processor ---")
            inputs = processor(images=image, return_tensors="pt")
            print(f"--- Image preprocessed successfully ---")
        except Exception as e:
            error_msg = f"Failed to preprocess image: {str(e)}"
            print(f"--- ERROR: {error_msg} ---")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=error_msg)

        # Run inference
        try:
            print("--- Running model inference ---")
            with torch.no_grad():
                outputs = model(**inputs)
            print("--- Inference complete ---")
        except Exception as e:
            error_msg = f"Failed to run inference: {str(e)}"
            print(f"--- ERROR: {error_msg} ---")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=error_msg)
        
        # Get logits (raw prediction scores)
        try:
            logits = outputs.logits
            print(f"--- Got logits shape: {logits.shape} ---")
        except Exception as e:
            error_msg = f"Failed to extract logits: {str(e)}"
            print(f"--- ERROR: {error_msg} ---")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=error_msg)
        
        # Apply softmax to get probabilities
        try:
            probabilities = torch.softmax(logits, dim=-1)[0]
            print(f"--- Computed probabilities shape: {probabilities.shape} ---")
        except Exception as e:
            error_msg = f"Failed to compute probabilities: {str(e)}"
            print(f"--- ERROR: {error_msg} ---")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=error_msg)
        
        # Get the predicted class index
        try:
            predicted_class_idx = logits.argmax(-1).item()
            print(f"--- Predicted class index: {predicted_class_idx} ---")
        except Exception as e:
            error_msg = f"Failed to get predicted class: {str(e)}"
            print(f"--- ERROR: {error_msg} ---")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=error_msg)
        
        # Get the actual class label from the model's configuration
        predicted_label = id2label.get(predicted_class_idx, f"Class_{predicted_class_idx}")
        
        # Get the confidence score for the predicted class
        confidence = probabilities[predicted_class_idx].item()
        
        # Get top predictions (optional - for debugging/monitoring)
        top_k = min(3, len(id2label))  # Get top 3 predictions
        top_probs, top_indices = torch.topk(probabilities, top_k)
        
        print(f"--- Prediction complete ---")
        print(f"--- Predicted class: {predicted_label} (index: {predicted_class_idx}) ---")
        print(f"--- Confidence: {confidence:.4f} ---")
        print(f"--- Top {top_k} predictions:")
        for i in range(top_k):
            idx = top_indices[i].item()
            prob = top_probs[i].item()
            label = id2label.get(idx, f"Class_{idx}")
            print(f"---   {label}: {prob:.4f} ---")
        
        # Build class probabilities map for all classes
        class_probabilities = {}
        for idx in range(len(id2label)):
            label = id2label.get(idx, f"Class_{idx}")
            prob = probabilities[idx].item()
            class_probabilities[label] = round(prob, 4)
        
        # Send back the final JSON response
        scan_id = request.scan_id if request.scan_id else (str(request.mri_scan_id) if request.mri_scan_id else None)
        
        response = {
            "scan_id": scan_id,
            "prediction": predicted_label,  # Use actual model prediction
            "confidence_score": round(confidence, 4),
            "class_probabilities": class_probabilities,  # All class probabilities
            "status": "COMPLETED"
        }
        
        print(f"--- Returning response: {response} ---")
        return response
        
    except HTTPException:
        # Re-raise HTTP exceptions as-is
        raise
    except Exception as e:
        # Catch any other unexpected errors
        error_msg = f"Unexpected error during prediction: {str(e)}"
        print(f"--- UNEXPECTED ERROR: {error_msg} ---")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=error_msg)

# Start the FastAPI server
if __name__ == "__main__":
    import uvicorn
    print("--- Starting FastAPI server ---")
    print("--- Server will be available at http://localhost:8000 ---")
    print("--- API docs will be available at http://localhost:8000/docs ---")
    uvicorn.run(app, host="0.0.0.0", port=8000)

