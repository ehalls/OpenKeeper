/*
 * Copyright (C) 2014-2015 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.world;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.steer.utils.paths.LinePath;
import com.badlogic.gdx.ai.steer.utils.paths.LinePath.Segment;
import com.badlogic.gdx.math.Vector2;
import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.bullet.BulletAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Line;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.game.data.Keeper;
import toniarts.openkeeper.game.state.GameState;
import toniarts.openkeeper.game.task.TaskManager;
import toniarts.openkeeper.tools.convert.AssetsConverter;
import toniarts.openkeeper.tools.convert.ConversionUtils;
import toniarts.openkeeper.tools.convert.map.Creature;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Player;
import toniarts.openkeeper.tools.convert.map.Room;
import toniarts.openkeeper.tools.convert.map.Terrain;
import toniarts.openkeeper.tools.convert.map.Tile;
import toniarts.openkeeper.tools.convert.map.Variable;
import toniarts.openkeeper.utils.Utils;
import toniarts.openkeeper.view.selection.SelectionArea;
import toniarts.openkeeper.world.creature.pathfinding.MapDistance;
import toniarts.openkeeper.world.creature.pathfinding.MapIndexedGraph;
import toniarts.openkeeper.world.creature.pathfinding.MapPathFinder;
import toniarts.openkeeper.world.effect.EffectManager;
import toniarts.openkeeper.world.listener.TileChangeListener;
import toniarts.openkeeper.world.room.GenericRoom;
import toniarts.openkeeper.world.room.RoomInstance;

/**
 * Handles the handling of game world, physics & visual wise
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public abstract class WorldState extends AbstractAppState {

    private Main app;
    private AppStateManager stateManager;
    private final MapLoader mapLoader;
    private final ThingLoader thingLoader;
    private final KwdFile kwdFile;
    private AssetManager assetManager;
    private Node worldNode;
    private final MapIndexedGraph pathFindingMap;
    private final MapPathFinder pathFinder;
    private final MapDistance heuristic;
    private final Node thingsNode;
    private final BulletAppState bulletAppState;
    private final EffectManager effectManager;
    private List<TileChangeListener> tileChangeListener;
    private final GameState gameState;

    private static final Logger logger = Logger.getLogger(WorldState.class.getName());

    public WorldState(final KwdFile kwdFile, final AssetManager assetManager, GameState gameState) {
        this.kwdFile = kwdFile;
        this.gameState = gameState;

        // Effect manager
        effectManager = new EffectManager(assetManager, kwdFile);

        // World node
        worldNode = new Node("World");

        // Create physics state
        bulletAppState = new BulletAppState();

        // Create the actual map
        this.mapLoader = new MapLoader(assetManager, kwdFile, effectManager, this) {
            @Override
            protected void updateProgress(int progress, int max) {
                WorldState.this.updateProgress(progress, max);
            }
        };
        worldNode.attachChild(mapLoader.load(assetManager, kwdFile));

        // For path finding
        pathFindingMap = new MapIndexedGraph(this);
        pathFinder = new MapPathFinder(pathFindingMap, false);
        heuristic = new MapDistance();

        // Things
        thingLoader = new ThingLoader(this, kwdFile, assetManager);
        thingsNode = thingLoader.loadAll();
        worldNode.attachChild(thingsNode);

        // Set up the money$$$
        for (Keeper player : gameState.getPlayers()) {
            if (player.getInitialGold() > 0) {
                addGold(player.getId(), player.getInitialGold());
            }
        }
    }

    @Override
    public void initialize(final AppStateManager stateManager, final Application app) {
        super.initialize(stateManager, app);
        this.app = (Main) app;
        this.stateManager = stateManager;
        this.assetManager = app.getAssetManager();

        // Attach physics
        this.stateManager.attach(bulletAppState);

        // Attach the world
        this.app.getRootNode().attachChild(worldNode);
    }

    @Override
    public void cleanup() {

        // Detach our map
        if (worldNode != null) {
            app.getRootNode().detachChild(worldNode);
            worldNode = null;
        }

        // Physics away
        stateManager.detach(stateManager.getState(BulletAppState.class));

        super.cleanup();
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    /**
     * If you want to monitor the map loading progress, use this method
     *
     * @param progress current progress
     * @param max max progress
     */
    protected abstract void updateProgress(int progress, int max);

    /**
     * If you want to get notified about tile changes
     *
     * @param listener the listener
     */
    public void addListener(TileChangeListener listener) {
        if (tileChangeListener == null) {
            tileChangeListener = new ArrayList<>();
        }
        tileChangeListener.add(listener);
    }

    public MapData getMapData() {
        return mapLoader.getMapData();
    }

    /**
     * Get the map loader
     *
     * @return MapLoader
     */
    public MapLoader getMapLoader() {
        return mapLoader;
    }

    public Node getWorld() {
        return worldNode;
    }

    /**
     * @deprecated
     *
     * @return KwdFile
     */
    public KwdFile getLevelData() {
        return kwdFile;
    }

    /**
     * Set some tiles selected/undelected
     *
     * @param selectionArea the selection area
     * @param select select or unselect
     * @param playerId the player who selected the tile
     */
    public void selectTiles(SelectionArea selectionArea, boolean select, short playerId) {
        List<Point> updatableTiles = new ArrayList<>();
        for (int x = (int) Math.max(0, selectionArea.getStart().x); x < Math.min(kwdFile.getMap().getWidth(), selectionArea.getEnd().x + 1); x++) {
            for (int y = (int) Math.max(0, selectionArea.getStart().y); y < Math.min(kwdFile.getMap().getHeight(), selectionArea.getEnd().y + 1); y++) {
                TileData tile = getMapData().getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());
                if (!terrain.getFlags().contains(Terrain.TerrainFlag.TAGGABLE)) {
                    continue;
                }
                tile.setSelected(select, playerId);
                updatableTiles.add(new Point(x, y));
            }
        }
        Point[] tiles = updatableTiles.toArray(new Point[updatableTiles.size()]);
        mapLoader.updateTiles(tiles);

        // Notify
        notifyTileChange(tiles);
    }

    /**
     * Determine if a tile at x & y is selected or not
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return is the tile selected
     */
    public boolean isSelected(int x, int y) {
        TileData tile = getMapData().getTile(x, y);
        if (tile == null) {
            return false;
        }
        return tile.isSelected();
    }

    /**
     * Determine if a tile at x & y is selectable or not
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return is the tile selectable
     */
    public boolean isTaggable(int x, int y) {
        TileData tile = getMapData().getTile(x, y);
        if (tile == null) {
            return false;
        }
        Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());
        return terrain.getFlags().contains(Terrain.TerrainFlag.TAGGABLE);
    }

    /**
     * Determine if a tile at x & y is buildable by the player
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param player the player
     * @param room the room to be build
     * @return is the tile buildable
     */
    public boolean isBuildable(int x, int y, Player player, Room room) {
        TileData tile = getMapData().getTile(x, y);
        if (tile == null) {
            return false;
        }

        Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());

        // Check that this is not already a room
        if (mapLoader.getRoomCoordinates().containsKey(new Point(x, y))) {
            return false;
        }

        // Ownable tile is needed for land building (and needs to be owned by us)
        if (room.getFlags().contains(Room.RoomFlag.PLACEABLE_ON_LAND)
                && !terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)
                && terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)
                && tile.getPlayerId() == player.getPlayerId()) {
            return true;
        }

        // See if we are dealing with bridges
        if (room.getFlags().contains(Room.RoomFlag.PLACEABLE_ON_WATER) && terrain.getFlags().contains(Terrain.TerrainFlag.WATER)) {
            return true;
        }
        if (room.getFlags().contains(Room.RoomFlag.PLACEABLE_ON_LAVA) && terrain.getFlags().contains(Terrain.TerrainFlag.LAVA)) {
            return true;
        }

        return false;
    }

    /**
     * Determine if a tile (maybe a room) at x & y is claimable by the player
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId the player
     * @return is the tile claimable by you
     */
    public boolean isClaimable(int x, int y, short playerId) {
        TileData tile = getMapData().getTile(x, y);
        if (tile == null) {
            return false;
        }

        // See if the terrain is claimable at all
        Terrain terrain = tile.getTerrain();
        boolean claimable = false;
        if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {
            if (tile.getPlayerId() != playerId) {
                claimable = true;
            }
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
            if (tile.getPlayerId() != playerId) {
                claimable = true;
            }
        } else {
            claimable = (terrain.getMaxHealthTypeTerrainId() != terrain.getTerrainId());
        }

        // In order to claim, it needs to be adjacent to your own tiles
        if (claimable) {
            for (Point p : getMapLoader().getSurroundingTiles(new Point(x, y), false)) {
                TileData neighbourTile = getMapData().getTile(p);
                if (neighbourTile != null && neighbourTile.getPlayerId() == playerId && neighbourTile.getTerrain().getFlags().contains(Terrain.TerrainFlag.OWNABLE) && !neighbourTile.getTerrain().getFlags().contains(Terrain.TerrainFlag.SOLID)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addPlayerGold(short playerId, int value) {
        if (value == 0) {
            return;
        }
        Keeper keeper = gameState.getPlayer(playerId);
        if (keeper == null) {
            return;
        }
        keeper.getGoldControl().addGold(value);
    }

    public void alterTerrain(Point pos, short terrainId, short playerId) {
        TileData tile = getMapData().getTile(pos.x, pos.y);
        if (tile == null) {
            return;
        }
        // set type
        tile.setTerrainId(terrainId);
        // set owner
        if (playerId != 0) {
            tile.setPlayerId(playerId);
        }
        // See if room walls are allowed and does this touch any rooms
        updateRoomWalls(tile);
        // update one
        mapLoader.updateTiles(mapLoader.getSurroundingTiles(pos, true));
    }

    /**
     * Dig a tile at x & y
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void digTile(int x, int y) {

        TileData tile = getMapData().getTile(x, y);
        if (tile == null) {
            return;
        }

        Terrain terrain = tile.getTerrain();
        if (terrain.getFlags().contains(Terrain.TerrainFlag.IMPENETRABLE)) {
            return;
        }

        // FIXME: this is just a debug stuff, remove when the imps can carry the gold
        addPlayerGold(Keeper.KEEPER1_ID, terrain.getGoldValue());

        tile.setTerrainId(terrain.getDestroyedTypeTerrainId());

        // See if room walls are allowed and does this touch any rooms
        updateRoomWalls(tile);
        mapLoader.updateTiles(mapLoader.getSurroundingTiles(tile.getLocation(), true));
    }

    private void updateRoomWalls(TileData tile) {
        Terrain terrain = tile.getTerrain();
        // See if room walls are allowed and does this touch any rooms
        if (!terrain.getFlags().contains(Terrain.TerrainFlag.ALLOW_ROOM_WALLS)) {
            return;
        }
        List<RoomInstance> wallUpdatesNeeded = new ArrayList<>();
        for (Point p : mapLoader.getSurroundingTiles(tile.getLocation(), false)) {
            RoomInstance room = mapLoader.getRoomCoordinates().get(p);
            if (room != null && room.getRoom().getFlags().contains(Room.RoomFlag.HAS_WALLS)) {
                wallUpdatesNeeded.add(room);
            }
        }
        if (!wallUpdatesNeeded.isEmpty()) {
            mapLoader.updateRoomWalls(wallUpdatesNeeded);
        }
    }

    public void flashTile(int x, int y, int time, boolean enabled) {
        mapLoader.flashTile(x, y, time, enabled);
    }

    /**
     * Claim a tile at x & y to the player's name
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId the player, the new owner
     */
    public void claimTile(int x, int y, short playerId) {
        if (!isClaimable(x, y, playerId)) {
            return;
        }

        TileData tile = getMapData().getTile(x, y);
        Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());

        if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {
            // TODO: Claim all current room
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
            // tile is claimed by another player and needs to be destroyed
            if (tile.getPlayerId() != playerId) {
                tile.setTerrainId(terrain.getDestroyedTypeTerrainId());
            }
        } else {
            tile.setTerrainId(terrain.getMaxHealthTypeTerrainId());
        }

        terrain = tile.getTerrain();
        if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
            tile.setPlayerId(playerId);
        }
        // See if room walls are allowed and does this touch any rooms
        updateRoomWalls(tile);

        mapLoader.updateTiles(mapLoader.getSurroundingTiles(tile.getLocation(), true));
    }

    /**
     * Build a building to the wanted area
     *
     * @param selectionArea the selection area
     * @param player the player, the new owner
     * @param room room to build
     */
    public void build(SelectionArea selectionArea, Player player, Room room) {
        Set<Point> updatableTiles = new HashSet<>();
        Set<Point> buildPlots = new HashSet<>();
        for (int x = (int) Math.max(0, selectionArea.getStart().x); x < Math.min(kwdFile.getMap().getWidth(), selectionArea.getEnd().x + 1); x++) {
            for (int y = (int) Math.max(0, selectionArea.getStart().y); y < Math.min(kwdFile.getMap().getHeight(), selectionArea.getEnd().y + 1); y++) {

                // See that is this valid
                if (!isBuildable(x, y, player, room)) {
                    continue;
                }

                // Build
                TileData tile = getMapData().getTile(x, y);
                tile.setPlayerId(player.getPlayerId());
                tile.setTerrainId(room.getTerrainId());

                Point p = new Point(x, y);
                buildPlots.addAll(Arrays.asList(mapLoader.getSurroundingTiles(p, false)));
                updatableTiles.addAll(Arrays.asList(mapLoader.getSurroundingTiles(p, true)));
            }
        }

        // See if we hit any of the adjacent rooms
        Set<RoomInstance> adjacentInstances = new HashSet<>();
        for (Point p : buildPlots) {
            if (mapLoader.getRoomCoordinates().containsKey(p) && mapLoader.getRoomCoordinates().get(p).getRoom().equals(room)) {

                // Same room, see that we own it
                TileData tile = getMapData().getTile(p.x, p.y);
                if (tile.getPlayerId() == player.getPlayerId()) {

                    // Bingo!
                    adjacentInstances.add(mapLoader.getRoomCoordinates().get(p));
                }
            }
        }

        // If any hits, merge, and update whole room
        if (!adjacentInstances.isEmpty()) {

            // Add the mergeable rooms to updatable tiles as well
            for (RoomInstance instance : adjacentInstances) {
                for (Point p : instance.getCoordinates()) {
                    updatableTiles.addAll(Arrays.asList(mapLoader.getSurroundingTiles(p, true)));
                }
            }

            // Remove all the room instances (they will be regenerated)
            mapLoader.removeRoomInstances(adjacentInstances.toArray(new RoomInstance[adjacentInstances.size()]));
        }

        mapLoader.updateTiles(updatableTiles.toArray(new Point[updatableTiles.size()]));
    }

    /**
     * Is the tile (building) sellable by us
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param player the player, the seller
     */
    public boolean isSellable(int x, int y, Player player) {
        TileData tile = getMapData().getTile(x, y);
        Point p = new Point(x, y);
        if (tile.getPlayerId() == player.getPlayerId() && mapLoader.getRoomCoordinates().containsKey(p)) {

            // We own it, see if sellable
            RoomInstance instance = mapLoader.getRoomCoordinates().get(p);
            return instance.getRoom().getFlags().contains(Room.RoomFlag.BUILDABLE);
        }
        return false;
    }

    /**
     * Sell building(s) from the wanted area
     *
     * @param selectionArea the selection area
     * @param player the player, the new owner
     */
    public void sell(SelectionArea selectionArea, Player player) {
        Set<Point> updatableTiles = new HashSet<>();
        Set<RoomInstance> soldInstances = new HashSet<>();
        for (int x = (int) Math.max(0, selectionArea.getStart().x); x < Math.min(kwdFile.getMap().getWidth(), selectionArea.getEnd().x + 1); x++) {
            for (int y = (int) Math.max(0, selectionArea.getStart().y); y < Math.min(kwdFile.getMap().getHeight(), selectionArea.getEnd().y + 1); y++) {

                // See that is this valid
                if (!isSellable(x, y, player)) {
                    continue;
                }

                // Sell
                TileData tile = getMapData().getTile(x, y);
                if (tile == null) {
                    continue;
                }
                Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());
                if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {
                    Room room = kwdFile.getRoomByTerrain(tile.getTerrainId());
                    if (room.getFlags().contains(Room.RoomFlag.PLACEABLE_ON_LAND)) {
                        tile.setTerrainId(terrain.getDestroyedTypeTerrainId());
                    } else {

                        // Water or lava
                        if (tile.getFlag() == Tile.BridgeTerrainType.LAVA) {
                            tile.setTerrainId(kwdFile.getMap().getLava().getTerrainId());
                        } else {
                            tile.setTerrainId(kwdFile.getMap().getWater().getTerrainId());
                        }
                    }
                }

                // Get the instance
                Point p = new Point(x, y);
                soldInstances.add(mapLoader.getRoomCoordinates().get(p));
                updatableTiles.addAll(Arrays.asList(mapLoader.getSurroundingTiles(p, true)));
            }
        }

        // Remove the sold instances (will be regenerated) and add them to updatable
        for (RoomInstance roomInstance : soldInstances) {
            for (Point p : roomInstance.getCoordinates()) {
                updatableTiles.addAll(Arrays.asList(mapLoader.getSurroundingTiles(p, true)));
            }
        }
        mapLoader.removeRoomInstances(soldInstances.toArray(new RoomInstance[soldInstances.size()]));

        mapLoader.updateTiles(updatableTiles.toArray(new Point[updatableTiles.size()]));
    }

    /**
     * Plays a positional sound in an given tile
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param soundFile the sound file
     */
    public void playSoundAtTile(int x, int y, String soundFile) {

        // We might cache these per file? Then they would be persistent, just move them, does it matter?
        // Since creation of new objects and all, I don't know if they stay in the scene graph..
        AudioNode audio = new AudioNode(assetManager, ConversionUtils.getCanonicalAssetKey(AssetsConverter.SOUNDS_FOLDER + soundFile));
        audio.setPositional(true);
        audio.setReverbEnabled(false);
        audio.setLocalTranslation(x, 0, y);
        worldNode.attachChild(audio);
        audio.play();
    }

    /**
     * Get a random tile, that is not a starting tile
     *
     * @param start starting coordinates
     * @param radius radius, in tiles
     * @param creature
     * @return a random tile if one is found
     */
    public Point findRandomAccessibleTile(Point start, int radius, Creature creature) {
        List<Point> tiles = new ArrayList<>(radius * radius - 1);
        for (int y = start.y - radius / 2; y < start.y + radius / 2; y++) {
            for (int x = start.x - radius / 2; x < start.x + radius / 2; x++) {

                // Skip start tile
                if (x == start.x && y == start.y) {
                    continue;
                }

                TileData tile = getMapData().getTile(x, y);
                if (tile != null && isAccessible(tile, creature)) {
                    tiles.add(new Point(x, y));
                }
            }
        }

        // Take a random point
        if (!tiles.isEmpty()) {
            return Utils.getRandomItem(tiles);
        }
        return null;
    }

    /**
     * FIXME: This can NOT be. Just for quick easy testing.
     *
     * @param start start point
     * @param end end point
     * @param creature the creature to find path for
     * @return output path, null if path not found
     */
    public GraphPath<TileData> findPath(Point start, Point end, Creature creature) {
        pathFindingMap.setCreature(creature);
        GraphPath<TileData> outPath = new DefaultGraphPath<>();
        if (pathFinder.searchNodePath(getMapData().getTile(start.x, start.y), getMapData().getTile(end.x, end.y), heuristic, outPath)) {
            return outPath;
        }
        return null;
    }

    /**
     * Get tile coordinates from 3D coordinates
     *
     * @param location position
     * @return tile coordinates
     */
    public Point getTileCoordinates(Vector3f location) {
        return new Point((int) Math.floor(location.x + 0.5f), (int) Math.floor(location.z + 0.5f));
    }

    /**
     * Check if given tile is accessible by the given creature
     *
     * @param tile the tile
     * @param creature creature
     * @return is accessible
     */
    public boolean isAccessible(TileData tile, Creature creature) {
        Terrain terrain = tile.getTerrain();
        if (!terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {

            // TODO: Rooms, obstacles and what not, should create an universal isAccessible(Creature) to map loader / world handler maybe
            if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {

                // Get room obstacles
                RoomInstance roomInstance = getMapLoader().getRoomCoordinates().get(new Point(tile.getX(), tile.getY()));
                GenericRoom room = getMapLoader().getRoomActuals().get(roomInstance);
                return room.isTileAccessible(tile.getX(), tile.getY());
            } else if (creature.getFlags().contains(Creature.CreatureFlag.CAN_FLY)) {
                return true;
            } else if (terrain.getFlags().contains(Terrain.TerrainFlag.LAVA) && !creature.getFlags().contains(Creature.CreatureFlag.CAN_WALK_ON_LAVA)) {
                return false;
            } else if (terrain.getFlags().contains(Terrain.TerrainFlag.WATER) && !creature.getFlags().contains(Creature.CreatureFlag.CAN_WALK_ON_WATER)) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Debug drawing of path
     *
     * @param linePath
     */
    public void drawPath(LinePath<Vector2> linePath) {
        for (Segment<Vector2> segment : linePath.getSegments()) {

            Line line = new Line(new Vector3f(segment.getBegin().x, 0.25f, segment.getBegin().y), new Vector3f(segment.getEnd().x, 0.25f, segment.getEnd().y));
            line.setLineWidth(2);
            Geometry geometry = new Geometry("Bullet", line);
            Material orange = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            orange.setColor("Color", ColorRGBA.Red);
            orange.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            geometry.setCullHint(Spatial.CullHint.Never);
            geometry.setMaterial(orange);
            getWorld().attachChild(geometry);
        }
    }

    public Node getThingsNode() {
        return thingsNode;
    }

    /**
     * Notify the tile change listeners
     *
     * @param tiles changed tiles
     */
    private void notifyTileChange(Point... tiles) {
        if (tileChangeListener != null) {
            for (Point p : tiles) {
                for (TileChangeListener listener : tileChangeListener) {
                    listener.onTileChange(p.x, p.y);
                }
            }
        }
    }

    /**
     * Get the task manager
     *
     * @return task manager
     */
    public TaskManager getTaskManager() {
        return stateManager.getState(GameState.class).getTaskManager();
    }

    /**
     * Damage a tile
     *
     * @param point the point
     * @param playerId the player applying the damage
     * @return you might get gold out of this
     */
    public int damageTile(Point point, short playerId) {
        TileData tile = getMapData().getTile(point);
        Terrain terrain = tile.getTerrain();

        // Calculate the damage
        int damage = 0;
        int returnedGold = 0;
        if (terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {
            if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
                if (tile.getPlayerId() == playerId) {
                    damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.DIG_OWN_WALL_HEALTH);
                } else {
                    damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.DIG_ENEMY_WALL_HEALTH);
                }
            } else if (tile.getGold() > 0) {
                // This is how I believe the gold mining works, it is not health damage we do, it is substracting gold
                // The mined tiles leave no loot, the loot is left by the imps if there is no place to store the gold
                if (terrain.getFlags().contains(Terrain.TerrainFlag.IMPENETRABLE)) {
                    damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.GOLD_MINED_FROM_GEMS);
                } else {
                    damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.MINE_GOLD_HEALTH);
                }
            } else {
                damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.DIG_ROCK_HEALTH);
            }
        } else {
            throw new UnsupportedOperationException("No support for damaging other than solid tiles!");
        }

        // Do the damage
        boolean tileDestroyed;
        damage = Math.abs(damage);
        if (tile.getGold() > 0) { // Mine
            returnedGold = tile.mineGold(damage);
            tileDestroyed = (tile.getGold() < 1);
        } else { // Apply damage
            tileDestroyed = tile.applyDamage(damage);
        }

        // See the results
        if (tileDestroyed) {

            // TODO: effect, drop loot & checks
            // The tile is dead
            if (terrain.getDestroyedEffectId() != 0) {
//                Node effect = effectManager.load(terrain.getDestroyedEffectId());
//                effect.setLocalTranslation(point.x + 0.5f, 0, point.y + 0.5f);
//                worldNode.attachChild(effect);
            }
            tile.setTerrainId(terrain.getDestroyedTypeTerrainId());

            updateRoomWalls(tile);
            mapLoader.updateTiles(mapLoader.getSurroundingTiles(tile.getLocation(), true));

            // Notify
            notifyTileChange(point);
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.DECAY)) {
            mapLoader.updateTiles(point);
        }

        return returnedGold;
    }

    /**
     * Heal a tile
     *
     * @param point the point
     * @param playerId the player applying the healing
     */
    public void healTile(Point point, short playerId) {
        TileData tile = getMapData().getTile(point);
        Terrain terrain = tile.getTerrain();

        // See the amount of healing
        // TODO: now just claiming of a tile (claim health variable is too big it seems)
        int healing;
        if (terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {

            if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
                if (tile.getPlayerId() == playerId) {
                    healing = (int) getLevelVariable(Variable.MiscVariable.MiscType.REPAIR_WALL_HEALTH);
                } else {
                    healing = (int) getLevelVariable(Variable.MiscVariable.MiscType.CLAIM_TILE_HEALTH);
                }
            } else {
                healing = (int) getLevelVariable(Variable.MiscVariable.MiscType.REINFORCE_WALL_HEALTH);
            }
        } else {
            healing = (int) getLevelVariable(Variable.MiscVariable.MiscType.REPAIR_TILE_HEALTH);
        }

        // Apply
        if (tile.applyHealing(healing)) {

            // TODO: effect & checks
            // The tile is upgraded
            if (terrain.getMaxHealthEffectId() != 0) {
//                Node effect = effectManager.load(terrain.getMaxHealthEffectId());
//                effect.setLocalTranslation(point.x + 0.5f, 0, point.y + 0.5f);
//                worldNode.attachChild(effect);
            }
            if (terrain.getMaxHealthTypeTerrainId() > 0) {
                tile.setTerrainId(terrain.getMaxHealthTypeTerrainId());
                tile.setPlayerId(playerId);
            }

            updateRoomWalls(tile);
            mapLoader.updateTiles(mapLoader.getSurroundingTiles(tile.getLocation(), true));

            // Notify
            notifyTileChange(point);
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.DECAY)) {
            mapLoader.updateTiles(point);
        }
    }

    /**
     * Get level variable value
     *
     * @param variable the variable type
     * @return variable value
     */
    public float getLevelVariable(Variable.MiscVariable.MiscType variable) {
        // TODO: player is able to change these, so need a wrapper and store these to GameState
        return kwdFile.getVariables().get(variable).getValue();
    }

    /**
     * Is repairable wall at tile point
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId for the player
     * @return is the wall repairable
     */
    public boolean isRepairableWall(int x, int y, short playerId) {
        TileData tile = getMapData().getTile(x, y);
        Terrain terrain = tile.getTerrain();
        return (!tile.isSelectedByPlayerId(playerId) && tile.getPlayerId() == playerId && terrain.getFlags().contains(Terrain.TerrainFlag.SOLID) && terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE) && !tile.isAtFullHealth());
    }

    /**
     * Is claimable wall at tile point
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId for the player
     * @return is the wall claimable
     */
    public boolean isClaimableWall(int x, int y, short playerId) {
        TileData tile = getMapData().getTile(x, y);
        Terrain terrain = tile.getTerrain();
        return (terrain.getFlags().contains(Terrain.TerrainFlag.SOLID) && isClaimable(x, y, playerId));
    }

    /**
     * Is claimable floor at tile point (not a room)
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId for the player
     * @return is the floor claimable
     */
    public boolean isClaimableTile(int x, int y, short playerId) {
        TileData tile = getMapData().getTile(x, y);
        Terrain terrain = tile.getTerrain();
        return (!terrain.getFlags().contains(Terrain.TerrainFlag.ROOM) && isClaimable(x, y, playerId));
    }

    /**
     * Is claimable room tile at tile point
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param playerId for the player
     * @return is the room claimable
     */
    public boolean isClaimableRoom(int x, int y, short playerId) {
        TileData tile = getMapData().getTile(x, y);
        Terrain terrain = tile.getTerrain();
        return (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM) && isClaimable(x, y, playerId));
    }

    /**
     * Attempt to claim the tile or room, applies either damage of heal,
     * depending whose tile is it
     *
     * @param point tile coordinate
     * @param playerId for the player
     */
    public void applyClaimTile(Point point, short playerId) {
        TileData tile = getMapData().getTile(point);
        Terrain terrain = tile.getTerrain();
        if (terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE) && tile.getPlayerId() != playerId) {
            if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {
                damageRoom(point, playerId);
            } else {
                damageTile(point, playerId);
            }
        } else {
            // TODO: Room healing
            healTile(point, playerId);
        }
    }

    /**
     * Damage a room
     *
     * @param point tile coordinate
     * @param playerId for the player
     */
    private void damageRoom(Point point, short playerId) {
        TileData tile = getMapData().getTile(point);

        // Calculate the damage
        int damage;
        if (tile.getPlayerId() == 0) {
            damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.CONVERT_ROOM_HEALTH);
        } else {
            damage = (int) getLevelVariable(Variable.MiscVariable.MiscType.ATTACK_ROOM_HEALTH);
        }

        // Get the room
        RoomInstance room = getMapLoader().getRoomCoordinates().get(point);
        List<Point> roomTiles = room.getCoordinates();

        // Apply the damage equally to all tiles so that the overall condition can be checked easily
        // I don't know if this model is correct or not, but like this the bigger the room the more effort it requires to claim
        int damagePerTile = Math.abs(damage / roomTiles.size());
        for (Point p : roomTiles) {
            TileData roomTile = getMapData().getTile(p);
            if (roomTile.applyDamage(damagePerTile)) {

                // If one of the tiles runs out (everyone should run out of the same time, unless a new tile has recently being added..)
                for (Point p2 : roomTiles) {
                    roomTile = getMapData().getTile(p2);
                    roomTile.setPlayerId(playerId); // Claimed!
                    roomTile.applyHealing(Integer.MAX_VALUE);

                    // TODO: Claimed room wall tiles lose the claiming I think?
                    notifyTileChange(p2);
                }
                break;
            }
        }
    }

    /**
     * Add a lump sum of gold to a player, distributes the gold to the available
     * rooms
     *
     * @param playerId for the player
     * @param sum the gold sum
     * @return returns a sum of gold that could not be added to player's gold
     */
    final public int addGold(short playerId, int sum) {
        return addGold(playerId, null, sum);
    }

    /**
     * Add a lump sum of gold to a player, distributes the gold to the available
     * rooms
     *
     * @param playerId for the player
     * @param p a point where to drop the gold, can be {@code  null}
     * @param sum the gold sum
     * @return returns a sum of gold that could not be added to player's gold
     */
    public int addGold(short playerId, Point p, int sum) {
        int originalSum = sum;

        // Gold to specified point/room
        if (p != null) {

            // Get a room in point
            RoomInstance roomInstance = getMapLoader().getRoomCoordinates().get(p);
            if (roomInstance != null) {
                GenericRoom room = getMapLoader().getRoomActuals().get(roomInstance);
                if (room.canStoreGold()) {
                    return room.getGoldControl().addGold(sum, p);
                } else {
                    // TODO: generate loose gold
                }
            } else {
                // TODO: generate loose gold
            }
        } else {

            // Distribute the gold
            for (Entry<RoomInstance, GenericRoom> roomEntry : getMapLoader().getRoomActuals().entrySet()) {
                if (roomEntry.getKey().getOwnerId() == playerId && roomEntry.getValue().canStoreGold()) {
                    sum = roomEntry.getValue().getGoldControl().addGold(sum, p);
                    if (sum == 0) {
                        break;
                    }
                }
            }
        }

        // Add to the player
        addPlayerGold(playerId, originalSum - sum);
        return sum;
    }

}
