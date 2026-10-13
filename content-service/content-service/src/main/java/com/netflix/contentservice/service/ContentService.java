package com.netflix.contentservice.service;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import com.netflix.contentservice.model.VideoStatus;
import com.netflix.contentservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {
    private final MovieRepository movieRepository;

    /**
     * Add a new Movie to the catalog
     * Video is not uploaded yet at this stage
     * @param request
     * @return
     */
    public MovieResponse addMovie(MovieRequest request){
        log.info("Adding new movie: {})",request.getTitle());

        Movie movie = new Movie();
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setGenre(Genre.valueOf(request.getGenre()));
        movie.setDirector(request.getDirector());
        movie.setCast(request.getCast());
        movie.setReleaseYear(request.getReleaseYear());
        movie.setRating(request.getRating());
        movie.setThumbnailUrl(request.getThumbnailUrl());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setVideoStatus(VideoStatus.PENDING);

        Movie savedMovie = movieRepository.save(movie);
        log.info("Saved movie with Id: {}",savedMovie.getId());

        return mapToResponse(savedMovie);
    }

    /**
     * Get all movies in the catalog
     * @return
     */
    public List<MovieResponse> getAllMovies(){
        return movieRepository
                .findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get movie by ID
     * @param movieId
     * @return
     */
    public MovieResponse getMovieById(String movieId){
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found " + movieId));

        return mapToResponse(movie);
    }

    /**
     * Get all movies by genre
     * @param genre
     * @return
     */
    public List<MovieResponse> getMoviesByGenre(Genre genre){
        return movieRepository.findByGenre(genre)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    /**
     * Search movie by title
     * @param title
     * @return
     */
    public List<MovieResponse> searchMovies(String title){
        return movieRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    /**
     *
     * @param movieId
     * @param videoKey
     */
    public void updateVideoKey(String movieId, String videoKey){
        log.info("Updating video key: {}",movieId);

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found " + movieId));

        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.PENDING);

        movieRepository.save(movie);
    }



    public void updateHlsUrl(String movieId, String hlsUrl){
        log.info("Updating HLS URL: {}",movieId);
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found " + movieId));

        movie.setHlsUrl(hlsUrl);
        movie.setVideoStatus(VideoStatus.READY);

        movieRepository.save(movie);

        log.info("Movie {} is now ready for streaming", movieId);
    }





    /**
     *
     * @param movie
     * @return
     */
    private MovieResponse mapToResponse(Movie movie){
        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setId(movie.getId());
        movieResponse.setTitle(movie.getTitle());
        movieResponse.setDescription(movie.getDescription());
        movieResponse.setGenre(movie.getGenre());
        movieResponse.setDirector(movie.getDirector());
        movieResponse.setCast(movie.getCast());
        movieResponse.setReleaseYear(movie.getReleaseYear());
        movieResponse.setRating(movie.getRating());
        movieResponse.setThumbnailUrl(movie.getThumbnailUrl());
        movieResponse.setDurationMinutes(movie.getDurationMinutes());
        movieResponse.setVideoKey(movie.getVideoKey());
        movieResponse.setVideoStatus(movie.getVideoStatus());
        movieResponse.setHlsUrl(movie.getHlsUrl());
        movieResponse.setCreatedAt(movie.getCreatedAt());

        return movieResponse;
    }
}
