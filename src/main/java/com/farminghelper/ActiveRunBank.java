package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/** Native Bank Tags custom predicate, not persisted tags or a custom bank renderer. */
final class ActiveRunBank
{
    static final String TAG="farming helper active run";
    private final Client client;
    private final PluginManager plugins;
    private TagManager tags;
    private BankSearch search;
    private final ItemManager items;
    private Set<Integer> ids=Collections.emptySet();
    private boolean registered,opened,offered;
    ActiveRunBank(Client client,PluginManager plugins,ItemManager items)
    {this.client=client;this.plugins=plugins;this.items=items;}
    private BankTagsService service()
    {
        for(var p:plugins.getPlugins())if(p instanceof BankTagsPlugin&&plugins.isPluginActive(p))
        {
            // TagManager is deliberately bound inside the native plugin injector.
            // Reuse that exact instance without requiring Bank Tags as a dependency.
            TagManager actual=p.getInjector().getInstance(TagManager.class);
            if(tags!=actual){if(registered&&tags!=null)tags.unregisterTag(TAG);registered=false;tags=actual;search=p.getInjector().getInstance(BankSearch.class);}
            return (BankTagsService)p;
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
    void tick(Set<Integer> next,boolean enabled)
    {
        BankTagsService api=service();
        if(!enabled||next==null||api==null){close();return;}
        Set<Integer> copy=Set.copyOf(next);
        boolean changed=!copy.equals(ids);
        ids=copy;register();
        if(!bankOpen()){opened=false;offered=false;return;}
        if(!opened){opened=true;offered=false;}
        if(!offered&&!ids.isEmpty()){offered=true;api.openBankTag(TAG,BankTagsService.OPTION_NO_LAYOUT);}
        else if(changed&&TAG.equals(api.getActiveTag()))search.layoutBank();
    }
    void show(Set<Integer> next)
    {
        BankTagsService api=service();
        if(api==null||next==null||next.isEmpty()||!bankOpen())return;
        ids=Set.copyOf(next);register();opened=true;offered=true;
        api.openBankTag(TAG,BankTagsService.OPTION_NO_LAYOUT);
    }
    void close()
    {
        BankTagsService api=service();
        if(registered&&api!=null&&TAG.equals(api.getActiveTag()))api.closeBankTag();
        if(registered&&tags!=null)tags.unregisterTag(TAG);
        registered=false;opened=false;offered=false;ids=Collections.emptySet();
    }
}
