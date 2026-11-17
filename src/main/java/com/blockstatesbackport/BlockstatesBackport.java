package com.blockstatesbackport;

import net.fabricmc.api.ClientModInitializer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockstatesBackport implements ClientModInitializer {

	public static final String MOD_ID = "blockstates-backport";
	public static final Logger LOG = LogManager.getLogger("BlockstatesBackport");

	@Override
	public void onInitializeClient() {
		LOG.info("Blockstates Backport loaded");
	}
}
