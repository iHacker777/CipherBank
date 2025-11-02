package com.paytrix.cipherbank.application.port.in;

import java.io.InputStream;
import java.time.Instant;

public interface StatementUploadUseCase {

    record UploadCommand(
            String parserKey,          // e.g. "iob" or "kgb"
            String username,           // uploader (for BankStatementUpload.username)
            String originalFilename,
            String contentType,        // may be null, extension will be used as fallback
            InputStream inputStream,
            String accountNoOverride   // nullable; when provided, overrides parsed account no
    ) {}

    record UploadResult(
            long uploadId,
            String parserKey,
            String accountNo,          // null if not present and not required
            int rowsParsed,
            int rowsInserted,
            int rowsDeduped,
            Instant uploadTime
    ) {}

    UploadResult upload(UploadCommand command);
}
