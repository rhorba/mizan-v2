package ma.mizan.api.contracts;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Upload limits (FR-2), bound from {@code mizan.upload.*}.
 *
 * @param maxBytes largest accepted file (the servlet multipart limit is set to the same value)
 * @param maxPages most pages accepted
 * @param minCharsPerPage below this average of non-blank characters the PDF counts as scanned
 * @param pdfMainMemoryBytes heap PDFBox may use while parsing; the rest spills to temp files
 * @param uploadsPerMinute per-user upload rate limit (429 above it)
 */
@Validated
@ConfigurationProperties("mizan.upload")
record UploadProperties(
    @DefaultValue("10485760") @Positive long maxBytes,
    @DefaultValue("30") @Positive int maxPages,
    @DefaultValue("20") @Positive int minCharsPerPage,
    @DefaultValue("16777216") @Positive long pdfMainMemoryBytes,
    @DefaultValue("10") @Positive int uploadsPerMinute) {}
