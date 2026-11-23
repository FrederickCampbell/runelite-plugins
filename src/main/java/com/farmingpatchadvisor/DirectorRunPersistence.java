package com.farmingpatchadvisor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** Persistent, profile-scoped run checkpoint encoding. Not a serialization of
 * client objects or a cache of a different account's bank inventory. */
final class DirectorRunPersistence
{
    private DirectorRunPersistence() {}

    static String encode(Set<String> completed)
    {
        if (completed == null || completed.isEmpty()) return "";
        Set<String> sorted = new TreeSet<>(completed);
        return String.join(";", sorted);
    }

    static Set<String> decode(String encoded)
    {
        if (encoded == null || encoded.isEmpty()) return Collections.emptySet();
        Set<String> results = new LinkedHashSet<>();
        for (String token : encoded.split(";"))
        {
            // FarmRouteManager's stable key: area~type~subpatch.
            if (!token.isEmpty() && token.split("~", -1).length == 3 &&
                token.length() <= 200)
            {
                results.add(token);
            }
        }
        return results;
    }
}
