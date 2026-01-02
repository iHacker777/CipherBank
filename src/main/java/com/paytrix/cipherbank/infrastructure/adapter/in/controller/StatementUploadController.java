package com.paytrix.cipherbank.infrastructure.adapter.in.controller;

import com.paytrix.cipherbank.application.port.in.StatementUploadUseCase;
import com.paytrix.cipherbank.infrastructure.exception.FileProcessingException;
import com.paytrix.cipherbank.infrastructure.exception.InvalidFileTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Controller for bank statement file uploads
 *
 * SECURITY NOTE:
 * - This endpoint is protected by SecurityConfig which requires authentication
 * - No @PreAuthorize needed - all authenticated users can upload
 * - If you need role-based access control, see the version with @PreAuthorize
 */
@RestController
@RequestMapping("/api/statements")
@Validated
public class StatementUploadController {

    private static final Logger log = LoggerFactory.getLogger(StatementUploadController.class);

    // Allowed file extensions
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".csv", ".xls", ".xlsx", ".pdf");

    // Maximum file size (10MB) - must match application.yml setting
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB in bytes

    private final StatementUploadUseCase useCase;

    public StatementUploadController(StatementUploadUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Upload bank statement file
     *
     * AUTHENTICATION: Required (from SecurityConfig)
     * AUTHORIZATION: None (all authenticated users can upload)
     *
     * @param parserKey Bank identifier (iob, kgb, indianbank)
     * @param username User uploading the file
     * @param accountNo Optional account number override (required for IOB)
     * @param file The statement file (CSV, XLS, XLSX, or PDF)
     * @return Upload result with statistics
     */

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementUploadUseCase.UploadResult> upload(
            @RequestParam("parserKey") @NotBlank String parserKey,
            @RequestParam("username")  @NotBlank String username,
            @RequestParam(value = "accountNo", required = false) String accountNo,
            @RequestPart("file") MultipartFile file
    ) {
        log.info("=== UPLOAD REQUEST RECEIVED ===");
        log.info("Parser Key: {}", parserKey);
        log.info("Username: {}", username);
        log.info("Account No: {}", accountNo != null ? accountNo : "Not provided");
        log.info("File Name: {}", file.getOriginalFilename());
        log.info("File Size: {} bytes", file.getSize());
        log.info("Content Type: {}", file.getContentType());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                log.error("Uploaded file is empty");
                throw new FileProcessingException("File is empty. Please upload a valid statement file.");
            }

            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                log.error("File size {} exceeds maximum {}", file.getSize(), MAX_FILE_SIZE);
                throw new FileProcessingException(
                        String.format("File size (%.2f MB) exceeds maximum allowed size (10 MB)",
                                file.getSize() / 1024.0 / 1024.0)
                );
            }

            // Validate file extension
            String filename = file.getOriginalFilename();
            if (filename == null || !hasAllowedExtension(filename)) {
                log.error("Invalid file type: {}", filename);
                throw new InvalidFileTypeException(
                        filename,
                        String.join(", ", ALLOWED_EXTENSIONS)
                );
            }

            // Validate parser key
            if (!isValidParserKey(parserKey)) {
                log.error("Invalid parser key: {}", parserKey);
                throw new IllegalArgumentException(
                        "Invalid parser key. Allowed values: iob, kgb, indianbank"
                );
            }

            // Create upload command
            var cmd = new StatementUploadUseCase.UploadCommand(
                    parserKey,
                    username,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    accountNo
            );

            log.info("Processing upload...");
            var result = useCase.upload(cmd);

            log.info("=== UPLOAD SUCCESSFUL ===");
            log.info("Upload ID: {}", result.uploadId());
            log.info("Rows Parsed: {}", result.rowsParsed());
            log.info("Rows Inserted: {}", result.rowsInserted());
            log.info("Rows Deduped: {}", result.rowsDeduped());
            log.info("========================");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("Validation error during upload: {}", e.getMessage());
            throw e; // GlobalExceptionHandler will handle as 400 Bad Request

        } catch (InvalidFileTypeException e) {
            log.error("Invalid file type: {}", e.getMessage());
            throw e; // GlobalExceptionHandler will handle as 415 Unsupported Media Type

        } catch (FileProcessingException e) {
            log.error("File processing error: {}", e.getMessage());
            throw e; // GlobalExceptionHandler will handle as 422 Unprocessable Entity

        } catch (IOException e) {
            log.error("IO error reading file: {}", e.getMessage(), e);
            throw new FileProcessingException(
                    file.getOriginalFilename(),
                    "Could not read file: " + e.getMessage()
            );

        } catch (Exception e) {
            log.error("Unexpected error during upload: {}", e.getMessage(), e);
            throw new FileProcessingException(
                    file.getOriginalFilename(),
                    "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Check if filename has an allowed extension
     */
    private boolean hasAllowedExtension(String filename) {
        String lowerFilename = filename.toLowerCase();
        return ALLOWED_EXTENSIONS.stream()
                .anyMatch(lowerFilename::endsWith);
    }

    /**
     * Check if parser key is valid
     */
    private boolean isValidParserKey(String parserKey) {
        return Arrays.asList("iob", "kgb", "indianbank")
                .contains(parserKey.toLowerCase());
    }
}