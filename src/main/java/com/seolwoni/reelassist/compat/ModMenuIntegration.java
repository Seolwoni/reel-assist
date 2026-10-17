package com.seolwoni.reelassist.compat;

import com.seolwoni.reelassist.gui.ReelConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ReelConfigScreen::new;
	}
}
