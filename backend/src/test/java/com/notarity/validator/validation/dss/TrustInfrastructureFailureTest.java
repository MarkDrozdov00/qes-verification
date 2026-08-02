package com.notarity.validator.validation.dss;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TrustInfrastructureFailureTest {
    @Test void ocspTimeoutIsInfrastructureFailure() {
        var failure = new IllegalStateException("OCSP request failed", new SocketTimeoutException("timed out"));
        assertThat(TrustInfrastructureFailure.isInfrastructureFailure(failure)).isTrue();
    }

    @Test void crlEndpointFailureIsInfrastructureFailure() {
        var failure = new IllegalStateException("CRL endpoint unavailable", new ConnectException("refused"));
        assertThat(TrustInfrastructureFailure.isInfrastructureFailure(failure)).isTrue();
    }

    @Test void documentParserFailureIsNotMisclassifiedAsTrustInfrastructure() {
        var failure = new IllegalArgumentException("Malformed PDF signature container");
        assertThat(TrustInfrastructureFailure.isInfrastructureFailure(failure)).isFalse();
    }
}
