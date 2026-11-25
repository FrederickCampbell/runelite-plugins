package com.farminghelper;

@lombok.Getter
@lombok.RequiredArgsConstructor
final class Crop
{
	private final PatchType patchType;
	private final String name;
	private final String itemName;
	private final int level;
	private final int itemId;
	private final int quantity;

	@Override
	public boolean equals(Object other)
	{
		return other instanceof Crop && ((Crop) other).itemId == itemId;
	}

	@Override
	public int hashCode()
	{
		return itemId;
	}
}
