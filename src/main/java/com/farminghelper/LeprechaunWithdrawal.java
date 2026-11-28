package com.farminghelper;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.InterfaceID.FarmingTools;
import static net.runelite.api.gameval.VarbitID.*;
import static net.runelite.api.widgets.WidgetUtil.componentToId;

/** Read-only withdrawal guidance for one immediate patch interaction. Native
 * storage counts follow farming_tools_getstored (script 1063). */
final class LeprechaunWithdrawal
{
    final int child,quantity;
    final String name;
    final LeprechaunWithdrawal companion;
    private LeprechaunWithdrawal(int child,int quantity,String name,LeprechaunWithdrawal companion)
    {this.child=child;this.quantity=quantity;this.name=name;this.companion=companion;}

    static int child(int item,boolean water)
    {
        if(water)return componentToId(FarmingTools.WATERINGCAN);
        switch(item)
        {
            case ItemID.RAKE:return componentToId(FarmingTools.RAKE);
            case ItemID.DIBBER:return componentToId(FarmingTools.DIBBER);
            case ItemID.SPADE:return componentToId(FarmingTools.SPADE);
            case ItemID.SECATEURS:case ItemID.FAIRY_ENCHANTED_SECATEURS:return componentToId(FarmingTools.SECATEURS);
            case Supplies.GARDENING_TROWEL:return componentToId(FarmingTools.TROWEL);
            case ItemID.PLANT_CURE:return componentToId(FarmingTools.PLANTCURE);
            case ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED:return componentToId(FarmingTools.BOTTOMLESS_BUCKET);
            case ItemID.BUCKET_COMPOST:return componentToId(FarmingTools.COMPOST);
            case ItemID.BUCKET_SUPERCOMPOST:return componentToId(FarmingTools.SUPERCOMPOST);
            case ItemID.BUCKET_ULTRACOMPOST:return componentToId(FarmingTools.ULTRACOMPOST);
            default:return -1;
        }
    }
    static int stored(Client c,int item)
    {
        switch(item)
        {
            case CompostRun.BUCKET:return c.getVarbitValue(FARMING_TOOLS_BUCKETS)+32*c.getVarbitValue(FARMING_TOOLS_EXTRABUCKETS)+256*c.getVarbitValue(FARMING_TOOLS_EXTRA2BUCKETS);
            case ItemID.RAKE:return 2*c.getVarbitValue(FARMING_TOOLS_EXTRARAKES)+c.getVarbitValue(FARMING_TOOLS_RAKE);
            case ItemID.DIBBER:return 2*c.getVarbitValue(FARMING_TOOLS_EXTRADIBBERS)+c.getVarbitValue(FARMING_TOOLS_DIBBER);
            case ItemID.SPADE:return 2*c.getVarbitValue(FARMING_TOOLS_EXTRASPADES)+c.getVarbitValue(FARMING_TOOLS_SPADE);
            case ItemID.SECATEURS:case ItemID.FAIRY_ENCHANTED_SECATEURS:
                return 2*c.getVarbitValue(FARMING_TOOLS_EXTRASECATEURS)+c.getVarbitValue(FARMING_TOOLS_SECATEURS);
            case Supplies.GARDENING_TROWEL:return 2*c.getVarbitValue(FARMING_TOOLS_EXTRATROWELS)+c.getVarbitValue(FARMING_TOOLS_TROWEL);
            case ItemID.PLANT_CURE:return c.getVarbitValue(FARMING_TOOLS_PLANTCURE);
            default:return CompostChoice.stored(c,item);
        }
    }
    static int needed(StepEngine.Action action,PatchType type,int compost,boolean barbarian,
        Map<Integer,Integer> bag,Map<Integer,Integer> carried,int wateringCan)
    {
        switch(action)
        {
            case WATER:return wateringCan<=0?0:-1;
            case COMPOST:return compost>=0&&bag.getOrDefault(compost,0)==0?compost:-1;
            case PLANT:
                if(type==PatchType.GRAPEVINE&&bag.getOrDefault(Supplies.GARDENING_TROWEL,0)==0)return Supplies.GARDENING_TROWEL;
                if(type.usesDibber()&&!barbarian&&bag.getOrDefault(ItemID.DIBBER,0)==0)return ItemID.DIBBER;
                if(type.isTree()&&bag.getOrDefault(ItemID.SPADE,0)==0)return ItemID.SPADE;
                return -1;
            case HARVEST:
                if(type==PatchType.HERB||type==PatchType.ALLOTMENT||type==PatchType.HOPS||type==PatchType.FLOWER)
                    return bag.getOrDefault(ItemID.SPADE,0)==0?ItemID.SPADE:-1;
                return -1;
            case RAKE:case DIG_STUMP:case CLEAR_DEAD:case CLEAR_LIVING:case CURE:
                for(int item:StepItems.select(action,type,null,compost,0,carried))
                    if((item==ItemID.SECATEURS||item==ItemID.FAIRY_ENCHANTED_SECATEURS?carried:bag).getOrDefault(item,0)==0)return item;
                return -1;
            default:return -1;
        }
    }
    static LeprechaunWithdrawal resolve(Client c,StepEngine.Action action,PatchType type,int compost,
        boolean barbarian,Map<Integer,Integer> bag,Map<Integer,Integer> carried,int wateringCan,int free,IntFunction<String> names)
    {
        int item=needed(action,type,compost,barbarian,bag,carried,wateringCan);
        if(item<0)return null;
        boolean water=action==StepEngine.Action.WATER;
        if(water)item=CropCare.storedCan(c,names);
        else if(item==ItemID.SECATEURS&&c.getVarbitValue(FARMING_TOOLS_FAIRYSECATEURS)==1)item=ItemID.FAIRY_ENCHANTED_SECATEURS;
        int stock=water?(item>0?1:0):stored(c,item);
        if(item<=0||stock<=0)return null;
        return plan(item,water,stock,free,names.apply(item));
    }
    static boolean magicStored(Client c)
    {return c.getVarbitValue(FARMING_TOOLS_FAIRYSECATEURS)==1&&stored(c,ItemID.FAIRY_ENCHANTED_SECATEURS)>0;}

