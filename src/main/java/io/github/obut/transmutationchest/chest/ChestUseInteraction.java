package io.github.obut.transmutationchest.chest;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.transmutationchest.TransmutationChestPlugin;
import io.github.obut.transmutationchest.ui.ChestPage;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Runs when a player uses (F / right-click) a Transmutation Chest block: opens the trading page. The block's JSON refers to this
 * class by the name it is registered under ({@link #TYPE}) in its "Use" interaction.
 * <p>
 * The server creates instances from the block JSON via {@link #CODEC} (there's no constructor we
 * call ourselves), so dependencies are fetched from the plugin instance instead of injected.
 * <p>
 * {@link SimpleBlockInteraction} already resolves the target block and checks the player is close
 * enough to it before calling {@link #interactWithBlock}.
 */
public class ChestUseInteraction extends SimpleBlockInteraction {

    public static final String TYPE = "TransmutationChest_Use";

    // Describes how to build this interaction from JSON. It has no settings of its own, so it
    // only inherits the parent's (e.g. "UseLatestTarget").
    public static final BuilderCodec<ChestUseInteraction> CODEC = BuilderCodec.builder(
                    ChestUseInteraction.class, ChestUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens a Transmutation Chest.")
            .build();

    // Runs on the server, on the world thread.
    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull InteractionType type, @Nonnull InteractionContext context,
                                     @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock,
                                     @Nonnull CooldownHandler cooldownHandler) {
        PlayerRef player = commandBuffer.getComponent(context.getEntity(), PlayerRef.getComponentType());
        if (player == null) {
            return; // not a player (e.g. an NPC)
        }
        Vector3i pos = ChestAccess.resolveMainBlock(world, targetBlock.x, targetBlock.y, targetBlock.z);
        var chestRef = ChestAccess.findChest(world, pos.x, pos.y, pos.z);
        if (chestRef == null) {
            TransmutationChestPlugin.get().getLogger().at(Level.WARNING).log(
                    "%s used a Transmutation Chest at (%d, %d, %d) that has no chest data", player.getUsername(), pos.x, pos.y, pos.z);
            return;
        }
        Player playerComponent = commandBuffer.getComponent(context.getEntity(), Player.getComponentType());
        if (playerComponent == null || playerComponent.getPageManager().getCustomPage() != null) {
            return; // another page is already open
        }
        TransmutationChestPlugin plugin = TransmutationChestPlugin.get();
        plugin.getLogger().at(Level.FINE).log("%s opened the Transmutation Chest at (%d, %d, %d) in %s",
                player.getUsername(), pos.x, pos.y, pos.z, world.getName());
        ChestPage page = new ChestPage(player, world, pos, plugin.getConfigManager(), plugin.getTradeService(),
                plugin.getChestViewers(), plugin.getRecentItems());
        playerComponent.getPageManager().openCustomPage(context.getEntity(), commandBuffer.getStore(), page);
    }

    // Client-side prediction hook. Nothing to predict: all chest logic is server-side.
    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                                             @Nullable ItemStack itemInHand, @Nonnull World world,
                                             @Nonnull Vector3i targetBlock) {
    }
}
