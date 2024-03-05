package com.test.voicerecorder.voice;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.util.Log;

import com.test.voicerecorder.io.AudioBuffer;
import com.test.voicerecorder.io.BaseThread;
import com.test.voicerecorder.io.CancellableRunnable;
import com.test.voicerecorder.legacy.UI;
import com.test.voicerecorder.model.TGAudio;
import com.test.voicerecorder.codec.Opus;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class VoicePlayer implements MediaPlayer.OnCompletionListener {

    private static final String TAG = "Audio";

    public static int[] readArgs = new int[3];

    private boolean isPaused = false;

    private MediaPlayer audioPlayer = null;
    private AudioTrack audioTrackPlayer = null;
    private int lastProgress = 0;
    private TGAudio currentAudio;
    private int playerBufferSize;
    private boolean decodingFinished = false;
    private long currentTotalPcmDuration;
    private long lastPlayPcm;
    private int ignoreFirstProgress = 0;
    private int buffersUsed;
    private final ArrayList<AudioBuffer> usedPlayerBuffers = new ArrayList<>();
    private final ArrayList<AudioBuffer> freePlayerBuffers = new ArrayList<>();

    private final Object playerSync = new Object();
    private final Object playerObjectSync = new Object();
    private final Object sync = new Object();

    private final BaseThread playerQueue;
    private final BaseThread fileDecodingQueue;

    private static VoicePlayer instance;

    public static VoicePlayer instance() {
        if (instance == null) {
            instance = new VoicePlayer();
        }
        return instance;
    }

    private VoicePlayer() {
        playerBufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (playerBufferSize <= 0) {
            playerBufferSize = 3840;
        }

        for (int a = 0; a < 3; a++) {
            freePlayerBuffers.add(new AudioBuffer(playerBufferSize));
        }

        playerQueue = new BaseThread();
        fileDecodingQueue = new BaseThread();
    }

    private void checkDecoderQueue() {
        fileDecodingQueue.post(() -> {
            if (decodingFinished) {
                checkPlayerQueue();
                return;
            }
            boolean was = false;
            while (true) {
                AudioBuffer buffer = null;
                synchronized (playerSync) {
                    if (!freePlayerBuffers.isEmpty()) {
                        buffer = freePlayerBuffers.get(0);
                        freePlayerBuffers.remove(0);
                    }
                    if (!usedPlayerBuffers.isEmpty()) {
                        was = true;
                    }
                }
                if (buffer != null) {
                    Opus.INSTANCE.readOpusFile(buffer.buffer, playerBufferSize, readArgs);
                    buffer.size = readArgs[0];
                    buffer.pcmOffset = readArgs[1];
                    buffer.finished = readArgs[2];
                    if (buffer.finished == 1) {
                        decodingFinished = true;
                    }
                    if (buffer.size != 0) {
                        buffer.buffer.rewind();
                        buffer.buffer.get(buffer.bufferBytes);
                        synchronized (playerSync) {
                            usedPlayerBuffers.add(buffer);
                        }
                    } else {
                        synchronized (playerSync) {
                            freePlayerBuffers.add(buffer);
                            break;
                        }
                    }
                    was = true;
                } else {
                    break;
                }
            }
            if (was) {
                checkPlayerQueue();
            }
        });
    }

    private void checkPlayerQueue() {
        playerQueue.post(() -> {
            synchronized (playerObjectSync) {
                if (audioTrackPlayer == null || audioTrackPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    return;
                }
            }
            AudioBuffer buffer = null;
            synchronized (playerSync) {
                if (!usedPlayerBuffers.isEmpty()) {
                    buffer = usedPlayerBuffers.get(0);
                    usedPlayerBuffers.remove(0);
                }
            }

            if (buffer != null) {
                int count = 0;
                try {
                    count = audioTrackPlayer.write(buffer.bufferBytes, 0, buffer.size);
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot write data to audio buffer", t);
                }

                buffersUsed++;

                if (count > 0) {
                    final long pcm = buffer.pcmOffset;
                    final int marker = buffer.finished == 1 ? buffer.size : -1;
                    final int finalBuffersUsed = buffersUsed;
                    UI.INSTANCE.post(() -> {
                        lastPlayPcm = pcm;
                        if (marker != -1) {
                            if (audioTrackPlayer != null) {
                                try {
                                    audioTrackPlayer.setNotificationMarkerPosition(1);
                                } catch (Throwable e) {
                                    Log.w(TAG, "setNotificationMarkerForPosition", e);
                                }
                                if (finalBuffersUsed == 1) {
                                    cleanupPlayer();
                                }
                            }
                        }
                    });
                }

                if (buffer.finished != 1) {
                    checkPlayerQueue();
                }
            }
            if (buffer == null || buffer.finished != 1) {
                checkDecoderQueue();
            }

            if (buffer != null) {
                synchronized (playerSync) {
                    freePlayerBuffers.add(buffer);
                }
            }
        });
    }

    public void cleanupPlayer() {
        // stopProximitySensor();
        if (audioPlayer != null || audioTrackPlayer != null) {
            if (audioPlayer != null) {
                try {
                    audioPlayer.stop();
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot stop audio player", t);
                }
                try {
                    audioPlayer.release();
                    audioPlayer = null;
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot release audio player", t);
                }
            } else {
                synchronized (playerObjectSync) {
                    try {
                        audioTrackPlayer.pause();
                        audioTrackPlayer.flush();
                    } catch (Throwable t) {
                        Log.e(TAG, "Cannot pause audio player", t);
                    }
                    try {
                        audioTrackPlayer.release();
                        audioTrackPlayer = null;
                    } catch (Throwable t) {
                        Log.e(TAG, "Cannot release audio player", t);
                    }
                }
            }
            stopProgressTimer();
            loopCount = 0;
            lastLoopCount = 0;
            lastProgress = 0;
            buffersUsed = 0;
            isPaused = false;
            currentAudio.setSeekProgress(0f, 0);
            currentAudio = null;
        }
    }

    private int loopCount, lastLoopCount;

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "onCompletion");
        if (mp.isLooping()) {
            loopCount++;
        } else {
            VoicePlayer.instance().cleanupPlayer();
            // playNextMessageInQueue()
        }
    }

    public boolean playAudio(TGAudio audio) {
        return playAudio(audio, 0);
    }

    public boolean playAudio(TGAudio audio, final int startTime) {
        if (audio == null || audio.getFilePath().isEmpty()) {
            return false;
        }
        if ((audioTrackPlayer != null || audioPlayer != null) && currentAudio != null && currentAudio.equals(audio)) {
            if (isPaused) {
                resumeAudio(audio);
            }
            return true;
        }
        cleanupPlayer();
        final File cacheFile = new File(audio.getFilePath());

        if (Opus.INSTANCE.isOpusFile(cacheFile.getAbsolutePath()) == 1) {
            synchronized (playerObjectSync) {
                try {
                    ignoreFirstProgress = 3;
                    final Semaphore semaphore = new Semaphore(0);
                    final Boolean[] result = new Boolean[1];
                    fileDecodingQueue.post(() -> {
                        result[0] = Opus.INSTANCE.openOpusFile(cacheFile.getAbsolutePath()) != 0;
                        semaphore.release();
                    });
                    semaphore.acquire();

                    if (!result[0]) {
                        return false;
                    }
                    currentTotalPcmDuration = Opus.INSTANCE.getTotalPcmDuration();

                    audioTrackPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playerBufferSize, AudioTrack.MODE_STREAM);
                    audioTrackPlayer.setVolume(1.0f);
                    audioTrackPlayer.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override
                        public void onMarkerReached(AudioTrack audioTrack) {
                            // Media.instance().post(() -> TdlibManager.instance().player().playNextMessageInQueue());
                            // playNextMessageInQueue()
                        }

                        @Override
                        public void onPeriodicNotification(AudioTrack audioTrack) {

                        }
                    });
                    audioTrackPlayer.play();
                    startProgressTimer();
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot open audio", t);
                    if (audioTrackPlayer != null) {
                        audioTrackPlayer.release();
                        audioTrackPlayer = null;
                        isPaused = false;
                        currentAudio = null;
                    }
                    return false;
                }
            }
        } else {
            try {
                audioPlayer = new MediaPlayer();
                audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC); // or AudioManager.STREAM_VOICE_CALL for raised mode
                audioPlayer.setDataSource(cacheFile.getAbsolutePath());
                audioPlayer.prepare();
                audioPlayer.start();
                audioPlayer.setOnCompletionListener(this);
                startProgressTimer();
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
                if (audioPlayer != null) {
                    audioPlayer.release();
                    audioPlayer = null;
                    isPaused = false;
                    currentAudio = null;
                }
                return false;
            }
        }

        isPaused = false;
        lastProgress = 0;
        lastPlayPcm = 0;
        currentAudio = audio;

        if (audioPlayer != null) {
            try {
                if (startTime > 0L) {
                    audioPlayer.seekTo(startTime);
                } else if (currentAudio.getSeekProgress() != 0f) {
                    int seekTo = (int) (audioPlayer.getDuration() * currentAudio.getSeekProgress());
                    audioPlayer.seekTo(seekTo);
                }
            } catch (Throwable t2) {
                currentAudio.setSeekProgress(0f, 0);
                Log.e(TAG, "Cannot seek audio", t2);
            }
        } else if (audioTrackPlayer != null) {
            if (currentAudio.getSeekProgress() == 1f) {
                currentAudio.setSeekProgress(0f, 0);
            }
            fileDecodingQueue.post(() -> {
                try {
                    if (startTime > 0L) {
                        lastPlayPcm = startTime;
                        Opus.INSTANCE.seekOpusFile((float) lastPlayPcm / (float) currentTotalPcmDuration);
                    } else if (currentAudio != null && currentAudio.getSeekProgress() != 0f) {
                        lastPlayPcm = (long) (currentTotalPcmDuration * currentAudio.getSeekProgress());
                        Opus.INSTANCE.seekOpusFile(currentAudio.getSeekProgress());
                    }
                } catch (Throwable t) {
                    Log.e(TAG, t.getMessage(), t);
                }
                synchronized (playerSync) {
                    freePlayerBuffers.addAll(usedPlayerBuffers);
                    usedPlayerBuffers.clear();
                }
                decodingFinished = false;
                checkPlayerQueue();
            });
        }

        return true;
    }

    public void stopAudio() {
        if (audioTrackPlayer == null && audioPlayer == null || currentAudio == null) {
            return;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.stop();
            } else {
                audioTrackPlayer.pause();
                audioTrackPlayer.flush();
            }
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.release();
                audioPlayer = null;
            } else if (audioTrackPlayer != null) {
                synchronized (playerObjectSync) {
                    audioTrackPlayer.release();
                    audioTrackPlayer = null;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
        }
        stopProgressTimer();
        if (currentAudio != null) {
            try {
                currentAudio.setSeekProgress(0f, 0);
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            }
        }
        currentAudio = null;
        isPaused = false;
    }

    public boolean pauseAudio(TGAudio audio) {
        if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.pause();
            } else {
                audioTrackPlayer.pause();
            }
            isPaused = true;
            stopProgressTimer();
        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
            isPaused = false;
            return false;
        }
        return true;
    }

    public boolean resumeAudio(TGAudio audio) {
        if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.start();
            } else {
                audioTrackPlayer.play();
                checkPlayerQueue();
            }
            isPaused = false;
            startProgressTimer();
        } catch (Throwable e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
        return true;
    }

    private void seekOpusPlayer(final float progress) {
        if (currentTotalPcmDuration * progress == currentTotalPcmDuration) {
            return;
        }
        if (!isPaused) {
            audioTrackPlayer.pause();
        }
        audioTrackPlayer.flush();
        fileDecodingQueue.post(() -> {
            Opus.INSTANCE.seekOpusFile(progress);
            synchronized (playerSync) {
                freePlayerBuffers.addAll(usedPlayerBuffers);
                usedPlayerBuffers.clear();
            }
            UI.INSTANCE.post(() -> {
                if (!isPaused) {
                    ignoreFirstProgress = 3;
                    lastPlayPcm = (long) (currentTotalPcmDuration * progress);
                    if (audioTrackPlayer != null) {
                        audioTrackPlayer.play();
                    }
                    lastProgress = (int) (currentTotalPcmDuration / 48.0f * progress);
                    checkPlayerQueue();
                }
            });
        });
    }

    public boolean seekToProgress(TGAudio audio, float progress) {
        if (audioTrackPlayer == null && audioPlayer == null || audio == null || currentAudio == null) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                int seekTo = (int) (audioPlayer.getDuration() * progress);
                audioPlayer.seekTo(seekTo);
                lastProgress = seekTo;
            } else {
                seekOpusPlayer(progress);
            }
            if (isPaused) {
                currentAudio.setSeekProgress(progress, lastProgress / 1000);
            } else {
                startProgressTimer();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Cannot seek audio player", t);
            return false;
        }

        return true;
    }

    private CancellableRunnable progressTask;

    public void startProgressTimer() {
        if (progressTask != null) {
            progressTask.cancel();
            progressTask = null;
        }
        progressTask = new CancellableRunnable() {
            @Override
            public void act() {
                synchronized (sync) {
                    if (currentAudio != null && (audioPlayer != null || audioTrackPlayer != null) && !isPaused) {
                        final int progress;
                        final float duration;

                        if (audioPlayer != null) {
                            progress = audioPlayer.getCurrentPosition();
                            duration = (float) audioPlayer.getDuration();
                        } else {
                            progress = (int) (lastPlayPcm / 48.0f);
                            duration = 0;
                        }

                        UI.INSTANCE.post(() -> {
                            if (currentAudio != null && (audioPlayer != null || audioTrackPlayer != null) && !isPaused) {
                                try {
                                    if (ignoreFirstProgress != 0) {
                                        ignoreFirstProgress--;
                                        return;
                                    }

                                    float value;
                                    if (audioPlayer != null) {
                                        value = duration == 0 ? 0 : (float) lastProgress / duration;
                                        if (loopCount != lastLoopCount) {
                                            if (progress >= lastProgress) {
                                                return;
                                            }
                                            lastLoopCount = loopCount;
                                            lastProgress = 0;
                                        }
                                        if (progress <= lastProgress) {
                                            return;
                                        }
                                    } else {
                                        value = (float) lastPlayPcm / (float) currentTotalPcmDuration;
                                        if (progress == lastProgress) {
                                            return;
                                        }
                                    }

                                    synchronized (sync) {
                                        if (isPending()) {
                                            lastProgress = progress;
                                            currentAudio.setSeekProgress(value, lastProgress / 1000);
                                        }
                                    }
                                } catch (Throwable t) {
                                    Log.e(TAG, "Cannot set progress of an audio", t);
                                }
                            }
                        });
                    }
                    if (isPending()) {
                        // Media.instance().post(progressTask, PROGRESS_DELAY);
                    }
                }
            }
        };
        synchronized (sync) {
            if (progressTask.isPending()) {
                // Media.instance().post(progressTask, PROGRESS_DELAY);
            }
        }
    }

    private static final int PROGRESS_DELAY = 40;

    public void stopProgressTimer() {
        synchronized (sync) {
            if (progressTask != null) {
                progressTask.cancel();
            }
        }
    }

}