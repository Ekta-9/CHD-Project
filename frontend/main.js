let patients = [];
let editingIndex = null;
let filteredPatients = null;
let searchTimeout = null;

function switchTab(tabName) {
    document.getElementById('section-home').style.display = 'none';
    document.getElementById('section-patients').style.display = 'none';
    document.getElementById('section-report').style.display = 'none';
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    if (tabName === 'home') {
        document.getElementById('section-home').style.display = 'block';
        document.querySelectorAll('.nav-item')[0].classList.add('active');
    } else if (tabName === 'patients') {
        document.getElementById('section-patients').style.display = 'block';
        document.querySelectorAll('.nav-item')[1].classList.add('active');
    } else if (tabName === 'report') {
        document.getElementById('section-report').style.display = 'block';
        document.querySelectorAll('.nav-item')[2].classList.add('active');
        loadPatientsForReport();
    }
}

// GLOBALIZE switchTab so onclick works!
window.switchTab = switchTab;

window.onload = async function () {
    // Check if logged in
    if (!API.getAccessToken()) {
        window.location.href = 'login.html';
        return;
    }

    try {
        // Load current user
        const user = await API.getCurrentUser();
        document.getElementById('docNameDisplay').innerText = user.fullName;
        
        // Load patients from backend
        const result = await API.listPatients();
        patients = result.content || [];
        renderPatients(patients);
    } catch (error) {
        console.error('Failed to load data:', error);
        // If unauthorized, redirect to login
        if (error.message && (error.message.includes('unauthorized') || error.message.includes('token'))) {
            window.location.href = 'login.html';
        }
    }
};

function renderPatients(data) {
    const container = document.getElementById('patient-list-container');
    container.innerHTML = '';
    document.getElementById('totalPatientsCount').innerText = data.length;

    if (data.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #888; padding: 20px;">No patients found. Click "+ Add New Patient" to add one.</p>';
        return;
    }

    data.forEach((patient) => {
        const card = document.createElement('div');
        card.className = 'patient-card';
        card.innerHTML = `
            <div>
                <h3>Patient Record</h3>
                <small>Code: ${patient.anonymizedCode}</small>
                <br>
                <small style="color: #999;">Access: ${patient.accessRole} | Created: ${new Date(patient.createdAt).toLocaleDateString()}</small>
            </div>
            <button class="btn-secondary" onclick="viewPatientDetails('${patient.patientId}')">View Details</button>
        `;
        container.appendChild(card);
    });
}

function filterPatients() {
    // Clear previous timeout
    if (searchTimeout) {
        clearTimeout(searchTimeout);
    }
    
    // Debounce search - wait 300ms after user stops typing
    searchTimeout = setTimeout(async () => {
        const query = document.getElementById('searchBar').value.trim();
        
        if (!query) {
            // If search is empty, reload all patients
            const result = await API.listPatients();
            patients = result.content || [];
            renderPatients(patients);
            return;
        }
        
        // Search by anonymized code (available in list)
        const searchLower = query.toLowerCase();
        const filtered = patients.filter(patient => {
            return patient.anonymizedCode && patient.anonymizedCode.toLowerCase().includes(searchLower);
        });
        
        renderPatients(filtered);
        
        // Show message if searching by name
        if (filtered.length === 0 && query.length > 2) {
            document.getElementById('patient-list-container').innerHTML = 
                '<p style="text-align: center; color: #888; padding: 20px;">No patients found matching "<strong>' + query + '</strong>".<br><small>Search by patient code (e.g., PAT-...). Patient names are encrypted and not searchable in list view.</small></p>';
        }
    }, 300);
}

function openAddModal() {
    document.getElementById('addPatientModal').style.display = 'block';
}

async function logout() {
    if (confirm("Are you sure you want to logout?")) {
        try {
            await API.logout();
        } catch (error) {
            console.error('Logout error:', error);
        }
        // Always clear and redirect even if API call fails
        sessionStorage.clear();
        window.location.href = "thankyou.html";
    }
}

// Make logout available globally
window.logout = logout;


function toggleUserDropdown() {
    document.getElementById("userDropdown").classList.toggle("show-dropdown");
}

