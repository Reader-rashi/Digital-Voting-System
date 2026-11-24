package com.digitalvoting.model;

/** One stored 100x100 grayscale face crop and the voter it belongs to. */
public record FaceSample(int voterId, byte[] image) {}