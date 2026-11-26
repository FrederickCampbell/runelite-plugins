package com.farminghelper;

import java.util.Map;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;

/** Read-only withdrawal guidance for one immediate patch interaction. Native
 * storage counts follow farming_tools_getstored (script 1063). */
final class LeprechaunWithdrawal
{
    final int child,quantity;
    final String name;
    private final boolean single;
    final LeprechaunWithdrawal companion;
    private LeprechaunWithdrawal(int child,int quantity,String name,boolean single)
    {this(child,quantity,name,single,null);}
    private LeprechaunWithdrawal(int child,int quantity,String name,boolean single,LeprechaunWithdrawal companion)
    {this.child=child;this.quantity=quantity;this.name=name;this.single=single;this.companion=companion;}

    static int child(int item,boolean water)
    {
        if(water)return 12;
        switch(item)
        {
            case ItemID.RAKE:return 8;
            case ItemID.DIBBER:return 9;
            case ItemID.SPADE:return 10;
            case ItemID.SECATEURS:case ItemID.FAIRY_ENCHANTED_SECATEURS:return 11;
            case Supplies.GARDENING_TROWEL:return 13;
            case ItemID.PLANT_CURE:return 14;
            case ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED:return 15;
            case ItemID.BUCKET_COMPOST:return 17;
            case ItemID.BUCKET_SUPERCOMPOST:return 18;
            case ItemID.BUCKET_ULTRACOMPOST:return 19;
            default:return -1;
        }
    }
    static int stored(Client c,int item)
    {
        switch(item)
        {
            case ItemID.RAKE:return 2*c.getVarbitValue(8357)+c.getVarbitValue(1435);
            case ItemID.DIBBER:return 2*c.getVarbitValue(8358)+c.getVarbitValue(1436);
            case ItemID.SPADE:return 2*c.getVarbitValue(8361)+c.getVarbitValue(1437);
            case ItemID.SECATEURS:case ItemID.FAIRY_ENCHANTED_SECATEURS:
                return 2*c.getVarbitValue(8359)+c.getVarbitValue(1438);
            case Supplies.GARDENING_TROWEL:return 2*c.getVarbitValue(8360)+c.getVarbitValue(1440);
            case ItemID.PLANT_CURE:return c.getVarbitValue(6268);
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
        else if(item==ItemID.SECATEURS&&c.getVarbitValue(1848)==1)item=ItemID.FAIRY_ENCHANTED_SECATEURS;
        int stock=water?(item>0?1:0):stored(c,item);
        if(item<=0||stock<=0)return null;
        return plan(item,water,stock,free,names.apply(item));
    }
    static boolean magicStored(Client c)
    {return c.getVarbitValue(1848)==1&&stored(c,ItemID.FAIRY_ENCHANTED_SECATEURS)>0;}

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
        return primary==null?water:new LeprechaunWithdrawal(primary.child,primary.quantity,primary.name,primary.single,water);
    }
    static LeprechaunWithdrawal plan(int item,boolean water,int stored,int free,String name)
    {
        int child=child(item,water);
        if(child<0||stored<=0)return null;
        return new LeprechaunWithdrawal(child,free>0?1:0,name,child==12||child==15);
    }
    String label()
    {
        if(quantity==0)return "Free a slot";
        return single?"Remove 1":"Remove-1";
    }
    String instruction()
    {
        return quantity==0?"Make one free inventory slot and withdraw "+name+" from the tool leprechaun":
            "Withdraw 1 × "+name+(companion==null?"":" and 1 × "+companion.name)+" from the tool leprechaun";
    }
}
