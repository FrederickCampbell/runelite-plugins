package com.farmingpatchadvisor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorTeleportSlotTest
{
    @Test public void consumablesUseTheirActualStackability()
    {
        Map<RouteTeleport, Integer> uses = new EnumMap<>(RouteTeleport.class);
        uses.put(RouteTeleport.CAMELOT, 3);
        uses.put(RouteTeleport.ECTOPHIAL, 1);
        assertEquals(4, DirectorTeleportPlan.inventorySlots(uses, Collections.emptyMap(), id -> false));
        assertEquals(2, DirectorTeleportPlan.inventorySlots(uses, Collections.emptyMap(), id -> true));
        assertEquals(1, DirectorTeleportPlan.inventorySlots(uses,
            Collections.singletonMap(RouteTeleport.ECTOPHIAL.itemIds()[0], 1), id -> true));
    }

    @Test public void protectionSuppliesHaveHumanReadableNames()
    {
        Crop yew = CropCatalog.findByName(PatchType.TREE, "Yew sapling");
        ProtectionPayment payment = ProtectionPaymentCatalog.forCrop(yew).get(0);
        assertEquals(payment.getName(), DirectorSupplies.name(payment.getItemId()));
    }
}
