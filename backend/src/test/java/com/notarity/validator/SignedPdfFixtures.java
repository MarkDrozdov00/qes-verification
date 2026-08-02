package com.notarity.validator;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

final class SignedPdfFixtures {
    private static final KeyPair KEY_PAIR;
    private static final X509Certificate CERTIFICATE;
    static {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();
            X500Name name = new X500Name("C=AT,O=Signature Validator Tests,CN=Maria Muster,GIVENNAME=Maria,SURNAME=Muster");
            Instant now = Instant.now();
            X509v3CertificateBuilder builder = new X509v3CertificateBuilder(name, java.math.BigInteger.valueOf(424242),
                Date.from(now.minusSeconds(86400)), Date.from(now.plusSeconds(31536000)), name,
                SubjectPublicKeyInfo.getInstance(KEY_PAIR.getPublic().getEncoded()));
            X509CertificateHolder holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(KEY_PAIR.getPrivate()));
            CERTIFICATE = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static byte[] unsignedPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    static byte[] signedPdf() throws Exception { return addSignature(unsignedPdf()); }
    static byte[] multiSignedPdf() throws Exception { return addSignature(addSignature(unsignedPdf())); }

    static byte[] modifiedSignedPdf() throws Exception {
        byte[] signed = signedPdf();
        try (PDDocument document = Loader.loadPDF(signed); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.saveIncremental(out);
            return out.toByteArray();
        }
    }

    static byte[] corruptedSignaturePdf() throws Exception {
        byte[] signed = signedPdf();
        byte[] marker = "/Contents <".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int at = indexOf(signed, marker);
        if (at >= 0) {
            int start = at + marker.length;
            int end = start;
            while (end < signed.length && signed[end] != '>') end++;
            int lastData = end - 1;
            while (lastData > start && signed[lastData] == '0') lastData--;
            int content = Math.max(start, lastData - 20);
            signed[content] = signed[content] == '0' ? (byte) '1' : (byte) '0';
        }
        return signed;
    }

    static String certificateSha256() throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CERTIFICATE.getEncoded()));
    }

    private static byte[] addSignature(byte[] source) throws Exception {
        try (PDDocument document = Loader.loadPDF(source); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName("Maria Muster");
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            signature.setSignDate(calendar);
            document.addSignature(signature);
            ExternalSigningSupport signing = document.saveIncrementalForExternalSigning(out);
            signing.setSignature(cms(signing.getContent().readAllBytes()));
            return out.toByteArray();
        }
    }

    private static byte[] cms(byte[] content) throws Exception {
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
            new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
            .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(KEY_PAIR.getPrivate()), CERTIFICATE));
        generator.addCertificates(new JcaCertStore(List.of(CERTIFICATE)));
        return generator.generate(new CMSProcessableByteArray(content), false).getEncoded();
    }
    private static int indexOf(byte[] bytes, byte[] marker) {
        outer: for (int i = 0; i <= bytes.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) if (bytes[i + j] != marker[j]) continue outer;
            return i;
        }
        return -1;
    }
}
