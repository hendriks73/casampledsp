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
#include "com_tagtraum_casampledsp_CAAudioFileReader.h"
#include "CAUtils.h"


/**
 * Creates the Java CoreAudioFileFormat object.
 *
 * @return Java CoreAudioFileFormat object.
 */
static jobject create_CoreAudioAudioFileFormat_object(JNIEnv *env, jstring file, jint dataFormat, jfloat sampleRate, jint sampleSize,
                                         jint channels, jint frameSize, jfloat frameRate, jint frameLength,
                                         jboolean bigEndian, jlong duration,
                                         jint bitRate, jboolean vbr) {
    jclass coreAudioAudioFileFormatClass;
    jmethodID cid;
    jobject result = NULL;
    
    coreAudioAudioFileFormatClass = env->FindClass("com/tagtraum/casampledsp/CAAudioFileFormat");
    if (coreAudioAudioFileFormatClass == NULL) {
        return NULL; // exception thrown
    }
    
    // Get the method ID for the constructor
    cid = env->GetMethodID(coreAudioAudioFileFormatClass, "<init>", "(Ljava/lang/String;IFIIIFIZJIZ)V");
    if (cid == NULL) {
        return NULL; // exception thrown
    }
    
    // Construct an CoreAudioAudioFormat object
    result = env->NewObject(coreAudioAudioFileFormatClass, cid, file, dataFormat, sampleRate, sampleSize, channels,
                            frameSize, frameRate, frameLength, bigEndian, duration, bitRate, vbr);
    
    // Free local references
    env->DeleteLocalRef(coreAudioAudioFileFormatClass);
    return result;    
}

/**
 * Creates a CoreAudioFileFormat for a URL.
 *
 * @param env JNI env
 * @param instance stream instance
 * @param url URL
 * @return Java CoreAudioFileFormat object
 */
JNIEXPORT jobject JNICALL Java_com_tagtraum_casampledsp_CAAudioFileReader_intGetAudioFormat__Ljava_lang_String_2
        (JNIEnv *env, jobject instance, jstring url) {
    
    int res;
	AudioFileID infile = NULL;
	AudioStreamBasicDescription inputFormat;
    jobject audioFormat = NULL;
    jlong durationInMicroSeconds;
    jfloat frameRate;
    jboolean vbr = JNI_FALSE;
    jint bitRate;
    jboolean bigEndian = JNI_TRUE;
    UInt32 size;
	CFURLRef inputURLRef;
    UInt64 dataPacketCount;
    UInt64 frameLength = 0;
    const Float64 bitsPerByte = 8.;
    
    ca_create_url_ref(env, url, inputURLRef);
    if (inputURLRef == NULL) {
        throwUnsupportedAudioFileExceptionIfError(env, -1, "Malformed URL. Failed to create CFURL.");
        goto bail;
    }

    res = AudioFileOpenURL(inputURLRef, 0x01, 0, &infile); // 0x01 = read only
    if (res) {
        if (res == fnfErr) {
            throwFileNotFoundExceptionIfError(env, res, env->GetStringUTFChars(url, NULL));
        } else {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio file");
        }
        goto bail;
    }
	
    // get the input file format
	size = sizeof(inputFormat);
	res = AudioFileGetProperty(infile, kAudioFilePropertyDataFormat, &size, &inputFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain data format");
        goto bail;
    }
    
    size = sizeof(dataPacketCount);
    res = AudioFileGetProperty(infile, kAudioFilePropertyAudioDataPacketCount, &size, &dataPacketCount);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain audio data packet count");
        goto bail;
    }
    // frameLength = number of frames in javax.sound.sampled
    frameLength = dataPacketCount;
    frameRate = (jfloat)(inputFormat.mSampleRate/inputFormat.mFramesPerPacket);
    durationInMicroSeconds = (jlong)((inputFormat.mFramesPerPacket * dataPacketCount * 1000L * 1000L) / inputFormat.mSampleRate);

	if (inputFormat.mBytesPerPacket && inputFormat.mFramesPerPacket) {
		bitRate = (jint)(bitsPerByte * (Float64)inputFormat.mBytesPerPacket * inputFormat.mSampleRate / (Float64)inputFormat.mFramesPerPacket);
        vbr = JNI_FALSE;
	} else {		
        bitRate = -1;
        vbr = JNI_TRUE;
    }   
    bigEndian = (inputFormat.mFormatID == kAudioFormatLinearPCM) && ((kAudioFormatFlagIsBigEndian & inputFormat.mFormatFlags) == kAudioFormatFlagIsBigEndian);
#ifdef DEBUG
    fprintf(stderr, "dataPackets: %llu\n", dataPacketCount);
    fprintf(stderr, "Frames/Pckt: %llu\n", inputFormat.mFramesPerPacket);
    fprintf(stderr, "sampleRate : %f\n", inputFormat.mSampleRate);
    fprintf(stderr, "sampleSize : %i\n", inputFormat.mBitsPerChannel);
    fprintf(stderr, "channels   : %i\n", inputFormat.mChannelsPerFrame);
    fprintf(stderr, "packetSize : %i\n", inputFormat.mBytesPerFrame);
    fprintf(stderr, "dataFormat : %i\n", inputFormat.mFormatID);
    fprintf(stderr, "duration   : %ld\n", durationInMicroSeconds);
    fprintf(stderr, "frameRate  : %f\n", frameRate);
    fprintf(stderr, "frameLength: %i\n", frameLength);
    if (bigEndian) {
        fprintf(stderr, "bigEndian : true\n");
    } else {
        fprintf(stderr, "bigEndian : false\n");
    }
