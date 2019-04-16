package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class ToneThread extends Thread {
    public interface ToneCallback {
        public void onProgress(int current, int total);

        public void onDone();
    }

    public interface ToneIterator extends Iterable<Integer> {
        public int size();
    }

    static final int sample_rate = 44100;
    static final float duration = 0.1f;
    static final int sample_size = Math.round(duration * sample_rate);//4410개의 샘플크기로

    final ToneIterator frequencies;
    final ToneCallback callback;
    boolean callback_done = false;

    public ToneThread(ToneIterator frequencies, ToneCallback callback) {
        this.frequencies = frequencies;
        this.callback = callback;
        setPriority(Thread.MAX_PRIORITY);
    }

    @Override
    public void run() {
        final AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sample_rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                2 * sample_size,
                AudioTrack.MODE_STREAM
        );

        final int total_samples = Math.round(frequencies.size() * sample_size);

       track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                if (!callback_done) {
                    callback.onDone();
                    callback_done = true;
                }
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                if (!callback_done) {
                    callback.onProgress(track.getPlaybackHeadPosition(), total_samples);
                }
            }
        });
        track.setPositionNotificationPeriod(sample_rate / 10);

        track.play();

        for (int freq : frequencies) {
            Log.d("ToneThread frequency",Integer.toString(freq));
            short[] samples = generate(freq);
            track.write(samples, 0, samples.length);
        }

        track.setNotificationMarkerPosition(sample_size);
    }

    static short[] generate(float frequency) {//각진동수를 구하기 위한
        final short sample[] = new short[sample_size];
        final double increment = 2 * Math.PI * frequency / sample_rate;//1쌤플당 몇 주파수?

        double angle = 0;
        for (int i = 0; i < sample.length; ++i) {
            sample[i] = (short) (Math.sin(angle) * Short.MAX_VALUE);
            angle += increment;
        }

        return sample;
    }
}