window.onclick = function (event) {
    if (!event.target.closest('.user-profile-container')) {
        var dropdowns = document.getElementsByClassName("dropdown-menu");
        for (var i = 0; i < dropdowns.length; i++) {
            if (dropdowns[i].classList.contains('show-dropdown')) {
                dropdowns[i].classList.remove('show-dropdown');
            }
        }
    }
}

function viewScan(patientName, scanUrl) {
    document.getElementById('scanViewerTitle').innerText = `ECG Scan - ${patientName}`;
    document.getElementById('scanViewerImage').src = scanUrl;
    document.getElementById('scanViewerModal').style.display = 'flex';
}

function closeScanViewer(event) {
    if (!event || event.target.id === 'scanViewerModal') {
        document.getElementById('scanViewerModal').style.display = 'none';
    }
}

async function addNewPatient() {
    const name = document.getElementById('newPatientName').value.trim();
    const age = document.getElementById('newPatientAge').value;
    const gender = document.getElementById('newPatientGender').value;
    const dob = document.getElementById('newPatientDOB').value;
    const phone = document.getElementById('newPatientPhone').value.trim();
    const history = document.getElementById('newPatientHistory').value.trim();
    const diagnosis = document.getElementById('newPatientDiagnosis').value.trim();
    const notes = document.getElementById('newPatientNotes').value.trim();

    if (!name || !age || !gender) {
        alert("Please fill in Name, Age, and Gender");
        return;
    }

    try {
        await API.createPatient({
            name,
            age: parseInt(age),
            gender,
            dateOfBirth: dob || "",
            phone: phone || "",
            medicalHistory: history || "",
            diagnosis: diagnosis || "",
            notes: notes || ""
        });
        
        alert("Patient added successfully!");
        
        // Reload patients
        const result = await API.listPatients();
        patients = result.content || [];
        renderPatients(patients);
        
        closeAddModal();
    } catch (error) {
        alert("Failed to add patient: " + error.message);
    }
}

function closeAddModal() {
    document.getElementById('addPatientModal').style.display = 'none';
    document.getElementById('newPatientName').value = '';
    document.getElementById('newPatientAge').value = '';
    document.getElementById('newPatientGender').value = '';
    document.getElementById('newPatientDOB').value = '';
    document.getElementById('newPatientPhone').value = '';
    document.getElementById('newPatientHistory').value = '';
    document.getElementById('newPatientDiagnosis').value = '';
    document.getElementById('newPatientNotes').value = '';
}

// Make functions globally available
window.toggleUserDropdown = toggleUserDropdown;
window.viewScan = viewScan;
window.closeScanViewer = closeScanViewer;
window.addNewPatient = addNewPatient;
window.closeAddModal = closeAddModal;
window.openAddModal = openAddModal;
window.filterPatients = filterPatients;


