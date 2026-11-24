package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiseaseInterruptTest
{
    private FarmRunPatch patch(String name){return new FarmRunPatch("Catherby",name,PatchType.ALLOTMENT);}
    @Test public void prioritySticksUntilCuredOrNoLongerLocal()
    {
        DiseaseInterrupt priority=new DiseaseInterrupt();
        FarmRunPatch a=patch("A"),b=patch("B");
        assertSame(a,priority.select(List.of(a),p->20));
        assertSame(a,priority.select(List.of(b,a),p->p==b?1:20));
        assertSame(b,priority.select(List.of(b),p->1));
        assertNull(priority.select(List.of(),p->1));
        assertSame(a,priority.select(List.of(a),p->1));
    }
    @Test public void skipOnlyDismissesInterruptAndResetAllowsNextRun()
    {
        DiseaseInterrupt priority=new DiseaseInterrupt();
        FarmRunPatch a=patch("A"),b=patch("B");
        assertSame(b,priority.select(List.of(a,b),p->p==b?1:5));
        priority.skip(b);
        assertSame(a,priority.select(List.of(a,b),p->p==b?1:5));
        priority.skip(a);
        assertNull(priority.select(List.of(a,b),p->1));
        priority.reset();
        assertSame(b,priority.select(List.of(b),p->1));
    }
}
