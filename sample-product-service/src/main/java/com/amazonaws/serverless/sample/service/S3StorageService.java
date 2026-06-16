// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for S3 operations such as uploading/downloading product images.
 * Demonstrates S3 client integration with Dagger DI.
 */
@Singleton
public class S3StorageService {

    private final String bucketName;
    private final S3Client s3Client;

    @Inject
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dagger-managed singleton injected via constructor DI")
    public S3StorageService(S3Client s3Client,
                            @Named("productImagesBucket") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String uploadFile(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        return key;
    }

    public byte[] downloadFile(String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build()).asByteArray();
    }

    public void deleteFile(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build());
    }

    public List<String> listFiles(String prefix) {
        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .build());
        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
}
