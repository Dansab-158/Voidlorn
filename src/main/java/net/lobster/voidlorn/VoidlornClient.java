package net.lobster.voidlorn;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only - this never loads on a dedicated server, so it's safe to touch client classes here.
@Mod(value = Voidlorn.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Voidlorn.MODID, value = Dist.CLIENT)
public class VoidlornClient {
    public VoidlornClient(ModContainer container) {
        // Lets players open a config screen from Mods > Voidlorn > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
    }
}
