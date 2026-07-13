/**
 * API Integration Module for CHD-EPICS Frontend
 * Handles all backend API calls with proper authentication
 */

const API_BASE_URL =
    // If deployed, we default to `/api` and rely on Vercel rewrites to proxy to Render.
    // For local dev, the frontend runs on :3000 while the backend runs on :8080.
    window.API_BASE_URL ||
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
        ? 'http://localhost:8080/api'
        : '/api');

// ============================================
// UTILITY FUNCTIONS
// ============================================

/**
 * Get stored access token from sessionStorage
 */
function getAccessToken() {
    return sessionStorage.getItem('accessToken');
}

/**
 * Get stored refresh token from sessionStorage
 */
function getRefreshToken() {
    return sessionStorage.getItem('refreshToken');
}

/**
 * Store authentication tokens
 */
function storeTokens(accessToken, refreshToken) {
    sessionStorage.setItem('accessToken', accessToken);
    if (refreshToken) {
        sessionStorage.setItem('refreshToken', refreshToken);
    }
}

/**
 * Clear all authentication data
 */
function clearAuth() {
    sessionStorage.clear();
}

/**
 * Make authenticated API request with automatic token refresh
 */
async function authenticatedFetch(url, options = {}) {
    const token = getAccessToken();
    
    if (!token) {
        throw new Error('No access token available');
    }

    // Add authorization header
    const headers = {
        ...options.headers,
        'Authorization': `Bearer ${token}`
    };

    // Make request
    let response = await fetch(url, { ...options, headers });

    // If unauthorized, try to refresh token
    if (response.status === 401) {
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            // Retry with new token
            headers.Authorization = `Bearer ${getAccessToken()}`;
            response = await fetch(url, { ...options, headers });
        } else {
            // Refresh failed, redirect to login
            clearAuth();
            window.location.href = 'login.html';
            throw new Error('Session expired. Please login again.');
        }
    }

    return response;
}

// ============================================
// AUTHENTICATION APIs
// ============================================

/**
 * Register a new doctor
 * @param {Object} data - { email, password, fullName, phone }
 * @returns {Promise<Object>} - { status, message, data: { doctorId, email, fullName } }
 */
async function register(data) {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Registration failed');
    }

    return result;
}

/**
 * Login doctor
 * @param {Object} credentials - { email, password }
 * @returns {Promise<Object>} - { status, data: { accessToken, refreshToken, expiresIn, tokenType, sessionId } }
 */
async function login(credentials) {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials)
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Login failed');
    }

    // Store tokens
    if (result.data) {
        storeTokens(result.data.accessToken, result.data.refreshToken);
        sessionStorage.setItem('sessionId', result.data.sessionId);
    }

    return result;
}

/**
 * Refresh access token using refresh token
 * @returns {Promise<boolean>} - true if successful
 */
async function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    
    if (!refreshToken) {
        return false;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken })
        });

        const result = await response.json();
        
        if (response.ok && result.data) {
            sessionStorage.setItem('accessToken', result.data.accessToken);
            return true;
        }
        
        return false;
    } catch (error) {
        console.error('Token refresh failed:', error);
        return false;
    }
}

/**
 * Logout current user
 */
async function logout() {
    try {
        await authenticatedFetch(`${API_BASE_URL}/auth/logout`, {
            method: 'POST'
        });
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        clearAuth();
        window.location.href = 'login.html';
    }
}

/**
 * Get current logged-in doctor's profile
 * @returns {Promise<Object>} - { doctorId, email, fullName, phone, isActive, mfaEnabled, createdAt }
 */
async function getCurrentUser() {
    const response = await authenticatedFetch(`${API_BASE_URL}/auth/me`);
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get user info');
    }

    // Store user info
    if (result.data) {
        sessionStorage.setItem('user', JSON.stringify(result.data));
    }

    return result.data;
}

// ============================================
// PATIENT APIs
// ============================================

/**
 * Create a new patient
 * @param {Object} patientData - { name, age, gender, dateOfBirth, medicalHistory, diagnosis, notes }
 * @returns {Promise<Object>} - { patientId, anonymizedCode, createdAt }
 */
async function createPatient(patientData) {
    const response = await authenticatedFetch(`${API_BASE_URL}/patients`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ patientData })
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to create patient');
    }

    return result.data;
}

/**
 * Get patient by ID
 * @param {string} patientId - UUID of the patient
 * @returns {Promise<Object>} - Patient details with decrypted data
 */
async function getPatient(patientId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/patients/${patientId}`);
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get patient');
    }

    return result.data;
}

/**
 * List all patients accessible by current doctor
 * @param {Object} params - { page, size, sort, order }
 * @returns {Promise<Object>} - { content: [...patients], page, size, totalElements, totalPages }
 */
async function listPatients(params = {}) {
    const queryParams = new URLSearchParams({
        page: params.page || 0,
        size: params.size || 20,
        sort: params.sort || 'createdAt',
        order: params.order || 'desc'
    });

    const response = await authenticatedFetch(`${API_BASE_URL}/patients?${queryParams}`);
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to list patients');
    }

    return result.data;
}

/**
 * Update patient information
 * @param {string} patientId - UUID of the patient
 * @param {Object} patientData - Updated patient data
 * @returns {Promise<Object>} - Updated patient details
 */
async function updatePatient(patientId, patientData) {
    const response = await authenticatedFetch(`${API_BASE_URL}/patients/${patientId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ patientData })
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to update patient');
    }

    return result.data;
}

