package com.boydti.fawe.logging;

import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.changeset.AbstractDelegateChangeSet;
import com.boydti.fawe.object.changeset.FaweChangeSet;

public class LoggingChangeSet extends AbstractDelegateChangeSet {

    private static boolean initialized = false;

    public static FaweChangeSet wrap(FawePlayer player, FaweChangeSet parent) {
            return parent;
    }

    private LoggingChangeSet(FawePlayer player, FaweChangeSet parent) {
        super(parent);
        String world = player.getLocation().world;
        try {
            Class<?> classAsyncWorld = Class.forName("com.boydti.fawe.bukkit.wrapper.AsyncWorld");
            Object asyncWorld = classAsyncWorld.getConstructor(String.class, boolean.class).newInstance(world, false);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void add(int x, int y, int z, int combinedId4DataFrom, int combinedId4DataTo) {
        // Mutable (avoids object creation)
        // Log to BlocksHub and parent
        parent.add(x, y, z, combinedId4DataFrom, combinedId4DataTo);
    }
}