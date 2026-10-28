package com.nick.prestol.mixin.hyw;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "ydmsama.hundred_years_war.main.entity.pathing.GroupPathingManager$SharedPath", remap = false)
public interface SharedPathAccess {
   @Accessor("nodes")
   List<BlockPos> prestol$nodes();
}
