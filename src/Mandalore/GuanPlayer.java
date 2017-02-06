package Mandalore;

import battlecode.common.*;

import java.util.ArrayList;

public class GuanPlayer {
    static RobotController rc;
    
    //Constant variables
    static final float epsilon = 1e-08f;
    static final float GARDENER_STRIDE_RADIUS = 1f;
    static final int VISITED_ARR_SIZE = 15; //Number of previous locations recorded
    static final int ALT_VISITED_ARR_SIZE = 15; //For alt movement
    static final int MOVEMENT_GRANULARITY = 24; //Number of possible move directions
    static final int MOVEMENT_GRANULARITY_MICRO = 8; //Number of possible move directions when conducting micro
    static final int PATIENCE_TOLERANCE = -10; //Maximum patience tolerance
    static final float STUCK_THRESHOLD = 0.2f; //Threshold to call if unit is stuck
    static final int MAX_INIT_TREES = 2; //Build max of 2 trees per gardener in first 100 turns
    static final int MAX_GARDENER_TREES = 6; //Maximum number of trees a gardener can spawn
    static final int MAX_GARDENER_WANDER_RADIUS = 4; //Maximum radius a gardener can wander
    static final int GARDENER_RESET_DURATION = 10; //Resets the gardener's actions if it cannot finish within this number of turns
    static final float SOLDIER_SHOOT_RANGE = 64f; //This is a squared distance
    static final float TARGET_RELEASE_DIST = 9f; //This is a square distance. Assume you have reached your destination if your target falls within this distance
    static final Direction[] TREE_DIR = new Direction[]{new Direction(0f), new Direction((float)(Math.PI / 3)), new Direction((float)(2 * Math.PI / 3)), new Direction((float)(Math.PI)),
    		new Direction((float)(4 * Math.PI / 3)), new Direction((float)(5 * Math.PI / 3))};
    static final int MIN_ACCEPTABLE_TREES = 4; //Min number of trees that gardener can plant
    static final int MAX_STOCKPILE_BULLET = 500; //Maximum bullets in stockpile before donation occurs
    static final int SOLDIER_ALARM_RELEVANCE = 5; //Relevance of a soldier alarm
    static final float SOLDIER_MICRO_DIST = 64f; //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_MICRO_DIST = 64f; //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_AVOIDANCE_RANGE = 5f; //How far to avoid lumberjacks
    static final float BULLET_SENSE_RANGE = 5f; //The range at which bullets are sensed
    static final float ARCHON_MICRO_DIST = 100f; //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_AVOIDANCE_RANGE_ARCHON = 8f; //How far to avoid lumberjacks
    static final float SCOUT_MICRO_DIST = 64f; //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float OFFENSE_FACTOR = 3f; //Factor to prioritise offense / defense for scout micro [Higher => more offensive]
    static final int TANK_DELAY = 100; //Number of turns to spawn a new tank
    static final int SCOUT_DELAY = 50; //Number of turns to spawn a new scout
    static final float SCOUT_SPAWN_CHANCE = 0.2f; //Chance to spawn a scout
    static final int EARLY_GARDENER_DELAY = 100; //Number of turns to spawn a new gardener
    static final int LATE_GARDENER_DELAY = 75; //Number of turns to spawn a new gardener
    static final int LENIENT_GARDENER_DELAY = 150; //For replenishing gardeners
    static final float UNSTUCK_DIST = 0.25f; //Heuristic to help with unstucking soldiers
    
    //Game phases
    static enum GamePhase {EARLY_GAME, MID_GAME, END_GAME, FINAL_PUSH};
    static final int EARLY_GAME = 0;
    static final int MID_GAME = 400;
    static final int END_GAME = 800;
    static final int FINAL_PUSH_TURNS = 300;
    
    //Macro specific variables
    static final float MIN_GARDENER_BULLETS = 100f; //Minimum stockpile bullets to spawn a gardener
    static final int MAX_UNGOVERNED_GARDENERS = 3; //For early game => Spawn up to this many gardeners without caring about settled ratios
    static final float MAX_TOLERABLE_UNSETTLED_RATIO = 0.8f; //The maximum unsettled ratio [The higher the stricter]
    static final float DEATH_HEALTH_PERCENT = 0.2f; //The percentage to report myself as killed
    static final int GARDENER_SHORT_DELAY = 10; //Number of turns to delay gardener spawn if gardener is not hireable
    static int UNIT_DELAY = 80; //Normal delay for spawning units
    static final int UNIT_SHORT_DELAY = 20; //Delay if unable to spawn unit
    static final int OFFENSE_DELAY = 40; //Delay when enemy base has been spotted
    static final int SCOUT_ATTACK_FORGIVENESS = 40; //Turns when a scout attack would be forgotten
    static final int GARDENER_OFFENSE_DELAY = 20; //Delay when gardeners are getting killed
    static final int SCOUT_NUM = 3; //Maintain 3 scouts at all times
    static final float MIN_DIST_FROM_ARCHON = 5f; //Minimum distance from archon
    static float MIN_GARDENER_SEPARATION = 5f; //Minimum distance between 2 gardeners
    static final float ECON_ADJUSTMENT = 200f; //Build up economy at mid game
    
    //Archon specific variables
    static int gardenerTimer;
    
    //Robot specific variables
    static boolean isSet; //Whether important local variables are set
    static int timeLived; //Time since spawn
    static int patience; //For movement
    static ArrayList<MapLocation> visitedArr; //For movement
    static MapLocation target; //This is the position the robot will move to
    static boolean inCombat; //Is this robot in combat?
    static Direction SPAWN_DIR;
    static boolean reportedDeath; //Has this robot reported its death?
    
    //Gardener specific variables
    static boolean settledDown;
    static int resetTimer;
    static Direction moveDir;
    static int unitTimer;
    static MapLocation initLoc;
    
    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable; //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise
    
    /*
     * Broadcast Channels:-1
     * 0 => left border, 1 => top border, 2 => right border, 3 => bottom border
     * 10 => turn enemy scouted, 11 => enemy scouted position [Only updated when in combat]
     * 12 => turn ally attacked, 13 => turn ally scouted
     * 20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
     * 24 => number of soldiers, 25 => number of tanks
     * 30 => last scout attack turn, 31 => last scout attack location
     * 
     * 99 => number of marked trees
     * 100 - 300 => marked trees
     * 
     */
    
    /*
     * Updates from previous guanplayer:
     * 
     */
    
