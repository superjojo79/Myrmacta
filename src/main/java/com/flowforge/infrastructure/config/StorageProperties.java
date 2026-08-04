package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        Filesystem filesystem,
        S3 s3) {

    public StorageProperties {
        if (type == null || type.isBlank()) {
            type = "filesystem";
        }
        if (filesystem == null) {
            filesystem = new Filesystem("./data/documents");
        }
        if (s3 == null) {
            s3 = new S3(null, null, null, null, null);
        }
    }

    public record Filesystem(String root) {
        public Filesystem {
            if (root == null || root.isBlank()) {
                root = "./data/documents";
            }
        }
    }

    public record S3(
            String bucket, String region, String endpoint, String accessKeyId, String secretAccessKey) {}
}
