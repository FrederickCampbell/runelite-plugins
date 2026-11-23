package com.farmingpatchadvisor;

import java.util.ArrayDeque;
import java.time.Instant;

/** Opt-in, bounded, local diagnostics. No account identifiers, inventory dumps or network. */
final class FarmingHelperDiagnostics
{
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private boolean enabled;
    synchronized void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        if (!enabled) events.clear();
        else record("Diagnostics enabled");
    }
    synchronized void record(String text)
    {
        if (!enabled) return;
        if (events.size() == 120) events.removeFirst();
        events.addLast(Instant.now() + " " + text);
    }
    synchronized String snapshot()
    {
        return "Farming Helper v0.9 beta\n" + (enabled ? String.join("\n", events) : "Diagnostics disabled.");
    }
}
