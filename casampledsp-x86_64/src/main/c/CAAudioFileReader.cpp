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
#include "com_tagtraum_casampledsp_CAAudioFileReader.h"
#include "CAUtils.h"


/**
 * Constructs the Java CAAudioFileFormat object.
 */
static jobject create_CoreAudioAudioFileFormat_object(
        JNIEnv *env, jstring file,
        jint dataFormat, jfloat sampleRate, jint sampleSize,
        jint channels, jint frameSize, jfloat frameRate, jint frameLength,
        jboolean bigEndian, jlong duration, jint bitRate, jboolean vbr) {

    jclass coreAudioAudioFileFormatClass = env->FindClass("com/tagtraum/casampledsp/CAAudioFileFormat");
    if (coreAudioAudioFileFormatClass == nullptr) {
        return nullptr;
    }
    jmethodID cid = env->GetMethodID(coreAudioAudioFileFormatClass, "<init>", "(Ljava/lang/String;IFIIIFIZJIZ)V");
    if (cid == nullptr) {
        return nullptr;
    }
    jobject result = env->NewObject(coreAudioAudioFileFormatClass, cid,
                                    file, dataFormat, sampleRate, sampleSize, channels,
                                    frameSize, frameRate, frameLength, bigEndian, duration, bitRate, vbr);
    // Free local references
    env->DeleteLocalRef(coreAudioAudioFileFormatClass);
    return result;
}

/**
 * Creates a CAAudioFileFormat for a URL.
 *
 * @param urlBytes URL as UTF-8 bytes (from Java: url.toString().getBytes(UTF_8))
 * @return Java CAAudioFileFormat object
 */
JNIEXPORT jobject JNICALL Java_com_tagtraum_casampledsp_CAAudioFileReader_intGetAudioFormat___3B
        (JNIEnv *env, jobject instance, jbyteArray urlBytes) {

    int    res     = 0;
    AudioFileID infile = nullptr;
    AudioStreamBasicDescription inputFormat;
    jobject audioFormat          = nullptr;
    jlong   durationInMicroSeconds = 0;
    jfloat  frameRate            = 0;
    jboolean vbr                 = JNI_FALSE;
    jint    bitRate              = 0;
    jboolean bigEndian           = JNI_TRUE;
    UInt32  size                 = 0;
    CFURLRef inputURLRef         = nullptr;
    UInt64  dataPacketCount      = 0;
    UInt64  frameLength          = 0;
    const Float64 bitsPerByte    = 8.;

    jsize  urlLen = env->GetArrayLength(urlBytes);
    jbyte *urlBuf = env->GetByteArrayElements(urlBytes, nullptr);
    char  *urlStr = static_cast<char*>(malloc(urlLen + 1));
    memcpy(urlStr, urlBuf, urlLen);
    urlStr[urlLen] = '\0';
    env->ReleaseByteArrayElements(urlBytes, urlBuf, JNI_ABORT);

    inputURLRef = ca_url_ref_from_utf8(urlStr, urlLen);
    if (inputURLRef == nullptr) {
        throwUnsupportedAudioFileExceptionIfError(env, -1, "Malformed URL. Failed to create CFURL.");
        goto bail;
    }

    res = AudioFileOpenURL(inputURLRef, kAudioFileReadPermission, 0, &infile);
    if (res) {
        if (res == fnfErr) {
            throwFileNotFoundExceptionIfError(env, res, urlStr);
        } else {
            throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio file");
        }
        goto bail;
    }

    // get the input file format
    size = sizeof(inputFormat);
    res  = AudioFileGetProperty(infile, kAudioFilePropertyDataFormat, &size, &inputFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain data format");
        goto bail;
    }

    size = sizeof(dataPacketCount);
    res  = AudioFileGetProperty(infile, kAudioFilePropertyAudioDataPacketCount, &size, &dataPacketCount);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to obtain audio data packet count");
        goto bail;
    }
    // frameLength = number of frames in javax.sound.sampled
    frameLength            = dataPacketCount;
    frameRate              = static_cast<jfloat>(inputFormat.mSampleRate / inputFormat.mFramesPerPacket);
    durationInMicroSeconds = static_cast<jlong>(inputFormat.mFramesPerPacket * dataPacketCount * 1000LL * 1000LL
                                                / inputFormat.mSampleRate);

    if (inputFormat.mBytesPerPacket && inputFormat.mFramesPerPacket) {
        bitRate = static_cast<jint>(bitsPerByte * static_cast<Float64>(inputFormat.mBytesPerPacket)
                                    * inputFormat.mSampleRate / static_cast<Float64>(inputFormat.mFramesPerPacket));
        vbr = JNI_FALSE;
    } else {
        bitRate = -1;
        vbr     = JNI_TRUE;
    }
    bigEndian = static_cast<jboolean>(
        (inputFormat.mFormatID == kAudioFormatLinearPCM)
        && ((kAudioFormatFlagIsBigEndian & inputFormat.mFormatFlags) == kAudioFormatFlagIsBigEndian));

#ifdef DEBUG
    fprintf(stderr, "dataPackets: %llu\n", dataPacketCount);
    fprintf(stderr, "Frames/Pckt: %llu\n", inputFormat.mFramesPerPacket);
    fprintf(stderr, "sampleRate : %f\n",   inputFormat.mSampleRate);
    fprintf(stderr, "sampleSize : %i\n",   inputFormat.mBitsPerChannel);
    fprintf(stderr, "channels   : %i\n",   inputFormat.mChannelsPerFrame);
    fprintf(stderr, "packetSize : %i\n",   inputFormat.mBytesPerFrame);
    fprintf(stderr, "dataFormat : %i\n",   inputFormat.mFormatID);
    fprintf(stderr, "duration   : %ld\n",  durationInMicroSeconds);
    fprintf(stderr, "frameRate  : %f\n",   frameRate);
    fprintf(stderr, "frameLength: %i\n",   static_cast<int>(frameLength));
    fprintf(stderr, bigEndian ? "bigEndian : true\n" : "bigEndian : false\n");
