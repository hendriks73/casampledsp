/*
 * =================================================
 * Copyright 2026 tagtraum industries incorporated
 * This file is part of CASampledSP.
 *
 * CASampledSP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * CASampledSP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with CASampledSP; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * =================================================
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
#include "com_tagtraum_casampledsp_CANativePeerInputStream.h"
#include "CAUtils.h"

jfieldID  nativeBufferFID     = nullptr;
jmethodID fillNativeBufferMID = nullptr;
jmethodID rewindMID           = nullptr;
jmethodID hasRemainingMID     = nullptr;
jmethodID getLimitMID         = nullptr;
jmethodID setLimitMID         = nullptr;
jmethodID getPositionMID      = nullptr;
jmethodID setPositionMID      = nullptr;

/**
 * Caches the field and method ids shared by all native peer streams.
 *
 * Called exactly once from the static initializer of CANativePeerInputStream.
 * Because the JVM serializes class initialization, the ids are set before any
 * thread can call into native code. Caching them lazily on first use instead
 * races: another thread may see some ids set and others still nullptr.
 *
 * Returns early with a pending NoSuchFieldError/NoSuchMethodError if a lookup
 * fails, so class initialization fails instead of leaving ids unset.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CANativePeerInputStream_initIDs(JNIEnv *env, jclass nativePeerClass) {
    jclass bufferClass = nullptr;

    nativeBufferFID     = env->GetFieldID(nativePeerClass,  "nativeBuffer", "Ljava/nio/ByteBuffer;");
    if (nativeBufferFID == nullptr) return;
    fillNativeBufferMID = env->GetMethodID(nativePeerClass, "fillNativeBuffer", "()V");
    if (fillNativeBufferMID == nullptr) return;

    bufferClass = env->FindClass("java/nio/Buffer");
    if (bufferClass == nullptr) return;
    rewindMID       = env->GetMethodID(bufferClass, "rewind",       "()Ljava/nio/Buffer;");
    if (rewindMID == nullptr) return;
    hasRemainingMID = env->GetMethodID(bufferClass, "hasRemaining", "()Z");
    if (hasRemainingMID == nullptr) return;
    getLimitMID     = env->GetMethodID(bufferClass, "limit",        "()I");
    if (getLimitMID == nullptr) return;
    setLimitMID     = env->GetMethodID(bufferClass, "limit",        "(I)Ljava/nio/Buffer;");
    if (setLimitMID == nullptr) return;
    getPositionMID  = env->GetMethodID(bufferClass, "position",     "()I");
    if (getPositionMID == nullptr) return;
    setPositionMID  = env->GetMethodID(bufferClass, "position",     "(I)Ljava/nio/Buffer;");
    env->DeleteLocalRef(bufferClass);
}
