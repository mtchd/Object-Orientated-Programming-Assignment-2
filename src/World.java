import java.util.*;

import org.newdawn.slick.Graphics;
import org.newdawn.slick.Input;

public class World {
	
	/** List of sprites in world */
	private List<Sprite> sprites;
	/** Count of targets covered */
	private int targetCount;
	/** Targets needed to win level */
	private int targetsNeeded;
	/** Sprites to add to sprites list */
	private List<Sprite> spritesToBirth;
	/** Sprites to delete from sprites list */
	private List<Sprite> spritesToDie;
	/** Moves done so far */
	private int moves;
	/** App this world is contained in */
	private App app;
	/** Boolean indicating all targets filled */
	private boolean gameWon = false;
	/** HUD x coordinate, in pixels */
	private static final int HUD_X_LOCATION = 50;
	/** Move counter y location, in pixels */
	private static final int MOVES_Y_LOCATION = 50;
	/** Target counter y location, in pixels */
	private static final int TARGETS_Y_LOCATION = 70;;
	
	public World(String levelSource, App app) {
		
		sprites = Loader.loadSprites(levelSource, this);
		
		// Count how many targets we need to win
		targetCount = 0;
		targetsNeeded = 0;
		for (Sprite sprite : sprites) {
			if (sprite instanceof Target) {
				targetsNeeded += 1;
			}
		}
		
		// General init
		moves = 0;
		linkDoors();
		this.app = app;
		spritesToBirth = new LinkedList<Sprite>();
		spritesToDie = new LinkedList<Sprite>();
	}
	
	public void update(Input input) {
		
		if (input.isKeyPressed(Input.KEY_Z)) {
			undo();
		}
		
		for (Sprite sprite : sprites) {
			sprite.update(input);
		}
		
		/* Add and remove queued sprites,
		 * as they cannot be changed while looping through the list.
		 */
		if (!spritesToBirth.isEmpty()) {
			sprites.addAll(spritesToBirth);
			spritesToBirth.clear();
		}
		if (!spritesToDie.isEmpty()) {
			sprites.removeAll(spritesToDie);
			spritesToDie.clear();
		}
		
		
	}
	
	public void render(Graphics g) {
		
		for (Sprite sprite : sprites) {
			sprite.render(g);
		}
		
		// Draw move count and targets so far
		g.drawString("Moves: " + moves, HUD_X_LOCATION, MOVES_Y_LOCATION);
		g.drawString("Targets: " + targetCount + "/" + targetsNeeded,
				HUD_X_LOCATION, TARGETS_Y_LOCATION);
	}
	
	/** Check if a location is traversable
	 * @param coord Coordinate of location
	 * @return True if location has no blocked tiles and at least one unblocked tiles
	 */
	public boolean traversable(Coordinate coord) {
		
		// Default to blocked
		boolean traversability = false;
		
		// Loop through sprites checking for tile on coord
		for (Sprite sprite : sprites) {
			if (sprite.getLocation().equals(coord)) {
				if (sprite instanceof Tile) {
					// If multiple tiles, it only takes one blocked for false
					if (((Tile) sprite).isTraversable()) {
						traversability = true;
					} else {
						return false;
					}
				}
			}
		}
		return traversability;
	}
	
	/** gotSprite returns if a map coordinate has that sprite
	 * @param coord Map coordinate to check.
	 * @param type Type of sprite to check a map coordinate for
	 * @return True if map coordinate contains one instance of the specified sprite
	 */
	public boolean gotSprite(Coordinate coord, Class<?> type) {
		
		// Loop through sprites checking for sprite on coord
		for (Sprite sprite : sprites) {
			if (sprite.getLocation().equals(coord) && type.isInstance(sprite)) {
				return true;
			}
		}
		
		// Default to doesn't contain sprite
		return false;
	}
	
	/** Pushes a block
	 * @param distance Distance to push block (negative to go backwards on axis)
	 * @param direction Axis to push block along
	 * @param location Location to push blocks at
	 * @return True if push succeeds, false if a block can't move
	 */
	public boolean push(int distance, char direction, Coordinate location) {
		
		for (Sprite sprite : sprites) {
			if (sprite.getLocation().equals(location) && sprite instanceof Block) {
				if (!((Block)sprite).move(distance, direction)){
					return false;
				}
			}
		}
		
		updateTargets();
		return true;
	}
	
	/* Gets the first sprite of specified type encountered at location */
	// TODO Is it okay to just get the first one?
	public Sprite getSpriteAt(Coordinate location, Class<?> type) {
		
		// Loop through sprites checking for sprite on coord
		for (Sprite sprite : sprites) {
			if (sprite.getLocation().equals(location) && type.isInstance(sprite)) {
				return sprite;
			}
		}
		
		// Default to doesn't contain sprite
		return null;
	}

	public void killSprite(Sprite dying) {
		spritesToDie.add(dying);
	}
	
	public void birthSprite(String imageName, Coordinate location) {
		spritesToBirth.add(Loader.addSprite(imageName, location, this));
	}
	
	public void reset() {
		app.resetLvl();
	}
	
	private void undo() {
		
		if (moves > 0) {
			moves -= 1;
			for (Sprite sprite : sprites) {
				if (sprite instanceof Reversable) {
					((Reversable) sprite).undo(moves);
				}
			}
			updateTargets();
		}
	}
	
	public void addPlayerMove(boolean successful, Coordinate newLoc) {
		
		if (successful) {
			moves += 1;
		}
		
		// Tell rogue & mage to move regardless of whether move was successful
		for (Sprite sprite : sprites) {
			
			if (sprite instanceof Rogue) {
				// Tells rogue to patrol along x axis
				((Rogue) sprite).patrol();
			} else if (sprite instanceof Mage) {
				// Tells mage to track player
				((Mage) sprite).trackingMove(newLoc);
			}
		}
	}
	
	// TODO Should this be in update?
	public void updateTargets() {
		
		// Count targets that have a block
		int count = 0;
		for (Sprite sprite: sprites) {
			if (sprite instanceof Target && ((Target) sprite).isOn()) {
				count += 1;
			}
		}
		
		// Update count and check if level won
		targetCount = count;
		if (targetCount >= targetsNeeded) {
			gameWon = true;
		}
	}
	
	/** Allows App to check if game is over
	 * @return won boolean stored in this world
	 */
	public boolean won() {
		boolean won = this.gameWon;
		return won;
	}
	
	/** Allows blocks to save their move at a specific index
	 * @return What move index number we are up to
	 */
	public int getMoves() {
		int m = moves;
		return m;
	}
	
	/** Link first door to the first switch down,
	 * 	second door to the second switch, etc
	 */
	private void linkDoors() {
		
		// Look through doors
		for (Sprite pDoor : sprites) {
			if (pDoor instanceof Door) {
				
				// For each door, look through switches
				for (Sprite pSwitchPad : sprites) {
					if (pSwitchPad instanceof Switch) {
						
						// If it's not already taken, link it and go to next door
						if (((Switch)pSwitchPad).getDoor() == null) {
							((Switch)pSwitchPad).linkDoor((Door)pDoor);
							break;
						}
					}
				}
			}
		}
	}
}