    /*General TODO: 
     * 
     */

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        GuanPlayer.rc = rc;
        

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        while (true){
        	try{
        		//Change some of the above variables depending on map size
    			float archondist = rc.getInitialArchonLocations(rc.getTeam())[0].distanceTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
				if (archondist < 40 && rc.getInitialArchonLocations(rc.getTeam()).length == 1){ //tiny map
					RushPlayer.run(rc);
					continue;
				}
				else if (archondist < 70){ //medium map
					MIN_GARDENER_SEPARATION = 7f;
					System.out.println("Dist: "+archondist);
					System.out.println("Medium map");
					if (rc.getType() == RobotType.ARCHON){
						rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
					}
				}
				else{ //large map => run farmerplayer2alt
					MIN_GARDENER_SEPARATION = 7f;
					UNIT_DELAY = 120; //build units slower
					System.out.println("Dist: "+archondist);
					System.out.println("Large map");
					if (rc.getType() == RobotType.ARCHON){
						rc.setIndicatorDot(rc.getLocation(), 0, 0, 255);
					}
				}
		        switch (rc.getType()) {
		            case ARCHON:
		                runArchon();
		                break;
		            case GARDENER:
		                runGardener();
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
		            case TANK:
		            	runSoldier();
		            	break;
		        }
		        
		        //Report in if close to dying [No healing in this game]
		        if (rc.getHealth() <= rc.getType().maxHealth * DEATH_HEALTH_PERCENT && !reportedDeath){
		        	reportedDeath = true;
		        	closeToDeath(rc.getType());
		        }
		        
		        //Donate
		        donate();
		        
		        //Shake if possible
		        TreeInfo [] nearbyTrees = rc.senseNearbyTrees(3, Team.NEUTRAL);
		        for (TreeInfo tree : nearbyTrees){
		        	if (rc.canShake(tree.getID())){
		        		rc.shake(tree.getID());
		        		break;
		        	}
		        }
		        
		        //Give up the turn
		        Clock.yield();
        	}
        	catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
        		e.printStackTrace();
        	}
        }
	}

    static void runArchon(){
    	if (!isSet){
    		gardenerTimer = 0;
    		isSet = true;
    	}
    	
    	/*if (hasRecentScoutAttack() && getGamePhase() != GamePhase.EARLY_GAME){
    		gardenerTimer = Math.min(gardenerTimer, GARDENER_OFFENSE_DELAY);
    		//System.out.println("Was recently attacked");
    	}
    	else{
    		//System.out.println("Was not recently attacked");
    	}*/
    	//Look for nearby enemies or bullets
    	MapLocation myLoc = rc.getLocation();
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
		float nearestdist = 1000000f;
		RobotInfo nearestEnemy = null;
		for (RobotInfo enemy : nearbyEnemies){
			float dist = enemy.getLocation().distanceSquaredTo(myLoc);
			if (dist < nearestdist){
				nearestdist = dist;
				nearestEnemy = enemy;
			}
		}
		
		//Debug the number of allies
		/*try{
			20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
			*24 => number of soldiers, 25 => number of tanks
			System.out.println("numGardeners: " + rc.readBroadcast(20));
			System.out.println("numGardeners Settled: " + rc.readBroadcast(21));
			System.out.println("numLumberjacks: " + rc.readBroadcast(22));
			System.out.println("numScouts: " + rc.readBroadcast(23));
			System.out.println("numSoldiers: " + rc.readBroadcast(24));
			System.out.println("numTanks: " + rc.readBroadcast(25));
		}
		catch (GameActionException e){
			e.printStackTrace();
		}*/
		
		//Rebuild gardeners if they r sniped
		int num_gardener = 10;
		try{
			num_gardener = rc.readBroadcast(20);
		}
		catch (GameActionException e){
			e.printStackTrace();
		}
		
		if (num_gardener < rc.getRoundNum() / EARLY_GARDENER_DELAY){
			gardenerTimer = Math.min(gardenerTimer, rc.getRoundNum() - GARDENER_OFFENSE_DELAY);
		}
		
		//System.out.println(nearbyEnemies.length);
		
		if (nearestdist <= ARCHON_MICRO_DIST || nearbyBullets.length > 0){
			archonCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
		}
		
		myLoc = rc.getLocation();
		
		//Fuck this timer it's the end game
		if (getGamePhase() == GamePhase.END_GAME){
			try {
    			int GARDENER_LIMIT = 20; //Number of times to try to spawn a gardener
    			Direction dir = new Direction(rc.getLocation(), rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
    			while (GARDENER_LIMIT >= 0){
    				if (rc.canHireGardener(dir)){
    					if (isGardenerHireable()){
	    					rc.hireGardener(dir);
	    					spawnedNewUnit(RobotType.GARDENER);
	    					break;
    					}
    					else{
    						gardenerTimer = rc.getRoundNum() + GARDENER_SHORT_DELAY; 
    						break;
    					}
    				}
    				//Choose random direction if first direction fails
    				dir = randomDirection();
    				GARDENER_LIMIT --;
    			}
    			if (rc.getRoundNum() <= 5){
	    			//Update known map bounds
	    			rc.broadcast(0, 0);
	    			rc.broadcast(1,  0);
	    			rc.broadcast(2, 600);
	    			rc.broadcast(3, 600);
	    			//Reset last scout spotted turn
	    			rc.broadcast(30, -10000);
    			}
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
		
		//Spawn Code
    	if (rc.getRoundNum() >= gardenerTimer){
    		try {
    			int GARDENER_LIMIT = 10; //Number of times to try to spawn a gardener
    			Direction dir = new Direction(rc.getLocation(), rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
    			while (GARDENER_LIMIT >= 0){
    				if (rc.canHireGardener(dir)){
    					if (isGardenerHireable()){
	    					rc.hireGardener(dir);
	    					if (getGamePhase() == GamePhase.EARLY_GAME){
	    						gardenerTimer = rc.getRoundNum() + EARLY_GARDENER_DELAY;
	    					}
	    					else if (getGamePhase() == GamePhase.MID_GAME){
	    						gardenerTimer = rc.getRoundNum() + LATE_GARDENER_DELAY;
	    					}
	    					spawnedNewUnit(RobotType.GARDENER);
	    					break;
    					}
    					else{
    						gardenerTimer = rc.getRoundNum() + GARDENER_SHORT_DELAY; 
    						break;
    					}
    				}
    				//Choose random direction if first direction fails
    				dir = randomDirection();
    				GARDENER_LIMIT --;
    			}
    			if (rc.getRoundNum() <= 5){
	    			//Update known map bounds
	    			rc.broadcast(0, 0);
	    			rc.broadcast(1,  0);
	    			rc.broadcast(2, 600);
	    			rc.broadcast(3, 600);
	    			//Reset last scout spotted turn
	    			rc.broadcast(30, -10000);
    			}
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
		
    }
    
    //Archon dodging
	static void archonCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies,
			BulletInfo[] nearbyBullets) {
		MapLocation myLoc = rc.getLocation();
    	ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
    	ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
    	boolean hasMoved = false; //Have I moved already?
    	
    	//Process nearby enemies
    	for (RobotInfo enemy : nearbyEnemies){
    		if (enemy.getType() == RobotType.LUMBERJACK){
    			nearbyLumberjacks.add(enemy);
    		}
    		else if (enemy.getType() != RobotType.ARCHON && enemy.getType() != RobotType.GARDENER){
    			nearbyCombatUnits.add(enemy);
    		}
    	}
    	
    	//System.out.println(nearbyLumberjacks);
    	
    	//Move in the 8 directions
    	
    	
    	float maxScore = -1000000f;
    	MapLocation prevtarget = target;
    	if (nearestEnemy != null){
    		target = nearestEnemy.getLocation();
    	}
    	else{
    		//Is this a good idea?
    		target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
    	}
    	
    	Direction initDir = rc.getLocation().directionTo(target); //Direction chosen 
    	Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			
			if (rc.canMove(chosenDir)){
				float score = getArchonScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
		}
		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		return;
		
	}

	static void runGardener(){
		MapLocation enemyLoc = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
		MapLocation myLoc = rc.getLocation();
		//Report any scout attacks
		reportScoutAttack();
		//Whether robot variables are set
		if (!isSet){
			//System.out.println("Hi I'm a gardener!");
			initLoc = myLoc;
			timeLived = 0;
			visitedArr = new ArrayList<MapLocation>();
			isSet = true;
			patience = 0;
			resetTimer = 10000; //Set an arbitrarily large value
			targetInfo = null;
			MapLocation dest = new MapLocation((myLoc.x + enemyLoc.x) / 2, (myLoc.y  + enemyLoc.y) / 2);
			//Add a random angle of +-30 degrees
			Direction dir = new Direction(myLoc, dest);
			Direction finalDir = addRandomAngle(dir, 45); //Not sure if this fans out correctly => Need to test
			target = myLoc.add(finalDir, myLoc.distanceTo(dest));
			settledDown = false; //Will be set to true if a suitable space can be found
			moveDir = randomDirection();
			//Get spawn direction
			float leastDiff = 1000000f;
			for (int i = 0; i < TREE_DIR.length; i++){
				float degDiff = (float)Math.abs(TREE_DIR[i].degreesBetween(myLoc.directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0])));
				if (degDiff < leastDiff){
					leastDiff = degDiff;
					SPAWN_DIR = TREE_DIR[i];
				}
			}
			unitTimer = rc.getRoundNum() + 40;
			//System.out.println("Spawn Dir: "+SPAWN_DIR);
		}
		else{
			timeLived += 1;
		}
		if (!settledDown){
			/*20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
			 * 24 => number of soldiers, 25 => number of tanks*/
			//Try to spawn a lumberjack
			int num_scout = 1;
			int num_lumberjack = 1;
			int num_soldier = 1;
			try{
				num_scout = rc.readBroadcast(23);
				num_lumberjack = rc.readBroadcast(22);
				num_soldier = rc.readBroadcast(24);
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
			/* 1 scout 1 lumberjack
			 * if (rc.getRoundNum() <= 20 && num_scout < 1){ //Spawn scout for early rush
				spawnUnitRandom(20, RobotType.SCOUT);
			}
			if (rc.getRoundNum() <= 20 && num_lumberjack < 1){ //Spawn scout for early rush
				spawnUnitRandom(20, RobotType.LUMBERJACK);
			}*/
			
			/* 1 scout */
			if (rc.getRoundNum() <= 20 && num_soldier < 2){ //Spawn scout for early rush
				spawnUnitRandom(20, RobotType.SOLDIER);
			}
			/* 2 scouts 1 lumberjack */
			/*if (rc.getRoundNum() <= 20 && num_scout < 2){ //Spawn scout for early rush
				spawnUnitRandom(20, RobotType.SCOUT);
			}
			if (rc.getRoundNum() <= 20 && num_lumberjack < 1){ //Spawn scout for early rush
				spawnUnitRandom(20, RobotType.LUMBERJACK);
			}*/
			/* 1 soldier */
			/*if (rc.getRoundNum() <= 20 && num_soldier < 1){ //Spawn soldier for early defence
				spawnUnitRandom(20, RobotType.SOLDIER);
			}*/
			if (rc.getRoundNum() >= unitTimer){
				spawnUnitRandom(20, RobotType.LUMBERJACK);
				unitTimer = rc.getRoundNum() + 20;
			}
			else if (getGamePhase() == GamePhase.END_GAME){
				//Fuck the timer
				spawnUnitRandom(20, RobotType.SOLDIER);
			}
			//Look for a space with a circle of radius 3
			gardenerMove(20);
			myLoc = rc.getLocation();
			
			
			//TODO: Rotate trees to be in line with nearby trees
			RobotInfo[] nearbyFriendlyRobots = rc.senseNearbyRobots(MIN_GARDENER_SEPARATION, rc.getTeam());
			boolean hasNearbyGardener = false;
			for (RobotInfo ally : nearbyFriendlyRobots){
				if (ally.getType() == RobotType.GARDENER || ally.getType() == RobotType.ARCHON){
					hasNearbyGardener = true;
					break;
				}
			}
			int validCnt = 0;
			float mostDiff = -100000f;
			Direction spawn_dir = null;
			for (int i = 0; i < MAX_GARDENER_TREES; i++){
				if (rc.canPlantTree(TREE_DIR[i])){
					validCnt += 1;
					float diff = (float)Math.abs(TREE_DIR[i].degreesBetween(myLoc.directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0])));
					if (diff > mostDiff){
						mostDiff = diff;
						spawn_dir = TREE_DIR[i];
					}
				}
			}
			System.out.println("validCnt: "+validCnt);
			if (validCnt >= MIN_ACCEPTABLE_TREES && !hasNearbyGardener){
				SPAWN_DIR = spawn_dir;
				gardenerHasSettled();
				settledDown = true;
				if (getGamePhase() == GamePhase.EARLY_GAME){ //Immediately spawn units in early game
					unitTimer = 0;
				}
				else{
					unitTimer = UNIT_DELAY;
				}
			}
			
		}
		else{
			if (hasRecentScoutAttack()){
				unitTimer = Math.min(unitTimer, OFFENSE_DELAY);
			}
			
			//System.out.println("Delay: "+(unitTimer - rc.getRoundNum()));
			//System.out.println("Settled Down");
			//Try to spawn a lumberjack
			TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3, rc.getTeam());
			//Plant trees
			if (!hasRecentScoutAttack()){
				if (rc.getRoundNum() <= 100){
					for (int i = 0; i < MAX_INIT_TREES; i++){
						if (TREE_DIR[i].radians != SPAWN_DIR.radians && rc.canPlantTree(TREE_DIR[i])){
							try{
								rc.plantTree(TREE_DIR[i]);
							}
							catch (Exception e){
								e.printStackTrace();
							}
						}
					}
				}
				else {
					for (int i = 0; i < MAX_GARDENER_TREES; i++){
						if (TREE_DIR[i].radians != SPAWN_DIR.radians && rc.canPlantTree(TREE_DIR[i])){
							try{
								rc.plantTree(TREE_DIR[i]);
							}
							catch (Exception e){
								e.printStackTrace();
							}
						}
					}
				}
			}
			//Water
			TreeInfo lowestHealthTree = null;
			float lowestHealth = 1000000f;
			for (TreeInfo tree : nearbyTrees){
				if (tree.getHealth() < lowestHealth){
					lowestHealth = tree.getHealth();
					lowestHealthTree = tree;
				}
			}
			if (lowestHealthTree != null){
				if (rc.canWater(lowestHealthTree.getID())){
					try {
						rc.water(lowestHealthTree.getID());
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}
			if (getGamePhase() == GamePhase.END_GAME){
				//Fuck the timer
				RobotType nextType = getNextUnitType();
				if (nextType != RobotType.ARCHON){
					if (spawnUnit(nextType, SPAWN_DIR)){
						unitTimer = rc.getRoundNum() + UNIT_DELAY;
					}
				}
				else{
					unitTimer = rc.getRoundNum() + UNIT_SHORT_DELAY;
				}
			}
			//Spawn a soldier if timer is up
			else if (rc.getRoundNum() >= unitTimer){
				RobotType nextType = getNextUnitType();
				if (nextType != RobotType.ARCHON){
					if (spawnUnit(nextType, SPAWN_DIR)){
						unitTimer = rc.getRoundNum() + UNIT_DELAY;
					}
				}
				else{
					unitTimer = rc.getRoundNum() + UNIT_SHORT_DELAY;
				}
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
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
		if (!inCombat && nearbyEnemies.length > 0){
			//Go into combat
			inCombat = true;
		}
		/*if (!inCombat && hasRecentScoutAttack()){
			try{
				target = intToMapLocation(rc.readBroadcast(31));
				isInterruptable = false;
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}*/
		if (inCombat && nearbyEnemies.length == 0){
			//Snap out of combat
			inCombat = false;
			target = null;
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
			if (nearestdist <= LUMBERJACK_MICRO_DIST){
				lumberjackCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
			}
			else{
				//Move to nearest enemy's position
				target = nearestEnemy.getLocation();
				moveToTarget(myLoc);
			}
			//Attack nearest enemy if possible
			//No micro here => He should micro to a position with the most enemies and least allies
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
			target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			
			isSet = true;
		}
		//Look for nearby enemies
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
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
		if (!inCombat && hasRecentScoutAttack() && getGamePhase() == GamePhase.EARLY_GAME){
			try{
				target = intToMapLocation(rc.readBroadcast(31));
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && nearbyEnemies.length == 0){
			//Snap out of combat
			target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
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
			
			//Attack before moving
			//System.out.println("Nearest Enemy Dist: "+nearestdist);
			if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0){
				//System.out.println("I'm making my COMBAT MOVE");
				soldierCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
			}
			else{
				//Move to nearest enemy's position
				target = nearestEnemy.getLocation();
				moveToTarget(myLoc);
			}
			
			myLoc = rc.getLocation();
			
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
    	//Broadcast whether scout is present
		/*try {
			rc.broadcast(21, rc.getRoundNum());
		} catch (GameActionException e) {
			e.printStackTrace();
		}*/
    	
    	MapLocation enemyLoc = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
    	MapLocation myLoc = rc.getLocation();
		//Set important variables
		if (!isSet){
			target = enemyLoc;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			
			isSet = true;
		}
		
		//Check if 4 map corners have been updated
    	/*try{
    		//0 => left border, 1 => top border, 2 => right border, 3 => bottom border
    		updateBorders(myLoc);
    		if (rc.readBroadcast(0) == 0){
    			target = new MapLocation(-100, myLoc.y);
    			moveToTarget(myLoc);
    			return;
    		}
    		if (rc.readBroadcast(1) == 0){
    			target = new MapLocation(myLoc.x, -100);
    			moveToTarget(myLoc);
    			return;
    		}
    		if (rc.readBroadcast(2) == 600){
    			target = new MapLocation(700, myLoc.y);
    			moveToTarget(myLoc);
    			return;
    		}
    		if (rc.readBroadcast(3) == 600){
    			target = new MapLocation(myLoc.x, 700);
    			moveToTarget(myLoc);
    			return;
    		}
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}*/
    	
		//Look for nearby enemies
		RobotInfo [] nearbyEnemiesRaw = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
		ArrayList<RobotInfo> nearbyEnemies = new ArrayList<RobotInfo>();
		for (RobotInfo enemy : nearbyEnemiesRaw){
			if (enemy.getType() != RobotType.ARCHON && enemy.getType() != RobotType.SCOUT){
				nearbyEnemies.add(enemy);
			}
		}
		
		if (!inCombat && nearbyEnemies.size() > 0){
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
		if (inCombat && nearbyEnemies.size() == 0){
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
			RobotInfo attackTgt = null;
			
			//Attack before moving
			//System.out.println("Nearest Enemy Dist: "+nearestdist);
			if (nearestdist <= SCOUT_MICRO_DIST || nearbyBullets.length > 0){
				//System.out.println("I'm making my COMBAT MOVE");
				attackTgt = scoutCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
			}
			else{
				//Move to nearest enemy's position
				target = nearestEnemy.getLocation();
				moveToTarget(myLoc);
			}
			
			myLoc = rc.getLocation();
			
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
    
    static void runTank() { //Behaves like a combination of soldier and lumberjack
    	MapLocation myLoc = rc.getLocation();
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			isInterruptable = false; //Can be interrupted to chop trees when scouting
			isSet = true;
		}
		//Look for nearby enemies
		RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
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
			
			//Attack before moving
			//System.out.println("Nearest Enemy Dist: "+nearestdist);
			if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0){
				//System.out.println("I'm making my COMBAT MOVE");
				soldierCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
			}
			else{
				//Move to nearest enemy's position
				target = nearestEnemy.getLocation();
				try{
					rc.move(target);
				}
				catch (GameActionException e){
					e.printStackTrace();
				}
			}
			
			myLoc = rc.getLocation();
			
		}
		else{
			if (target == null){
				//See if can chop down any trees
				target = newScoutingLocation(20);
			}
			if (target != null){
				/*try {
					rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				try{
					rc.move(target);
				}
				catch (GameActionException e){
					e.printStackTrace();
				}
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
    
  //A heuristic to help scouts move in combat
    static RobotInfo scoutCombatMove(RobotInfo nearestEnemy, ArrayList<RobotInfo> nearbyEnemies, BulletInfo[] nearbyBullets){
    	MapLocation myLoc = rc.getLocation();
    	TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
    	RobotInfo nearestGardener = null;
    	float nearestGardenerDist = 1000000f;
    	RobotInfo nearestLumberjack = null;
    	float nearestLumberjackDist = 1000000f;
    	ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
    	ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
    	boolean hasMoved = false; //Have I moved already?
    	
    	//Process nearby enemies
    	for (RobotInfo enemy : nearbyEnemies){
    		if (enemy.getType() == RobotType.LUMBERJACK){
    			float dist = myLoc.distanceSquaredTo(enemy.getLocation());
    			if (dist < nearestLumberjackDist){
    				nearestLumberjackDist = dist;
    				nearestLumberjack = enemy;
    			}
    			nearbyLumberjacks.add(enemy);
    		}
    		else if (enemy.getType() == RobotType.GARDENER){
    			float dist = myLoc.distanceSquaredTo(enemy.getLocation());
    			if (dist < nearestGardenerDist){
    				nearestGardenerDist = dist;
    				nearestGardener = enemy;
    			}
    		}
    		else if (enemy.getType() != RobotType.ARCHON){
    			nearbyCombatUnits.add(enemy);
    		}
    	}
    	
    	//System.out.println(nearbyLumberjacks);
    	
    	//Avoid friendly fire
    	RobotInfo [] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
    	
    	nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    	
    	
    	float nearestDistToGardener = 10000000f;
    	MapLocation bestMoveLoc = null;
    	//See if there is any tree available
    	for (TreeInfo tree : nearbyTrees){
    		boolean canMove = true;
    		for (RobotInfo lumberjack : nearbyLumberjacks){
    			//System.out.println("Dist: "+tree.getLocation().distanceTo(lumberjack.getLocation()));
    			if (tree.getLocation().distanceTo(lumberjack.getLocation()) <= LUMBERJACK_AVOIDANCE_RANGE){
    				canMove = false;
    				break;
    			}
    		}
    		//Direct towards nearest gardener
    		if (canMove && nearestGardener != null){
    			MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestGardener.getLocation()), 0.01f);
    			if (rc.canMove(moveLoc) && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
					float distToGardener = moveLoc.distanceTo(nearestGardener.getLocation());
					if (distToGardener < nearestDistToGardener){
						nearestDistToGardener = distToGardener;
						bestMoveLoc = moveLoc;
					}
    			}
    		}
    		else if (canMove && nearestGardener == null){
    			//Position away from any bullet
    			if (nearestEnemy != null){
    				MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestEnemy.getLocation()), 0.01f);
        			if (rc.canMove(moveLoc) && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
        				try{
        					rc.move(moveLoc);
        					hasMoved = true;
        					return nearestEnemy;
        				}
        				catch (GameActionException e){
        					e.printStackTrace();
        				}
        			}
    			}
    			else if (nearbyBullets.length > 0){
    				MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearbyBullets[0].getLocation()), 0.01f);
    				if (rc.canMove(moveLoc)  && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
    					try{
    						rc.move(moveLoc);
    						hasMoved = true;
    						return nearestLumberjack;
    					}
    					catch (GameActionException e){
    						e.printStackTrace();
    					}
    				}
    			}
    		}
    	}
    	
    	if (!hasMoved && bestMoveLoc != null){
    		try{
    			if (rc.canMove(bestMoveLoc)){
	    			rc.move(bestMoveLoc);
	    			hasMoved = true;
	        		return nearestGardener;
    			}
    		}
    		catch (GameActionException e){
    			e.printStackTrace();
    		}
    	}
    	//If no trees available then try the 8 directions
    	
    	if (hasMoved){
    		if (nearestGardener == null){
    			return nearestLumberjack;
    		}
    		return nearestGardener;
    	}
    	
    	
    	float maxScore = -1000000f;
    	MapLocation prevtarget = target;
    	if (nearestGardener != null){
    		target = nearestGardener.getLocation();
    	}
    	else{
    		//Is this a good idea?
    		target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
    	}
    	Direction initDir = rc.getLocation().directionTo(target); //Direction chosen 
    	Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			
			if (rc.canMove(chosenDir)){
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
				float score = getScoutScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
			else{
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
			}
		}
		
		//Sometimes the best move is not to move
		float score = getScoutScore(myLoc, nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
		if (score > maxScore){
			maxScore = score;
			finalDir = null;
		}
		
		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		
		//Conduct attack
    	if (rc.canFireSingleShot()){
    		MapLocation attackLoc = null;
    		if (nearestGardener != null){
    			attackLoc = nearestGardener.getLocation();
    		}
    		else if (nearestLumberjack != null){
    			attackLoc = nearestLumberjack.getLocation();
    		}
    		else{
    			attackLoc = nearestEnemy.getLocation();
    		}
    		if (attackLoc != null){
    			Direction shotDir = myLoc.directionTo(attackLoc);
    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
    			boolean safeToShoot = true;
    			for (RobotInfo ally : nearbyAllies){
    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
    					safeToShoot = false;
    					break;
    				}
    			}
    			if (safeToShoot){
	    			try {
						rc.fireSingleShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
		
		
		if (nearestGardener == null){
			return nearestLumberjack;
		}
		return nearestGardener;
    }
    
  //A heuristic to help soldiers move in combat
    static void soldierCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
    	MapLocation myLoc = rc.getLocation();
    	ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
    	ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
    	ArrayList<RobotInfo> nearbyNonCombatUnits = new ArrayList<RobotInfo>();
    	boolean hasMoved = false; //Have I moved already?
    	
    	//Process nearby enemies
    	for (RobotInfo enemy : nearbyEnemies){
    		if (enemy.getType() == RobotType.LUMBERJACK){
    			nearbyLumberjacks.add(enemy);
    		}
    		else if (enemy.getType() != RobotType.ARCHON && enemy.getType() != RobotType.GARDENER){
    			nearbyCombatUnits.add(enemy);
    		}
    		else{
    			nearbyNonCombatUnits.add(enemy);
    		}
    	}
    	
    	//System.out.println(nearbyLumberjacks);
    	
    	//Avoid friendly fire
    	RobotInfo [] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
    	//Should I use -1?
    	
    	boolean hasFired = false;
    	
    	
    	
    	nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    	//Move in the 8 directions
    	
    	
    	float maxScore = -1000000f;
    	MapLocation prevtarget = target;
    	if (nearestEnemy != null){
    		target = nearestEnemy.getLocation();
    	}
    	else{
    		//Is this a good idea?
    		target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
    	}
    	
    	Direction initDir = rc.getLocation().directionTo(target); //Direction chosen 
    	Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			
			if (rc.canMove(chosenDir)){
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
				float score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyNonCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
			else{
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
			}
		}
		
		//Sometimes the best move is not to move
		float score = getSoldierScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyNonCombatUnits, nearbyBullets);
		if (score > maxScore){
			maxScore = score;
			finalDir = null;
		}
		
		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		
		//Conduct attack
    	//Avoid friendly fire
    	if (rc.canFirePentadShot()){
    		MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
    		if (attackLoc != null){
    			boolean safeToShoot = true;
    			for (int i = 0; i < 5; i++){
    				Direction shotDir = null;
    				if (i <= 1){
    					shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (2 - i));
    				}
    				else if (i == 2){
    					shotDir = myLoc.directionTo(attackLoc);
    				}
    				else{
    					shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (i - 2));
    				}
	    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
	    			
	    			for (RobotInfo ally : nearbyAllies){
	    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
	    					safeToShoot = false;
	    					break;
	    				}
	    			}
	    			if (!safeToShoot){
	    				break;
	    			}
    			}
    			if (safeToShoot){
	    			try {
						rc.firePentadShot(myLoc.directionTo(attackLoc));
						hasFired = true;
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
    	if (!hasFired && rc.canFireTriadShot()){
    		MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
    		if (attackLoc != null){
    			boolean safeToShoot = true;
    			for (int i = 0; i < 3; i++){
    				Direction shotDir = null;
    				if (i == 0){
    					shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
    				}
    				else if (i == 1){
    					shotDir = myLoc.directionTo(attackLoc);
    				}
    				else{
    					shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
    				}
	    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
	    			
	    			for (RobotInfo ally : nearbyAllies){
	    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
	    					safeToShoot = false;
	    					break;
	    				}
	    			}
	    			if (!safeToShoot){
	    				break;
	    			}
    			}
    			if (safeToShoot){
	    			try {
						rc.fireTriadShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
    	if (!hasFired && rc.canFireSingleShot()){
    		MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
    		if (attackLoc != null){
    			boolean safeToShoot = true;
				Direction shotDir = myLoc.directionTo(attackLoc);
    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
    			
    			for (RobotInfo ally : nearbyAllies){
    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
    					safeToShoot = false;
    					break;
    				}
    			}
    			if (safeToShoot){
	    			try {
						rc.fireSingleShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
		return;
    }
    
  //A heuristic to help lumberjacks move in combat
    static void lumberjackCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
    	MapLocation myLoc = rc.getLocation();
    	ArrayList<RobotInfo> hitEnemies = new ArrayList<RobotInfo>();
    	boolean hasMoved = false; //Have I moved already?
    	
    	//Process nearby enemies
    	for (RobotInfo enemy : nearbyEnemies){
    		if (enemy.getLocation().distanceTo(myLoc) <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
    			hitEnemies.add(enemy);
    		}
    	}
    	
    	
    	//Should I use -1?
    	
    	//Move then attack
    	
    	nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    	//Move in the 8 directions
    	
    	
    	float maxScore = -1000000f;
    	MapLocation prevtarget = target;
    	if (nearestEnemy != null){
    		target = nearestEnemy.getLocation();
    	}
    	else{
    		//Is this a good idea?
    		target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
    	}
    	
    	Direction initDir = rc.getLocation().directionTo(target); //Direction chosen 
    	Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			
			if (rc.canMove(chosenDir)){
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
				float score = getLumberjackScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
			else{
				//rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
			}
		}
		
		//Sometimes the best move is not to move
		float score = getLumberjackScore(myLoc, nearbyBullets);
		if (score > maxScore){
			maxScore = score;
			finalDir = null;
		}
		
		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		
		//Carry out attack
		RobotInfo [] nearbyAllies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
		nearbyEnemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
		
		if (nearbyAllies.length <= nearbyEnemies.length){
			if (rc.canStrike()){
				try {
					rc.strike();
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
		}
		
    }
    
    //Soldier score for micro.
    static float getLumberjackScore(MapLocation loc, BulletInfo[] nearbyBullets){
    	float ans = 0;
    	RobotInfo [] nearbyAllies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius + 1.0f, rc.getTeam());
    	RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius + 1.0f, rc.getTeam());
    	
    	//Avoid bullets [Heavy penalty]
    	for (BulletInfo bullet : nearbyBullets){
    		if (willCollideWithMe(loc, bullet)){
    			ans -= bullet.getDamage() * 1000f;
    		}
    	}
    	
    	//Avoid allied robots [Heavy penalty]
    	for (RobotInfo ally : nearbyAllies){
    		float dist = loc.distanceTo(ally.getLocation());
    		if (dist <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
    			ans -= RobotType.LUMBERJACK.attackPower * 1000f;
    		}
    		else{
    			ans -= distanceScaling(dist);
    		}
    	}
    	
    	//Go close to enemy robots [Heavy reward if in range, slight reward if not in range]
    	for (RobotInfo enemy : nearbyEnemies){
    		float dist = loc.distanceTo(enemy.getLocation());
    		if (dist <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
    			ans += RobotType.LUMBERJACK.attackPower * 1000f;
    		}
    		else{
    			ans += distanceScaling(dist);
    		}
    	}
    	
    	
    	return ans;
    }
    
    static boolean spawnUnit(RobotType type, Direction dir){
    	if (rc.canBuildRobot(type, dir)){
    		try {
				rc.buildRobot(type, dir);
				spawnedNewUnit(type);
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
    				spawnedNewUnit(type);
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
    	//System.out.println(new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f));
    	return new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f);
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
			
			if (rc.canMove(chosenDir) || canMoveTank(chosenDir)){
				boolean isValid = true;
				//This is irrelevant
				for (MapLocation loc : visitedArr){ 
					if (loc.distanceTo(myLoc.add(chosenDir, rc.getType().strideRadius)) <= UNSTUCK_DIST){
						isValid = false;
						break;
					}
				}
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
			if (actualdist / expecteddist < STUCK_THRESHOLD && rc.getType() != RobotType.SOLDIER && rc.getType() != RobotType.TANK && rc.getType() != RobotType.SCOUT){ //Help I'm stuck [Gardener no longer moves]
				//Not sure if this helps to avoid getting stuck
				target = null; //Reset target
			}
			visitedArr.remove(0);
		}
		visitedArr.add(rc.getLocation());
    }
    
  //This assumes that rc.canMove returned false. Returns false when not a tank
    static boolean canMoveTank(Direction chosenDir){
    	if (rc.getType() != RobotType.TANK){
    		return false;
    	}
    	MapLocation newPos = rc.getLocation().add(chosenDir, RobotType.TANK.strideRadius);
    	RobotInfo [] nearbyRobots = rc.senseNearbyRobots(newPos, RobotType.TANK.bodyRadius, null);
    	TreeInfo [] nearbyTrees = rc.senseNearbyTrees(newPos, RobotType.TANK.bodyRadius, rc.getTeam());
    	return (nearbyRobots.length == 0 && nearbyTrees.length == 0);
    }
    
    
    static void updateBorders(MapLocation myLoc){
    	//0 => left border, 1 => top border, 2 => right border, 3 => bottom border
    	Direction[] dir = {new Direction(-1, 0), new Direction(0, -1), new Direction(1,0), new Direction(0,1)};
    	for (int i = 0; i < 4; i++){
	    	try{
	    		if (rc.getType() == RobotType.SCOUT){
	    			MapLocation newLoc = myLoc.add(dir[i], 10);
	    		}
	    		if (rc.getType() == RobotType.SOLDIER){
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
			int leftborder = rc.readBroadcast(0);
			int topborder = rc.readBroadcast(1);
			int rightborder = rc.readBroadcast(2);
			int bottomborder = rc.readBroadcast(3);
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
			int leftborder = rc.readBroadcast(0);
			int topborder = rc.readBroadcast(1);
			int rightborder = rc.readBroadcast(2);
			int bottomborder = rc.readBroadcast(3);
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
    
    static MapLocation newDefenseLocation(int max_attempts){
    	try {
			int leftborder = rc.readBroadcast(0);
			int topborder = rc.readBroadcast(1);
			int rightborder = rc.readBroadcast(2);
			int bottomborder = rc.readBroadcast(3);
			//System.out.println("Borders: "+ leftborder + " " + topborder + " " + rightborder + " " + bottomborder);
			while (max_attempts > 0){
				float x = (float)(leftborder + (rightborder - leftborder) * Math.random());
				float y = (float)(topborder + (bottomborder - topborder) * Math.random());
				MapLocation loc = new MapLocation(x,y);
				if (rc.canSenseLocation(loc)){
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
    
    
    //Generate a new mapLocation which the system thinks a gardener can move onto
    /*static MapLocation newGardenerMoveLocation(int max_attempts){
    	try {
			int leftborder = rc.readBroadcast(0);
			int topborder = rc.readBroadcast(1);
			int rightborder = rc.readBroadcast(2);
			int bottomborder = rc.readBroadcast(3);
			//System.out.println("Borders: "+ leftborder + " " + topborder + " " + rightborder + " " + bottomborder);
			while (max_attempts > 0){
				float x = (float)(leftborder + (rightborder - leftborder) * Math.random());
				float y = (float)(topborder + (bottomborder - topborder) * Math.random());
				MapLocation loc = new MapLocation(x,y);
				if (!rc.canSenseAllOfCircle(loc, GARDENER_FREE_RADIUS)){
					//System.out.println("x: " + x + " y: " + y);
					return loc;
				}
				else{
					if (!rc.isCircleOccupiedExceptByThisRobot(loc, GARDENER_FREE_RADIUS)){
						return loc;
					}
				}
				max_attempts --;
			}
			return null;
		} catch (GameActionException e) {
			e.printStackTrace();
			return null;
		}
    }*/
    
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
    
    static void callForHelp(MapLocation enemyLoc){
    	try{
    		rc.broadcast(12, rc.getRoundNum());
    		rc.broadcast(13, mapLocationToInt(enemyLoc));
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }
    
    static void donate(){
    	/*if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET){
    		try {
				rc.donate((float)(Math.floor((rc.getTeamBullets() - MAX_STOCKPILE_BULLET) / rc.getVictoryPointCost()) * rc.getVictoryPointCost()));
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}*/
    	if (rc.getTeamBullets() >= rc.getVictoryPointCost() * 1000f){
    		try{
    			rc.donate(rc.getTeamBullets());
    		}
    		catch (GameActionException e){
    			e.printStackTrace();
    		}
    	}
    }
    
    static void gardenerMove(int num_attempts){ //Bounces the gardener around
    	while (num_attempts > 0){
	    	if (!rc.canMove(moveDir)){
	    		moveDir = randomDirection();
	    	}
	    	else{
	    		try{
	    			rc.move(moveDir);
	    			break;
	    		}
	    		catch (GameActionException e){
	    			e.printStackTrace();
	    		}
	    	}
	    	num_attempts --;
    	}
    }
    
  //Soldier score for micro.
    static float getSoldierScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, ArrayList<RobotInfo> nearbyNonCombatRobots, BulletInfo[] nearbyBullets){
    	float ans = 0;
    	//Avoid lumberjacks
    	for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
    		if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
    			//System.out.println(lumberjack.getLocation().distanceTo(loc));
    			ans -= rc.getType().attackPower * 1000f;
    		}
    	}
    	
    	//Avoid bullets [Heavy penalty]
    	for (BulletInfo bullet : nearbyBullets){
    		if (willCollideWithMe(loc, bullet)){
    			ans -= bullet.getDamage() * 1000f;
    		}
    	}
    	
    	//Be as far as possible from the soldiers
    	for (RobotInfo robot : nearbyCombatRobots){
    		ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
    	}
    	
    	//Be as close as possible to the non combat units
    	for (RobotInfo robot : nearbyNonCombatRobots){
    		ans += distanceScaling(loc.distanceTo(robot.getLocation()));
    	}
    	
    	return ans;
    }
    
    static float distanceScaling(float dist){
    	if (dist <= 1.0f){
    		return 10f; //Very good
    	}
    	else{
    		return 10f / dist; //Not so good
    	}
    }
    
  //Scout Score for micro
    static float getScoutScore(MapLocation loc, RobotInfo nearestGardener, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
    	float ans = 0;
    	//Avoid lumberjacks
    	for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
    		if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
    			//System.out.println(lumberjack.getLocation().distanceTo(loc));
    			ans -= RobotType.LUMBERJACK.attackPower * 1000f;
    		}
    	}
    	
    	//Avoid bullets [Heavy penalty]
    	for (BulletInfo bullet : nearbyBullets){
    		if (willCollideWithMe(loc, bullet)){
    			ans -= bullet.getDamage() * 1000f;
    		}
    	}
    	
    	//Be as close as possible to the gardener
    	if (nearestGardener != null){
    		ans += distanceScaling(loc.distanceTo(nearestGardener.getLocation()));
    	}
    	
    	//Be as far as possible from the soldiers
    	for (RobotInfo robot : nearbyCombatRobots){
    		ans -= distanceScaling(loc.distanceTo(robot.getLocation())) / OFFENSE_FACTOR;
    	}
    	
    	return ans;
    }
    
  //Archon score for micro.
    static float getArchonScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
    	float ans = 0;
    	//Avoid lumberjacks
    	for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
    		if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE_ARCHON){
    			//System.out.println(lumberjack.getLocation().distanceTo(loc));
    			ans -= RobotType.LUMBERJACK.attackPower * distanceScaling(lumberjack.getLocation().distanceTo(loc)) * 1000f;
    		}
    	}
    	
    	//Avoid bullets [Heavy penalty]
    	for (BulletInfo bullet : nearbyBullets){
    		if (willCollideWithMe(loc, bullet)){
    			ans -= bullet.getDamage() * 1000f;
    		}
    	}
    	
    	//Be as far as possible from the soldiers
    	for (RobotInfo robot : nearbyCombatRobots){
    		ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
    	}
    	
    	return ans;
    }
    
    static boolean willCollideWithMe(MapLocation newLoc, BulletInfo bullet) {
        MapLocation myLocation = newLoc;

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
    
    static boolean willCollideWithRobot(Direction bulletDir, MapLocation bulletLoc, MapLocation robotLoc, float robotBodyRadius) {

        // Get relevant bullet information
        Direction propagationDirection = bulletDir;
        MapLocation bulletLocation = bulletLoc;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(robotLoc);
        float distToRobot = bulletLocation.distanceTo(robotLoc);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= robotBodyRadius);
    }
    
    //Move randomly with num_attempts attempts
    static void randomMove(int num_attempts){
    	while (num_attempts > 0){
    		Direction dir = randomDirection();
    		if (rc.canMove(dir)){
    			try {
					rc.move(dir);
					return;
				} catch (GameActionException e) {
					e.printStackTrace();
				}
    		}
    		num_attempts --;
    	}
    }
    
    static GamePhase getGamePhase(){
    	int roundNum = rc.getRoundNum();
    	if (roundNum >= rc.getRoundLimit() - FINAL_PUSH_TURNS){
    		return GamePhase.FINAL_PUSH;
    	}
    	else if (roundNum >= END_GAME){
    		return GamePhase.END_GAME;
    	}
    	else if (roundNum >= MID_GAME){
    		return GamePhase.MID_GAME;
    	}
    	return GamePhase.EARLY_GAME;
    }
    
    //Returns whether team can hire gardener
    static boolean isGardenerHireable(){
    	/*20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
    	 * 24 => number of soldiers, 25 => number of tanks*/
    	if (rc.getTeamBullets() <= MIN_GARDENER_BULLETS){
    		return false;
    	}
    	/*if (getGamePhase() == GamePhase.EARLY_GAME && hasRecentScoutAttack()){
    		return false;
    	}*/
    	int num_gardeners = 10;
    	int num_gardeners_settled = 2; //Default to false if unable to read
    	try{
    		num_gardeners = rc.readBroadcast(20);
    		num_gardeners_settled = rc.readBroadcast(21);
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    	System.out.println("Unsettled Ratio: " + (float)num_gardeners_settled / num_gardeners);
    	if ((float)num_gardeners_settled / num_gardeners <= MAX_TOLERABLE_UNSETTLED_RATIO && num_gardeners >= MAX_UNGOVERNED_GARDENERS){
    		return false;
    	}
    	return true;
    }
    
    static void spawnedNewUnit(RobotType type){
    	/*20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
    	 * 24 => number of soldiers, 25 => number of tanks*/
    	int relChannel = 0;
    	switch (type){
		case GARDENER:
				relChannel = 20;
			break;
		case LUMBERJACK:
				relChannel = 22;
			break;
		case SCOUT:
				relChannel = 23;
			break;
		case SOLDIER:
				relChannel = 24;
			break;
		case TANK:
				relChannel = 25;
			break;
    		
    	}
    	try{
    		int num_unit = rc.readBroadcast(relChannel);
    		rc.broadcast(relChannel, num_unit + 1);
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }
    
    static void closeToDeath(RobotType type){
    	/*20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
    	 * 24 => number of soldiers, 25 => number of tanks*/
    	int relChannel = 0;
    	switch (type){
		case GARDENER:
				relChannel = 20;
			break;
		case LUMBERJACK:
				relChannel = 22;
			break;
		case SCOUT:
				relChannel = 23;
			break;
		case SOLDIER:
				relChannel = 24;
			break;
		case TANK:
				relChannel = 25;
			break;
		case ARCHON:
			return;
    		
    	}
    	try{
    		int num_unit = rc.readBroadcast(relChannel);
    		rc.broadcast(relChannel, num_unit - 1);
    		if (type.compareTo(RobotType.GARDENER) == 0 && settledDown){
    			num_unit = rc.readBroadcast(21);
    			rc.broadcast(21, num_unit - 1);
    		}
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }
    
    static void gardenerHasSettled(){
    	try{
    		int num_unit = rc.readBroadcast(21);
    		rc.broadcast(21, num_unit + 1);
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }
    
    //Get the next unit to be spawned. Returns ARCHON if unable to spawn anything
    static RobotType getNextUnitType(){
    	/*20 => number of gardeners, 21 => number of gardeners settled, 22 => number of lumberjacks, 23 => number of scouts, 
    	 * 24 => number of soldiers, 25 => number of tanks*/
    	
    	
    	TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, Team.NEUTRAL);
    	boolean hasNearbyTree = (nearbyTrees.length != 0);
    	
    	try{
    		int num_gardeners = rc.readBroadcast(20);
    		int num_gardeners_settled = rc.readBroadcast(21);
    		int num_lumberjacks = rc.readBroadcast(22);
    		int num_scouts = rc.readBroadcast(23);
    		int num_soldiers = rc.readBroadcast(24);
    		//don't build excessive numbers
    		if (num_soldiers > 50){
    			return RobotType.ARCHON;
    		}
	    	if (rc.getTeamBullets() < 100f){
	    		return RobotType.ARCHON;
	    	}
	    	else if (getGamePhase() == GamePhase.EARLY_GAME){ //Focus on scout and lumberjack. Spawn soldiers in later early game
	    		//Build lumberjacks if high ratio of not settled gardeners
	    		if (hasRecentScoutAttack() && num_soldiers <= 1){
	    			return RobotType.SOLDIER;
	    			//return RobotType.LUMBERJACK;
	    		}
	    		if (((float)num_gardeners_settled / num_gardeners <= MAX_TOLERABLE_UNSETTLED_RATIO || hasNearbyTree) && (num_lumberjacks * 2 <= num_soldiers)){
	        		return RobotType.LUMBERJACK;
	        	}
	    		else{
	    			/*float soldierSpawnChance = (float)(EARLY_GAME - rc.getRoundNum() ) / (EARLY_GAME - 100);
	    			if (Math.random() <= soldierSpawnChance){
	    				return RobotType.SOLDIER;
	    			}
	    			else{
	    				return RobotType.SCOUT;
	    			}*/
	    			return RobotType.SOLDIER;
	    		}
	    		
	    	}
	    	else if (getGamePhase() == GamePhase.MID_GAME){
	    		if (rc.getTeamBullets() <= ECON_ADJUSTMENT){
	    			return RobotType.ARCHON;
	    		}
	    		return RobotType.SOLDIER;
	    		/*if (hasRecentScoutAttack() && num_soldiers <= 1){
	    			return RobotType.SOLDIER;
	    		}
	    		if ((float)num_gardeners_settled / num_gardeners <= MAX_TOLERABLE_UNSETTLED_RATIO || hasNearbyTree){
	        		return RobotType.LUMBERJACK;
	        	}
	    		else{
	    			
	    			return RobotType.SOLDIER;
	    		}*/
	    	}
	    	else if (getGamePhase() == GamePhase.END_GAME || getGamePhase() == GamePhase.FINAL_PUSH){
	    		return RobotType.SOLDIER;
	    	}
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    	return RobotType.ARCHON;
    }
    
    static void reportScoutAttack(){ //Only run on gardeners
    	//30 => last scout attack turn
    	RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    	for (RobotInfo enemy : nearbyEnemies){
    		if (enemy.getType() == RobotType.SCOUT){
    			//Raise the alarm
    			try{
    				rc.broadcast(30, rc.getRoundNum());
    				rc.broadcast(31, mapLocationToInt(rc.getLocation()));
    			}
    			catch (GameActionException e){
    				e.printStackTrace();
    			}
    		}
    	}
    }
    
    static boolean hasRecentScoutAttack(){
    	try{
    		int last_attack = rc.readBroadcast(30);
    		if (last_attack >= rc.getRoundNum() - SCOUT_ATTACK_FORGIVENESS){
    			return true;
    		}
    	}
    	catch (GameActionException e){
    		e.printStackTrace();;
    	}
    	return false;
    }
    
    
    //If you ever need to fire somewhere on the map
    static void safeAttack(MapLocation attackLoc){
    	MapLocation myLoc = rc.getLocation();
    	//Avoid friendly fire
    	RobotInfo [] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
    	//Should I use -1?
    	
    	boolean hasFired = false;
    	
    	//Conduct attack
    	//Avoid friendly fire
    	if (rc.canFirePentadShot()){
    		if (attackLoc != null){
    			boolean safeToShoot = true;
    			for (int i = 0; i < 5; i++){
    				Direction shotDir = null;
    				if (i <= 1){
    					shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (2 - i));
    				}
    				else if (i == 2){
    					shotDir = myLoc.directionTo(attackLoc);
    				}
    				else{
    					shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (i - 2));
    				}
	    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
	    			
	    			for (RobotInfo ally : nearbyAllies){
	    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
	    					safeToShoot = false;
	    					break;
	    				}
	    			}
	    			if (!safeToShoot){
	    				break;
	    			}
    			}
    			if (safeToShoot){
	    			try {
						rc.firePentadShot(myLoc.directionTo(attackLoc));
						hasFired = true;
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
    	if (!hasFired && rc.canFireTriadShot()){
    		if (attackLoc != null){
    			boolean safeToShoot = true;
    			for (int i = 0; i < 3; i++){
    				Direction shotDir = null;
    				if (i == 0){
    					shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
    				}
    				else if (i == 1){
    					shotDir = myLoc.directionTo(attackLoc);
    				}
    				else{
    					shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
    				}
	    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
	    			
	    			for (RobotInfo ally : nearbyAllies){
	    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
	    					safeToShoot = false;
	    					break;
	    				}
	    			}
	    			if (!safeToShoot){
	    				break;
	    			}
    			}
    			if (safeToShoot){
	    			try {
						rc.fireTriadShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
    	if (!hasFired && rc.canFireSingleShot()){
    		if (attackLoc != null){
    			boolean safeToShoot = true;
				Direction shotDir = myLoc.directionTo(attackLoc);
    			MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
    			
    			for (RobotInfo ally : nearbyAllies){
    				if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
    					safeToShoot = false;
    					break;
    				}
    			}
    			if (safeToShoot){
	    			try {
						rc.fireSingleShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
    			}
    		}
    	}
    }
    
    
}
