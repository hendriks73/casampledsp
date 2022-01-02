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
#include <pthread.h>

static jfieldID nativeBufferFieldID = NULL;
static jmethodID rewindMethodID = NULL;
static jmethodID setLimitMethodID = NULL;
static jmethodID getLimitMethodID = NULL;
static jmethodID setPositionMethodID = NULL;
static jmethodID getPositionMethodID = NULL;

/**
 * Init static method and field ids for Java methods/fields, if we don't have them already.
 *
 * @param env JNIEnv
 */
static void init_ids(JNIEnv *env, jobject stream) {
    // get method and field ids, if we don't have them already 
    if (nativeBufferFieldID == NULL || rewindMethodID == NULL || setLimitMethodID == NULL
            || getLimitMethodID == NULL || getPositionMethodID==NULL || setPositionMethodID==NULL) {

        nativeBufferFieldID = env->GetFieldID(env->GetObjectClass(stream), "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass = env->FindClass("java/nio/Buffer");
        rewindMethodID = env->GetMethodID(bufferClass, "rewind", "()Ljava/nio/Buffer;");
        setLimitMethodID = env->GetMethodID(bufferClass, "limit", "(I)Ljava/nio/Buffer;");
        getLimitMethodID = env->GetMethodID(bufferClass, "limit", "()I");
        getPositionMethodID = env->GetMethodID(bufferClass, "position", "()I");
        setPositionMethodID = env->GetMethodID(bufferClass, "position", "(I)Ljava/nio/Buffer;");;
    }
}


/**
 * Packet callback for AudioFileStreamOpen used in <code>open</code>.
 */
static void CAStreamInputStream_PacketsProc (
                                           void                          *inClientData,
                                           UInt32                        inNumberBytes,
                                           UInt32                        inNumberPackets,
                                           const void                    *inInputData,
                                           AudioStreamPacketDescription  *inPacketDescriptions
                                           ){
#ifdef DEBUG
    fprintf(stderr, "CAStreamInputStream_PacketsProc\n");
#endif

    CAAudioStreamIO *asio = (CAAudioStreamIO*)inClientData;
    jobject byteBuffer = NULL;
    jlong capacity = 0;
    jint limit = 0;
    int totalPackets = 0;
    int oldPackets = 0;
    AudioStreamPacketDescription *newPktDescs = NULL;
    int i=0;

    // get java-managed byte buffer reference
    byteBuffer = asio->env->GetObjectField(asio->javaInstance, nativeBufferFieldID);    
    if (byteBuffer == NULL) {
        throwIOExceptionIfError(asio->env, 1, "Failed to obtain native buffer");
        goto bail;
    }
    limit = asio->env->CallIntMethod(byteBuffer, getLimitMethodID);

    // get pointer to our java managed bytebuffer
    asio->srcBuffer = (char *)asio->env->GetDirectBufferAddress(byteBuffer);
    capacity = asio->env->GetDirectBufferCapacity(byteBuffer);
    if (asio->srcBuffer == NULL) {
        throwIOExceptionIfError(asio->env, 1, "Failed to obtain direct buffer address");
        goto bail;
    }
    if (capacity-limit < inNumberBytes) {
        throwIOExceptionIfError(asio->env, 1, "Native buffer to small for decoded audio");
        goto bail;
    }
    // copy data to our byte buffer
    memcpy(asio->srcBuffer+limit, inInputData, inNumberBytes);
    
    // advance input file packet position
    if (limit == 0) {
        asio->lastPos = asio->pos;
    }
    oldPackets = asio->pos - asio->lastPos;
	asio->pos += inNumberPackets;
    asio->srcBufferSize = inNumberBytes+limit;
    totalPackets = asio->pos - asio->lastPos;

    // we already wrote to the buffer, now we still need to
    // set new bytebuffer limit and position to 0.
    asio->env->CallObjectMethod(byteBuffer, setPositionMethodID, 0);
    asio->env->CallObjectMethod(byteBuffer, setLimitMethodID, inNumberBytes+limit);
    
    if (inPacketDescriptions) {
        newPktDescs = new AudioStreamPacketDescription[totalPackets];
        if (asio->pktDescs != NULL) {
            // copy to new array
            memcpy(newPktDescs, asio->pktDescs, sizeof(AudioStreamPacketDescription)*oldPackets);
            // delete the old one
            delete[] asio->pktDescs;
        }

        // copy new packets
        memcpy(&newPktDescs[oldPackets], inPacketDescriptions, sizeof(AudioStreamPacketDescription)*inNumberPackets);
        // correct offsets
        for (i=1; i<totalPackets; i++) {
            newPktDescs[i].mStartOffset = newPktDescs[i-1].mDataByteSize+newPktDescs[i-1].mStartOffset;
        }
        asio->pktDescs = newPktDescs;
    }
    
bail:
    return;
}


/**
 * Property callback for AudioFileStreamOpen used in <code>open</code>.
 */
static void CAStreamInputStream_PropertyListenerProc(void                        *inClientData,
                                                    AudioFileStreamID           stream,
                                                    AudioFileStreamPropertyID   inPropertyID,
                                                    UInt32                      *ioFlags) {
    int res = 0;
    UInt32 size;

#ifdef DEBUG
    fprintf(stderr, "CAStreamInputStream_PropertyListenerProc\n");
    fprintf(stderr, "AudioFileStreamPropertyID %i\n", inPropertyID);
#endif

    CAAudioStreamIO *asio = (CAAudioStreamIO*)inClientData;

    if (inPropertyID == kAudioFileStreamProperty_MagicCookieData) {
        res = AudioFileStreamGetPropertyInfo(stream, kAudioFileStreamProperty_MagicCookieData, &asio->cookieSize, NULL);
        if (res && res != kAudioFileUnsupportedPropertyError) {
            throwUnsupportedAudioFileExceptionIfError(asio->env, res, "Failed to obtain cookie info from audio stream");
            goto bail;
        }
        res = 0;
        if (!res && asio->cookieSize) {
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
        res = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_DataFormat, &size, &asio->srcFormat);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(asio->env, res, "Failed to read audio format from stream");
            goto bail;
        }
        /*
        // find out how many packets fit into the buffer
        if (asio->srcFormat.mBytesPerPacket == 0) {
            // format is VBR, so we need to get max size per packet
            size = sizeof(asio->srcSizePerPacket);
            res = AudioFileStreamGetProperty(asio->asid, kAudioFileStreamProperty_MaximumPacketSize, &size, &asio->srcSizePerPacket);
            if (res) {
                throwIOExceptionIfError(asio->env, res, "AudioStreamGetProperty kAudioFileStreamProperty_MaximumPacketSize failed");
                goto bail;
            }
            asio->numPacketsPerRead = asio->srcBufferSize / asio->srcSizePerPacket;
            asio->pktDescs = new AudioStreamPacketDescription [asio->numPacketsPerRead];
        }
        else {
            asio->srcSizePerPacket = asio->srcFormat.mBytesPerPacket;
            asio->numPacketsPerRead = asio->srcBufferSize / asio->srcSizePerPacket;
            asio->pktDescs = NULL;
        }
        */
    }
    
bail:
    return;
}

