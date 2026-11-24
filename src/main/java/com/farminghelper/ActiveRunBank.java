package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;

/** Native Bank Tags custom predicate, not persisted tags or a custom bank renderer. */
final class ActiveRunBank
{
    static final String TAG="farming helper active run";
    private final Client client;
    private final PluginManager plugins;
    private TagManager tags;
    private BankSearch search;
    private final ItemManager items;
    private Set<Integer> ids=Collections.emptySet(), admitted=Collections.emptySet();
    private int[] lastLayout=new int[0];
    private boolean registered,opened,offered,birdPhase;
    ActiveRunBank(Client client,PluginManager plugins,ItemManager items)
    {this.client=client;this.plugins=plugins;this.items=items;}
    private BankTagsPlugin service()
    {
        for(var p:plugins.getPlugins())if(p instanceof BankTagsPlugin&&plugins.isPluginActive(p))
        {
            // TagManager is deliberately bound inside the native plugin injector.
            // Reuse that exact instance without requiring Bank Tags as a dependency.
            TagManager actual=p.getInjector().getInstance(TagManager.class);
            if(tags!=actual){if(registered&&tags!=null)tags.unregisterTag(TAG);registered=false;tags=actual;admitted=Collections.emptySet();search=p.getInjector().getInstance(BankSearch.class);}
            return (BankTagsPlugin)p;
        }
        return null;
    }
    boolean bankOpen()
    {
        var root=client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        return client.getGameState()==GameState.LOGGED_IN&&root!=null&&!root.isHidden();
    }
    static boolean includes(Set<Integer> ids,int id,ItemComposition definition)
    {return ids.contains(id)&&definition!=null&&definition.getPlaceholderTemplateId()==-1;}
    private void register()
    {
        if(!registered){tags.registerTag(TAG,id->includes(admitted,id,items.getItemComposition(id))&&ids.contains(id));registered=true;}
    }
    /** One live ownership snapshot for both curation and positional layout.
     * Seed Vault stock and native bank placeholders are not bank shelf items. */
    static Map<Integer,Integer> ownedStock(Client client,IntFunction<ItemComposition> definition)
    {return ownedStock(client,definition,id->id);}
    static Map<Integer,Integer> ownedStock(Client client,IntFunction<ItemComposition> definition,IntUnaryOperator canonicalizeWorn)
    {
        Map<Integer,Integer> result=new HashMap<>(),worn=new HashMap<>();
        PhysicalStock.collect(result,client.getItemContainer(InventoryID.BANK),definition);
        PhysicalStock.collect(result,client.getItemContainer(InventoryID.INVENTORY),definition);
        PhysicalStock.collect(worn,client.getItemContainer(InventoryID.EQUIPMENT),definition);
        // Only normalize worn IDs for this visual shelf; exact supply stock is unchanged.
        worn.forEach((id,n)->result.merge(canonicalizeWorn.applyAsInt(id),n,Integer::sum));
        return Map.copyOf(result);
    }
    Map<Integer,Integer> ownedStock(){return ownedStock(client,items::getItemComposition,items::canonicalize);}
    private String name(int id){ItemComposition d=items.getItemComposition(id);return d==null?"":d.getName();}
    private int slot(int id){var s=items.getItemStats(id);return s==null||s.getEquipment()==null?-1:s.getEquipment().getSlot();}
    static Set<Integer> admittedIds(int[] layout)
    {
        Set<Integer> result=new HashSet<>();for(int id:layout)if(id>=0)result.add(id);
        return Set.copyOf(result);
    }
    private void open(BankTagsPlugin api,int[] layout)
    {
        // Publish only items already in the supplied layout. Native Bank Tags saves
        // a layout if a filtered item is missing from it. New IDs therefore cannot
        // enter through our predicate before their corresponding layout is ready.
        lastLayout=layout.clone();admitted=admittedIds(layout);
        api.openTag(TAG,new Layout(TAG,layout),BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM);
    }
    void tick(Set<Integer> next,Set<Integer> owned,boolean enabled,boolean bird)
    {
        if(!bankOpen()){release();return;}
        BankTagsPlugin api=service();
        if(!enabled||next==null||api==null){close(api);return;}
        boolean changed=updateEligible(next,owned);register();
        if(!opened){opened=true;offered=false;lastLayout=new int[0];}
        boolean stageChanged=birdPhase!=bird;birdPhase=bird;
        if(stageChanged)lastLayout=new int[0];
        if(offered&&!TAG.equals(api.getActiveTag()))return; // Respect manual browsing.
        int[] layout=BankShelfLayout.reconcile(lastLayout,ids,owned,this::name,this::slot,CuratedBank::transportCategory);
        if(!offered&&!ids.isEmpty()){offered=true;open(api,layout);}
        else if(!offered&&TAG.equals(api.getActiveTag()))api.closeBankTag();
        else if(offered&&(stageChanged||!Arrays.equals(layout,lastLayout)))open(api,layout);
        else if(offered&&changed&&search!=null)search.layoutBank();
    }
    void show(Set<Integer> next,Set<Integer> owned,boolean bird)
    {
        BankTagsPlugin api=service();
        if(api==null||next==null||next.isEmpty()||!bankOpen())return;
        updateEligible(next,owned);
        if(!opened||birdPhase!=bird)lastLayout=new int[0];birdPhase=bird;
        register();opened=true;offered=true;
        open(api,BankShelfLayout.reconcile(lastLayout,ids,owned,this::name,this::slot,CuratedBank::transportCategory));
    }
    private boolean updateEligible(Set<Integer> next,Set<Integer> owned)
    {
        Set<Integer> filtered=new HashSet<>(owned);filtered.retainAll(next);
        boolean changed=!ids.equals(filtered);ids=Set.copyOf(filtered);return changed;
    }
    void close(){close(bankOpen()?service():null);}
    private void close(BankTagsService api)
    {
        // Native Bank Tags can restore its remembered tag on reopen even after
        // our in-memory registration was released. Close only our exact tag.
        try{if(api!=null&&TAG.equals(api.getActiveTag())&&bankOpen())api.closeBankTag();}
        finally{release();}
    }
    private void release()
    {
        if(registered&&tags!=null)tags.unregisterTag(TAG);
        // Never save or delete another plugin's configuration. Registration is
        // in-memory; no actual TagTab or permanent item tag is created.
        registered=false;opened=false;offered=false;ids=Collections.emptySet();admitted=Collections.emptySet();lastLayout=new int[0];
    }
}
