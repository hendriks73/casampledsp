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
 */
package com.tagtraum.casampledsp;

import javax.sound.sampled.AudioFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CASampledSP's {@link AudioFormat} adding a {@link #PROVIDER} property and a special constructor
 * to be called from {@link CAAudioFileFormat}.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CAAudioFormat extends AudioFormat {

    public static final String PROVIDER = "provider";
    public static final String CASAMPLEDSP = "casampledsp";

    public CAAudioFormat(final int dataFormat, final float sampleRate, final int sampleSize, final int channels,
                         final int packetSize, final float frameRate, final boolean bigEndian, final int bitRate, final boolean vbr) {
        super(CAEncoding.getInstance(dataFormat), sampleRate, sampleSize, channels, packetSize, frameRate, bigEndian, createProperties(bitRate, vbr));
    }

    private static Map<String, Object> createProperties(final int bitRate, final boolean vbr) {
        final Map<String, Object> properties = new HashMap<>();
        if (bitRate > 0) properties.put("bitrate", bitRate);
        properties.put("vbr", vbr);
        properties.put(PROVIDER, CASAMPLEDSP);
        return properties;
    }

    /**
     * Special CoreAudio encodings that are aware of their CoreAudio dataformat.
     */
    public static class CAEncoding extends Encoding {

        private static final int kAudioFormatMPEGLayer1 = toInt(".mp1");
        private static final int kAudioFormatMPEGLayer2 = toInt(".mp2");
        private static final int kAudioFormatMPEGLayer3 = toInt(".mp3");
        private static final int kAudioFormatAppleLossless = toInt("alac");
        private static final int kAudioFormatMPEG4AAC = toInt("aac ");
        private static final int kAudioFormatMPEG4AAC_HE = toInt("aach");
        private static final int kAudioFormatMPEG4AAC_HE_V2 = toInt("aacp");
        private static final int kAudioFormatMPEG4AAC_LD = toInt("aacl");
        private static final int kAudioFormatMPEG4AAC_Spatial = toInt("aacs");
        private static final int kAudioFormatMPEG4CELP = toInt("celp");
        private static final int kAudioFormatMPEG4HVXC = toInt("hvxc");
        private static final int kAudioFormatMPEG4TwinVQ = toInt("twvq");
        private static final int kAudioFormatULaw = toInt("ulaw");
        private static final int kAudioFormatALaw = toInt("alaw");
        private static final int WAVE_LITTLE_ENDIAN = toInt("sowt"); // twos spelled backwards to indicate little endian
        private static final int WAVE_BIG_ENDIAN = toInt("twos");
        private static final int kAudioFormatLinearPCM = toInt("lpcm");
        private static final int kAudioFormatAudible = toInt("AUDB");
        private static final int kAudioFormatiLBC = toInt("ilibc");
        private static final int kAudioFormatDVIIntelIMA = 0x6D730011;
        private static final int kAudioFormatMicrosoftGSM = 0x6D730031;
        private static final int kAudioFormatAES3 = toInt("aes3");
        private static final int kAudioFormatAMR = toInt("samr");
        private static final int kAudioFormatAC3 = toInt("ac-3");

        // currently not supported, because these codecs don't really seem to be in use anymore
        /*
        private static final int kAudioFormat60958AC3 = toInt("cac3");
        private static final int kAudioFormatAppleIMA4 = toInt("ima4");
        private static final int kAudioFormatMACE3 = toInt("MAC3");
        private static final int kAudioFormatMACE6 = toInt("MAC6");
        private static final int kAudioFormatQDesign = toInt("QDMC");
        private static final int kAudioFormatQDesign2 = toInt("QDM2");
        private static final int kAudioFormatQUALCOMM = toInt("Qclp");
        private static final int kAudioFormatTimeCode = toInt("time");
        private static final int kAudioFormatMIDIStream = toInt("midi");
        private static final int kAudioFormatParameterValueStream = toInt("apvs");
        */

        public static CAEncoding MP1 = new CAEncoding("MPEG-1, Layer 1", kAudioFormatMPEGLayer1);
        public static CAEncoding MP2 = new CAEncoding("MPEG-1, Layer 2", kAudioFormatMPEGLayer2);
        public static CAEncoding MP3 = new CAEncoding("MPEG-1, Layer 3", kAudioFormatMPEGLayer3);

        public static CAEncoding APPLE_LOSSLESS = new CAEncoding("Apple Lossless", kAudioFormatAppleLossless);

        public static CAEncoding MPEG4_AAC = new CAEncoding("MPEG4 AAC", kAudioFormatMPEG4AAC);
        public static CAEncoding MPEG4_AAC_HE = new CAEncoding("MPEG4 AAC High Efficiency", kAudioFormatMPEG4AAC_HE);
        public static CAEncoding MPEG4_AAC_HE_V2 = new CAEncoding("MPEG4 AAC High Efficiency Version 2", kAudioFormatMPEG4AAC_HE_V2);
        public static CAEncoding MPEG4_AAC_LD = new CAEncoding("MPEG4 AAC Low Delay", kAudioFormatMPEG4AAC_LD);
        public static CAEncoding MPEG4_AAC_SPATIAL = new CAEncoding("MPEG4 AAC Spatial", kAudioFormatMPEG4AAC_Spatial);

        public static CAEncoding MPEG4_CELP = new CAEncoding("MPEG4 CELP", kAudioFormatMPEG4CELP);
        public static CAEncoding MPEG4_HVXC = new CAEncoding("MPEG4 HVXC", kAudioFormatMPEG4HVXC);
        public static CAEncoding MPEG4_TWINVQ = new CAEncoding("MPEG4 TwinVQ", kAudioFormatMPEG4TwinVQ);

        public static CAEncoding ULAW = new CAEncoding(Encoding.ULAW.toString(), kAudioFormatULaw);
        public static CAEncoding ALAW = new CAEncoding(Encoding.ALAW.toString(), kAudioFormatALaw);

        public static CAEncoding WAVE_LE = new CAEncoding("WAVE_LE", WAVE_LITTLE_ENDIAN);
        public static CAEncoding WAVE_BE = new CAEncoding("WAVE_BE", WAVE_BIG_ENDIAN);
        public static CAEncoding PCM_SIGNED = new CAEncoding(Encoding.PCM_SIGNED.toString(), kAudioFormatLinearPCM);
        public static CAEncoding AUDIBLE = new CAEncoding("Audible", kAudioFormatAudible);
        public static CAEncoding I_LBC = new CAEncoding("iLBC", kAudioFormatiLBC);
        public static CAEncoding DVI_INTEL_IMA = new CAEncoding("DVI Intel IMA", kAudioFormatDVIIntelIMA);
        public static CAEncoding MICROSOFT_GSM = new CAEncoding("Microsoft GSM", kAudioFormatMicrosoftGSM);
        public static CAEncoding AES3 = new CAEncoding("AES3", kAudioFormatAES3);
        public static CAEncoding AMR = new CAEncoding("AMR", kAudioFormatAMR);
        public static CAEncoding AC3 = new CAEncoding("AC3", kAudioFormatAC3);

        private static final Map<Integer, CAEncoding> DATAFORMAT_MAP = new HashMap<>();
        private static final Map<String, CAEncoding> NAME_MAP = new HashMap<>();

        static {
            DATAFORMAT_MAP.put(MP1.getDataFormat(), MP1);
            DATAFORMAT_MAP.put(MP2.getDataFormat(), MP2);
            DATAFORMAT_MAP.put(MP3.getDataFormat(), MP3);

            DATAFORMAT_MAP.put(APPLE_LOSSLESS.getDataFormat(), APPLE_LOSSLESS);

            DATAFORMAT_MAP.put(MPEG4_AAC.getDataFormat(), MPEG4_AAC);
            DATAFORMAT_MAP.put(MPEG4_AAC_HE.getDataFormat(), MPEG4_AAC_HE);
            DATAFORMAT_MAP.put(MPEG4_AAC_HE_V2.getDataFormat(), MPEG4_AAC_HE_V2);
            DATAFORMAT_MAP.put(MPEG4_AAC_LD.getDataFormat(), MPEG4_AAC_LD);
            DATAFORMAT_MAP.put(MPEG4_AAC_SPATIAL.getDataFormat(), MPEG4_AAC_SPATIAL);

            DATAFORMAT_MAP.put(MPEG4_CELP.getDataFormat(), MPEG4_CELP);
            DATAFORMAT_MAP.put(MPEG4_HVXC.getDataFormat(), MPEG4_HVXC);
            DATAFORMAT_MAP.put(MPEG4_TWINVQ.getDataFormat(), MPEG4_TWINVQ);

            DATAFORMAT_MAP.put(ULAW.getDataFormat(), ULAW);
            DATAFORMAT_MAP.put(ALAW.getDataFormat(), ALAW);

            DATAFORMAT_MAP.put(WAVE_LE.getDataFormat(), WAVE_LE);
            DATAFORMAT_MAP.put(WAVE_BE.getDataFormat(), WAVE_BE);
            DATAFORMAT_MAP.put(PCM_SIGNED.getDataFormat(), PCM_SIGNED);

            DATAFORMAT_MAP.put(AUDIBLE.getDataFormat(), AUDIBLE);
            DATAFORMAT_MAP.put(I_LBC.getDataFormat(), I_LBC);
            DATAFORMAT_MAP.put(DVI_INTEL_IMA.getDataFormat(), DVI_INTEL_IMA);
            DATAFORMAT_MAP.put(MICROSOFT_GSM.getDataFormat(), MICROSOFT_GSM);
            DATAFORMAT_MAP.put(AES3.getDataFormat(), AES3);
            DATAFORMAT_MAP.put(AMR.getDataFormat(), AMR);
            DATAFORMAT_MAP.put(AC3.getDataFormat(), AC3);

            for (final CAEncoding encoding : DATAFORMAT_MAP.values()) {
                NAME_MAP.put(encoding.toString(), encoding);
            }
        }

        private final int dataFormat;

        public CAEncoding(final String name, final int dataFormat) {
            super(name);
            this.dataFormat = dataFormat;
        }

        public static Set<CAEncoding> getSupportedEncodings() {
            return new HashSet<>(DATAFORMAT_MAP.values());
        }

        public static synchronized CAEncoding getInstance(final String name) {
            return NAME_MAP.get(name);
        }

        public static synchronized CAEncoding getInstance(final int dataFormat) {
            CAEncoding encoding = DATAFORMAT_MAP.get(dataFormat);
            if (encoding == null) {
                encoding = new CAEncoding(toString(dataFormat), dataFormat);
                DATAFORMAT_MAP.put(dataFormat, encoding);
                NAME_MAP.put(encoding.toString(), encoding);
            }
            return encoding;
        }

        private static String toString(final int dataFormat) {
            return new String(
                    new char[]{(char) (dataFormat >> 24 & 0xff), (char) (dataFormat >> 16 & 0xff),
                            (char) (dataFormat >> 8 & 0xff), (char) (dataFormat & 0xff)}
            );
        }

        static int toInt(final String s) {
            return s.charAt(0) << 24 | s.charAt(1) << 16 | s.charAt(2) << 8 | s.charAt(3);
        }


        public int getDataFormat() {
            return dataFormat;
        }

    }
}
