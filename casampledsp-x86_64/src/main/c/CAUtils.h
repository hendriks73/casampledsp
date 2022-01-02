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
#include <math.h>
#include <jni.h>
#include <AudioToolbox/AudioToolbox.h>
#include <CoreServices/CoreServices.h>


/*! \mainpage CASampledSP
 *
 * \section intro_sec Introduction
 *
 * <a href="../index.html">CASampledSP</a> is a free implementation
 * of the <a href="http://docs.oracle.com/javase/10/docs/api/javax/sound/sampled/spi/package-summary.html">javax.sound.sampled.spi</a>
 * interfaces.
 * <br/>
 * Its main purpose is to decode audio from various formats at high speed.
 * CASampledSP supports pretty much all formats supported by Apple's Core Audio.
 * <br/>
 * This is the source documentation for the native part of the library. You can find the
 * documentation for the Java part <a href="../apidocs/index.html">here</a>.
 */

/**
 * Central context representing the native peer to the Java CANativePeerInputStream object.
 */
struct CAAudioIO
{
    JNIEnv *        env;                        ///< JNI environment
    jobject         javaInstance;               ///< Calling java object
	AudioStreamBasicDescription srcFormat;      ///< Source format
    AudioStreamPacketDescription * pktDescs;    ///< Packet descriptions
	char *			srcBuffer;                  ///< Source buffer
	UInt32			srcBufferSize;              ///< Source buffer size
	UInt32			numPacketsPerRead;          ///< Number of packets per read
	SInt64			pos;                        ///< Current position (in packets)
	SInt64			lastPos;                    ///< Last position (in packets)
	UInt32          frameOffset;                ///< Frame offset (needed for seeking to the middles of a packet)
	UInt32			srcSizePerPacket;           ///< Source size per packet
    char *          cookie;                     ///< Cookie
    UInt32          cookieSize;                 ///< Cookie size
};

/**
 * Central context representing the native peer to the Java CACodecInputStream object.
 */
struct CAAudioConverterIO:CAAudioIO
{
    AudioConverterRef   acref;          ///< The used AudioConverter
    jobject             sourceStream;   ///< Source stream object (Java)
    CAAudioIO *         sourceAudioIO;  ///< CAAudioIO of the stream that we want to convert
};

/**
 * Central context representing the native peer to the Java CAURLInputStream object.
 */
struct CAAudioFileIO:CAAudioIO
{
	AudioFileID		afid;           ///< File id
};

/**
 * Central context representing the native peer to the Java CAStreamInputStream object.
 */
struct CAAudioStreamIO:CAAudioIO
{
	AudioFileStreamID   asid;       ///< Stream id
};

void throwUnsupportedAudioFileExceptionIfError(JNIEnv *, int, const char*);

void throwIOExceptionIfError(JNIEnv *, int, const char*);

void throwIllegalArgumentExceptionIfError(JNIEnv *, int, const char *);

void throwFileNotFoundExceptionIfError(JNIEnv *, int, const char *);

/**
 * Returns true if an error occurred.
 */
void ca_create_url_ref(JNIEnv *, jstring, CFURLRef&);