#endif

    {
        jstring urlJString = env->NewStringUTF(urlStr);
        audioFormat = create_CoreAudioAudioFileFormat_object(
            env, urlJString,
            static_cast<jint>(inputFormat.mFormatID),
            static_cast<jfloat>(inputFormat.mSampleRate),
            static_cast<jint>(inputFormat.mBitsPerChannel),
            static_cast<jint>(inputFormat.mChannelsPerFrame),
            static_cast<jint>(inputFormat.mBytesPerFrame),
            frameRate,
            static_cast<jint>(frameLength),
            bigEndian,
            durationInMicroSeconds,
            bitRate,
            vbr);
        env->DeleteLocalRef(urlJString);
    }

bail:
    free(urlStr);
    if (inputURLRef != nullptr) CFRelease(inputURLRef);
    if (infile      != nullptr) AudioFileClose(infile);
    return audioFormat;
}

/**
 * Callback for AudioFileStreamOpen — packets proc (probe only, no real decoding).
 */
static void CAAudioFileReader_PacketsProc(
        void                         *inClientData,
        UInt32                        inNumberBytes,
        UInt32                        inNumberPackets,
        const void                   *inInputData,
        AudioStreamPacketDescription *inPacketDescriptions) {
#ifdef DEBUG
    fprintf(stderr, "CAAudioFileReader_PacketsProc\n");
#endif
}

/**
 * Callback for AudioFileStreamOpen — property listener (probe only, no real decoding).
 */
static void CAAudioFileReader_PropertyListenerProc(
        void                       *inClientData,
        AudioFileStreamID           inAudioFileStream,
        AudioFileStreamPropertyID   inPropertyID,
        UInt32                     *ioFlags) {
#ifdef DEBUG
    fprintf(stderr, "CAAudioFileReader_PropertyListenerProc\n");
    fprintf(stderr, "AudioFileStreamPropertyID %i\n", inPropertyID);
#endif
}

/**
 * Creates a CAAudioFileFormat for the first bytes of a stream.
 */
JNIEXPORT jobject JNICALL Java_com_tagtraum_casampledsp_CAAudioFileReader_intGetAudioFormat___3BII
        (JNIEnv *env, jobject instance, jbyteArray byteArray, jint length, jint hint) {

    int res = 0;
    AudioFileStreamID stream = nullptr;
    jobject audioFormat      = nullptr;
    jbyte  *inBuf            = nullptr;
    AudioStreamBasicDescription inputFormat;
    UInt32 size                  = 0;
    jlong  durationInMicroSeconds = -1;
    jfloat frameRate             = -1;
    UInt64 dataPacketCount       = static_cast<UInt64>(-1);
    UInt64 frameLength           = static_cast<UInt64>(-1);
    jboolean bigEndian           = JNI_TRUE;

    inBuf = env->GetByteArrayElements(byteArray, nullptr);
    res   = AudioFileStreamOpen(inBuf, CAAudioFileReader_PropertyListenerProc,
                                CAAudioFileReader_PacketsProc,
                                static_cast<AudioFileTypeID>(hint), &stream);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to open audio stream");
        goto bail;
    }

    res = AudioFileStreamParseBytes(stream, static_cast<UInt32>(length), inBuf, kAudioFileStreamPropertyFlag_CacheProperty);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to parse bytes");
        goto bail;
    }

    size = sizeof(inputFormat);
    res  = AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_DataFormat, &size, &inputFormat);
    if (res) {
        throwUnsupportedAudioFileExceptionIfError(env, res, "Failed to get data format from stream");
        goto bail;
    }

    size = sizeof(dataPacketCount);
    if (!AudioFileStreamGetProperty(stream, kAudioFileStreamProperty_AudioDataPacketCount, &size, &dataPacketCount)) {
        frameLength            = dataPacketCount;
        durationInMicroSeconds = static_cast<jlong>(inputFormat.mFramesPerPacket * dataPacketCount * 1000LL * 1000LL
                                                    / inputFormat.mSampleRate);
        frameRate              = static_cast<jfloat>(dataPacketCount * inputFormat.mSampleRate / static_cast<Float64>(frameLength));
    }

    bigEndian = static_cast<jboolean>(
        (inputFormat.mFormatID == kAudioFormatLinearPCM)
        && ((kAudioFormatFlagIsBigEndian & inputFormat.mFormatFlags) == kAudioFormatFlagIsBigEndian));

    audioFormat = create_CoreAudioAudioFileFormat_object(
        env, nullptr,
        static_cast<jint>(inputFormat.mFormatID),
        static_cast<jfloat>(inputFormat.mSampleRate),
        static_cast<jint>(inputFormat.mBitsPerChannel),
        static_cast<jint>(inputFormat.mChannelsPerFrame),
        static_cast<jint>(inputFormat.mBytesPerFrame),
        frameRate,
        static_cast<jint>(frameLength),
        bigEndian,
        durationInMicroSeconds,
        -1,    // bitRate unknown from probe
        false  // vbr unknown from probe
    );

bail:
    if (inBuf  != nullptr) env->ReleaseByteArrayElements(byteArray, inBuf, JNI_ABORT);
    if (stream != nullptr) AudioFileStreamClose(stream);
    return audioFormat;
}
