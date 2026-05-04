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
#include "CAUtils.h"
#include <string>


/**
 * Converts an error code to a four letter code.
 */
static void fourLetterCode(int err, char *cbuf) {
    const char *p = reinterpret_cast<const char*>(&err);
    cbuf[0] = p[3];
    cbuf[1] = p[2];
    cbuf[2] = p[1];
    cbuf[3] = p[0];
}

static std::string formatErrorMessage(const char *message, int err) {
    char cbuf[4];
    fourLetterCode(err, cbuf);
    return std::string(message) + " (" + std::string(cbuf, 4) + ")";
}

/**
 * Throws an UnsupportedAudioFileException.
 */
void throwUnsupportedAudioFileExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (!err) return;
#ifdef DEBUG
    fprintf(stderr, "UnsupportedAudioFileException: '%s' %d\n", message, err);
#endif
    const std::string formatted = formatErrorMessage(message, err);
    jclass excCls = env->FindClass("javax/sound/sampled/UnsupportedAudioFileException");
    env->ThrowNew(excCls, formatted.c_str());
}

/**
 * Throws an IOException.
 */
void throwIOExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (!err) return;
#ifdef DEBUG
    fprintf(stderr, "IOException: '%s' %d\n", message, err);
#endif
    const std::string formatted = formatErrorMessage(message, err);
    jclass excCls = env->FindClass("java/io/IOException");
    env->ThrowNew(excCls, formatted.c_str());
}

/**
 * Throws an IllegalArgumentException.
 */
void throwIllegalArgumentExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (!err) return;
#ifdef DEBUG
    fprintf(stderr, "IllegalArgumentException: '%s' %d\n", message, err);
#endif
    const std::string formatted = formatErrorMessage(message, err);
    jclass excCls = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(excCls, formatted.c_str());
}

/**
 * Throws a FileNotFoundException.
 */
void throwFileNotFoundExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (!err) return;
    jclass excCls = env->FindClass("java/io/FileNotFoundException");
    env->ThrowNew(excCls, message);
}

/**
 * Creates a CFURLRef from a UTF-8 byte buffer supplied by the Java caller.
 * The Java side calls url.toString().getBytes(StandardCharsets.UTF_8) before
 * crossing the JNI boundary, so no JNI call-back into Java is needed here.
 */
CFURLRef ca_url_ref_from_utf8(const char *urlBytes, CFIndex len) {
    return CFURLCreateWithBytes(kCFAllocatorDefault,
                                reinterpret_cast<const UInt8*>(urlBytes),
                                len, kCFStringEncodingUTF8, nullptr);
}
