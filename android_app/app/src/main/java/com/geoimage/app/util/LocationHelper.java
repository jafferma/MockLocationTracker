package com.geoimage.app.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.geoimage.app.model.Location;

/**
 * Utility class for handling location-related operations
 */
public class LocationHelper {
    private static final String TAG = "LocationHelper";
    
    /**
     * Check if the app has location permissions
     *
     * @param context Application context
     * @return true if has permission, false otherwise
     */
    public static boolean hasLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if mock location is allowed
     *
     * @param context Application context
     * @return true if allowed, false otherwise
     */
    public static boolean isMockLocationEnabled(Context context) {
        boolean mockLocationsEnabled = false;
        
        try {
            // Different methods to check depending on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For Android 6.0+
                LocationManager locationManager = 
                        (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                mockLocationsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                        !android.provider.Settings.Secure.getString(context.getContentResolver(),
                                "mock_location").equals("0");
            } else {
                // For older Android versions
                mockLocationsEnabled = !android.provider.Settings.Secure.getString(
                        context.getContentResolver(),
                        android.provider.Settings.Secure.ALLOW_MOCK_LOCATION).equals("0");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking mock location settings: " + e.getMessage());
        }
        
        return mockLocationsEnabled;
    }
    
    /**
     * Set a mock location
     *
     * @param context Application context
     * @param location Location to mock
     * @return true if successful, false otherwise
     */
    public static boolean setMockLocation(Context context, Location location) {
        if (!hasLocationPermission(context) || !isMockLocationEnabled(context)) {
            return false;
        }
        
        LocationManager locationManager = 
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        try {
            locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    0,
                    0);
                    
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            
            android.location.Location mockLocation = new android.location.Location(LocationManager.GPS_PROVIDER);
            mockLocation.setLatitude(location.getLatitude());
            mockLocation.setLongitude(location.getLongitude());
            mockLocation.setAltitude(0);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setAccuracy(1.0f);
            mockLocation.setElapsedRealtimeNanos(System.nanoTime());
            
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception setting mock location: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error setting mock location: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disable any active mock locations
     *
     * @param context Application context
     */
    public static void disableMockLocation(Context context) {
        if (!hasLocationPermission(context)) {
            return;
        }
        
        LocationManager locationManager = 
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling mock location: " + e.getMessage());
        }
    }
    
    /**
     * Format coordinates as a user-friendly string
     *
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Formatted coordinate string
     */
    public static String formatCoordinates(double latitude, double longitude) {
        char latDirection = latitude >= 0 ? 'N' : 'S';
        char lngDirection = longitude >= 0 ? 'E' : 'W';
        
        return String.format(java.util.Locale.US, "%.4f° %s, %.4f° %s",
                Math.abs(latitude), latDirection,
                Math.abs(longitude), lngDirection);
    }
}