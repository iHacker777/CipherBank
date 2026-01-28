package com.paytrix.cipherbank.infrastructure.adapter.in.controller;

import com.paytrix.cipherbank.application.port.in.StatementUploadUseCase;
import com.paytrix.cipherbank.infrastructure.config.FileUploadConfigProperties;
import com.paytrix.cipherbank.infrastructure.config.parser.ParserConfigLoader;
import com.paytrix.cipherbank.infrastructure.exception.FileProcessingException;
import com.paytrix.cipherbank.infrastructure.exception.InvalidFileTypeException;
import com.paytrix.cipherbank.infrastructure.exception.InvalidParserKeyException;
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

/**
 * Controller for bank statement file uploads
 *
 * SECURITY NOTE:
 * - This endpoint is protected by SecurityConfig which requires authentication
 * - Only ROLE_ADMIN and ROLE_USER can upload files
 *
 * CONFIGURATION:
 * - File size limits: Loaded from application.yml
 * (file.upload.max-file-size-mb)
 * - Allowed file types: Determined by parser-config.yml (parser-specific
 * formats)
 * - Valid parser keys: Loaded from parser-config.yml (banks section keys)
 */
@RestController
@RequestMapping("/api/statements")
@Validated
public class StatementUploadController {

    private static final Logger log = LoggerFactory.getLogger(StatementUploadController.class);

    private final StatementUploadUseCase useCase;
    private final FileUploadConfigProperties fileUploadConfig;
    private final ParserConfigLoader parserConfigLoader;

    public StatementUploadController(
            StatementUploadUseCase useCase,
            FileUploadConfigProperties fileUploadConfig,
            ParserConfigLoader parserConfigLoader) {
        this.useCase = useCase;
        this.fileUploadConfig = fileUploadConfig;
        this.parserConfigLoader = parserConfigLoader;

        // Log configuration on startup
        log.info("StatementUploadController initialized");
        log.info("  - Max file size: {}", fileUploadConfig.getMaxFileSizeDisplay());
        log.info("  - Valid parser keys: {}", parserConfigLoader.getValidParserKeysDisplay());
    }

    /**
     * Upload bank statement file
     *
     * AUTHENTICATION: Required (from SecurityConfig)
     * AUTHORIZATION: ROLE_ADMIN or ROLE_USER
     *
     * @param parserKey Bank identifier (loaded from parser-config.yml)
     * @param username  User uploading the file
     * @param accountNo Optional account number override (required for IOB)
     * @param file      The statement file (CSV, XLS, XLSX, or PDF)
     * @return Upload result with statistics
     */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementUploadUseCase.UploadResult> upload(
            @RequestParam("parserKey") @NotBlank String parserKey,
            @RequestParam("username") @NotBlank String username,
            @RequestParam(value = "accountNo", required = false) String accountNo,
            @RequestPart("file") MultipartFile file
    ) {

        log.info("=== UPLOAD REQUEST RECEIVED ===");
        log.info("Parser Key: {}", parserKey);
        log.info("Username: {}", username);
        log.info("Account No: {}", accountNo != null ? accountNo : "Not provided");
        log.info("File Name: {}", file.getOriginalFilename());
        log.info("File Size: {} bytes ({} MB)",
                file.getSize(),
                String.format("%.2f", file.getSize() / 1024.0 / 1024.0));
        log.info("Content Type: {}", file.getContentType());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                log.error("Uploaded file is empty");
                throw new FileProcessingException("File is empty. Please upload a valid statement file.");
            }

            // Validate file size using configured max size
            long maxFileSizeBytes = fileUploadConfig.getMaxFileSizeBytes();
            if (file.getSize() > maxFileSizeBytes) {
                double fileSizeMb = file.getSize() / 1024.0 / 1024.0;
                log.error("File size {} bytes ({} MB) exceeds maximum {} bytes ({})",
                        file.getSize(),
                        String.format("%.2f", fileSizeMb),
                        maxFileSizeBytes,
                        fileUploadConfig.getMaxFileSizeDisplay());

                throw new FileProcessingException(
                        String.format(
                                "File size (%.2f MB) exceeds maximum allowed size (%s)",
                                fileSizeMb,
                                fileUploadConfig.getMaxFileSizeDisplay()));
            }

            // Validate parser key (includes both existence and enabled check)
            if (!isValidParserKey(parserKey)) {
                log.error("Invalid parser key: {} - Valid keys are: {}",
                        parserKey,
                        parserConfigLoader.getValidParserKeysDisplay());
                throw new InvalidParserKeyException(
                        parserKey,
                        String.format(
                                "Invalid parser key: '%s'. Allowed values: %s",
                                parserKey,
                                parserConfigLoader.getValidParserKeysDisplay()));
            }

            // Validate file extension using parser-specific supported extensions
            String filename = file.getOriginalFilename();
            if (filename == null || !hasAllowedExtension(filename, parserKey)) {
                log.error("Invalid file type '{}' for parser '{}' - Supported extensions: {}",
                        filename,
                        parserKey,
                        parserConfigLoader.getSupportedExtensionsDisplay(parserKey));
                throw new InvalidFileTypeException(
                        filename,
                        parserConfigLoader.getSupportedExtensionsDisplay(parserKey));
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
     * Check if filename has an allowed extension for the specific parser
     * Uses parser-specific supported extensions from parser-config.yml
     *
     * @param filename  The filename to check
     * @param parserKey The parser key to check extensions for
     * @return true if extension is supported by the parser, false otherwise
     */
    private boolean hasAllowedExtension(String filename, String parserKey) {
        String lowerFilename = filename.toLowerCase();
        return parserConfigLoader.getSupportedExtensions(parserKey).stream()
                .anyMatch(lowerFilename::endsWith);
    }

    /**
     * Check if parser key is valid
     * Uses parser keys from parser-config.yml
     *
     * @param parserKey The parser key to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidParserKey(String parserKey) {
        return parserConfigLoader.isValidParserKey(parserKey);
    }
}