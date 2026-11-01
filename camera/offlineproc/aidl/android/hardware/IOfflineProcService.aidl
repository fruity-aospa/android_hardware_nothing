/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package android.hardware;

import android.hardware.IOfflineProcClient;
import android.hardware.OfflineParameters;
import android.hardware.camera2.impl.CameraMetadataNative;

/**
 * Offline processing service published by the Nothing OS camera service as
 * "media.offline". Declaration order fixes the transaction codes and must
 * match the stock libcamera_client proxy that NTCamera's JNI is built against.
 */
interface IOfflineProcService {
    void initOffline(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6);
    void setPath(String path);
    void addMetadata(int id, in CameraMetadataNative metadata);
    void setOfflineListener(IOfflineProcClient listener);
    void processOffline(in OfflineParameters params);
    void setBitmapData(in OfflineParameters params);
    void closeOffline();
}