async function viewPatientDetails(patientId) {
    try {
        const patient = await API.getPatient(patientId);
        const p = patient.patientData;
        
        const content = document.getElementById('patientDetailsContent');
        content.innerHTML = `
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 15px;">
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                    <div>
                        <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Name</p>
                        <p style="margin: 0; font-weight: 600; font-size: 1.1rem;">${p.name}</p>
                    </div>
                    <div>
                        <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Age</p>
                        <p style="margin: 0; font-weight: 600; font-size: 1.1rem;">${p.age}</p>
                    </div>
                    <div>
                        <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Gender</p>
                        <p style="margin: 0; font-weight: 600;">${p.gender}</p>
                    </div>
                    <div>
                        <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Date of Birth</p>
                        <p style="margin: 0; font-weight: 600;">${p.dateOfBirth || 'N/A'}</p>
                    </div>
                    <div style="grid-column: 1 / -1;">
                        <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Phone</p>
                        <p style="margin: 0; font-weight: 600;">${p.phone || 'N/A'}</p>
                    </div>
                </div>
            </div>
            
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 15px;">
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 8px 0; color: #666; font-size: 0.85rem; font-weight: 600;">Medical History</p>
                    <p style="margin: 0; line-height: 1.6;">${p.medicalHistory || 'No medical history recorded'}</p>
                </div>
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 8px 0; color: #666; font-size: 0.85rem; font-weight: 600;">Diagnosis</p>
                    <p style="margin: 0; line-height: 1.6;">${p.diagnosis || 'No diagnosis recorded'}</p>
                </div>
                <div>
                    <p style="margin: 0 0 8px 0; color: #666; font-size: 0.85rem; font-weight: 600;">Notes</p>
                    <p style="margin: 0; line-height: 1.6;">${p.notes || 'No notes recorded'}</p>
                </div>
            </div>
            
            <div style="background: #e8e8e8; padding: 15px; border-radius: 6px; font-size: 0.85rem; color: #555;">
                <p style="margin: 0 0 8px 0;"><strong>Anonymized Code:</strong> ${patient.anonymizedCode}</p>
                <p style="margin: 0 0 8px 0;"><strong>Access Role:</strong> <span style="text-transform: capitalize;">${patient.accessRole}</span></p>
                <p style="margin: 0;"><strong>Created:</strong> ${new Date(patient.createdAt).toLocaleString()}</p>
            </div>
        `;
        
        document.getElementById('viewPatientModal').style.display = 'flex';
    } catch (error) {
        alert('Failed to load patient details: ' + error.message);
    }
}

function closeViewPatientModal() {
    document.getElementById('viewPatientModal').style.display = 'none';
}

window.viewPatientDetails = viewPatientDetails;
window.closeViewPatientModal = closeViewPatientModal;


// Store current patient being viewed/edited
let currentPatient = null;

// Update viewPatientDetails to store current patient
const originalViewPatientDetails = viewPatientDetails;
viewPatientDetails = async function(patientId) {
    await originalViewPatientDetails(patientId);
    // Store the patient ID for edit/delete operations
    currentPatient = await API.getPatient(patientId);
};

function openEditPatient() {
    if (!currentPatient) return;
    
    const p = currentPatient.patientData;
    
    // Populate edit form
    document.getElementById('editPatientName').value = p.name || '';
    document.getElementById('editPatientAge').value = p.age || '';
    document.getElementById('editPatientGender').value = p.gender || '';
    document.getElementById('editPatientDOB').value = p.dateOfBirth || '';
    document.getElementById('editPatientPhone').value = p.phone || '';
    document.getElementById('editPatientHistory').value = p.medicalHistory || '';
    document.getElementById('editPatientDiagnosis').value = p.diagnosis || '';
    document.getElementById('editPatientNotes').value = p.notes || '';
    
    // Close view modal and open edit modal
    document.getElementById('viewPatientModal').style.display = 'none';
    document.getElementById('editPatientModal').style.display = 'flex';
}

async function savePatientEdit() {
    if (!currentPatient) return;
    
    const name = document.getElementById('editPatientName').value.trim();
    const age = document.getElementById('editPatientAge').value;
    const gender = document.getElementById('editPatientGender').value;
    const dob = document.getElementById('editPatientDOB').value;
    const phone = document.getElementById('editPatientPhone').value.trim();
    const history = document.getElementById('editPatientHistory').value.trim();
    const diagnosis = document.getElementById('editPatientDiagnosis').value.trim();
    const notes = document.getElementById('editPatientNotes').value.trim();
    
    if (!name || !age || !gender) {
        alert("Please fill in Name, Age, and Gender");
        return;
    }
    
    try {
        await API.updatePatient(currentPatient.patientId, {
            name,
            age: parseInt(age),
            gender,
            dateOfBirth: dob || "",
            phone: phone || "",
            medicalHistory: history || "",
            diagnosis: diagnosis || "",
            notes: notes || ""
        });
        
        alert("Patient updated successfully!");
        
        // Reload patients list
        const result = await API.listPatients();
        patients = result.content || [];
        renderPatients(patients);
        
        closeEditModal();
    } catch (error) {
        alert("Failed to update patient: " + error.message);
    }
}

function closeEditModal() {
    document.getElementById('editPatientModal').style.display = 'none';
}

