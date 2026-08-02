package com.notarity.validator.validation.dss;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TrustInfrastructureFailure {
    private TrustInfrastructureFailure() {}

    static boolean isInfrastructureFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                || current instanceof ConnectException
                || current instanceof UnknownHostException) return true;
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            boolean trustContext = message.contains("ocsp") || message.contains("crl")
                || message.contains("revocation") || message.contains("certificate chain")
                || message.contains("trusted list") || message.contains("aia");
            boolean infrastructureCondition = className.contains("timeout") || className.contains("connect")
                || className.contains("httphost") || className.contains("data.loader")
                || message.contains("timed out") || message.contains("connection refused")
                || message.contains("unknown host") || message.contains("unavailable")
                || message.contains("unable to download") || message.contains("cannot download");
            if (trustContext && infrastructureCondition) return true;
            current = current.getCause();
        }
        return false;
    }

    static Throwable deepest(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    static String safeCauseChain(Throwable failure) {
        List<String> classes = new ArrayList<>();
        Throwable current = failure;
        while (current != null && classes.size() < 12) {
            classes.add(current.getClass().getName());
            current = current.getCause();
        }
        return String.join(" -> ", classes);
    }

    static String safeMessage(Throwable failure) {
        if (failure == null || failure.getMessage() == null) return "none";
        String value = failure.getMessage().replace('\n', ' ').replace('\r', ' ');
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    static String failureArea(Throwable failure) {
        StringBuilder value = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            value.append(' ').append(current.getClass().getName()).append(' ')
                .append(current.getMessage() == null ? "" : current.getMessage());
            current = current.getCause();
        }
        String normalized = value.toString().toLowerCase(Locale.ROOT);
        if (normalized.contains("ocsp")) return "OCSP";
        if (normalized.contains("crl")) return "CRL";
        if (normalized.contains("timestamp")) return "TIMESTAMP";
        if (normalized.contains("certificate") || normalized.contains("chain")) return "CERTIFICATE_CHAIN";
        if (normalized.contains("report")) return "REPORT_GENERATION";
        return "DOCUMENT_VALIDATION";
    }
}
