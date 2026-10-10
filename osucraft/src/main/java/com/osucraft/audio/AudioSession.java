package com.osucraft.audio;

import java.nio.ShortBuffer;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

/**
 * Reproduce música y efectos usando el OpenAL que ya inicializó Minecraft.
 * Si no hay dispositivo de audio, todo falla en silencio ({@link #isOk()} devuelve false).
 */
public final class AudioSession implements AutoCloseable {
    private static final int SFX_SOURCES = 8;

    private boolean ok;
    private int musicSource;
    private int musicBuffer;
    private boolean musicStarted;
    private final int[] sfxSources = new int[SFX_SOURCES];
    private int sfxNext;
    private int hitBuffer;
    private int metroStrongBuffer;
    private int metroWeakBuffer;
    private float sfxGain = 0.6f;

    public AudioSession() {
        try {
            if (ALC10.alcGetCurrentContext() == 0L) return;
            AL10.alGetError();
            for (int i = 0; i < SFX_SOURCES; i++) {
                sfxSources[i] = AL10.alGenSources();
                prepare(sfxSources[i]);
            }
            hitBuffer = makeClick(1500, 0.045, 0.55);
            metroStrongBuffer = makeClick(1000, 0.07, 0.6);
            metroWeakBuffer = makeClick(700, 0.05, 0.4);
            ok = AL10.alGetError() == AL10.AL_NO_ERROR;
        } catch (Throwable t) {
            ok = false;
        }
    }

    public boolean isOk() {
        return ok;
    }

    private static void prepare(int src) {
        AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0f);
    }

    private static int makeClick(double freq, double seconds, double amp) {
        int rate = 44100;
        int n = (int) (rate * seconds);
        ShortBuffer buf = MemoryUtil.memAllocShort(n);
        try {
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-6.0 * i / n);
                buf.put((short) (Math.sin(2 * Math.PI * freq * i / rate) * env * amp * 32767));
            }
            buf.flip();
            int id = AL10.alGenBuffers();
            AL10.alBufferData(id, AL10.AL_FORMAT_MONO16, buf, rate);
            return id;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    public void setSfxVolume(int percent) {
        sfxGain = Math.max(0, Math.min(100, percent)) / 100f;
    }

    private void play(int buffer, float gain) {
        if (!ok) return;
        try {
            int src = sfxSources[sfxNext];
            sfxNext = (sfxNext + 1) % SFX_SOURCES;
            AL10.alSourceStop(src);
            AL10.alSourcei(src, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(src, AL10.AL_GAIN, Math.min(1f, gain * sfxGain));
            AL10.alSourcePlay(src);
        } catch (Throwable t) {
            ok = false;
        }
    }

    public void hit() {
        play(hitBuffer, 1f);
    }

    public void metronome(boolean strong) {
        play(strong ? metroStrongBuffer : metroWeakBuffer, 1f);
    }

    /** Carga la música (no empieza a sonar hasta {@link #playMusic()}). */
    public void setMusic(AudioDecoder.Pcm pcm, int volumePercent) {
        if (!ok || pcm == null) return;
        try {
            ShortBuffer buf = MemoryUtil.memAllocShort(pcm.data.length);
            try {
                buf.put(pcm.data).flip();
                musicBuffer = AL10.alGenBuffers();
                AL10.alBufferData(musicBuffer, pcm.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, buf, pcm.rate);
            } finally {
                MemoryUtil.memFree(buf);
            }
            musicSource = AL10.alGenSources();
            prepare(musicSource);
            AL10.alSourcei(musicSource, AL10.AL_BUFFER, musicBuffer);
            setMusicVolume(volumePercent);
        } catch (Throwable t) {
            musicSource = 0;
        }
    }

    /** Velocidad de reproducción (1.0 normal, 1.5 = DT). Cambia también el tono, como el nightcore. */
    public void setMusicRate(float rate) {
        if (hasMusic()) AL10.alSourcef(musicSource, AL10.AL_PITCH, Math.max(0.5f, Math.min(2.0f, rate)));
    }

    public boolean hasMusic() {
        return ok && musicSource != 0;
    }

    public void setMusicVolume(int percent) {
        if (hasMusic()) AL10.alSourcef(musicSource, AL10.AL_GAIN, Math.max(0, Math.min(100, percent)) / 100f);
    }

    public void playMusic() {
        if (!hasMusic()) return;
        AL10.alSourcePlay(musicSource);
        musicStarted = true;
    }

    public void pauseMusic() {
        if (hasMusic() && musicStarted) AL10.alSourcePause(musicSource);
    }

    public void resumeMusic() {
        if (hasMusic() && musicStarted) AL10.alSourcePlay(musicSource);
    }

    public void stopMusic() {
        if (hasMusic()) AL10.alSourceStop(musicSource);
        musicStarted = false;
    }

    /** Posición actual de la música en ms, o -1 si no está disponible. */
    public double musicPositionMs() {
        if (!hasMusic() || !musicStarted) return -1;
        try {
            return AL10.alGetSourcef(musicSource, AL11.AL_SEC_OFFSET) * 1000.0;
        } catch (Throwable t) {
            return -1;
        }
    }

    public boolean musicPlaying() {
        if (!hasMusic() || !musicStarted) return false;
        return AL10.alGetSourcei(musicSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    @Override
    public void close() {
        if (!ok && musicSource == 0) return;
        try {
            if (musicSource != 0) {
                AL10.alSourceStop(musicSource);
                AL10.alDeleteSources(musicSource);
                musicSource = 0;
            }
            if (musicBuffer != 0) {
                AL10.alDeleteBuffers(musicBuffer);
                musicBuffer = 0;
            }
            for (int i = 0; i < SFX_SOURCES; i++) {
                if (sfxSources[i] != 0) {
                    AL10.alSourceStop(sfxSources[i]);
                    AL10.alDeleteSources(sfxSources[i]);
                    sfxSources[i] = 0;
                }
            }
            if (hitBuffer != 0) AL10.alDeleteBuffers(hitBuffer);
            if (metroStrongBuffer != 0) AL10.alDeleteBuffers(metroStrongBuffer);
            if (metroWeakBuffer != 0) AL10.alDeleteBuffers(metroWeakBuffer);
            hitBuffer = metroStrongBuffer = metroWeakBuffer = 0;
        } catch (Throwable ignored) {
            // limpieza de mejor esfuerzo
        }
        ok = false;
    }
}
