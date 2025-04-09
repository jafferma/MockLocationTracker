document.addEventListener('DOMContentLoaded', function() {
    // ========== Variables ==========
    let map, modalMap;
    let selectedLocation = null;
    let currentFile = null;
    let gallery = [];
    let locationHistory = [];
    
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
    const locationHistoryEl = document.getElementById('location-history');
    
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
    
    // Save location button (if exists)
    const saveLocationBtn = document.getElementById('save-location-btn');
    if (saveLocationBtn) {
        saveLocationBtn.addEventListener('click', function() {
            saveCurrentLocation();
        });
    }
    
    // Save favorite location button (if exists)
    const saveFavoriteBtn = document.getElementById('save-favorite-btn');
    if (saveFavoriteBtn) {
        saveFavoriteBtn.addEventListener('click', function() {
            saveCurrentLocation(true);
        });
    };
    
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
    
    // ========== Location History Functions ==========
    // Load location history
    function loadLocationHistory() {
        fetch('/api/locations')
            .then(response => response.json())
            .then(data => {
                locationHistory = data.locations;
                renderLocationHistory();
            })
            .catch(error => {
                console.error('Error loading location history:', error);
                if (locationHistoryEl) {
                    locationHistoryEl.innerHTML = `
                        <div class="empty-history">
                            <i class="fas fa-exclamation-circle"></i>
                            <p>Error loading location history</p>
                        </div>
                    `;
                }
            });
    }
    
    // Render location history
    function renderLocationHistory() {
        if (!locationHistoryEl) return;
        
        if (!locationHistory || locationHistory.length === 0) {
            locationHistoryEl.innerHTML = `
                <div class="empty-history">
                    <i class="fas fa-map-marker-alt"></i>
                    <p>No location history yet</p>
                </div>
            `;
            return;
        }
        
        let historyHTML = '<div class="history-list">';
        
        // Show only the top 10 most recent/used locations
        const locationsToShow = locationHistory.slice(0, 10);
        
        locationsToShow.forEach(location => {
            // Format the date
            const lastUsed = new Date(location.last_used);
            const formattedDate = lastUsed.toLocaleDateString();
            
            // Truncate long location names
            const displayName = location.location_name.length > 30 
                ? location.location_name.substring(0, 30) + '...' 
                : location.location_name;
            
            // Star icon for favorites
            const starIcon = location.is_favorite 
                ? '<i class="fas fa-star favorite"></i>' 
                : '<i class="far fa-star"></i>';
            
            historyHTML += `
                <div class="history-item" data-id="${location.id}" data-lat="${location.lat}" data-lng="${location.lng}" data-name="${location.location_name}">
                    <div class="history-item-content">
                        <div class="history-location">
                            <i class="fas fa-map-marker-alt"></i>
                            <span>${displayName}</span>
                        </div>
                        <div class="history-meta">
                            <span class="history-use-count" title="Used ${location.use_count} times">
                                <i class="fas fa-redo"></i> ${location.use_count}
                            </span>
                            <span class="history-favorite" title="Toggle favorite">
                                ${starIcon}
                            </span>
                        </div>
                    </div>
                    <div class="history-coordinates">
                        ${location.lat.toFixed(6)}, ${location.lng.toFixed(6)}
                    </div>
                    <div class="history-actions">
                        <button class="use-location-btn" title="Use this location">Use</button>
                        <button class="delete-location-btn" title="Remove from history">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </div>
            `;
        });
        
        historyHTML += '</div>';
        locationHistoryEl.innerHTML = historyHTML;
        
        // Add event listeners to history items
        document.querySelectorAll('.history-item').forEach(item => {
            // Use location button
            const useBtn = item.querySelector('.use-location-btn');
            if (useBtn) {
                useBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const lat = parseFloat(item.getAttribute('data-lat'));
                    const lng = parseFloat(item.getAttribute('data-lng'));
                    const name = item.getAttribute('data-name');
                    
                    // Update map and selection
                    map.setView([lat, lng], 13);
                    selectLocation(lat, lng, name);
                    
                    // Update location use count
                    const locationId = item.getAttribute('data-id');
                    updateLocationUseCount(locationId);
                });
            }
            
            // Delete location button
            const deleteBtn = item.querySelector('.delete-location-btn');
            if (deleteBtn) {
                deleteBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const locationId = item.getAttribute('data-id');
                    deleteLocation(locationId);
                });
            }
            
            // Toggle favorite
            const favoriteBtn = item.querySelector('.history-favorite');
            if (favoriteBtn) {
                favoriteBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const locationId = item.getAttribute('data-id');
                    const location = locationHistory.find(loc => loc.id == locationId);
                    if (location) {
                        toggleFavoriteLocation(locationId, !location.is_favorite);
                    }
                });
            }
        });
    }
    
    // Update location use count
    function updateLocationUseCount(locationId) {
        fetch(`/api/locations/${locationId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({})  // Empty body to just increment use count
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Reload location history
                loadLocationHistory();
            } else {
                console.error('Error updating location:', data.error);
            }
        })
        .catch(error => {
            console.error('Error updating location:', error);
        });
    }
    
    // Delete location from history
    function deleteLocation(locationId) {
        fetch(`/api/locations/${locationId}`, {
            method: 'DELETE'
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Reload location history
                loadLocationHistory();
            } else {
                console.error('Error deleting location:', data.error);
            }
        })
        .catch(error => {
            console.error('Error deleting location:', error);
        });
    }
    
    // Toggle favorite status
    function toggleFavoriteLocation(locationId, isFavorite) {
        fetch(`/api/locations/${locationId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                is_favorite: isFavorite
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Reload location history
                loadLocationHistory();
            } else {
                console.error('Error updating favorite status:', data.error);
            }
        })
        .catch(error => {
            console.error('Error updating favorite status:', error);
        });
    }
    
    // Add current location to history (or update if exists)
    function saveCurrentLocation(isFavorite = false) {
        if (!selectedLocation) return;
        
        fetch('/api/locations', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                lat: selectedLocation.lat,
                lng: selectedLocation.lng,
                location_name: selectedLocation.name,
                is_favorite: isFavorite
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Reload location history
                loadLocationHistory();
                showUploadStatus('Location saved to history', 'success');
            } else {
                console.error('Error saving location:', data.error);
                showUploadStatus('Error saving location', 'error');
            }
        })
        .catch(error => {
            console.error('Error saving location:', error);
            showUploadStatus('Error saving location', 'error');
        });
    }
    
    // ========== Initialization ==========
    // Initialize the map
    initMap();
    
    // Load gallery images
    loadGalleryImages();
    
    // Load location history
    loadLocationHistory();
});