/**
 * Delete patient
 * @param {string} patientId - UUID of the patient
 */
async function deletePatient(patientId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/patients/${patientId}`, {
        method: 'DELETE'
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to delete patient');
    }

    return result;
}

// ============================================
// SCAN APIs
// ============================================

/**
 * Upload ECG scan for a patient
 * @param {File} file - Image file (JPEG/PNG)
 * @param {string} patientId - UUID of the patient
 * @param {string} metadata - Optional metadata (notes, device info, etc.)
 * @returns {Promise<Object>} - { scanId, patientId, storageUri, mimetype, uploadedAt }
 */
async function uploadScan(file, patientId, metadata = '') {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('patientId', patientId);
    if (metadata) {
        formData.append('metadata', metadata);
    }

    const response = await authenticatedFetch(`${API_BASE_URL}/scans/upload`, {
        method: 'POST',
        body: formData
        // Don't set Content-Type header - browser will set it with boundary for multipart
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to upload scan');
    }

    return result.data;
}

/**
 * Get scan metadata
 * @param {string} scanId - UUID of the scan
 * @returns {Promise<Object>} - Scan details
 */
async function getScan(scanId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/scans/${scanId}`);
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get scan');
    }

    return result.data;
}

/**
 * Download scan image
 * @param {string} scanId - UUID of the scan
 * @returns {Promise<Blob>} - Image blob
 */
async function downloadScan(scanId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/scans/${scanId}/download`);
    
    if (!response.ok) {
        throw new Error('Failed to download scan');
    }

    return await response.blob();
}

/**
 * Get download URL for scan (for displaying in <img> tag)
 * @param {string} scanId - UUID of the scan
 * @returns {string} - URL with authorization header
 */
function getScanDownloadUrl(scanId) {
    return `${API_BASE_URL}/scans/${scanId}/download`;
}

/**
 * Delete scan
 * @param {string} scanId - UUID of the scan
 */
async function deleteScan(scanId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/scans/${scanId}`, {
        method: 'DELETE'
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to delete scan');
    }

    return result;
}

/**
 * Get all scans for a patient
 * @param {string} patientId - UUID of the patient
 * @param {Object} params - { page, size }
 * @returns {Promise<Object>} - { content: [...scans], page, size, totalElements, totalPages }
 */
async function getPatientScans(patientId, params = {}) {
    const queryParams = new URLSearchParams({
        page: params.page || 0,
        size: params.size || 20
    });

    const response = await authenticatedFetch(
        `${API_BASE_URL}/patients/${patientId}/scans?${queryParams}`
    );
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get patient scans');
    }

    return result.data;
}

// ============================================
// ML PREDICTION APIs
// ============================================

/**
 * Trigger ML prediction for a scan
 * @param {string} scanId - UUID of the scan
 * @param {Object} options - { modelVersion, threshold }
 * @returns {Promise<Object>} - Prediction result
 */
async function predictFromScan(scanId, options = {}) {
    const response = await authenticatedFetch(`${API_BASE_URL}/ml/predict/${scanId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(options)
    });

    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Prediction failed');
    }

    return result.data;
}

/**
 * Get ML prediction result
 * @param {string} resultId - UUID of the result
 * @returns {Promise<Object>} - Prediction details
 */
async function getPredictionResult(resultId) {
    const response = await authenticatedFetch(`${API_BASE_URL}/ml/results/${resultId}`);
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get prediction result');
    }

    return result.data;
}

/**
 * Get all predictions for a patient
 * @param {string} patientId - UUID of the patient
 * @param {Object} params - { page, size }
 * @returns {Promise<Object>} - List of predictions
 */
async function getPatientPredictions(patientId, params = {}) {
    const queryParams = new URLSearchParams({
        page: params.page || 0,
        size: params.size || 20
    });

    const response = await authenticatedFetch(
        `${API_BASE_URL}/patients/${patientId}/predictions?${queryParams}`
    );
    const result = await response.json();
    
    if (!response.ok) {
        throw new Error(result.error?.message || 'Failed to get predictions');
    }

    return result.data;
}

// ============================================
// EXPORT API FUNCTIONS
// ============================================

// Make functions available globally
window.API = {
    // Auth
    register,
    login,
    logout,
    getCurrentUser,
    refreshAccessToken,
    
    // Patients
    createPatient,
    getPatient,
    listPatients,
    updatePatient,
    deletePatient,
    
    // Scans
    uploadScan,
    getScan,
    downloadScan,
    getScanDownloadUrl,
    deleteScan,
    getPatientScans,
    
    // ML Predictions
    predictFromScan,
    getPredictionResult,
    getPatientPredictions,
    
    // Utilities
    getAccessToken,
    clearAuth
};
