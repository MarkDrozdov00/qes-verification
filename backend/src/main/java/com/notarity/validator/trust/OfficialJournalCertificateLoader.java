package com.notarity.validator.trust;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.client.http.DataLoader;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import eu.europa.esig.dss.model.DSSDocument;

final class OfficialJournalCertificateLoader {
    private static final Pattern PEM = Pattern.compile(
        "-----BEGIN CERTIFICATE-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END CERTIFICATE-----");
    private static final Set<String> APPROVED_SHA256 = Set.of(
        "c0641c4f7d56c431b1c924742db7fce9c1eef7d7fd212113a2768486b3abcdc5",
        "e0a620fbb6747362bb933ac44169d676a553444716cf5f31605f12a22b8396b1",
        "df7e29360c34b2b8d6d5f40325c1d4d12c9922cecd33b7407674a74b2b3ca1e5",
        "b63d416744e7098bf9ec2caa596a93bc2468e37f8284ba65ecc061711bcbaa18",
        "236103f03a8031ae8f47f9059bf8de38564cdbfebedde4a597d50f8980aa653b",
        "d2064fdd70f6982dcc516b86d9d5c56aea939417c624b2e478c0b29de54f8474");

    CommonCertificateSource load(String url, DataLoader loader) {
        return load(loader.get(url));
    }

    CommonCertificateSource load(DSSDocument document) {
        if (document == null) throw new IllegalStateException("No cached official-journal certificate notice is available");
        try (var input = document.openStream()) {
            return load(input.readAllBytes());
        } catch (Exception exception) {
            throw new IllegalStateException("The cached official-journal certificate notice is unreadable", exception);
        }
    }

    CommonCertificateSource load(byte[] response) {
        String html = new String(response, StandardCharsets.UTF_8)
            .replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&#43;", "+");
        CommonCertificateSource source = new CommonCertificateSource();
        Matcher matcher = PEM.matcher(html);
        while (matcher.find()) {
            try {
                String body = matcher.group(1).replaceAll("\\s+", "");
                byte[] der = java.util.Base64.getDecoder().decode(body);
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
                if (!APPROVED_SHA256.contains(fingerprint)) continue;
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
                source.addCertificate(new CertificateToken(certificate));
            } catch (Exception exception) {
                throw new IllegalStateException("The official-journal certificate set is invalid", exception);
            }
        }
        if (source.getNumberOfCertificates() != APPROVED_SHA256.size()) {
            throw new IllegalStateException("The authenticated official-journal certificate set is incomplete");
        }
        return source;
    }
}
