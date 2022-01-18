/*
 * Copyright (C) 2016 Naman Dwivedi
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package com.naman14.androidlame;

public class LameBuilder {


    public enum Mode {
        STEREO, JSTEREO, MONO, DEFAULT
        //DUAL_CHANNEL not supported
    }

    public enum VbrMode {
        VBR_OFF, VBR_RH, VBR_MTRH, VBR_ABR, VBR_DEFAUT
    }


    public int inSampleRate;
    public int outSampleRate;
    public int outBitrate;
    public int outChannel;
    public int quality;
    public int vbrQuality;
    public int abrMeanBitrate;
    public int lowpassFreq;
    public int highpassFreq;
    public float scaleInput;
    public Mode mode;
    public VbrMode vbrMode;

    public String id3tagTitle;
    public String id3tagArtist;
    public String id3tagAlbum;
    public String id3tagComment;
    public String id3tagYear;

    public LameBuilder() {

        id3tagTitle = null;
        id3tagAlbum = null;
        id3tagArtist = null;
        id3tagComment = null;
        id3tagYear = null;

        inSampleRate = 44100;

        //default 0, Lame picks best according to compression
        outSampleRate = 0;

        outChannel = 2;
        outBitrate = 128;
        scaleInput = 1;

        quality = 5;
        mode = Mode.DEFAULT;
        vbrMode = VbrMode.VBR_OFF;
        vbrQuality = 5;
        abrMeanBitrate = 128;

        //default =0, Lame chooses
        lowpassFreq = 0;
        highpassFreq = 0;
    }

    public LameBuilder setQuality(int quality) {
        this.quality = quality;
        return this;
    }

    public LameBuilder setInSampleRate(int inSampleRate) {
        this.inSampleRate = inSampleRate;
        return this;
    }

    public LameBuilder setOutSampleRate(int outSampleRate) {
        this.outSampleRate = outSampleRate;
        return this;
    }

    public LameBuilder setOutBitrate(int bitrate) {
        outBitrate = bitrate;
        return this;
    }

    public LameBuilder setOutChannels(int channels) {
        outChannel = channels;
        return this;
    }

    public LameBuilder setId3tagTitle(String title) {
        id3tagTitle = title;
        return this;
    }

    public LameBuilder setId3tagArtist(String artist) {
        id3tagArtist = artist;
        return this;
    }

    public LameBuilder setId3tagAlbum(String album) {
        id3tagAlbum = album;
        return this;
    }

    public LameBuilder setId3tagComment(String comment) {
        id3tagComment = comment;
        return this;
    }

    public LameBuilder setId3tagYear(String year) {
        id3tagYear = year;
        return this;
    }

    public LameBuilder setScaleInput(float scaleAmount) {
        scaleInput = scaleAmount;
        return this;
    }

    public LameBuilder setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public LameBuilder setVbrMode(VbrMode mode) {
        vbrMode = mode;
        return this;
    }

    public LameBuilder setVbrQuality(int quality) {
        vbrQuality = quality;
        return this;
    }

    public LameBuilder setAbrMeanBitrate(int bitrate) {
        abrMeanBitrate = bitrate;
        return this;
    }

    public LameBuilder setLowpassFreqency(int freq) {
        lowpassFreq = freq;
        return this;
    }

    public LameBuilder setHighpassFreqency(int freq) {
        highpassFreq = freq;
        return this;
    }

    public AndroidLame build() {
        return new AndroidLame(this);
    }

}
