package com.geoimage.app.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Model class representing an image with geolocation data
 */
public class GeoImage implements Parcelable {
    private long id;
    private String filename;
    private String path;
    private double latitude;
    private double longitude;
    private String locationName;
    private Date timestamp;

    public GeoImage(long id, String filename, String path, double latitude, double longitude, 
                   String locationName, Date timestamp) {
        this.id = id;
        this.filename = filename;
        this.path = path;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    // Parcelable implementation
    protected GeoImage(Parcel in) {
        id = in.readLong();
        filename = in.readString();
        path = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        locationName = in.readString();
        long tmpTimestamp = in.readLong();
        timestamp = tmpTimestamp != -1 ? new Date(tmpTimestamp) : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(filename);
        dest.writeString(path);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(locationName);
        dest.writeLong(timestamp != null ? timestamp.getTime() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GeoImage> CREATOR = new Creator<GeoImage>() {
        @Override
        public GeoImage createFromParcel(Parcel in) {
            return new GeoImage(in);
        }

        @Override
        public GeoImage[] newArray(int size) {
            return new GeoImage[size];
        }
    };
}