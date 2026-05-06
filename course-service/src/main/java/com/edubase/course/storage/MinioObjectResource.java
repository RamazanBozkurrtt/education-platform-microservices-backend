package com.edubase.course.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

public class MinioObjectResource extends AbstractResource {

    private final MinioClient minioClient;
    private final String bucket;
    private final String objectKey;
    private final long contentLength;

    public MinioObjectResource(MinioClient minioClient, String bucket, String objectKey, long contentLength) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.contentLength = contentLength;
    }

    @Override
    public String getDescription() {
        return "MinIO object resource: %s/%s".formatted(bucket, objectKey);
    }

    @Override
    public String getFilename() {
        int lastSlash = objectKey.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == objectKey.length() - 1) {
            return objectKey;
        }
        return objectKey.substring(lastSlash + 1);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ex) {
            throw new IOException("Failed to read MinIO object: %s/%s".formatted(bucket, objectKey), ex);
        }
    }

    @Override
    public long contentLength() {
        return contentLength;
    }
}
