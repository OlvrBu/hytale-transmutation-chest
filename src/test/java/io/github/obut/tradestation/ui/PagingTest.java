package io.github.obut.tradestation.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {

    @Test
    void countsPages() {
        assertEquals(1, Paging.pageCount(0, 30));
        assertEquals(1, Paging.pageCount(30, 30));
        assertEquals(2, Paging.pageCount(31, 30));
        assertEquals(32, Paging.pageCount(941, 30));
    }

    @Test
    void clampsIntoRange() {
        assertEquals(0, Paging.clamp(-1, 100, 30));
        assertEquals(3, Paging.clamp(9, 100, 30));
        assertEquals(0, Paging.clamp(5, 0, 30));
    }

    @Test
    void slicesPages() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(1, 2), Paging.slice(items, 0, 2));
        assertEquals(List.of(5), Paging.slice(items, 2, 2));
        assertEquals(List.of(5), Paging.slice(items, 7, 2)); // clamped to the last page
        assertEquals(List.of(), Paging.slice(List.of(), 0, 2));
    }
}
