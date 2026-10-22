/*
 * Copyright (C) 2024 Neoteric OS
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

package co.aospa.glyph.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.List;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.Utils.ResourceUtils;

public class AutoBrightnessService extends Service {

    private static final String TAG = "GlyphAutoBrightnessService";
    private static final boolean DEBUG = true;

    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private int[] autoBrightnessLux;
    private int[] brightnessValues;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Resolve device resources here rather than in class initializers: a
        // missing overlay crashed the whole app process at first class load.
        try {
            autoBrightnessLux = ResourceUtils.getIntArray("glyph_auto_brightness_levels");
            brightnessValues = Constants.getBrightnessLevels();
        } catch (Exception e) {
            Log.w(TAG, "Auto brightness resources missing, feature disabled", e);
            stopSelf();
            return;
        }

        // Get light sensor type
        String sensorName = ResourceUtils.getString("glyph_light_sensor");
        int sensorType = -1;
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensors) {
            if (sensorName.equals(sensor.getStringType())) {
                sensorType = sensor.getType();
                break;
            }
        }

        mLightSensor = sensorType >= 0 ? mSensorManager.getDefaultSensor(sensorType) : null;
        if (mLightSensor == null) {
            Log.w(TAG, "Light sensor \"" + sensorName + "\" not found, auto brightness disabled");
            stopSelf();
            return;
        }
        mSensorManager.registerListener(mSensorEventListener,
            mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (brightnessValues == null || brightnessValues.length == 0
                    || autoBrightnessLux == null || autoBrightnessLux.length == 0) {
                return;
            }
            int lux = (int) event.values[0];
            int lux_index = 0;
            int brightnessValue;

            for (int i = 1; i < autoBrightnessLux.length; i++) {
                if (lux >= autoBrightnessLux[i]) {
                    lux_index = i;
                } else {
                    break;
                }
            }

            // Both resource arrays come from overlays that can be misaligned;
            // clamp so a longer lux list cannot index out of the levels array.
            lux_index = Math.min(lux_index, brightnessValues.length - 1);
            brightnessValue = brightnessValues[lux_index];

            if (brightnessValue != Constants.getBrightness()) {
                if (DEBUG) {
                    int ledLux = autoBrightnessLux[Math.min(lux_index, autoBrightnessLux.length - 1)];
                    Log.d(TAG, "Brightness changed: " + "RealLux: " + lux +
                    " | BrightnessLux: " + ledLux + " | BrightnessValue: " + brightnessValue);
                }
                Constants.setBrightness(brightnessValue);
                if (StatusManager.isEssentialLedActive())
                    AnimationManager.playEssential();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
