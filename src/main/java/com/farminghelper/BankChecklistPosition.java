package com.farminghelper;

public enum BankChecklistPosition
{
	AUTOMATIC("Automatic"),
	LEFT("Left"),
	RIGHT("Right");

	private final String displayName;

	BankChecklistPosition(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
