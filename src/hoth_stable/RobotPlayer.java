package hoth_stable;

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
    static float DEG_60 = (float) Math.PI/3;
    static float DEG_90 = (float) Math.PI/2;
    static float DEG_180 = (float) Math.PI;
    static float DEG_360 = (float) Math.PI*2;

    //Radius allowances
    static float RADIUS_1 = 1.01f;

    //Constant variables
    static final float epsilon = 1e-08f;
    static final int VISITED_ARR_SIZE = 10;         			//Number of previous locations recorded

    //Early game estimation heuristics
    static final float MAP_SMALL = 40f;							//Max size of map considered 'small'
    static final float MAP_LARGE = 97f;							//Max size of map considered 'large'
    static final float INITIAL_BULLETS = 300f;					//Starting number of bullets

    //Movement finesse
    static final int MOVEMENT_GRANULARITY = 24;      			//Number of possible move directions
    static final int SEARCH_GRANULARITY = 12;       			//Number of search directions
    static final float TARGET_RELEASE_DIST = 9f;    			//This is a square distance. Assume you have reached your destination if your target falls within this distance
    static final float STUCK_THRESHOLD = 0.2f;      			//Threshold to call if unit is stuck
    static final int GARDENER_MOVE_GRANULARITY = 30;			//Number of times gardener will try random directions

    //Game-changing macro controls
    static final int MAX_STOCKPILE_BULLET = 350;    			//Maximum bullets in stockpile before donation occurs
    static final int DONATION_AMT = 20;             			//Amount donated each time
    static final int GAME_INITIALIZE_ROUND = 2;					//How many rounds before game is initialized
    static final int EARLY_GAME = 124;              			//How early is counted as 'early game'
    static final int MID_GAME = 500;							//When mid-game starts
    static final int LATE_GAME = 1800;							//Late game case
    static final int THE_FINAL_PROBLEM = 2700;					//Last ditch effort
    static final float FOREST_DENSITY_THRESHOLD = 0.20f;		//% of area covered by trees in sense range
    static final int FARMER_MIN_SLOTS = 4;          			//Minimum number of trees that can be built before starting farm [1-6]
    static final int GARDENER_STUCK_TIMER = 17;					//After n turn, just spawn a lumberjack already!
    static final float BULLET_SENSE_RANGE = 5f; 				//The range at which bullets are sensed
    static final float OFFENSE_FACTOR = 3f; 					//Factor to prioritise offense / defense for scout micro [Higher => more offensive]

    //Early game build order
    static final int MAX_BUILD_ORDER = 5;						//Reserve how many slots in build queue
    static final int BUILD_ORDER_QUEUE = 100;					//Start of channel for build order

    //Spawning constants (Macro control)
    static final int SOLDIER_DELAY = 40;          				//Number of turns to spawn a new soldier
    static final int GARDENER_DELAY = 50;           			//Number of turns to spawn a new gardener
    static final int SCOUT_DELAY = 50; 							//Number of turns to spawn a new scout
    static final int TANK_DELAY = 20; 							//Number of turns to spawn a new tank

    static final float SCOUT_SPAWN_BASE_CHANCE = 0.45f;			//Base scout spawn chance
    static float SCOUT_SPAWN_CHANCE = SCOUT_SPAWN_BASE_CHANCE; 	//Initial chance to spawn a scout (set as base chance)
    static final float SPAWN_GARDENER_CHANCE = 0.8f;			//Chance to spawn a gardener

    static final int SCOUT_OBSOLETE_LIMIT = 2500;				//By which round the scout becomes obsolete

    //Micro constants
    static final float SOLDIER_MICRO_DIST = 64.3f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float ARCHON_MICRO_DIST = 100.1f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float SCOUT_MICRO_DIST = 64.3f;				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_AVOIDANCE_RANGE = 5f; 		//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_ARCHON = 8f; 	//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_SCOUT = 4f;	//How far to avoid lumberjacks
    static final float PENTAD_EFFECTIVE_DISTANCE = 7.596f;		//(1/tan(7.5deg)) Such that at least 2 shots will hit a target with radius 1

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
    static float ARCHON_SIGHT_AREA = 100*(float)Math.PI;
    static int gardenerTimer;
    static float SPAWN_FARMER_DENSITY_THRESHOLD = 1.2f;
    static float ARCHON_JIGGLE_RADIUS = 3.2f;
    static boolean can_spawn_gardener;
    static Direction least_dense_direction;
    static Direction spawn_direction;
    static float est_size;

    //Gardener specific variables
    static float GARDENER_SIGHT_AREA = 49*(float)Math.PI;
    static boolean spawnedLumberjack;
    static boolean settledDown;
    static int resetTimer;
    static int soldierTimer;
    static int lumberjackTimer;
    static Direction moveDir = randomDirection();
    static Direction SPAWN_DIR = null;              //Location to start spawning soldiers/troops
    static int FARMER_TIMER_THRESHOLD = 0;
    static int GARDENER_TANK_THRESHOLD = 400;
    static float FARMER_CLEARANCE_RADIUS = 3f;
    static float FARMER_ARCHON_DISTANCE = 7f;
    static int FARMER_MAX_TREES = 3;                //Max number of trees to plant within first few turns?
    static ArrayList<Direction> current_available_slots = new ArrayList<>();

    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable;                 //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise

    //Soldier specific variables
    static final int SOLDIER_ALARM_RELEVANCE = 5;   			//Relevance of a soldier alarm
    static final float SOLDIER_SHOOT_RANGE = 64f;   			//This is a squared distance

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
    static final int DENSE_FOREST_DETECTED = 442;

    static final int RESERVE_BULLETS = 600;

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
	 * [442] Dense Forest detected
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
	 * ENEMY AT THE GATES
	 * [600-699]
	 * [600] Request for bullet reservation (archon pls dun spawn garderners thx)
	 * [601] Heartbeat for request
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
                    //Use archon positions to find rough size of map then set early-game strategy
                    MY_LOCATION = rc.getLocation();
                    est_size = ENEMY_ARCHON_LOCATION.distanceTo(MY_LOCATION);

                    //Estimate which corner archon is in


                    if (est_size < MAP_SMALL){
                        rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
                    }
                    else if (est_size < MAP_LARGE){
                        rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
                        rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
                    }
                    else {
                        rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
                        rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
                    }

                    //Setup build order
                    //Check forest density
                    float area_trees = 0f;
                    int[] build_list = null;
                    int[] sco_lum_sco = {1,2,1};
                    int[] sco_sco_lum = {1,1,2};
                    int[] sco_sco_sco = {1,1,1};
                    int[] sol_sco_sco = {3,1,1};
                    SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    for (TreeInfo t:SURROUNDING_TREES_NEUTRAL) area_trees += t.radius*t.radius*Math.PI;
                    if ((area_trees/ARCHON_SIGHT_AREA) < FOREST_DENSITY_THRESHOLD){
                        build_list = sco_sco_lum;
                        if (est_size < MAP_SMALL){
                            build_list = sol_sco_sco;
                        }
                        else if (est_size > MAP_LARGE){
                            build_list = sco_sco_sco;
                        }
                    }
                    else{
                        build_list = sco_lum_sco;
                    }
                    if (build_list!=null) {
                        rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
                        rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
                        rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);
                    }

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
                SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
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
                        //Macro game adaptations to perceived map size && round number
                        if (rc.getRoundNum() > EARLY_GAME){
                            //Decaying chance to spawn scouts ==> late game less scouts
                            System.out.println("Scout spawn chance:");
                            SCOUT_SPAWN_CHANCE *= (float)(SCOUT_OBSOLETE_LIMIT-rc.getRoundNum()+(0.95f*(rc.getRoundNum()-EARLY_GAME)))/(float)(SCOUT_OBSOLETE_LIMIT-EARLY_GAME);
                            System.out.println(SCOUT_SPAWN_CHANCE);
                        }
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
        can_spawn_gardener = true;
        least_dense_direction = null;

        if (!isSet) {
            visitedArr = new ArrayList<MapLocation>();
            isSet = true;
            targetInfo = null;
            patience = 0;
        }

        try {
            //Only spawn 1 gardener
			/*if (rc.getRoundNum() < EARLY_GAME && est_size < MAP_LARGE){
				System.out.println("Game initializing...");
				System.out.println("Current bullets left: "+Float.toString(rc.getTeamBullets()));
				if (rc.getTeamBullets() < INITIAL_BULLETS-80) can_spawn_gardener = false;
			}*/
            //Check for scout rush in progress
            if (rc.getRoundNum() < EARLY_GAME) {
                if (rc.readBroadcast(SCOUT_RUSH_DETECTED) == 1) can_spawn_gardener = false;
                if (rc.getRoundNum() > GAME_INITIALIZE_ROUND) {
                    for (int i = 0; i < MAX_BUILD_ORDER; i++) {
                        if (rc.readBroadcast(i + BUILD_ORDER_QUEUE) != 0) can_spawn_gardener = false;
                    }
                }
            }
            //Check whether there's request to reserve bullets
            int gardener_need_bullet = rc.readBroadcast(RESERVE_BULLETS);
            int heartbeat = rc.readBroadcast(RESERVE_BULLETS+1);
            if (gardener_need_bullet == 1 && heartbeat > rc.getRoundNum()-3){
                can_spawn_gardener = false;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        if (!isSet){
            gardenerTimer = 0;
            isSet = true;
        }

        //Calls for help when enemies are nearby
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0].getLocation());
        }

        //Look for nearby enemies or bullets
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
        float nearestdist = 1000000f;
        RobotInfo nearestEnemy = null;
        for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
            float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
            if (dist < nearestdist){
                nearestdist = dist;
                nearestEnemy = enemy;
            }
        }
        if (nearestdist <= ARCHON_MICRO_DIST || nearbyBullets.length > 0){
            archonCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
        }

        //Check whether current position can spawn units, else move
        try {
            for (int i = 0; i < SEARCH_GRANULARITY; i++) {
                if (can_spawn) break;
                Direction cDir = new Direction(i * (DEG_360/SEARCH_GRANULARITY));
                if (!rc.isCircleOccupied(MY_LOCATION.add(cDir, 2.01f), 1f)) {
                    can_spawn = true;
                }
            }
            if (!can_spawn) {
                if (!rc.hasMoved()) {
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
                        if (rc.canMove(least_dense_direction)) rc.move(least_dense_direction);
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        //Spawn Code
        if (rc.getRoundNum() >= gardenerTimer && ((rc.getRoundNum() > 3)?(rand.nextFloat() < SPAWN_GARDENER_CHANCE):true) && getDensity(MY_LOCATION,4.05f, rc.getTeam())<SPAWN_FARMER_DENSITY_THRESHOLD && can_spawn_gardener){

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

        //Debug settling down
        System.out.println("Have I settled? "+Boolean.toString(settledDown));
        System.out.println("Max slots around me: "+Integer.toString(Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1)));

        //Activate emergency mode when there's enemies about...
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0].getLocation());
            emergency = true;
        }

        System.out.println("SCANNED FOR ENEMIES");

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
            settledDown = false; //Will be set to true if a suitable space can be found
        }
        else{
            timeLived += 1;
        }

        try {
            if (settledDown) current_available_slots = getBuildSlots(MY_LOCATION, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1));
            else current_available_slots = getBuildSlots(MY_LOCATION, FARMER_MIN_SLOTS);
            //if (settledDown) current_available_slots = getBuildSlots_exhaustive(MY_LOCATION, 1, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_NEUTRAL.length,1));
            //else current_available_slots = getBuildSlots_exhaustive(MY_LOCATION, FARMER_MIN_SLOTS, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_NEUTRAL.length,1));

            //Debug bytecode costs
            System.out.println("Bytes used to compute build locations: "+Integer.toString(Clock.getBytecodeNum()));

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
            else if (SURROUNDING_TREES_OWN.length > 4){
                //Quickly reserve a spawn location before tree is planted!!!
                Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
                for (int i = 0; i < 5; i++) {
                    cDir = new Direction(absRad(cDir.radians + DEG_60));
                    if (rc.canBuildRobot(RobotType.SCOUT, cDir)) SPAWN_DIR = cDir;
                }
            }
        } catch (Exception e) {
            System.out.println("Gardener fails to get surrounding build slots");
            e.printStackTrace();
        }

        //Debug for spawn direction
        if (SPAWN_DIR != null) System.out.println("SPAWN_DIR: "+Float.toString(SPAWN_DIR.radians));

        //Check for early game starts
        try {
            if (rc.getRoundNum() < EARLY_GAME) {
                if (SPAWN_DIR != null) {
                    //Follow build order
                    for (int i=0;i<MAX_BUILD_ORDER;i++){
                        int cur_build = rc.readBroadcast(i+BUILD_ORDER_QUEUE);
                        //if (cur_build != 0) emergency = true;
                        if (cur_build == 1){
                            if (rc.canBuildRobot(RobotType.SCOUT, SPAWN_DIR)) {
                                rc.buildRobot(RobotType.SCOUT, SPAWN_DIR);
                                rc.broadcast(i+BUILD_ORDER_QUEUE, 0);
                            }
                        }
                        else if (cur_build == 2){
                            if (rc.canBuildRobot(RobotType.LUMBERJACK, SPAWN_DIR)) {
                                rc.buildRobot(RobotType.LUMBERJACK, SPAWN_DIR);
                                rc.broadcast(i+BUILD_ORDER_QUEUE, 0);
                            }
                        }
                        else if (cur_build == 3){
                            if (rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIR)) {
                                rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
                                rc.broadcast(i+BUILD_ORDER_QUEUE, 0);
                            }
                        }
                    }
                }
                else {
					/*int early_scout_build = rc.readBroadcast(EARLY_SCOUT);
					if (early_scout_build == 1) {
						if (forceUnitSpawn(RobotType.SCOUT, SEARCH_GRANULARITY)) rc.broadcast(EARLY_SCOUT, 0);
					}*/
                    int cur_build = rc.readBroadcast(BUILD_ORDER_QUEUE);
                    //if (cur_build != 0) emergency = true;
                    if (cur_build == 1){
                        if (rc.canBuildRobot(RobotType.SCOUT, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.SCOUT, SPAWN_DIR);
                            rc.broadcast(BUILD_ORDER_QUEUE, 0);
                        }
                    }
                    else if (cur_build == 2){
                        if (rc.canBuildRobot(RobotType.LUMBERJACK, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.LUMBERJACK, SPAWN_DIR);
                            rc.broadcast(BUILD_ORDER_QUEUE, 0);
                        }
                    }
                    else if (cur_build == 3){
                        if (rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
                            rc.broadcast(BUILD_ORDER_QUEUE, 0);
                        }
                    }
                }
            } /*else if (rc.readBroadcast(SCOUT_RUSH_DETECTED) == 1 && rc.readBroadcast(SCOUT_RUSHED) == 0) {
                //Scout rush still not responded to!
				emergency = true;
				if (rc.isBuildReady()) {
					if (rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIR)) {
						rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
						rc.broadcast(SCOUT_RUSHED, 1);
					} else {
						//put in request to reserve some bullets
						rc.broadcast(RESERVE_BULLETS, 1);
						rc.broadcast(RESERVE_BULLETS + 1, rc.getRoundNum());
					}
				}
            }*/
            if (SPAWN_DIR != null) {
                //Follow build order
                for (int i = 0; i < MAX_BUILD_ORDER; i++) {
                    int cur_build = rc.readBroadcast(i + BUILD_ORDER_QUEUE);
                    if (cur_build == 1) {
                        if (rc.canBuildRobot(RobotType.SCOUT, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.SCOUT, SPAWN_DIR);
                            rc.broadcast(i + BUILD_ORDER_QUEUE, 0);
                        }
                    } else if (cur_build == 2) {
                        if (rc.canBuildRobot(RobotType.LUMBERJACK, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.LUMBERJACK, SPAWN_DIR);
                            rc.broadcast(i + BUILD_ORDER_QUEUE, 0);
                        }
                    } else if (cur_build == 3) {
                        if (rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIR)) {
                            rc.buildRobot(RobotType.SOLDIER, SPAWN_DIR);
                            rc.broadcast(i + BUILD_ORDER_QUEUE, 0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS)) settledDown = true;

        //Debug bytecode costs
        System.out.println("Bytes thus far: "+Integer.toString(Clock.getBytecodeNum()));

        if (!settledDown){
            //Try to spawn a lumberjack
            //Check if there are trees around
            try {
                if (SPAWN_DIR != null) {
                    if (SURROUNDING_ROBOTS_ENEMY.length > 0) {
                        spawnUnit(RobotType.SOLDIER, SPAWN_DIR);
                    } else if (rc.getRoundNum() < EARLY_GAME) {
                        if (!spawnedLumberjack) {
                            spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                            spawnedLumberjack = true;
                        }
                    } else {
                        spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                    }
                }
                else if (rc.getRoundNum() - GARDENER_STUCK_TIMER > ROBOT_SPAWN_ROUND) {
                    for (int i = 0; i < SEARCH_GRANULARITY; i++) {
                        Direction cDir = new Direction(i * DEG_360/SEARCH_GRANULARITY);
                        spawnUnit(RobotType.LUMBERJACK, cDir);
                    }
                }

                //Look for a space with a circle of radius 3
                if (target == null) target = ENEMY_ARCHON_LOCATION;
                gardenerMove(GARDENER_MOVE_GRANULARITY);

            } catch (Exception e){
                e.printStackTrace();
            }

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
            if (!emergency){
                try {
                    for (Direction dir : current_available_slots) {
                        if (rc.canPlantTree(dir)) rc.plantTree(dir);
                    }
                    //Try forcing 5 trees around gardener
                    if (SURROUNDING_TREES_OWN.length < 5 && SURROUNDING_TREES_OWN.length > 0) {
                        Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
                        for (int i = 0; i < 5; i++) {
                            cDir = new Direction(absRad(cDir.radians + DEG_60));
                            if (rc.canPlantTree(cDir)){
                                System.out.println("Forcing planting of tree at: "+Float.toString(cDir.radians));
                                float angle_diff = absRad(cDir.radians - SPAWN_DIR.radians);
                                if (angle_diff > DEG_180) angle_diff = DEG_360-angle_diff;
                                if (angle_diff >= DEG_60) rc.plantTree(cDir);
                            }
                        }
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

            if (rc.getRoundNum() >= soldierTimer){
                try {
                    if (rand.nextFloat() < SCOUT_SPAWN_CHANCE){
                        if (spawnUnit(RobotType.SCOUT, SPAWN_DIR)){
                            soldierTimer = rc.getRoundNum() + SCOUT_DELAY;
                        }
                    }
                    else if (spawnUnit(RobotType.SOLDIER, SPAWN_DIR)) {
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
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0].getLocation());
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
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
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

            //Attack before moving
            //System.out.println("Nearest Enemy Dist: "+nearestdist);
            if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0){
                //System.out.println("I'm making my COMBAT MOVE");
                soldierCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
            }
            else{
                //Move to nearest enemy's position
                target = nearestEnemy.getLocation();
                moveToTarget(MY_LOCATION);
            }

            MY_LOCATION = rc.getLocation();

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

    static void runScout() {
        //Broadcast whether scout is present
		/*try {
			rc.broadcast(21, rc.getRoundNum());
		} catch (GameActionException e) {
			e.printStackTrace();
		}*/
        //Set important variables
        if (!isSet){
            target = ENEMY_ARCHON_LOCATION;
            visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
            patience = 0;
            inCombat = false;
            isSet = true;
        }
        boolean lone_archon = false;

        //Look for nearby enemies
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            //Don't engage early game ARCHON
            if (rc.getRoundNum() < MID_GAME && SURROUNDING_ROBOTS_ENEMY.length == 1){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Only archon detected...");
                    inCombat = false;
                    lone_archon = true;
                }
                else inCombat = true;
            }
            else inCombat = true;
            //Go into combat
            target = null;

            if(!lone_archon) {
                //Call all noncombat soldiers to enemy position
                float nearestdist = 1000000f;
                RobotInfo nearestEnemy = null;
                for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY) {
                    float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
                    if (dist < nearestdist) {
                        nearestdist = dist;
                        nearestEnemy = enemy;
                    }
                }
                spotEnemy(nearestEnemy.getLocation());
            }
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
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[0].location);
            //Don't engage early game ARCHON
            if (rc.getRoundNum() < MID_GAME){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Ignoring early game archon");
                    if (SURROUNDING_ROBOTS_ENEMY.length == 1){
                        target = null;
                        inCombat = false;
                        lone_archon = true;
                    }
                    else {
                        nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                        nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[1].location);
                    }
                }
            }

            if (!lone_archon) {

                RobotInfo attackTgt = null;

                //Attack before moving
                //System.out.println("Nearest Enemy Dist: "+nearestdist);
                if (nearestdist <= SCOUT_MICRO_DIST || nearbyBullets.length > 0) {
                    //System.out.println("I'm making my COMBAT MOVE");
                    attackTgt = scoutCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                } else {
                    //Move to nearest enemy's position
                    target = nearestEnemy.getLocation();
                    moveToTarget(MY_LOCATION);
                }

                MY_LOCATION = rc.getLocation();
            }
            else{
                if (target == null){
                    target = newScoutingLocation(20);
                }
                if (target != null){
                    moveToTarget(MY_LOCATION);
                    if (target != null){
                        if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
                            target = newScoutingLocation(20);
                        }
                    }
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

    //==========================================================================
    //<--------------------------- METRIC FUNCTIONS --------------------------->
    //==========================================================================
    //Metric to test if gardeners are spaced out enough :)
    static boolean isSpreadOut(MapLocation myLoc, float radius){
        RobotInfo[] farmers = rc.senseNearbyRobots(radius, rc.getTeam());
        float distance_to_archon = 999f;

        //Avoid gardeners/archons
        for (RobotInfo f:farmers) if (f.type == RobotType.GARDENER || f.type == RobotType.ARCHON) return false;

        //Check distance to archon
        RobotInfo[] archons = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a:archons){
            if (a.type == RobotType.ARCHON){
                float curDist = myLoc.distanceTo(a.location);
                System.out.println("ARCHON NEARBY!!! DISTANCE: "+Float.toString(curDist));
                if (curDist<distance_to_archon) distance_to_archon = curDist;
            }
        }

        if (distance_to_archon < FARMER_ARCHON_DISTANCE) return false;

        //Avoids all friendly robots
        //if (farmers.length > 0) return false;

        return true;
    }

    //Check if have enough space to plant some trees :)
    static ArrayList<Direction> getBuildSlots_legacy(MapLocation origin, int MIN_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();

        Direction[] hexPattern = new Direction[6];
        for (int i = 0; i < 6; i++) {
            hexPattern[i] = new Direction(0 + i * (DEG_45 + DEG_15));
        }

        int offset = 0;
        int avail_count = 0;
        boolean found_space = false;

        //Scan in a hexagonal pattern first
        for(int i=0;i<30;i++){
            if(found_space) continue;
            for(int j=0;j<6;j++){
                if(rc.canPlantTree(new Direction(absRad(hexPattern[j].radians+i*DEG_1*2)))){
                    offset = i;
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
            else found_space = true;
        }

        if(found_space){
            for(int j=0;j<6;j++){
                if(rc.canPlantTree(new Direction(absRad(hexPattern[j].radians+offset*DEG_1*2)))){
                    available_slots.add(new Direction(absRad(hexPattern[j].radians+offset*DEG_1*2)));
                }
            }
        }

        //Irregular hexagonal pattern (less than 6 slots, wiggle around for build space)

        return available_slots;
    }

    //Better checking build slots functions :D
    static ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();
        //Scan in a hexagonal pattern first
        for (int i=0;i<20;i++){
            if (available_slots.size() > MIN_SLOTS) return available_slots;
            else available_slots.clear();
            Direction curDir = new Direction(absRad(DEG_1*3*i));
            if (!rc.canPlantTree(curDir)) continue;
            else {
                available_slots.add(curDir);
                curDir = curDir.rotateRightRads(DEG_60);
                for (int j=0;j<48;j++){
                    if ((48-j)*DEG_1*5 < (MIN_SLOTS - available_slots.size() - 1)*DEG_60) break;
                    if (rc.canPlantTree(curDir)){
                        available_slots.add(curDir);
                        curDir = curDir.rotateRightRads(DEG_60);
                        j+=12;
                    }
                    else curDir = curDir.rotateRightRads(DEG_1*5);
                }
            }
        }

        return available_slots;
    }

    //Better checking build slots functions :D
    static ArrayList<Direction> getBuildSlots_exhaustive(MapLocation origin, int MIN_SLOTS, int MAX_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();
        ArrayList<Direction> best_slots = new ArrayList<Direction>();
        int scan_counter = 0;
        int num_best_slots = 0;
        //Scan in a hexagonal pattern first
        for (int i=0;i<20;i++){
            if (available_slots.size() > num_best_slots){
                num_best_slots = available_slots.size();
                if (num_best_slots >= MAX_SLOTS) return available_slots;
                best_slots.clear();
                best_slots.addAll(available_slots);
                available_slots.clear();
            }
            else if (available_slots.size() > MIN_SLOTS){
                if (scan_counter > 2) return available_slots;
                else{
                    available_slots.clear();
                    scan_counter++;
                }
            }
            else available_slots.clear();
            Direction curDir = new Direction(absRad(DEG_1*3*i));
            if (!rc.canPlantTree(curDir)) continue;
            else {
                available_slots.add(curDir);
                curDir = curDir.rotateRightRads(DEG_60);
                for (int j=0;j<48;j++){
                    if ((48-j)*DEG_1*5 < (MIN_SLOTS - available_slots.size() - 1)*DEG_60) break;
                    if (rc.canPlantTree(curDir)){
                        available_slots.add(curDir);
                        curDir = curDir.rotateRightRads(DEG_60);
                        j+=12;
                    }
                    else curDir = curDir.rotateRightRads(DEG_1*5);
                }
            }
        }
        if (num_best_slots > MIN_SLOTS) return best_slots;
        else{
            available_slots.clear();
            return available_slots;
        }
    }

    //Get Density of objects (area)
    static float getDensity(MapLocation center, float radius) {
        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
        TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
        float robots_area = 0;
        float tree_area = 0;
        for (RobotInfo r:robots) robots_area += r.getRadius()*r.getRadius()*DEG_180;
        for (TreeInfo t:trees) tree_area += t.getRadius()*t.getRadius()*DEG_180;
        return (robots_area+tree_area) / (radius*radius*DEG_180);
    }

    //Get Density of objects
    static float getDensity_linear(MapLocation center,float radius){
        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
        TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
        return (float)(robots.length+trees.length)/(radius);
    }

    //Get Density of robots from the team
    static float getDensity(MapLocation center,float radius,Team t){
        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, t);
        return (float)(robots.length)/(radius);
    }

    //==========================================================================
    //<--------------------------- MICRO FUNCTIONS --------------------------->
    //==========================================================================
    //<----------------------------- ARCHON MICRO ---------------------------->
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

    static void archonCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets) {
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
        for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
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

    //<----------------------------- SCOUT MICRO ----------------------------->
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

    static RobotInfo scoutCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
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

        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);


        float nearestDistToGardener = 10000000f;
        MapLocation bestMoveLoc = null;
        //See if there is any tree available
        for (TreeInfo tree : nearbyTrees){
            boolean canMove = true;
            for (RobotInfo lumberjack : nearbyLumberjacks){
                //System.out.println("Dist: "+tree.getLocation().distanceTo(lumberjack.getLocation()));
                if (tree.getLocation().distanceTo(lumberjack.getLocation()) <= LUMBERJACK_AVOIDANCE_RANGE_SCOUT){
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
        for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
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


        if (nearestGardener == null){
            return nearestLumberjack;
        }
        return nearestGardener;
    }

    //<---------------------------- SOLDIER MICRO ---------------------------->
    static float getSoldierScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
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

        return ans;
    }

    static void soldierCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
        MapLocation myLoc = rc.getLocation();
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
        boolean hasMoved = false; //Have I moved already?

        //Process nearby enemies
        for (RobotInfo enemy : nearbyEnemies){
            if (enemy.getType() == RobotType.LUMBERJACK){
                nearbyLumberjacks.add(enemy);
            }
            else if (enemy.getType() != RobotType.ARCHON){
                nearbyCombatUnits.add(enemy);
            }
        }

        //System.out.println(nearbyLumberjacks);

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        //Should I use -1?

        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
		/*if (nearestEnemy != null){
			if (myLoc.distanceTo(nearestEnemy.location) > PENTAD_EFFECTIVE_DISTANCE){
				should_fire_pentad = false;
				if (nearestEnemy.type == RobotType.LUMBERJACK || nearestEnemy.type == RobotType.ARCHON || nearestEnemy.type == RobotType.SCOUT ){
					should_fire_triad = false;
				}
			}
		}*/

        //Conduct attack
        //Avoid friendly fire
        if (rc.canFirePentadShot() && should_fire_pentad){
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
        if (!hasFired && rc.canFireTriadShot() && should_fire_triad){
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
        for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }

            if (rc.canMove(chosenDir)){
                //rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                float score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
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
        float score = getSoldierScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
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
        return;
    }

    //==========================================================================
    //<--------------------------- HELPER FUNCTIONS --------------------------->
    //==========================================================================
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

    static float distanceScaling(float dist){
        if (dist <= 1.0f){
            return 10f; //Very good
        }
        else{
            return 10f / dist; //Not so good
        }
    }

    static boolean forceUnitSpawn(RobotType type, int granularity){
        for (int i=0;i<granularity;i++){
            Direction cDir = new Direction(i*DEG_360/granularity);
            if (rc.canBuildRobot(type, cDir)){
                try {
                    rc.buildRobot(type, cDir);
                } catch (Exception e){
                    e.printStackTrace();
                }
                return true;
            }
        }
        return false;
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

}