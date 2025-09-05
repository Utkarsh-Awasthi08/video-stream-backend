package com.stream.spring_boot_app.repository;

import com.stream.spring_boot_app.entities.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, String> {

    Optional<Video> findByVideoId(String video_id);
    Optional<Video> findByTitle(String title);
    List<Video> findByTitleContainingIgnoreCase(String title);

}
