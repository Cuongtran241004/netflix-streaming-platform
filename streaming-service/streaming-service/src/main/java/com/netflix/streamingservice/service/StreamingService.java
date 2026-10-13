package com.netflix.streamingservice.service;

import com.netflix.streamingservice.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamingService {
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; // 60 minutes

    // Redis key for caching streaming URLs
    private static final String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    /**
     * Get streaming url for a movie
     *
     * FLOWS:
     * 1. Check redis cache for existing presigned url
     * 2. If cached - return immediately
     * 3. If not cached - generate new presigned url form s3
     * 4. Cache the URL in redis
     * 5. Return streaming URL
     *
     * Why presigned URL?
     * - S3 bucket is private locker room - videos are not publicly accessible
     * - Presigned url fives temporary access(X minutes)
     * - Prevent unauthorized video downloads
     */
    public StreamingResponse getStreamingUrl(String movieId, String playlistKey) {
        log.info("Getting streaming URL for movie: {}", movieId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;

        // Check redis cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);

        if(cachedUrl != null) {
            log.info("Returning streaming URL for movie: {}", movieId);
            return new StreamingResponse(movieId, cachedUrl, "1080p, 720p, 480p, 360p", presignedUrlExpiry);
        }


        // Generate presigned url from S3
        log.info("Generating streaming URL for movie: {}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // Cache in redis for 55 minutes
        // (5 minutes less that actual expiry to avoid edge cases)
        redisTemplate.opsForValue()
                .set(cacheKey,
                    presignedUrl,
                    55,
                    TimeUnit.MINUTES);

        log.info("Returning streaming URL for movie: {}", movieId);

        return new StreamingResponse(
                movieId,
                presignedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );
    }

    /**
     * This is the KEY METHOD THAT MAKES EVERYTHING SECURE
     *
     * @param movieId
     * @param playlistPath
     * @return
     */
    public String getSignedPlaylist(String movieId, String playlistPath){
        // Get base path for this playlist
        String basePath = playlistPath.substring(0,
                playlistPath.lastIndexOf('/') + 1);

        String m3u8Content = readFromS3(playlistPath);

        // rewrite each line that is a segment or playlist references
        String signedContent = rewriteM3u8SignedUrls(
                m3u8Content, basePath);

        return signedContent;
    }


    private String rewriteM3u8SignedUrls(String m3u8Content, String basePath){
        StringBuilder rewritten = new StringBuilder();

        for(String line : m3u8Content.split("\n")){
            String trimmed =  line.trim();

            // Skip empty lines and comments
            if(trimmed.isEmpty() || trimmed.startsWith("#")){
                rewritten.append(line).append("\n");
                continue;
            }

            // This is a segment or playlist reference
            // Build full s3 key and sign it
            String fullKey = basePath + trimmed;
            String signedUrl = generatePresignedUrl(fullKey);

            rewritten.append(signedUrl).append("\n");
        }
        return rewritten.toString();
    }


    /**
     * Read file content from S3
     * @param s3Key
     * @return
     */
    private String readFromS3(String s3Key){
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);

        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    /**
     * Generate a presigned url for S3 object
     * URL expires after configured time
     * @param key
     * @return
     */
    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    /**
     * Invalidate Cache streaming URL
     * Called when video is re-encoded or updates
     */
    public void invalidateCache(String movieId) {
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplate.delete(cacheKey);

        log.info("Invalidating streaming URL for movie: {}", movieId);
    }
}