async function deleteCurrentPatient() {
    if (!currentPatient) return;
    
    const p = currentPatient.patientData;
    const confirmDelete = confirm(`Are you sure you want to delete patient "${p.name}"?\n\nThis action cannot be undone.`);
    
    if (!confirmDelete) return;
    
    try {
        await API.deletePatient(currentPatient.patientId);
        
        alert("Patient deleted successfully!");
        
        // Reload patients list
        const result = await API.listPatients();
        patients = result.content || [];
        renderPatients(patients);
        
        closeViewPatientModal();
        currentPatient = null;
    } catch (error) {
        alert("Failed to delete patient: " + error.message);
    }
}

// Make functions globally available
window.openEditPatient = openEditPatient;
window.savePatientEdit = savePatientEdit;
window.closeEditModal = closeEditModal;
window.deleteCurrentPatient = deleteCurrentPatient;


// Close modal when clicking outside
function closeModalOnBackdrop(event, modalId) {
    if (event.target.id === modalId) {
        document.getElementById(modalId).style.display = 'none';
    }
}

window.closeModalOnBackdrop = closeModalOnBackdrop;


// ============================================
// ECG SCAN MANAGEMENT
// ============================================

async function loadPatientScans(patientId) {
    try {
        const result = await API.getPatientScans(patientId);
        const scans = result.content || [];
        
        const container = document.getElementById('patientScansContainer');
        
        if (scans.length === 0) {
            container.innerHTML = '<p style="color: #888; text-align: center; padding: 15px; background: #f9f9f9; border-radius: 8px;">No ECG scans uploaded yet.</p>';
            return;
        }
        
        container.innerHTML = scans.map(scan => `
            <div style="background: #f9f9f9; padding: 12px; border-radius: 8px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <div style="font-weight: 600;">ECG Scan</div>
                    <small style="color: #666;">Uploaded: ${new Date(scan.uploadedAt).toLocaleString()}</small>
                </div>
                <div style="display: flex; gap: 8px;">
                    <button onclick="viewScanImage('${scan.scanId}')" class="btn-secondary" style="padding: 6px 12px; font-size: 0.85rem;">View</button>
                    <button onclick="analyzeScan('${scan.scanId}')" class="btn-primary" style="padding: 6px 12px; font-size: 0.85rem;">Analyze</button>
                    <button onclick="deleteScan('${scan.scanId}')" class="btn-secondary" style="padding: 6px 12px; font-size: 0.85rem; background: #dc2626; color: white;">Delete</button>
                </div>
            </div>
        `).join('');
        
    } catch (error) {
        console.error('Failed to load scans:', error);
        document.getElementById('patientScansContainer').innerHTML = 
            '<p style="color: #dc2626; text-align: center; padding: 15px;">Failed to load scans</p>';
    }
}

function openUploadScanModal() {
    if (!currentPatient) return;
    document.getElementById('uploadScanModal').style.display = 'flex';
    
    // Add preview functionality
    document.getElementById('scanFileInput').onchange = function(e) {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = function(event) {
                document.getElementById('scanPreviewImage').src = event.target.result;
                document.getElementById('scanPreview').style.display = 'block';
            };
            reader.readAsDataURL(file);
        }
    };
}

function closeUploadScanModal() {
    document.getElementById('uploadScanModal').style.display = 'none';
    document.getElementById('scanFileInput').value = '';
    document.getElementById('scanNotes').value = '';
    document.getElementById('scanPreview').style.display = 'none';
}

async function uploadScan() {
    if (!currentPatient) return;
    
    const fileInput = document.getElementById('scanFileInput');
    const file = fileInput.files[0];
    const notes = document.getElementById('scanNotes').value.trim();
    
    if (!file) {
        alert('Please select an image file');
        return;
    }
    
    // Validate file size (10MB max)
    if (file.size > 10 * 1024 * 1024) {
        alert('File size must be less than 10MB');
        return;
    }
    
    // Validate file type
    if (!['image/jpeg', 'image/png', 'image/jpg'].includes(file.type)) {
        alert('Only JPEG and PNG images are supported');
        return;
    }
    
    const uploadBtn = document.getElementById('uploadScanBtn');
    uploadBtn.disabled = true;
    uploadBtn.textContent = 'Uploading...';
    
    try {
        const metadata = notes ? JSON.stringify({ notes }) : '';
        await API.uploadScan(file, currentPatient.patientId, metadata);
        
        alert('Scan uploaded successfully!');
        closeUploadScanModal();
        
        // Reload scans
        await loadPatientScans(currentPatient.patientId);
        
        // Keep patient details modal open
        document.getElementById('viewPatientModal').style.display = 'flex';
        
    } catch (error) {
        alert('Failed to upload scan: ' + error.message);
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = 'Upload Scan';
    }
}

