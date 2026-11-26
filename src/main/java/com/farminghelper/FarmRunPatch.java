package com.farminghelper;

final class FarmRunPatch
{
	@lombok.Getter private final String location;
	@lombok.Getter private final String name;
	@lombok.Getter private final PatchType patchType;
	private final String key;

	FarmRunPatch(String location, String name, PatchType patchType)
	{
		this.location = location;
		this.name = name;
		this.patchType = patchType;
		this.key = location + "~" + patchType.name() + "~" + name;
	}

	FarmRunType getFarmRunType()
	{
		return patchType.getRunType();
	}

	String key()
	{
		return key;
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
