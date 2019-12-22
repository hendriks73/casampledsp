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
#include "com_tagtraum_casampledsp_CACodecInputStream.h"
#include "CAUtils.h"

static jfieldID nativeBufferFID = NULL;
static jmethodID rewindMID = NULL;
static jmethodID limitMID = NULL;
static jmethodID fillNativeBufferMID = NULL;
static jmethodID hasRemainingMID = NULL;
static jmethodID positionMID = NULL;


/**
 * Callback for AudioConverterFillComplexBuffer used in fillNativeBuffer.
 */
static OSStatus CACodecInputStream_ComplexInputDataProc (
                                             AudioConverterRef             inAudioConverter,
                                             UInt32                        *ioNumberDataPackets,
                                             AudioBufferList               *ioData,
                                             AudioStreamPacketDescription  **outDataPacketDescription,
                                             void                          *inUserData) {

#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream_ComplexInputDataProc\n");
#endif
 
    int res = 0;
    CAAudioConverterIO *acio = (CAAudioConverterIO*)inUserData;
    jobject byteBuffer; 

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
    acio->env->CallIntMethod(byteBuffer, positionMID, acio->sourceAudioIO->srcBufferSize);
    ioData->mNumberBuffers = 1;
    ioData->mBuffers[0].mNumberChannels = acio->sourceAudioIO->srcFormat.mChannelsPerFrame;
    ioData->mBuffers[0].mDataByteSize = acio->sourceAudioIO->srcBufferSize;
    //ioData->mBuffers[0].mDataByteSize = acio->sourceAudioIO->pktDescs[0].mDataByteSize;
    ioData->mBuffers[0].mData = acio->sourceAudioIO->srcBuffer;
    *ioNumberDataPackets = acio->sourceAudioIO->pos - acio->sourceAudioIO->lastPos;

    if (outDataPacketDescription != NULL) {
        *outDataPacketDescription = acio->sourceAudioIO->pktDescs;
    }
    
bail:
    if (res) {
        *ioNumberDataPackets = 0;
    } 
    
    return res;
}

/**
 * Fill this streams native buffer by transcoding the content of the source stream's native buffer to the
 * desired format.
 *
 * @param env JNI env
 * @param stream calling stream object
 * @param converterPtr pointer to the used CAAudioConverterIO struct
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_fillNativeBuffer(JNIEnv *env, jobject stream, jlong converterPtr) {
    
#ifdef DEBUG
    fprintf(stderr, "CACodecInputStream fillNativeBuffer\n");
#endif

    int res = 0;
    int limit = 0;
    UInt32 ioOutputDataPacketSize;
    CAAudioConverterIO *acio = (CAAudioConverterIO*)converterPtr;
    AudioBufferList outOutputData;
    jobject byteBuffer = NULL;
    acio->env = env;
    
    // get java-managed byte buffer reference
    byteBuffer = env->GetObjectField(stream, nativeBufferFID);    
    if (byteBuffer == NULL) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer for this codec");
        goto bail;
    }
    
    // get pointer to our java managed bytebuffer
    acio->srcBuffer = (char *)env->GetDirectBufferAddress(byteBuffer);
    acio->srcBufferSize = env->GetDirectBufferCapacity(byteBuffer);
    if (acio->srcBuffer == NULL) {
        throwIOExceptionIfError(env, 1, "Failed to obtain native buffer address for this codec");
        goto bail;
    }
    ioOutputDataPacketSize = acio->srcBufferSize/acio->srcFormat.mBytesPerPacket;
    outOutputData.mNumberBuffers = 1;
    outOutputData.mBuffers[0].mNumberChannels = acio->srcFormat.mChannelsPerFrame;
    outOutputData.mBuffers[0].mDataByteSize = acio->srcBufferSize;
    outOutputData.mBuffers[0].mData = acio->srcBuffer;

    res = AudioConverterFillComplexBuffer(acio->acref,
                                    CACodecInputStream_ComplexInputDataProc,
                                    acio,
                                    &ioOutputDataPacketSize,
                                    &outOutputData,
                                    acio->pktDescs);

    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to fill complex audio buffer");
        goto bail;
    }
    
    // we already wrote to the buffer, now we still need to
    // set new bytebuffer limit and position to 0.
    acio->lastPos = acio->pos; 
    acio->pos += ioOutputDataPacketSize; 
    if (acio->srcFormat.mBytesPerPacket != 0) {
        limit = ioOutputDataPacketSize*acio->srcFormat.mBytesPerPacket;
    }
    else {
        uint i;
        for (i=0; i<ioOutputDataPacketSize; i++) {
            limit += acio->pktDescs[i].mDataByteSize;
        }
    }
    acio->srcBufferSize = limit;
    env->CallObjectMethod(byteBuffer, limitMID, (jint)limit);
    env->CallObjectMethod(byteBuffer, rewindMID);
    if (acio->sourceAudioIO->frameOffset != 0) {
#ifdef DEBUG
        fprintf(stderr, "Need to adjust position to frame: %i\n", acio->sourceAudioIO->frameOffset);
        fprintf(stderr, "acio->srcFormat.mBytesPerFrame  : %i\n", acio->srcFormat.mBytesPerFrame);
        env->CallIntMethod(byteBuffer, positionMID, acio->srcFormat.mBytesPerFrame * acio->sourceAudioIO->frameOffset);
#endif
        acio->sourceAudioIO->frameOffset = 0;
    }

bail:
    return;
}

/**
 * Sets up an AudioConverter to convert data to the desired format.
 *
 * @param env JNI env
 * @param stream calling stream object
 * @param targetFormat target format
 * @param sourceStream source data stream
 * @param pointer pointer to the source data stream's CAAudioIO struct
 * @return new CAAudioConverterIO pointer
 */
