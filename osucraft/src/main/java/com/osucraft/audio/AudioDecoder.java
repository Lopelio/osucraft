package com.osucraft.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Decodifica mp3, ogg y wav a PCM de 16 bits. Se puede llamar desde cualquier hilo. */
public final class AudioDecoder {
    private AudioDecoder() { }

    public static final class Pcm {
        public final short[] data;
        public final int channels;
        public final int rate;

        public Pcm(short[] data, int channels, int rate) {
            this.data = data;
            this.channels = channels;
            this.rate = rate;
        }

        public double durationMs() {
            return data.length / (double) channels / rate * 1000.0;
        }
    }

    public static Pcm decode(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3")) return mp3(file);
        if (n.endsWith(".ogg")) return ogg(file);
        if (n.endsWith(".wav")) return wav(file);
        throw new IOException("Formato de audio no soportado: " + n);
    }

    private static final class ShortList {
        short[] a = new short[1 << 20];
        int n;

        void add(short[] src, int len) {
            if (n + len > a.length) a = Arrays.copyOf(a, Math.max(a.length * 2, n + len));
            System.arraycopy(src, 0, a, n, len);
            n += len;
        }

        short[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }

    private static Pcm mp3(Path file) throws IOException {
        ShortList out = new ShortList();
        int channels = 2;
        int rate = 44100;
        boolean got = false;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            try {
                while (true) {
                    Header h = bs.readFrame();
                    if (h == null) break;
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    if (!got) {
                        channels = sb.getChannelCount();
                        rate = sb.getSampleFrequency();
                        got = true;
                    }
                    out.add(sb.getBuffer(), sb.getBufferLength());
                    bs.closeFrame();
                }
            } catch (Exception e) {
                if (!got) throw new IOException("No se pudo decodificar el mp3: " + e.getMessage(), e);
                // frame corrupto al final: se usa lo decodificado hasta ahora
            }
        }
        if (!got || out.n == 0) throw new IOException("El mp3 está vacío o dañado");
        return new Pcm(out.toArray(), channels, rate);
    }

    private static Pcm ogg(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer enc = MemoryUtil.memAlloc(bytes.length);
        enc.put(bytes).flip();
        long handle = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(enc, err, null);
            if (handle == 0) throw new IOException("No se pudo abrir el ogg (error " + err.get(0) + ")");
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            int channels = info.channels();
            int rate = info.sample_rate();
            int frames = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
            if (channels < 1 || channels > 2 || frames <= 0) throw new IOException("Ogg no soportado");
            ShortBuffer pcm = MemoryUtil.memAllocShort(frames * channels);
            try {
                int read = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                short[] data = new short[read * channels];
                pcm.rewind();
                pcm.get(data);
                return new Pcm(data, channels, rate);
            } finally {
                MemoryUtil.memFree(pcm);
            }
        } finally {
            if (handle != 0) STBVorbis.stb_vorbis_close(handle);
            MemoryUtil.memFree(enc);
        }
    }

    private static Pcm wav(Path file) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < 44 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("WAV inválido");
        int pos = 12;
        int channels = 0;
        int rate = 0;
        int bits = 0;
        int format = 0;
        while (pos + 8 <= b.limit()) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            if (id == 0x20746d66) { // "fmt "
                format = b.getShort(body) & 0xFFFF;
                channels = b.getShort(body + 2);
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14);
                if (format == 0xFFFE && size >= 26) format = b.getShort(body + 24) & 0xFFFF;
            } else if (id == 0x61746164) { // "data"
                if (format != 1 || channels < 1 || channels > 2 || (bits != 16 && bits != 8)) {
                    throw new IOException("WAV no soportado (solo PCM de 8/16 bits)");
                }
                int len = Math.min(size < 0 ? Integer.MAX_VALUE : size, b.limit() - body);
                int count = len / (bits / 8);
                short[] data = new short[count];
                for (int i = 0; i < count; i++) {
                    data[i] = bits == 16 ? b.getShort(body + i * 2) : (short) (((b.get(body + i) & 0xFF) - 128) << 8);
                }
                return new Pcm(data, channels, rate);
            }
            if (size < 0) break;
            pos = body + size + (size & 1);
        }
        throw new IOException("WAV sin datos");
    }
}
