package com.notarity.validator.document;

import com.notarity.validator.api.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class DocumentInspectionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentInspectionService.class);
    public static final long MAX_SIZE = 25L * 1024 * 1024;

    public InspectedDocument inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_DOCUMENT", "Select a PDF document to validate.");
        if (file.getSize() > MAX_SIZE) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The document exceeds the 25 MB upload limit.");
        String type = file.getContentType();
        if (type != null && !type.equals("application/pdf") && !type.equals("application/octet-stream"))
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Only PDF documents are supported.");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF", "The file claims to be a PDF but its PDF header is invalid.");
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                String filename = sanitize(file.getOriginalFilename());
                var signatureFields = pdf.getSignatureFields();
                log.info("PDF signature structure filename={} signatureFieldCount={}", filename, signatureFields.size());
                for (var field : signatureFields) {
                    var signature = field.getSignature();
                    log.info("PDF signature field id={} subFilter={} populated={}",
                        field.getFullyQualifiedName(), signature == null ? null : signature.getSubFilter(),
                        signature != null);
                }
                return new InspectedDocument(bytes, new DocumentMetadata(filename, "application/pdf", bytes.length,
                    sha256(bytes), pdf.getNumberOfPages(), false));
            } catch (InvalidPasswordException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ENCRYPTED_PDF", "Password-protected PDFs cannot be validated in this version.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF", "The PDF is invalid or could not be read.");
        }
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) return "document.pdf";
        String name = input.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isBlank() ? "document.pdf" : name.substring(0, Math.min(180, name.length()));
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record InspectedDocument(byte[] bytes, DocumentMetadata metadata) {}
}