JNIEXPORT jlong JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_open(JNIEnv *env, jobject stream, jobject targetFormat, jobject sourceStream, jlong pointer) {
    int res = 0;
    CAAudioConverterIO *acio = new CAAudioConverterIO;
    acio->sourceStream = NULL;

    jobject byteBuffer = NULL;
    jclass audioFormatClass = NULL;
    jmethodID sampleRateMID = NULL;
    jmethodID channelsMID = NULL;
    jmethodID frameSizeMID = NULL;
    jmethodID sampleSizeInBitsMID = NULL;
    jmethodID encodingMID = NULL;
    jmethodID bigEndianMID = NULL;
    
    jclass caEncodingClass = NULL;
    jmethodID dataFormatMID = NULL;
    jobject targetEncoding = NULL;
    
    /* get method and field ids, if we don't have them already */
    if (fillNativeBufferMID == NULL || hasRemainingMID == NULL || positionMID == NULL || nativeBufferFID == NULL || rewindMID == NULL || limitMID == NULL) {
        jclass nativePeerInputStreamClass = env->FindClass("com/tagtraum/casampledsp/CANativePeerInputStream");
        fillNativeBufferMID = env->GetMethodID(nativePeerInputStreamClass, "fillNativeBuffer", "()V");
        nativeBufferFID = env->GetFieldID(nativePeerInputStreamClass, "nativeBuffer", "Ljava/nio/ByteBuffer;");
        jclass bufferClass = env->FindClass("java/nio/Buffer");
        hasRemainingMID = env->GetMethodID(bufferClass, "hasRemaining", "()Z");
        positionMID = env->GetMethodID(bufferClass, "position", "(I)Ljava/nio/Buffer;");
        rewindMID = env->GetMethodID(bufferClass, "rewind", "()Ljava/nio/Buffer;");
        limitMID = env->GetMethodID(bufferClass, "limit", "(I)Ljava/nio/Buffer;");
    }
    
    // get java-managed byte buffer reference
    byteBuffer = env->GetObjectField(stream, nativeBufferFID);    
    if (byteBuffer == NULL) {
        throwIOExceptionIfError(env, 1, "Failed to get native buffer for this codec");
        goto bail;
    }
    
    acio->sourceStream = env->NewGlobalRef(sourceStream);
    acio->sourceAudioIO = (CAAudioIO*)pointer;
    acio->pktDescs = NULL;
    acio->env = env;
    acio->srcBuffer = (char *)env->GetDirectBufferAddress(byteBuffer);
    acio->srcBufferSize = env->GetDirectBufferCapacity(byteBuffer);
    acio->cookie = NULL;
    acio->cookieSize = 0;
    acio->pos = 0;
    acio->lastPos = 0;
    acio->frameOffset = 0;

    
    audioFormatClass = env->FindClass("javax/sound/sampled/AudioFormat");
    sampleRateMID = env->GetMethodID(audioFormatClass, "getSampleRate", "()F");
    channelsMID = env->GetMethodID(audioFormatClass, "getChannels", "()I");
    frameSizeMID = env->GetMethodID(audioFormatClass, "getFrameSize", "()I");
    sampleSizeInBitsMID = env->GetMethodID(audioFormatClass, "getSampleSizeInBits", "()I");
    encodingMID = env->GetMethodID(audioFormatClass, "getEncoding", "()Ljavax/sound/sampled/AudioFormat$Encoding;");
    bigEndianMID = env->GetMethodID(audioFormatClass, "isBigEndian", "()Z");

    caEncodingClass = env->FindClass("com/tagtraum/casampledsp/CAAudioFormat$CAEncoding");
    dataFormatMID = env->GetMethodID(caEncodingClass, "getDataFormat", "()I");
    targetEncoding = env->CallObjectMethod(targetFormat, encodingMID);
    
    
    // set up the format we want to convert *to*
    acio->srcFormat.mSampleRate = (Float64)env->CallFloatMethod(targetFormat, sampleRateMID);
    acio->srcFormat.mChannelsPerFrame = (UInt32)env->CallIntMethod(targetFormat, channelsMID);
    acio->srcFormat.mBitsPerChannel = (UInt32)env->CallIntMethod(targetFormat, sampleSizeInBitsMID);
    acio->srcFormat.mFramesPerPacket = 1;
    acio->srcFormat.mBytesPerFrame = (UInt32)env->CallIntMethod(targetFormat, frameSizeMID);
    acio->srcFormat.mBytesPerPacket = acio->srcFormat.mBytesPerFrame;
    acio->srcFormat.mFormatID = (UInt32)env->CallIntMethod(targetEncoding, dataFormatMID);
    acio->srcFormat.mFormatFlags = 0;
    acio->srcFormat.mReserved = 0;

    // massage format flags
    if (acio->srcFormat.mFormatID == kAudioFormatLinearPCM) {
        acio->srcFormat.mFormatFlags += env->CallBooleanMethod(targetFormat, bigEndianMID) == JNI_TRUE ? kAudioFormatFlagIsBigEndian : 0;
        acio->srcFormat.mFormatFlags += kAudioFormatFlagIsPacked;
        //acio->srcFormat.mFormatFlags += kAudioFormatFlagIsFloat;
        // for now we don't support unsigned PCM
        acio->srcFormat.mFormatFlags += kAudioFormatFlagIsSignedInteger;
    }
        
    // make sure that AudioSystem#NOT_SPECIFIED (i.e. -1) is converted to 0.
    if (acio->srcFormat.mSampleRate < 0) acio->srcFormat.mSampleRate = 0;
    if (acio->srcFormat.mChannelsPerFrame < 0) acio->srcFormat.mChannelsPerFrame = 0;
    if (acio->srcFormat.mBitsPerChannel < 0) acio->srcFormat.mBitsPerChannel = 0;
    if (acio->srcFormat.mBytesPerFrame < 0) acio->srcFormat.mBytesPerFrame = 0;
    if (acio->srcFormat.mBytesPerPacket < 0) acio->srcFormat.mBytesPerPacket = 0;


    // checks - we need to make sure to not divide by zero later on
    if (acio->srcFormat.mBytesPerFrame == 0) {
        throwIllegalArgumentExceptionIfError(env, 1, "frameSize must be positive");
        goto bail;
    }
    if (acio->srcFormat.mBytesPerPacket == 0) {
        throwIllegalArgumentExceptionIfError(env, 1, "bytesPerPacket must be positive");
        goto bail;
    }
    if (acio->srcFormat.mBitsPerChannel == 0) {
        throwIllegalArgumentExceptionIfError(env, 1, "sampleSizeInBits must be positive");
        goto bail;
    }
    
    
    // setup the format we want to convert *from*
    while (acio->sourceAudioIO->srcFormat.mFormatID == 0) {
        // we don't have the native structure yet/anymore - therefore we have to call fillBuffer at least once
        env->CallVoidMethod(sourceStream, fillNativeBufferMID);
        res = acio->env->ExceptionCheck();
        if (res) {
            goto bail;
        }
    }
    
    res = AudioConverterNew(&acio->sourceAudioIO->srcFormat, &acio->srcFormat, &acio->acref);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to create native codec");
        goto bail;
    }
    
    // TODO: Deal with AudioConverterPrimeInfo as described in ConvertFile sample code.
    
    /*
    if (acio->sourceAudioIO->srcFormat.mBytesPerPacket == 0) {
		// input format is VBR, so we need to get max size per packet
        UInt32 size = sizeof(acio->srcSizePerPacket);
        res = AudioConverterGetProperty(acio->acref, kAudioConverterPropertyMaximumInputPacketSize, &size, &acio->srcSizePerPacket);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to get maximum output packet size");
            goto bail;
        }
        acio->srcSizePerPacket = 5793;
        fprintf(stderr, "acio->srcSizePerPacket   : %i\n", acio->srcSizePerPacket);
        fprintf(stderr, "acio->srcBufferSize      : %i\n", acio->srcBufferSize);
		acio->numPacketsPerRead = acio->srcBufferSize / acio->srcSizePerPacket;
		//acio->pktDescs = new AudioStreamPacketDescription [acio->numPacketsPerRead];
        fprintf(stderr, "acio->numPacketsPerRead   : %i\n", acio->numPacketsPerRead);
	}
	else {
		acio->srcSizePerPacket = acio->srcFormat.mBytesPerPacket;
		acio->numPacketsPerRead = acio->srcBufferSize / acio->srcSizePerPacket;
	}
     */
    
    // set cookie, if we have one
    if (acio->sourceAudioIO->cookieSize > 0) {
		res = AudioConverterSetProperty(acio->acref, kAudioConverterDecompressionMagicCookie, acio->sourceAudioIO->cookieSize, acio->sourceAudioIO->cookie);
        if (res) {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to set cookie from source.");
            goto bail;
        }
    }

