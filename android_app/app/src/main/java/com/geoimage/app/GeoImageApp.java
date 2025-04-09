package com.geoimage.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.util.Date;

/**
 * Application class for initializing global components and AdMob
 */
public class GeoImageApp extends Application implements Application.ActivityLifecycleCallbacks, LifecycleObserver {

    private static final String TAG = "GeoImageApp";
    private static final String ADMOB_AD_UNIT_ID = "ca-app-pub-3940256099942544/3419835294"; // Test ID
    private AppOpenAdManager appOpenAdManager;
    private Activity currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize AdMob
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                Log.d(TAG, "AdMob SDK initialized: " + initializationStatus.toString());
            }
        });
        
        // Register activity lifecycle callbacks
        registerActivityLifecycleCallbacks(this);
        
        // Initialize app open ad manager
        appOpenAdManager = new AppOpenAdManager();
        
        // Add lifecycle observer to detect when the app goes to foreground
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }
    
    /**
     * Show an app open ad when the app is brought to the foreground
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    protected void onAppForegrounded() {
        // Show the ad (if available) when the app moves to the foreground
        appOpenAdManager.showAdIfAvailable(currentActivity);
    }

    /**
     * ActivityLifecycleCallbacks methods
     */
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // Save the current activity for use when showing app open ads
        if (!appOpenAdManager.isShowingAd) {
            currentActivity = activity;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}

    /**
     * Inner class to manage app open ads
     */
    public class AppOpenAdManager {
        private static final long MAX_CACHE_TIME = 4 * 60 * 60 * 1000; // 4 hours
        
        private AppOpenAd appOpenAd = null;
        private boolean isLoadingAd = false;
        private boolean isShowingAd = false;
        private long loadTime = 0;

        /**
         * Load an app open ad
         */
        public void loadAd(Context context) {
            // Don't load another ad if one is already loading
            if (isLoadingAd || isAdAvailable()) {
                return;
            }

            isLoadingAd = true;
            AdRequest request = new AdRequest.Builder().build();
            AppOpenAd.load(
                    context,
                    context.getString(R.string.app_open_ad_unit_id),
                    request,
                    AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                    new AppOpenAd.AppOpenAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull AppOpenAd ad) {
                            appOpenAd = ad;
                            isLoadingAd = false;
                            loadTime = (new Date()).getTime();
                            Log.d(TAG, "App open ad loaded");
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoadingAd = false;
                            Log.d(TAG, "App open ad failed to load: " + loadAdError.getMessage());
                        }
                    });
        }

        /**
         * Check if an ad is available and not expired
         */
        private boolean isAdAvailable() {
            return appOpenAd != null && !isAdExpired();
        }
        
        /**
         * Check if the cached ad has expired
         */
        private boolean isAdExpired() {
            long now = new Date().getTime();
            return now - loadTime > MAX_CACHE_TIME;
        }

        /**
         * Show the ad if it's available, otherwise load a new one
         *
         * @param activity The activity to show the ad in
         */
        public void showAdIfAvailable(@NonNull final Activity activity) {
            // If the app open ad is already showing, don't show another one
            if (isShowingAd) {
                Log.d(TAG, "An app open ad is already showing");
                return;
            }

            // If an ad is not available, load one
            if (!isAdAvailable()) {
                Log.d(TAG, "App open ad not available, loading new one");
                loadAd(activity);
                return;
            }

            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    // The ad was dismissed, set flag and load the next ad
                    appOpenAd = null;
                    isShowingAd = false;
                    loadAd(activity);
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    // The ad failed to show, set flag and load a new one
                    appOpenAd = null;
                    isShowingAd = false;
                    loadAd(activity);
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    // The ad was shown, set flag
                    isShowingAd = true;
                }
            });

            // Show the ad
            appOpenAd.show(activity);
        }
    }
}