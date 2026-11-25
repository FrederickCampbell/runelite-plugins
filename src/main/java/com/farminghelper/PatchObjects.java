/*
 * Copyright (c) 2019 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.farminghelper;

import java.util.*;

/** Object IDs extracted from the explicit object-ID comments in the vendored
 * Quest Helper/RuneLite farming decoder. These are type candidates, not exact
 * location identities; PatchHighlight additionally verifies region, proximity
 * and patch grouping. Do not infer IDs from undocumented varbit ranges. */
final class PatchObjects
{
    private static final Map<PatchType,BitSet> byType=new EnumMap<>(PatchType.class);
    private static final BitSet all=new BitSet();
    static
    {
        // Inclusive endpoints of consecutive IDs explicitly listed in the decoder.
        ranges(PatchType.MUSHROOM, 8311, 8336);
        ranges(PatchType.HESPORI, 33722, 33730);
        ranges(PatchType.ALLOTMENT, 8535, 8549,  8558, 8576,  8580, 8686,  33666, 33692);
        ranges(PatchType.HERB, 8132, 8135,  8144, 8149,  9044, 9054,  14209, 14209,
            26825, 26829,  39748, 39811,  55347, 55351);
        ranges(PatchType.FLOWER, 7840, 7843,  7851, 7934,  33650, 33665);
        ranges(PatchType.BUSH, 7573, 7576,  7581, 7587,  7591, 7612,  7616, 7640,
            7644, 7670,  7674, 7697,  7701, 7721,  7725, 7742);
        ranges(PatchType.FRUIT_TREE, 7935, 7961,  7966, 8131,  34008, 34034);
        ranges(PatchType.HOPS, 8154, 8172,  8177, 8310,  58836, 58880);
        ranges(PatchType.TREE, 8392, 8468,  8473, 8534);
        ranges(PatchType.HARDWOOD_TREE, 30406, 30413,  30416, 30425,  30428, 30434,  30437, 30458,
            30476, 30479,  58712, 58719,  58722, 58731,  58734, 58740,
            58743, 58750,  58753, 58762,  58765, 58771,  58774, 58782,
            58784, 58794,  58796, 58803);
        ranges(PatchType.REDWOOD, 34047, 34050,  34061, 34065,  34070, 34070,  34079, 34079,
            34097, 34097,  34115, 34115,  34130, 34134,  34139, 34139,
            34148, 34148,  34166, 34166,  34184, 34184,  34205, 34210,
            34215, 34215,  34224, 34224,  34242, 34242,  34260, 34260,
            34278, 34278,  34297, 34297);
        ranges(PatchType.SPIRIT_TREE, 8339, 8356,  8358, 8368,  8370, 8381);
        ranges(PatchType.ANIMA, 33980, 33997,  33999, 34005);
        ranges(PatchType.BELLADONNA, 7557, 7571);
        ranges(PatchType.CACTUS, 7743, 7754,  7757, 7770,  33734, 33760);
        ranges(PatchType.CORAL, 58676, 58709);
        ranges(PatchType.SEAWEED, 30483, 30499);
        ranges(PatchType.CALQUAT, 7772, 7806);
        ranges(PatchType.CELASTRUS, 33695, 33709,  33711, 33715,  33717, 33721);
        ranges(PatchType.CRYSTAL_TREE, 34907, 34918);
    }
    private static void ranges(PatchType type,int... endpoints)
    {
        BitSet ids=new BitSet();
        for(int i=0;i<endpoints.length;i+=2)ids.set(endpoints[i],endpoints[i+1]+1);
        byType.put(type,ids);all.or(ids);
    }
    static boolean known(int id){return id>=0&&all.get(id);}
    static boolean matches(PatchType type,int id)
    {
        BitSet ids=byType.get(type);
        return id>=0&&ids!=null&&ids.get(id);
    }
}
