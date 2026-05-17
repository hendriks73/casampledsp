/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
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
#include "com_tagtraum_casampledsp_CAStreamInputStream.h"
#include "CAUtils.h"

static jfieldID  nativeBufferFieldID  = nullptr;
static jmethodID rewindMethodID       = nullptr;
static jmethodID setLimitMethodID     = nullptr;
static jmethodID getLimitMethodID     = nullptr;
static jmethodID setPositionMethodID  = nullptr;
static jmethodID getPositionMethodID  = nullptr;

/**
 * Init static method and field ids for Java methods/fields, if we don't have them already.
 */
static void init_ids(JNIEnv *env, jobject stream) {
    if (nativeBufferFieldID == nullptr || rewindMethodID == nullptr || setLimitMethodID == nullptr
            || getLimitMethodID == nullptr || getPositionMethodID == nullptr || setPositionMethodID == nullptr) {
        nativeBufferFieldID = env->GetFieldID(env->GetObjectClass(stream), "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass  = env->FindClass("java/nio/Buffer");
        rewindMethodID      = env->GetMethodID(bufferClass, "rewind",    "()Ljava/nio/Buffer;");
        setLimitMethodID    = env->GetMethodID(bufferClass, "limit",     "(I)Ljava/nio/Buffer;");
        getLimitMethodID    = env->GetMethodID(bufferClass, "limit",     "()I");
        getPositionMethodID = env->GetMethodID(bufferClass, "position",  "()I");
        setPositionMethodID = env->GetMethodID(bufferClass, "position",  "(I)Ljava/nio/Buffer;");
    }
}


/**
 * Packet callback for AudioFileStreamOpen used in open().
 */
static void CAStreamInputStream_PacketsProc(
        void                         *inClientData,
        UInt32                        inNumberBytes,
        UInt32                        inNumberPackets,
        const void                   *inInputData,
        AudioStreamPacketDescription *inPacketDescriptions) {
#ifdef DEBUG
    fprintf(stderr, "CAStreamInputStream_PacketsProc\n");
#endif

    CAAudioStreamIO *asio = static_cast<CAAudioStreamIO*>(inClientData);
    jobject byteBuffer    = nullptr;
    jlong   capacity      = 0;
    jint    limit         = 0;
    int     totalPackets  = 0;
    int     oldPackets    = 0;
    AudioStreamPacketDescription *newPktDescs = nullptr;

    byteBuffer = asio->env->GetObjectField(asio->javaInstance, nativeBufferFieldID);
    if (byteBuffer == nullptr) {
        throwIOExceptionIfError(asio->env, 1, "Failed to obtain native buffer");
        goto bail;
    }
    limit = asio->env->CallIntMethod(byteBuffer, getLimitMethodID);

    asio->srcBuffer = static_cast<char*>(asio->env->GetDirectBufferAddress(byteBuffer));
    capacity        = asio->env->GetDirectBufferCapacity(byteBuffer);
    if (asio->srcBuffer == nullptr) {
        throwIOExceptionIfError(asio->env, 1, "Failed to obtain direct buffer address");
        goto bail;
    }
    if (capacity - limit < static_cast<jlong>(inNumberBytes)) {
        throwIOExceptionIfError(asio->env, 1, "Native buffer too small for decoded audio");
        goto bail;
    }

    memcpy(asio->srcBuffer + limit, inInputData, inNumberBytes);

    if (limit == 0) {
        asio->lastPos = asio->pos;
    }
    oldPackets           = static_cast<int>(asio->pos - asio->lastPos);
    asio->pos           += inNumberPackets;
    asio->srcBufferSize  = inNumberBytes + limit;
    totalPackets         = static_cast<int>(asio->pos - asio->lastPos);

    asio->env->CallObjectMethod(byteBuffer, setPositionMethodID, 0);
    asio->env->CallObjectMethod(byteBuffer, setLimitMethodID, static_cast<jint>(inNumberBytes + limit));

    if (inPacketDescriptions) {
        newPktDescs = new AudioStreamPacketDescription[totalPackets]{};
        if (asio->pktDescs != nullptr) {
            memcpy(newPktDescs, asio->pktDescs, sizeof(AudioStreamPacketDescription) * oldPackets);
            delete[] asio->pktDescs;
        }
        memcpy(&newPktDescs[oldPackets], inPacketDescriptions, sizeof(AudioStreamPacketDescription) * inNumberPackets);
        for (int i = 1; i < totalPackets; i++) {
            newPktDescs[i].mStartOffset = newPktDescs[i-1].mDataByteSize + newPktDescs[i-1].mStartOffset;
        }
        asio->pktDescs = newPktDescs;
    }

bail:
    return;
}


/**
 * Property callback for AudioFileStreamOpen used in open().
 */
static void CAStreamInputStream_PropertyListenerProc(
        void                      *inClientData,
        AudioFileStreamID          stream,
        AudioFileStreamPropertyID  inPropertyID,
        UInt32                    *ioFlags) {
    int    res  = 0;
    UInt32 size = 0;

#ifdef DEBUG
    fprintf(stderr, "CAStreamInputStream_PropertyListenerProc\n");
    fprintf(stderr, "AudioFileStreamPropertyID %i\n", inPropertyID);
#endif

    CAAudioStreamIO *asio = static_cast<CAAudioStreamIO*>(inClientData);

    if (inPropertyID == kAudioFileStreamProperty_MagicCookieData) {
        res = AudioFileStreamGetPropertyInfo(stream, kAudioFileStreamProperty_MagicCookieData, &asio->cookieSize, nullptr);
        if (res && res != kAudioFileUnsupportedPropertyError) {
            throwUnsupportedAudioFileExceptionIfError(asio->env, res, "Failed to obtain cookie info from audio stream");
            goto bail;
        }
        res = 0;
        if (asio->cookieSize) {
            asio->cookie = new char[asio->cookieSize];
            res = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_MagicCookieData, &asio->cookieSize, asio->cookie);
            if (res) {
                throwUnsupportedAudioFileExceptionIfError(asio->env, res, "Failed to obtain cookie from audio stream");
                goto bail;
            }
        }
    }

    if (inPropertyID == kAudioFileStreamProperty_DataFormat) {
        size = sizeof(asio->srcFormat);
        res  = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_DataFormat, &size, &asio->srcFormat);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(asio->env, res, "Failed to read audio format from stream");
            goto bail;
        }
    }