bail:
    if (res) {
        if (acio->sourceStream != NULL) {
            env->DeleteGlobalRef(acio->sourceStream);
            acio->sourceStream = NULL;
        }
        if (acio->acref != NULL) {
            AudioConverterDispose(acio->acref);
        }
        if (acio->pktDescs != NULL) {
            delete acio->pktDescs;
        }
        delete acio;
    }
    
    return (jlong)acio;
}

/**
 * Closes the AudioConverter and cleans up other resources.
 *
 * @param env JNI env
 * @param stream calling stream object
 * @param converterPtr pointer to CAAudioConverterIO struct
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_close(JNIEnv *env, jobject stream, jlong converterPtr) {
    if (converterPtr == 0) return;
    CAAudioConverterIO *acio = (CAAudioConverterIO*)converterPtr;
    if (acio->sourceStream != NULL) {
        env->DeleteGlobalRef(acio->sourceStream);
        acio->sourceStream = NULL;
    }
    if (acio->acref != NULL) {
        int res = AudioConverterDispose(acio->acref);
        if (res) {
            throwIOExceptionIfError(env, res, "Failed to close codec");
        }
    }
    if (acio->pktDescs != NULL) {
        delete acio->pktDescs;
    }
    delete acio;
    
}

/**
 * Resets the converter - necessary after seek() to flush codec buffers.
 *
 * @param env JNI env
 * @param stream calling stream object
 * @param converterPtr pointer to CAAudioConverterIO struct
 */
JNIEXPORT void JNICALL Java_com_tagtraum_casampledsp_CACodecInputStream_reset(JNIEnv *env, jobject stream, jlong converterPtr) {
    if (converterPtr == 0) return;
    CAAudioConverterIO *acio = (CAAudioConverterIO*)converterPtr;
    if (acio->acref != NULL) {
        int res = AudioConverterReset(acio->acref);
        if (res) {
            throwIOExceptionIfError(env, res, "Failed to reset audio converter");
        }
    } else {
        throwIOExceptionIfError(env, -1, "Failed to reset audio converter as it is NULL");
    }
}

