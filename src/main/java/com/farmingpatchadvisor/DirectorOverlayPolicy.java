package com.farmingpatchadvisor;

/** Guide visibility is independent of the retained planner/stock data. */
final class DirectorOverlayPolicy
{
    private DirectorOverlayPolicy() {}
    static boolean world(boolean paused, boolean preparing, boolean planning, boolean finished, boolean compost)
    {
        return !paused && !preparing && !planning && !finished && !compost;
    }
    static boolean bank(boolean hasPlan, boolean preparing, boolean awaitingBank,
                        boolean paused, boolean finished, boolean planning, boolean compost)
    {
        return hasPlan && (preparing || awaitingBank) && !paused && !finished && !planning && !compost;
    }
}
