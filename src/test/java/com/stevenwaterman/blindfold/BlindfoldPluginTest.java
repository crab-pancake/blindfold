package com.stevenwaterman.blindfold;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import com.stevenwaterman.blindfold.BlindfoldPlugin;

public class BlindfoldPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BlindfoldPlugin.class);
		RuneLite.main(args);
	}
}