/**
 * Called by the Java code to fill the native buffer.
 *
 * @param env JNI env
 * @param stream stream instance
 * @param asioPtr pointer to CAAudioStreamIO
 * @param buf byte array with the first X bytes of data
 * @param length length of the byte buffer
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong asioPtr, jbyteArray buf, jint length) {

#ifdef DEBUG
    fprintf(stderr, "fillNativeBuffer: %lld\n", asioPtr);
#endif
    
    int res = 0;
    CAAudioStreamIO *asio = (CAAudioStreamIO*)asioPtr;
    char inBuf[length];

    // update jav env
    asio->env = env;
    asio->javaInstance = stream;
    
    // convert byte buf to native array
    env->GetByteArrayRegion(buf, 0, length, (jbyte*)inBuf);
    
    // pump bytes into the stream reader
    res = AudioFileStreamParseBytes(asio->asid, length, &inBuf, kAudioFileStreamPropertyFlag_CacheProperty);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to parse bytes from audio stream");
        goto bail;
    }
    
bail:
    return;
}

/**
 * Opens the audio stream - at this point only the callbacks are set up via AudioFileStreamOpen.
 *
 * @param env JNI env
 * @param stream Java stream instance
 * @param hint file type hint
 * @return pointer to CAAudioStreamIO struct
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_open
        (JNIEnv *env, jobject stream, jint hint, jint bufferSize) {
    int res = 0;
    CAAudioStreamIO *asio = new CAAudioStreamIO;
    
    init_ids(env, stream);
    
	asio->srcBufferSize = bufferSize;
	asio->pos = 0;
	asio->lastPos = 0;
	asio->asid = NULL;
    asio->env = env;
    asio->javaInstance = stream;
    asio->srcFormat.mFormatID = 0;
    asio->pktDescs = NULL;
    asio->cookie = NULL;
    asio->cookieSize = 0;
    asio->frameOffset = 0;

    res = AudioFileStreamOpen(asio, CAStreamInputStream_PropertyListenerProc, CAStreamInputStream_PacketsProc, hint, &asio->asid);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio stream");
        goto bail;
    }

bail:
    if (res) {
        if (asio->cookie != NULL) {
            delete asio->cookie;
        }
        if (asio->pktDescs != NULL) {
            delete[] asio->pktDescs;
        }
        if (asio->asid != NULL) {
            AudioFileStreamClose(asio->asid);
        }
        delete asio;
    }
    
    return (jlong)asio;
}

/**
 * Closes the stream and all associated resources.
 *
 * @param env JNI env
 * @param stream calling stream instance
 * @param asioPtr pointer to CAAudioStreamIO
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CAStreamInputStream_close
        (JNIEnv *env, jobject stream, jlong asioPtr) {
    if (asioPtr == 0) return;
    CAAudioStreamIO *asio = (CAAudioStreamIO*)asioPtr;
    if (asio->cookie != NULL) {
        delete asio->cookie;
    }
    if (asio->pktDescs != NULL) {
        delete[] asio->pktDescs;
    }
    int res = AudioFileStreamClose(asio->asid);
    if (res) {
        throwIOExceptionIfError(env, res, "Failed to close audio stream");
    }
    delete asio;
}
