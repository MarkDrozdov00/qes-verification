package com.notarity.validator.document;

public record DocumentMetadata(String filename, String mediaType, long sizeBytes, String sha256,
                               int pageCount, boolean encrypted) {}
