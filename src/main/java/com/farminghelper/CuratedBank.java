package com.farminghelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.*;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;

/** Read-only supply shelf. No routing, loadout optimization or settings overrides. */
final class CuratedBank
{
    private static final Pattern CONDITION=Pattern.compile("(\\d+)(=|>=|<=|>|<|&)(\\d+)");
    private static final String TRAVEL=".*graceful (?:hood|top|legs|gloves|boots|cape).*|strung rabbit foot|(?:divine )?rune pouch|(?:air|water|earth|fire|law|astral|nature|blood|death|soul|cosmic|mind|body|steam|mist|dust|mud|smoke|lava) rune|staff of (?:air|water|earth|fire)|(?:air|water|earth|fire|steam|mist|dust|mud|smoke|lava) battlestaff|mystic .* staff|tome of (?:fire|water|earth).*|coins";
    private static final Pattern BIRD=Pattern.compile("(?i)^(?:"+TRAVEL+")$");
    private static final Pattern GEAR=Pattern.compile("(?i)^(?:"+TRAVEL+"|farmer.s (?:strawhat|jacket|shirt|boro trousers|breeches|boots)|magic secateurs|secateurs|herb sack|seed box|gricoller.s can|watering can(?:\\([0-8]\\))?|spade|rake|seed dibber|gardening trowel|plant cure|bottomless compost bucket.*|ultracompost|supercompost|compost|empty bucket|bucket|volcanic ash|compost potion.*|basket|sack|amulet of bounty.*|amulet of nature|pre-nature amulet|fishbowl helmet|diving apparatus|medallion of the deep|flippers|(?:bronze|iron|steel|black|mithril|adamant|rune|dragon|crystal|infernal|3rd age) axe.*|imcando hammer|hammer|chisel)$");
    private static final List<String[]> ROWS=load();
    private static List<String[]> load()
    {
        List<String[]> result=new ArrayList<>();
        try(InputStream in=CuratedBank.class.getResourceAsStream("/farming-teleport-shelf.tsv"))
        {
            if(in==null)return result;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)))
            {for(String line;(line=reader.readLine())!=null;){if(line.startsWith("#"))continue;String[] c=line.split("\\t",-1);if(c.length==9)result.add(c);}}
        }catch(IOException ignored){}
        return List.copyOf(result);
    }
    static boolean equipment(String name,boolean bird)
    {return name!=null&&(bird?BIRD:GEAR).matcher(name).matches();}
    static boolean modeAllows(String mode,boolean consumable)
    {
        String normalized=mode==null?"INVENTORY_NON_CONSUMABLE":mode.toUpperCase(Locale.ROOT);
        if(normalized.equals("NONE"))return false;
        if(!(normalized.startsWith("INVENTORY")||normalized.startsWith("UNLOCKED")||normalized.startsWith("ALL")))return false;
        return !consumable||!(normalized.contains("NON_CONSUMABLE")||normalized.contains("(PERM)"));
    }
    static Set<Integer> blocked(String value)
    {
        Set<Integer> ids=new HashSet<>();
        if(value!=null)for(String entry:value.split(","))try
        {
            String part=entry.trim();
            // Conservatively hide a family with any destination block.
            if(part.contains("@"))part=part.substring(0,part.indexOf('@'));
            else if(part.contains(":"))continue;
            ids.add(Integer.parseInt(part));
        }catch(NumberFormatException ignored){}
        return ids;
    }
    private static boolean conditions(String text,java.util.function.IntUnaryOperator value)
    {
        if(text.isEmpty())return true;
        for(String condition:text.split(";"))
        {
            Matcher m=CONDITION.matcher(condition.trim());if(!m.matches())return false;
            int v=value.applyAsInt(Integer.parseInt(m.group(1))),n=Integer.parseInt(m.group(3));
            boolean ok;
            switch(m.group(2)){case "=":ok=v==n;break;case ">":ok=v>n;break;case "<":ok=v<n;break;case ">=":ok=v>=n;break;case "<=":ok=v<=n;break;case "&":ok=(v&n)==n;break;default:ok=false;}
            if(!ok)return false;
        }
        return true;
    }
    private static boolean skills(Client c,String text)
    {
        if(text.isEmpty())return true;
        for(String condition:text.split(";"))
        {
            String[] p=condition.trim().split(" ",2);if(p.length!=2)return false;
            try{if(c.getRealSkillLevel(Skill.valueOf(p[1].toUpperCase(Locale.ROOT)))<Integer.parseInt(p[0]))return false;}
            catch(IllegalArgumentException e){return false;}
        }
        return true;
    }
    private static boolean quests(Client c,String text)
    {
        if(text.isEmpty())return true;
        for(String name:text.split(";"))
        {
            Quest found=null;for(Quest q:Quest.values())if(q.getName().equalsIgnoreCase(name.trim())){found=q;break;}
            if(found==null||found.getState(c)!=QuestState.FINISHED)return false;
        }
        return true;
    }
    private static boolean fairyAccess(Client c)
    {
        for(Quest q:Quest.values())if(q.name().contains("FAIRYTALE_II")||q.name().contains("FAIRY_TALE_II"))
            return q.getState(c)!=QuestState.NOT_STARTED;
        return false;
    }
    private static String config(ConfigManager c,String key){return c.getConfiguration("shortestpath",key);}
    static Set<Integer> shelf(Set<Integer> run,Map<Integer,Integer> bank,IntFunction<String> names,Client client,ConfigManager config,boolean bird)
    {
        if(run==null)return null;
        Set<Integer> result=new HashSet<>(run);
        String mode=config(config,"useTeleportationItems");
        Set<Integer> blocked=blocked(config(config,"blockedTeleportItems"));
        boolean fairy=!"false".equalsIgnoreCase(config(config,"useFairyRings"));
        boolean quetzal=!"false".equalsIgnoreCase(config(config,"useQuetzals"));
        for(var entry:bank.entrySet())
        {
            if(entry.getValue()<=0)continue;
            int id=entry.getKey();String name=names.apply(id);
            if(equipment(name,bird))result.add(id);
            if(fairy&&!blocked.contains(id)&&(id==772||id==9084)&&fairyAccess(client))result.add(id);
        }
        for(String[] row:ROWS)
        {
            int id=Integer.parseInt(row[0]),family=Integer.parseInt(row[1]);
            if(bank.getOrDefault(id,0)<=0||blocked.contains(id)||blocked.contains(family)||!modeAllows(mode,"T".equals(row[2])))continue;
            if("whistle".equals(row[7])&&!quetzal)continue;
            try
            {
                if(skills(client,row[3])&&quests(client,row[4])&&conditions(row[5],client::getVarbitValue)&&conditions(row[6],client::getVarpValue))result.add(id);
            }catch(RuntimeException ignored){}
        }
        return Set.copyOf(result);
    }
}
