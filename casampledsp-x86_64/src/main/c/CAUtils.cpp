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
    // message is usually a URL in UTF-8, which ThrowNew (Modified UTF-8) would garble
    jclass excCls = env->FindClass("java/io/FileNotFoundException");
    if (excCls == nullptr) return;
    jmethodID cid = env->GetMethodID(excCls, "<init>", "(Ljava/lang/String;)V");
    if (cid == nullptr) return;
    jstring msg = ca_new_string_from_utf8(env, message, static_cast<CFIndex>(strlen(message)));
    if (msg == nullptr) return;
    jobject exc = env->NewObject(excCls, cid, msg);
    if (exc != nullptr) env->Throw(static_cast<jthrowable>(exc));
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

/**
 * Copies a Java byte[] holding UTF-8 into a new NUL-terminated C string.
 * Returns nullptr with a Java exception pending on failure. Release with free().
 */
char *ca_utf8_from_byte_array(JNIEnv *env, jbyteArray bytes, jsize *len) {
    *len = env->GetArrayLength(bytes);
    char *str = static_cast<char*>(malloc(static_cast<size_t>(*len) + 1));
    if (str == nullptr) {
        jclass excCls = env->FindClass("java/lang/OutOfMemoryError");
        if (excCls != nullptr) env->ThrowNew(excCls, "Failed to allocate URL buffer");
        return nullptr;
    }
    env->GetByteArrayRegion(bytes, 0, *len, reinterpret_cast<jbyte*>(str));
    if (env->ExceptionCheck()) {
        free(str);
        return nullptr;
    }
    str[*len] = '\0';
    return str;
}

/**
 * Creates a Java string from standard UTF-8. Unlike NewStringUTF, which expects
 * Modified UTF-8, this handles 4-byte sequences such as emoji.
 * Returns nullptr with a Java exception pending on failure.
 */
jstring ca_new_string_from_utf8(JNIEnv *env, const char *utf8, CFIndex len) {
    CFStringRef cfString = CFStringCreateWithBytes(kCFAllocatorDefault,
                                                   reinterpret_cast<const UInt8*>(utf8),
                                                   len, kCFStringEncodingUTF8, false);
    if (cfString == nullptr) {
        throwIllegalArgumentExceptionIfError(env, -1, "Invalid UTF-8");
        return nullptr;
    }
    const CFIndex length = CFStringGetLength(cfString);
    UniChar *chars = new UniChar[length > 0 ? length : 1];
    CFStringGetCharacters(cfString, CFRangeMake(0, length), chars);
    jstring result = env->NewString(reinterpret_cast<const jchar*>(chars), static_cast<jsize>(length));
    delete[] chars;
    CFRelease(cfString);
    return result;
}
