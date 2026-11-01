/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "OfflineProcClient"

#include <ntcam/OfflineProcClient.h>

#include <log/log.h>

namespace android {

OfflineProcClient::OfflineProcClient() = default;

OfflineProcClient::~OfflineProcClient() = default;

void OfflineProcClient::setListener(const sp<OfflineProcClientListener>& listener) {
    mListener = listener;
}

binder::Status OfflineProcClient::notifyCallback(int32_t msgType, int32_t ext1, int32_t ext2) {
    ALOGV("notifyCallback(%d, %d, %d)", msgType, ext1, ext2);
    return binder::Status::ok();
}

}  // namespace android
