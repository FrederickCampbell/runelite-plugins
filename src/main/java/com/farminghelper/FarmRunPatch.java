package com.farminghelper;

@lombok.Getter
@lombok.RequiredArgsConstructor
final class FarmRunPatch
{
	private final String location;
	private final String name;
	private final PatchType patchType;
	FarmRunType getFarmRunType()
	{
		return FarmRunType.forPatchType(patchType);
	}

	String key()
	{
		return location + "~" + patchType.name() + "~" + name;
	}

	String getDisplayName()
	{
		return name.isEmpty() ? location : location + " - " + name;
	}

    String guideLabel()
    {
        if (patchType == PatchType.HERB)
        {
            if (location.equals("Kourend")) return "Hosidius";
            if (location.equals("Civitas illa Fortis")) return "Varlamore";
        }
        return getDisplayName();
    }
}
