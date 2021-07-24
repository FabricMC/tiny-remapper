package net.fabricmc.tinyremapper.extension.mixin.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringUtilityTest {

    @Test
    void addPrefix() {
        assertEquals("prefix123", StringUtility.addPrefix("prefix", "123"));
        assertEquals("BlaBlaAaa", StringUtility.addPrefix("Bla", "BlaAaa"));
    }

    @Test
    void removePrefix() {
        assertEquals("123", StringUtility.removePrefix("prefix", "prefix123"));
        assertThrows(RuntimeException.class, () -> StringUtility.removePrefix("prefix", "noprefix123"));
    }

    @Test
    void addCamelPrefix() {
        assertEquals("prefix", StringUtility.addCamelPrefix("prefix", ""));
        assertEquals("prefix123", StringUtility.addCamelPrefix("prefix", "123"));
        assertEquals("prefixAbc", StringUtility.addCamelPrefix("prefix", "abc"));
        assertEquals("prefixAbc", StringUtility.addCamelPrefix("prefix", "Abc"));
    }

    @Test
    void removeCamelPrefix() {
        assertEquals("123", StringUtility.removeCamelPrefix("prefix", "prefix123"));
        assertEquals("abc", StringUtility.removeCamelPrefix("prefix", "prefixAbc"));
        assertEquals("abc", StringUtility.removeCamelPrefix("prefix", "prefixabc"));
        assertEquals("", StringUtility.removeCamelPrefix("prefix", "prefix"));
    }

    @Test
    void isClassDesc() {
    }

    @Test
    void isClassName() {
    }

    @Test
    void isFieldDesc() {
    }

    @Test
    void isMethodDesc() {
    }

    @Test
    void classNameToDesc() {
    }

    @Test
    void classDescToName() {
    }
}