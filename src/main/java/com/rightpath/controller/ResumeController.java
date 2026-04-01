package com.rightpath.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.entity.Resume;
import com.rightpath.service.ResumeService;

@RestController
@RequestMapping("/api")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    // ===================================================================== //
    // Endpoint: Upload a resume for a user with jobPrefix                  //
    // ===================================================================== //
    /**
     * Uploads a resume for the specified user with a jobPrefix.
     *
     * @param file      The resume file to be uploaded.
     * @param email     The email of the user.
     * @param jobPrefix The job prefix associated with this resume.
     * @return ResponseEntity with success or error message.
     */
    @PostMapping("/upload-resume")
    @PreAuthorize("hasAuthority('RESUME_UPLOAD')")
    public ResponseEntity<String> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("email") String email,
            @RequestParam("jobPrefix") String jobPrefix) {
        try {
            resumeService.saveResume(file, email, jobPrefix);
            return ResponseEntity.ok("Resume uploaded successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload resume.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===================================================================== //
    // Endpoint: Update existing resume                                     //
    // ===================================================================== //
    /**
     * Updates the existing resume of the user.
     *
     * @param file  The new resume file.
     * @param email The email of the user.
     * @return ResponseEntity with success or error message.
     */
    @PutMapping("/update-resume")
    @PreAuthorize("hasAuthority('RESUME_UPDATE')")
    public ResponseEntity<Map<String, String>> updateResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("email") String email) {
        Map<String, String> response = new HashMap<>();
        try {
            resumeService.updateResume(file, email);
            response.put("message", "Resume updated successfully.");
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("error", "Failed to update resume.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ===================================================================== //
    // Endpoint: View resume by user email                                  //
    // ===================================================================== //
    /**
     * Retrieves and downloads the resume of a user by their email.
     *
     * @param email The email of the user.
     * @return ResponseEntity containing the resume file.
     */
    @GetMapping("/view-resume/{email}")
    @PreAuthorize("hasAuthority('RESUME_VIEW')")
    public ResponseEntity<byte[]> viewResume(@PathVariable String email) {
        Resume resume = resumeService.getResumeByEmail(email);
        if (resume == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resume.getFileName() + "\"")
                .body(resume.getData());
    }

    // ===================================================================== //
    // Endpoint: View all resumes                                           //
    // ===================================================================== //
    /**
     * Retrieves all resumes with associated user information.
     *
     * @return List of resumes.
     */
    @GetMapping("/view-all-resumes")
    @PreAuthorize("hasAuthority('RESUME_VIEW_ALL')")
    public List<Resume> getAllResumesWithUsers() {
        return resumeService.getAllResumesWithUsers();
    }

   
}
