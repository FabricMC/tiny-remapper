package net.fabricmc.tinyremapper.util;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class OptionalIntComparatorTest {

    @Test
    public void compare() {
        Comparator<OptionalInt> comp = new OptionalIntComparator();

        OptionalInt o1 = OptionalInt.empty();
        OptionalInt o2 = OptionalInt.of(1);
        OptionalInt o3 = OptionalInt.of(2);

        assertEquals(0, comp.compare(o1, o1));
        assertEquals(0, comp.compare(o2, o2));

        assertEquals(-1, comp.compare(o1, o2));
        assertEquals(-1, comp.compare(o2, o3));

        assertEquals(1, comp.compare(o2, o1));
        assertEquals(1, comp.compare(o3, o2));

        List<OptionalInt> list = Arrays.asList(
                OptionalInt.of(1), OptionalInt.of(3), OptionalInt.empty(), OptionalInt.of(2)
        );
        list.sort(comp);
        assertArrayEquals(list.toArray(), new OptionalInt[]{
                OptionalInt.empty(), OptionalInt.of(1), OptionalInt.of(2), OptionalInt.of(3)
        });
    }
}