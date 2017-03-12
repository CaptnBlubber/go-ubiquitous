package com.example.android.sunshine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Angelo Rüggeberg <s3xy4ngc@googlemail.com>
 */
public class SunshineWearableListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /**
     * Do not use directly use getApiClient() instead
     */
    @Nullable
    private GoogleApiClient apiClient;

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();

                SunshineWatchfaceService.mHighTemperature = getString(R.string.temperature_format, dataMap.getInt("MAX_TEMP"));
                SunshineWatchfaceService.mLowTemperature = getString(R.string.temperature_format, dataMap.getInt("MIN_TEMP"));

                Asset weatherIcon = dataMap.getAsset("WEATHER_ICON");
                SunshineWatchfaceService.mWeatherDrawable = loadBitmapFromAsset(weatherIcon);
                SunshineWatchfaceService.mHasWeatherData = true;
            }
        }
    }

    @Nullable
    Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                getApiClient().blockingConnect(500, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                getApiClient(), asset).await().getInputStream();
        getApiClient().disconnect();

        if (assetInputStream == null) {
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    private GoogleApiClient getApiClient() {
        if (apiClient != null) {
            return apiClient;
        }

        apiClient = new GoogleApiClient.Builder(getBaseContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        return apiClient;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //STUB
    }

    @Override
    public void onConnectionSuspended(int i) {
        //STUB
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //STUB
    }
}
