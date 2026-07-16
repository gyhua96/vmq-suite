package com.vone.vmq;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * 动态无声音频循环播放工具 (用于对抗国内系统深度冻结)
 */
public class MuteAudioHelper {
    private static final String TAG = "MuteAudioHelper";
    private static AudioTrack audioTrack;
    private static boolean isPlaying = false;
    private static Thread playThread;

    public static synchronized void start(final android.content.Context context) {
        if (isPlaying) return;
        isPlaying = true;
        Log.d(TAG, "启动无声音频保活...");
        LogStore.i(context, TAG, "启动无声音频保活...");

        playThread = new Thread(() -> {
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

            try {
                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );

                byte[] silenceBuffer = new byte[bufferSize];
                // 填充PCM静音数据 (16bit PCM静音即全为0)
                java.util.Arrays.fill(silenceBuffer, (byte) 0);

                audioTrack.play();
                while (isPlaying) {
                    audioTrack.write(silenceBuffer, 0, silenceBuffer.length);
                    // 稍作休眠，防止紧密循环耗费CPU
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "无声音频播放线程中断");
            } catch (Exception e) {
                Log.e(TAG, "无声音频保活运行异常: " + e.getMessage());
                LogStore.e(context, TAG, "无声音频保活运行异常: " + e.getMessage(), e);
            } finally {
                stopTrack();
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    public static synchronized void stop() {
        if (!isPlaying) return;
        isPlaying = false;
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
        stopTrack();
        Log.d(TAG, "停止无声音频保活");
    }

    private static void stopTrack() {
        try {
            if (audioTrack != null) {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "释放AudioTrack失败: " + e.getMessage());
        }
    }
}
