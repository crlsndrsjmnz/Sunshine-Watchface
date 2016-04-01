/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private final String LOG_TAG = SunshineWatchFace.class.getSimpleName();

    public static final String PACKAGE_NAME = "com.example.android.sunshine";

    public static final String ACTION_UPDATE_WEATHER = PACKAGE_NAME + ".intent.action.UPDATE_WEATHER";

    public static final String EXTRA_MAX_TEMP_KEY = PACKAGE_NAME + ".intent.extra.MAX_TEMP_KEY";
    public static final String EXTRA_MIN_TEMP_KEY = PACKAGE_NAME + ".intent.extra.MIN_TEMP_KEY";
    public static final String EXTRA_WEATHER_ID_KEY = PACKAGE_NAME + ".intent.extra.WEATHER_ID_KEY";


    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherReceiver = false;

        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mDateAmbientPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Paint mLowAmbientTemp;

        Paint mIconPaint;
        Bitmap mIconBitmap;
        Bitmap mGrayIconBitmap;

        float iconPadding;
        float temperaturePadding;

        int weatherId = -1;
        String mMaxTemp = "";
        String mMinTemp = "";
        boolean weatherSynced = false;

        boolean bLogInfo = true;

        boolean mAmbient;
        Time mTime;

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mWeatherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getExtras() == null)
                    return;

                Bundle extras = intent.getExtras();

                if (extras.containsKey(EXTRA_WEATHER_ID_KEY)) {
                    weatherId = Utility.getIconResourceForWeatherCondition(extras.getInt(EXTRA_WEATHER_ID_KEY, 0));

                    mIconBitmap = BitmapFactory.decodeResource(getResources(), weatherId);
                    initGrayBackgroundBitmap();
                }

                if (extras.containsKey(EXTRA_MAX_TEMP_KEY))
                    mMaxTemp = Utility.formatTemperature(context, extras.getDouble(EXTRA_MAX_TEMP_KEY, 0));

                if (extras.containsKey(EXTRA_MIN_TEMP_KEY))
                    mMinTemp = Utility.formatTemperature(context, extras.getDouble(EXTRA_MIN_TEMP_KEY, 0));

                weatherSynced = true;
            }
        };

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.primary_light));

            mDateAmbientPaint = new Paint();
            mDateAmbientPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.primary_light));

            mLowAmbientTemp = new Paint();
            mLowAmbientTemp = createTextPaint(resources.getColor(R.color.digital_text));

            mIconPaint = new Paint();

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter timeZoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, timeZoneFilter);
            }

            if (!mRegisteredWeatherReceiver) {
                mRegisteredWeatherReceiver = true;
                IntentFilter weatherFilter = new IntentFilter(ACTION_UPDATE_WEATHER);
                SunshineWatchFace.this.registerReceiver(mWeatherReceiver, weatherFilter);
            }
        }

        private void unregisterReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            }

            if (mRegisteredWeatherReceiver) {
                mRegisteredWeatherReceiver = false;
                SunshineWatchFace.this.unregisterReceiver(mWeatherReceiver);
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTimePaint.setTextSize(textSize);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mDateAmbientPaint.setTextSize(resources.getDimension(R.dimen.date_text_size));

            mHighTempPaint.setTextSize(resources.getDimension(R.dimen.temperature_text_size));
            mLowTempPaint.setTextSize(resources.getDimension(R.dimen.temperature_text_size));

            iconPadding = resources.getDimension(R.dimen.icon_padding);
            temperaturePadding = resources.getDimension(R.dimen.temperature_padding);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mDateAmbientPaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or interactive mode.
            mTime.setToNow();

            String time = String.format("%d:%02d", mTime.hour, mTime.minute);
            drawCenter(canvas, mTimePaint, time, -70);

            String date = String.format("%s, %s %d %d", Utility.getWeekDay(mTime.weekDay), Utility.getMonth(mTime.month), mTime.monthDay, mTime.year);
            if (isInAmbientMode()) {
                drawCenter(canvas, mDateAmbientPaint, date, -25);
            } else {
                drawCenter(canvas, mDatePaint, date, -25);
            }

            if (isInAmbientMode()) {
                canvas.drawLine(bounds.width() * 0.4f, bounds.height() / 2, bounds.width() * 0.6f, bounds.height() / 2, mDateAmbientPaint);
            } else {
                canvas.drawLine(bounds.width() * 0.4f, bounds.height() / 2, bounds.width() * 0.6f, bounds.height() / 2, mDatePaint);
            }

            if (weatherSynced)
                drawTemperature(canvas, -30);

            bLogInfo = false;
        }

        private Rect r = new Rect();

        private void drawCenter(Canvas canvas, Paint paint, String text, float yPos) {
            canvas.getClipBounds(r);
            int cHeight = r.height();
            int cWidth = r.width();
            paint.setTextAlign(Paint.Align.LEFT);
            paint.getTextBounds(text, 0, text.length(), r);
            float x = cWidth / 2f - r.width() / 2f - r.left;
            float y = cHeight / 2f + r.height() / 2f - r.bottom + yPos;
            canvas.drawText(text, x, y, paint);
        }

        private void drawTemperature(Canvas canvas, float yPos) {

            float dataXPositionHighTemp = 0;
            float dataXPositionLowTemp = 0;
            float dataYTempPosition = 0;

            float xHighTemp = 0;
            float yHighTemp = 0;
            float xHighTempSize = 0;

            float xLowTemp = 0;
            float yLowTemp = 0;

            float xIcon = 0;
            float yIcon = 0;

            canvas.getClipBounds(r);
            int cHeight = r.height();
            int cWidth = r.width();

            mHighTempPaint.setTextAlign(Paint.Align.LEFT);
            mHighTempPaint.getTextBounds(mMaxTemp, 0, mMaxTemp.length(), r);
            xHighTempSize = r.width();
            dataXPositionHighTemp = r.width() / 2f + r.left;
            dataYTempPosition = r.height() / 2f - r.bottom;

            mLowTempPaint.setTextAlign(Paint.Align.LEFT);
            mLowTempPaint.getTextBounds(mMinTemp, 0, mMinTemp.length(), r);
            dataXPositionLowTemp = r.width() / 2f + r.left;

            xIcon = cWidth / 2f - dataXPositionHighTemp - dataXPositionLowTemp - mIconBitmap.getWidth() / 2f - iconPadding / 2f - temperaturePadding / 2f;
            yIcon = (cHeight * 3 / 4) + yPos - mIconBitmap.getWidth() / 2f;

            if (isInAmbientMode()) {
                canvas.drawBitmap(mGrayIconBitmap, xIcon, yIcon, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mIconBitmap, xIcon, yIcon, mBackgroundPaint);
            }

            xHighTemp = xIcon + mIconBitmap.getWidth() + iconPadding;
            yHighTemp = (cHeight * 3 / 4) + yPos + dataYTempPosition;

            canvas.drawText(mMaxTemp, xHighTemp, yHighTemp, mHighTempPaint);

            xLowTemp = xHighTemp + xHighTempSize + temperaturePadding;
            yLowTemp = (cHeight * 3 / 4) + yPos + dataYTempPosition;

            if (isInAmbientMode()) {
                canvas.drawText(mMinTemp, xLowTemp, yLowTemp, mHighTempPaint);
            } else {
                canvas.drawText(mMinTemp, xLowTemp, yLowTemp, mLowTempPaint);
            }

        }

        private void initGrayBackgroundBitmap() {
            mGrayIconBitmap = Bitmap.createBitmap(
                    mIconBitmap.getWidth(),
                    mIconBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayIconBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mIconBitmap, 0, 0, grayPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void logInfo(String log) {
            if (bLogInfo)
                Log.d(LOG_TAG, log);
        }
    }
}
