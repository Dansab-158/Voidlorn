package net.lobster.voidlorn;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// MODID here has to match the entry in META-INF/neoforge.mods.toml.
@Mod(Voidlorn.MODID)
public class Voidlorn {
    public static final String MODID = "voidlorn";
    public static final Logger LOGGER = LogUtils.getLogger();

    // This constructor runs once, when the mod is loaded. NeoForge injects the event bus and
    // mod container for us based on the parameter types.
    public Voidlorn(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        net.lobster.voidlorn.registry.ModWorldgen.register(modEventBus);

        net.lobster.voidlorn.registry.ModEntities.register(modEventBus);
        net.lobster.voidlorn.registry.ModEffects.register(modEventBus);
        net.lobster.voidlorn.registry.ModItems.register(modEventBus);
        net.lobster.voidlorn.registry.ModBlocks.register(modEventBus);
        net.lobster.voidlorn.registry.ModSounds.register(modEventBus);
        net.lobster.voidlorn.registry.ModAttachments.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(net.lobster.voidlorn.worldgen.WorldSeedInjector.class);
        modEventBus.register(net.lobster.voidlorn.worldgen.WorldgenTuning.class);
        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {}

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // No custom items to add to a creative tab yet.
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