    /** The current compost step remains authoritative. Only offer the future
     * watering can when it fits alongside the immediate withdrawal. */
    static LeprechaunWithdrawal withWater(Client c,LeprechaunWithdrawal primary,StepEngine.Action action,
        int compost,Map<Integer,Integer> bag,int wateringCan,int free,boolean needsCan,IntFunction<String> names)
    {
        if(action!=StepEngine.Action.COMPOST||!needsCan||wateringCan>0||compost<0)return primary;
        if(primary==null&&bag.getOrDefault(compost,0)==0)return null;
        if(free<(primary==null?1:2))return primary;
        int can=CropCare.storedCan(c,names);
        if(can<=0)return primary;
        LeprechaunWithdrawal water=plan(can,true,1,free,names.apply(can));
        return primary==null?water:new LeprechaunWithdrawal(primary.child,primary.quantity,primary.name,water);
    }
    /** Append a distinct exchange slot only when the whole collection fits.
     * A zero-quantity primary must first be resolved by the player. */
    static LeprechaunWithdrawal append(LeprechaunWithdrawal primary,LeprechaunWithdrawal extra,int free)
    {
        if(extra==null||extra.quantity<=0||free<extra.quantity)return primary;
        if(primary==null)return extra;
        if(primary.quantity<=0||primary.child==extra.child)return primary;
        LeprechaunWithdrawal tail=append(primary.companion,extra,free-primary.quantity);
        return tail==primary.companion?primary:new LeprechaunWithdrawal(primary.child,primary.quantity,primary.name,tail);
    }
    /** Upgrade an existing ordinary-compost withdrawal or add it to the
     * collection. Quantities are manual total counts, not menu operations. */
    static LeprechaunWithdrawal withCompost(LeprechaunWithdrawal primary,int item,int demand,int stock,int free,String name)
    {
        if(!CompostChoice.isOrdinary(item)||demand<=0||stock<=0||free<=0)return primary;
        int child=child(item,false),used=0,already=0;
        for(LeprechaunWithdrawal p=primary;p!=null;p=p.companion)
        {
            if(p.quantity==0)return primary;
            used+=p.quantity;
            if(p.child==child)already=p.quantity;
        }
        int quantity=Math.min(Math.min(demand,stock),Math.max(0,free-used+already));
        if(quantity<=already)return primary;
        if(already==0)return append(primary,new LeprechaunWithdrawal(child,quantity,name,null),free);
        return replaceQuantity(primary,child,quantity);
    }
    private static LeprechaunWithdrawal replaceQuantity(LeprechaunWithdrawal p,int child,int quantity)
    {
        if(p.child==child)return new LeprechaunWithdrawal(p.child,quantity,p.name,p.companion);
        return new LeprechaunWithdrawal(p.child,p.quantity,p.name,replaceQuantity(p.companion,child,quantity));
    }
    static LeprechaunWithdrawal plan(int item,boolean water,int stored,int free,String name)
    {
        int child=child(item,water);
        if(child<0||stored<=0)return null;
        return new LeprechaunWithdrawal(child,free>0?1:0,name,null);
    }
    /** Empty bucket widget IDs are not assumed. The overlay resolves the visible native item. */
    static LeprechaunWithdrawal buckets(int demand,int stored,int free)
    {
        if(demand<=0||stored<=0)return null;
        return new LeprechaunWithdrawal(-1,Math.min(Math.min(demand,stored),Math.max(0,free)),"empty bucket(s)",null);
    }
    String label()
    {
        if(quantity==0)return "Free a slot";
        if(quantity>1)return "Remove "+quantity+" total";
        return child==componentToId(FarmingTools.WATERINGCAN)||child==componentToId(FarmingTools.BOTTOMLESS_BUCKET)?"Remove 1":"Remove-1";
    }
    String instruction()
    {
        if(quantity==0)return "Make one free inventory slot and withdraw "+name+" from the tool leprechaun";
        if(quantity==1&&(companion==null||companion.quantity==1&&companion.companion==null))
            return "Withdraw 1 × "+name+(companion==null?"":" and 1 × "+companion.name)+" from the tool leprechaun";
        StringJoiner items=new StringJoiner(", ");
        for(LeprechaunWithdrawal p=this;p!=null;p=p.companion)items.add(p.quantity+" × "+p.name);
        return "Withdraw "+items+" from the tool leprechaun";
    }
}
