package kashyyyk;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public strictfp class RobotPlayer {
    static RobotController rc;

	//Precomp Degrees
	static float DEG_01 = (float) Math.PI/1800;
	static float DEG_05 = (float) Math.PI/360;
	static float DEG_1 = (float) Math.PI/180;
	static float DEG_5 = (float) Math.PI/36;
	static float DEG_15 = (float) Math.PI/12;
	static float DEG_30 = (float) Math.PI/6;
	static float DEG_45 = (float) Math.PI/4;
	static float DEG_90 = (float) Math.PI/2;
	static float DEG_180 = (float) Math.PI;
	static float DEG_360 = (float) Math.PI*2;

	//Radius allowances
	static float RADIUS_1 = 1.01f;
    
    //Constant variables
    static final float epsilon = 1e-08f;
    static final int VISITED_ARR_SIZE = 10;         //Number of previous locations recorded
    static final int MOVEMENT_GRANULARITY = 8;      //Number of possible move directions
    static final float STUCK_THRESHOLD = 0.2f;      //Threshold to call if unit is stuck
    static final int SOLDIER_DELAY = 100;           //Number of turns to spawn a new soldier
    static final int GARDENER_DELAY = 50;           //Number of turns to spawn a new gardener
    static final float SOLDIER_SHOOT_RANGE = 64f;   //This is a squared distance
    static final float TARGET_RELEASE_DIST = 9f;    //This is a square distance. Assume you have reached your destination if your target falls within this distance
    static final int MAX_STOCKPILE_BULLET = 350;    //Maximum bullets in stockpile before donation occurs
	static final int DONATION_AMT = 20;             //Amount donated each time
    static final int SOLDIER_ALARM_RELEVANCE = 5;   //Relevance of a soldier alarm
    static final int EARLY_GAME = 100;              //How early is counted as 'early game'
    static final int FARMER_MIN_SLOTS = 4;          //Minimum number of trees that can be built before starting farm [1-6]
    static final int SEARCH_GRANULARITY = 12;       //Number of search directions

	//Flocking behavior
	static float SPHERE_REPULSION = 3f;
	static float SPHERE_ATTRACTION = 6f;
	static float SPHERE_ALIGNMENT = 12f;

	//Surroundings sensing
	static TreeInfo[] SURROUNDING_TREES_OWN;
	static TreeInfo[] SURROUNDING_TREES_ENEMY;
	static TreeInfo[] SURROUNDING_TREES_NEUTRAL;
	static RobotInfo[] SURROUNDING_ROBOTS_OWN;
	static RobotInfo[] SURROUNDING_ROBOTS_ENEMY;
	static MapLocation ENEMY_ARCHON_LOCATION;
	static MapLocation MY_LOCATION;
    
    //Robot specific variables
    static boolean isSet;                           //Whether important local variables are set
    static int timeLived;                           //Time since spawn
    static int patience;                            //For movement
    static ArrayList<MapLocation> visitedArr;       //For movement
    static MapLocation target;                      //This is the position the robot will move to
    static boolean inCombat;                        //Is this robot in combat?
	static int ROBOT_SPAWN_ROUND = 0;

	//Archon specific variables
	static int gardenerTimer;
	static float SPAWN_FARMER_DENSITY_THRESHOLD = 1.2f;
    
    //Gardener specific variables
    static boolean spawnedLumberjack;
    static boolean settledDown;
    static int resetTimer;
    static int soldierTimer;
    static int lumberjackTimer;
    static Direction moveDir;
	static Direction SPAWN_DIR = null;              //Location to start spawning soldiers/troops
	static int FARMER_TIMER_THRESHOLD = 0;
	static int GARDENER_TANK_THRESHOLD = 400;
	static float FARMER_CLEARANCE_RADIUS = 3f;
    static int FARMER_MAX_TREES = 3;                //Max number of trees to plant within first few turns?
    static ArrayList<Direction> current_available_slots = new ArrayList<>();
    
    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable;                 //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise

	//Broadcast channel statics
	static final int BORDER_LEFT = 0;
	static final int BORDER_TOP = 1;
	static final int BORDER_RIGHT = 2;
	static final int BORDER_BOTTOM = 3;
	static final int SCOUTED_ENEMY_TURN = 10;
	static final int SCOUTED_ENEMY_POSITION = 11;
	static final int SPAWN_TREES = 410;
	static final int SPAWN_SCOUT = 411;
	static final int SPAWN_SOLDIERS = 412;
	static final int SPAWN_TANKS = 413;
	static final int SPAWN_LUMBERJACKS = 414;

	static final int EARLY_SCOUT = 421;

	static final int EARLY_SOLDIER = 431;
	static final int SCOUT_RUSH_DETECTED = 432;
	static final int SCOUT_RUSHED = 433;

	static final int SPAWN_FARMER = 440;
	static final int SPAWN_FARMER_THRESHOLD = 441;

    /*
     * Broadcast Channels:
     * 0 => left border, 1 => top border, 2 => right border, 3 => bottom border
     * 10 => turn enemy scouted, 11 => enemy scouted position [Only updated when in combat]
     *
     * Shared Array SOP
	 * [100-109] Team Orders
	 * [200-209] Team Order Data
	 * [0-300] Team Robot Status
	 *
	 * Squad Setup
	 * [10-99] Team Members
	 * [110-199] Member Data
	 * [210-299] Member Keep Alive Signal
	 *
	 * Ops Orders
	 * [400-410] Reserved
	 * [410] Spawn Trees
	 * [411] Spawn Scout
	 * [412] Spawn Soldiers
	 * [413] Spawn Tanks
	 * [414] Spawn Lumberjacks
	 * [420] Offensive Posture
	 * [421] Early game scout rush		[0] - Deactivated	[1] - Activated (Allows gardener to build scouts early game)
	 * [430] Defensive Posture
	 * [431] Early Soldier Spawn		[0] - Deactivated	[1] - Activated (Allows gardener to build soldiers early game)
	 * [432] Enemy scout rush detected!
	 * [440] Farming mode
	 * [441] Farmer Spawn Threshold
	 *
	 * Archon Location
	 * [501-502] Archon location
	 * [500] Archon ID
	 * [511-512] Archon location
	 * [510] Archon ID
	 * [521-522] Archon location
	 * [520] Archon ID
	 * [531-532] Archon location
	 * [530] Archon ID
	 * [541-542] Archon location
	 * [540] Archon ID
	 *
	 * Farmers
	 * [551-552] Farmer location
	 * [550] Farmer ID
	 * [553] Farmer Keep Alive
	 * ... etc
	 *
	 * Gardeners Counter
	 * [700-799] Keep Alive heartbeat
	 *
	 * Troops Counter
	 * [800-899] Keep Alive heartbeat
	 *
	 * Reserved Channels for future use
	 * [900-999]
     *
     */

    /*
     * General TODO:
     * 1. Work on micro for soldiers => Reduce friendly fire
     * 2. Soldiers should micro away from lumberjacks
     * 3. Find a way to determine map density then choose the appropriate bot
     */

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        //Global Initialization
        RobotPlayer.rc = rc;
		ROBOT_SPAWN_ROUND = rc.getRoundNum();
		ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];

		//Robot Specific Initializations
		try{
			switch (rc.getType()) {
				case ARCHON:
					rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
					break;
				case GARDENER:
					SPHERE_REPULSION = 4.04f;
					SPHERE_ATTRACTION = 6.7f;
					break;
				case SOLDIER:
					break;
				case LUMBERJACK:
					break;
				case SCOUT:
					break;
				case TANK:
					break;
			}
		}
		catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
			e.printStackTrace();
		}

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        while (true){
        	try{
		        //Always be on the lookout for neutral trees - might come in handy for pathing later on too...
				SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
				SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
				SURROUNDING_TREES_OWN = rc.senseNearbyTrees(3f, rc.getTeam());
				SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        		SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                MY_LOCATION = rc.getLocation();

                if (rc.getRoundNum() < EARLY_GAME) {
                    for (RobotInfo r : SURROUNDING_ROBOTS_ENEMY) {
                        if (r.type == RobotType.SCOUT){
                            System.out.println("I have detected a scout rushing us!!");
                            rc.broadcast(SCOUT_RUSH_DETECTED, 1);
                        }
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
		            	runSoldier();
		            	break;
		            case TANK:
						runTank();
		            	break;
		        }

		        //Shake trees in vicinity
				if (SURROUNDING_TREES_NEUTRAL.length > 0){
        			if (rc.canShake(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.shake(SURROUNDING_TREES_NEUTRAL[0].ID);
				}

		        //Donate
		        donate();

		        //Give up the turn
		        Clock.yield();
        	}
        	catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
        		e.printStackTrace();
        	}
        }
	}

    static void runArchon(){
    	boolean can_spawn = false;
    	Random rand = new Random(rc.getID());

    	if (!isSet){
    		gardenerTimer = 0;
    		isSet = true;
    	}

		//Check whether current position can spawn units, else move
		try {
			for (int i = 0; i < SEARCH_GRANULARITY; i++) {
				Direction cDir = new Direction(i * (DEG_360/SEARCH_GRANULARITY));
				if (!rc.isCircleOccupied(MY_LOCATION.add(cDir, 2.01f), 1f)) {
					can_spawn = true;
				}
			}
			if (!can_spawn) {
				if (!rc.hasMoved()) {
					//Move to least dense area

					//Get most spacious direction
					float density = 999999f;
					float offset = rand.nextInt(360)*DEG_1;
					Direction least_dense_direction = null;
					for(int i=0;i<SEARCH_GRANULARITY;i++) {
						Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
						float eval = getDensity(MY_LOCATION.add(cDir,1.5f),5f);
						if(eval<density){
							density = eval;
							least_dense_direction = cDir;
						}
					}

					if(least_dense_direction != null) {
						//target = myLocation.add(least_dense_direction, 1f);
						//moveToTarget(myLocation);
						if (rc.canMove(least_dense_direction)) rc.move(least_dense_direction);
					}
				}
			}
		} catch (Exception e){
    		e.printStackTrace();
		}

    	//Spawn Code
    	if (rc.getRoundNum() >= gardenerTimer && getDensity(MY_LOCATION,4.05f, rc.getTeam())<SPAWN_FARMER_DENSITY_THRESHOLD){

    	    try {
    			//Attempt to build gardener
				Direction[] build_locations = new Direction[SEARCH_GRANULARITY];
				int conut = 0;
				if (rc.isBuildReady()) {
					//Search for location to build gardener
					float offset = rand.nextInt(360)*DEG_1;
					for(int i=0;i<SEARCH_GRANULARITY;i++) {
						Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
						if (rc.canHireGardener(cDir)) {
							build_locations[conut] = cDir;
							conut++;
						}
					}
				}

				//Get most spacious direction
				float density = 999999f;
				Direction spawn_direction = null;
				for(int i=0;i<conut;i++){
					float eval = getDensity(MY_LOCATION.add(build_locations[i],1.5f),5f);
                    /*System.out.println("Density Evaluation: ");
                    System.out.println(eval);
                    System.out.println(build_locations[i]);*/
					if(eval<density){
						density = eval;
						spawn_direction = build_locations[i];
					}
				}
				if(spawn_direction != null) {
					System.out.println("Hired Gardener in direction:");
					System.out.println(spawn_direction.radians);
					System.out.println(density);
					rc.hireGardener(spawn_direction);
					gardenerTimer = rc.getRoundNum() + GARDENER_DELAY;
				}

    			if (rc.getRoundNum() <= 5){
	    			//Update known map bounds
	    			rc.broadcast(0, 0);
	    			rc.broadcast(1,  0);
	    			rc.broadcast(2, 600);
	    			rc.broadcast(3, 600);
    			}
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }

	static void runGardener(){
		Random rand = new Random(rc.getID());
		boolean emergency = false;

		//Whether robot variables are set
		if (!isSet){
			//System.out.println("Hi I'm a gardener!");
			timeLived = 0;
			visitedArr = new ArrayList<MapLocation>();
			isSet = true;
			patience = 0;
			spawnedLumberjack = false;
			resetTimer = 10000; //Set an arbitrarily large value
			targetInfo = null;
			soldierTimer = 200 + rc.getRoundNum();//When to spawn the next soldier
			MapLocation dest = new MapLocation((MY_LOCATION.x + ENEMY_ARCHON_LOCATION.x) / 2, (MY_LOCATION.y  + ENEMY_ARCHON_LOCATION.y) / 2);
			//Add a random angle of +-30 degrees
			Direction dir = new Direction(MY_LOCATION, dest);
			Direction finalDir = addRandomAngle(dir, 45); //Not sure if this fans out correctly => Need to test
			target = MY_LOCATION.add(finalDir, MY_LOCATION.distanceTo(dest));
			settledDown = false; //Will be set to true if a suitable space can be found
			moveDir = randomDirection();
		}
		else{
			timeLived += 1;
		}

        try {
            if (settledDown) current_available_slots = getBuildSlots(MY_LOCATION, 1);
            else current_available_slots = getBuildSlots(MY_LOCATION, FARMER_MIN_SLOTS);
            if (current_available_slots.size() > 0) {
                //Get most spacious direction
                float density = 999999f;
                int spawn_direction = -1;
                System.out.println("Total build slots: " + Integer.toString(current_available_slots.size()));
                for (int i = 0; i < current_available_slots.size(); i++) {
                    System.out.println("Available build slot at: " + Float.toString(current_available_slots.get(i).radians));
                    float eval = getDensity(MY_LOCATION.add(current_available_slots.get(i), 1.5f), 5f);
                    if (eval < density) {
                        density = eval;
                        spawn_direction = i;
                    }
                }
                if (spawn_direction != -1) {
                    SPAWN_DIR = current_available_slots.get(spawn_direction);
                    current_available_slots.remove(spawn_direction);
                }
            }
        } catch (Exception e) {
            System.out.println("Gardener fails to get surrounding build slots");
            e.printStackTrace();
        }

		//Check for early game starts
        try {
            if (rc.getRoundNum() < EARLY_GAME) {
                if (SPAWN_DIR != null) {
                    int scout_rush = rc.readBroadcast(SCOUT_RUSH_DETECTED);
                    int early_scout_build = rc.readBroadcast(EARLY_SCOUT);
                    int early_soldier_build = rc.readBroadcast(EARLY_SOLDIER);
                    if (scout_rush == 1 && rc.readBroadcast(SCOUT_RUSHED) == 0) emergency = true;
                    if (rc.isBuildReady()) {
                        if (early_soldier_build == 1 && scout_rush == 1) {
                            if (rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIR)) {
                                rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
                                rc.broadcast(SCOUT_RUSHED, 1);
                            }
                        }
                        if (early_scout_build == 1) {
                            if (rc.canBuildRobot(RobotType.SCOUT, SPAWN_DIR)) rc.buildRobot(RobotType.SCOUT, SPAWN_DIR);
                        }
                    }
                }
            } else if (rc.readBroadcast(SCOUT_RUSH_DETECTED) == 1 && rc.readBroadcast(SCOUT_RUSHED) == 0) {
                //Scout rush still not responded to!
                if (rc.isBuildReady() && SPAWN_DIR != null) {
                    rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
                    rc.broadcast(SCOUT_RUSHED, 1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (!settledDown){
			//Try to spawn a lumberjack
			//Check if there are trees around
			if (SPAWN_DIR != null) {
				if (current_available_slots.size() > 0 && SURROUNDING_ROBOTS_ENEMY.length > 0) {
					spawnUnit(RobotType.SOLDIER, SPAWN_DIR);
				} else {
					spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
				}
			}

            //Look for a space with a circle of radius 3
            gardenerMove(20);
			if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS)) settledDown = true;

			/*try {
                if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, FARMER_CLEARANCE_RADIUS)){
                    settledDown = true;
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            }*/

            //Bubble Squeeze method...
			/*if (current_available_slots.size()>0){
				//See if can move further away from archon
				//Read Archon positions
				RobotInfo[] friendlies = null;
				RobotInfo curArchon = null;
				try{
					for(RobotInfo f:SURROUNDING_ROBOTS_OWN){
						if(f.type == RobotType.ARCHON) curArchon = f;
					}
				} catch (Exception e){
					e.printStackTrace();
				}
				Direction toArc;

				if (curArchon != null) {
					toArc = MY_LOCATION.directionTo(curArchon.location);
					if (rc.getRoundNum() - ROBOT_SPAWN_ROUND > FARMER_TIMER_THRESHOLD) {
						settledDown = true;
					}
					else {
						target = MY_LOCATION.add(toArc.opposite(), 1f);
						moveToTarget(MY_LOCATION);
					}
					//target = myLoc.add(toArc.opposite(), 1f);
					//moveToTarget(myLoc);
				}
				else settledDown = true;
			}
			else {
				//Move around to find another spot for farming
				try {
					TreeInfo[] surrTrees = rc.senseNearbyTrees(-1, null);
					RobotInfo[] surrRobots = rc.senseNearbyRobots(-1, null);
					if (surrTrees.length == 0 && surrRobots.length == 0){
						current_available_slots = getBuildSlots(MY_LOCATION, 1);
					}
					else {
						MapLocation[] mapLoc = new MapLocation[surrTrees.length + surrRobots.length];
						float[] radii = new float[surrTrees.length + surrRobots.length];
						for (int i = 0; i < surrTrees.length; i++) {
							mapLoc[i] = surrTrees[i].getLocation();
							radii[i] = surrTrees[i].getRadius();
						}
						for (int i = 0; i < surrRobots.length; i++) {
							mapLoc[i + surrTrees.length] = surrRobots[i].getLocation();
							radii[i + surrTrees.length] = surrRobots[i].getRadius();
						}
						Direction[] bubble_vector = bubbleSqueeze(mapLoc, radii, MY_LOCATION, FARMER_CLEARANCE_RADIUS);
						if (rc.canMove(bubble_vector[0], Math.min(1f, bubble_vector[1].radians))) {
							rc.move(bubble_vector[0], Math.min(1f, bubble_vector[1].radians));
						} else {
							Direction randDir = randomDirection();
							if (rc.canMove(randDir)) rc.move(randDir);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}*/
		}
		else{
			//System.out.println("Settled Down");

			//Try to spawn a lumberjack when there are trees around >.<
			if (SPAWN_DIR != null) {
			    try {
                    if (rc.getRoundNum() < EARLY_GAME) {
                        if (SURROUNDING_TREES_NEUTRAL.length > 0 && !spawnedLumberjack) {
                            spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                            spawnedLumberjack = true;
                        }
                    } else {
                        if (SURROUNDING_TREES_NEUTRAL.length > 0) {
                            spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                        }
                    }
                } catch (Exception e){
			        e.printStackTrace();
                }
			}

			//Plant trees
            if (rc.getRoundNum() < EARLY_GAME) {
			    int cur_trees = SURROUNDING_TREES_OWN.length;
                if (!emergency && cur_trees < FARMER_MAX_TREES) {
                    try {
                        for (Direction dir : current_available_slots) {
                            if (rc.canPlantTree(dir)) rc.plantTree(dir);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            else {
                try {
                    for (Direction dir : current_available_slots) {
                        if (rc.canPlantTree(dir)) rc.plantTree(dir);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

			//Water
			TreeInfo lowestHealthTree = null;
			float lowestHealth = 1000000f;
			for (TreeInfo tree : SURROUNDING_TREES_OWN){
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

			//Spawn a soldier/tank if timer is up
			/*if (rc.getRoundNum() >= soldierTimer){
				if (rand.nextInt(1000) < GARDENER_TANK_THRESHOLD){
					System.out.println("Attempting to build a tank!");
					if (rc.canBuildRobot(RobotType.TANK, SPAWN_DIR)){
						try {
							rc.buildRobot(RobotType.TANK, SPAWN_DIR);
						} catch (Exception e){
							e.printStackTrace();
						}
					}
					else if (spawnUnit(RobotType.SOLDIER, SPAWN_DIR)){
						soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
					}
				}
				else if (spawnUnit(RobotType.SOLDIER, SPAWN_DIR)){
					soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
				}
			}*/

            if (rc.getRoundNum() >= soldierTimer){
                try {
                    if (spawnUnit(RobotType.SOLDIER, SPAWN_DIR)) {
                        soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

		}

    }
	
	static void runLumberjack(){
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
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Go into combat
			inCombat = true;
		}
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(MY_LOCATION);
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
				TreeInfo nearestTree = null;
				if (SURROUNDING_TREES_NEUTRAL.length>0) nearestTree = SURROUNDING_TREES_NEUTRAL[0];
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
				if (MY_LOCATION.isWithinDistance(target, 1)){
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
					moveToTarget(MY_LOCATION);
				}
			}
		}
	}

    static void runSoldier() {
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			
			isSet = true;
		}
		//Look for nearby enemies
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Go into combat
			inCombat = true;
			target = null;
			//Call all noncombat soldiers to enemy position
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
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
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(MY_LOCATION);
			//Attack nearest enemy if possible
			//No micro here => Just shooting bullets at the nearest enemy
			if (MY_LOCATION.isWithinDistance(target, SOLDIER_SHOOT_RANGE)){
				try {
					if (rc.canFirePentadShot()){
						//todo Check for friendly fire
						/*RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
						boolean friendlyFire = false;
						for(RobotInfo f:friendly){
							if(intersectLineCircle(myLoc,f.location,myLoc.directionTo(f.location))){
								friendlyFire = true;
							}
						}
						if(!friendlyFire) rc.firePentadShot(myLoc.directionTo(target));*/
						rc.firePentadShot(MY_LOCATION.directionTo(target));
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
				moveToTarget(MY_LOCATION);
				if (target != null){
					if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
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

	static void runTank() {
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;

			isSet = true;
		}
		//Look for nearby enemies
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Go into combat
			inCombat = true;
			target = null;
			//Call all noncombat soldiers to enemy position
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
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
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(MY_LOCATION);
			//Attack nearest enemy if possible
			//No micro here => Just shooting bullets at the nearest enemy
			if (MY_LOCATION.isWithinDistance(target, SOLDIER_SHOOT_RANGE)){
				try {
					if (rc.canFirePentadShot()){
						//todo Check for friendly fire
						/*RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
						boolean friendlyFire = false;
						for(RobotInfo f:friendly){
							if(intersectLineCircle(myLoc,f.location,myLoc.directionTo(f.location))){
								friendlyFire = true;
							}
						}
						if(!friendlyFire) rc.firePentadShot(myLoc.directionTo(target));*/
						rc.firePentadShot(MY_LOCATION.directionTo(target));
					}
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}

		}
		else{
			//Look for trees to crush! hehehe :D
			TreeInfo closest_tree = null;
			if (SURROUNDING_TREES_ENEMY.length > 0) closest_tree = SURROUNDING_TREES_ENEMY[0];
			else if (SURROUNDING_TREES_NEUTRAL.length > 0) closest_tree = SURROUNDING_TREES_NEUTRAL[0];

			if (closest_tree != null){
				target = closest_tree.location;
			}

			if (target == null){
				target = newScoutingLocation(20);
			}
			else{
				/*try {
					rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				moveToTarget(MY_LOCATION);
				if (target != null){
					if (MY_LOCATION.distanceSquaredTo(target) <= TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
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

    //Checks whether a float is an integer value
    static boolean isIntegral(float f){
    	return (Math.abs(Math.floor(f) - f) <= epsilon);
    }
    
    static Direction randomDirection(){
    	//System.out.println(new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f));
    	return new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f);
    }

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
			if (actualdist / expecteddist < STUCK_THRESHOLD && rc.getType() != RobotType.SOLDIER && rc.getType() != RobotType.TANK && rc.getType() != RobotType.SCOUT){ //Help I'm stuck [Gardener no longer moves]
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
	    		if (rc.getType() == RobotType.SOLDIER || rc.getType() == RobotType.TANK){
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
				//rc.donate((float)(Math.floor((rc.getTeamBullets() - MAX_STOCKPILE_BULLET) / 10.0) * 10.0));
				rc.donate(DONATION_AMT);
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }

	//Line intersection with circles
	static boolean intersectLineCircle(MapLocation origin, MapLocation cir, Direction path){
		Direction origin_to_cir = origin.directionTo(cir);
		float acute_angle = absRad(path.radians-origin_to_cir.radians);
		float normal_to_cir = absRad(origin_to_cir.radians+DEG_90);
		if(acute_angle<Math.max(absRad(normal_to_cir+DEG_180),normal_to_cir) && acute_angle>Math.min(absRad(normal_to_cir+DEG_180),normal_to_cir)){
			return false;
		}
		else{
			if(acute_angle>DEG_90) acute_angle = DEG_360-acute_angle;
		}
		return origin.distanceTo(cir)*Math.sin(acute_angle)<RADIUS_1;
	}

	//ABS func radians
	static float absRad(float r){
		if(r>DEG_360) return r-DEG_360;
		else if(r<0) return r+DEG_360;
		else return r;
	}

	//Check if have enough space to plant some trees :)
	static ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
		ArrayList<Direction> available_slots = new ArrayList<Direction>();

		Direction[] hexPattern = new Direction[6];
		for (int i = 0; i < 6; i++) {
			hexPattern[i] = new Direction(0 + i * (DEG_45 + DEG_15));
		}

		int offset = 0;
		int avail_count = 0;
		boolean found_space = false;

		//Scan in a hexagonal pattern first
		for(int i=0;i<20;i++){
			if(found_space) continue;
			for(int j=0;j<6;j++){
				if(rc.canPlantTree(new Direction(absRad(hexPattern[j].radians+i*DEG_1*3)))){
					avail_count++;
				}
			}
			if(avail_count < MIN_SLOTS){
				//todo Check for irregular pattern of build slots
                /*int alternate_slots = 0;
                if(alternate_slots > FARMER_MIN_BUILD_SLOTS-avail_count){

                }
                else{
                    avail_count = 0;
                }*/
				avail_count = 0;
			}
			else{
                offset = i;
			    found_space = true;
            }
		}

		if(found_space){
			for(int j=0;j<6;j++){
				if(rc.canPlantTree(new Direction(absRad(hexPattern[j].radians+offset*DEG_1*3)))){
					available_slots.add(new Direction(absRad(hexPattern[j].radians+offset*DEG_1*3)));
				}
			}
		}

		//Irregular hexagonal pattern (less than 6 slots, wiggle around for build space)

		return available_slots;
	}

	//Get Density of objects
	static float getDensity(MapLocation center,float radius){
		RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
		TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
		return (float)(robots.length+trees.length)/(radius);
	}

	//Get Density of robots from the team
	static float getDensity(MapLocation center,float radius,Team t){
		RobotInfo[] robots = rc.senseNearbyRobots(center, radius, t);
		return (float)(robots.length)/(radius);
	}

	//static Direction[] bubbleSqueeze(MapLocation[] mapLoc, float[] radii, int recurse, Direction prevVec, float prevDist) throws GameActionException {
	static Direction[] bubbleSqueeze(MapLocation[] mapLoc, float[] radii, MapLocation myLoc, float radius) throws GameActionException {
		Direction[] retDat = new Direction[2];
        /*if(recurse > MAX_STACK) {
            retDat[0] = prevVec;
            retDat[1] = new Direction(prevDist);
            return retDat;
        }*/
		float eject = 0f;
		MapLocation self = myLoc;
		float self_rad = radius;
		float tot_rad = 0f;
		float tot_dist = 0f;
		float[] intersectDir = new float[mapLoc.length];
		float[] intersectDist = new float[mapLoc.length];
		int conut = 0;

		for(int i=0;i<mapLoc.length;i++){
			float curDist = self.distanceTo(mapLoc[i]);
			float overlap = (self_rad+radii[i]-curDist);
			if(overlap>0){
				Direction curDir = self.directionTo(mapLoc[i]);
				intersectDir[conut] = curDir.radians;
				intersectDist[conut] = overlap;
				tot_rad += (float)Math.sqrt(overlap);
				conut++;
			}
		}

		for(int i=0;i<conut;i++){
			eject += intersectDir[i]*(Math.sqrt(intersectDist[i])/tot_rad);
			tot_dist += intersectDist[i]*(Math.sqrt(intersectDist[i])/tot_rad);
		}

		eject = absRad(eject/conut+DEG_180);

		retDat[0] = new Direction(eject);
		retDat[1] = new Direction(tot_dist);

		return retDat;
	}

	//Bouncing gardener movement algo
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

    //Metric to test if gardeners are spaced out enough :)
    static boolean isSpreadOut(MapLocation myLoc, float radius){
        RobotInfo[] farmers = rc.senseNearbyRobots(radius, rc.getTeam());
        //for (RobotInfo f:farmers) if(f.type == RobotType.GARDENER || f.type == RobotType.ARCHON) return false;
        if (farmers.length > 0) return false;
        return true;
    }

}
