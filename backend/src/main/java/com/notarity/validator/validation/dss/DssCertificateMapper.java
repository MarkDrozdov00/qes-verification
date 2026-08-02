package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import org.springframework.stereotype.Component;
import com.notarity.validator.trust.TrustedListStatusService;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.security.interfaces.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class DssCertificateMapper {
    private final SignerDisplayNameNormalizer names;
    private final TrustedListStatusService trustedLists;

    @Autowired
    public DssCertificateMapper(SignerDisplayNameNormalizer names, TrustedListStatusService trustedLists) {
        this.names = names;
        this.trustedLists = trustedLists;
    }

    DssCertificateMapper(SignerDisplayNameNormalizer names) {
        this(names, new TrustedListStatusService());
    }

    public CertificateResult map(CertificateWrapper certificate, Instant validationTime, Instant relevantTime) {
        if (certificate == null) return unknown();
        byte[] encoded = certificate.getBinaries();
        X509Certificate x509 = parse(encoded);
        RevocationStatus revocation = revocation(certificate);
        CertificateStatus atSigning = statusAt(certificate, relevantTime, revocation);
        ChainStatus chain = certificate.isTrustedChain() || certificate.isTrustedListReached()
            ? ChainStatus.TRUSTED
            : !trustedLists.available() ? ChainStatus.UNKNOWN : ChainStatus.INCOMPLETE;
        List<ValidationMessage> trustWarnings = chain == ChainStatus.TRUSTED ? List.of()
            : List.of(new ValidationMessage("TRUST_NOT_ESTABLISHED", MessageSeverity.WARNING,
                "Certificate trust not established",
                trustedLists.available()
                    ? "The available certificate chain could not be completed to an authoritative trust anchor."
                    : "Certificate trust could not be evaluated because authenticated Trusted List data was unavailable.",
                null, "TrustedListState=" + trustedLists.snapshot().state()));
        return new CertificateResult(
            certificate.getCertificateDN(), certificate.getCertificateIssuerDN(), certificate.getSerialNumber(),
            sha256(encoded), instant(certificate.getNotBefore()), instant(certificate.getNotAfter()),
            currentStatus(certificate, validationTime), atSigning, chain,
            revocation, certificate.isQcCompliance(), safeList(certificate.getCertificatePoliciesOids()),
            safeList(certificate.getOtherQcStatements()),
            x509 == null ? null : x509.getPublicKey().getAlgorithm(), keySize(x509), trustWarnings);
    }

    public CertificateResult map(CertificateWrapper certificate, Instant validationTime) {
        return map(certificate, validationTime, null);
    }

    public SignerIdentity signer(CertificateWrapper certificate) {
        if (certificate == null) return new SignerIdentity("Unknown signer", null, null, null, null, null, null, null,
            "Unknown", null, SignerEntityType.UNKNOWN);
        String given = clean(certificate.getGivenName()), surname = clean(certificate.getSurname());
        String common = clean(certificate.getCommonName()), organization = clean(certificate.getOrganizationName());
        SignerEntityType entityType = given != null || surname != null
            ? SignerEntityType.PERSON : organization != null ? SignerEntityType.ORGANIZATION : SignerEntityType.UNKNOWN;
        String display = join(given, surname);
        if (display == null) display = first(common, organization, clean(certificate.getCertificateDN()), "Unknown signer");
        display = entityType == SignerEntityType.PERSON ? names.person(display)
            : entityType == SignerEntityType.ORGANIZATION ? names.organization(display) : display;
        return new SignerIdentity(display, common, given, surname, organization, clean(certificate.getOrganizationalUnit()),
            clean(certificate.getCountryName()), clean(certificate.getEmail()), certificate.getCertificateDN(),
            display.toLowerCase(Locale.ROOT), entityType);
    }

    private CertificateResult unknown() {
        return new CertificateResult("Unknown", "Unknown", "Unknown", "", null, null,
            CertificateStatus.UNKNOWN, CertificateStatus.UNKNOWN, ChainStatus.UNKNOWN, RevocationStatus.NOT_CHECKED,
            null, List.of(), List.of(), null, null, List.of());
    }
    private CertificateStatus currentStatus(CertificateWrapper certificate, Instant now) {
        Instant from = instant(certificate.getNotBefore()), until = instant(certificate.getNotAfter());
        if (from != null && now.isBefore(from)) return CertificateStatus.NOT_YET_VALID;
        if (until != null && now.isAfter(until)) return CertificateStatus.EXPIRED;
        return CertificateStatus.VALID;
    }
    private CertificateStatus statusAt(CertificateWrapper certificate, Instant time, RevocationStatus revocation) {
        if (time == null) return CertificateStatus.UNKNOWN;
        if (revocation == RevocationStatus.REVOKED) return CertificateStatus.REVOKED;
        Instant from = instant(certificate.getNotBefore()), until = instant(certificate.getNotAfter());
        if (from != null && time.isBefore(from)) return CertificateStatus.NOT_YET_VALID;
        if (until != null && time.isAfter(until)) return CertificateStatus.EXPIRED;
        return revocation == RevocationStatus.GOOD ? CertificateStatus.VALID : CertificateStatus.UNKNOWN;
    }
    private RevocationStatus revocation(CertificateWrapper certificate) {
        var evidence = safeList(certificate.getCertificateRevocationData());
        if (evidence.stream().anyMatch(eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper::isRevoked)) {
            return RevocationStatus.REVOKED;
        }
        if (evidence.stream().anyMatch(eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper::isKnown)) {
            return RevocationStatus.GOOD;
        }
        return RevocationStatus.UNKNOWN;
    }
    private String sha256(byte[] bytes) {
        if (bytes == null) return "";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private X509Certificate parse(byte[] bytes) {
        if (bytes == null) return null;
        try { return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(bytes)); }
        catch (Exception ignored) { return null; }
    }
    private Integer keySize(X509Certificate certificate) {
        if (certificate == null) return null;
        var key = certificate.getPublicKey();
        if (key instanceof RSAKey rsa) return rsa.getModulus().bitLength();
        if (key instanceof DSAKey dsa && dsa.getParams() != null) return dsa.getParams().getP().bitLength();
        if (key instanceof ECKey ec && ec.getParams() != null) return ec.getParams().getCurve().getField().getFieldSize();
        return null;
    }
    static Instant instant(java.util.Date date) { return date == null ? null : date.toInstant(); }
    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String join(String a, String b) { return a == null || b == null ? null : a + " " + b; }
    private String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return null; }
}
