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

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchfaceService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    static String mHighTemperature;
    static String mLowTemperature;

    @Nullable
    static Bitmap mWeatherDrawable;

    static boolean mHasWeatherData = false;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<SunshineWatchfaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchfaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchfaceService.Engine engine = mWeakReference.get();
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

        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mHighTemperatureTextPaint;
        Paint mLowTemperatureTextPaint;
        Paint mDividerPaint;

        boolean mAmbient;
        boolean showHoursSeperator;

        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private float lineHeight;
        private float marginWeather;
        private float dividerMargin;
        private float dividerWidth;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchfaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineWatchfaceService.this.getResources();

            dividerMargin = resources.getDimension(R.dimen.divider_margin);
            lineHeight = resources.getDimension(R.dimen.digital_line_height);
            marginWeather = resources.getDimension(R.dimen.margin_weather);
            dividerWidth = resources.getDimension(R.dimen.divider_width);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mTimeTextPaint.setTextSize(resources.getDimension(R.dimen.time_text_size));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.light_text), NORMAL_TYPEFACE);
            mDateTextPaint.setTextSize(resources.getDimension(R.dimen.date_text_size));

            mDividerPaint = new Paint();
            mDividerPaint.setColor(resources.getColor(R.color.light_text));

            mHighTemperatureTextPaint = new Paint();
            mHighTemperatureTextPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mHighTemperatureTextPaint.setTextSize(resources.getDimension(R.dimen.weather_text_size));

            mLowTemperatureTextPaint = new Paint();
            mLowTemperatureTextPaint = createTextPaint(resources.getColor(R.color.light_text), NORMAL_TYPEFACE);
            mLowTemperatureTextPaint.setTextSize(resources.getDimension(R.dimen.weather_text_size));

            mCalendar = Calendar.getInstance();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchfaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchfaceService.this.unregisterReceiver(mTimeZoneReceiver);
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
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            long now = System.currentTimeMillis();

            mCalendar.setTimeInMillis(now);

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);


            float centerX = bounds.width() / 2;


            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchfaceService.this);


            SimpleDateFormat simpleDateFormat;

            if (is24Hour) {
                simpleDateFormat = new SimpleDateFormat(showHoursSeperator ? "HH:mm" : "HH mm", Locale.getDefault());
            } else {
                simpleDateFormat = new SimpleDateFormat(showHoursSeperator ? "hh:mm a" : "hh mm", Locale.getDefault());
            }

            showHoursSeperator = !showHoursSeperator;

            String timeString = simpleDateFormat.format(mCalendar.getTime());

            float x = centerX - (mTimeTextPaint.measureText(timeString) / 2);
            //drawing the timeString
            canvas.drawText(timeString, x, bounds.exactCenterY() - lineHeight, mTimeTextPaint);

            // Drawing Day , Date , WeatherIcon , temperature
            simpleDateFormat = new SimpleDateFormat("EE MM dd yyyy", Locale.getDefault());
            String dayNDate = simpleDateFormat.format(mCalendar.getTime());
            x = centerX - (mDateTextPaint.measureText(dayNDate) / 2);

            canvas.drawText(
                    dayNDate,
                    x, bounds.exactCenterY(), mDateTextPaint);

            float yDivider = bounds.exactCenterY() + lineHeight / 2;

            canvas.drawLine(centerX - dividerWidth / 2, yDivider, centerX + dividerWidth / 2, yDivider, mDividerPaint);

            if (mHasWeatherData) {

                float yWeather = yDivider + dividerMargin + lineHeight;

                float xHighTemp = centerX - mHighTemperatureTextPaint.measureText(mHighTemperature) / 2;
                canvas.drawText(mHighTemperature, xHighTemp, yWeather, mHighTemperatureTextPaint);

                float xLowTemp = xHighTemp + mHighTemperatureTextPaint.measureText(mHighTemperature);
                canvas.drawText(mLowTemperature, xLowTemp, yWeather, mLowTemperatureTextPaint);

                if (mWeatherDrawable != null) {
                    float xWeather = xHighTemp - mWeatherDrawable.getWidth() - marginWeather;
                    canvas.drawBitmap(mWeatherDrawable, xWeather, yDivider + dividerMargin, new Paint());
                }
            }

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
    }
}
