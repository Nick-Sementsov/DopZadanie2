package io.github.nicksementsov.druzhina.registry;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.block.MusterTableBlock;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.item.BatonItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DruzhinaRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Druzhina.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Druzhina.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Druzhina.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Druzhina.MODID);

    public static final RegistryObject<EntityType<Soldier>> SOLDIER = ENTITIES.register("soldier", () -> EntityType.Builder.<Soldier>of(Soldier::new, MobCategory.CREATURE)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(10)
            .build(Druzhina.id("soldier").toString()));

    public static final RegistryObject<Block> MUSTER_TABLE = BLOCKS.register("muster_table", () -> new MusterTableBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5f)
            .sound(SoundType.WOOD)
            .noOcclusion()));

    public static final RegistryObject<Item> MUSTER_TABLE_ITEM = ITEMS.register("muster_table", () -> new BlockItem(MUSTER_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BATON = ITEMS.register("commander_baton", () -> new BatonItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.druzhina"))
            .icon(() -> new ItemStack(BATON.get()))
            .displayItems((params, out) -> {
                out.accept(MUSTER_TABLE_ITEM.get());
                out.accept(BATON.get());
            })
            .build());

    private DruzhinaRegistry() {
    }

    public static void register(IEventBus modBus) {
        DruzhinaAttributes.ATTRIBUTES.register(modBus);
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(SOLDIER.get(), Soldier.createAttributes().build()));
    }
}
