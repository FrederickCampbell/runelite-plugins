package com.farminghelper;

import java.util.Set;

/** A forward jump must not silently lose earlier unfinished patches. */
final class Progress
{
    private Progress() {}

    static int nextUnfinished(int size, int current, Set<Integer> completed)
    {
        for (int offset = 1; offset < size; offset++)
        {
            int next = (current + offset) % size;
            if (!completed.contains(next)) return next;
        }
        return size;
    }
}