bail:
    return;
}

/**
 * Called by the Java code to fill the native buffer.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong asioPtr, jbyteArray buf, jint length) {
#ifdef DEBUG
    fprintf(stderr, "fillNativeBuffer: %lld\n", asioPtr);
#endif

    int res = 0;
    CAAudioStreamIO *asio = reinterpret_cast<CAAudioStreamIO*>(asioPtr);
    jbyte *inBuf = nullptr;

    asio->env          = env;
    asio->javaInstance = stream;

    inBuf = env->GetByteArrayElements(buf, nullptr);
    if (inBuf == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to obtain byte array for stream parsing");
        goto bail;
    }

    res = AudioFileStreamParseBytes(asio->asid, static_cast<UInt32>(length), inBuf, kAudioFileStreamPropertyFlag_CacheProperty);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to parse bytes from audio stream");
        goto bail;
    }

bail:
    if (inBuf != nullptr) {
        env->ReleaseByteArrayElements(buf, inBuf, JNI_ABORT);
    }
    return;
}

/**
 * Opens the audio stream by registering Core Audio callbacks via AudioFileStreamOpen.
 *
 * @return pointer to CAAudioStreamIO struct, or 0 on error
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_open(JNIEnv *env, jobject stream, jint hint, jint bufferSize) {
    int res = 0;
    CAAudioStreamIO *asio = new CAAudioStreamIO{};  // zero-initializes all fields

    init_ids(env, stream);

    asio->srcBufferSize = static_cast<UInt32>(bufferSize);
    asio->env           = env;
    asio->javaInstance  = stream;

    res = AudioFileStreamOpen(asio, CAStreamInputStream_PropertyListenerProc, CAStreamInputStream_PacketsProc, static_cast<AudioFileTypeID>(hint), &asio->asid);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio stream");
        goto bail;
    }

bail:
    if (res) {
        if (asio->cookie   != nullptr) delete[] asio->cookie;
        if (asio->pktDescs != nullptr) delete[] asio->pktDescs;
        if (asio->asid     != nullptr) AudioFileStreamClose(asio->asid);
        delete asio;
        return 0;
    }
    return reinterpret_cast<jlong>(asio);
}

/**
 * Closes the stream and frees all associated resources.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_close(JNIEnv *env, jobject stream, jlong asioPtr) {
    if (asioPtr == 0) return;
    CAAudioStreamIO *asio = reinterpret_cast<CAAudioStreamIO*>(asioPtr);
    if (asio->cookie   != nullptr) delete[] asio->cookie;
    if (asio->pktDescs != nullptr) delete[] asio->pktDescs;
    int res = AudioFileStreamClose(asio->asid);
    if (res) {
        throwIOExceptionIfError(env, res, "Failed to close audio stream");
    }
    delete asio;
}
