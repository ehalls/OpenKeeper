/*
 * Copyright (C) 2014-2016 OpenKeeper
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
package toniarts.openkeeper.world.room.control;

import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import toniarts.openkeeper.world.room.GenericRoom;

/**
 * Not really a JME control currently. Manages how the gold places in the room.
 * Should be generalized to provide a control for any type of object owned by
 * the room. Either these controls or decorator pattern, lets see...
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public abstract class GoldControl {

    private final GenericRoom parent;
    private int storedGold = 0;
    private final Map<Point, Integer> goldTiles = new HashMap<>();

    public GoldControl(GenericRoom parent) {
        this.parent = parent;
    }

    protected abstract int getGoldPerTile();

    protected abstract int getNumberOfAccessibleTiles();

    public int getMaxGoldCapacity() {
        return getGoldPerTile() * getNumberOfAccessibleTiles();
    }

    /**
     * Add gold to room
     *
     * @param sum the sum to add
     * @param p preferred dropping point for the gold
     * @return the gold that doesn't fit
     */
    public int addGold(int sum, Point p) {
        if (p != null) {
            sum = putGold(sum, p);
        }
        if (sum > 0) {
            List<Point> coordinates = parent.getRoomInstance().getCoordinates();
            for (Point coordinate : coordinates) {
                sum = putGold(sum, coordinate);
                if (sum == 0) {
                    break;
                }
            }
        }
        return sum;
    }

    private int putGold(int sum, Point p) {
        Integer pointStoredGold = goldTiles.get(p);
        if (pointStoredGold == null) {
            pointStoredGold = 0;
        }
        if (pointStoredGold < getGoldPerTile()) {
            int goldToStore = Math.min(sum, getGoldPerTile() - pointStoredGold);
            pointStoredGold += goldToStore;
            sum -= goldToStore;
            goldTiles.put(p, goldToStore);
            storedGold += goldToStore;
        }
        return sum;
    }

    public int getStoredGold() {
        return storedGold;
    }

}
