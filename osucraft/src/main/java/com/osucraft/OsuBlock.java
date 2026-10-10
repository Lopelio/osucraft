package com.osucraft;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bloque que abre osu! al hacerle click derecho. La pantalla se abre solo en el cliente. */
public class OsuBlock extends Block {
    public static final MapCodec<OsuBlock> CODEC = createCodec(OsuBlock::new);

    public OsuBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            OsuCraft.openMenuHook.run();
        }
        return ActionResult.SUCCESS;
    }
}
