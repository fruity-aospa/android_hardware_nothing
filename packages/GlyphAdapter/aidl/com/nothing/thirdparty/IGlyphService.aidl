/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.thirdparty;

/**
 * The service that apps built with the Glyph SDK bind to.
 *
 * The SDK calls these methods by their position, so only append new ones.
 */
interface IGlyphService {
    void setFrameColors(in int[] levels);
    void openSession();
    void closeSession();
    boolean register(in String name);
    boolean registerSDK(in String name, in String key);
    boolean registerMatrixSDK(in String name);
    void setMatrixColors(in int[] levels);
    void setGlyphMatrixTimeout(boolean active);
    void setAppMatrixColors(in int[] levels);
    void closeAppMatrix();
}
