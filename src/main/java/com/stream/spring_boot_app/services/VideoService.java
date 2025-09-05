package com.stream.spring_boot_app.services;

import com.stream.spring_boot_app.entities.Video;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface VideoService {

    Video save(Video video, MultipartFile file);
    Video get(String videoId);
    Video getByTitle(String title);
    List<Video> getAllVideos();
    boolean processVideo(String videoId);
    public List<Video> searchByTitle(String query);
    void deleteVideo(String id);
    void cancelUpload(String videoId);
}
