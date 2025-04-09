package com.geoimage.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.geoimage.app.model.Location;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for processing images and handling geolocation data
 */
public class ImageProcessor {
    private static final String TAG = "ImageProcessor";
    private static final String FILE_PROVIDER_AUTHORITY = "com.geoimage.app.fileprovider";

    /**
     * Convert decimal coordinates to GPS DMS (Degrees, Minutes, Seconds) format
     * for EXIF data
     *
     * @param coordinate Latitude or longitude as decimal degrees
     * @return Array of rationals [degrees, minutes, seconds] for EXIF
     */
    private static double[] convertToDMS(double coordinate) {
        double absoluteCoordinate = Math.abs(coordinate);
        
        int degrees = (int) absoluteCoordinate;
        double minutesDouble = (absoluteCoordinate - degrees) * 60;
        int minutes = (int) minutesDouble;
        double seconds = (minutesDouble - minutes) * 60;
        
        return new double[] { degrees, minutes, seconds };
    }
    
    /**
     * Add geolocation metadata to an image file using Android's ExifInterface
     *
     * @param imagePath Full path to the image file
     * @param location Location to add to the image
     * @return true if successful, false otherwise
     */
    public static boolean addGeotagToImage(String imagePath, Location location) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            
            // Set GPS tags
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS");
            
            // Set latitude
            double lat = location.getLatitude();
            double[] latDMS = convertToDMS(lat);
            String latRef = lat >= 0 ? "N" : "S";
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef);
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, 
                    latDMS[0] + "/1," + latDMS[1] + "/1," + latDMS[2] + "/1000");
            
            // Set longitude
            double lng = location.getLongitude();
            double[] lngDMS = convertToDMS(lng);
            String lngRef = lng >= 0 ? "E" : "W";
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef);
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, 
                    lngDMS[0] + "/1," + lngDMS[1] + "/1," + lngDMS[2] + "/1000");
            
            // Add date/time if not present
            if (exif.getAttribute(ExifInterface.TAG_DATETIME) == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                exif.setAttribute(ExifInterface.TAG_DATETIME, sdf.format(new Date()));
            }
            
            // Save changes
            exif.saveAttributes();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error adding geotag to image: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a temporary image file
     *
     * @param context Application context
     * @return File object for the temporary image
     * @throws IOException if file creation fails
     */
    public static File createImageFile(Context context) throws IOException {
        // Create a unique filename with timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        
        // Create the storage directory if it does not exist
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        
        // Create the file
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }
    
    /**
     * Get a content URI for a file using FileProvider
     *
     * @param context Application context
     * @param file File to get URI for
     * @return Content URI for the file
     */
    public static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file);
    }
    
    /**
     * Copy an image from a content URI to a destination file
     *
     * @param context Application context
     * @param sourceUri Source URI of the image
     * @param destFile Destination file
     * @return true if successful, false otherwise
     */
    public static boolean copyImageFromUri(Context context, Uri sourceUri, File destFile) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {
            
            if (inputStream == null) {
                return false;
            }
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying image: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a properly sized bitmap from a file path
     *
     * @param filePath Path to the image file
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Scaled bitmap
     */
    public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }
    
    /**
     * Calculate an appropriate inSampleSize value for bitmap scaling
     *
     * @param options BitmapFactory.Options with outHeight and outWidth set
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Calculated sample size
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
}