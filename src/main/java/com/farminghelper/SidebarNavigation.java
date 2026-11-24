package com.farminghelper;

/** Screen ownership is independent of the running guide and of game ticks. */
final class SidebarNavigation
{
    enum Page {HOME,GUIDE,MANUAL,SETTINGS,GENERAL,GROUPS,PATCHES,PRIORITIES,STORAGE,ADVANCED,RUN_SETTINGS}
    private Page page=Page.HOME;
    private boolean awaiting;
    Page page(){return page;}
    void show(Page next){page=next;awaiting=false;}
    void starting(){awaiting=true;}
    boolean acceptGuide(){if(awaiting){page=Page.GUIDE;awaiting=false;}return page==Page.GUIDE;}
    void reset(){page=Page.HOME;awaiting=false;}
}
