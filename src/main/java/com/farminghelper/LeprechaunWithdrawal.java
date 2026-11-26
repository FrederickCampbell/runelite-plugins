package com.farminghelper;

import net.runelite.api.gameval.ItemID;

/** Advisory only: the native exchange widget remains entirely player-controlled. */
final class LeprechaunWithdrawal
{
    final int child,quantity;
    final String name;
    private final boolean single;
    private LeprechaunWithdrawal(int child,int quantity,String name,boolean single)
    {this.child=child;this.quantity=quantity;this.name=name;this.single=single;}

    static LeprechaunWithdrawal plan(int item,boolean water,int remaining,int stored,int free,String name)
    {
        int child=water?12:item==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED?15:
            item==ItemID.BUCKET_COMPOST?17:item==ItemID.BUCKET_SUPERCOMPOST?18:19;
        boolean single=water||child==15;
        int wanted=single?1:Math.max(1,remaining);
        int quantity=Math.max(0,Math.min(wanted,Math.min(stored,free)));
        return new LeprechaunWithdrawal(child,quantity,name,single);
    }
    String label()
    {
        if(quantity==0)return "Free a slot";
        if(single)return "Remove 1";
        if(quantity==1||quantity==5)return "Remove-"+quantity;
        return "Remove-X: "+quantity;
    }
    String instruction()
    {
        return quantity==0?"Make one free inventory slot and withdraw "+name+" from the tool leprechaun":
            "Withdraw "+quantity+" × "+name+" from the tool leprechaun";
    }
}
