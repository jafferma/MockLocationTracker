package com.geoimage.app.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.geoimage.app.R;
import com.geoimage.app.model.GeoImage;
import com.geoimage.app.util.AdManager;
import com.geoimage.app.util.ImageProcessor;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    
    private RecyclerView recyclerView;
    private TextView emptyGalleryText;
    private GalleryAdapter adapter;
    private List<GeoImage> geoImages;

    private AdView adView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        
        // Initialize views
        recyclerView = findViewById(R.id.imagesRecyclerView);
        emptyGalleryText = findViewById(R.id.emptyGalleryText);
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        geoImages = new ArrayList<>();
        adapter = new GalleryAdapter(geoImages);
        recyclerView.setAdapter(adapter);
        
        // Initialize banner ad
        adView = findViewById(R.id.adView);
        AdManager.initBannerAd(adView);
        
        // Load images
        new LoadGeoImagesTask().execute();
        
        // Show interstitial ad when opening gallery
        AdManager.showInterstitialAd(this);
    }
    
    /**
     * Adapter for the gallery RecyclerView
     */
    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
        private List<GeoImage> images;
        
        GalleryAdapter(List<GeoImage> images) {
            this.images = images;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_gallery_image, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GeoImage image = images.get(position);
            
            // Load thumbnail
            Bitmap thumbnail = ImageProcessor.decodeSampledBitmapFromFile(
                    image.getPath(), 500, 500);
            holder.imageView.setImageBitmap(thumbnail);
            
            // Set text
            holder.locationText.setText(image.getLocationName());
            holder.coordinatesText.setText(String.format(Locale.US, 
                    "%.4f, %.4f", 
                    image.getLatitude(), 
                    image.getLongitude()));
            
            // Format date
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            holder.dateText.setText(displayFormat.format(image.getTimestamp()));
            
            // Set click listener
            holder.itemView.setOnClickListener(v -> openImage(image.getPath()));
        }
        
        @Override
        public int getItemCount() {
            return images.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView locationText;
            TextView coordinatesText;
            TextView dateText;
            
            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
                locationText = itemView.findViewById(R.id.locationText);
                coordinatesText = itemView.findViewById(R.id.coordinatesText);
                dateText = itemView.findViewById(R.id.dateText);
            }
        }
    }
    
    /**
     * Open the image in the device's gallery/viewer
     *
     * @param path Path to the image file
     */
    private void openImage(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(new File(path));
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }
    
    @Override
    protected void onDestroy() {
        // Clean up the banner ad to avoid memory leaks
        if (adView != null) {
            adView.destroy();
        }
        
        super.onDestroy();
    }
    
    /**
     * AsyncTask to load geotagged images
     */
    private class LoadGeoImagesTask extends AsyncTask<Void, Void, List<GeoImage>> {
        @Override
        protected List<GeoImage> doInBackground(Void... voids) {
            List<GeoImage> result = new ArrayList<>();
            
            // Check our app's GeoImage directory first
            File geoImageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeoImage");
            if (geoImageDir.exists() && geoImageDir.isDirectory()) {
                File[] files = geoImageDir.listFiles(file -> file.isFile() && 
                        file.getName().toLowerCase().endsWith(".jpg"));
                
                if (files != null) {
                    for (File file : files) {
                        GeoImage image = getGeoImageFromFile(file, result.size() + 1);
                        if (image != null) {
                            result.add(image);
                        }
                    }
                }
            }
            
            // Also search the media store for images we've added
            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_TAKEN
            };
            
            String selection = MediaStore.Images.Media.DATA + " LIKE ?";
            String[] selectionArgs = new String[] { "%GeoImage%" };
            
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
                
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                    int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String name = cursor.getString(nameColumn);
                        String path = cursor.getString(dataColumn);
                        
                        // Check if we already have this image
                        boolean alreadyAdded = false;
                        for (GeoImage existingImage : result) {
                            if (existingImage.getPath().equals(path)) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        
                        if (!alreadyAdded) {
                            // Get additional information from the image file
                            File file = new File(path);
                            if (file.exists()) {
                                GeoImage image = getGeoImageFromFile(file, result.size() + 1);
                                if (image != null) {
                                    result.add(image);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying media store: " + e.getMessage());
            }
            
            return result;
        }
        
        @Override
        protected void onPostExecute(List<GeoImage> geoImageList) {
            if (geoImageList.isEmpty()) {
                emptyGalleryText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyGalleryText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                geoImages.clear();
                geoImages.addAll(geoImageList);
                adapter.notifyDataSetChanged();
            }
        }
        
        /**
         * Extract GeoImage data from an image file
         *
         * @param file Image file
         * @param id ID to assign
         * @return GeoImage object or null if no GPS data
         */
        private GeoImage getGeoImageFromFile(File file, long id) {
            try {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                
                // Check if the image has GPS data
                if (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null &&
                        exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null) {
                    
                    // Get location information
                    float[] latLong = new float[2];
                    if (exif.getLatLong(latLong)) {
                        double latitude = latLong[0];
                        double longitude = latLong[1];
                        
                        // Try to get location name if available
                        String locationName = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
                        if (locationName == null || locationName.equals("GPS")) {
                            // If no location name, format the coordinates
                            locationName = String.format(Locale.US, "%.4f, %.4f", latitude, longitude);
                        }
                        
                        // Get timestamp
                        Date timestamp;
                        String dateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME);
                        if (dateTimeString != null) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                                timestamp = sdf.parse(dateTimeString);
                            } catch (ParseException e) {
                                // Use file modification date as fallback
                                timestamp = new Date(file.lastModified());
                            }
                        } else {
                            timestamp = new Date(file.lastModified());
                        }
                        
                        return new GeoImage(
                                id,
                                file.getName(),
                                file.getAbsolutePath(),
                                latitude,
                                longitude,
                                locationName,
                                timestamp
                        );
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading EXIF data from " + file.getName() + ": " + e.getMessage());
            }
            
            return null;
        }
    }
}