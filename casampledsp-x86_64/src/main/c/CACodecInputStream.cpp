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
#include "com_tagtraum_casampledsp_CACodecInputStream.h"
#include "CAUtils.h"

static jfieldID  nativeBufferFID     = nullptr;
static jmethodID rewindMID           = nullptr;
static jmethodID limitMID            = nullptr;
static jmethodID fillNativeBufferMID = nullptr;
static jmethodID hasRemainingMID     = nullptr;
static jmethodID positionMID         = nullptr;


/**
 * Callback for AudioConverterFillComplexBuffer used in fillNativeBuffer.
 */
static OSStatus CACodecInputStream_ComplexInputDataProc(
        AudioConverterRef             inAudioConverter,
        UInt32                       *ioNumberDataPackets,
        AudioBufferList              *ioData,
        AudioStreamPacketDescription **outDataPacketDescription,
        void                         *inUserData) {
#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream_ComplexInputDataProc\n");
#endif

    int res = 0;
    CAAudioConverterIO *acio = static_cast<CAAudioConverterIO*>(inUserData);
    jobject byteBuffer = nullptr;

    *ioNumberDataPackets = 0;
    byteBuffer = acio->env->GetObjectField(acio->sourceStream, nativeBufferFID);

    // check whether we have to fill the source's native buffer
    if (acio->env->CallBooleanMethod(byteBuffer, hasRemainingMID) == JNI_FALSE) {
        // fill native buffer
        acio->env->CallVoidMethod(acio->sourceStream, fillNativeBufferMID);
        res = acio->env->ExceptionCheck();
        if (res) {
            goto bail;
        }
    }
    if (acio->env->CallBooleanMethod(byteBuffer, hasRemainingMID) == JNI_FALSE) {
        goto bail;
    }
    // move position in java bytebuffer
    acio->env->CallIntMethod(byteBuffer, positionMID, static_cast<jint>(acio->sourceAudioIO->srcBufferSize));
    ioData->mNumberBuffers                  = 1;
    ioData->mBuffers[0].mNumberChannels     = acio->sourceAudioIO->srcFormat.mChannelsPerFrame;
    ioData->mBuffers[0].mDataByteSize       = acio->sourceAudioIO->srcBufferSize;
    ioData->mBuffers[0].mData               = acio->sourceAudioIO->srcBuffer;
    *ioNumberDataPackets                    = static_cast<UInt32>(acio->sourceAudioIO->pos - acio->sourceAudioIO->lastPos);

    if (outDataPacketDescription != nullptr) {
        *outDataPacketDescription = acio->sourceAudioIO->pktDescs;
    }

#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream_ComplexInputDataProc *ioNumberDataPackets=%i\n", *ioNumberDataPackets);
#endif

bail:
    if (byteBuffer != nullptr) {
        acio->env->DeleteLocalRef(byteBuffer);
    }
    if (res) {
#ifdef DEBUG
        fprintf(stderr, "CACodecInputStream_ComplexInputDataProc res=%i\n", res);
#endif
        *ioNumberDataPackets = 0;
    }
    return res;
}

