package com.garous.pandora;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pandora implements ModInitializer {
	public static final String MOD_ID = "pandora";
	public static final String MOD_NAME = "Pandora";
	public static final String VERSION = "B1";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[Pandora] {} {} initialized.", MOD_NAME, VERSION);
	}
}
