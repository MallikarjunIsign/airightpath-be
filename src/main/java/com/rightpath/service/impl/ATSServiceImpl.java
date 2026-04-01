package com.rightpath.service.impl;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.rightpath.service.ATSService;

@Service
public class ATSServiceImpl implements ATSService {

    // Apache Tika for extracting text from uploaded documents
    private final Tika tika = new Tika();

    /**
     * Main method to process a resume and evaluate it against a job description.
     * Uses multiple scoring strategies and combines them with defined weights.
     *
     * @param resume         MultipartFile resume upload.
     * @param jobDescription Job description text.
     * @return Weighted ATS score (0–100).
     */
    @Override
    public double processFiles(MultipartFile resume, String jobDescription) throws IOException, TikaException {
        String resumeText = tika.parseToString(resume.getInputStream());

        resumeText = cleanText(resumeText);
        jobDescription = cleanText(jobDescription);

        // Score breakdown
        double tfidfScore = calculateTFIDFScore(resumeText, jobDescription);
        double skillMatch = calculateSkillMatch(resumeText, jobDescription);
        double experienceMatch = calculateExperienceMatch(resumeText, jobDescription);
        double educationMatch = calculateEducationMatch(resumeText, jobDescription);

        System.out.printf("TF-IDF Score: %.2f%%\n", tfidfScore);
        System.out.printf("Skill Match: %.2f%%\n", skillMatch);
        System.out.printf("Experience Match: %.2f%%\n", experienceMatch);
        System.out.printf("Education Match: %.2f%%\n", educationMatch);

        // Weighted score calculation
        double totalScore = 0.2 * tfidfScore + 0.4 * skillMatch + 0.2 * experienceMatch + 0.2 * educationMatch;
        System.out.printf("Total ATS Score: %.2f%%\n", totalScore);
        return totalScore;
    }

    // ----------------------------
    // Preprocessing & TF-IDF Logic
    // ----------------------------

