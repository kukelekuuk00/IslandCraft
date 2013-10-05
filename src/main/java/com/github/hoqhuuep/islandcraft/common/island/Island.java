package com.github.hoqhuuep.islandcraft.common.island;

import java.util.Collections;
import java.util.List;

import com.github.hoqhuuep.islandcraft.common.Geometry;
import com.github.hoqhuuep.islandcraft.common.api.ICDatabase;
import com.github.hoqhuuep.islandcraft.common.api.ICPlayer;
import com.github.hoqhuuep.islandcraft.common.api.ICProtection;
import com.github.hoqhuuep.islandcraft.common.api.ICServer;
import com.github.hoqhuuep.islandcraft.common.api.ICWorld;
import com.github.hoqhuuep.islandcraft.common.type.ICLocation;
import com.github.hoqhuuep.islandcraft.common.type.ICRegion;
import com.github.hoqhuuep.islandcraft.common.type.ICType;

public class Island {
	private final ICDatabase database;
	private final ICServer server;
	private final int maxIslands;
	private final String purchaseItem;
	private final int purchaseCostAmount;
	private final int purchaseCostIncrease;
	private final String taxItem;
	private final int taxCostAmount;
	private final int taxCostIncrease;
	private final int taxInitial;
	private final int taxIncrease;
	private final int taxMax;
	private final ICProtection protection;

	public Island(final ICDatabase database, final ICProtection protection, final ICServer server, final int maxIslands, final String purchaseItem,
			final int purchaseCostAmount, final int purchaseCostIncrease, final String taxItem, final int taxCostAmount, final int taxCostIncrease) {
		this.database = database;
		this.protection = protection;
		this.server = server;
		this.maxIslands = maxIslands;
		this.purchaseItem = purchaseItem;
		this.purchaseCostAmount = purchaseCostAmount;
		this.purchaseCostIncrease = purchaseCostIncrease;
		this.taxItem = taxItem;
		this.taxCostAmount = taxCostAmount;
		this.taxCostIncrease = taxCostIncrease;
		// TODO load from configuration
		taxInitial = 500;
		taxIncrease = 500;
		taxMax = 2000;
	}

	/**
	 * To be called when a chunk is loaded. Creates WorldGuard regions if they
	 * do not exist.
	 * 
	 * @param x
	 * @param z
	 */
	public void onLoad(final ICLocation location, final long worldSeed) {
		final ICWorld world = server.findOnlineWorld(location.getWorld());
		if (world == null) {
			// Not ready
			return;
		}
		final Geometry geometry = world.getGeometry();
		if (geometry == null) {
			// Not an IslandCraft world
			return;
		}
		for (final ICLocation island : geometry.getOuterIslands(location)) {
			final ICRegion region = geometry.outerRegion(island);
			final ICType type = database.loadIslandType(island);
			if (type == null) {
				if (geometry.isSpawn(island)) {
					database.saveIsland(island, ICType.RESERVED, null, "Spawn Island", -1);
				} else if (geometry.isResource(island, worldSeed)) {
					database.saveIsland(island, ICType.RESOURCE, null, "Resource Island", -1);
				} else {
					database.saveIsland(island, ICType.NEW, null, "New Island", -1);
				}
			}
			if (type == ICType.ABANDONED || type == ICType.NEW || type == ICType.REPOSSESSED || type == ICType.RESERVED) {
				protection.setReserved(region);
			} else if (type == ICType.PRIVATE) {
				final String owner = database.loadIslandOwner(island);
				protection.setPrivate(region, owner);
			} else if (type == ICType.RESOURCE) {
				protection.setPublic(region);
			}
		}
	}

