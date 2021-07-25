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
    void isClassName() {
        assertTrue(StringUtility.isClassName("com/github/logicf/class"));        assertTrue(StringUtility.isClassName("com/github/logicf/class"));
        assertTrue(StringUtility.isClassName("com/github/logicf/$$__cl_a_ss"));
        assertTrue(StringUtility.isClassName("com/gith$$ub/log$$icf/cla$$ss"));
        assertTrue(StringUtility.isClassName("$$/__"));
        assertFalse(StringUtility.isClassName("abc"));
        assertFalse(StringUtility.isClassName("com/github/logicf/"));
        assertFalse(StringUtility.isClassName("com//github/logicf"));
        assertFalse(StringUtility.isClassName("com.github.logicf.class"));
    }

    @Test
    void isClassDesc() {
        assertTrue(StringUtility.isClassDesc("Lcom/github/logicf/class;"));        assertTrue(StringUtility.isClassName("com/github/logicf/class"));
        assertTrue(StringUtility.isClassDesc("Lcom/github/logicf/$$__cl_a_ss;"));
        assertTrue(StringUtility.isClassDesc("Lcom/gith$$ub/log$$icf/cla$$ss;"));
        assertTrue(StringUtility.isClassDesc("L$$/__;"));
        assertFalse(StringUtility.isClassDesc("Labc;"));
        assertFalse(StringUtility.isClassDesc("Lcom/github/logicf/;"));
        assertFalse(StringUtility.isClassDesc("Lcom//github/logicf;"));
        assertFalse(StringUtility.isClassDesc("Lcom.github.logicf.class;"));
        assertFalse(StringUtility.isClassDesc("com/github/logicf/class"));
    }

    @Test
    void isFieldDesc() {
        assertTrue(StringUtility.isFieldDesc("I"));
        assertTrue(StringUtility.isFieldDesc("Ljava/lang/Object;"));
        assertTrue(StringUtility.isFieldDesc("[[[D"));
        assertTrue(StringUtility.isFieldDesc("[B"));
        assertTrue(StringUtility.isFieldDesc("[Ljava/lang/Object;"));
        assertTrue(StringUtility.isFieldDesc("[[Ljava/lang/Object;"));
        assertFalse(StringUtility.isFieldDesc("[[Ljava/lang/Object;["));
        assertFalse(StringUtility.isFieldDesc("[[Ljava/lang/Object;B"));
        assertFalse(StringUtility.isFieldDesc("com/github/logicf/class"));
        assertFalse(StringUtility.isFieldDesc("()V"));
        assertFalse(StringUtility.isFieldDesc("([Ljava/lang/Object;)D"));
    }

    @Test
    void isMethodDesc() {
        assertTrue(StringUtility.isMethodDesc("()V"));
        assertTrue(StringUtility.isMethodDesc("()Ljava/lang/Object;"));
        assertTrue(StringUtility.isMethodDesc("()[[[D"));
        assertTrue(StringUtility.isMethodDesc("([BBBDDBB[BBDLjava/lang/Object;)V"));
        assertTrue(StringUtility.isMethodDesc("([Ljava/lang/Object;)D"));
        assertTrue(StringUtility.isMethodDesc("([[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
        assertFalse(StringUtility.isMethodDesc("com/github/logicf/class"));
        assertFalse(StringUtility.isMethodDesc("Ljava/lang/Object;"));
        assertFalse(StringUtility.isMethodDesc("[[[D"));
    }

    @Test
    void classNameToDesc() {
    }

    @Test
    void classDescToName() {
    }
}