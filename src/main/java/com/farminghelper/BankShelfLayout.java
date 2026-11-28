package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.regex.*;
import net.runelite.client.game.ItemVariationMapping;

/** Positional native Bank Tags layout. No custom bank rendering or item movement.
 * Physical stock is separate from layout positions, including withdrawal placeholders. */
final class BankShelfLayout
{
    static final int WIDTH=8;
    static final int PERMANENT=20,CHARGED=21,CONSUMABLE=22,TOOLS=23,COMPOST=24,SEEDS=25;
    private static final Pattern CHARGE=Pattern.compile("\\s*\\(([0-9]+)\\)$");
    private static final String[] RUNES={"air","water","earth","fire","law","nature","astral","cosmic","mist","dust","mud","smoke","steam","lava","blood","soul","death","mind","body"};
    private static final String[] TOOL_ORDER={"spade","rake","seed dibber","gardening trowel","watering can","gricoller","magic secateurs","secateurs","herb sack","silklined herb sack","seed box","reagent pouch","plant cure","axe","basket","sack","hammer","chisel","clockwork","bird house","birdhouse","logs"};
    private static final String[] COMPOST_ORDER={"empty bucket","bucket","empty bottomless compost","bottomless compost","compost","supercompost","ultracompost","volcanic ash","compost potion","saltpetre","coins"};
    private BankShelfLayout(){}
    private static String name(IntFunction<String> names,int id){return Objects.toString(names.apply(id),"").toLowerCase(Locale.ROOT);}
    private static String family(String n){return CHARGE.matcher(n).replaceFirst("").replaceFirst(" \\(i\\)$","").trim();}
    private static int charge(String n){Matcher m=CHARGE.matcher(n);return m.find()?Integer.parseInt(m.group(1)):0;}
    private static String baseName(IntFunction<String> names,int id)
    {String n=name(names,id);if(n.contains("graceful")||n.contains("farmer's"))return n;return name(names,ItemVariationMapping.map(id));}
    private static boolean outfit(String n,String kind){return n.contains(kind);}
    private static boolean diary(String n){return n.startsWith("ardougne cloak")||n.startsWith("explorer's ring")||n.startsWith("rada's blessing")||n.startsWith("karamja gloves")||n.startsWith("desert amulet")||n.startsWith("morytania legs")||n.startsWith("fremennik sea boots")||n.startsWith("kandarin headgear")||n.startsWith("wilderness sword")||n.startsWith("western banner");}
    private static boolean cape(String n){return n.contains("cape")&&!n.contains("graceful")&&!n.contains("escape");}
    private static boolean spellGear(String n){return n.contains("rune pouch")||n.startsWith("tome of ")||(n.contains("staff")&&!n.equals("dramen staff")&&!n.equals("lunar staff"));}
    private static boolean consumableTeleport(String n){return n.contains("tablet")||n.endsWith(" basalt")||n.contains("teleport scroll")||n.endsWith(" teleport")||n.endsWith(" teleport scroll");}
    private static boolean jewellery(String n){return n.startsWith("amulet ")||n.startsWith("ring ")||n.startsWith("skills necklace")||n.startsWith("games necklace")||n.startsWith("combat bracelet")||n.startsWith("necklace of passage")||n.startsWith("slayer ring")||n.startsWith("digsite pendant")||n.startsWith("pendant of ates")||n.startsWith("xeric's talisman")||n.startsWith("teleport crystal")||n.startsWith("pharaoh's sceptre");}
    private static int slot(String n,int actual)
    {
        if(n.contains("hood")||n.contains("helmet")||n.contains("strawhat"))return 0;
        if(n.contains("cape")||n.equals("diving apparatus"))return 1;
        if(n.startsWith("amulet")||n.equals("medallion of the deep")||n.equals("strung rabbit foot"))return 2;
        if(n.startsWith("tome of"))return 5;
        if(n.contains("graceful top")||n.contains("farmer's jacket")||n.contains("farmer's shirt"))return 4;
        if(n.contains("graceful legs")||n.contains("trousers")||n.contains("breeches"))return 7;
        if(n.contains("gloves"))return 9;
        if(n.contains("boots")||n.equals("flippers"))return 10;
        return actual;
    }
    // Same 4-column equipment silhouette as the native equipped layouts.
    private static int mannequinPos(int slot)
    {switch(slot){case 0:return 1;case 1:return 8;case 2:return 9;case 13:return 10;case 3:return 16;case 4:return 17;case 5:return 18;case 7:return 25;case 9:return 32;case 10:return 33;case 12:return 34;default:return -1;}}
    private static int order(String n,String[] ordered)
    {for(int i=0;i<ordered.length;i++)if(n.equals(ordered[i])||n.startsWith(ordered[i]+" ")||n.startsWith(ordered[i]+"("))return i;return ordered.length;}
    private static void pad(List<Integer> out){while(out.size()%WIDTH!=0)out.add(-1);}
    private static List<Integer> take(Set<Integer> left,java.util.function.IntPredicate condition,Comparator<Integer> comparator)
    {List<Integer> result=new ArrayList<>();for(int id:left)if(condition.test(id))result.add(id);result.sort(comparator);left.removeAll(result);return result;}
    private static void familyRows(List<Integer> out,List<Integer> items,IntFunction<String> names)
    {
        Map<String,List<Integer>> families=new TreeMap<>();for(int id:items)families.computeIfAbsent(family(name(names,id)),k->new ArrayList<>()).add(id);
        for(List<Integer> group:families.values())
        {
            group.sort(Comparator.comparingInt((Integer id)->charge(name(names,id))).reversed().thenComparingInt(Integer::intValue));
            out.addAll(group);
        }
    }
    private static int priorityKind(String n,int slot)
    {
        if(n.endsWith(" rune"))return 0;
        if(slot==2||n.startsWith("amulet ")||n.contains("necklace")||n.contains("pendant")||n.equals("xeric's talisman"))return 1;
        if(slot==12||n.startsWith("ring ")||n.startsWith("slayer ring")||n.startsWith("explorer's ring"))return 2;
        if(n.contains("book")||n.startsWith("tome of "))return 3;
        if(slot>=0||n.contains("staff")||n.contains("cape")||diary(n))return 4;
        if(n.contains("rune pouch"))return 5;
        if(consumableTeleport(n))return 6;
        if(jewellery(n))return 7;
        return 8;
    }
    static List<Integer> priorityOrder(Collection<Integer> ids,IntFunction<String> names,IntUnaryOperator equipment)
    {
        List<Integer> result=new ArrayList<>(new LinkedHashSet<>(ids));
        Map<Integer,String> labels=new HashMap<>();
        Map<Integer,Integer> kinds=new HashMap<>();
        for(int id:result){String n=name(names,id);labels.put(id,n);kinds.put(id,priorityKind(n,equipment.applyAsInt(id)));}
        result.sort(Comparator.comparingInt((Integer id)->kinds.get(id))
            .thenComparingInt(id->kinds.get(id)==0?order(labels.get(id).replaceFirst(" rune$",""),RUNES):0)
            .thenComparing(id->family(labels.get(id))).thenComparing(Comparator.comparingInt((Integer id)->charge(labels.get(id))).reversed()).thenComparingInt(Integer::intValue));
        return result;
    }
    static int[] build(Set<Integer> owned,IntFunction<String> names,IntUnaryOperator equipment,IntUnaryOperator transport)
    {return build(owned,names,equipment,transport,List.of());}
    static int[] build(Set<Integer> owned,IntFunction<String> names,IntUnaryOperator equipment,IntUnaryOperator transport,List<Integer> priority)
    {
        Set<Integer> left=new HashSet<>(owned);
        Comparator<Integer> byName=Comparator.comparing((Integer id)->name(names,id)).thenComparingInt(Integer::intValue);
        List<Integer> out=new ArrayList<>();
        for(int id:priority)if(left.remove(id))out.add(id);
        pad(out);
        // Two mannequins per row block. Extra recolors form further silhouettes,
        // never repeated IDs, and no synthetic unowned equipment.
        List<Integer> graceful=take(left,id->outfit(baseName(names,id),"graceful"),byName);
        List<Integer> farmer=take(left,id->outfit(baseName(names,id),"farmer's"),byName);
        List<Integer> overflow=new ArrayList<>();
        while(!graceful.isEmpty()||!farmer.isEmpty())
        {
            int[] cells=new int[40];Arrays.fill(cells,-1);
            for(int side=0;side<2;side++)
            {
                List<Integer> group=side==0?graceful:farmer;
                for(Iterator<Integer> iter=group.iterator();iter.hasNext();)
                {
                    int id=iter.next();int pos=mannequinPos(slot(baseName(names,id),equipment.applyAsInt(id)));
                    if(pos<0){overflow.add(id);iter.remove();continue;}
                    pos+=side*4;if(cells[pos]<0){cells[pos]=id;iter.remove();}
                }
            }
            if(Arrays.stream(cells).anyMatch(id->id>=0)){for(int id:cells)out.add(id);}
        }
        out.addAll(overflow);
        out.addAll(take(left,id->cape(name(names,id)),byName));
        out.addAll(take(left,id->CuratedBank.divingEquipment(name(names,id)),Comparator.comparingInt((Integer id)->slot(name(names,id),equipment.applyAsInt(id))).thenComparing(byName)));
        out.addAll(take(left,id->diary(name(names,id)),byName));
        // Permanent travel accessories, then one row per jewellery family.
        out.addAll(take(left,id->transport.applyAsInt(id)==PERMANENT&&!jewellery(name(names,id))&&!consumableTeleport(name(names,id)),byName));
        familyRows(out,take(left,id->transport.applyAsInt(id)>=0&&jewellery(name(names,id)),byName),names);
        out.addAll(take(left,id->transport.applyAsInt(id)>=0&&!consumableTeleport(name(names,id)),byName));
        out.addAll(take(left,id->name(names,id).equals("dramen staff")||name(names,id).equals("lunar staff"),byName));
        // 4-column rune matrix on the left, spell equipment in the right-hand block.
        List<Integer> runes=take(left,id->name(names,id).endsWith(" rune"),byName);
        List<Integer> spell=take(left,id->spellGear(name(names,id)),Comparator.comparingInt((Integer id)->name(names,id).contains("rune pouch")?0:name(names,id).startsWith("tome of")?2:1).thenComparing(byName));
        if(!runes.isEmpty()||!spell.isEmpty())
        {
            pad(out);int highest=runes.stream().mapToInt(id->Arrays.asList(RUNES).indexOf(name(names,id).replaceFirst(" rune$",""))).max().orElse(-1);
            int rows=Math.max((highest+4)/4,(spell.size()+3)/4);
            int[] cells=new int[rows*WIDTH];Arrays.fill(cells,-1);
            List<Integer> spare=new ArrayList<>();
            for(int id:runes){int i=Arrays.asList(RUNES).indexOf(name(names,id).replaceFirst(" rune$",""));if(i<0||cells[i/4*WIDTH+i%4]>=0)spare.add(id);else cells[i/4*WIDTH+i%4]=id;}
            for(int i=0;i<spell.size();i++)cells[i/4*WIDTH+4+i%4]=spell.get(i);
            for(int id:cells)out.add(id);out.addAll(spare);
        }
        out.addAll(take(left,id->consumableTeleport(name(names,id))||name(names,id).equals("master scroll book"),byName));
        out.addAll(take(left,id->{String n=name(names,id);return order(n,TOOL_ORDER)<TOOL_ORDER.length||n.contains(" axe")||n.endsWith(" logs")||n.contains("watering can");},Comparator.comparingInt((Integer id)->order(name(names,id),TOOL_ORDER)).thenComparing(byName)));
        out.addAll(take(left,id->order(name(names,id),COMPOST_ORDER)<COMPOST_ORDER.length||name(names,id).contains("compost"),Comparator.comparingInt((Integer id)->order(name(names,id),COMPOST_ORDER)).thenComparing(byName)));
        // Pot/seedling preparation is a separate shelf, not a new crop allocation.
        out.addAll(take(left,id->CuratedBank.saplingPreparation(name(names,id)),Comparator.comparingInt((Integer id)->{String n=name(names,id);return n.equals("plant pot")||n.equals("empty plant pot")?0:n.equals("filled plant pot")?1:n.endsWith("(w)")?3:2;}).thenComparing(byName)));
        Map<String,List<Integer>> crops=new TreeMap<>();
        for(int id:take(left,id->{String n=name(names,id);return CropCatalog.findByItemId(id)!=null||n.endsWith(" seed")||n.endsWith(" seeds")||n.contains("sapling")||n.endsWith(" spore")||n.endsWith(" spores");},byName))
        {
            Crop crop=VaultRanking.resolve(id,names.apply(id));String kind=crop==null?"Other planting":crop.getPatchType().getDisplayName();
            crops.computeIfAbsent(kind,k->new ArrayList<>()).add(id);
        }
        // All remaining explicit supplies, including protection payments, get their own block.
        out.addAll(take(left,id->true,byName));
        for(List<Integer> group:crops.values())out.addAll(group);
        while(!out.isEmpty()&&out.get(out.size()-1)==-1)out.remove(out.size()-1);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
    /** Keep withdrawal positions for this bank visit. New stock appends as a
     * structured block; newly ineligible owned stock is removed. Empty
     * layout cells never enter the stock/requirement calculations. */
    static int[] reconcile(int[] prior,Set<Integer> eligible,Set<Integer> owned,IntFunction<String> names,IntUnaryOperator equipment,IntUnaryOperator transport)
    {
        if(prior.length==0)return build(eligible,names,equipment,transport);
        List<Integer> out=new ArrayList<>();Set<Integer> fresh=new HashSet<>(eligible);
        Set<Integer> previous=new HashSet<>();for(int id:prior)if(id>=0)previous.add(id);
        for(int id:prior)
        {
            if(id>=0&&owned.contains(id)&&!eligible.contains(id))out.add(-1);
            else if(id>=0&&!owned.contains(id))
            {
                // A consumed charge can change an ID, but not the shelf position.
                // Only replace with a newly owned, eligible charged family member;
                // never conflate different seeds, tools, or simultaneous stacks.
                String oldName=name(names,id);
                int replacement=fresh.stream().filter(n->!previous.contains(n)&&charge(oldName)>0&&
                    charge(name(names,n))>0&&family(oldName).equals(family(name(names,n)))&&
                    ItemVariationMapping.map(id)==ItemVariationMapping.map(n)&&transport.applyAsInt(id)==CHARGED&&
                    transport.applyAsInt(n)==CHARGED).min(Integer::compareTo).orElse(id);
                out.add(replacement);fresh.remove(replacement);
            }
            else{out.add(id);fresh.remove(id);}
        }
        if(!fresh.isEmpty()){pad(out);for(int id:build(fresh,names,equipment,transport))out.add(id);}
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
