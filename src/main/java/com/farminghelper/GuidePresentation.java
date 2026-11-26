package com.farminghelper;

/** Display-only wording and progress. Never changes run decisions. */
final class GuidePresentation
{
    static final long STORAGE_FRESH_MS=30L*60*1000;
    static final String STORAGE_HELP="Farming Helper remembers your last observed storage. Open the bank and Seed Vault regularly so crop choices, quantities and travel suggestions stay accurate. Refresh after moving supplies. Missing items never prevent starting.";
    static final String ITEMS_HELP="Carried quantities are compared with the supplies for this run. Last seen shows positive quantities observed in storage. You can start or continue with missing items.";
    static final String TRAVEL_HELP="Optional travel items you own for the remaining patches. Bring enough charges. Shortest Path handles navigation; these items are not required.";
    static final String PREPARATION_HELP="Collect what you need, then start or continue. You can use any accessible storage. Missing items do not block the run. Farming tools may be stored with a tool leprechaun.";

    static boolean stale(long observed,long now)
    {return observed<=0||now-observed>=STORAGE_FRESH_MS||now<observed;}

    static String storageWarning(boolean bankStale,boolean vaultStale)
    {
        if(bankStale&&vaultStale)return "Open bank and Seed Vault to refresh stock.";
        if(bankStale)return "Open your bank to refresh stock.";
        if(vaultStale)return "Open the Seed Vault to refresh stock.";
        return "";
    }

    static String preparation(boolean route,String destination,boolean satisfied)
    {
        if(satisfied)return "Supplies ready. Start when ready.";
        return route?"Collect supplies: "+destination+".":"Collect your remaining supplies.";
    }

    static int done(GuideView.Stats stats,int fallback)
    {return stats.processed<0?fallback:stats.processed+stats.preserved;}
    static int remaining(GuideView.Stats stats,int fallback)
    {return stats.pending<0?fallback:stats.pending;}

    static String routeWarning(String status)
    {
        if(status.startsWith("No verified route")||status.startsWith("Route check did not finish"))
            return "Follow the travel instructions.";
        return "";
    }

    private GuidePresentation(){}
}
