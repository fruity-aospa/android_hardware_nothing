/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package android.hardware;

oneway interface IOfflineProcClient {
    void notifyCallback(int msgType, int ext1, int ext2);
}
