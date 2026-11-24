package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** Geographical grouping and open-route ordering only. No teleport or pathfinding database.
 * Ordering techniques adapted from elzerma's Farm Run Autopilot RoutePlanner (BSD-2-Clause,
 * see LICENSE-FARM-RUN-AUTOPILOT): Held-Karp, nearest neighbour, 2-opt, or-opt.
 * The proxy is a bounded spatial score, NOT an estimate of travel time. Native Shortest Path
 * may choose the first onward cluster from a bounded set, before the order is frozen. */
final class RunOrder
{
    static final int EXACT_LIMIT=12, PROBE_LIMIT=4;
    static String site(FarmRunPatch p)
    {
        String loc=p.getLocation(); PatchType t=p.getPatchType();
        switch(loc)
        {
            case "Falador": return t==PatchType.TREE?"Falador Park":"South Falador";
            case "Ardougne": return t==PatchType.BUSH?"Ardougne Monastery":"North Ardougne";
            case "Kourend": return t==PatchType.GRAPEVINE?"Hosidius Vinery":"Hosidius";
            case "Seaweed": return "Fossil Island";
            default: return loc;
        }
    }
    private static boolean interior(FarmRunPatch p)
    {return p.getPatchType()==PatchType.HESPORI || p.getPatchType()==PatchType.SEAWEED || p.getPatchType()==PatchType.CORAL;}
    static WorldPoint surface(FarmRunPatch p)
    {
        switch(p.getPatchType())
        {
            case HESPORI:return SpecialRoutes.HESPORI_ENTRANCE;
            case SEAWEED:return SpecialRoutes.SEAWEED_ISLAND;
            case CORAL:return SpecialRoutes.CORAL_ENTRANCE;
            default:return Waypoints.destination(p);
        }
    }
    static final class Group
    {
        final String site;final List<FarmRunPatch> patches;
        Group(String site,List<FarmRunPatch> patches){this.site=site;this.patches=List.copyOf(patches);}
        WorldPoint entry(){return surface(patches.get(0));}
        WorldPoint exit(){return surface(patches.get(patches.size()-1));}
        boolean nativeCandidate(){return patches.stream().noneMatch(p->interior(p)||SpecialRoutes.anglers(p));}
    }
    static List<Group> groups(List<FarmRunPatch> patches,WorldPoint start)
    {
        Map<String,List<FarmRunPatch>> groups=new LinkedHashMap<>();
        for(FarmRunPatch p:patches)groups.computeIfAbsent(site(p),k->new ArrayList<>()).add(p);
        List<Group> result=new ArrayList<>();
        for(Map.Entry<String,List<FarmRunPatch>> entry:groups.entrySet())
        {
            // Complete surface work first, then enter a cave/underwater area once.
            List<FarmRunPatch> ordered=new ArrayList<>(entry.getValue());
            ordered.sort(Comparator.comparingInt(p->interior(p)?3:p.getPatchType()==PatchType.COMPOST_BIN?2:
                "Gnome Stronghold".equals(entry.getKey())&&p.getPatchType()==PatchType.FRUIT_TREE?0:1));
            // Sort the birdhouse subset without disturbing other patch types or the interior-last rule.
            List<FarmRunPatch> birds=new ArrayList<>();for(FarmRunPatch p:ordered)if(p.getPatchType()==PatchType.BIRDHOUSE)birds.add(p);
            birds.sort(Comparator.comparingInt(BirdhouseRun::order));
            int bird=0;for(int i=0;i<ordered.size();i++)if(ordered.get(i).getPatchType()==PatchType.BIRDHOUSE)ordered.set(i,birds.get(bird++));
            result.add(new Group(entry.getKey(),ordered));
        }
        return result;
    }
    // Nearby sites are preferred. Distant sites have a bounded proxy because
    // map distance across continents is not a credible teleport/travel time.
    static double proxy(WorldPoint a,WorldPoint b)
    {
        if(a==null||b==null)return 110;
        int d=Math.max(Math.abs(a.getX()-b.getX()),Math.abs(a.getY()-b.getY()));
        return Math.min(100,d)+(a.getPlane()==b.getPlane()?0:10);
    }
    static List<Group> plan(List<Group> groups,WorldPoint start)
    {
        int n=groups.size();if(n<2)return List.copyOf(groups);
        double[] first=new double[n];double[][] legs=new double[n][n];
        for(int i=0;i<n;i++)
        {
            first[i]=proxy(start,groups.get(i).entry());
            for(int j=0;j<n;j++)legs[i][j]=proxy(groups.get(i).exit(),groups.get(j).entry());
        }
        int[] order=optimize(first,legs);
        List<Group> result=new ArrayList<>();for(int i:order)result.add(groups.get(i));return result;
    }
    /** Guild is a fixed first cluster, not a destination visited again after leaving. */
    static List<Group> preset(List<FarmRunPatch> patches,WorldPoint start,List<String> reference,boolean optimize)
    {
        List<Group> all=groups(patches,start);
        all.sort(Comparator.comparingInt(g->{int i=reference.indexOf(g.site);return i<0?Integer.MAX_VALUE:i;}));
        Group guild=all.stream().filter(g->g.site.equals("Farming Guild")).findFirst().orElse(null);
        if(guild!=null)all.remove(guild);
        List<Group> result=new ArrayList<>();if(guild!=null)result.add(guild);
        // Reference order is the stable fallback. The spatial optimizer can improve
        // manual runs; native Shortest Path can refine the next stop in any group run.
        result.addAll(optimize?plan(all,guild==null?start:guild.exit()):all);
        return result;
    }
    static int[] optimize(double[] first,double[][] legs)
    {return first.length<=EXACT_LIMIT?exact(first,legs):heuristic(first,legs);}
    private static int[] exact(double[] first,double[][] legs)
    {
        int n=first.length;if(n==0)return new int[0];int full=(1<<n)-1;
        double[][] best=new double[full+1][n];int[][] prev=new int[full+1][n];
        for(double[] row:best)Arrays.fill(row,Double.POSITIVE_INFINITY);
        for(int j=0;j<n;j++){best[1<<j][j]=first[j];prev[1<<j][j]=-1;}
        for(int mask=1;mask<=full;mask++)for(int j=0;j<n;j++)if((mask&(1<<j))!=0 && Double.isFinite(best[mask][j]))
            for(int k=0;k<n;k++)if((mask&(1<<k))==0)
            {int next=mask|(1<<k);double c=best[mask][j]+legs[j][k];if(c<best[next][k]){best[next][k]=c;prev[next][k]=j;}}
        int last=0;for(int j=1;j<n;j++)if(best[full][j]<best[full][last])last=j;
        int[] order=new int[n];int mask=full;
        for(int k=n-1;k>=0;k--){order[k]=last;int prior=prev[mask][last];mask^=1<<last;last=prior;}
        return order;
    }
    static double cost(int[] order,double[] first,double[][] legs)
    {if(order.length==0)return 0;double c=first[order[0]];for(int i=1;i<order.length;i++)c+=legs[order[i-1]][order[i]];return c;}
    private static int[] heuristic(double[] first,double[][] legs)
    {
        int n=first.length;int[] best=null;double bestCost=Double.POSITIVE_INFINITY;
        // Bound work on the client thread: use four candidate starts and four improvement passes.
        Integer[] starts=new Integer[n];for(int i=0;i<n;i++)starts[i]=i;
        Arrays.sort(starts,Comparator.comparingDouble(i->first[i]));
        for(int s=0;s<Math.min(4,n);s++)
        {
            int[] order=new int[n];boolean[] used=new boolean[n];order[0]=starts[s];used[order[0]]=true;
            for(int k=1;k<n;k++){int next=-1;for(int j=0;j<n;j++)if(!used[j]&&(next<0||legs[order[k-1]][j]<legs[order[k-1]][next]))next=j;order[k]=next;used[next]=true;}
            double value=cost(order,first,legs);
            for(int pass=0;pass<4;pass++)
            {
                double before=value;
                for(int i=0;i<n-1;i++)for(int j=i+1;j<n;j++)
                {reverse(order,i,j);double c=cost(order,first,legs);if(c+1e-9<value)value=c;else reverse(order,i,j);}
                for(int len=1;len<=3;len++)for(int i=0;i+len<=n;i++)for(int at=0;at<=n-len;at++)if(at!=i)
                {int[] candidate=move(order,i,len,at);double c=cost(candidate,first,legs);if(c+1e-9<value){order=candidate;value=c;}}
                if(value>=before-1e-9)break;
            }
            if(value<bestCost){best=order;bestCost=value;}
        }
        return best;
    }
    private static void reverse(int[] a,int i,int j){while(i<j){int t=a[i];a[i++]=a[j];a[j--]=t;}}
    private static int[] move(int[] a,int start,int length,int at)
    {int[] out=new int[a.length];int k=0;for(int i=0;i<a.length;i++)if(i<start||i>=start+length)out[k++]=a[i];System.arraycopy(out,at,out,at+length,k-at);System.arraycopy(a,start,out,at,length);return out;}

