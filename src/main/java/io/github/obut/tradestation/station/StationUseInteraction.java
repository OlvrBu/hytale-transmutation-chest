package io.github.obut.tradestation.station;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.TradeStationPlugin;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Runs when a player uses (F / right-click) a Trade Station block. The block's JSON refers to this
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
        TradeStationPlugin.get().getLogger().at(Level.INFO).log("%s used the Trade Station at (%d, %d, %d) in %s",
                player.getUsername(), targetBlock.x, targetBlock.y, targetBlock.z, world.getName());
        player.sendMessage(Message.raw("Trade Station at " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " (no menu yet)."));
    }

    // Client-side prediction hook. Nothing to predict: all station logic is server-side.
    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                                             @Nullable ItemStack itemInHand, @Nonnull World world,
                                             @Nonnull Vector3i targetBlock) {
    }
}
