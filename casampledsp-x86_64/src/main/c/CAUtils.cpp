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
#include "CAUtils.h"


/**
 * Converts an error code to a four letter code.
 */
static void fourLetterCode(int err, char *cbuf) {
    cbuf[0] = ((char*)&err)[3];
    cbuf[1] = ((char*)&err)[2];
    cbuf[2] = ((char*)&err)[1];
    cbuf[3] = ((char*)&err)[0];
}

/**
 * Throws an UnsupportedAudioFileException exception
 */
void throwUnsupportedAudioFileExceptionIfError(JNIEnv *env, int err, const char * message) {
    if (err) {
        //fprintf (stderr, "UnsupportedAudioFileException: '%s' %d (%4.4s)\n", message, (int)err, (char*)&err);
        char cbuf[4];
        fourLetterCode(err, cbuf);
        char formattedMessage [strlen(message)+8];
        snprintf(formattedMessage, strlen(message)+8, "%s (%4.4s)", message, cbuf);
        jclass excCls = env->FindClass("javax/sound/sampled/UnsupportedAudioFileException");
        env->ThrowNew(excCls, formattedMessage);
    }
}

/**
 * Throws an IOException.
 */
void throwIOExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (err) {
		//fprintf (stderr, "IOException: '%s' %d (%4.4s)\n", message, (int)err, (char*)&err);
        char cbuf[4];
        fourLetterCode(err, cbuf);
        char formattedMessage [strlen(message)+8];
        snprintf(formattedMessage, strlen(message)+8, "%s (%4.4s)", message, cbuf);
        jclass excCls = env->FindClass("java/io/IOException");
        env->ThrowNew(excCls, formattedMessage);
    }
}

/**
 * Throws an IllegalArgumentException.
 */
void throwIllegalArgumentExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (err) {
		//fprintf (stderr, "IllegalArgumentException: '%s' %d (%4.4s)\n", message, (int)err, (char*)&err);
        char cbuf[4];
        fourLetterCode(err, cbuf);
        char formattedMessage [strlen(message)+8];
        snprintf(formattedMessage, strlen(message)+8, "%s (%4.4s)", message, cbuf);
        jclass excCls = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(excCls, formattedMessage);
        
    }
}
/**
 * Throws an IllegalArgumentException.
 */
void throwFileNotFoundExceptionIfError(JNIEnv *env, int err, const char *message) {
    if (err) {
		//fprintf (stderr, "FileNotFoundException: '%s' %d (%4.4s)\n", message, (int)err, (char*)&err);
        jclass excCls = env->FindClass("java/io/FileNotFoundException");
        env->ThrowNew(excCls, message);
    }
}

/**
 * Creates a CFURLRef from the given path.
 */
void ca_create_url_ref(JNIEnv *env, jstring path, CFURLRef &urlRef) {
    const jchar *chars = env->GetStringChars(path, NULL);
    CFStringRef cfPath = CFStringCreateWithCharacters (kCFAllocatorDefault, chars, env->GetStringLength(path));
    env->ReleaseStringChars(path, chars);
	urlRef = CFURLCreateWithString(kCFAllocatorDefault, cfPath, NULL);
	CFRelease(cfPath);
}
