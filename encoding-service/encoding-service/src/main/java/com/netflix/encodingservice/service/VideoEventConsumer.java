package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final EncodingService encodingService;

    /**
     * Listens to video.uploaded Kafka topic.
     *Triggered when video service uploads a raw video to S3
     *
     * FLOW:
     * Video Service -> S3 upload -> Kafka (video.uploaded)
     *                             -> This Consumer
     *                             -> EncodingService -> FFmpeg -> S3
     *                             -> Kafka (video.encoded)
     */
    public void consumeVideoUploadedEvent(VideoUploadedEvent event){
        log.info("Consumed VideoUploadedEvent for movie: {} file: {}",
                event.getMovieId(), event.getOriginalFileName());

        try {
            encodingService.encodeVideo(event);
        } catch (Exception e) {
            log.error("Error while consuming VideoUploadedEvent for {}", event.getMovieId(), e);
        }
    }
}
