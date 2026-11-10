package com.mixtape.infrastructure.audio;

import com.mixtape.application.port.TempoEstimator;
import com.mixtape.application.port.TrackCatalog;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

@Component
public class PreviewBpm implements TempoEstimator {
    private static final int MAX_SAMPLES = 48_000 * 35;

    private final TrackCatalog catalog;

    public PreviewBpm(TrackCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<OptionalDouble> estimate(long trackId) {
        Optional<byte[]> audio = catalog.previewBytes(trackId);
        if (audio.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(estimateMp3(audio.get()));
        } catch (BitstreamException | DecoderException | RuntimeException e) {
            return Optional.of(OptionalDouble.empty());
        }
    }

    static OptionalDouble estimateMp3(byte[] mp3) throws BitstreamException, DecoderException {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        short[] pcm = new short[MAX_SAMPLES];
        int length = 0;
        int sampleRate = 0;
        try {
            Header header;
            while ((header = bitstream.readFrame()) != null && length < pcm.length) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                sampleRate = buffer.getSampleFrequency();
                length = appendMono(buffer, pcm, length);
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
        return BpmEstimator.estimate(Arrays.copyOf(pcm, length), sampleRate);
    }

    private static int appendMono(SampleBuffer buffer, short[] target, int start) {
        int channels = buffer.getChannelCount();
        short[] samples = buffer.getBuffer();
        int length = start;
        for (int i = 0; i + channels <= buffer.getBufferLength() && length < target.length; i += channels) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += samples[i + channel];
            }
            target[length++] = (short) (sum / channels);
        }
        return length;
    }
}
