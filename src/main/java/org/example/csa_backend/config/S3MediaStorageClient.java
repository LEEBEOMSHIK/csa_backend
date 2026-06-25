package org.example.csa_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "storage", name = "mode", havingValue = "cdn")
public class S3MediaStorageClient {

    private final S3Client s3Client;
    private final String bucket;

    public S3MediaStorageClient(StorageProperties storageProperties) {
        StorageProperties.S3 config = storageProperties.getS3();
        this.bucket = config.getBucket();
        this.s3Client = buildClient(config);
    }

    S3MediaStorageClient(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    private S3Client buildClient(StorageProperties.S3 config) {
        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                        .build());

        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        return builder.build();
    }

    public void upload(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(data));
    }

    public void deleteByPrefix(String prefix) {
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);
            if (continuationToken != null) {
                listRequest.continuationToken(continuationToken);
            }

            ListObjectsV2Response listing = s3Client.listObjectsV2(listRequest.build());
            List<ObjectIdentifier> keys = listing.contents().stream()
                    .map(S3Object::key)
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();

            deleteInBatches(keys);
            continuationToken = listing.nextContinuationToken();
        } while (continuationToken != null);
    }

    private void deleteInBatches(List<ObjectIdentifier> keys) {
        if (keys.isEmpty()) {
            return;
        }

        for (int start = 0; start < keys.size(); start += 1000) {
            int end = Math.min(start + 1000, keys.size());
            List<ObjectIdentifier> batch = new ArrayList<>(keys.subList(start, end));
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).build())
                    .build());
        }
    }
}
