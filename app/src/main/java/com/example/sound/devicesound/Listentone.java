package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.List;

import calsualcoding.reedsolomon.EncoderDecoder;
import google.zxing.common.reedsolomon.ReedSolomonException;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);
    private EncoderDecoder encoderDecoder;

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone() {
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
        encoderDecoder = new EncoderDecoder();
    }

    private double findFrequency(short[] toTransform) {
        int blockSize = findPowerSize(toTransform.length);
        double[] real = new double[blockSize];
        double[] img = new double[blockSize];
        double realNum;
        double imgNum;
        double[] mag = new double[blockSize];
        double[] freqs = new double[blockSize];
        int max = 0;

        for(int i = 0; i < toTransform.length; i++) {
            if(i<blockSize) freqs[i] = (double) toTransform[i];
            else freqs[i] = 0;
        }

        Complex[] complex = transform.transform(freqs, TransformType.FORWARD);
        double[] freq = this.fftfreq(complex.length, 1);

        for(int i = 0; i < complex.length; i++) {
            realNum = complex[i].getReal();
            imgNum = complex[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));

            if (mag[i] > mag[max])
                max = i;
        }

        return Math.abs(freq[max] * mSampleRate);
    }

    private static double[] fftfreq(int length, int spacing) {
        int i;
        double dLength = length;
        double[] result = new double[length];

        for(i = 0; i < length/2; i++) {
            result[i] = i / dLength;
            result[length-1-i] = -(i+1) / dLength;
        }

        if ( length % 2 != 0 ) {
            result[i] = i/dLength;
        }

        return result;
    }

    public void PreRequest() {
        boolean inPacket = false;
        int dominant;

        int blocksize = Math.round((interval / 2) * mSampleRate);
        short[] buffer = new short[blocksize];
        List<Integer> packet = new ArrayList<>();
        List<Integer> byteStream;

        while(true) {
            int BufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            if(BufferedReadResult < 0) continue;

            dominant = (int)findFrequency(buffer);
            if(inPacket && match(dominant, HANDSHAKE_END_HZ)) {
                byteStream = extractPacket(packet);
                byte[] byteStreamList = new byte[byteStream.size()];

                for(int i = 0; i < byteStream.size(); i++) {
                    byteStreamList[i] = byteStream.get(i).byteValue();
                }

                try {
                    byteStreamList = encoderDecoder.decodeData(byteStreamList, FEC_BYTES);
                } catch (ReedSolomonException | EncoderDecoder.DataTooLargeException e) {

                }

                Log.d("result string : ", printString(byteStreamList));

                packet.clear();
                inPacket = false;
            } else if(inPacket) {
                packet.add(dominant);
            } else if(match(dominant, HANDSHAKE_START_HZ)) {
                inPacket = true;
            }
        }
    }

    private String printString(byte[] byteStreamList){
        String string = "";
        for(int i = 0; i < byteStreamList.length; i++) {
           string += (char)byteStreamList[i];
        }
        return string;
    }

    private List<Integer> extractPacket(List<Integer> packet) {
        List<Integer> bitChunks = new ArrayList<>();
        int bit;
        if (match(packet.get(0), HANDSHAKE_START_HZ))
            packet.remove(0);
        if (match(packet.get(1), HANDSHAKE_START_HZ))
            packet.remove(1);

        for(int i = 0; i < packet.size(); i += 2) {
            bit = (byte) Math.round((packet.get(i) - START_HZ) / (double)STEP_HZ);
            if(bit < Math.pow(2, BITS) && bit >= 0) {
                bitChunks.add(bit);
            }
        }

        return decodeBitchunks(BITS, bitChunks);
    }

    private List<Integer> decodeBitchunks(int chunkBits, List<Integer> chunk) {
        List<Integer> outByte = new ArrayList<>();

        int b = 0;
        int nextReadChunk = 0;
        int nextReadBit = 0;
        int bitsLeft = 8;

        while(nextReadChunk < chunk.size()) {
            int canFill = chunkBits - nextReadBit;
            int toFill = Math.min(bitsLeft, canFill);
            int offset = chunkBits - nextReadBit - toFill;

            b <<= toFill;
            int shifted = chunk.get(nextReadChunk) & (((1 << toFill) - 1) << offset);
            b |= shifted >> offset;
            bitsLeft -= toFill;
            nextReadBit += toFill;

            if (bitsLeft <= 0) {
                outByte.add(b);
                b = 0;
                bitsLeft = 8;
            }

            if(nextReadBit >= chunkBits) {
                nextReadChunk += 1;
                nextReadBit -= chunkBits;
            }

        }
        return outByte;
    }

    private boolean match(int freq1, int freq2) {
        return Math.abs(freq1 - freq2) < 20;
    }

    private int findPowerSize(int numOfSample) {
        return (int)Math.pow(2, Math.ceil(Math.log(numOfSample)/Math.log(2)));
    }
}
