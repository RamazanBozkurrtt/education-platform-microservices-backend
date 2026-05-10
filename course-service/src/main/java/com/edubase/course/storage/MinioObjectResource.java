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
    private final long offset;
    private final Long rangeLength;

    public MinioObjectResource(MinioClient minioClient, String bucket, String objectKey, long contentLength) {
        this(minioClient, bucket, objectKey, contentLength, 0L, null);
    }

    public MinioObjectResource(MinioClient minioClient, String bucket, String objectKey, long contentLength, long offset, Long rangeLength) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.contentLength = contentLength;
        this.offset = Math.max(0L, offset);
        this.rangeLength = rangeLength;
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
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey);
            if (offset > 0L) {
                builder.offset(offset);
            }
            if (rangeLength != null && rangeLength > 0L) {
                builder.length(rangeLength);
            }
            return minioClient.getObject(builder.build());
        } catch (Exception ex) {
            throw new IOException("Failed to read MinIO object: %s/%s".formatted(bucket, objectKey), ex);
        }
    }

    @Override
    public long contentLength() {
        return contentLength;
    }
}
