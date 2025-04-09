package com.geoimage.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.geoimage.app.R;
import com.geoimage.app.model.Location;
import com.geoimage.app.util.AdManager;
import com.geoimage.app.util.ImageProcessor;
import com.geoimage.app.util.LocationHelper;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final int REQUEST_CAMERA_PERMISSION = 1002;
    private static final int REQUEST_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_SELECT_LOCATION = 2001;
    private static final int REQUEST_TAKE_PHOTO = 2002;
    private static final int REQUEST_PICK_IMAGE = 2003;
    
    private TextView locationNameText;
    private TextView coordinatesText;
    private Button selectLocationButton;
    private Button mockLocationButton;
    private Button takePhotoButton;
    private Button selectImageButton;
    private Button viewGalleryButton;
    
    private Location currentLocation;
    private File currentPhotoFile;
    private Uri currentPhotoUri;
    private boolean isMockLocationActive = false;

    private AdView adView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        locationNameText = findViewById(R.id.locationNameText);
        coordinatesText = findViewById(R.id.coordinatesText);
        selectLocationButton = findViewById(R.id.selectLocationButton);
        mockLocationButton = findViewById(R.id.mockLocationButton);
        takePhotoButton = findViewById(R.id.takePhotoButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        viewGalleryButton = findViewById(R.id.viewGalleryButton);
        
        // Set click listeners
        selectLocationButton.setOnClickListener(v -> openLocationSelection());
        mockLocationButton.setOnClickListener(v -> toggleMockLocation());
        takePhotoButton.setOnClickListener(v -> checkCameraPermissionAndTakePhoto());
        selectImageButton.setOnClickListener(v -> checkStoragePermissionAndPickImage());
        viewGalleryButton.setOnClickListener(v -> openGallery());
        
        // Update UI state
        updateLocationDisplay();
        updateButtonStates();
        
        // Initialize banner ad
        adView = findViewById(R.id.adView);
        AdManager.initBannerAd(adView);
        
        // Preload interstitial and rewarded ads
        AdManager.loadInterstitialAd(this);
        AdManager.loadRewardedAd(this);
    }
    
    @Override
    protected void onDestroy() {
        // Ensure we clean up any mock location when the app is closed
        if (isMockLocationActive) {
            LocationHelper.disableMockLocation(this);
        }
        
        // Clean up the banner ad to avoid memory leaks
        if (adView != null) {
            adView.destroy();
        }
        
        super.onDestroy();
    }
    
    /**
     * Update the location display in the UI
     */
    private void updateLocationDisplay() {
        if (currentLocation != null) {
            String name = currentLocation.getName();
            locationNameText.setText(name != null && !name.isEmpty() ? name : getString(R.string.selected_location));
            
            String coordinates = LocationHelper.formatCoordinates(
                    currentLocation.getLatitude(), 
                    currentLocation.getLongitude());
            coordinatesText.setText(coordinates);
        } else {
            locationNameText.setText(R.string.no_location_selected);
            coordinatesText.setText(R.string.lat_lng_placeholder);
        }
    }
    
    /**
     * Update button states based on app state
     */
    private void updateButtonStates() {
        boolean hasLocation = currentLocation != null;
        
        mockLocationButton.setEnabled(hasLocation && LocationHelper.isMockLocationEnabled(this));
        mockLocationButton.setText(isMockLocationActive ? 
                R.string.mock_location_disabled : R.string.set_as_mock_location);
    }
    
    /**
     * Open location selection activity
     */
    private void openLocationSelection() {
        Intent intent = new Intent(this, LocationSelectionActivity.class);
        if (currentLocation != null) {
            intent.putExtra("current_location", currentLocation);
        }
        startActivityForResult(intent, REQUEST_SELECT_LOCATION);
    }
    
    /**
     * Toggle mock location on/off
     */
    private void toggleMockLocation() {
        if (currentLocation == null) {
            Toast.makeText(this, R.string.select_location_first, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!LocationHelper.hasLocationPermission(this)) {
            requestLocationPermission();
            return;
        }
        
        if (!LocationHelper.isMockLocationEnabled(this)) {
            Toast.makeText(this, R.string.enable_mock_location_provider, Toast.LENGTH_LONG).show();
            return;
        }
        
        if (isMockLocationActive) {
            // Disable mock location
            LocationHelper.disableMockLocation(this);
            isMockLocationActive = false;
            Toast.makeText(this, R.string.mock_location_disabled, Toast.LENGTH_SHORT).show();
        } else {
            // Enable mock location
            boolean success = LocationHelper.setMockLocation(this, currentLocation);
            if (success) {
                isMockLocationActive = true;
                Toast.makeText(this, 
                        String.format(getString(R.string.mock_location_enabled), 
                                currentLocation.getName()), 
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.mock_location_error, Toast.LENGTH_SHORT).show();
            }
        }
        
        updateButtonStates();
    }
    
    /**
     * Check for camera permission and take photo if granted
     */
    private void checkCameraPermissionAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 
                    REQUEST_CAMERA_PERMISSION);
        } else {
            takePhoto();
        }
    }
    
    /**
     * Launch camera intent to take a photo
     */
    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the file where the photo should go
            try {
                currentPhotoFile = ImageProcessor.createImageFile(this);
            } catch (IOException ex) {
                // Error occurred while creating the File
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Continue only if the file was successfully created
            if (currentPhotoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        "com.geoimage.app.fileprovider",
                        currentPhotoFile);
                
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
    
    /**
     * Check for storage permission and pick image if granted
     */
    private void checkStoragePermissionAndPickImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    REQUEST_STORAGE_PERMISSION);
        } else {
            pickImage();
        }
    }
    
    /**
     * Launch gallery intent to pick an image
     */
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }
    
    /**
     * Open gallery activity
     */
    private void openGallery() {
        Intent intent = new Intent(this, GalleryActivity.class);
        startActivity(intent);
    }
    
    /**
     * Process the selected or captured image
     *
     * @param imageUri URI of the image to process
     */
    private void processImage(Uri imageUri) {
        if (currentLocation == null) {
            Toast.makeText(this, R.string.select_location_first, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Start image preview activity
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putExtra("image_uri", imageUri.toString());
        intent.putExtra("location", currentLocation);
        startActivity(intent);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case REQUEST_LOCATION_PERMISSION:
                    updateButtonStates();
                    break;
                case REQUEST_CAMERA_PERMISSION:
                    takePhoto();
                    break;
                case REQUEST_STORAGE_PERMISSION:
                    pickImage();
                    break;
            }
        } else {
            // Permission denied
            String message = "";
            switch (requestCode) {
                case REQUEST_LOCATION_PERMISSION:
                    message = getString(R.string.location_permission_needed);
                    break;
                case REQUEST_CAMERA_PERMISSION:
                    message = getString(R.string.camera_permission_needed);
                    break;
                case REQUEST_STORAGE_PERMISSION:
                    message = getString(R.string.storage_permission_needed);
                    break;
            }
            
            if (!message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_SELECT_LOCATION:
                    if (data != null && data.hasExtra("selected_location")) {
                        currentLocation = data.getParcelableExtra("selected_location");
                        updateLocationDisplay();
                        updateButtonStates();
                    }
                    break;
                    
                case REQUEST_TAKE_PHOTO:
                    if (currentPhotoUri != null) {
                        processImage(currentPhotoUri);
                    }
                    break;
                    
                case REQUEST_PICK_IMAGE:
                    if (data != null && data.getData() != null) {
                        processImage(data.getData());
                    }
                    break;
            }
        }
    }
    
    /**
     * Request location permission
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
    }
}