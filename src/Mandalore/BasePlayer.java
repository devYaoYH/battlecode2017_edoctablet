package Mandalore;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class BasePlayer {
    static RobotController rc;

    //Debugging
    static boolean debug = false;
    static boolean microTest = false;

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
    static final float EPSILON = 0.1f;

    //Constant variables
    static final float epsilon = 1e-08f;
    static final int VISITED_ARR_SIZE = 10;         			//Number of previous locations recorded

    //Early game estimation heuristics
    static final float MAP_SMALL = 40f;							//Max size of map considered 'small'
    static final float MAP_LARGE = 73f;							//Max size of map considered 'large'
    static final float INITIAL_BULLETS = 300f;					//Starting number of bullets

    //Movement finesse
    static final int MOVEMENT_GRANULARITY = 45;      			//Number of possible move directions
    static final float MOVEMENT_SEARCH_ANGLE = DEG_90;          //Angle to search for possible moves
    static final int MOVEMENT_GRANULARITY_MICRO = 8;      		//Number of possible move directions for micro
    static final int BASHING_GRANULARITY = 15;                  //Number of angles around the targeted direction to force first before wall-following
    static final int SEARCH_GRANULARITY = 24;       			//Number of search directions
    static final int SENSOR_GRANULARITY = 72;                   //How many angles to scan around
    static final float SENSOR_SMOOTHENING_THRESHOLD = 0.001f;   //Smoothens 'bumps' in the sensor data
    static float MOVEMENT_SCANNING_RADIUS = 1.1f;               //Radius of dot scanned to move in
    static float MOVEMENT_SCANNING_DISTANCE = 2.1f;             //Distance away from center to scan in
    static final float TARGET_RELEASE_DIST = 2f;    			//If target falls within this distance, release from target
    static final float STUCK_THRESHOLD = 0.2f;      			//Threshold to call if unit is stuck
    static final int STUCK_THRESHOLD_TURNS = 17;                //Number of turns spent going back and forth being stuck...
    static final int GARDENER_MOVE_GRANULARITY = 12;			//Number of times gardener will try random directions

    //Game-changing macro controls
    static final int MAX_STOCKPILE_BULLET = 500;    			//Maximum bullets in stockpile before donation occurs
    static final int DONATION_AMT = 50;             			//Amount donated each time
    static final int GAME_INITIALIZE_ROUND = 2;					//How many rounds before game is initialized
    static final int EARLY_GAME = 124;              			//How early is counted as 'early game'
    static final int MID_GAME = 500;							//When mid-game starts
    static final int LATE_GAME = 1800;							//Late game case
    static int THE_FINAL_PROBLEM = 2998;						//Last ditch effort
    static final float FOREST_DENSITY_THRESHOLD = 0.15f;		//% of area covered by trees in sense range
    static final int SUSTAINABLE_ECONOMY = 20;                  //Minimum number of trees before tank production kicks in
    static final float OFFENSE_FACTOR = 3f; 					//Factor to prioritise offense / defense for scout micro [Higher => more offensive]
    static final int SOLDIER_ALARM_RELEVANCE = 5;   			//Relevance of a soldier alarm
    static final int MAX_ENEMY_PINGS = 15;                      //Maximum of queued enemy alerts
    static final int PING_LIFESPAN = 100;                       //How many rounds it takes for enemy ping to 'die'

    //Farmer control variables
    static float FARMER_ARCHON_DISTANCE = 5f;		            //Distance to be away from archon before starting farm
    static final int FARMER_MIN_SLOTS = 4;          			//Minimum number of trees that can be built before starting farm [1-6]
    static final int TREE_DELAY = 12;                           //Number of turns before able to build another tree
    static final float PLANT_TREE_CHANCE = 1.00f;               //Chance to actually plant a tree

    //Early game build order
    static final int MAX_BUILD_ORDER = 2;						//Reserve how many slots in build queue
    static final int BUILD_ORDER_QUEUE = 100;					//Start of channel for build order

    //Micro constants
    static final int BYTE_THRESHOLD = 12500;                    //Max number of bytecodes to be used...
    static final int SUSTAINABLE_TREE_THRESHOLD = 0;            //Sustainable amount of trees for soldiers to spam shots
    static final float CONSERVE_BULLETS_LIMIT = 157f;           //Above this, spam, below, conserve ammo by not shooting at trees
    static final float ARCHON_MICRO_DIST = 100f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float SCOUT_MICRO_DIST = 64f;				    //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float SOLDIER_MICRO_DIST = 81f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float TANK_MICRO_DIST = 81f;                   //Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_AVOIDANCE_RANGE = 6f; 		//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_ARCHON = 8f; 	//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_SCOUT = 4f;	//How far to avoid lumberjacks
    static float PENTAD_EFFECTIVE_DISTANCE = 3f;		        //(1/tan(7.5deg)) Such that at least 2 shots will hit a target with radius 1
    static final float BULLET_SENSE_RANGE = 3f; 				//The range at which bullets are sensed
    static final float SOLDIER_COMBAT_SENSE_RADIUS = 5f;        //Distance to sense friendly units at (soldier)
    static final float SCOUT_COMBAT_SENSE_RADIUS = 5f;          //Distance to sense friendly units at (scout)
    static final float POINT_BLANK_RANGE = 0.15f;               //Distance to just shoot an enemy even if friendly fire will happen

    //Danger levels per robot
    static final int TRIAD_SHOT_FACTOR = 3;                     //Ability to use multi-shot attacks
    static final int PENTAD_SHOT_FACTOR = 5;                    //Ability to use multi-shot attacks
    static final float MAX_DANGER_RATING = 5000;                //Maximum danger rating (scaled to [0-9])
    static float DANGER_GARDENER;                               //Currently: 0
    static float DANGER_LUMBERJACK;                             //Currently: 100
    static float DANGER_SCOUT;                                  //Currently: 10
    static float DANGER_SOLDIER;                                //Currently: 500
    static float DANGER_TANK;                                   //Currently: 3000
    static float[] DANGER_RATINGS = new float[7];               //List of danger ratings

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
    static final float TEST_ON_MAP_RADIUS = 0.25f;
    static final float SENSE_SURROUNDING_DENSITY_RADIUS = 5f;

    static ArrayList<Direction> HEXAGONAL_PATTERN = new ArrayList<Direction>(Arrays.asList(new Direction(0f), new Direction(DEG_60), new Direction (DEG_60*2), new Direction(DEG_180),
            new Direction(DEG_60*4), new Direction(DEG_60*5)));
    static float[] SENSOR_ANGLES = new float[SENSOR_GRANULARITY];

    //Robot specific variables
    static boolean isSet;                           //Whether important local variables are set
    static int timeLived;                           //Time since spawn
    static int patience;                            //For movement
    static ArrayList<MapLocation> visitedArr;       //For movement
    static MapLocation target;                      //This is the position the robot will move to
    static boolean inCombat;                        //Is this robot in combat?
    static int ROBOT_SPAWN_ROUND = 0;

    //Recon map
    static Broadcaster broadcaster;
    static Broadcaster treeBroadcaster;

    //Broadcast channel statics
    static final int BORDER_LEFT = 0;
    static final int BORDER_TOP = 1;
    static final int BORDER_RIGHT = 2;
    static final int BORDER_BOTTOM = 3;

    static final int ENEMY_ARCHON_CHANNEL = 9;
    static final int SPOTTED_TURN = 10;
    static final int SPOTTED_LOCATION = 11;
    static final int SPOTTED_ENEMY_TYPE = 12;

    static final int CONTROL_ARCHON = 20;
    static final int CONTROL_ARCHON_HEARTBEAT = 21;

    static final int STAYING_ALIVE = 42;

    static final int SCOUT_CHEESE_SIGNAL = 53;
    static final int FORCE_SCOUT_CHEESE = 54;
    static final int ENEMY_ARCHON_KILLED = 55;

    static final int COUNT_GARDENERS = 300;
    static final int SUM_GARDENERS = 301;
    static final int COUNT_LUMBERJACKS = 302;
    static final int SUM_LUMBERJACKS = 303;
    static final int COUNT_SCOUTS = 304;
    static final int SUM_SCOUTS = 305;
    static final int COUNT_SOLDIERS = 306;
    static final int SUM_SOLDIERS = 307;
    static final int COUNT_TANKS = 308;
    static final int SUM_TANKS = 309;

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

    static final int ENEMY_PINGS = 700;

    static final int SETTLED_FARMERS = 798;
    static final int AVAILABLE_FARMING_LOCATIONS = 799;
    static final int FARMING_LOCATIONS_X = 800;
    static final int FARMING_LOCATIONS_Y = 801;

    static final int BLACKLISTED_FARMING_LOCATIONS = 1299;
    static final int BLACKLISTED_LOCATIONS_X = 1300;
    static final int BLACKLISTED_LOCATIONS_Y = 1301;

    //Broadcaster Arrays
    static final int SPOT_CONTACT_REP = 2000;
    static final int TREE_REP = 2500;
    static final int TARGET_REP = 3000;

    //Selection enums
    static final int ROBOTS = 111;
    static final int TREES = 222;
    static final int OBJECTS = 999;

    /*
     * Broadcast Channels:
     * 0 => left border, 1 => top border, 2 => right border, 3 => bottom border
     * 5 => x_upper, 6 => x_lower, 7 => y_upper, 8 => y_lower
     * 10 => turn enemy scouted, 11 => enemy scouted position [Only updated when in combat]
     *
     * Shared Array SOP
	 * [100-109] Team Orders
	 * [200-209] Team Order Data
	 * [0-300] Team Robot Status
	 *
	 * Squad Setup
	 * [10-99] Team Members
	 * [20] Controlling Archon
	 * [21] Archon heartbeat
	 * [42] Scout donation cheese activation signal
	 * [110-199] Member Data
	 * [210-299] Member Keep Alive Signal
	 *
	 * Unit counts
	 * [300-301] Gardener count
	 * [302-303] Lumberjack count
	 * [304-305] Scout count
	 * [306-307] Soldier count
	 * [308-309] Tank count
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
	 * ENEMY PINGS
	 * [701-703] Grid reference | Message | Heartbeat
	 *
	 * FARM POSITIONS
	 * [799] Available farm positions
	 * [800-801] Farm position X | Farm position Y
	 * [1299] Blacklisted farming positions
	 * [1300-1301] Blacklist position X | Blacklist position Y
	 *
	 * Reserved Channels for future use
	 * [1500-1999]
	 *
	 * Broadcaster Arrays (2000~3500)
	 * [1995-1999] Array Config
	 * [2000-2399] Array 1
	 * [2400-2500] Overflow
	 * ... etc
     *
     */

    /*
     * General TODO:
     * 1. Find a way to determine map density then choose the appropriate bot
     * 2. Improve scout micro to scout out broadcasting enemy robots (hunter killer behavior)
     * 3. OOP the code structure :D
     */

    public BasePlayer(RobotController control) throws GameActionException {
        try {
            BasePlayer.rc = control;
            ROBOT_SPAWN_ROUND = rc.getRoundNum();
            THE_FINAL_PROBLEM = rc.getRoundLimit() - 50;
            ENEMY_ARCHON_LOCATION = readLatestEnemyArchonLocation();
            if (ENEMY_ARCHON_LOCATION != null) System.out.println("Enemy archon location: "+mapLocationToInt(ENEMY_ARCHON_LOCATION));
            if (ENEMY_ARCHON_LOCATION == null){
                ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
                System.out.println("Default location: "+mapLocationToInt(ENEMY_ARCHON_LOCATION));
                updateLatestEnemyArchonLocation(ENEMY_ARCHON_LOCATION);
            }
            MY_LOCATION = rc.getLocation();
            broadcaster = new Broadcaster(rc, SPOT_CONTACT_REP);
            treeBroadcaster = new Broadcaster(rc, TREE_REP);

            //Calculate relative robot risks
            DANGER_GARDENER = 0;
            DANGER_LUMBERJACK = RobotType.LUMBERJACK.maxHealth * RobotType.LUMBERJACK.attackPower;
            DANGER_SCOUT = RobotType.SCOUT.maxHealth * RobotType.SCOUT.attackPower;
            DANGER_SOLDIER = RobotType.SOLDIER.maxHealth * RobotType.SOLDIER.attackPower * PENTAD_SHOT_FACTOR;
            DANGER_TANK = RobotType.TANK.maxHealth * RobotType.TANK.attackPower * TRIAD_SHOT_FACTOR;
            DANGER_RATINGS[robotType(RobotType.ARCHON)] = 0;
            DANGER_RATINGS[robotType(RobotType.GARDENER)] = DANGER_GARDENER;
            DANGER_RATINGS[robotType(RobotType.LUMBERJACK)] = DANGER_LUMBERJACK;
            DANGER_RATINGS[robotType(RobotType.SCOUT)] = DANGER_SCOUT;
            DANGER_RATINGS[robotType(RobotType.SOLDIER)] = DANGER_SOLDIER;
            DANGER_RATINGS[robotType(RobotType.TANK)] = DANGER_TANK;


            //Set up sensor angle array
            for (int i = 0; i < SENSOR_GRANULARITY; i++) {
                SENSOR_ANGLES[i] = i * (DEG_360 / SENSOR_GRANULARITY);
            }
            //Global Initialization
            System.out.println("Robot overhead cost: " + Integer.toString(Clock.getBytecodeNum()));
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    protected void startRound() throws GameActionException {
        //Always be on the lookout for neutral trees - might come in handy for pathing later on too...
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(4f, Team.NEUTRAL);
        ENEMY_ARCHON_LOCATION = readLatestEnemyArchonLocation();
        if (ENEMY_ARCHON_LOCATION == null){
            ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
            updateLatestEnemyArchonLocation(ENEMY_ARCHON_LOCATION);
        }
        MY_LOCATION = rc.getLocation();
        broadcaster.update();
        treeBroadcaster.update();
        return;
    }

    protected void endRound() throws GameActionException {
        //Shake trees in vicinity
        if (SURROUNDING_TREES_NEUTRAL.length > 0){
            if (rc.canShake(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.shake(SURROUNDING_TREES_NEUTRAL[0].ID);
        }

        //Update grid pos
        /*if (SURROUNDING_ROBOTS_ENEMY.length == 0){
            if (target != ENEMY_ARCHON_LOCATION) sitRep();
            else sitRep(true);  //Writes archon present data into message to extend validity till 150 rounds
        }*/
        if (SURROUNDING_ROBOTS_ENEMY.length == 0){
            sitRep();
            if (broadcaster.location_to_channel(MY_LOCATION) == broadcaster.location_to_channel(ENEMY_ARCHON_LOCATION)){
                rc.broadcast(ENEMY_ARCHON_KILLED, 1);
            }
        }

        //todo Neutral Tree reporting
        treeRep();

        //Update latest enemy archon location
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            for (RobotInfo r:SURROUNDING_ROBOTS_ENEMY){
                if (r.type == RobotType.ARCHON){
                    //Update
                    updateLatestEnemyArchonLocation(r.location);
                    rc.broadcast(ENEMY_ARCHON_KILLED, 0);
                    break;
                }
            }
        }

        //Dequeue outdated reports
        //Clean up reports (based on latest boundary information
        if (SURROUNDING_ROBOTS_ENEMY.length == 0) {
            int cur_grid = broadcaster.location_to_channel(MY_LOCATION);
            for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
                int gridCoded = rc.readBroadcast(i);
                MapLocation gridLoc = broadcaster.channel_to_location(gridCoded);
                if (gridCoded == 0) continue;
                if (gridCoded == cur_grid){
                    remove_report_from_queue(gridLoc);
                }
                if (!broadcaster.isValidGrid(gridLoc)){
                    int gridMsg = rc.readBroadcast(i+1);
                    remove_report_from_queue(gridLoc);
                    add_report_to_queue(broadcaster.nearestValidGrid(gridLoc), gridMsg);
                }
            }
        }

        //Donate
        //todo micro testing ==> disable donation
        if (!microTest) donate();

        //Final few rounds, cash everything in!!!
        if (rc.getRoundNum() > THE_FINAL_PROBLEM){
            rc.donate((float)Math.floor(rc.getTeamBullets()/rc.getVictoryPointCost())*rc.getVictoryPointCost());
        }

        //todo updates scout cheesing code
        if (Clock.getBytecodeNum() < 7500){
            SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
            float tot_bullets = 0;
            for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
                tot_bullets += t.containedBullets;
            }
            rc.broadcast(FORCE_SCOUT_CHEESE, rc.readBroadcast(FORCE_SCOUT_CHEESE)+Math.round(tot_bullets));
        }
    }

    //==========================================================================
    //<--------------------------- METRIC FUNCTIONS --------------------------->
    //==========================================================================
    //Metric to test if gardeners are spaced out enough :)
    //RETURNS  false if distance between farmers is < radius
    protected boolean isSpreadOut(MapLocation myLoc, float radius){
        RobotInfo[] farmers = rc.senseNearbyRobots(radius, rc.getTeam());
        //Avoid gardeners
        for (RobotInfo f:farmers){
            if (f.type == RobotType.GARDENER) return false;
            else if (f.type == RobotType.ARCHON && myLoc.distanceTo(f.location) < FARMER_ARCHON_DISTANCE) return false;
        }
        return true;
    }

    //Building slots checking
    //How to check if a position on the map is valid for building (can plant tree)
    private boolean slot_free_metric(MapLocation origin, Direction dir) throws GameActionException {
        //return (rc.isCircleOccupied(origin.add(dir,2.01f),1f));
        return rc.canPlantTree(dir);
    }

    //Distance falloff scaling for score used in micro codes
    protected float distanceScaling(float dist){
        if (dist <= 1.0f){
            return 10f; //Very good
        }
        else{
            return 10f / dist; //Not so good
        }
    }

    //Get Density of objects (area)
    //RETURNS area occupied as % of area scanned (circular radius)
    protected float getDensity(MapLocation center, float radius, int SELECTOR) {
        try {
            if (!rc.onTheMap(center)) return 1f;
        } catch (Exception e){
            e.printStackTrace();
        }
        switch (SELECTOR){
            case ROBOTS:
                return density_robots(center, radius, null);
            case TREES:
                return density_trees(center, radius, null);
            case OBJECTS:
                return density_robots(center, radius, null) + density_trees(center, radius, null);
            default:
                return 1f;
        }
    }

    //Density of all robots
    private float density_robots(MapLocation center, float radius, Team SELECTOR){
        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, SELECTOR);
        float robots_area = 0;
        for (RobotInfo bot:robots){
            //calculates partial area inside scan radius
            float r = bot.getRadius();
            float R = radius;
            float d = center.distanceTo(bot.location);
            if (d+r<=R) robots_area +=  r*r*DEG_180;
            else {
                if (R < r) {
                    r = radius;
                    R = bot.getRadius();
                }
                float part1 = r * r * (float) Math.acos((d * d + r * r - R * R) / (2 * d * r));
                float part2 = R * R * (float) Math.acos((d * d + R * R - r * r) / (2 * d * R));
                float part3 = 0.5f * (float) Math.sqrt((-d + r + R) * (d + r - R) * (d - r + R) * (d + r + R));
                float intersectionArea = part1 + part2 - part3;

                robots_area += intersectionArea;
            }
        }
        return robots_area / (radius*radius*DEG_180);
    }

    //Density of trees
    private float density_trees(MapLocation center, float radius, Team SELECTOR){
        TreeInfo[] trees = rc.senseNearbyTrees(center, radius, SELECTOR);
        float tree_area = 0;
        for (TreeInfo t:trees){
            //calculates partial area inside scan radius
            float r = t.radius;
            float R = radius;
            float d = center.distanceTo(t.location);
            if (d+r<=R) tree_area +=  r*r*DEG_180;
            else {
                if (R < r) {
                    r = radius;
                    R = t.radius;
                }
                float part1 = r * r * (float) Math.acos((d * d + r * r - R * R) / (2 * d * r));
                float part2 = R * R * (float) Math.acos((d * d + R * R - r * r) / (2 * d * R));
                float part3 = 0.5f * (float) Math.sqrt((-d + r + R) * (d + r - R) * (d - r + R) * (d + r + R));
                float intersectionArea = part1 + part2 - part3;

                tree_area += intersectionArea;
            }
        }
        return tree_area / (radius*radius*DEG_180);
    }

    //==========================================================================
    //<-------------- THE ALMIGHTY PATHFINDING THINGUMMYWHAT ----------------->
    //==========================================================================
    protected Direction closest_direction_to_target(int GRANULARITY, int sensorDir, float distance, boolean rightBot){
        if (rightBot) {
            for (int i = 1; i < GRANULARITY; i++) {
                Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir + i, GRANULARITY)]);
                if (rc.canMove(tryDir, distance)) {
                    return tryDir;
                }
            }
        }
        else {
            for (int i = 1; i < GRANULARITY; i++) {
                Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir - i, GRANULARITY)]);
                if (rc.canMove(tryDir, distance)) {
                    return tryDir;
                }
            }
        }
        return null;
    }

    //Slug Pathing algo...
    protected void moveToTargetExperimental(MapLocation myLoc, int GRANULARITY, boolean rightBot, boolean stuck, int SLUG_GRANULARITY){
        if (rc.hasMoved()) return;
        if (myLoc == target) return;
        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<GRANULARITY;i++){
            if (absRad(initDir.radians) > SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (absRad(initDir.radians) - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir++, GRANULARITY);

        if (debug) rc.setIndicatorLine(myLoc, myLoc.add(SENSOR_ANGLES[sensorDir], 2f), 0, 255, 0);

        //Show movement direction
//        Direction cDir = new Direction(SENSOR_ANGLES[0]);
//        MapLocation scanning_loc = null;
//        for (int i=0;i<GRANULARITY;i++){
//            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
//            if (rc.canMove(cDir)) rc.setIndicatorDot(scanning_loc, 0, 0, 255);
//            else rc.setIndicatorDot(scanning_loc, 255, 0, 0);
//            cDir = new Direction(SENSOR_ANGLES[i]);
//        }
//        rc.setIndicatorDot(MY_LOCATION.add(initDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 0);

        //Bug pathing algorithm!! :O
        //Right turning bot...

        //Try trivial case

        try {
            broadcaster.updateBoundaries(myLoc);
            //todo DANGEROUS CODE...use tryMove unless stuck...
//            if (rc.canMove(initDir)){
//                rc.move(initDir);
//                return;
//            }
            if (!stuck){
                if (tryMove(initDir)) return;
            }
            if (!rc.hasMoved()){
                //Slug Pathing
                Direction tryDir = null;
                Direction bestDir = null;
                float closestAngle = 99999f;
                float distanceMoved = 0f;
                for (int i=0;i<SLUG_GRANULARITY;i++){
                    tryDir = closest_direction_to_target(GRANULARITY, sensorDir, rc.getType().strideRadius/(float)Math.pow(2,i), rightBot);
                    if (initDir.radiansBetween(tryDir) < closestAngle){
                        bestDir = tryDir;
                        closestAngle = initDir.radiansBetween(tryDir);
                        distanceMoved = rc.getType().strideRadius/(float)Math.pow(2,i);
                    }
                }
                if (bestDir != null){
                    rc.move(bestDir, distanceMoved);
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return;
    }

    //Bruteforcing way pathing
    protected boolean tryMove(Direction moveDir) throws GameActionException {
        if (rc.hasMoved()) return false;
        if (rc.canMove(moveDir)){
            rc.move(moveDir);
            return true;
        }
        for (int i=1;i<MOVEMENT_GRANULARITY;i++){
            Direction tryDir = moveDir.rotateRightRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (rc.canMove(tryDir)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
            tryDir = moveDir.rotateLeftRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (rc.canMove(tryDir)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
        }
        return false;
    }

    //Note: MapLocation target must have been set, visitedArr and patience should be initialised
    protected void moveToTarget(MapLocation myLoc){
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
                        broadcaster.updateBoundaries(myLoc);
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

    //==========================================================================
    //<--------------------------- MICRO FUNCTIONS --------------------------->
    //==========================================================================
    //Better checking build slots for farmers functions :D
    protected ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();
        //Scan in a hexagonal pattern first
        for (int i=0;i<20;i++){
            if (available_slots.size() > MIN_SLOTS) return available_slots;
            else available_slots.clear();
            //todo skip? or overrun and get more build slots?
            //if (Clock.getBytecodeNum() > BYTE_THRESHOLD-1500) continue;
            Direction curDir = new Direction(absRad(DEG_1*3*i));
            if (!slot_free_metric(origin,curDir)) continue;
            else {
                available_slots.add(curDir);
                curDir = curDir.rotateRightRads(DEG_60);
                for (int j=0;j<48;j++){
                    if ((48-j)*DEG_1*5 < (MIN_SLOTS - available_slots.size() - 1)*DEG_60) break;
                    if (slot_free_metric(origin,curDir)){
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

    //Gets least dense direction given a list of directions
    protected Direction getLeastDenseDirection(ArrayList<Direction> test_dir) throws GameActionException {
        //Get most spacious direction
        float density = 999999f;
        Direction spawn_direction = null;
        for(int i=0;i<test_dir.size();i++){
            float eval = getDensity(MY_LOCATION.add(test_dir.get(i),rc.getType().bodyRadius + 1f + EPSILON),5f + EPSILON, TREES);
            if (!rc.onTheMap(MY_LOCATION.add(test_dir.get(i),rc.getType().bodyRadius + 4f + EPSILON), TEST_ON_MAP_RADIUS)) continue;
            if(eval<density){
                density = eval;
                spawn_direction = test_dir.get(i);
            }
        }
        return spawn_direction;
    }

    //==========================================================================
    //<------------------------- COLLISION FUNCTIONS -------------------------->
    //==========================================================================
    //Collision checking between bullet and other robots
    protected boolean willCollideWithRobot(Direction bulletDir, MapLocation bulletLoc, MapLocation robotLoc, float robotBodyRadius) {

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

    //Collision checking between bullet and self
    protected boolean willCollideWithMe(MapLocation newLoc, BulletInfo bullet) {
        System.out.println("Collision function: "+Integer.toString(Clock.getBytecodeNum()));
        MapLocation myLocation = newLoc;

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        //If already collided
        if (bulletLocation.distanceTo(myLocation) < rc.getType().bodyRadius) return true;

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
        System.out.println(absRad(theta));
        System.out.println((int)(absRad(theta)/DEG_1));

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    //Collision checking between bullet and trees
    protected boolean willCollideWithTree(Direction targetDir, TreeInfo[] surrounding_trees) {

        // Get relevant bullet information
        Direction propagationDirection = targetDir;

        for (TreeInfo t:surrounding_trees) {
            //Snap out if bytecode limit exceeded
            if (Clock.getBytecodeNum() > BYTE_THRESHOLD) return false;

            // Calculate direction towards tree
            Direction directionToTree = MY_LOCATION.directionTo(t.location);
            float distanceToTree = MY_LOCATION.distanceTo(t.location);
            float theta = propagationDirection.radiansBetween(directionToTree);

            // If theta > 90 degrees, then the tree is out of the way and we can break early
            if (Math.abs(theta) > Math.PI / 2) {
                continue;
            }

            float perpendicularDist = (float) Math.abs(distanceToTree * Math.sin(theta)); // soh cah toa :)
            if (perpendicularDist <= t.radius) return true;
        }

        return false;
    }

    //==========================================================================
    //<------------------------ MAP UPDATE FUNCTIONS -------------------------->
    //==========================================================================
    //Test whether grid search location has been reached
    protected boolean hasReachedGrid(MapLocation target_grid){
        return MY_LOCATION.distanceTo(target_grid) < TARGET_RELEASE_DIST;
    }

    //Returns profile of enemy information
    //Most Prevalent RobotType | Number of most prevalent robot | Whether archon is present
    protected int[] profileSurroundingEnemies(RobotInfo[] surr_enemies){
        int[] enemy_data = new int[8];
        if (surr_enemies.length > 0){
            //Enemies present in vicinity
            //Get a profile of enemies
            int num_gardeners = 0;
            int num_lumberjacks = 0;
            int num_scouts = 0;
            int num_soldiers = 0;
            int num_tanks = 0;
            boolean archon_present = false;
            for (RobotInfo r:surr_enemies){
                switch(r.type){
                    case GARDENER:
                        num_gardeners++;
                        break;
                    case LUMBERJACK:
                        num_lumberjacks++;
                        break;
                    case SCOUT:
                        num_scouts++;
                        break;
                    case SOLDIER:
                        num_soldiers++;
                        break;
                    case TANK:
                        num_tanks++;
                        break;
                    case ARCHON:
                        archon_present = true;
                        break;
                }
            }
            int max_units = Math.max(num_lumberjacks,Math.max(num_gardeners,Math.max(num_scouts,Math.max(num_soldiers,num_tanks))));
            RobotType most_frequent_unit = null;
            if (num_tanks == max_units){
                most_frequent_unit = RobotType.TANK;
            }
            else if (num_soldiers == max_units){
                most_frequent_unit = RobotType.SOLDIER;
            }
            else if (num_lumberjacks == max_units){
                most_frequent_unit = RobotType.LUMBERJACK;
            }
            else if (num_gardeners == max_units){
                most_frequent_unit = RobotType.GARDENER;
            }
            else if (num_scouts == max_units){
                most_frequent_unit = RobotType.SCOUT;
            }
            enemy_data[0] = robotType(most_frequent_unit);
            enemy_data[1] = max_units;
            if (archon_present) enemy_data[2] = 1;
            else enemy_data[2] = 0;
            enemy_data[3] = num_gardeners;
            enemy_data[4] = num_lumberjacks;
            enemy_data[5] = num_scouts;
            enemy_data[6] = num_soldiers;
            enemy_data[7] = num_tanks;
            return enemy_data;
        }
        else return null;
    }

    //Assess situation of enemy deployment
    private int assessAlert(int[] enDep){
        if (enDep == null) return 0;
        float cur_danger = 0f;
        int num_gardeners = enDep[3];
        int num_lumberjacks = enDep[4];
        int num_scouts = enDep[5];
        int num_soldiers = enDep[6];
        int num_tanks = enDep[7];
        cur_danger += num_gardeners*DANGER_GARDENER;
        cur_danger += num_lumberjacks*DANGER_LUMBERJACK;
        cur_danger += num_scouts*DANGER_SCOUT;
        cur_danger += num_soldiers*DANGER_SOLDIER;
        cur_danger += num_tanks*DANGER_TANK;
        return Math.min(9, Math.round(cur_danger/MAX_DANGER_RATING*9));     //Max value is 9
    }

    //Enqueue report
    public void add_report_to_queue(MapLocation reportLoc, int msg) throws GameActionException {
        if (debug) rc.setIndicatorLine(MY_LOCATION, reportLoc, 0, 0, 0);
        //Look for report at same location, if outdated, replace with current info
        int grid_report_index = 0;
        int report_channel = broadcaster.location_to_channel(reportLoc);
        for (int i=ENEMY_PINGS;i<ENEMY_PINGS+MAX_ENEMY_PINGS*3;i+=3){
            if (rc.readBroadcast(i) == report_channel){
                //Found similar reporting location
                grid_report_index = i;
            }
        }
        if (grid_report_index == 0) {
            //No prior reports found
            for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
                if (rc.readBroadcast(i + 2) <= Math.max(0, rc.getRoundNum() - PING_LIFESPAN)) {
                    //current slot outdated
                    rc.broadcast(i, report_channel);
                    rc.broadcast(i + 1, msg);
                    rc.broadcast(i + 2, rc.getRoundNum());
                    return;
                }
            }
        }
        else{
            rc.broadcast(grid_report_index, report_channel);
            rc.broadcast(grid_report_index+1, msg);
            rc.broadcast(grid_report_index+2, rc.getRoundNum());
        }
    }

    //Dequeue report
    public void remove_report_from_queue(MapLocation reportLoc) throws GameActionException {
        rc.setIndicatorLine(reportLoc, reportLoc, 255, 0, 100);
        //Look for report at same location, if outdated, replace with current info
        int report_channel = broadcaster.location_to_channel(reportLoc);
        for (int i=ENEMY_PINGS;i<ENEMY_PINGS+MAX_ENEMY_PINGS*3;i+=3){
            if (rc.readBroadcast(i) == report_channel){
                //Found similar reporting location
                //Wipe report
                rc.broadcast(i, 0);
                rc.broadcast(i+1, 0);
                rc.broadcast(i+2, 0);
            }
        }
    }

    protected void sitRep(boolean archon_present) throws GameActionException {
        if (archon_present) broadcaster.updateGrid(MY_LOCATION, rc.getRoundNum(), 10);
    }

    //Updates situation at current grid locale
    protected void sitRep() throws GameActionException {
        broadcaster.updateGrid(MY_LOCATION, rc.getRoundNum(), 0);
    }

    protected void treeRep() throws GameActionException {
        if (SURROUNDING_TREES_NEUTRAL.length > 0){
            //Occupied tree rep
            treeBroadcaster.updateGrid(MY_LOCATION, rc.getRoundNum(), SURROUNDING_TREES_NEUTRAL.length);
        }
        else{
            //Empty tree rep
            treeBroadcaster.updateGrid(MY_LOCATION, rc.getRoundNum(), 0);
        }
    }

    //todo Forces a ping on the grid
    protected void sitRep(MapLocation pingLoc) throws GameActionException {
        broadcaster.updateGrid(pingLoc, rc.getRoundNum(), 0);
    }

    //Scores an alert rating for enemies
    protected void spotRep() throws GameActionException {
        int[] enemyData = profileSurroundingEnemies(SURROUNDING_ROBOTS_ENEMY);
        if (enemyData == null) return;
        System.out.println("Valid SPOTREP sent, number of enemies: "+Integer.toString(SURROUNDING_ROBOTS_ENEMY.length));
        //Take the grid to be updated to furthest enemy position
        MapLocation reportLoc = SURROUNDING_ROBOTS_ENEMY[SURROUNDING_ROBOTS_ENEMY.length-1].location;
        if (!broadcaster.isValidGrid(reportLoc)){
            reportLoc = broadcaster.nearestValidGrid(reportLoc);
        }
        int archon_present = enemyData[2];
        RobotType most_prevalent_enemy = robotType(enemyData[0]);
        int num_prevalent_enemey = Math.max(9, enemyData[1]);
        Message spotRep = new Message();
        //Enemy type
        spotRep.writeEnemy(most_prevalent_enemy);
        //Enemy number
        spotRep.writeNumberOfEnemies(num_prevalent_enemey);
        //Alert Level
        spotRep.writeAlertLevel(assessAlert(enemyData));
        //Archon presence
        spotRep.writeArchonPresent(archon_present);
        //Message type
        spotRep.writeMessageType(Message.SPOT_REP);

        //Update grid with spotRep
        broadcaster.updateGrid(reportLoc, rc.getRoundNum(), spotRep.getRawMsg());

        //Add report to queue of alerts
        add_report_to_queue(reportLoc, spotRep.getRawMsg());
    }

    //Alerts to enemy contact made
    protected void contactRep(){

    }

    //Alerts to target of opportunity
    protected void targetRep(){

    }

    private void updateLatestEnemyArchonLocation(MapLocation enLoc) throws GameActionException {
        rc.broadcast(ENEMY_ARCHON_CHANNEL, mapLocationToInt(enLoc));
    }

    private MapLocation readLatestEnemyArchonLocation() throws GameActionException {
        int latest_archon_location = rc.readBroadcast(ENEMY_ARCHON_CHANNEL);
        if (latest_archon_location == 0) return null;
        return intToMapLocation(latest_archon_location);
    }

    //==========================================================================
    //<--------------------------- HELPER FUNCTIONS --------------------------->
    //==========================================================================
    protected boolean spawnUnit(RobotType type, Direction dir){
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

    protected Direction randomDirection(){
        //System.out.println(new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f));
        return new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f);
    }

    protected int mapLocationToInt(MapLocation loc){
        return ((int)(loc.x) * 1000 + (int)(loc.y));
    }

    protected MapLocation intToMapLocation(int loc){
        return new MapLocation((float)(loc / 1000), (float)(loc % 1000));
    }

    //To be used for maplocations only
    protected int floatToIntPrecise(float f){
        return (int)(f * 1000000);
    }

    //To be used for maplocations only
    protected float intToFloatPrecise(int query){
        return ((float)query / 1000000);
    }

    static int robotType(RobotType type){
        if (type == null) return 0;
        switch (type){
            case ARCHON:
                return 1;
            case GARDENER:
                return 2;
            case LUMBERJACK:
                return 3;
            case SOLDIER:
                return 4;
            case SCOUT:
                return 5;
            case TANK:
                return 6;
            default:
                return 0;
        }
    }

    static RobotType robotType(int i){
        switch (i){
            case 1:
                return RobotType.ARCHON;
            case 2:
                return RobotType.GARDENER;
            case 3:
                return RobotType.LUMBERJACK;
            case 4:
                return RobotType.SOLDIER;
            case 5:
                return RobotType.SCOUT;
            case 6:
                return RobotType.TANK;
            default:
                return null;
        }
    }

    protected void donate(){
        if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET && rc.getRoundNum() > MID_GAME*2){
            try {
                if (rc.getTeamBullets() >= rc.getVictoryPointCost() * 1000f){
                    rc.donate(rc.getTeamBullets());
                }
                else{
                    float excess = rc.getTeamBullets() - MAX_STOCKPILE_BULLET;
                    rc.donate((int)(excess/rc.getVictoryPointCost())*rc.getVictoryPointCost());
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    //ABS func radians
    protected float absRad(float r){
        if(r>DEG_360) return r-DEG_360;
        else if(r<0) return r+DEG_360;
        else return r;
    }

    //ABS sensor array length
    protected int absIdx(int i, int n){
        if (i>=n) return i-n;
        else if (i<0) return i+n;
        else return i;
    }

}
