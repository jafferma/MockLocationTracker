document.addEventListener('DOMContentLoaded', function() {
    // ========== Variables ==========
    let map, modalMap;
    let selectedLocation = null;
    let currentFile = null;
    let gallery = [];
    
    // DOM elements
    const mapElement = document.getElementById('map');
    const searchInput = document.getElementById('location-search');
    const searchBtn = document.getElementById('search-btn');
    const selectedLocationEl = document.getElementById('selected-location');
    const latDisplay = document.getElementById('lat-display');
    const lngDisplay = document.getElementById('lng-display');
    const uploadArea = document.getElementById('upload-area');
    const fileInput = document.getElementById('file-input');
    const uploadPreview = document.getElementById('upload-preview');
    const previewImage = document.getElementById('preview-image');
    const previewFilename = document.getElementById('preview-filename');
    const removePreviewBtn = document.getElementById('remove-preview');
    const uploadBtn = document.getElementById('upload-btn');
    const uploadStatus = document.getElementById('upload-status');
    const imageGallery = document.getElementById('image-gallery');
    const imageModal = document.getElementById('image-modal');
    const closeModal = document.querySelector('.close-modal');
    const modalImage = document.getElementById('modal-image');
    const modalFilename = document.getElementById('modal-filename');
    const modalLocation = document.getElementById('modal-location');
    const modalCoordinates = document.getElementById('modal-coordinates');
    const modalDate = document.getElementById('modal-date');
    const modalMapEl = document.getElementById('modal-map');
    
    // ========== Initialize Map ==========
    function initMap() {
        // Default view centered on a neutral location (0,0)
        map = L.map(mapElement).setView([0, 0], 2);
        
        // Add base map layer (OpenStreetMap)
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).addTo(map);
        
        // Add geocoder control for searching
        const geocoder = L.Control.geocoder({
            defaultMarkGeocode: false,
            placeholder: 'Search...',
            errorMessage: 'Nothing found.',
            suggestMinLength: 3,
            suggestTimeout: 250,
            queryMinLength: 1
        }).addTo(map);
        
        // Add marker on location selection
        let marker = null;
        
        // Handle location selection on map click
        map.on('click', function(e) {
            selectLocation(e.latlng.lat, e.latlng.lng);
            
            // Reverse geocode to get location name
            reverseGeocode(e.latlng.lat, e.latlng.lng);
        });
        
        // Handle results from geocoder
        geocoder.on('markgeocode', function(e) {
            const latlng = e.geocode.center;
            map.setView(latlng, 13);
            
            selectLocation(latlng.lat, latlng.lng, e.geocode.name);
        });
        
        // Function to select a location and update marker
        function selectLocation(lat, lng, name = null) {
            // Update selected location data
            selectedLocation = {
                lat: lat,
                lng: lng,
                name: name || 'Custom location'
            };
            
            // Update marker
            if (marker) {
                marker.setLatLng([lat, lng]);
            } else {
                marker = L.marker([lat, lng]).addTo(map);
            }
            
            // Update UI display
            updateLocationDisplay();
            
            // Enable upload button if file is selected
            updateUploadButtonState();
        }
        
        // Function to reverse geocode coordinates to get location name
        function reverseGeocode(lat, lng) {
            fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`)
                .then(response => response.json())
                .then(data => {
                    if (data && data.display_name) {
                        selectedLocation.name = data.display_name;
                        updateLocationDisplay();
                    }
                })
                .catch(error => {
                    console.error('Error reverse geocoding:', error);
                });
        }
    }
    
    // ========== Functions ==========
    // Update the display of selected location
    function updateLocationDisplay() {
        if (selectedLocation) {
            selectedLocationEl.textContent = selectedLocation.name;
            latDisplay.textContent = selectedLocation.lat.toFixed(6);
            lngDisplay.textContent = selectedLocation.lng.toFixed(6);
        } else {
            selectedLocationEl.textContent = 'None';
            latDisplay.textContent = '--';
            lngDisplay.textContent = '--';
        }
    }
    
    // Handle file selection for upload
    function handleFileSelection(file) {
        if (!file) return;
        
        // Check if file is an image
        if (!file.type.match('image.*')) {
            showUploadStatus('Please select an image file.', 'error');
            return;
        }
        
        // Store current file and update preview
        currentFile = file;
        previewFilename.textContent = file.name;
        
        // Create preview
        const reader = new FileReader();
        reader.onload = function(e) {
            previewImage.src = e.target.result;
            uploadArea.style.display = 'none';
            uploadPreview.style.display = 'block';
        };
        reader.readAsDataURL(file);
        
        // Update upload button state
        updateUploadButtonState();
    }
    
    // Update upload button enabled/disabled state
    function updateUploadButtonState() {
        uploadBtn.disabled = !(currentFile && selectedLocation);
    }
    
    // Show upload status message
    function showUploadStatus(message, type = 'info') {
        uploadStatus.textContent = message;
        uploadStatus.className = `status ${type}`;
        
        // Clear the message after 5 seconds
        setTimeout(() => {
            uploadStatus.textContent = '';
            uploadStatus.className = '';
        }, 5000);
    }
    
    // Reset the upload form
    function resetUploadForm() {
        currentFile = null;
        uploadArea.style.display = 'block';
        uploadPreview.style.display = 'none';
        previewImage.src = '';
        previewFilename.textContent = '';
        fileInput.value = '';
        updateUploadButtonState();
    }
    
    // Upload image with location data
    function uploadImage() {
        if (!currentFile || !selectedLocation) {
            showUploadStatus('Please select both an image and a location.', 'error');
            return;
        }
        
        // Create form data for upload
        const formData = new FormData();
        formData.append('file', currentFile);
        formData.append('lat', selectedLocation.lat);
        formData.append('lng', selectedLocation.lng);
        formData.append('location_name', selectedLocation.name);
        
        // Show loading status
        showUploadStatus('Uploading image...', 'info');
        uploadBtn.disabled = true;
        
        // Send to server
        fetch('/api/upload', {
            method: 'POST',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data.error) {
                showUploadStatus(data.error, 'error');
            } else {
                showUploadStatus('Image uploaded successfully!', 'success');
                resetUploadForm();
                loadGalleryImages();
            }
        })
        .catch(error => {
            showUploadStatus('Upload failed: ' + error, 'error');
        })
        .finally(() => {
            updateUploadButtonState();
        });
    }
    
    // Load gallery images from server
    function loadGalleryImages() {
        fetch('/api/images')
            .then(response => response.json())
            .then(data => {
                gallery = data.images;
                renderGallery();
            })
            .catch(error => {
                console.error('Error loading gallery:', error);
                imageGallery.innerHTML = `
                    <div class="empty-gallery">
                        <i class="fas fa-exclamation-circle"></i>
                        <p>Error loading images: ${error}</p>
                    </div>
                `;
            });
    }
    
    // Render gallery images
    function renderGallery() {
        if (!gallery || gallery.length === 0) {
            imageGallery.innerHTML = `
                <div class="empty-gallery">
                    <i class="fas fa-images"></i>
                    <p>No images yet. Upload some!</p>
                </div>
            `;
            return;
        }
        
        let galleryHTML = '';
        
        gallery.forEach(image => {
            galleryHTML += `
                <div class="gallery-item" data-id="${image.id}">
                    <img src="data:image/jpeg;base64,${image.base64}" alt="${image.filename}">
                    <div class="gallery-info">
                        <p class="location">${image.location_name.substring(0, 30)}${image.location_name.length > 30 ? '...' : ''}</p>
                    </div>
                </div>
            `;
        });
        
        imageGallery.innerHTML = galleryHTML;
        
        // Add click event listeners to gallery items
        document.querySelectorAll('.gallery-item').forEach(item => {
            item.addEventListener('click', function() {
                const imageId = this.getAttribute('data-id');
                openImageModal(imageId);
            });
        });
    }
    
    // Open image details modal
    function openImageModal(imageId) {
        const image = gallery.find(img => img.id == imageId);
        
        if (!image) return;
        
        // Set modal content
        modalImage.src = `data:image/jpeg;base64,${image.base64}`;
        modalFilename.textContent = image.filename;
        modalLocation.textContent = image.location_name;
        modalCoordinates.textContent = `${image.lat.toFixed(6)}, ${image.lng.toFixed(6)}`;
        
        // Format date from timestamp
        const timestamp = image.timestamp;
        const formattedDate = `${timestamp.substring(0, 4)}-${timestamp.substring(4, 6)}-${timestamp.substring(6, 8)} ${timestamp.substring(9, 11)}:${timestamp.substring(11, 13)}:${timestamp.substring(13, 15)}`;
        modalDate.textContent = formattedDate;
        
        // Show modal
        imageModal.style.display = 'block';
        
        // Initialize modal map
        setTimeout(() => {
            if (!modalMap) {
                modalMap = L.map(modalMapEl).setView([image.lat, image.lng], 13);
                
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                    maxZoom: 19
                }).addTo(modalMap);
            } else {
                modalMap.setView([image.lat, image.lng], 13);
            }
            
            // Add marker
            L.marker([image.lat, image.lng]).addTo(modalMap);
            
            // Fix map rendering issue
            modalMap.invalidateSize();
        }, 100);
    }
    
    // Close image modal
    function closeImageModal() {
        imageModal.style.display = 'none';
    }
    
    // Search for a location
    function searchLocation() {
        const query = searchInput.value.trim();
        
        if (!query) return;
        
        fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}`)
            .then(response => response.json())
            .then(data => {
                if (data && data.length > 0) {
                    const result = data[0];
                    const lat = parseFloat(result.lat);
                    const lng = parseFloat(result.lon);
                    
                    map.setView([lat, lng], 13);
                    selectLocation(lat, lng, result.display_name);
                } else {
                    showUploadStatus('Location not found. Try another search.', 'error');
                }
            })
            .catch(error => {
                console.error('Error searching location:', error);
                showUploadStatus('Error searching for location.', 'error');
            });
    }
    
    // Helper function to select a location
    function selectLocation(lat, lng, name = null) {
        selectedLocation = {
            lat: lat,
            lng: lng,
            name: name || 'Custom location'
        };
        
        // Update marker on the map
        if (window.currentMarker) {
            window.currentMarker.setLatLng([lat, lng]);
        } else {
            window.currentMarker = L.marker([lat, lng]).addTo(map);
        }
        
        // Update displayed information
        updateLocationDisplay();
        updateUploadButtonState();
    }
    
    // ========== Event Listeners ==========
    // Search button click
    searchBtn.addEventListener('click', searchLocation);
    
    // Search input enter key
    searchInput.addEventListener('keyup', function(e) {
        if (e.key === 'Enter') {
            searchLocation();
        }
    });
    
    // File input change
    fileInput.addEventListener('change', function() {
        if (this.files && this.files[0]) {
            handleFileSelection(this.files[0]);
        }
    });
    
    // Upload area click
    uploadArea.addEventListener('click', function() {
        fileInput.click();
    });
    
    // Upload area drag & drop
    uploadArea.addEventListener('dragover', function(e) {
        e.preventDefault();
        this.classList.add('dragover');
    });
    
    uploadArea.addEventListener('dragleave', function() {
        this.classList.remove('dragover');
    });
    
    uploadArea.addEventListener('drop', function(e) {
        e.preventDefault();
        this.classList.remove('dragover');
        
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            handleFileSelection(e.dataTransfer.files[0]);
        }
    });
    
    // Remove preview button
    removePreviewBtn.addEventListener('click', resetUploadForm);
    
    // Upload button
    uploadBtn.addEventListener('click', uploadImage);
    
    // Modal close button
    closeModal.addEventListener('click', closeImageModal);
    
    // Close modal when clicking outside
    window.addEventListener('click', function(e) {
        if (e.target === imageModal) {
            closeImageModal();
        }
    });
    
    // ========== Initialization ==========
    // Initialize the map
    initMap();
    
    // Load gallery images
    loadGalleryImages();
});
