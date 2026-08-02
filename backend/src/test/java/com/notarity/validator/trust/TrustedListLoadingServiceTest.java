package com.notarity.validator.trust;

import eu.europa.esig.dss.model.tsl.LOTLInfo;
import eu.europa.esig.dss.model.tsl.TLInfo;
import eu.europa.esig.dss.model.tsl.TLValidationJobSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedListLoadingServiceTest {

    @Test
    void includesNationalListsNestedUnderLotlAndOtherDirectLists() {
        TLValidationJobSummary summary = mock(TLValidationJobSummary.class);
        LOTLInfo lotl = mock(LOTLInfo.class);
        TLInfo austria = mock(TLInfo.class);
        TLInfo germany = mock(TLInfo.class);
        TLInfo direct = mock(TLInfo.class);

        when(lotl.getTLInfos()).thenReturn(List.of(austria, germany));
        when(summary.getLOTLInfos()).thenReturn(List.of(lotl));
        when(summary.getOtherTLInfos()).thenReturn(List.of(direct));

        assertThat(TrustedListLoadingService.nationalTrustedLists(summary))
            .containsExactly(austria, germany, direct);
    }
}
