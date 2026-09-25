package com.sxw.sxwaiagent.attachment;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration @EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentConfig {
 @Bean MinioClient minioClient(AttachmentProperties p) {
  MinioClient client = MinioClient.builder().endpoint(p.endpoint()).credentials(p.accessKey(),p.secretKey()).build();
  client.setTimeout(10000, 30000, 30000);
  return client;
 }
}
