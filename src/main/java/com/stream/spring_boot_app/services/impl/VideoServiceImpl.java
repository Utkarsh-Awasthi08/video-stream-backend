package com.stream.spring_boot_app.services.impl;

import com.stream.spring_boot_app.entities.Video;
import com.stream.spring_boot_app.repository.VideoRepository;
import com.stream.spring_boot_app.services.VideoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoServiceImpl implements VideoService {

    @Value("${files.video}")
    String DIR;

    @Value("${file.video.hsl}")
    String HSL_DIR;

    private final Path videoStorageLocation;
    private final VideoRepository videoRepository;
    private final Map<String, Process> ffmpegProcesses = new ConcurrentHashMap<>();

    public VideoServiceImpl(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
        videoStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        File file = new File(DIR);
        try {
            Files.createDirectories(Paths.get(HSL_DIR));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!file.exists()) {
            file.mkdir();
            System.out.println("Folder Created:");
        } else {
            System.out.println("Folder already created");
        }
    }

    @Override
    public Video save(Video video, MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            String contentType = file.getContentType();
            InputStream inputStream = file.getInputStream();

            String cleanFileName = StringUtils.cleanPath(filename);
            String cleanFolder = StringUtils.cleanPath(DIR);

            Path path = Paths.get(cleanFolder, cleanFileName);

            System.out.println(contentType);
            System.out.println(path);

            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);

            video.setContentType(contentType);
            video.setFilePath(path.toString());
            return videoRepository.save(video);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error in processing video: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteVideo(String id) {
        System.out.println("Deleting video with ID: " + id);
        Video video = videoRepository.findByVideoId(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        System.out.println("Fetched video: " + video.getVideoId() + " | " + video.getFilePath());

        videoRepository.delete(video);

        try {
            Path originalFilePath = Paths.get(video.getFilePath()).normalize().toAbsolutePath();
            if (Files.exists(originalFilePath)) {
                Files.delete(originalFilePath);
                System.out.println("Deleted original file: " + originalFilePath);
            } else {
                System.out.println("File not found: " + originalFilePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete original video file", e);
        }

        try {
            Path hlsFolder = Paths.get(HSL_DIR).resolve(id).normalize().toAbsolutePath();
            if (Files.exists(hlsFolder)) {
                FileSystemUtils.deleteRecursively(hlsFolder);
                System.out.println("Deleted HLS folder: " + hlsFolder);
            } else {
                System.out.println("HLS folder not found: " + hlsFolder);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete HLS video files", e);
        }
    }

    @Override
    public void cancelUpload(String videoId) {
        Process ffmpegProcess = ffmpegProcesses.get(videoId);
        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
            ffmpegProcesses.remove(videoId);
            System.out.println("Cancelled FFmpeg process for video ID: " + videoId);
        }

        Video video = videoRepository.findByVideoId(videoId).orElse(null);
        if (video != null) {
            try {
                Files.deleteIfExists(Paths.get(video.getFilePath()));
                Path hlsFolder = Paths.get(HSL_DIR).resolve(videoId);
                FileSystemUtils.deleteRecursively(hlsFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            videoRepository.delete(video);
        }
    }

    @Override
    public Video get(String videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
    }

    @Override
    public List<Video> searchByTitle(String query) {
        return videoRepository.findByTitleContainingIgnoreCase(query);
    }

    @Override
    public Video getByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    @Override
    public boolean processVideo(String videoId) {
        Video video = this.get(videoId);
        Path videoPath = Paths.get(video.getFilePath());
        Path outputPath = Paths.get(HSL_DIR, videoId);
        Process process = null;

        try {
            Files.createDirectories(outputPath);

            String ffmpegCmd = String.format(
                    "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 10 -hls_list_size 0 -hls_segment_filename \"%s/segment_%%3d.ts\" \"%s/master.m3u8\"",
                    videoPath, outputPath, outputPath
            );

            ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", ffmpegCmd);
            processBuilder.inheritIO();
            process = processBuilder.start();

            ffmpegProcesses.put(videoId, process);
            System.out.println(ffmpegProcesses);

            int exitCode = process.waitFor();
            System.out.println(exitCode);
            if (exitCode != 0) {
                System.err.println("FFmpeg processing failed for videoId: " + videoId);
                return false;
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Video processing was interrupted for videoId: " + videoId);
            return false;
        } finally {
            ffmpegProcesses.remove(videoId);
        }

        return true;
    }
}