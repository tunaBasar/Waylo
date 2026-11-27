package com.waylo.hotelservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceNamesTest {

    @Test
    void istanbulVariantsAllNormalizeTheSame() {
        for (String v : new String[] {"\u0130stanbul", "istanbul", "ISTANBUL", "Istanbul", " istanbul ", "\u0131stanbul"}) {
            assertThat(PlaceNames.normalizeForSearch(v)).isEqualTo("istanbul");
        }
    }

    @Test
    void turkishLettersFold() {
        assertThat(PlaceNames.normalizeForSearch("\u0130zmir")).isEqualTo(PlaceNames.normalizeForSearch("Izmir"));
        assertThat(PlaceNames.normalizeForSearch("Nev\u015fehir")).isEqualTo("nevsehir");
        assertThat(PlaceNames.normalizeForSearch("G\u00f6reme")).isEqualTo("goreme");
        assertThat(PlaceNames.normalizeForSearch("\u00c7ukurova \u011e\u00dc\u00d6")).isEqualTo("cukurova guo");
    }

    @Test
    void otherDiacriticsStripped() {
        assertThat(PlaceNames.normalizeForSearch("Z\u00fcrich")).isEqualTo("zurich");
        assertThat(PlaceNames.normalizeForSearch("S\u00e3o Paulo")).isEqualTo("sao paulo");
    }

    @Test
    void nullAndBlankMeanNoFilter() {
        assertThat(PlaceNames.normalizeForSearch(null)).isNull();
        assertThat(PlaceNames.normalizeForSearch("   ")).isNull();
        assertThat(PlaceNames.matches("Istanbul", null)).isTrue();
        assertThat(PlaceNames.matches("Istanbul", " ")).isTrue();
        assertThat(PlaceNames.matches(null, "istanbul")).isFalse();
    }
}
