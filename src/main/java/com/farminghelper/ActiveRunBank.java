package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

/** Native Bank Tags custom predicate, not persisted tags or a custom bank renderer. */
final class ActiveRunBank
{
    static final String TAG="farming helper active run";
    private final Client client;
    private final PluginManager plugins;
    private TagManager tags;
    private LayoutManager layouts;
    private BankSearch search;
    private final ItemManager items;
    private Set<Integer> ids=Collections.emptySet();
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
            if(tags!=actual){if(registered&&tags!=null)tags.unregisterTag(TAG);registered=false;tags=actual;layouts=p.getInjector().getInstance(LayoutManager.class);search=p.getInjector().getInstance(BankSearch.class);}
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
        if(!registered){tags.registerTag(TAG,id->includes(ids,id,items.getItemComposition(id)));registered=true;}
    }
    private Set<Integer> physical()
    {
        Set<Integer> result=new HashSet<>();
        ItemContainer bank=client.getItemContainer(InventoryID.BANK);
        if(bank!=null)for(Item item:bank.getItems())if(item!=null&&PhysicalStock.available(item.getId(),item.getQuantity(),items::getItemComposition))result.add(item.getId());
        return Set.copyOf(result);
    }
    private String name(int id){ItemComposition d=items.getItemComposition(id);return d==null?"":d.getName();}
    private int slot(int id){var s=items.getItemStats(id);return s==null||s.getEquipment()==null?-1:s.getEquipment().getSlot();}
    private void open(BankTagsPlugin api,int[] layout)
    {
        // Native in-memory layout, no user tag or layout config write by this plugin.
        lastLayout=layout.clone();api.openTag(TAG,new Layout(TAG,layout),BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM);
    }
    void tick(Set<Integer> next,boolean enabled,boolean bird)
    {
        BankTagsPlugin api=service();
        if(!enabled||next==null||api==null){close();return;}
        if(!bankOpen()){opened=false;offered=false;lastLayout=new int[0];return;}
        Set<Integer> physical=physical();Set<Integer> filtered=new HashSet<>(physical);filtered.retainAll(next);
        boolean changed=!ids.equals(filtered);ids=Set.copyOf(filtered);register();
        if(!opened){opened=true;offered=false;lastLayout=new int[0];}
        boolean stageChanged=birdPhase!=bird;birdPhase=bird;
        if(stageChanged)lastLayout=new int[0];
        if(offered&&!TAG.equals(api.getActiveTag()))return; // Respect manual browsing.
        int[] layout=BankShelfLayout.reconcile(lastLayout,ids,physical,this::name,this::slot,CuratedBank::transportCategory);
        if(!offered&&!ids.isEmpty()){offered=true;open(api,layout);}
        else if(offered&&(stageChanged||!Arrays.equals(layout,lastLayout)))open(api,layout);
        else if(offered&&changed&&search!=null)search.layoutBank();
    }
    void show(Set<Integer> next,boolean bird)
    {
        BankTagsPlugin api=service();
        if(api==null||next==null||next.isEmpty()||!bankOpen())return;
        Set<Integer> physical=physical();Set<Integer> filtered=new HashSet<>(physical);filtered.retainAll(next);ids=Set.copyOf(filtered);
        if(!opened||birdPhase!=bird)lastLayout=new int[0];birdPhase=bird;
        register();opened=true;offered=true;
        open(api,BankShelfLayout.reconcile(lastLayout,ids,physical,this::name,this::slot,CuratedBank::transportCategory));
    }
    void close()
    {
        BankTagsService api=service();
        if(registered&&api!=null&&TAG.equals(api.getActiveTag()))api.closeBankTag();
        if(registered&&tags!=null)tags.unregisterTag(TAG);
        // Native Bank Tags may save a layout if it reconciles a changed bank between ticks.
        // Remove only our reserved temporary tag's layout, never another user's tag.
        if(registered&&layouts!=null)layouts.removeLayout(TAG);
        registered=false;opened=false;offered=false;ids=Collections.emptySet();lastLayout=new int[0];
    }
}
