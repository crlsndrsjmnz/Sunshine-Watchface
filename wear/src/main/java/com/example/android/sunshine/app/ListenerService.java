package com.example.android.sunshine.app;

import android.content.Intent;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class ListenerService extends WearableListenerService {

    private final String LOG_TAG = ListenerService.class.getSimpleName();

    private static final String WEARABLE_DATA_PATH = "/sunshine";

    private static final String WEAR_MAX_TEMP_KEY = "com.example.android.sunshine.max_temp";
    private static final String WEAR_MIN_TEMP_KEY = "com.example.android.sunshine.min_temp";
    private static final String WEAR_WEATHER_ID_KEY = "com.example.android.sunshine.weather_id";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        DataMap dataMap;
        for (DataEvent event : dataEvents) {

            // Check the data type
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // Check the data path
                String path = event.getDataItem().getUri().getPath();

                if (path.equals(WEARABLE_DATA_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                    Intent intent = new Intent(SunshineWatchFace.ACTION_UPDATE_WEATHER);
                    intent.putExtra(SunshineWatchFace.EXTRA_MAX_TEMP_KEY, dataMap.getDouble(WEAR_MAX_TEMP_KEY));
                    intent.putExtra(SunshineWatchFace.EXTRA_MIN_TEMP_KEY, dataMap.getDouble(WEAR_MIN_TEMP_KEY));
                    intent.putExtra(SunshineWatchFace.EXTRA_WEATHER_ID_KEY, dataMap.getInt(WEAR_WEATHER_ID_KEY));
                    sendBroadcast(intent);
                }
            }
        }
    }
}
