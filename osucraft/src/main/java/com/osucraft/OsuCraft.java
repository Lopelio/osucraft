package com.osucraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class OsuCraft implements ModInitializer {
    public static final String MOD_ID = "osucraft";

    public static Block OSU_BLOCK;
    public static Item OSU_BLOCK_ITEM;

    /** Lo reemplaza el cliente al iniciar; en un servidor dedicado no hace nada. */
    public static volatile Runnable openMenuHook = () -> { };

    @Override
    public void onInitialize() {
        Identifier id = Identifier.of(MOD_ID, "osu_block");
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .mapColor(MapColor.PINK)
                .strength(2.0f, 6.0f)
                .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .luminance(state -> 8);
        OSU_BLOCK = Registry.register(Registries.BLOCK, blockKey, new OsuBlock(settings));

        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey();
        OSU_BLOCK_ITEM = Registry.register(Registries.ITEM, itemKey, new BlockItem(OSU_BLOCK, itemSettings));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(OSU_BLOCK_ITEM));
    }
}
