/*
 * Copyright 2019 LinkedIn Corporation
 * All Rights Reserved.
 *
 * Licensed under the BSD 2-Clause License (the "License").  See License in the project root for
 * license information.
 */
package com.linkedin.android.litr.codec;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.linkedin.android.litr.exception.TrackTranscoderException;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class MediaCodecDecoder implements Decoder {

    private MediaCodec mediaCodec;

    private boolean isRunning;
    private boolean isReleased;
    private MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();

    @Override
    public void init(@NonNull MediaFormat mediaFormat, @Nullable Surface surface) throws TrackTranscoderException {
        mediaCodec = null;
        isReleased = true;
        MediaCodecList mediaCodecList = null;
        String sourceMimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
                String decoderCodecName = mediaCodecList.findDecoderForFormat(mediaFormat);
                if (decoderCodecName != null) {
                    mediaCodec = MediaCodec.createByCodecName(decoderCodecName);
                }
            } else {
                mediaCodec = MediaCodec.createDecoderByType(sourceMimeType);
            }

            if (mediaCodec != null) {
                mediaCodec.configure(mediaFormat, surface, null, 0);
                isReleased = false;
            } else {
                throw new TrackTranscoderException(TrackTranscoderException.Error.DECODER_NOT_FOUND, mediaFormat, mediaCodec, mediaCodecList);
            }
        } catch (IOException e) {
            throw new TrackTranscoderException(TrackTranscoderException.Error.DECODER_FORMAT_NOT_FOUND, mediaFormat, mediaCodec, mediaCodecList, e);
        } catch (IllegalStateException e) {
            if (mediaCodec != null) {
                mediaCodec.release();
                isReleased = true;
            }
            mediaCodecList = null;
            throw new TrackTranscoderException(TrackTranscoderException.Error.DECODER_CONFIGURATION_ERROR, mediaFormat, mediaCodec, mediaCodecList, e);
        }
    }

    @Override
    public void start() throws TrackTranscoderException {
        if (mediaCodec == null) {
            throw new IllegalStateException("Codec is not initialized");
        }

        if (!isRunning) {
            try {
                startDecoder();
            } catch (Exception codecException) {
                throw new TrackTranscoderException(TrackTranscoderException.Error.INTERNAL_CODEC_ERROR, codecException);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int dequeueInputFrame(long timeout) {
        return mediaCodec.dequeueInputBuffer(timeout);
    }

    @Override
    @Nullable
    public Frame getInputFrame(@IntRange(from = 0) int tag) {
        if (tag >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = mediaCodec.getInputBuffer(tag);
            } else {
                ByteBuffer[] decoderInputBuffers = mediaCodec.getInputBuffers();
                inputBuffer = decoderInputBuffers[tag];
            }

            return new Frame(tag, inputBuffer, null);
        }

        return null;
    }

    public void queueInputFrame(@NonNull Frame frame) {
        mediaCodec.queueInputBuffer(frame.tag,
                                    frame.bufferInfo.offset,
                                    frame.bufferInfo.size,
                                    frame.bufferInfo.presentationTimeUs,
                                    frame.bufferInfo.flags);
    }

    @Override
    public int dequeueOutputFrame(long timeout) {
        return mediaCodec.dequeueOutputBuffer(outputBufferInfo, 0);
    }

    @Override
    @Nullable
    public Frame getOutputFrame(@IntRange(from = 0) int tag) {
        if (tag >= 0) {
            ByteBuffer buffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                buffer = mediaCodec.getOutputBuffer(tag);
            } else {
                ByteBuffer[] encoderOutputBuffers = mediaCodec.getOutputBuffers();
                buffer = encoderOutputBuffers[tag];
            }
            return new Frame(tag, buffer, outputBufferInfo);
        }

        return null;
    }

    @Override
    public void releaseOutputFrame(@IntRange(from = 0) int tag, boolean render) {
        mediaCodec.releaseOutputBuffer(tag, render);
    }

    @Override
    @NonNull
    public MediaFormat getOutputFormat() {
        return mediaCodec.getOutputFormat();
    }

    @Override
    public void stop() {
        if (isRunning) {
            mediaCodec.stop();
            isRunning = false;
        }
    }

    @Override
    public void release() {
        if (!isReleased) {
            mediaCodec.release();
            isReleased = true;
        }
    }

    @Override
    @NonNull
    public String getName() throws TrackTranscoderException {
        try {
            return mediaCodec.getName();
        } catch (IllegalStateException e) {
            throw new TrackTranscoderException(TrackTranscoderException.Error.CODEC_IN_RELEASED_STATE, e);
        }
    }

    private void startDecoder() {
        mediaCodec.start();
        isRunning = true;
    }
}
