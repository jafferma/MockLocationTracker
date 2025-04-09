package com.geoimage.app.ui;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoimage.app.R;
import com.geoimage.app.model.GeoImage;
import com.geoimage.app.model.Location;
import com.geoimage.app.util.AdManager;
import com.geoimage.app.util.ImageProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImagePreviewActivity extends AppCompatActivity {
    private static final String TAG = "ImagePreviewActivity";
    
    private ImageView imagePreview;
    private TextView locationNameText;
    private TextView coordinatesText;
    private Button cancelButton;
    private Button saveButton;
    
    private Uri imageUri;
    private Location location;
    private File outputFile;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);
        
        // Initialize views
        imagePreview = findViewById(R.id.imagePreview);
        locationNameText = findViewById(R.id.locationNameText);
        coordinatesText = findViewById(R.id.coordinatesText);
        cancelButton = findViewById(R.id.cancelButton);
        saveButton = findViewById(R.id.saveButton);
        
        // Set click listeners
        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> processAndSaveImage());
        
        // Get intent data
        if (getIntent() != null) {
            String uriString = getIntent().getStringExtra("image_uri");
            if (uriString != null) {
                imageUri = Uri.parse(uriString);
            }
            
            location = getIntent().getParcelableExtra("location");
        }
        
        // Validate required data
        if (imageUri == null || location == null) {
            Toast.makeText(this, "Missing image or location data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Set up the progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.processing_image));
        progressDialog.setCancelable(false);
        
        // Display the image and location
        displayImage();
        displayLocation();
    }
    
    /**
     * Display the selected image in the preview
     */
    private void displayImage() {
        try {
            // Load a scaled down version for preview
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();
                
                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, 1080, 1920);
                
                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false;
                inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                    imagePreview.setImageBitmap(bitmap);
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading image: " + e.getMessage());
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Calculate appropriate scaling factor for the image
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    /**
     * Display the location information
     */
    private void displayLocation() {
        locationNameText.setText(location.getName());
        coordinatesText.setText(String.format(Locale.US, 
                "%.6f, %.6f", 
                location.getLatitude(), 
                location.getLongitude()));
    }
    
    /**
     * Process and save the image with location data
     */
    private void processAndSaveImage() {
        new ProcessImageTask().execute(imageUri);
    }
    
    /**
     * AsyncTask for processing the image in the background
     */
    private class ProcessImageTask extends AsyncTask<Uri, Void, Boolean> {
        private String errorMessage;
        private String savedFilePath;
        
        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }
        
        @Override
        protected Boolean doInBackground(Uri... uris) {
            Uri uri = uris[0];
            try {
                // Create a file to save the processed image
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "GeoImage_" + timeStamp + ".jpg";
                
                // Create output directory if it doesn't exist
                File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeoImage");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                // Create output file
                outputFile = new File(outputDir, fileName);
                
                // Copy the image to the output file
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(outputFile);
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                
                in.close();
                out.close();
                
                // Add geotag to the image
                boolean geotagSuccess = ImageProcessor.addGeotagToImage(
                        outputFile.getAbsolutePath(), location);
                
                if (!geotagSuccess) {
                    errorMessage = "Failed to add geotag to image";
                    return false;
                }
                
                // Add the image to the media store so it appears in the gallery
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, fileName);
                values.put(MediaStore.Images.Media.DESCRIPTION, "Image with location: " + location.getName());
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATA, outputFile.getAbsolutePath());
                
                getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                
                savedFilePath = outputFile.getAbsolutePath();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error processing image: " + e.getMessage());
                errorMessage = "Error processing image: " + e.getMessage();
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            
            if (success) {
                Toast.makeText(ImagePreviewActivity.this, 
                        getString(R.string.image_saved), 
                        Toast.LENGTH_SHORT).show();
                
                // Show a rewarded ad
                AdManager.showRewardedAd(ImagePreviewActivity.this, new AdManager.RewardCallback() {
                    @Override
                    public void onRewarded() {
                        // Successfully watched the ad - continue to gallery
                        openGallery();
                    }
                    
                    @Override
                    public void onRewardFailed() {
                        // Ad failed to show or wasn't available - continue anyway
                        openGallery();
                    }
                });
            } else {
                Toast.makeText(ImagePreviewActivity.this, 
                        errorMessage != null ? errorMessage : getString(R.string.image_save_error), 
                        Toast.LENGTH_LONG).show();
            }
        }
        
        /**
         * Open gallery and close this activity
         */
        private void openGallery() {
            // Open the gallery to show the saved image
            Intent intent = new Intent(ImagePreviewActivity.this, GalleryActivity.class);
            startActivity(intent);
            
            // Close this activity
            finish();
        }
    }
}