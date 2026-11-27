package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
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
    private final BankTagsService bankTags;
    private final TagManager tags;
    private final BankSearch search;
    private final ItemManager items;
    private Set<Integer> ids=Collections.emptySet(), admitted=Collections.emptySet();
    private int[] lastLayout=new int[0];
    private Set<Integer> layoutEligible=Set.of(),layoutOwned=Set.of();
    private List<Integer> layoutPriority=List.of();
    private boolean registered,opened,offered,birdPhase,stagePending;
    ActiveRunBank(Client client,PluginManager plugins,ItemManager items,BankTagsService bankTags,TagManager tags,BankSearch search)
    {
        this.client=client;this.plugins=plugins;this.items=items;
        this.bankTags=bankTags;this.tags=tags;this.search=search;
    }
    private BankTagsPlugin service()
    {
        // The explicit-layout overload is on the native plugin implementing the exported service.
        if(!(bankTags instanceof BankTagsPlugin))return null;
        BankTagsPlugin api=(BankTagsPlugin)bankTags;
        return plugins.isPluginActive(api)?api:null;
    }
    boolean bankOpen()
    {
        var root=client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        return client.getGameState()==GameState.LOGGED_IN&&root!=null&&!root.isHidden();
    }
    static boolean includes(Set<Integer> ids,int id,ItemComposition definition)
    {
        if (definition == null) return false;
        int base = definition.getPlaceholderTemplateId() == -1 ? id : definition.getPlaceholderId();
        return ids.contains(base);
    }
    private void register()
    {
        if(!registered){tags.registerTag(TAG,id->includes(admitted,id,items.getItemComposition(id)));registered=true;}
    }
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
    {tick(next,owned,enabled,bird,List.of());}
    void tick(Set<Integer> next,Set<Integer> owned,boolean enabled,boolean bird,List<Integer> priority)
    {
        if(!bankOpen()){if(opened||registered)release();return;}
        BankTagsPlugin api=service();
        if(!enabled||next==null||api==null){close(api);return;}
        updateEligible(next,owned);register();
        if(!opened){opened=true;offered=false;lastLayout=new int[0];}
        if(birdPhase!=bird){birdPhase=bird;stagePending=true;lastLayout=new int[0];}
        if(offered&&!TAG.equals(api.getActiveTag()))return; // Respect manual browsing.
        if(!offered&&ids.isEmpty())
        {if(TAG.equals(api.getActiveTag()))api.closeBankTag();return;}
        boolean changed=!layoutEligible.equals(ids)||!layoutOwned.equals(owned);
        boolean reprioritize=!layoutPriority.equals(priority);
        if(offered&&!stagePending&&!changed&&!reprioritize)return;
        if(reprioritize)lastLayout=new int[0];
        int[] layout=reconcile(owned,priority);
        if(!offered||stagePending||!Arrays.equals(layout,lastLayout)){offered=true;open(api,layout);}
        else if(changed&&search!=null)search.layoutBank();
        stagePending=false;
    }
    String show(Set<Integer> next,Set<Integer> owned,boolean bird,List<Integer> priority)
    {
        BankTagsPlugin api=service();
        if(api==null)return "Enable Bank Tags to show the farming layout.";
        if(!bankOpen())return "Open a bank, then choose Show farming bank.";
        updateEligible(next,owned);
        if(ids.isEmpty() && (!opened || birdPhase!=bird || lastLayout.length==0))return "No matching farming items or placeholders in this bank view.";
        if(!opened||birdPhase!=bird||!layoutPriority.equals(priority))lastLayout=new int[0];birdPhase=bird;
        register();opened=true;offered=true;
        open(api,reconcile(owned,priority));stagePending=false;
        return "";
    }
    private int[] reconcile(Set<Integer> owned,List<Integer> priority)
    {
        int[] layout=lastLayout.length==0?BankShelfLayout.build(ids,this::name,this::slot,CuratedBank::transportCategory,priority)
            :BankShelfLayout.reconcile(lastLayout,ids,owned,this::name,this::slot,CuratedBank::transportCategory);
        layoutEligible=ids;layoutOwned=Set.copyOf(owned);layoutPriority=List.copyOf(priority);return layout;
    }
    private void updateEligible(Set<Integer> next,Set<Integer> owned)
    {
        // Stable layouts are common. Check the intersection without copying the bank.
        int count=0;
        Set<Integer> smaller=owned.size()<next.size()?owned:next, larger=smaller==owned?next:owned;
        for(int id:smaller)if(larger.contains(id))
        {
            if(!ids.contains(id)){count=-1;break;}
            count++;
        }
        if(count==ids.size())return;
        Set<Integer> filtered=new HashSet<>(smaller);filtered.retainAll(larger);
        ids=Set.copyOf(filtered);
    }
    void bankClosed(){release();}
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
        registered=false;opened=false;offered=false;stagePending=false;ids=Collections.emptySet();admitted=Collections.emptySet();lastLayout=new int[0];layoutEligible=Set.of();layoutOwned=Set.of();layoutPriority=List.of();
    }
}
