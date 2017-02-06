package Scarif_stable;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class BasePlayer {
    static RobotController rc;

    //Debugging
    static boolean debug = false;
    static boolean microTest = false;

    //Precompute Degrees (more for convenience, probably doesn't affect performance significantly)
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

    //Radius allowances (cuz floating point shenanigans)
    static final float EPSILON = 0.1f;

    //Movement finesse
    static final int MOVEMENT_GRANULARITY = 45;      			//Number of possible move directions
    static final float MOVEMENT_SEARCH_ANGLE = DEG_90;          //Angle to search for possible moves
    static final int MOVEMENT_GRANULARITY_MICRO = 8;      		//Number of possible move directions for micro
    static final int SEARCH_GRANULARITY = 24;       			//Number of search directions
    static final int GARDENER_SEARCH_GRANULARITY = 12;          //Gardener's search
    static final int SENSOR_GRANULARITY = 72;                   //How many angles to scan around
    static float MOVEMENT_SCANNING_RADIUS = 1.1f;               //Radius of dot scanned to move in
    static float MOVEMENT_SCANNING_DISTANCE = 2.1f;             //Distance away from center to scan in
    static final float TARGET_RELEASE_DIST = 2f;    			//If target falls within this distance, release from target
    static final float STUCK_THRESHOLD = 0.2f;      			//Threshold to call if unit is stuck
    static final int STUCK_THRESHOLD_TURNS = 17;                //Number of turns spent going back and forth being stuck...
    static final int GARDENER_MOVE_GRANULARITY = 12;			//Number of times gardener will try random directions

    //Game-changing macro controls
    static final int MAX_STOCKPILE_BULLET = 500;    			//Maximum bullets in stockpile before donation occurs
    static final int EARLY_GAME = 124;              			//How early is counted as 'early game'
    static final int MID_GAME = 500;							//When mid-game starts
    static final int LATE_GAME = 1800;							//Late game case
    static int THE_FINAL_PROBLEM = 2998;						//Last ditch effort
    static final float FOREST_DENSITY_THRESHOLD = 0.15f;		//% of area covered by trees in sense range
    static final int SUSTAINABLE_ECONOMY = 20;                  //Minimum number of trees before tank production kicks in
    static final float OFFENSE_FACTOR = 3f; 					//Factor to prioritise offense / defense for scout micro [Higher => more offensive]
    static final int MAX_ENEMY_PINGS = 15;                      //Maximum of queued enemy alerts
    static final int PING_LIFESPAN = 100;                       //How many rounds it takes for enemy ping to 'die'

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

    //Farmer control variables
    static float FARMER_CLEARANCE_RADIUS = 8f;                  //How far apart should farmers be
    static float FARMER_ARCHON_DISTANCE = 5f;		            //Distance to be away from archon before starting farm
    static final int FARMER_MIN_SLOTS = 4;          			//Minimum number of trees that can be built before starting farm [1-6]
    static final int TREE_DELAY = 13;                           //Number of turns before able to build another tree
    static final float PLANT_TREE_CHANCE = 1.00f;               //Chance to actually plant a tree
    static final int MAX_BLACKLISTED_LOCATIONS = 120;           //maximum number of blacklisted locations

    //Early game build order
    static final float INITIAL_BULLETS = 300f;					//Starting number of bullets
    static final int MAX_BUILD_ORDER = 2;						//Reserve how many slots in build queue
    static final int BUILD_ORDER_QUEUE = 100;					//Start of channel for build order

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

    //Early game estimation
    static final float MAP_SMALL = 40f;							//Max size of map considered 'small'
    static final float MAP_LARGE = 73f;							//Max size of map considered 'large'

    //Surroundings sensing
    static TreeInfo[] SURROUNDING_TREES_OWN;
    static TreeInfo[] SURROUNDING_TREES_ENEMY;
    static TreeInfo[] SURROUNDING_TREES_NEUTRAL;
    static RobotInfo[] SURROUNDING_ROBOTS_OWN;
    static RobotInfo[] SURROUNDING_ROBOTS_ENEMY;
    static MapLocation ENEMY_ARCHON_LOCATION;
    static MapLocation MY_LOCATION;
    static final float TEST_ON_MAP_RADIUS = 0.25f;
    static float X_UPPER = 600f;
    static float X_LOWER = 0f;
    static float Y_UPPER = 600f;
    static float Y_LOWER = 0f;

    //Precomputing 6-directional hexagonal angles
    static ArrayList<Direction> HEXAGONAL_PATTERN = new ArrayList<Direction>(Arrays.asList(new Direction(0f), new Direction(DEG_60), new Direction (DEG_60*2), new Direction(DEG_180),
            new Direction(DEG_60*4), new Direction(DEG_60*5)));
    static final Direction[] HEX_DIR = new Direction[]{new Direction(0f), new Direction((float)(Math.PI / 3)), new Direction((float)(2 * Math.PI / 3)), new Direction((float)(Math.PI)),
            new Direction((float)(4 * Math.PI / 3)), new Direction((float)(5 * Math.PI / 3))};

    //Computing sensor angles for pathing
    static float[] SENSOR_ANGLES = new float[SENSOR_GRANULARITY];

    //Robot specific variables
    static MapLocation target;                                  //This is the position the robot will move to
    static boolean inCombat;                                    //Is this robot in combat?

    //Recce map information
    static Broadcaster broadcaster;                             //General purpose enemy messages (coordinating attacks)
    static Broadcaster treeBroadcaster;                         //Sending neutral tree locations (for lumberjacks to quickly clear forests)

    //Going for that economic win
    static boolean imminentEconomicWin = false;

    //Random number generator
    static Random rand;

    //Broadcast channels SOP (Standard Operating Procedure)
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
    static final int ENEMY_ENGAGED = 43;
    static final int ECONOMIC_WIN = 44;
    static final int MAP_SIZE = 45;

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
    static final int COUNT_SETTLED = 310;
    static final int SUM_SETTLED = 311;

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

    static final int CONTROL_FARMER_HEARTBEAT = 796;
    static final int CONTROL_FARMER = 797;
    static final int SETTLED_FARMERS = 798;
    static final int AVAILABLE_FARMING_LOCATIONS = 3799;
    static final int FARMING_LOCATIONS_X = 3800;
    static final int FARMING_LOCATIONS_Y = 3801;

    static final int BLACKLISTED_FARMING_LOCATIONS = 5299;
    static final int BLACKLISTED_LOCATIONS_X = 5300;
    static final int BLACKLISTED_LOCATIONS_Y = 5301;

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

    //==========================================================================
    //<----------------------------- E DOC TABLET ----------------------------->
    //==========================================================================
    /*
    Hi there!
    If you've reached this far into our source code, my heartiest congratulations, we don't write the friendliest of codes >.<
    Anyway, here's a good place to start, BasePlayer contains most of our basic robot functions, before extending out to individual
    robots' roles in their respective classes.

    A clearly-defined project structure would aid rapid development and ease maintenance (especially at 3am)

    We started off writing a single-class robotplayer (from examplefuncsplayer) but realized it was quickly getting out of hand
    having 3k+ lines of source code wasn't easy to maintain/debug
    So after sprint tourney we spent a day migrating over to OOP style splitting up each robot's individual functions into classes
    of their own. However you would find remnants of that previous single-class robotplayer scattered all over our project.

    Many of the //todo tags scattered around the code are last-minute fixes and stuff we haven't implemented yet...

    Next year, we've resolved to start off right with OOP. (and encourage y'all to do so too!)

    Project Structure:
        RobotPlayer                 (Battlecode template requirement .run(rc) function - switches down to individual robot types)

        Broadcaster                 (A persistent serialized 2D grid array to store changing map conditions)
            --> Message             (Encoding/Decoding map information into int for broadcasting to other robots)
            --> CoordsQueue         (custom implementation of queue<int[]>)

        BasePlayer                  (Base class for all robots - common functions, init(), things to do on start()/end() every round etc...)
            --> ArchonPlayer
            --> GardenerPlayer
            --> LumberjackPlayer
            --> SoldierPlayer
            --> TankPlayer
            --> ScoutPlayer         (depreciated - after sprint balancing, scouts just weren't that useful anymore)
            --> ScoutShaker         (our only task for the scout: shaking bullet trees)
    */
    public BasePlayer(RobotController control) throws GameActionException {
        try {
            BasePlayer.rc = control;
            rand = new Random(rc.getID());
            THE_FINAL_PROBLEM = rc.getRoundLimit() - 50;    //As round limits weren't stated to be static, just in case - it never changed from 3000 tho :/
            ENEMY_ARCHON_LOCATION = readLatestEnemyArchonLocation();
            if (ENEMY_ARCHON_LOCATION != null) System.out.println("Enemy archon location: "+mapLocationToInt(ENEMY_ARCHON_LOCATION));
            if (ENEMY_ARCHON_LOCATION == null){
                ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
                System.out.println("Default location: "+mapLocationToInt(ENEMY_ARCHON_LOCATION));
                updateLatestEnemyArchonLocation(ENEMY_ARCHON_LOCATION);
            }
            MY_LOCATION = rc.getLocation();

            //Initializes 2 serialized 2D arrays, one for reporting enemy locations, the other for reporting neutral trees
            broadcaster = new Broadcaster(rc, SPOT_CONTACT_REP);
            treeBroadcaster = new Broadcaster(rc, TREE_REP);

            //Calculate relative robot risks - 1 time initialization pass cuz who knows when the devs would rebalance units again...
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

            //Global Initialization bytecode cost
            System.out.println("Robot overhead cost: " + Integer.toString(Clock.getBytecodeNum()));

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    protected void startRound() throws GameActionException {
        //Always be on the lookout for neutral trees - might come in handy for pathing later on too...
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        ENEMY_ARCHON_LOCATION = readLatestEnemyArchonLocation();
        if (ENEMY_ARCHON_LOCATION == null){
            ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
            updateLatestEnemyArchonLocation(ENEMY_ARCHON_LOCATION);
        }

        //Updates the state of the game
        MY_LOCATION = rc.getLocation();
        broadcaster.update();
        treeBroadcaster.update();

        //Updates map boundaries
        updateBoundaries();

        //Read whether archons have evaluated an imminent economic win - if so, stop unit production
        imminentEconomicWin = (rc.readBroadcast(ECONOMIC_WIN) == 1);

        return;
    }

    protected void endRound() throws GameActionException {
        //Shake nearest tree in vicinity
        if (SURROUNDING_TREES_NEUTRAL.length > 0){
            if (rc.canShake(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.shake(SURROUNDING_TREES_NEUTRAL[0].ID);
        }

        //Reports enemies nearby
        if (SURROUNDING_ROBOTS_ENEMY.length == 0){
            sitRep();
            //If last known enemy archon location is empty, assume archon killed (soldiers stop defaulting to this location)
            if (broadcaster.location_to_channel(MY_LOCATION) == broadcaster.location_to_channel(ENEMY_ARCHON_LOCATION)){
                rc.broadcast(ENEMY_ARCHON_KILLED, 1);
            }
        }

        //Reports nearby neutral trees for lumberjacks to chop down
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
                if (rc.getType() != RobotType.SCOUT && rc.getType() != RobotType.ARCHON) {
                    if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                        rc.broadcast(ENEMY_ENGAGED, 1);
                    }
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

        //scout cheesing code
        //Order construction of shaker scout - If nearby bullets sensed in neutral trees outweigh the cost of a scout
        if (Clock.getBytecodeNum() < BYTE_THRESHOLD + 1000) {
            float numBullets = 0;
            for (TreeInfo tree : SURROUNDING_TREES_NEUTRAL) {
                numBullets += tree.getContainedBullets();
            }
            try {
                int max_bullet = rc.readBroadcast(FORCE_SCOUT_CHEESE);
                rc.broadcast(FORCE_SCOUT_CHEESE, (int) Math.max(max_bullet, numBullets));
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }

        //Dequeue outdated reports
        //Clean up reports (based on latest boundary information
        if (SURROUNDING_ROBOTS_ENEMY.length == 0) {
            int cur_grid = broadcaster.location_to_channel(MY_LOCATION);
            for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
                //Yields turn if too many bytecodes used
                if (Clock.getBytecodeNum() > BYTE_THRESHOLD+2000) continue;
                int gridCoded = rc.readBroadcast(i);
                MapLocation gridLoc = broadcaster.channel_to_location(gridCoded);
                if (gridCoded == 0) continue;
                if (gridCoded == cur_grid){
                    remove_report_from_queue(gridLoc);
                }
                //Only queue valid reports
                else if (!broadcaster.isValidGrid(gridLoc)){
                    remove_report_from_queue(gridLoc);
                }
            }
        }

    }

    //==========================================================================
    //<--------------------------- METRIC FUNCTIONS --------------------------->
    //==========================================================================
    //Metric to test if gardeners are spaced out enough :)
    //RETURNS  false if distance between farmers is < radius
    protected boolean isSpreadOut(MapLocation myLoc, float radius, boolean spreadFarmers){
        RobotInfo[] farmers = rc.senseNearbyRobots(Math.min(rc.getType().sensorRadius, radius), rc.getTeam());
        //Avoid gardeners
        for (RobotInfo f:farmers){
            if (f.type == RobotType.GARDENER && spreadFarmers) return false;
            //todo ignore archon?
            else if (f.type == RobotType.ARCHON && myLoc.distanceTo(f.location) < FARMER_ARCHON_DISTANCE) return false;
        }
        return true;
    }

    //Building slots checking
    //How to check if a position on the map is valid for building (can plant tree)
    private boolean slot_free_metric(MapLocation origin, Direction dir) throws GameActionException {
        //Naive method, whether can plant tree - runs into problems when bullets < 50 or tree planting cooldown timer hasn't expired
        //return rc.canPlantTree(dir);

        //Test whether circle is occupied - runs into problems when it's just a passing robot
        //return (!rc.isCircleOccupied(origin.add(dir,2f),1f));

        //Final rendition, excludes passing robots from evaluation of whether slot is free for building/planting - runs into bytecode issues tho...
        if (!rc.isCircleOccupied(origin.add(dir, 2f), 1f) && rc.onTheMap(origin.add(dir, 2f), 1f)){
    		return true;
		}
		else{
			if (rc.isLocationOccupiedByRobot(origin.add(dir, 2f)) && rc.senseRobotAtLocation(origin.add(dir, 2f)).getType() != RobotType.ARCHON && rc.senseRobotAtLocation(origin.add(dir, 2f)).getType() != RobotType.GARDENER){
				return true;
			}
		}
    	return false;
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
        //Approximate using a square (will incur slightly heavier weighing, but that's acceptable)
        float off_map = 0f;
        //Compute X-axis area off map
        float x_upper_excess = X_UPPER - (center.x + radius);
        float x_lower_excess = X_LOWER - (center.x - radius);
        System.out.println("excess x (left: "+x_upper_excess+"|right: "+x_lower_excess);
        if (x_upper_excess < 0) off_map += -x_upper_excess*radius;
        else x_upper_excess = 0;
        if (x_lower_excess > 0) off_map += x_lower_excess*radius;
        else x_lower_excess = 0;
        //Compute Y-axis area off map
        float y_upper_excess = Y_UPPER - (center.y + radius);
        float y_lower_excess = Y_LOWER - (center.y - radius);
        System.out.println("excess y (top: "+y_upper_excess+"|bottom: "+y_lower_excess);
        if (y_upper_excess < 0) off_map += -y_upper_excess*(radius - Math.max(-x_upper_excess, x_lower_excess));
        if (y_lower_excess > 0) off_map += y_lower_excess*(radius - Math.max(-x_upper_excess, x_lower_excess));
        System.out.println("Off the map areas"+off_map);
        switch (SELECTOR){
            case ROBOTS:
                return off_map + density_robots(center, radius);
            case TREES:
                return off_map + density_trees(center, radius);
            case OBJECTS:
                return off_map + density_robots(center, radius) + density_trees(center, radius);
            default:
                return 1f;
        }
    }

    //Density of all robots
    protected float density_robots(MapLocation center, float radius){
        //RobotInfo[] robots = rc.senseNearbyRobots(center, radius, SELECTOR);
        float robots_area = 0;
        for (RobotInfo bot:SURROUNDING_ROBOTS_ENEMY){
            //calculates partial area inside scan radius
            float r = bot.getRadius();
            float R = radius;
            float d = center.distanceTo(bot.location);
            if (d>r+R) continue;    //Skips if object is out of intersection range
            if (d+r<=R) robots_area +=  r*r*DEG_180;    //If object is fully within radius
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
        for (RobotInfo bot:SURROUNDING_ROBOTS_OWN){
            //calculates partial area inside scan radius
            float r = bot.getRadius();
            float R = radius;
            float d = center.distanceTo(bot.location);
            if (d>r+R) continue;    //Skips if object is out of intersection range
            if (d+r<=R) robots_area +=  r*r*DEG_180;    //If object is fully within radius
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
    private float density_trees(MapLocation center, float radius){
        //TreeInfo[] trees = rc.senseNearbyTrees(center, radius, SELECTOR);
        float tree_area = 0;
        for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
            //calculates partial area inside scan radius
            float r = t.radius;
            float R = radius;
            float d = center.distanceTo(t.location);
            if (d>r+R) continue;    //Skips if object is out of intersection range
            if (d+r<=R) tree_area +=  r*r*DEG_180;  //If object is fully within radius
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
    /*
    Here, an explanation of our pathing algo seems apt...
    It's a combination of 3 different pathing algos:
        --> Examplefuncsplayer      - tryMove() in examplefuncsplayer was pretty effective movement algo when path is relatively clear
        --> Bug Pathing             - No tangent bug, just the naive right/left wall-following bug pathing
                                    - Also, doesn't actually follow obstacles, rather approximates with rc.canMove()
        --> Slug Pathing            - Slug pathing in lecture code applied to bug pathing
                                    - We step down travel distance and run bug pathing algo before selecting the closest direction
                                      to our intended direction of travel.

    So when do we run each of these pathing algos?
        --> Defaults to tryMove
        --> When stuck (hanging around same location for some time)
            --> Runs Slug Pathing until robot is closer to intended target before becoming unstuck and resuming tryMove
    */
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

    //Slug Pathing algo
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
        if (absRad(initDir.radians) - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir + 1, GRANULARITY);

        if (debug) rc.setIndicatorLine(myLoc, myLoc.add(SENSOR_ANGLES[sensorDir], 2f), 0, 255, 0);

        try {
            broadcaster.updateBoundaries(myLoc); //Keeps updating map boundaries as robot travels around the map
            if (!stuck){
                if (tryMove(initDir)) return; //Default navigation (works really well in maps like Magic Woods)
            }
            if (!rc.hasMoved()){ //Starts to bug-path around obstacles
                //Slug Pathing
                Direction tryDir = null;
                Direction bestDir = null;
                float closestAngle = 99999f;
                float distanceMoved = 0f;
                for (int i=0;i<SLUG_GRANULARITY;i++){ //We select the closest direction to intended one taking into account if we take a smaller step
                    tryDir = closest_direction_to_target(GRANULARITY, sensorDir, rc.getType().strideRadius/(float)Math.pow(2,i), rightBot);
                    if (tryDir != null && initDir.radiansBetween(tryDir) < closestAngle){
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

    //Bruteforce pathing
    protected boolean tryMove(Direction moveDir) throws GameActionException {
        if (rc.hasMoved()) return false;
        if (rc.canMove(moveDir)){
            rc.move(moveDir);
            return true;
        }
        //Can turn on the debugging indicators but that took up quite some bytecodes (calculating where to draw)
        //so we turned them off for tournaments
        for (int i=1;i<MOVEMENT_GRANULARITY;i++){
            Direction tryDir = moveDir.rotateRightRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (rc.canMove(tryDir)){
                //if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            //else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
            tryDir = moveDir.rotateLeftRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (rc.canMove(tryDir)){
                //if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            //else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
        }
        return false;
    }

    //==========================================================================
    //<--------------------------- MICRO FUNCTIONS --------------------------->
    //==========================================================================
    //Function to force spawning of robot
    protected boolean forceBuilding(RobotType robot) throws GameActionException {
        if (!rc.isBuildReady()) return false;
        if (imminentEconomicWin) return false;
        Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION);
        if (debug) rc.setIndicatorLine(MY_LOCATION, ENEMY_ARCHON_LOCATION, 255, 255, 0);
        Direction tryDir = null;
        for (int i=0;i<SEARCH_GRANULARITY/2;i++){
            tryDir = initDir.rotateRightRads(i*DEG_360/SEARCH_GRANULARITY);
            if (rc.canBuildRobot(robot,tryDir)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.buildRobot(robot,tryDir);
                return true;
            }
            else{
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
            }
            tryDir = initDir.rotateLeftRads(i*DEG_360/SEARCH_GRANULARITY);
            if (rc.canBuildRobot(robot,tryDir)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.buildRobot(robot,tryDir);
                return true;
            }
            else{
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
            }
        }
        return false;
    }

    //Rotates a template mask of hexagonal slots around the robot to find optimal offset (most slots given applied offset)
    //Only returns if free slots found is > MIN_SLOTS
    protected ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();
        int maxFreeSlots = 0;
        int curFreeSlots = 0;
        float maxFreeOffset = 0;
        float curOffset = 0;
        for (int i=0;i<GARDENER_SEARCH_GRANULARITY;i++) {
            if (maxFreeSlots > 1 && Clock.getBytecodeNum() > BYTE_THRESHOLD - 5000) continue; //Escapes with best offset thus far if using too many bytes
            curFreeSlots = 0;
            curOffset = i * DEG_60 / GARDENER_SEARCH_GRANULARITY;
            for (Direction dir : HEXAGONAL_PATTERN) {
                if (slot_free_metric(origin, dir.rotateRightRads(curOffset))) {
                    curFreeSlots++;
                }
            }
            if (curFreeSlots > maxFreeSlots) {
                maxFreeSlots = curFreeSlots;
                maxFreeOffset = curOffset;
            }
            if (maxFreeSlots >= MIN_SLOTS) {
                for (Direction dir : HEXAGONAL_PATTERN) {
                    available_slots.add(dir.rotateRightRads(maxFreeOffset));
                    return available_slots;
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
            //todo bytecode limit
            if (Clock.getBytecodeNum() > BYTE_THRESHOLD) return spawn_direction;
            System.out.println("Direction tested: "+test_dir.get(i));
            float eval = getDensity(MY_LOCATION.add(test_dir.get(i),rc.getType().bodyRadius + 1f + EPSILON),rc.getType().sensorRadius-(rc.getType().bodyRadius + 1f + EPSILON), TREES);
            if (!rc.onTheMap(MY_LOCATION.add(test_dir.get(i),rc.getType().bodyRadius + 3f + EPSILON), TEST_ON_MAP_RADIUS)) continue;
            if(eval<density){
                density = eval;
                spawn_direction = test_dir.get(i);
            }
            System.out.println(eval);
        }
        return spawn_direction;
    }

    //==========================================================================
    //<------------------------- COLLISION FUNCTIONS -------------------------->
    //==========================================================================
    //Collision checking between bullet and other robots
    protected boolean willCollideWithRobot(Direction bulletDir, MapLocation bulletLoc, MapLocation robotLoc, float robotBodyRadius) {
        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLoc.directionTo(robotLoc);
        float distToRobot = bulletLoc.distanceTo(robotLoc);
        float theta = bulletDir.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= robotBodyRadius);
    }

    //Collision checking between bullet and self
    protected boolean willCollideWithMe(MapLocation newLoc, BulletInfo bullet) {
        System.out.println("Collision function: "+Integer.toString(Clock.getBytecodeNum()));

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        //If already collided
        if (bulletLocation.distanceTo(newLoc) < rc.getType().bodyRadius) return true;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(newLoc);
        float distToRobot = bulletLocation.distanceTo(newLoc);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    //Collision checking taking into account whether bullet will actually hit next turn
    protected boolean willCollideWithMe(MapLocation newLoc, BulletInfo bullet, float multiple) {
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

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)
        float travelDist = (float)(distToRobot * Math.cos(theta));

        return (perpendicularDist <= rc.getType().bodyRadius && travelDist <= /*3 * */multiple * bullet.getSpeed());
    }

    //Collision checking taking into account whether bullet will actually hit next turn
    protected boolean willCollideWithRobot(Direction bulletDir, MapLocation bulletLoc, MapLocation robotLoc, float robotBodyRadius, float multiple) {

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

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)
        float travelDist = (float)(distToRobot * Math.cos(theta));

        return (perpendicularDist <= robotBodyRadius && travelDist <= /*3 * */multiple * rc.getType().bulletSpeed);
    }

    //Collision checking between bullet and trees
    protected boolean willCollideWithTree(Direction targetDir, TreeInfo[] surrounding_trees) {

        for (TreeInfo t:surrounding_trees) {
            //Snap out if bytecode limit exceeded
            if (Clock.getBytecodeNum() > BYTE_THRESHOLD) return false;

            // Calculate direction towards tree
            Direction directionToTree = MY_LOCATION.directionTo(t.location);
            float distanceToTree = MY_LOCATION.distanceTo(t.location);
            float theta = targetDir.radiansBetween(directionToTree);

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

    //Updates finely the map boundaries
    protected void updateBoundaries() throws GameActionException {
        float new_x_upper = -1f;
        float new_x_lower = -1f;
        float new_y_lower = -1f;
        float new_y_upper = -1f;
        float cur_x_upper = intToFloatPrecise(rc.readBroadcast(BORDER_RIGHT));
        float cur_x_lower = intToFloatPrecise(rc.readBroadcast(BORDER_LEFT));
        float cur_y_upper = intToFloatPrecise(rc.readBroadcast(BORDER_TOP));
        float cur_y_lower = intToFloatPrecise(rc.readBroadcast(BORDER_BOTTOM));
        X_UPPER = (cur_x_upper!=0?cur_x_upper:X_UPPER);
        X_LOWER = (cur_x_lower!=0?cur_x_lower:X_LOWER);
        Y_UPPER = (cur_y_upper!=0?cur_y_upper:Y_UPPER);
        Y_LOWER = (cur_y_lower!=0?cur_y_lower:Y_LOWER);
        System.out.println("X_lower: "+X_LOWER);
        System.out.println("X_upper: "+X_UPPER);
        System.out.println("Y_lower: "+Y_LOWER);
        System.out.println("Y_upper: "+Y_UPPER);
        //Test the 4 directions for map edges
        //RIGHT
        Direction testDir = new Direction(0f);
        if (!rc.onTheMap(MY_LOCATION.add(testDir,rc.getType().bodyRadius+2f),0.1f)){
            new_x_upper = MY_LOCATION.x+rc.getType().bodyRadius+2f-0.1f;
        }
        //DOWN
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(MY_LOCATION.add(testDir,rc.getType().bodyRadius+2f),0.1f)){
            new_y_lower = MY_LOCATION.y-rc.getType().bodyRadius-2f+0.1f;
        }
        //LEFT
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(MY_LOCATION.add(testDir,rc.getType().bodyRadius+2f),0.1f)){
            new_x_lower = MY_LOCATION.x-rc.getType().bodyRadius-2f+0.1f;
        }
        //UP
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(MY_LOCATION.add(testDir,rc.getType().bodyRadius+2f),0.1f)){
            new_y_upper = MY_LOCATION.y+rc.getType().bodyRadius+2f-0.1f;
        }

        //Update values
        if (new_x_upper != -1f && new_x_upper < X_UPPER){
            rc.broadcast(BORDER_RIGHT, floatToIntPrecise(new_x_upper));
            rc.setIndicatorDot(MY_LOCATION, 0, 0, 0);
            X_UPPER = new_x_upper;
        }
        if (new_x_lower != -1f && new_x_lower > X_LOWER){
            rc.broadcast(BORDER_LEFT, floatToIntPrecise(new_x_lower));
            rc.setIndicatorDot(MY_LOCATION, 0, 0, 0);
            X_LOWER = new_x_lower;
        }
        if (new_y_upper != -1f && new_y_upper < Y_UPPER){
            rc.broadcast(BORDER_TOP, floatToIntPrecise(new_y_upper));
            rc.setIndicatorDot(MY_LOCATION, 0, 0, 0);
            Y_UPPER = new_y_upper;
        }
        if (new_y_lower != -1f && new_y_lower > Y_LOWER){
            rc.broadcast(BORDER_BOTTOM, floatToIntPrecise(new_y_lower));
            rc.setIndicatorDot(MY_LOCATION, 0, 0, 0);
            Y_LOWER = new_y_lower;
        }
        return;
    }

    //Updates most recently spotted enemy archon location
    private void updateLatestEnemyArchonLocation(MapLocation enLoc) throws GameActionException {
        rc.broadcast(ENEMY_ARCHON_CHANNEL, mapLocationToInt(enLoc));
    }

    //Reads off most recently spotted enemy archon location
    private MapLocation readLatestEnemyArchonLocation() throws GameActionException {
        int latest_archon_location = rc.readBroadcast(ENEMY_ARCHON_CHANNEL);
        if (latest_archon_location == 0) return null;
        return intToMapLocation(latest_archon_location);
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
            if (max_units != 0) {
                if (num_tanks == max_units) {
                    most_frequent_unit = RobotType.TANK;
                }
                else if (num_soldiers == max_units) {
                    most_frequent_unit = RobotType.SOLDIER;
                }
                else if (num_lumberjacks == max_units) {
                    most_frequent_unit = RobotType.LUMBERJACK;
                }
                else if (num_gardeners == max_units) {
                    most_frequent_unit = RobotType.GARDENER;
                }
                else if (num_scouts == max_units) {
                    most_frequent_unit = RobotType.SCOUT;
                }
            }
            else most_frequent_unit = RobotType.ARCHON; //Only archon(s) spotted
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

    /*
    Black magics behind our bot's efficient dispersed attacking/scouting system (engaging on multiple fronts/spreading out to scout)
    Functions to interface with Broadcaster objects (2D array)
    add_report_to_queue         :   Adds a report of interest into globally-shared array
    remove_report_from_queue    :   As it says...
    treeRep                     :   Tree Report --> Reports neutral tree locations to global array (for lumberjacks to acquire target)
    sitRep                      :   Situation Report --> Simply pings that no enemies are around
    spotRep                     :   Spotted Enemy Report --> Sends a message to be stored into global array of enemy information
    contactRep                  :   Contacted Enemy Report --> spotRep, but for gardeners (heavier weightage for soldiers to defend)
    targetRep                   :   Target of Opportunity Report --> We wanted to implement tanks as artillery units, alas, no time...
    */
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
        int[] gridData = broadcaster.readGrid(MY_LOCATION);
        if (gridData[1] == 0 && gridData[0] == 0) {
            broadcaster.updateGrid(MY_LOCATION, rc.getRoundNum(), 0);
        }
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
        //todo furthest gives null in certain situations?? o.O
        MapLocation reportLoc = SURROUNDING_ROBOTS_ENEMY[SURROUNDING_ROBOTS_ENEMY.length-1].location;
        if (!broadcaster.isValidGrid(reportLoc)){
            reportLoc = broadcaster.nearestValidGrid(reportLoc);
        }
        if (reportLoc == null) return;
        System.out.println("Reporting enemy at location: "+reportLoc);
        /*if (!broadcaster.isValidGrid(reportLoc)){
            reportLoc = broadcaster.nearestValidGrid(reportLoc);
        }*/
        int archon_present = enemyData[2];
        RobotType most_prevalent_enemy = robotType(enemyData[0]);
        int num_prevalent_enemey = enemyData[1];
        if (num_prevalent_enemey > 9) num_prevalent_enemey = 9;
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
    protected void contactRep() throws GameActionException {
        int[] enemyData = profileSurroundingEnemies(SURROUNDING_ROBOTS_ENEMY);
        if (enemyData == null) return;
        System.out.println("Valid CONTACTREP sent, number of enemies: "+Integer.toString(SURROUNDING_ROBOTS_ENEMY.length));
        //Take the grid to be current location (sends troops here)
        MapLocation reportLoc = MY_LOCATION;
        if (!broadcaster.isValidGrid(reportLoc)){
            reportLoc = broadcaster.nearestValidGrid(reportLoc);
        }
        if (reportLoc == null) return;
        System.out.println("Reporting enemy at location: "+reportLoc);
        int archon_present = enemyData[2];
        RobotType most_prevalent_enemy = robotType(enemyData[0]);
        int num_prevalent_enemey = enemyData[1];
        if (num_prevalent_enemey > 9) num_prevalent_enemey = 9;
        Message contactRep = new Message();
        //Enemy type
        contactRep.writeEnemy(most_prevalent_enemy);
        //Enemy number
        contactRep.writeNumberOfEnemies(num_prevalent_enemey);
        //Alert Level
        contactRep.writeAlertLevel(assessAlert(enemyData));
        //Archon presence
        contactRep.writeArchonPresent(archon_present);
        //Message type
        contactRep.writeMessageType(Message.CONTACT_REP);

        //Update grid with spotRep
        broadcaster.updateGrid(reportLoc, rc.getRoundNum(), contactRep.getRawMsg());

        //Add report to queue of alerts
        add_report_to_queue(reportLoc, contactRep.getRawMsg());
    }

    //Alerts to target of opportunity
    protected void targetRep(){

    }

    //==========================================================================
    //<--------------------------- HELPER FUNCTIONS --------------------------->
    //==========================================================================
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

    //todo win by farming (when one has lots of trees and a standing army)
    protected void donate(){
        if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET && rc.getRoundNum() > MID_GAME){
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

    //ABS sensor array length - Cyclic array indices
    protected int absIdx(int i, int n){
        if (i>=n) return i-n;
        else if (i<0) return i+n;
        else return i;
    }

}
