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
#include "com_tagtraum_casampledsp_CAURLInputStream.h"
#include "CAUtils.h"

static jfieldID nativeBufferFID = nullptr;
static jmethodID rewindMID      = nullptr;
static jmethodID limitMID       = nullptr;

/**
 * Init static method and field ids for Java methods/fields, if we don't have them already.
 */
static void init_ids(JNIEnv *env, jobject stream) {
    if (nativeBufferFID == nullptr || rewindMID == nullptr || limitMID == nullptr) {
        nativeBufferFID = env->GetFieldID(env->GetObjectClass(stream), "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass = env->FindClass("java/nio/Buffer");
        rewindMID = env->GetMethodID(bufferClass, "rewind", "()Ljava/nio/Buffer;");
        limitMID  = env->GetMethodID(bufferClass, "limit",  "(I)Ljava/nio/Buffer;");
    }
}


/**
 * Callback to fill the native buffer.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong afioPtr) {
#ifdef DEBUG
    fprintf(stderr, "fillNativeBuffer: %llu\n", afioPtr);
#endif

    int res = 0;
    jobject byteBuffer = nullptr;
    CAAudioFileIO *afio = reinterpret_cast<CAAudioFileIO*>(afioPtr);
    UInt32 ioNumberDataPackets;
    UInt32 outNumBytes = 0;

    init_ids(env, stream);

    byteBuffer = env->GetObjectField(stream, nativeBufferFID);
    if (byteBuffer == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer");
        goto bail;
    }

    // find out buffer's capacity
    outNumBytes      = static_cast<UInt32>(env->GetDirectBufferCapacity(byteBuffer));
    // get pointer to our java managed bytebuffer
    afio->srcBuffer  = static_cast<char*>(env->GetDirectBufferAddress(byteBuffer));
    if (afio->srcBuffer == nullptr) {
        throwIOExceptionIfError(env, 1, "Failed to get address for native buffer");
        goto bail;
    }

    // figure out how much to read
    ioNumberDataPackets = afio->numPacketsPerRead;

    // do the actual read from the file
    res = AudioFileReadPacketData(afio->afid, false, &outNumBytes, afio->pktDescs,
                                  afio->pos, &ioNumberDataPackets, afio->srcBuffer);
    if (res) {
        throwIOExceptionIfError(env, res, "Failed to read packet data from file");
        goto bail;
    }

    // advance input file packet position
    afio->lastPos      = afio->pos;
    afio->pos         += ioNumberDataPackets;
    afio->srcBufferSize = outNumBytes;

    // we already wrote to the buffer, now we still need to
    // set new bytebuffer limit and position to 0.
    env->CallObjectMethod(byteBuffer, rewindMID);
    env->CallObjectMethod(byteBuffer, limitMID, static_cast<jint>(outNumBytes));

bail:
    return;
}

/**
 * Opens the given URL via AudioFileOpenURL.
 *
 * @param urlBytes URL as UTF-8 bytes (from Java: url.toString().getBytes(UTF_8))
 * @return pointer to the underlying CAAudioFileIO struct, or 0 on error
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_open(JNIEnv *env, jobject stream, jbyteArray urlBytes, jint bufferSize) {
    int res = 0;
    CFURLRef inputURLRef = nullptr;
    CAAudioFileIO *afio  = new CAAudioFileIO{};   // zero-initializes all fields
    UInt32 size;
    jsize  urlLen = env->GetArrayLength(urlBytes);
    jbyte *urlBuf = env->GetByteArrayElements(urlBytes, nullptr);
    char  *urlStr = static_cast<char*>(malloc(urlLen + 1));
    memcpy(urlStr, urlBuf, urlLen);
    urlStr[urlLen] = '\0';
    env->ReleaseByteArrayElements(urlBytes, urlBuf, JNI_ABORT);

    afio->srcBufferSize    = static_cast<UInt32>(bufferSize);
    afio->numPacketsPerRead = 1;

    inputURLRef = ca_url_ref_from_utf8(urlStr, urlLen);
    res = AudioFileOpenURL(inputURLRef, kAudioFileReadPermission, 0, &afio->afid);
    if (res) {
        if (res == fnfErr || res == kAudioFileUnspecifiedError) {
            throwFileNotFoundExceptionIfError(env, res, urlStr);
        } else {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio file");
        }
        goto bail;
    }

    // get the source file format
    size = sizeof(afio->srcFormat);
    res  = AudioFileGetProperty(afio->afid, kAudioFilePropertyDataFormat, &size, &afio->srcFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain format for audio file");
        goto bail;
    }

    // find out how many packets fit into the buffer
    if (!afio->srcFormat.mBytesPerPacket) {
#ifdef DEBUG
        fprintf(stderr, "VBR\n");
#endif
        size = sizeof(afio->srcSizePerPacket);
        res  = AudioFileGetProperty(afio->afid, kAudioFilePropertyPacketSizeUpperBound, &size, &afio->srcSizePerPacket);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain packet size upper bound");
            goto bail;
        }
        if (afio->srcSizePerPacket != 0) {
            afio->numPacketsPerRead = afio->srcBufferSize / afio->srcSizePerPacket;
        }
#ifdef DEBUG
        else { fprintf(stderr, "VBR: srcSizePerPacket == 0!!\n"); }
#endif
        afio->pktDescs = new AudioStreamPacketDescription[afio->numPacketsPerRead]{};
    } else {
#ifdef DEBUG
        fprintf(stderr, "CBR\n");
#endif
        afio->srcSizePerPacket = afio->srcFormat.mBytesPerPacket;
        if (afio->srcSizePerPacket != 0) {
            afio->numPacketsPerRead = afio->srcBufferSize / afio->srcSizePerPacket;
        }
#ifdef DEBUG
        else { fprintf(stderr, "CBR: srcSizePerPacket == 0!!\n"); }
#endif
    }

    res = AudioFileGetPropertyInfo(afio->afid, kAudioFilePropertyMagicCookieData, &afio->cookieSize, nullptr);
    if (res && res != kAudioFileUnsupportedPropertyError) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain cookie info from audio file");
        goto bail;
    }
    res = 0;
    if (afio->cookieSize) {
        afio->cookie = new char[afio->cookieSize];
        res = AudioFileGetProperty(afio->afid, kAudioFilePropertyMagicCookieData, &afio->cookieSize, afio->cookie);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain cookie from audio file");
            goto bail;
        }
    }

bail:
    free(urlStr);
    if (inputURLRef != nullptr) CFRelease(inputURLRef);
    if (res) {
        if (afio->afid    != nullptr) AudioFileClose(afio->afid);
        if (afio->pktDescs != nullptr) delete[] afio->pktDescs;
        if (afio->cookie   != nullptr) delete[] afio->cookie;
        delete afio;
        return 0;
    }

#ifdef DEBUG
    fprintf(stderr, "Opened: %llu\n", reinterpret_cast<jlong>(afio));
#endif
    return reinterpret_cast<jlong>(afio);
}

/**
 * Indicates whether the resource is seekable.
 */
JNIEXPORT jboolean JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_isSeekable(JNIEnv *env, jobject stream, jlong afioPtr) {
    return JNI_TRUE;
}

/**
 * Attempts to seek to a given timestamp in the resource.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_seek(JNIEnv *env, jobject stream, jlong afioPtr, jlong microseconds) {
    int res = 0;
    UInt32 size;
    CAAudioFileIO *afio = reinterpret_cast<CAAudioFileIO*>(afioPtr);
    AudioFramePacketTranslation translation;

    translation.mFrame = static_cast<SInt64>(afio->srcFormat.mSampleRate * microseconds) / 1000000LL;

#ifdef DEBUG
    fprintf(stderr, "microseconds      : %llu\n", microseconds);
    fprintf(stderr, "translation.mFrame: %llu\n", translation.mFrame);
#endif

    size = sizeof(translation);
    res  = AudioFileGetProperty(afio->afid, kAudioFilePropertyFrameToPacket, &size, &translation);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to translate frame to packet.");
        goto bail;
    }
    afio->pos         = translation.mPacket;
    afio->frameOffset = translation.mFrameOffsetInPacket;

#ifdef DEBUG
    fprintf(stderr, "frameOffset: %i\n",   afio->frameOffset);
    fprintf(stderr, "afio->pos  : %llu\n", afio->pos);
#endif

bail:
    return;
}

/**
 * Closes this resource and frees all associated resources.
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_close(JNIEnv *env, jobject stream, jlong afioPtr) {
#ifdef DEBUG
    fprintf(stderr, "Closing: %llu\n", afioPtr);
#endif
    if (afioPtr == 0) return;

    CAAudioFileIO *afio = reinterpret_cast<CAAudioFileIO*>(afioPtr);
    int res = AudioFileClose(afio->afid);
    if (res) {
        throwIOExceptionIfError(env, res, "Failed to close audio file");
    }
    if (afio->pktDescs != nullptr) delete[] afio->pktDescs;
    if (afio->cookie   != nullptr) delete[] afio->cookie;
    delete afio;
}