	/**
	 * To be called when a player tries to abandon the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onAbandon(final ICPlayer player) {
		final Geometry geometry = player.getWorld().getGeometry();
		if (null == geometry) {
			player.message("island-abandon-world-error");
			return;
		}
		final ICLocation location = player.getLocation();
		final ICLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.message("island-abandon-ocean-error");
			return;
		}
		if (database.loadIslandType(island) != ICType.PRIVATE || !database.loadIslandOwner(island).equals(player.getName())) {
			player.message("island-abandon-owner-error");
			return;
		}

		// Success
		final String title = database.loadIslandTitle(island);
		database.saveIsland(island, ICType.ABANDONED, player.getName(), title, -1);
		protection.setReserved(geometry.outerRegion(island));
		player.message("island-abandon");
	}

	/**
	 * To be called when a player tries to examine the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onExamine(final ICPlayer player) {
		final Geometry geometry = player.getWorld().getGeometry();
		if (null == geometry) {
			player.message("island-examine-world-error");
			return;
		}
		final ICLocation location = player.getLocation();
		final ICLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.message("island-examine-ocean-error");
			return;
		}

		final Long seed = database.loadSeed(island);
		final String biome;
		if (null == seed) {
			biome = "Unknown";
		} else {
			biome = geometry.biome(seed.longValue()).getName();
		}

		final String world = island.getWorld();
		final int x = island.getX();
		final int z = island.getZ();
		final ICType type = database.loadIslandType(island);
		final int tax = database.loadIslandTax(island);
		final String taxString;
		if (tax < 0) {
			taxString = "infinite";
		} else {
			taxString = String.valueOf(tax);
		}
		if (ICType.RESOURCE == type) {
			player.message("island-examine-resource", world, x, z, biome, taxString);
		} else if (ICType.RESERVED == type) {
			player.message("island-examine-reserved", world, x, z, biome);
		} else if (ICType.NEW == type) {
			// TODO
			player.message("island-examine-new", world, x, z, biome);
		} else if (ICType.ABANDONED == type) {
			// TODO
			player.message("island-examine-abandoned", world, x, z, biome);
		} else if (ICType.REPOSSESSED == type) {
			// TODO
			player.message("island-examine-repossessed", world, x, z, biome);
		} else if (ICType.PRIVATE == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-examine-private", world, x, z, biome, owner, taxString);
		}
	}

	/**
	 * To be called when a player tries to purchase the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onPurchase(final ICPlayer player) {
		final Geometry geometry = player.getWorld().getGeometry();
		if (null == geometry) {
			player.message("island-purchase-world-error");
			return;
		}
		final ICLocation location = player.getLocation();
		final ICLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.message("island-purchase-ocean-error");
			return;
		}

		final ICType type = database.loadIslandType(island);
		final String name = player.getName();

		if (ICType.RESERVED == type) {
			player.message("island-purchase-reserved-error");
			return;
		}
		if (ICType.RESOURCE == type) {
			player.message("island-purchase-resource-error");
			return;
		}
		if (ICType.PRIVATE == type) {
			final String owner = database.loadIslandOwner(island);
			if (owner.equals(name)) {
				player.message("island-purchase-self-error");
			} else {
				player.message("island-purchase-other-error");
			}
			return;
		}
		if (islandCount(name) >= maxIslands) {
			player.message("island-purchase-max-error");
			return;
		}

		final int cost = calculatePurchaseCost(name);

		if (!player.takeItems(purchaseItem, cost)) {
			// Insufficient funds
			player.message("island-purchase-funds-error", Integer.toString(cost));
			return;
		}

		// Success
		protection.setPrivate(geometry.outerRegion(island), name);
		database.saveIsland(island, ICType.PRIVATE, name, "Private Island", taxInitial);
		player.message("island-purchase");
	}

	public void onTax(final ICPlayer player) {
		final Geometry geometry = player.getWorld().getGeometry();
		if (null == geometry) {
			player.message("island-tax-world-error");
			return;
		}
		final ICLocation location = player.getLocation();
		final ICLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.message("island-tax-ocean-error");
			return;
		}
		final String name = player.getName();
		if (database.loadIslandType(island) != ICType.PRIVATE || !database.loadIslandOwner(island).equals(name)) {
			player.message("island-tax-owner-error");
			return;
		}

		final int newTax = database.loadIslandTax(island) + taxIncrease;
		if (newTax > taxMax) {
			player.message("island-tax-max-error");
			return;
		}

		final int cost = calculateTaxCost(name);

		if (!player.takeItems(taxItem, cost)) {
			// Insufficient funds
			player.message("island-tax-funds-error", Integer.toString(cost));
			return;
		}

		// Success
		final String title = database.loadIslandTitle(island);
		database.saveIsland(island, ICType.PRIVATE, name, title, newTax);
		player.message("island-tax");
	}

	public void onDawn(final String world) {
		final Geometry geometry = server.findOnlineWorld(world).getGeometry();
		if (geometry == null) {
			// Not an IslandCraft world
			return;
		}
		final List<ICLocation> islands = database.loadIslandsByWorld(world);
		for (ICLocation island : islands) {
			final int tax = database.loadIslandTax(island);
			final ICType type = database.loadIslandType(island);
			final String owner = database.loadIslandOwner(island);
			final String title = database.loadIslandTitle(island);
			if (tax > 0) {
				// Decrement tax
				database.saveIsland(island, type, owner, title, tax - 1);
			} else if (tax == 0) {
				if (type == ICType.PRIVATE) {
					// Repossess island
					protection.setReserved(geometry.outerRegion(island));
					database.saveIsland(island, ICType.REPOSSESSED, owner, title, -1);
				} else {
					// TODO regenerate island + update tax
					if (type == ICType.REPOSSESSED || type == ICType.ABANDONED) {
						protection.setReserved(geometry.outerRegion(island));
						database.saveIsland(island, ICType.NEW, null, "New Island", -1);
					}
				}
			}
			// tax < 0 == infinite
		}
	}

	/**
	 * To be called when the player tries to rename the island at their current
	 * location.
	 * 
	 * @param player
	 * @param title
	 */
	public final void onRename(final ICPlayer player, final String title) {
		final Geometry geometry = player.getWorld().getGeometry();
		if (null == geometry) {
			player.message("island-rename-world-error");
			return;
		}
		final ICLocation location = player.getLocation();
		final ICLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.message("island-rename-ocean-error");
			return;
		}
		final String name = player.getName();
		if (database.loadIslandType(island) != ICType.PRIVATE || !database.loadIslandOwner(island).equals(name)) {
			player.message("island-rename-owner-error");
			return;
		}