    private String cleanText(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private double calculateTFIDFScore(String resume, String jobDesc) {
        List<String> resumeTokens = Arrays.asList(resume.split("\\s+"));
        List<String> jobTokens = Arrays.asList(jobDesc.split("\\s+"));

        List<List<String>> allDocs = Arrays.asList(resumeTokens, jobTokens);
        Map<String, Double> resumeTfidf = computeTFIDF(resumeTokens, allDocs);
        Map<String, Double> jobTfidf = computeTFIDF(jobTokens, allDocs);

        return computeCosineSimilarity(resumeTfidf, jobTfidf) * 100;
    }

    private Map<String, Double> computeTFIDF(List<String> tokens, List<List<String>> allDocuments) {
        Map<String, Double> tfidfScores = new HashMap<>();
        Map<String, Integer> termFreq = new HashMap<>();
        Map<String, Integer> docFreq = new HashMap<>();
        int totalDocs = allDocuments.size();

        for (String token : tokens) {
            termFreq.put(token, termFreq.getOrDefault(token, 0) + 1);
        }

        for (List<String> doc : allDocuments) {
            for (String token : new HashSet<>(doc)) {
                docFreq.put(token, docFreq.getOrDefault(token, 0) + 1);
            }
        }

        for (String token : tokens) {
            double tf = (double) termFreq.get(token) / tokens.size();
            double idf = Math.log((double) totalDocs / (1 + docFreq.getOrDefault(token, 0)));
            tfidfScores.put(token, tf * idf);
        }

        return tfidfScores;
    }

    private double computeCosineSimilarity(Map<String, Double> doc1, Map<String, Double> doc2) {
        double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0;

        for (String key : doc1.keySet()) {
            dotProduct += doc1.get(key) * doc2.getOrDefault(key, 0.0);
            norm1 += Math.pow(doc1.get(key), 2);
        }

        for (double value : doc2.values()) {
            norm2 += Math.pow(value, 2);
        }

        return (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }

    // ----------------------------
    // Skill Matching Logic
    // ----------------------------

    private double calculateSkillMatch(String resume, String jobDesc) {
        List<String> jobSkills = extractKeywords(jobDesc);
        List<String> resumeSkills = extractKeywords(resume);

        if (jobSkills.isEmpty()) return 0;

        long matchCount = resumeSkills.stream().filter(jobSkills::contains).count();
        return ((double) matchCount / jobSkills.size()) * 100;
    }

    private List<String> extractKeywords(String text) {
        return Arrays.asList(text.toLowerCase().split("\\W+"));
    }

    // ----------------------------
    // Experience Matching Logic
    // ----------------------------

    private int extractYearsOfExperience(String text) {
        if (text.toLowerCase().contains("fresher")) return 0;

        Pattern pattern = Pattern.compile("(\\d+)\\s*(\\+)?\\s*(years?|yrs?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        int maxYears = 0;
        while (matcher.find()) {
            try {
                int years = Integer.parseInt(matcher.group(1));
                maxYears = Math.max(maxYears, years);
            } catch (NumberFormatException e) {
                System.err.println("Invalid year format: " + matcher.group(1));
            }
        }

        return maxYears;
    }

    private double calculateExperienceMatch(String resume, String jobDesc) {
        int resumeExp = extractYearsOfExperience(resume);
        int jobExp = extractYearsOfExperience(jobDesc);

        System.out.printf("Extracted Resume Experience: %d years\n", resumeExp);
        System.out.printf("Required Job Experience: %d years\n", jobExp);

        if (resumeExp == 0 && jobExp == 0) return 100.0;
        if (resumeExp == 0 && jobExp > 0) return 0.0;
        if (resumeExp >= jobExp) return 100.0;

        return ((double) resumeExp / jobExp) * 100;
    }

    // ----------------------------
    // Education Matching Logic
    // ----------------------------

    private double calculateEducationMatch(String resume, String jobDesc) {
        List<String> resumeDegrees = extractDegrees(resume);
        List<String> jobDegrees = extractDegrees(jobDesc);
        List<Integer> resumeYears = extractPassingYears(resume);
        List<Integer> jobYears = extractPassingYears(jobDesc);

        if (jobDegrees.isEmpty() || (jobDegrees.size() == 1 && jobDegrees.get(0).equalsIgnoreCase("any degree"))) {
            return 100.0;
        }

        boolean degreeMatch = resumeDegrees.stream().anyMatch(jobDegrees::contains);
        if (!degreeMatch) return 0.0;

        if (jobYears.isEmpty()) return 100.0;

        boolean yearMatch = resumeYears.stream().anyMatch(jobYears::contains);
        return yearMatch ? 100.0 : 0.0;
    }

    private List<String> extractDegrees(String text) {
        List<String> degrees = Arrays.asList(
                "bachelor", "b.sc", "b.tech", "b.com", "bba", "bca",
                "master", "m.sc", "m.tech", "mba", "mca", "phd"
        );
        List<String> found = new ArrayList<>();
        for (String deg : degrees) {
            if (text.toLowerCase().contains(deg)) {
                found.add(deg);
            }
        }
        return found;
    }

    private List<Integer> extractPassingYears(String text) {
        List<Integer> years = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d{4})").matcher(text);

        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 1950 && year <= 2030) {
                years.add(year);
            }
        }

        // Handle ranges like "2018-2021"
        Matcher rangeMatcher = Pattern.compile("(\\d{4})\\s*-\\s*(\\d{4})").matcher(text);
        while (rangeMatcher.find()) {
            int start = Integer.parseInt(rangeMatcher.group(1));
            int end = Integer.parseInt(rangeMatcher.group(2));
            for (int y = start; y <= end; y++) {
                if (!years.contains(y)) years.add(y);
            }
        }

        return years;
    }

    // ----------------------------
    // Notification Logic
    // ----------------------------

    /**
     * Evaluates whether a candidate qualifies based on the ATS score.
     * Sends a notification if the score meets the threshold.
     */
    public void evaluateScoreAndNotify(String toEmail, String fullName, double atsScore) {
        if (atsScore >= 70) {
            sendShortlistNotification(toEmail, fullName);
            System.out.printf("✅ Candidate '%s' shortlisted. Email sent to %s\n", fullName, toEmail);
        } else {
            System.out.printf("❌ Candidate '%s' did not meet the threshold. ATS Score: %.2f%%\n", fullName, atsScore);
        }
    }

    /**
     * Simulated email service for sending shortlist notifications.
     * Replace with actual implementation.
     */
    public void sendShortlistNotification(String toEmail, String fullName) {
        // TODO: Replace with actual email service logic.
        System.out.printf("📧 Sending shortlist notification to %s for candidate %s\n", toEmail, fullName);
    }
}
