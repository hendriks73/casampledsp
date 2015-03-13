/*
 * =================================================
 * Copyright 2011 tagtraum industries incorporated
 * This file is part of CASampledSP.
 *
 * FFSampledSP is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFSampledSP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFSampledSP; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * =================================================
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
#include "com_tagtraum_casampledsp_CAURLInputStream.h"
#include "CAUtils.h"

static jfieldID nativeBufferFID = NULL;
static jmethodID rewindMID = NULL;
static jmethodID limitMID = NULL;

/**
 * Init static method and field ids for Java methods/fields, if we don't have them already.
 *
 * @param env JNIEnv
 */
static void init_ids(JNIEnv *env, jobject stream) {
    if (nativeBufferFID == NULL || rewindMID == NULL || limitMID == NULL) {
        nativeBufferFID = env->GetFieldID(env->GetObjectClass(stream), "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass = env->FindClass("java/nio/Buffer");
        rewindMID = env->GetMethodID(bufferClass, "rewind", "()Ljava/nio/Buffer;");
        limitMID = env->GetMethodID(bufferClass, "limit", "(I)Ljava/nio/Buffer;");
    }
}


/**
 * Callback to fill the native buffer.
 *
 * @param env JNI env
 * @param stream calling Java stream
 * @param afioPtr pointer to CAAudioFileIO
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong afioPtr) {
#ifdef DEBUG
    fprintf(stderr, "fillNativeBuffer: %llu\n", afioPtr);
#endif

    int res = 0;
    jobject byteBuffer = NULL;
    CAAudioFileIO *afio = (CAAudioFileIO*)afioPtr;
	UInt32 outNumBytes = BUFFER_SIZE; // max bytes to read
    UInt32 ioNumberDataPackets;

    init_ids(env, stream);

    // get java-managed byte buffer reference
    byteBuffer = env->GetObjectField(stream, nativeBufferFID);    
    if (byteBuffer == NULL) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer");
        goto bail;
    }
    // get pointer to our java managed bytebuffer
    afio->srcBuffer = (char *)env->GetDirectBufferAddress(byteBuffer);
    if (afio->srcBuffer == NULL) {
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
    afio->lastPos = afio->pos;
	afio->pos += ioNumberDataPackets;
    afio->srcBufferSize = outNumBytes;

    // we already wrote to the buffer, now we still need to
    // set new bytebuffer limit and position to 0.
    env->CallObjectMethod(byteBuffer, rewindMID);
    env->CallObjectMethod(byteBuffer, limitMID, outNumBytes);

bail:
    return;
}

/**
 * Opens the given URL via AudioFileOpenURL.
 *
 * @param env JNI env
 * @param stream calling Java stream
 * @param url URL to open
 * @return pointer to the underlying CAAudioFileIO struct
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_open(JNIEnv *env, jobject stream, jstring url) {
    int res = 0;
	CFURLRef inputURLRef;
    CAAudioFileIO *afio = new CAAudioFileIO;
    UInt32 size;
    
	afio->srcBufferSize = BUFFER_SIZE;
	afio->pos = 0;
	afio->afid = NULL;
    afio->pktDescs = NULL;
    afio->cookie = NULL;
    afio->cookieSize = 0;
    afio->frameOffset = 0;

    // open file
    ca_create_url_ref(env, url, inputURLRef);
    res = AudioFileOpenURL(inputURLRef, 0x01, 0, &afio->afid); // 0x01 = read only
    if (res) {
        if (res == fnfErr || res == kAudioFileUnspecifiedError) {
            throwFileNotFoundExceptionIfError(env, res, env->GetStringUTFChars(url, NULL));
        } else {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio file");
        }
        goto bail;
    }
    
    // get the source file format
	size = sizeof(afio->srcFormat);
	res = AudioFileGetProperty(afio->afid, kAudioFilePropertyDataFormat, &size, &afio->srcFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain format for audio file");
        goto bail;
    }
    
    // find out how many packets fit into the buffer
    if (!afio->srcFormat.mBytesPerPacket) {
		// format is VBR, so we need to get max size per packet
		size = sizeof(afio->srcSizePerPacket);
		res = AudioFileGetProperty(afio->afid, kAudioFilePropertyPacketSizeUpperBound, &size, &afio->srcSizePerPacket);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain packet size upper bound");
            goto bail;
        }
		afio->numPacketsPerRead = afio->srcBufferSize / afio->srcSizePerPacket;
		afio->pktDescs = new AudioStreamPacketDescription [afio->numPacketsPerRead];
	}
	else {
		afio->srcSizePerPacket = afio->srcFormat.mBytesPerPacket;
		afio->numPacketsPerRead = afio->srcBufferSize / afio->srcSizePerPacket;
	}
    
    // check for cookies
    res = AudioFileGetPropertyInfo(afio->afid, kAudioFilePropertyMagicCookieData, &afio->cookieSize, NULL);
    if (res && res != kAudioFileUnsupportedPropertyError) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain cookie info from audio file");
        goto bail;
    }
    res = 0;
	if (!res && afio->cookieSize) {
		afio->cookie = new char[afio->cookieSize];
		res = AudioFileGetProperty(afio->afid, kAudioFilePropertyMagicCookieData, &afio->cookieSize, afio->cookie);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain cookie from audio file");
            goto bail;
        }
	}
    
    
bail:
    if (res) {
        if (afio->afid != NULL) {
            AudioFileClose(afio->afid);
        }
        if (afio->pktDescs != NULL) {
            delete afio->pktDescs;
        }
        if (afio->cookie != NULL) {
            delete afio->cookie;
        }
        delete afio;
    }

#ifdef DEBUG
    fprintf(stderr, "Opened: %llu\n", (jlong)afio);
#endif

    return (jlong)afio;
}

/**
 * Indicates whether the resource is seekable.
 *
 * @param env JNI env
 * @param stream calling Java stream
 * @param afioPtr pointer to CAAudioFileIO
 * @return always returns <code>JNI_TRUE</code>
 */
JNIEXPORT jboolean JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_isSeekable(JNIEnv *env, jobject stream, jlong afioPtr) {
    return JNI_TRUE;
}

/**
 * Attempts the seek a given timestamp in the resource.
 *
 * @param env JNI env
 * @param stream calling Java stream
 * @param afioPtr pointer to CAAudioFileIO
 * @param microseconds timestamp in microseconds
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_seek(JNIEnv *env, jobject stream, jlong afioPtr, jlong microseconds) {
    int res = 0;
    UInt32 size;
    CAAudioFileIO *afio = (CAAudioFileIO*)afioPtr;
    AudioFramePacketTranslation translation;

    translation.mFrame = (SInt64)(afio->srcFormat.mSampleRate * microseconds) / 1000000LL;

#ifdef DEBUG
    fprintf(stderr, "microseconds      : %llu\n", microseconds);
    fprintf(stderr, "translation.mFrame: %llu\n", translation.mFrame);
#endif

    size = sizeof(translation);
    res = AudioFileGetProperty(afio->afid, kAudioFilePropertyFrameToPacket, &size, &translation);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to translate frame to packet.");
        goto bail;
    }
    afio->pos = translation.mPacket;
    afio->frameOffset = translation.mFrameOffsetInPacket;


#ifdef DEBUG
    fprintf(stderr, "frameOffset: %i\n", afio->frameOffset);
    fprintf(stderr, "afio->pos  : %llu\n", afio->pos);
#endif

    bail:

    return;
}


/**
 * Closes this resource and frees all associated resources.
 *
 * @param env JNI env
 * @param stream calling Java stream
 * @param afioPtr pointer to CAAudioFileIO
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAURLInputStream_close(JNIEnv *env, jobject stream, jlong afioPtr) {

#ifdef DEBUG
    fprintf(stderr, "Closing: %llu\n", afioPtr);
#endif
    if (afioPtr == 0) return;

    CAAudioFileIO *afio = (CAAudioFileIO*)afioPtr;
    int res = AudioFileClose(afio->afid);
    if (res) {
        throwIOExceptionIfError(env, res, "Failed to close audio file");
    }
    if (afio->pktDescs != NULL) {
        delete afio->pktDescs;
    }
    if (afio->cookie != NULL) {
        delete afio->cookie;
    }
    delete afio;
    return;
}