		// Success
		final int tax = database.loadIslandTax(island);
		final ICType type = database.loadIslandType(island);
		database.saveIsland(island, type, name, title, tax);
		player.message("island-rename");
	}

	public void onWarp(final ICPlayer player) {
		final List<ICLocation> islands = database.loadIslands();
		Collections.shuffle(islands);
		for (final ICLocation island : islands) {
			final ICType type = database.loadIslandType(island);
			if (type == ICType.NEW || type == ICType.ABANDONED || type == ICType.REPOSSESSED) {
				player.warpTo(island);
				player.message("island-warp");
				return;
			}
		}
		player.message("island-warp-error");
	}

	private int calculatePurchaseCost(final String player) {
		return purchaseCostAmount + islandCount(player) * purchaseCostIncrease;
	}

	private int calculateTaxCost(final String player) {
		return taxCostAmount + (islandCount(player) - 1) * taxCostIncrease;
	}

	public void onMove(final ICPlayer player, final ICLocation from, final ICLocation to) {
		final Geometry geometry = player.getWorld().getGeometry();
		final ICLocation fromIsland = geometry.getInnerIsland(from);
		final ICLocation toIsland = geometry.getInnerIsland(from);
		if (fromIsland.equals(toIsland)) {
			return;
		}
		if (fromIsland != null) {
			leaveIsland(player, fromIsland);
		}
		if (toIsland != null) {
			enterIsland(player, toIsland);
		}
		// TODO also send message on log-in, rename, purchase, repossess, etc.
	}

	private void enterIsland(final ICPlayer player, final ICLocation island) {
		final ICType type = database.loadIslandType(island);
		final String title = database.loadIslandTitle(island);
		if (ICType.RESOURCE == type) {
			player.message("island-enter-resource", title);
		} else if (ICType.RESERVED == type) {
			player.message("island-enter-reserved", title);
		} else if (ICType.NEW == type) {
			player.message("island-enter-new", title);
		} else if (ICType.ABANDONED == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-enter-abandoned", title, owner);
		} else if (ICType.REPOSSESSED == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-enter-repossessed", title, owner);
		} else if (ICType.PRIVATE == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-enter-private", title, owner);
		}
	}

	private void leaveIsland(final ICPlayer player, final ICLocation island) {
		final ICType type = database.loadIslandType(island);
		final String title = database.loadIslandTitle(island);
		if (ICType.RESOURCE == type) {
			player.message("island-leave-resource", title);
		} else if (ICType.RESERVED == type) {
			player.message("island-leave-reserved", title);
		} else if (ICType.NEW == type) {
			player.message("island-leave-new", title);
		} else if (ICType.ABANDONED == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-leave-abandoned", title, owner);
		} else if (ICType.REPOSSESSED == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-leave-repossessed", title, owner);
		} else if (ICType.PRIVATE == type) {
			final String owner = database.loadIslandOwner(island);
			player.message("island-leave-private", title, owner);
		}
	}

	private int islandCount(final String player) {
		final List<ICLocation> islands = database.loadIslandsByOwner(player);
		int count = 0;
		for (ICLocation island : islands) {
			if (database.loadIslandType(island) == ICType.PRIVATE) {
				++count;
			}
		}
		return count;
	}
}
