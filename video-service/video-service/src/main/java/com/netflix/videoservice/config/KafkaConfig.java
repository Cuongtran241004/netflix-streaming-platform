package com.netflix.videoservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic videoUploadTopic(){
        return TopicBuilder.name("video.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    };

    @Bean
    public NewTopic videoEncodedTopic(){
        return TopicBuilder.name("video.encoded")
                .partitions(3)
                .replicas(1)
                .build();
    };
}