    static List<FarmRunPatch> flatten(List<Group> groups)
    {List<FarmRunPatch> result=new ArrayList<>();for(Group g:groups)result.addAll(g.patches);return result;}
    /** A single native multi-target search, from the first cluster's exit. We only
     * apply a result before leaving that cluster, never move the active cluster. */
    static List<WorldPoint> candidates(List<Group> order)
    {
        List<WorldPoint> out=new ArrayList<>();if(order.size()<3||!order.get(0).nativeCandidate())return out;
        for(int i=1;i<order.size()&&out.size()<PROBE_LIMIT;i++)
        {Group g=order.get(i);WorldPoint p=g.entry();if(g.nativeCandidate()&&p!=null&&!out.contains(p))out.add(p);}
        return out.size()<2?Collections.emptyList():out;
    }
    static List<Group> preferNext(List<Group> order,WorldPoint target)
    {
        if(!candidates(order).contains(target))return order;
        List<Group> tail=new ArrayList<>(order.subList(1,order.size()));
        Group next=tail.stream().filter(g->g.nativeCandidate()&&Objects.equals(g.entry(),target)).findFirst().orElse(null);
        if(next==null)return order;
        tail.remove(next);List<Group> result=new ArrayList<>();result.add(order.get(0));result.add(next);result.addAll(plan(tail,next.exit()));return result;
    }
}
