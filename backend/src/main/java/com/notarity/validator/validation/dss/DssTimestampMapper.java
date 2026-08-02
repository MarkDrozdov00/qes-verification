package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import eu.europa.esig.dss.diagnostic.TimestampWrapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
public class DssTimestampMapper {
    public TimestampResult map(TimestampWrapper timestamp) {
        if (timestamp == null) {
            return new TimestampResult(null, TimestampType.OTHER, null, TernaryStatus.INDETERMINATE,
                null, null, List.of());
        }
        boolean valid = timestamp.isSignatureValid() && timestamp.isSignatureIntact() && timestamp.isMessageImprintDataIntact();
        var certificate = timestamp.getSigningCertificate();
        return new TimestampResult(timestamp.getId(), type(timestamp), DssCertificateMapper.instant(timestamp.getProductionTime()),
            valid ? TernaryStatus.VALID : TernaryStatus.INVALID,
            certificate == null ? null : certificate.getReadableCertificateName(),
            certificate == null ? null : fingerprint(certificate.getBinaries()), List.of());
    }

    public boolean isTrustedSignatureTimestamp(TimestampWrapper timestamp) {
        return timestamp != null && type(timestamp) == TimestampType.SIGNATURE_TIMESTAMP
            && timestamp.isSignatureValid() && timestamp.isSignatureIntact() && timestamp.isMessageImprintDataIntact();
    }

    private TimestampType type(TimestampWrapper timestamp) {
        String value = timestamp.getType() == null ? "" : timestamp.getType().name();
        if (value.contains("SIGNATURE")) return TimestampType.SIGNATURE_TIMESTAMP;
        if (value.contains("CONTENT")) return TimestampType.CONTENT_TIMESTAMP;
        if (value.contains("ARCHIVE")) return TimestampType.ARCHIVE_TIMESTAMP;
        if (value.contains("DOCUMENT")) return TimestampType.DOCUMENT_TIMESTAMP;
        return TimestampType.OTHER;
    }
    private String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { return null; }
    }
}
