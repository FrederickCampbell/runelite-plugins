package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.runelite.api.coords.WorldPoint;

/** Run the same allocation fixtures against either checkout outside the plugin jar. */
public final class RefactorProbe
{
    private static volatile int sink;
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception
    {
        if (args.length == 2 && "pixels".equals(args[0]))
        {
            for (String name : List.of("farming-helper-preview.png", "farming-helper-home-preview.png", "farming-helper-preparation.png"))
            {
                var a = javax.imageio.ImageIO.read(new java.io.File(args[1] + "/build/reports", name));
                var b = javax.imageio.ImageIO.read(new java.io.File("build/reports", name));
                if (a.getWidth()!=b.getWidth() || a.getHeight()!=b.getHeight()) throw new AssertionError(name);
                int changed = 0;
                for (int y=0; y<a.getHeight(); y++) for (int x=0; x<a.getWidth(); x++)
                    if (a.getRGB(x,y)!=b.getRGB(x,y)) changed++;
                System.out.printf("%s: %dx%d, changed pixels=%d%n",name,a.getWidth(),a.getHeight(),changed);
                if (changed != 0) throw new AssertionError(name);
            }
            return;
        }
        if (args.length > 0 && "preview".equals(args[0]))
        {
            FarmingHelperPreviewTest preview = new FarmingHelperPreviewTest();
            preview.rendersHomeAtSidebarWidth();
            preview.rendersPreparationWithItsPinnedStartButton();
            preview.rendersGuideAtSidebarWidth();
            return;
        }

        THREADS.setThreadAllocatedMemoryEnabled(true);
        QuestFarmingStateReader reader = new QuestFarmingStateReader(null, null);
        List<FarmRunPatch> patches = FarmRunCatalog.patches();
        WorldPoint tile = new WorldPoint(2813, 3463, 0);
        Map<Integer,Integer> stock = new HashMap<>();
        for (int i = 10000; i < 10512; i++) stock.put(i, 1);
        List<FarmRunPatch> ordinary = List.of(new FarmRunPatch("Catherby", "", PatchType.HERB));

        measure("regions", () -> {
            int hits = 0;
            for (FarmRunPatch patch : patches)
                if (reader.inPatchRegion(patch.getPatchType().name(), patch.getLocation(), patch.getName(), tile)) hits++;
            return hits;
        });
        measure("watering", () -> CropCare.wateringCan(stock, id -> id == 10333 ? "Watering can(7)" : "Other item"));
        measure("ordinary-kit", () -> {
            Map<Integer,Integer> kit = new HashMap<>();
            CompostRun.addSupplies(kit, ordinary, Map.of(), stock, new HashMap<>());
            return kit.size() + BirdhouseRun.supplies(ordinary, Map.of(), stock, kit, 80, 80).size();
        });
        measure("timestamps", () -> {
            int result = 1;
            for (String input : SAMPLES)
                result = 31 * result + Long.hashCode(QuestFarmingStateReader.timestampMillis(input));
            return result;
        });
    }

    private static final List<String> SAMPLES = List.of("4:1234567890", "87:1700000000", "0:1", "34:1700102222");
    private static void measure(String name, IntSupplier operation)
    {
        final int iterations = 1024;
        int expected = operation.getAsInt();
        for (int i = 0; i < iterations; i++) sink = operation.getAsInt();
        long[] samples = new long[5];
        long thread = Thread.currentThread().getId();
        for (int batch = 0; batch < samples.length; batch++)
        {
            long before = THREADS.getThreadAllocatedBytes(thread);
            for (int i = 0; i < iterations; i++)
            {
                int result = operation.getAsInt();
                if (result != expected) throw new AssertionError(name);
                sink = result;
            }
            samples[batch] = (THREADS.getThreadAllocatedBytes(thread) - before) / iterations;
        }
        Arrays.sort(samples);
        System.out.printf("PROBE %s %d %d%n", name, expected, samples[2]);
    }
}