async function viewScanImage(scanId) {
    try {
        const blob = await API.downloadScan(scanId);
        const url = URL.createObjectURL(blob);
        
        // Open in new window with close button
        const win = window.open('', '_blank', 'width=1000,height=800');
        win.document.write(`
            <html>
                <head>
                    <title>ECG Scan</title>
                    <style>
                        body {
                            margin: 0;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                            background: #000;
                            position: relative;
                        }
                        img {
                            max-width: 95%;
                            max-height: 95vh;
                            object-fit: contain;
                        }
                        .close-btn {
                            position: fixed;
                            top: 20px;
                            right: 20px;
                            background: rgba(255, 255, 255, 0.9);
                            border: none;
                            border-radius: 50%;
                            width: 40px;
                            height: 40px;
                            font-size: 24px;
                            cursor: pointer;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
                            transition: all 0.2s;
                            z-index: 1000;
                        }
                        .close-btn:hover {
                            background: #fff;
                            transform: scale(1.1);
                        }
                    </style>
                </head>
                <body>
                    <button class="close-btn" onclick="window.close()" title="Close">×</button>
                    <img src="${url}" alt="ECG Scan" />
                </body>
            </html>
        `);
        
        // Clean up after a delay
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        
    } catch (error) {
        alert('Failed to load scan image: ' + error.message);
    }
}

async function analyzeScan(scanId) {
    if (!confirm('Trigger ML analysis for this scan?')) return;
    
    try {
        const result = await API.predictFromScan(scanId);
        
        // Show results in a nice modal
        alert(`ML Analysis Complete!\n\nPredicted Diagnosis: ${result.predictedLabel}\nConfidence: ${(result.confidenceScore * 100).toFixed(2)}%\n\nClass Probabilities:\n${Object.entries(result.classProbabilities).map(([label, prob]) => `${label}: ${(prob * 100).toFixed(2)}%`).join('\n')}`);
        
    } catch (error) {
        alert('ML analysis failed: ' + error.message);
    }
}

async function deleteScan(scanId) {
    if (!confirm('Are you sure you want to delete this scan?')) return;
    
    try {
        await API.deleteScan(scanId);
        alert('Scan deleted successfully!');
        
        // Reload scans
        if (currentPatient) {
            await loadPatientScans(currentPatient.patientId);
        }
        
    } catch (error) {
        alert('Failed to delete scan: ' + error.message);
    }
}

// Update viewPatientDetails to load scans
const originalViewPatientDetailsFunc = viewPatientDetails;
viewPatientDetails = async function(patientId) {
    await originalViewPatientDetailsFunc(patientId);
    // Load scans after patient details are shown
    await loadPatientScans(patientId);
};

// Make functions globally available
window.openUploadScanModal = openUploadScanModal;
window.closeUploadScanModal = closeUploadScanModal;
window.uploadScan = uploadScan;
window.viewScanImage = viewScanImage;
window.analyzeScan = analyzeScan;
window.deleteScan = deleteScan;


// ============================================
// REPORT GENERATION
// ============================================

async function loadPatientsForReport() {
    try {
        const result = await API.listPatients();
        const select = document.getElementById('reportPatientSelect');
        
        select.innerHTML = '<option value="">-- Choose a patient --</option>';
        
        result.content.forEach(patient => {
            const option = document.createElement('option');
            option.value = patient.patientId;
            option.textContent = `${patient.anonymizedCode} (Created: ${new Date(patient.createdAt).toLocaleDateString()})`;
            select.appendChild(option);
        });
    } catch (error) {
        alert('Failed to load patients: ' + error.message);
    }
}

