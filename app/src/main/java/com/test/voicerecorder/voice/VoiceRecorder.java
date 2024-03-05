package com.test.voicerecorder.voice;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;

import com.test.voicerecorder.io.BaseThread;
import com.test.voicerecorder.io.CancellableRunnable;
import com.test.voicerecorder.legacy.UI;
import com.test.voicerecorder.audiofx.AudioEnhancer;
import com.test.voicerecorder.codec.Opus;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class VoiceRecorder implements Runnable {

    private static final String TAG = "Recorder";
    private static final String TAG_VOICE = "Voice";

    private static VoiceRecorder instance;

    public static VoiceRecorder instance() {
        if (instance == null) {
            instance = new VoiceRecorder();
        }
        return instance;
    }

    public static final long PROGRESS_FRAME_DELAY = 57L;
    public static final long START_DELAY = 150L;
    public static final long MIN_DURATION = 700L;

    private static BaseThread recordThread, encodeThread;
    private String currentGeneration;
    private String generationToRemove;
    private boolean isRecording;
    private long samplesCount;
    private final short[] recordSamples = new short[1024];

    private VoiceRecorder() {
        recordThread = new BaseThread();
        encodeThread = new BaseThread();
    }

    // Public callers

    private CancellableRunnable startRunnable;

    private void setRecording(final boolean isRecording) {
        synchronized (this) {
            this.isRecording = isRecording;
        }
    }

    public void record(final Listener listener) {
        setRecording(true);
        encodeThread.post(() -> recordThread.post(startRunnable = new CancellableRunnable() {
            @Override
            public void act() {
                startRunnable = null;
                synchronized (VoiceRecorder.this) {
                    if (!isRecording) {
                        return;
                    }
                }
                startRecording(listener);
            }
        }, START_DELAY));
    }

    public void save() {
        setRecording(false);
        if (SystemClock.elapsedRealtime() - recordStart < MIN_DURATION) {
            cancel();
            return;
        }
        stopRecording(false);
    }

    public void cancel() {
        setRecording(false);
        stopRecording(true);
    }

    // Internal

    private void dispatchError() {
        if (currentGeneration != null) {
            currentGeneration = null;
        }
        encodeThread.post(() -> cleanupRecording(true));
        UI.INSTANCE.post(() -> listener.onFail());
    }

    private long recordStart;
    private int recordTimeCount;
    private long lastDispatchTime;

    private void dispatchProgress() {
        if (!isRecording) {
            return;
        }
        long ms = System.currentTimeMillis();

        if (ms - lastDispatchTime >= PROGRESS_FRAME_DELAY) {
            lastDispatchTime = ms;
            recordThread.post(this::processProgress, PROGRESS_FRAME_DELAY);
        }
    }

    private AudioRecord recorder;
    private VoiceRecorder.Listener listener;
    private ArrayList<ByteBuffer> buffers;
    private ByteBuffer fileBuffer;
    private int bufferSize;

    @SuppressLint({"MissingPermission", "SdCardPath"})
    private void startRecording(VoiceRecorder.Listener listener) {
        this.listener = listener;

        String id = UUID.randomUUID().toString().substring(0, 4);
        currentGeneration = "/data/data/com.test.voicerecorder/cache/record-" + id + ".ogg";

        if (generationToRemove != null && new File(generationToRemove).delete()) {
            generationToRemove = null;
        }

        try {
            if (Opus.INSTANCE.startRecord(currentGeneration) == 0) {
                dispatchError();
                return;
            }

            if (bufferSize == 0) {
                bufferSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

                if (bufferSize <= 0) {
                    bufferSize = 1280;
                }
            }

            if (buffers == null) {
                buffers = new ArrayList<>(5);
                for (int i = 0; i < 5; i++) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                    buffer.order(ByteOrder.nativeOrder());
                    buffers.add(buffer);
                }
            }

            if (fileBuffer == null) {
                fileBuffer = ByteBuffer.allocateDirect(1920);
                fileBuffer.order(ByteOrder.nativeOrder());
            } else {
                fileBuffer.rewind();
            }

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 10);
        } catch (Throwable t) {
            Log.e(TAG, "Couldn't set up recorder", t);
            dispatchError();
            return;
        }

        try {
            AudioEnhancer.INSTANCE.acquireEnhancers(recorder);
            recordStart = SystemClock.elapsedRealtime();
            recordTimeCount = 0;
            removeFile = true;
            recorder.startRecording();
            initMaxAmplitude();
            dispatchRecord();
        } catch (Throwable t) {
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Throwable ignored) {
                }
            }
            Log.e(TAG, "Couldn't start recording", t);
            dispatchError();
        }
    }

    private void cleanupRecording(boolean removeFile) {
        Opus.INSTANCE.stopRecord();
        setRecording(false);
        if (currentGeneration != null) {
            if (removeFile) {
                generationToRemove = currentGeneration;
            } else if (listener != null) {
                listener.onSave(currentGeneration, recordTimeCount, getWaveform());
            }
        }
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }

    // Recording internal

    private void dispatchRecord() {
        recordThread.post(this);
    }

    @Override
    public void run() {
        if (recorder == null) {
            return;
        }

        ByteBuffer buffer;
        if (buffers.isEmpty()) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
            buffer.order(ByteOrder.nativeOrder());
        } else {
            buffer = buffers.get(0);
            buffers.remove(0);
        }
        buffer.rewind();

        int length = recorder.read(buffer, buffer.capacity());

        if (length <= 0) {
            buffers.add(buffer);
            encodeThread.post(() -> cleanupRecording(removeFile));
            return;
        }

        buffer.limit(length);
        final ByteBuffer finalBuffer = buffer;
        final boolean flush = length != buffer.capacity();

        calculateMaxAmplitude(buffer, length);

        encodeThread.post(() -> processBuffer(finalBuffer, flush));

        dispatchRecord();
        dispatchProgress();
    }

    private void processBuffer(final ByteBuffer buffer, boolean flush) {
        while (buffer.hasRemaining()) {
            int oldLimit = -1;
            if (buffer.remaining() > fileBuffer.remaining()) {
                oldLimit = buffer.limit();
                buffer.limit(fileBuffer.remaining() + buffer.position());
            }
            fileBuffer.put(buffer);
            if (fileBuffer.position() == fileBuffer.limit() || flush) {
                if (Opus.INSTANCE.writeFrame(fileBuffer, !flush ? fileBuffer.limit() : buffer.position()) != 0) {
                    fileBuffer.rewind();
                    recordTimeCount += fileBuffer.limit() / 3 / 2 / 16;
                }
            }
            if (oldLimit != -1) {
                buffer.limit(oldLimit);
            }
        }
        recordThread.post(() -> buffers.add(buffer));
    }

    private boolean removeFile;

    private void stopRecording(final boolean removeFile) {
        encodeThread.post(() -> {
            final boolean started;
            if (startRunnable != null) {
                startRunnable.cancel();
                startRunnable = null;
                started = false;
            } else {
                started = true;
            }
            recordThread.post(() -> {
                if (started) {
                    VoiceRecorder.this.removeFile = removeFile;
                    if (recorder == null) {
                        return;
                    }
                    try {
                        recorder.stop();
                        AudioEnhancer.INSTANCE.releaseEffects();
                    } catch (Throwable t) {
                        Log.e(TAG, "Cannot stop recorder", t);
                    }
                } else {
                    cleanupRecording(removeFile);
                }
            });
        });
    }

    public byte[] getWaveform() {
        return Opus.INSTANCE.getWaveform2(recordSamples, recordSamples.length);
    }

    // Progress

    private void processProgress() {
        if (recorder != null) {
            final float amplitude = getLastAmplitude();
            if (listener != null && isRecording) {
                UI.INSTANCE.post(() -> {
                    listener.onAmplitude(amplitude);
                    listener.onProgress(System.currentTimeMillis() - recordStart);
                });
            }
        }
    }

    // Amplitude

    private float lastAmplitude;

    private void initMaxAmplitude() {
        lastAmplitude = 0;
        if (samplesCount > 0) {
            Arrays.fill(recordSamples, (short) 0);
            samplesCount = 0;
        }
    }

    private void calculateMaxAmplitude(ByteBuffer buffer, int length) {
        double sum = 0;
        try {
            long newSamplesCount = samplesCount + length / 2;
            int currentPart = (int) (((double) samplesCount / (double) newSamplesCount) * recordSamples.length);
            int newPart = recordSamples.length - currentPart;
            float sampleStep;
            if (currentPart != 0) {
                sampleStep = (float) recordSamples.length / (float) currentPart;
                float currentNum = 0;
                for (int a = 0; a < currentPart; a++) {
                    recordSamples[a] = recordSamples[(int) currentNum];
                    currentNum += sampleStep;
                }
            }
            int currentNum = currentPart;
            float nextNum = 0;
            sampleStep = (float) length / 2 / (float) newPart;
            for (int i = 0; i < length / 2; i++) {
                short peak = buffer.getShort();
                if (peak > 2500) {
                    sum += peak * peak;
                }
                if (i == (int) nextNum && currentNum < recordSamples.length) {
                    recordSamples[currentNum] = peak;
                    nextNum += sampleStep;
                    currentNum++;
                }
            }
            samplesCount = newSamplesCount;
        } catch (Throwable t) {
            Log.e(TAG_VOICE, "Cannot calculate max amplitude", t);
        }
        buffer.position(0);

        lastAmplitude = (float) Math.sqrt(sum / length / 2);
    }

    private float getLastAmplitude() {
        return lastAmplitude;
    }

    public interface Listener {
        void onAmplitude(float amplitude);

        void onProgress(long duration);

        void onFail();

        void onSave(String generation, int duration, byte[] waveform);
    }
}