/**
 * Fill this stream's native buffer by transcoding the source stream's native buffer to the
 * desired format.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong converterPtr) {
#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream fillNativeBuffer\n");
#endif

    int res = 0;
    int limit = 0;
    UInt32 ioOutputDataPacketSize;
    CAAudioConverterIO *acio = reinterpret_cast<CAAudioConverterIO*>(converterPtr);
    AudioBufferList outOutputData;
    jobject byteBuffer = nullptr;
    acio->env = env;

    // get java-managed byte buffer reference
    byteBuffer = env->GetObjectField(stream, nativeBufferFID);
    if (byteBuffer == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer for this codec");
        goto bail;
    }

    // get pointer to our java managed bytebuffer
    acio->srcBuffer     = static_cast<char*>(env->GetDirectBufferAddress(byteBuffer));
    acio->srcBufferSize = static_cast<UInt32>(env->GetDirectBufferCapacity(byteBuffer));
    if (acio->srcBuffer == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to obtain native buffer address for this codec");
        goto bail;
    }
    ioOutputDataPacketSize                  = acio->srcBufferSize / acio->srcFormat.mBytesPerPacket;
    outOutputData.mNumberBuffers            = 1;
    outOutputData.mBuffers[0].mNumberChannels = acio->srcFormat.mChannelsPerFrame;
    outOutputData.mBuffers[0].mDataByteSize = acio->srcBufferSize;
    outOutputData.mBuffers[0].mData         = acio->srcBuffer;

#ifdef DEBUG
    fprintf(stderr, "pre AudioConverterFillComplexBuffer\n");
#endif
    res = AudioConverterFillComplexBuffer(acio->acref,
                                          CACodecInputStream_ComplexInputDataProc,
                                          acio,
                                          &ioOutputDataPacketSize,
                                          &outOutputData,
                                          acio->pktDescs);
#ifdef DEBUG
    fprintf(stderr, "post AudioConverterFillComplexBuffer\n");
#endif
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to fill complex audio buffer");
        goto bail;
    }

    // we already wrote to the buffer, now we still need to
    // set new bytebuffer limit and position to 0.
    acio->lastPos = acio->pos;
    acio->pos    += ioOutputDataPacketSize;
#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream fillNativeBuffer ioOutputDataPacketSize=%i\n", ioOutputDataPacketSize);
    fprintf(stderr, "CACodecInputStream fillNativeBuffer acio->srcFormat.mBytesPerPacket=%i\n", acio->srcFormat.mBytesPerPacket);
#endif
    if (acio->srcFormat.mBytesPerPacket != 0) {
        limit = static_cast<int>(ioOutputDataPacketSize * acio->srcFormat.mBytesPerPacket);
    } else {
        for (UInt32 i = 0; i < ioOutputDataPacketSize; i++) {
            limit += static_cast<int>(acio->pktDescs[i].mDataByteSize);
        }
    }
#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream fillNativeBuffer limit=%i\n", limit);
#endif
    acio->srcBufferSize = static_cast<UInt32>(limit);
    env->CallObjectMethod(byteBuffer, limitMID, static_cast<jint>(limit));
    env->CallObjectMethod(byteBuffer, rewindMID);
    if (acio->sourceAudioIO->frameOffset != 0) {
#ifdef DEBUG
        fprintf(stderr, "Need to adjust position to frame: %i\n", acio->sourceAudioIO->frameOffset);
        fprintf(stderr, "acio->srcFormat.mBytesPerFrame  : %i\n", acio->srcFormat.mBytesPerFrame);
        env->CallIntMethod(byteBuffer, positionMID,
            static_cast<jint>(acio->srcFormat.mBytesPerFrame * acio->sourceAudioIO->frameOffset));
#endif
        acio->sourceAudioIO->frameOffset = 0;
    }

bail:
    return;
}

/**
 * Sets up an AudioConverter to convert data to the desired format.
 *
 * @return new CAAudioConverterIO pointer, or 0 on error
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_open(JNIEnv *env, jobject stream, jobject targetFormat, jobject sourceStream, jlong pointer) {
    int res = 0;
    CAAudioConverterIO *acio = new CAAudioConverterIO{};  // zero-initializes all fields

    jobject byteBuffer         = nullptr;
    jclass  audioFormatClass   = nullptr;
    jmethodID sampleRateMID    = nullptr;
    jmethodID channelsMID      = nullptr;
    jmethodID frameSizeMID     = nullptr;
    jmethodID sampleSizeMID    = nullptr;
    jmethodID encodingMID      = nullptr;
    jmethodID bigEndianMID     = nullptr;
    jclass    caEncodingClass  = nullptr;
    jmethodID dataFormatMID    = nullptr;
    jobject   targetEncoding   = nullptr;
    jclass    encodingBaseClass = nullptr;
    jmethodID toStringMID      = nullptr;
    jstring   encodingNameStr  = nullptr;
    const char *encodingName   = nullptr;
    bool isFloatTarget         = false;

    if (fillNativeBufferMID == nullptr || hasRemainingMID == nullptr || positionMID == nullptr
            || nativeBufferFID == nullptr || rewindMID == nullptr || limitMID == nullptr) {
        jclass nativePeerClass = env->FindClass("com/tagtraum/casampledsp/CANativePeerInputStream");
        fillNativeBufferMID = env->GetMethodID(nativePeerClass, "fillNativeBuffer", "()V");
        nativeBufferFID     = env->GetFieldID(nativePeerClass,  "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass  = env->FindClass("java/nio/Buffer");
        hasRemainingMID = env->GetMethodID(bufferClass, "hasRemaining", "()Z");
        positionMID     = env->GetMethodID(bufferClass, "position",     "(I)Ljava/nio/Buffer;");
        rewindMID       = env->GetMethodID(bufferClass, "rewind",       "()Ljava/nio/Buffer;");
        limitMID        = env->GetMethodID(bufferClass, "limit",        "(I)Ljava/nio/Buffer;");
    }

    // get java-managed byte buffer reference
    byteBuffer = env->GetObjectField(stream, nativeBufferFID);
    if (byteBuffer == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer for this codec");
        goto bail;
    }

    acio->sourceStream  = env->NewGlobalRef(sourceStream);
    acio->sourceAudioIO = reinterpret_cast<CAAudioIO*>(pointer);
    acio->env           = env;
    acio->srcBuffer     = static_cast<char*>(env->GetDirectBufferAddress(byteBuffer));
    acio->srcBufferSize = static_cast<UInt32>(env->GetDirectBufferCapacity(byteBuffer));

    audioFormatClass  = env->FindClass("javax/sound/sampled/AudioFormat");
    sampleRateMID     = env->GetMethodID(audioFormatClass, "getSampleRate",     "()F");
    channelsMID       = env->GetMethodID(audioFormatClass, "getChannels",       "()I");
    frameSizeMID      = env->GetMethodID(audioFormatClass, "getFrameSize",      "()I");
    sampleSizeMID     = env->GetMethodID(audioFormatClass, "getSampleSizeInBits", "()I");
    encodingMID       = env->GetMethodID(audioFormatClass, "getEncoding",       "()Ljavax/sound/sampled/AudioFormat$Encoding;");
    bigEndianMID      = env->GetMethodID(audioFormatClass, "isBigEndian",       "()Z");

    caEncodingClass = env->FindClass("com/tagtraum/casampledsp/CAAudioFormat$CAEncoding");
    dataFormatMID   = env->GetMethodID(caEncodingClass, "getDataFormat", "()I");
    targetEncoding  = env->CallObjectMethod(targetFormat, encodingMID);

    // Detect PCM_FLOAT by encoding name: both PCM_SIGNED and PCM_FLOAT share
    // kAudioFormatLinearPCM as format ID; the distinction is in the format flags.
    encodingBaseClass = env->FindClass("javax/sound/sampled/AudioFormat$Encoding");
    toStringMID       = env->GetMethodID(encodingBaseClass, "toString", "()Ljava/lang/String;");
    encodingNameStr   = static_cast<jstring>(env->CallObjectMethod(targetEncoding, toStringMID));
    encodingName      = env->GetStringUTFChars(encodingNameStr, nullptr);
    isFloatTarget     = (strcmp(encodingName, "PCM_FLOAT") == 0);
    env->ReleaseStringUTFChars(encodingNameStr, encodingName);
    env->DeleteLocalRef(encodingNameStr);

    // set up the target AudioStreamBasicDescription
    acio->srcFormat.mSampleRate       = static_cast<Float64>(env->CallFloatMethod(targetFormat, sampleRateMID));
    acio->srcFormat.mChannelsPerFrame = static_cast<UInt32>(env->CallIntMethod(targetFormat, channelsMID));
    acio->srcFormat.mBitsPerChannel   = static_cast<UInt32>(env->CallIntMethod(targetFormat, sampleSizeMID));
    acio->srcFormat.mFramesPerPacket  = 1;
    acio->srcFormat.mBytesPerFrame    = static_cast<UInt32>(env->CallIntMethod(targetFormat, frameSizeMID));
    acio->srcFormat.mBytesPerPacket   = acio->srcFormat.mBytesPerFrame;
    acio->srcFormat.mFormatID         = static_cast<UInt32>(env->CallIntMethod(targetEncoding, dataFormatMID));

    if (acio->srcFormat.mFormatID == kAudioFormatLinearPCM) {
        if (isFloatTarget) {
            acio->srcFormat.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
        } else {
            acio->srcFormat.mFormatFlags =
                (env->CallBooleanMethod(targetFormat, bigEndianMID) == JNI_TRUE ? kAudioFormatFlagIsBigEndian : 0u)
                | kAudioFormatFlagIsPacked
                | kAudioFormatFlagIsSignedInteger;
        }
    }

    // convert AudioSystem.NOT_SPECIFIED (-1) to 0
    if (static_cast<SInt32>(acio->srcFormat.mSampleRate)       < 0) acio->srcFormat.mSampleRate       = 0;
    if (static_cast<SInt32>(acio->srcFormat.mChannelsPerFrame)  < 0) acio->srcFormat.mChannelsPerFrame  = 0;
    if (static_cast<SInt32>(acio->srcFormat.mBitsPerChannel)    < 0) acio->srcFormat.mBitsPerChannel    = 0;
    if (static_cast<SInt32>(acio->srcFormat.mBytesPerFrame)     < 0) acio->srcFormat.mBytesPerFrame     = 0;
    if (static_cast<SInt32>(acio->srcFormat.mBytesPerPacket)    < 0) acio->srcFormat.mBytesPerPacket    = 0;

    if (acio->srcFormat.mBytesPerFrame  == 0) { throwIllegalArgumentExceptionIfError(env, 1, "frameSize must be positive");     goto bail; }
    if (acio->srcFormat.mBytesPerPacket == 0) { throwIllegalArgumentExceptionIfError(env, 1, "bytesPerPacket must be positive"); goto bail; }
    if (acio->srcFormat.mBitsPerChannel == 0) { throwIllegalArgumentExceptionIfError(env, 1, "sampleSizeInBits must be positive"); goto bail; }

    while (acio->sourceAudioIO->srcFormat.mFormatID == 0) {
        env->CallVoidMethod(sourceStream, fillNativeBufferMID);
        res = acio->env->ExceptionCheck();
        if (res) goto bail;
    }

    res = AudioConverterNew(&acio->sourceAudioIO->srcFormat, &acio->srcFormat, &acio->acref);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to create native codec");
        goto bail;
    }

    if (acio->sourceAudioIO->cookieSize > 0) {
        res = AudioConverterSetProperty(acio->acref, kAudioConverterDecompressionMagicCookie,
                                        acio->sourceAudioIO->cookieSize, acio->sourceAudioIO->cookie);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to set cookie from source.");
            goto bail;
        }
    }

bail:
    if (res) {
        if (acio->sourceStream != nullptr) {
            env->DeleteGlobalRef(acio->sourceStream);
            acio->sourceStream = nullptr;
        }
        if (acio->acref    != nullptr) AudioConverterDispose(acio->acref);
        if (acio->pktDescs != nullptr) delete[] acio->pktDescs;
        delete acio;
        return 0;
    }
    return reinterpret_cast<jlong>(acio);
}

/**
 * Closes the AudioConverter and cleans up all resources.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_close(JNIEnv *env, jobject stream, jlong converterPtr) {
    if (converterPtr == 0) return;
    CAAudioConverterIO *acio = reinterpret_cast<CAAudioConverterIO*>(converterPtr);
    if (acio->sourceStream != nullptr) {
        env->DeleteGlobalRef(acio->sourceStream);
        acio->sourceStream = nullptr;
    }
    if (acio->acref != nullptr) {
        int res = AudioConverterDispose(acio->acref);
        if (res) {
            throwIOExceptionIfError(env, res, "Failed to close codec");
        }
    }
    if (acio->pktDescs != nullptr) delete[] acio->pktDescs;
    delete acio;
}

/**
 * Resets the converter — necessary after seek() to flush codec buffers.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_reset(JNIEnv *env, jobject stream, jlong converterPtr) {
    if (converterPtr == 0) return;
    CAAudioConverterIO *acio = reinterpret_cast<CAAudioConverterIO*>(converterPtr);
    if (acio->acref != nullptr) {
        int res = AudioConverterReset(acio->acref);
        if (res) {
            throwIOExceptionIfError(env, res, "Failed to reset audio converter");
        }
    } else {
        throwIOExceptionIfError(env, -1, "Failed to reset audio converter as it is NULL");
    }
}
