/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/hardware/BnOfflineProcClient.h>

namespace android {

class OfflineProcClientListener : public virtual RefBase {};

class OfflineProcClient : public hardware::BnOfflineProcClient {
  public:
    OfflineProcClient();
    ~OfflineProcClient() override;

    void setListener(const sp<OfflineProcClientListener>& listener);

    binder::Status notifyCallback(int32_t msgType, int32_t ext1, int32_t ext2) override;

  private:
    sp<OfflineProcClientListener> mListener;
};

// libofflineproc_jni.so allocates this object itself (operator new(0x68)).
static_assert(sizeof(OfflineProcClient) <= 0x68, "OfflineProcClient outgrew the Nothing OS allocation");

}  // namespace android