#endif
    audioFormat = create_CoreAudioAudioFileFormat_object(env, url,
                                                         inputFormat.mFormatID,
                                                         inputFormat.mSampleRate,
                                                         inputFormat.mBitsPerChannel,
                                                         inputFormat.mChannelsPerFrame,
                                                         inputFormat.mBytesPerFrame,
                                                         frameRate,
                                                         (jint)frameLength,
                                                         bigEndian,
                                                         durationInMicroSeconds,
                                                         bitRate,
                                                         vbr);
bail:
    if (infile != NULL) {
        AudioFileClose(infile);
        infile = NULL;
    }    
    return audioFormat;
}

/**
 * Callback for AudioFileStreamOpen used for determining AudioFileFormat for a stream.
 * Implementation serves debug purposes only.
 */
static void CAAudioFileReader_PacketsProc (
                                    void                          *inClientData,
                                    UInt32                        inNumberBytes,
                                    UInt32                        inNumberPackets,
                                    const void                    *inInputData,
                                    AudioStreamPacketDescription  *inPacketDescriptions
                                    ){
#ifdef DEBUG
    fprintf(stderr, "CAAudioFileReader_PacketsProc\n");
#endif
}

/**
 * Callback for AudioFileStreamOpen used for determining AudioFileFormat for a stream.
 * Implementation serves debug purposes only.
 */
static void CAAudioFileReader_PropertyListenerProc (
                                             void                        *inClientData,
                                             AudioFileStreamID           inAudioFileStream,
                                             AudioFileStreamPropertyID   inPropertyID,
                                             UInt32                      *ioFlags
                                             ) {
#ifdef DEBUG
    fprintf(stderr, "CAAudioFileReader_PropertyListenerProc\n");
    fprintf(stderr, "AudioFileStreamPropertyID %i\n", inPropertyID);
#endif
}

/**
 * Creates a CoreAudioFileFormat for the first X bytes of a stream.
 *
 * @param env JNI env
 * @param instance stream instance
 * @param byteArray first X bytes
 * @param length of the byte array
 * @param hint file type hint
 * @return Java CoreAudioFileFormat object
 */
JNIEXPORT jobject JNICALL Java_com_tagtraum_casampledsp_CAAudioFileReader_intGetAudioFormat___3BII
        (JNIEnv *env, jobject instance, jbyteArray byteArray, jint length, jint hint) {
    int res;
    AudioFileStreamID stream = NULL;
    jobject audioFormat = NULL;
    jbyte *inBuf = NULL;
    AudioStreamBasicDescription inputFormat;
    UInt32 size;
    jlong durationInMicroSeconds = -1;
    jfloat frameRate = -1;
    UInt64 dataPacketCount = -1;
    UInt64 frameLength = -1;
    jboolean bigEndian = JNI_TRUE;

    inBuf = env->GetByteArrayElements(byteArray, NULL);
    res = AudioFileStreamOpen(inBuf, CAAudioFileReader_PropertyListenerProc, CAAudioFileReader_PacketsProc, hint, &stream);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio stream");
        goto bail;
    }
    
    res = AudioFileStreamParseBytes(stream, length, inBuf, kAudioFileStreamPropertyFlag_CacheProperty);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to parse bytes");
        goto bail;
    }

    // now check stuff
    // get the input file format
	size = sizeof(inputFormat);
	res = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_DataFormat, &size, &inputFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to get data format from stream");
        goto bail;
    }

    size = sizeof(dataPacketCount);
    res = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_AudioDataPacketCount, &size, &dataPacketCount);
    if (!res) {
        frameLength = dataPacketCount;
        durationInMicroSeconds = (jlong)((inputFormat.mFramesPerPacket * dataPacketCount * 1000L * 1000L) / inputFormat.mSampleRate);
        frameRate = (jfloat)(dataPacketCount*inputFormat.mSampleRate/frameLength);
    }
//    if (!res && inputFormat.mFramesPerPacket) {
//        frameLength = inputFormat.mFramesPerPacket * dataPacketCount;
//        durationInMicroSeconds = (jlong)((frameLength * 1000L * 1000L) / inputFormat.mSampleRate);
//        frameRate = (jfloat)(dataPacketCount*inputFormat.mSampleRate/frameLength);
//    }
    bigEndian = (inputFormat.mFormatID == kAudioFormatLinearPCM) && ((kAudioFormatFlagIsBigEndian & inputFormat.mFormatFlags) == kAudioFormatFlagIsBigEndian);
    audioFormat = create_CoreAudioAudioFileFormat_object(env, NULL,
                                                         inputFormat.mFormatID,
                                                         inputFormat.mSampleRate,
                                                         inputFormat.mBitsPerChannel,
                                                         inputFormat.mChannelsPerFrame,
                                                         inputFormat.mBytesPerFrame,
                                                         frameRate,
                                                         (jint)frameLength,
                                                         bigEndian,
                                                         durationInMicroSeconds,
                                                         -1, //bitRate,
                                                         false //vbr
                                                         );
    
bail:
    if (inBuf != NULL) {
        env->ReleaseByteArrayElements(byteArray, inBuf, JNI_ABORT);
    }
    if (stream != NULL) {
        AudioFileStreamClose(stream);
        stream = NULL;
    }    
    return audioFormat;
}


