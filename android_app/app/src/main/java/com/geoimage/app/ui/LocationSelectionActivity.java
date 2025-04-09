package com.geoimage.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.geoimage.app.R;
import com.geoimage.app.model.Location;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationSelectionActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    
    private GoogleMap map;
    private EditText searchEditText;
    private ImageButton searchButton;
    private ImageButton myLocationButton;
    private TextView selectedLocationText;
    private TextView selectedCoordinatesText;
    private Button confirmButton;
    
    private Marker currentMarker;
    private Location selectedLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_selection);
        
        // Initialize views
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        myLocationButton = findViewById(R.id.myLocationButton);
        selectedLocationText = findViewById(R.id.selectedLocationText);
        selectedCoordinatesText = findViewById(R.id.selectedCoordinatesText);
        confirmButton = findViewById(R.id.confirmButton);
        
        // Set click listeners
        searchButton.setOnClickListener(v -> searchLocation());
        myLocationButton.setOnClickListener(v -> getMyLocation());
        confirmButton.setOnClickListener(v -> confirmSelection());
        
        // Get map fragment
        SupportMapFragment mapFragment = 
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        // Check if we have a previously selected location
        if (getIntent().hasExtra("current_location")) {
            selectedLocation = getIntent().getParcelableExtra("current_location");
            updateSelectedLocationDisplay();
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        
        // Set up map UI settings
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        
        // Set map click listener
        map.setOnMapClickListener(this::selectLocationOnMap);
        
        // Check and request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
        
        // If we have a previously selected location, show it on the map
        if (selectedLocation != null) {
            LatLng position = new LatLng(selectedLocation.getLatitude(), selectedLocation.getLongitude());
            addMarkerToMap(position, selectedLocation.getName());
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
        } else {
            // Default location (world view)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2));
        }
    }
    
    /**
     * Add a marker to the map
     *
     * @param position LatLng position for the marker
     * @param title Title for the marker
     */
    private void addMarkerToMap(LatLng position, String title) {
        if (currentMarker != null) {
            currentMarker.remove();
        }
        
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(title != null ? title : "Selected Location");
                
        currentMarker = map.addMarker(markerOptions);
    }
    
    /**
     * Select a location on the map
     *
     * @param latLng The selected LatLng
     */
    private void selectLocationOnMap(LatLng latLng) {
        // Reverse geocode to get location name
        reverseGeocode(latLng.latitude, latLng.longitude);
    }
    
    /**
     * Get current user location
     */
    private void getMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }
        
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                android.location.Location lastKnownLocation = 
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                
                if (lastKnownLocation == null) {
                    lastKnownLocation = 
                            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastKnownLocation != null) {
                    LatLng myLocation = new LatLng(
                            lastKnownLocation.getLatitude(), 
                            lastKnownLocation.getLongitude());
                    
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 15));
                    reverseGeocode(myLocation.latitude, myLocation.longitude);
                } else {
                    Toast.makeText(this, "Unable to get your current location", Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException e) {
                Toast.makeText(this, "Location permission issue", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Search for a location by name
     */
    private void searchLocation() {
        String query = searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng position = new LatLng(address.getLatitude(), address.getLongitude());
                
                // Build a descriptive name for the location
                StringBuilder locationName = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) locationName.append(", ");
                    locationName.append(address.getAddressLine(i));
                }
                
                // Use the first line of the address if full address is too long
                String name = locationName.toString();
                if (name.isEmpty()) {
                    name = address.getFeatureName();
                    if (address.getLocality() != null) {
                        name += ", " + address.getLocality();
                    }
                    if (address.getCountryName() != null) {
                        name += ", " + address.getCountryName();
                    }
                }
                
                // Create location object
                selectedLocation = new Location(
                        address.getLatitude(),
                        address.getLongitude(),
                        name);
                
                // Add marker and move camera
                addMarkerToMap(position, name);
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
                
                // Update display
                updateSelectedLocationDisplay();
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Geocoding error, please try again", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Reverse geocode coordinates to get location name
     *
     * @param latitude Latitude
     * @param longitude Longitude
     */
    private void reverseGeocode(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                
                // Build location name from address
                StringBuilder locationName = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) locationName.append(", ");
                    locationName.append(address.getAddressLine(i));
                }
                
                // Use a short version if full address is empty
                String name = locationName.toString();
                if (name.isEmpty()) {
                    name = address.getFeatureName();
                    if (address.getLocality() != null) {
                        name += ", " + address.getLocality();
                    }
                    if (address.getCountryName() != null) {
                        name += ", " + address.getCountryName();
                    }
                }
                
                // If we still don't have a name, use the coordinates
                if (name == null || name.isEmpty()) {
                    name = String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
                }
                
                // Create location object
                selectedLocation = new Location(latitude, longitude, name);
                
                // Add marker
                LatLng position = new LatLng(latitude, longitude);
                addMarkerToMap(position, name);
                
                // Update display
                updateSelectedLocationDisplay();
            } else {
                // No address found, use coordinates as the name
                String name = String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
                selectedLocation = new Location(latitude, longitude, name);
                
                LatLng position = new LatLng(latitude, longitude);
                addMarkerToMap(position, name);
                
                updateSelectedLocationDisplay();
            }
        } catch (IOException e) {
            // Handle geocoding failure
            String name = String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
            selectedLocation = new Location(latitude, longitude, name);
            
            LatLng position = new LatLng(latitude, longitude);
            addMarkerToMap(position, name);
            
            updateSelectedLocationDisplay();
        }
    }
    
    /**
     * Update the UI to display the selected location
     */
    private void updateSelectedLocationDisplay() {
        if (selectedLocation != null) {
            selectedLocationText.setText(selectedLocation.getName());
            selectedCoordinatesText.setText(String.format(Locale.US, 
                    "%.6f, %.6f", 
                    selectedLocation.getLatitude(), 
                    selectedLocation.getLongitude()));
            confirmButton.setEnabled(true);
        } else {
            selectedLocationText.setText(R.string.tap_on_map_to_select);
            selectedCoordinatesText.setText("");
            confirmButton.setEnabled(false);
        }
    }
    
    /**
     * Confirm the selected location and return to previous activity
     */
    private void confirmSelection() {
        if (selectedLocation != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_location", selectedLocation);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (map != null) {
                    try {
                        map.setMyLocationEnabled(true);
                    } catch (SecurityException e) {
                        // Permission should be granted at this point, but handle just in case
                        Toast.makeText(this, "Location permission issue", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, R.string.location_permission_needed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}