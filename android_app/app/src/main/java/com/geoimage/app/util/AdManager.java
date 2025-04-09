package com.geoimage.app.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.geoimage.app.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Utility class to handle ad loading and display
 */
public class AdManager {
    private static final String TAG = "AdManager";
    
    private static InterstitialAd interstitialAd;
    private static RewardedAd rewardedAd;
    private static boolean isLoadingInterstitialAd = false;
    private static boolean isLoadingRewardedAd = false;
    
    /**
     * Interface for reward callback
     */
    public interface RewardCallback {
        void onRewarded();
        void onRewardFailed();
    }
    
    /**
     * Initialize a banner ad
     *
     * @param adView The AdView to initialize
     */
    public static void initBannerAd(AdView adView) {
        if (adView == null) return;
        
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                Log.d(TAG, "Banner ad loaded");
            }
            
            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                Log.e(TAG, "Banner ad failed to load: " + loadAdError.getMessage());
            }
        });
        adView.loadAd(adRequest);
    }
    
    /**
     * Load interstitial ad
     *
     * @param context Context to use for loading
     */
    public static void loadInterstitialAd(Context context) {
        if (isLoadingInterstitialAd || interstitialAd != null) {
            return;
        }
        
        isLoadingInterstitialAd = true;
        
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, context.getString(R.string.interstitial_ad_unit_id), adRequest, 
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        isLoadingInterstitialAd = false;
                        Log.d(TAG, "Interstitial ad loaded");
                    }
                    
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "Interstitial ad failed to load: " + loadAdError.getMessage());
                        interstitialAd = null;
                        isLoadingInterstitialAd = false;
                    }
                });
    }
    
    /**
     * Show interstitial ad
     *
     * @param activity Activity to show the ad in
     */
    public static void showInterstitialAd(Activity activity) {
        if (interstitialAd == null) {
            loadInterstitialAd(activity);
            return;
        }
        
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                // The ad was dismissed. Load a new one.
                interstitialAd = null;
                loadInterstitialAd(activity);
            }
            
            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                // The ad failed to show. Load a new one.
                Log.e(TAG, "Interstitial ad failed to show: " + adError.getMessage());
                interstitialAd = null;
                loadInterstitialAd(activity);
            }
            
            @Override
            public void onAdShowedFullScreenContent() {
                // Ad showed successfully
                Log.d(TAG, "Interstitial ad showed");
            }
        });
        
        interstitialAd.show(activity);
    }
    
    /**
     * Load rewarded ad
     *
     * @param context Context to use for loading
     */
    public static void loadRewardedAd(Context context) {
        if (isLoadingRewardedAd || rewardedAd != null) {
            return;
        }
        
        isLoadingRewardedAd = true;
        
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, context.getString(R.string.rewarded_ad_unit_id), adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        isLoadingRewardedAd = false;
                        Log.d(TAG, "Rewarded ad loaded");
                    }
                    
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "Rewarded ad failed to load: " + loadAdError.getMessage());
                        rewardedAd = null;
                        isLoadingRewardedAd = false;
                    }
                });
    }
    
    /**
     * Show rewarded ad
     *
     * @param activity Activity to show the ad in
     * @param callback Callback to notify about reward
     */
    public static void showRewardedAd(Activity activity, RewardCallback callback) {
        if (rewardedAd == null) {
            if (callback != null) {
                callback.onRewardFailed();
            }
            loadRewardedAd(activity);
            return;
        }
        
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                // Ad was dismissed. Load a new one.
                rewardedAd = null;
                loadRewardedAd(activity);
            }
            
            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                // Ad failed to show. Load a new one.
                Log.e(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                rewardedAd = null;
                if (callback != null) {
                    callback.onRewardFailed();
                }
                loadRewardedAd(activity);
            }
            
            @Override
            public void onAdShowedFullScreenContent() {
                // Ad showed successfully
                Log.d(TAG, "Rewarded ad showed");
            }
        });
        
        rewardedAd.show(activity, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                // Handle the reward
                Log.d(TAG, "User earned reward: " + rewardItem.getAmount() + " " + rewardItem.getType());
                Toast.makeText(activity, R.string.reward_received, Toast.LENGTH_SHORT).show();
                
                if (callback != null) {
                    callback.onRewarded();
                }
            }
        });
    }
}