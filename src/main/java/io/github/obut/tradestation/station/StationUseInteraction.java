package io.github.obut.tradestation.station;

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
import io.github.obut.tradestation.TradeStationPlugin;
import io.github.obut.tradestation.ui.StationPage;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Runs when a player uses (F / right-click) a Trade Station block: opens the trading page. The block's JSON refers to this
 * class by the name it is registered under ({@link #TYPE}) in its "Use" interaction.
 * <p>
 * The server creates instances from the block JSON via {@link #CODEC} (there's no constructor we
 * call ourselves), so dependencies are fetched from the plugin instance instead of injected.
 * <p>
 * {@link SimpleBlockInteraction} already resolves the target block and checks the player is close
 * enough to it before calling {@link #interactWithBlock}.
 */
public class StationUseInteraction extends SimpleBlockInteraction {

    public static final String TYPE = "TradeStation_Use";

    // Describes how to build this interaction from JSON. It has no settings of its own, so it
    // only inherits the parent's (e.g. "UseLatestTarget").
    public static final BuilderCodec<StationUseInteraction> CODEC = BuilderCodec.builder(
                    StationUseInteraction.class, StationUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens a Trade Station.")
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
        Vector3i pos = StationAccess.resolveMainBlock(world, targetBlock.x, targetBlock.y, targetBlock.z);
        var stationRef = StationAccess.findStation(world, pos.x, pos.y, pos.z);
        if (stationRef == null) {
            TradeStationPlugin.get().getLogger().at(Level.WARNING).log(
                    "%s used a Trade Station at (%d, %d, %d) that has no station data", player.getUsername(), pos.x, pos.y, pos.z);
            return;
        }
        Player playerComponent = commandBuffer.getComponent(context.getEntity(), Player.getComponentType());
        if (playerComponent == null || playerComponent.getPageManager().getCustomPage() != null) {
            return; // another page is already open
        }
        TradeStationPlugin plugin = TradeStationPlugin.get();
        plugin.getLogger().at(Level.FINE).log("%s opened the Trade Station at (%d, %d, %d) in %s",
                player.getUsername(), pos.x, pos.y, pos.z, world.getName());
        StationPage page = new StationPage(player, world, pos, plugin.getConfigManager(), plugin.getTradeService(),
                plugin.getStationViewers());
        playerComponent.getPageManager().openCustomPage(context.getEntity(), commandBuffer.getStore(), page);
    }

    // Client-side prediction hook. Nothing to predict: all station logic is server-side.
    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                                             @Nullable ItemStack itemInHand, @Nonnull World world,
                                             @Nonnull Vector3i targetBlock) {
    }
}
