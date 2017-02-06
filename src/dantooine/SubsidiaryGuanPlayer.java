package dantooine;

import battlecode.common.*;

import java.util.ArrayList;

public strictfp class SubsidiaryGuanPlayer {
    static RobotController rc;
    
    //Constant variables
    static final float epsilon = 1e-08f;
    static final float GARDENER_STRIDE_RADIUS = 1f;
    static final int VISITED_ARR_SIZE = 10; //Number of previous locations recorded
    static final int ALT_VISITED_ARR_SIZE = 15; //For alt movement
    static final int MOVEMENT_GRANULARITY = 8; //Number of possible move directions
    static final int PATIENCE_TOLERANCE = -10; //Maximum patience tolerance
    static final float STUCK_THRESHOLD = 0.2f; //Threshold to call if unit is stuck
    static final int MAX_INIT_TREES = 2; //Build max of 2 trees per gardener in first 100 turns
    static final int MAX_GARDENER_TREES = 5; //Maximum number of trees a gardener can spawn
    static final int MAX_GARDENER_WANDER_RADIUS = 4; //Maximum radius a gardener can wander
    static final int GARDENER_RESET_DURATION = 10; //Resets the gardener's actions if it cannot finish within this number of turns
    static final int SOLDIER_DELAY = 100; //Number of turns to spawn a new soldier
    static final int GARDENER_DELAY = 100; //Number of turns to spawn a new gardener
    static final float SOLDIER_SHOOT_RANGE = 64f; //This is a squared distance
    static final float TARGET_RELEASE_DIST = 9f; //This is a square distance. Assume you have reached your destination if your target falls within this distance
    static final Direction[] TREE_DIR = new Direction[]{new Direction((float)(Math.PI / 3)), new Direction((float)(2 * Math.PI / 3)), new Direction((float)(Math.PI)),
    		new Direction((float)(4 * Math.PI / 3)), new Direction((float)(5 * Math.PI / 3))};
    static final Direction SPAWN_DIR = new Direction(0f);
    static final int GARDENER_FREE_RADIUS = 5; //Radius around gardener that needs to be free
    static final int MAX_STOCKPILE_BULLET = 500; //Maximum bullets in stockpile before donation occurs
    static final int SOLDIER_ALARM_RELEVANCE = 5; //Relevance of a soldier alarm
    static final int LUMBERJACK_DELAY = 10; //Delay for spawning a lumberjack if not settled
    
    //Archon specific variables
    static int gardenerTimer;
    
    //Robot specific variables
    static boolean isSet; //Whether important local variables are set
    static int timeLived; //Time since spawn
    static int patience; //For movement
    static ArrayList<MapLocation> visitedArr; //For movement
    static MapLocation target; //This is the position the robot will move to
    static boolean inCombat; //Is this robot in combat?
    
    //Gardener specific variables
    static boolean spawnedLumberjack;
    static boolean settledDown;
    static int resetTimer;
    static int soldierTimer;
    static int lumberjackTimer;
    
    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable; //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise
    
    /*
     * Broadcast Channels:
     * 20 => left border, 21 => top border, 22 => right border, 23 => bottom border
     * 10 => turn enemy scouted, 11 => enemy scouted position [Only updated when in combat]
     */
    
    /*
     * General TODO:
     * 1. Work on micro for soldiers => Reduce friendly fire
     * 2. Soldiers should micro away from lumberjacks
     * 3. Find a way to determine map density then choose the appropriate bot
     */

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        SubsidiaryGuanPlayer.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
    	try{
	        switch (rc.getType()) {
	            case ARCHON:
	                runArchon();
	                break;
	            case SOLDIER:
	                runSoldier();
	                break;
	            case LUMBERJACK:
	            	runLumberjack();
	            	break;
	            case SCOUT:
	            	runScout();
	            	break;
	        }
    	}
    	catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
    		e.printStackTrace();
    	}
	}

    static void runArchon(){
    	if (!isSet){
    		gardenerTimer = 0;
    		isSet = true;
    	}
    	//Movement Code
    	MapLocation myLocation = rc.getLocation(); //Location of archon
    	
    	//Spawn Code
    	if (rc.getRoundNum() >= gardenerTimer){
    		try {
    			if (rc.getRoundNum() <= 5){
	    			//Update known map bounds
	    			rc.broadcast(20, 0);
	    			rc.broadcast(21,  0);
	    			rc.broadcast(22, 600);
	    			rc.broadcast(23, 600);
    			}
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }


	
	static void runLumberjack(){
		MapLocation myLoc = rc.getLocation();
		//Set important variables
		if (!isSet){
			target = null;
			targetInfo = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			isInterruptable = false;
			isSet = true;
			inCombat = false;
		}
		//Look for nearby enemies
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		if (!inCombat && nearbyEnemies.length > 0){
			//Go into combat
			inCombat = true;
		}
		if (inCombat && nearbyEnemies.length == 0){
			//Snap out of combat
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : nearbyEnemies){
				float dist = enemy.getLocation().distanceSquaredTo(myLoc);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(myLoc);
			//Attack nearest enemy if possible
			//No micro here => He should micro to a position with the most enemies and least allies
			if (rc.canStrike()){
				try {
					rc.strike();
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
		}
		else{
			//Chop down trees if not in combat
			if (target == null || isInterruptable){ //Find a new target
				TreeInfo [] nearbyTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
				TreeInfo nearestTree = null;
				//Find nearest tree
				float minDist = 100000f;
				for (TreeInfo tree : nearbyTrees){
					float dist = myLoc.distanceSquaredTo(tree.getLocation());
					if (dist < minDist){
						minDist = dist;
						nearestTree = tree;
					}
				}
				if (nearestTree != null){ //No more trees
					target = nearestTree.getLocation();
					targetInfo = nearestTree;
					isInterruptable = false;
				}
				else if (target == null){ //Don't replace with scouting if target is not null
					//No more trees => TODO: Some scouting
					target = newScoutingLocation(20); //Go to a random scouting location with 20 attempts
					isInterruptable = true;
				}
			}
			if (target != null){
				//Check if has approached target
				boolean done = false;
				if (rc.canShake(target)){
					try{
						rc.shake(target);
						done = true;
					}
					catch (GameActionException e){
						e.printStackTrace();
					}
				}
				if (rc.canChop(target)){
					try{
						//System.out.println("Chop chop");
						rc.chop(target);
						done = true;
					}
					catch (GameActionException e){
						e.printStackTrace();
					}
				}
				
				//If target is gone
				if (myLoc.isWithinDistance(target, 1)){
					target = null;
				}
				/*if (target != null){
					System.out.println("Target: " + target);
				}
				else{
					System.out.println("Null Target");
				}*/
				//Move toward target if not in range
				if (!done && target != null){
					moveToTarget(myLoc);
				}
			}
		}
	}

    static void runSoldier() {
    	MapLocation myLoc = rc.getLocation();
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			
			isSet = true;
		}
		//Look for nearby enemies
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		if (!inCombat && nearbyEnemies.length > 0){
			//Go into combat
			inCombat = true;
			target = null;
			//Call all noncombat soldiers to enemy position
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : nearbyEnemies){
				float dist = enemy.getLocation().distanceSquaredTo(myLoc);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			spotEnemy(nearestEnemy.getLocation());
		}
		if (!inCombat){
			//If someone has spotted an enemy
			try{
				int turnSpotted = rc.readBroadcast(10);
				int locationSpotted = rc.readBroadcast(11);
				//Alarm all other soldiers
				if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE){
					target = intToMapLocation(locationSpotted);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && nearbyEnemies.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : nearbyEnemies){
				float dist = enemy.getLocation().distanceSquaredTo(myLoc);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(myLoc);
			//Attack nearest enemy if possible
			//No micro here => Just shooting bullets at the nearest enemy
			if (myLoc.isWithinDistance(target, SOLDIER_SHOOT_RANGE)){
				try {
					if (rc.canFirePentadShot()){
						rc.firePentadShot(myLoc.directionTo(target));
					}
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
			
		}
		else{
			if (target == null){
				target = newScoutingLocation(20);
			}
			if (target != null){
				/*try {
					rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				moveToTarget(myLoc);
				if (target != null){
					if (myLoc.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
						target = newScoutingLocation(20);
					}
				}
			}
		}
		if (target != null){
			//System.out.println("Target: "+target);
			//System.out.println("myPos: "+myLoc);
			//System.out.println("inCombat: "+inCombat);
		}
    }
    
    static void runScout() {
    	MapLocation myLoc = rc.getLocation();
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			
			isSet = true;
		}
		//Look for nearby enemies
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		if (!inCombat && nearbyEnemies.length > 0){
			//Go into combat
			inCombat = true;
			target = null;
			//Call all noncombat soldiers to enemy position
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : nearbyEnemies){
				float dist = enemy.getLocation().distanceSquaredTo(myLoc);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			spotEnemy(nearestEnemy.getLocation());
		}
		if (!inCombat){
			//If someone has spotted an enemy
			try{
				int turnSpotted = rc.readBroadcast(10);
				int locationSpotted = rc.readBroadcast(11);
				//Alarm all other soldiers
				if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE){
					target = intToMapLocation(locationSpotted);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && nearbyEnemies.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : nearbyEnemies){
				float dist = enemy.getLocation().distanceSquaredTo(myLoc);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(myLoc);
			//Attack nearest enemy if possible
			//No micro here => Just shooting bullets at the nearest enemy
			if (myLoc.isWithinDistance(target, SOLDIER_SHOOT_RANGE)){
				try {
					if (rc.canFireSingleShot()){
						rc.fireSingleShot(myLoc.directionTo(target));
					}
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
			
		}
		else{
			if (target == null){
				target = newScoutingLocation(20);
			}
			if (target != null){
				/*try {
					rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				moveToTarget(myLoc);
				if (target != null){
					if (myLoc.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
						target = newScoutingLocation(20);
					}
				}
			}
		}
		if (target != null){
			//System.out.println("Target: "+target);
			//System.out.println("myPos: "+myLoc);
			//System.out.println("inCombat: "+inCombat);
		}
    }
    
    static boolean spawnUnit(RobotType type, Direction dir){
    	if (rc.canBuildRobot(type, dir)){
    		try {
				rc.buildRobot(type, dir);
				return true;
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    	return false;
    }
    
    //Spawns a unit in a random direction. Returns true on success
    static boolean spawnUnitRandom(int num_attempts, RobotType type){
    	while (num_attempts > 0){
    		Direction dir = randomDirection();
    		if (rc.canBuildRobot(type, dir)){
    			try{
    				rc.buildRobot(type, dir);
    				return true;
    			} catch (Exception e){
    				e.printStackTrace();
    			}
    		}
    		num_attempts --;
    	}
    	return false;
    }
    
    //Checks whether a float is an integer value
    static boolean isIntegral(float f){
    	return (Math.abs(Math.floor(f) - f) <= epsilon);
    }
    
    static Direction randomDirection(){
    	return new Direction((float)Math.random(), (float)Math.random());
    }
    
    static Direction randomCardinalDirection(){
    	double rnd = Math.random();
    	if (rnd <= 0.25){
    		return new Direction(1,0);
    	}
    	else if (rnd <= 0.5){
    		return new Direction(-1,0);
    	}
    	else if (rnd <= 0.75){
    		return new Direction(0,-1);
    	}
    	else{
    		return new Direction(0,1);
    	}
    }
    
    
    //Another way to generate a scouting location => should integrate with new bounds mechanics
    /*static MapLocation defensiveScoutLocation(MapLocation myLoc, float dist){
    	if (Math.random() <= 0.66){
    		//Go toward base
    		Direction dir = new Direction(myLoc, rc.getInitialArchonLocations(rc.getTeam())[0]);
    		Direction finalDir = addRandomAngle(dir, 90);
    		return myLoc.add(finalDir, dist);
    	}
    	else{
    		//Go toward enemy
    		Direction dir = new Direction(myLoc, rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
    		Direction finalDir = addRandomAngle(dir, 90);
    		return myLoc.add(finalDir, dist);
    	}
    }*/
    
    //Add a random angle to a direction
    static Direction addRandomAngle(Direction dir, float angle){ //Angle in degrees
    	return new Direction((float)(dir.radians + (Math.random() - 0.5) * angle / 90 * Math.PI) );
    }
    
    
    //Movement Function
    //Note: MapLocation target must have been set, visitedArr and patience should be initialised
    static void moveToTarget(MapLocation myLoc){ 
    	//System.out.println(myLoc);
    	//System.out.println(visitedArr);
    	/*if (rc.getType() == RobotType.GARDENER){
    		System.out.println("Target: "+target);
    	}*/
    	Direction initDir = rc.getLocation().directionTo(target); //Direction chosen 
		boolean hasMoved = false;
		for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
			if (i == 0){ //Try the trivial case
				Direction approachDir = myLoc.directionTo(target);
				MapLocation closestApproach = target.subtract(approachDir, 1 + rc.getType().bodyRadius);
				if (rc.canMove(closestApproach)){
					try {
						//System.out.println("Closest Approach !!!");
						rc.move(closestApproach);
					} catch (GameActionException e) {
						e.printStackTrace();
					}
					break;
				}
			}
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
			}
			
			if (rc.canMove(chosenDir)){
				boolean isValid = true;
				//This is irrelevant
				/*for (MapLocation loc : visitedArr){ 
					if (loc.equals(myLoc.add(chosenDir))){
						isValid = false;
						break;
					}
				}*/
				if (isValid){
					try{
						rc.move(chosenDir);
						hasMoved = true;
						break;
					}
					catch (GameActionException e){
						e.printStackTrace();
					}
				}
			}
			else if (i == 0){ //Detect if target is out of bounds
				try {
					if (rc.canSenseAllOfCircle(myLoc.add(chosenDir), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(chosenDir), rc.getType().bodyRadius)){
						//System.out.println("I think I'm out of bounds");
						//System.out.println(myLoc);
						//Target is out of bounds
						target = null;
						//Update known bounds
						updateBorders(myLoc);
					}
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
		}
		if (visitedArr.size() >= VISITED_ARR_SIZE){
			//Alt way of checking if stuck
			float expecteddist = rc.getType().strideRadius * VISITED_ARR_SIZE;
			float actualdist = visitedArr.get(0).distanceTo(myLoc);
			if (actualdist / expecteddist < STUCK_THRESHOLD && rc.getType() != RobotType.SOLDIER){ //Help I'm stuck [Gardener no longer moves]
				//Not sure if this helps to avoid getting stuck
				target = null; //Reset target
			}
			visitedArr.remove(0);
		}
		visitedArr.add(rc.getLocation());
    }
    
    
    static void updateBorders(MapLocation myLoc){
    	//0 => left border, 1 => top border, 2 => right border, 3 => bottom border
    	Direction[] dir = {new Direction(-1, 0), new Direction(0, -1), new Direction(1,0), new Direction(0,1)};
    	for (int i = 0; i < 4; i++){
	    	try{
	    		if (rc.getType() == RobotType.SOLDIER || rc.getType() == RobotType.SCOUT){
	    			MapLocation newLoc = myLoc.add(dir[i], 7);
	    		}
	    		MapLocation newLoc = myLoc.add(dir[i], 4);
	    		/*if (rc.canSenseLocation(newLoc)){
	    			System.out.println("Hai");
	    		}
	    		if (rc.onTheMap(newLoc)){
	    			System.out.println("HaiHai");
	    		}*/
	    		//System.out.println(newLoc);
	    		if (rc.canSenseLocation(newLoc) && !rc.onTheMap(newLoc)){
	    			if (i % 2 == 0){
	    				System.out.println("Updated border " + i + " : " + myLoc.x);
	    				rc.broadcast(i, (int)myLoc.x);
	    			}
	    			else{
	    				System.out.println("Updated border " + i + " : " + myLoc.y);
	    				rc.broadcast(i, (int)myLoc.y);
	    			}
	    			
	    		}
	    	}
	    	catch (GameActionException e){
	    		e.printStackTrace();
	    	}
    	}
    }
    
    static int getClosestGridDirection(Direction query){ //0 => left, 1 => up, 2 => right, 3 => down
    	Direction[] directions = new Direction[]{new Direction(-1,0), new Direction(0, -1), new Direction(1, 0), new Direction(0, 1)}; //left up right down
    	float smallestdiff = 1000f;
    	int bestdir = -1;
    	for (int i = 0; i < 4; i++){
    		float diff = query.radiansBetween(directions[i]);
    		if (diff < smallestdiff){
    			smallestdiff = diff;
    			bestdir = i;
    		}
    	}
    	//System.out.println("Query: "+query);
    	//System.out.println("Best: " + bestdir);
    	return bestdir;
    }
    
    //Returns whether the location is within map bounds based on gathered data
    static boolean withinMapBounds(MapLocation loc){
    	// 0 => left border, 1 => top border, 2 => right border, 3 => bottom border
    	try {
			int leftborder = rc.readBroadcast(20);
			int topborder = rc.readBroadcast(21);
			int rightborder = rc.readBroadcast(22);
			int bottomborder = rc.readBroadcast(23);
			if (leftborder >= loc.x || rightborder <= loc.x || topborder >= loc.y || bottomborder <= loc.y){
				return false;
			}
			return true;
		} catch (GameActionException e) {
			e.printStackTrace();
			return false;
		}
    	
    }
    
    //Generate a new maplocation which is not yet scouted
    static MapLocation newScoutingLocation(int max_attempts){
    	try {
			int leftborder = rc.readBroadcast(20);
			int topborder = rc.readBroadcast(21);
			int rightborder = rc.readBroadcast(22);
			int bottomborder = rc.readBroadcast(23);
			//System.out.println("Borders: "+ leftborder + " " + topborder + " " + rightborder + " " + bottomborder);
			while (max_attempts > 0){
				float x = (float)(leftborder + (rightborder - leftborder) * Math.random());
				float y = (float)(topborder + (bottomborder - topborder) * Math.random());
				MapLocation loc = new MapLocation(x,y);
				if (!rc.canSenseLocation(loc)){
					//System.out.println("x: " + x + " y: " + y);
					return loc;
				}
				max_attempts --;
			}
			return null;
		} catch (GameActionException e) {
			e.printStackTrace();
			return null;
		}
    }
    
    
    static int mapLocationToInt(MapLocation loc){
    	return ((int)(loc.x) * 1000 + (int)(loc.y));
    }
    
    static MapLocation intToMapLocation(int loc){
    	return new MapLocation((float)(loc / 1000), (float)(loc % 1000));
    }
    
    static void spotEnemy(MapLocation enemyLoc){
    	try{
			rc.broadcast(10, rc.getRoundNum());
    		rc.broadcast(11, mapLocationToInt(enemyLoc));
    		
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }
    
    static void donate(){
    	if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET){
    		try {
				rc.donate((float)(Math.floor((rc.getTeamBullets() - MAX_STOCKPILE_BULLET) / 10.0) * 10.0));
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }
    
    
}