async function generateReport() {
    const patientId = document.getElementById('reportPatientSelect').value;
    
    if (!patientId) {
        alert('Please select a patient');
        return;
    }
    
    try {
        // Show loading
        document.getElementById('reportDisplay').innerHTML = '<p style="text-align: center; padding: 40px;">⏳ Generating report...</p>';
        document.getElementById('reportDisplay').style.display = 'block';
        
        // Fetch all data
        const patient = await API.getPatient(patientId);
        const scansResult = await API.getPatientScans(patientId);
        const predictionsResult = await API.getPatientPredictions(patientId);
        
        const scans = scansResult.content || [];
        const predictions = predictionsResult.content || [];
        
        // Generate report HTML
        const reportHTML = generateReportHTML(patient, scans, predictions);
        
        document.getElementById('reportDisplay').innerHTML = reportHTML;
        
    } catch (error) {
        document.getElementById('reportDisplay').innerHTML = 
            '<p style="text-align: center; padding: 40px; color: #dc2626;">Failed to generate report: ' + error.message + '</p>';
    }
}

function generateReportHTML(patient, scans, predictions) {
    const p = patient.patientData;
    const currentDate = new Date().toLocaleString();
    
    return `
        <div style="max-width: 800px; margin: 0 auto;">
            <!-- Header -->
            <div style="text-align: center; border-bottom: 3px solid #333; padding-bottom: 20px; margin-bottom: 30px;">
                <h1 style="margin: 0; color: #333; font-size: 2rem;">CHD-EPICS Medical Report</h1>
                <p style="margin: 10px 0 0 0; color: #666;">Chronic Heart Defects - ECG Prediction System</p>
                <p style="margin: 5px 0 0 0; color: #999; font-size: 0.9rem;">Generated: ${currentDate}</p>
            </div>
            
            <!-- Patient Information -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">Patient Information</h2>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                    <div><strong>Name:</strong> ${p.name}</div>
                    <div><strong>Age:</strong> ${p.age} years</div>
                    <div><strong>Gender:</strong> ${p.gender}</div>
                    <div><strong>Date of Birth:</strong> ${p.dateOfBirth || 'N/A'}</div>
                    <div><strong>Phone:</strong> ${p.phone || 'N/A'}</div>
                    <div><strong>Patient Code:</strong> ${patient.anonymizedCode}</div>
                </div>
            </div>
            
            <!-- Medical History -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">Medical History</h2>
                <p style="line-height: 1.6; margin: 0;">${p.medicalHistory || 'No medical history recorded'}</p>
            </div>
            
            <!-- Current Diagnosis -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">Current Diagnosis</h2>
                <p style="line-height: 1.6; margin: 0;">${p.diagnosis || 'No diagnosis recorded'}</p>
            </div>
            
            <!-- ECG Scans Summary -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">ECG Scans Summary</h2>
                ${scans.length > 0 ? `
                    <p><strong>Total Scans:</strong> ${scans.length}</p>
                    <ul style="margin: 10px 0; padding-left: 20px;">
                        ${scans.map(scan => `
                            <li style="margin-bottom: 8px;">
                                Scan uploaded on ${new Date(scan.uploadedAt).toLocaleString()}
                            </li>
                        `).join('')}
                    </ul>
                ` : '<p>No ECG scans uploaded yet.</p>'}
            </div>
            
            <!-- ML Predictions -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">ML Analysis Results</h2>
                ${predictions.length > 0 ? `
                    <p><strong>Total Analyses:</strong> ${predictions.length}</p>
                    <div style="margin-top: 15px;">
                        ${predictions.map((pred, index) => `
                            <div style="background: white; padding: 15px; border-radius: 6px; margin-bottom: 12px; border-left: 4px solid ${pred.confidenceScore > 0.8 ? '#16a34a' : '#f59e0b'};">
                                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                                    <strong style="font-size: 1.1rem;">Analysis #${predictions.length - index}</strong>
                                    <span style="color: #666; font-size: 0.9rem;">${new Date(pred.createdAt).toLocaleString()}</span>
                                </div>
                                <div style="margin-bottom: 5px;">
                                    <strong>Predicted Diagnosis:</strong> 
                                    <span style="color: #333; font-size: 1.05rem;">${pred.predictedLabel}</span>
                                </div>
                                <div>
                                    <strong>Confidence Score:</strong> 
                                    <span style="color: ${pred.confidenceScore > 0.8 ? '#16a34a' : '#f59e0b'}; font-weight: 600;">
                                        ${(pred.confidenceScore * 100).toFixed(2)}%
                                    </span>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                ` : '<p>No ML analyses performed yet.</p>'}
            </div>
            
            <!-- Doctor's Notes -->
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 25px;">
                <h2 style="margin: 0 0 15px 0; color: #333; font-size: 1.3rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">Doctor's Notes</h2>
                <p style="line-height: 1.6; margin: 0;">${p.notes || 'No additional notes'}</p>
            </div>
            
            <!-- Footer -->
            <div style="text-align: center; margin-top: 40px; padding-top: 20px; border-top: 2px solid #e0e0e0; color: #666; font-size: 0.9rem;">
                <p style="margin: 0;">This report was generated automatically by CHD-EPICS</p>
                <p style="margin: 5px 0 0 0;">For medical use only. Confidential patient information.</p>
            </div>
            
            <!-- Action Buttons -->
            <div style="text-align: center; margin-top: 30px;">
                <button onclick="printReport()" class="btn-primary" style="margin-right: 10px;">
                    🖨️ Print Report
                </button>
                <button onclick="downloadReportPDF()" class="btn-primary">
                    📥 Download PDF
                </button>
            </div>
        </div>
    `;
}

function printReport() {
    window.print();
}

function downloadReportPDF() {
    alert('PDF download feature coming soon!\n\nFor now, you can use "Print Report" and save as PDF from the print dialog.');
}

// Make functions globally available
window.loadPatientsForReport = loadPatientsForReport;
window.generateReport = generateReport;
window.printReport = printReport;
window.downloadReportPDF = downloadReportPDF;


// ============================================
// ACCOUNT DETAILS
// ============================================

async function showAccountDetails() {
    try {
        const user = await API.getCurrentUser();
        
        const content = document.getElementById('accountDetailsContent');
        content.innerHTML = `
            <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 15px;">
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Full Name</p>
                    <p style="margin: 0; font-weight: 600; font-size: 1.1rem;">${user.fullName}</p>
                </div>
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Email</p>
                    <p style="margin: 0; font-weight: 600;">${user.email}</p>
                </div>
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Phone</p>
                    <p style="margin: 0; font-weight: 600;">${user.phone || 'Not provided'}</p>
                </div>
                <div style="margin-bottom: 15px;">
                    <p style="margin: 0 0 5px 0; color: #666; font-size: 0.85rem;">Doctor ID</p>
                    <p style="margin: 0; font-weight: 600; font-size: 0.85rem; color: #666;">${user.doctorId}</p>
                </div>
            </div>
            
            <div style="background: #e8e8e8; padding: 15px; border-radius: 6px; font-size: 0.85rem; color: #555;">
                <p style="margin: 0 0 8px 0;"><strong>Account Status:</strong> <span style="color: ${user.isActive ? '#16a34a' : '#dc2626'};">${user.isActive ? 'Active' : 'Inactive'}</span></p>
                <p style="margin: 0 0 8px 0;"><strong>MFA Enabled:</strong> ${user.mfaEnabled ? 'Yes' : 'No'}</p>
                <p style="margin: 0;"><strong>Account Created:</strong> ${new Date(user.createdAt).toLocaleString()}</p>
            </div>
        `;
        
        document.getElementById('accountDetailsModal').style.display = 'flex';
        
    } catch (error) {
        alert('Failed to load account details: ' + error.message);
    }
}

function closeAccountDetails() {
    document.getElementById('accountDetailsModal').style.display = 'none';
}

// Make functions globally available
window.showAccountDetails = showAccountDetails;
window.closeAccountDetails = closeAccountDetails;
