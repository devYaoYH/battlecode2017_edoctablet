package tatooine;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class BasePlayer {
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
    static final float EPSILON = 0.1f;

    //Constant variables
    static final float epsilon = 1e-08f;
    static final int VISITED_ARR_SIZE = 10;         			//Number of previous locations recorded

    //Early game estimation heuristics
    static final float MAP_SMALL = 40f;							//Max size of map considered 'small'
    static final float MAP_LARGE = 73f;							//Max size of map considered 'large'
    static final float INITIAL_BULLETS = 300f;					//Starting number of bullets

    //Movement finesse
    static final int MOVEMENT_GRANULARITY = 12;      			//Number of possible move directions
    static final float MOVEMENT_SEARCH_ANGLE = DEG_30;          //Angle to search for possible moves
    static final int MOVEMENT_GRANULARITY_MICRO = 8;      		//Number of possible move directions for micro
    static final int BASHING_GRANULARITY = 15;                  //Number of angles around the targeted direction to force first before wall-following
    static final int SEARCH_GRANULARITY = 24;       			//Number of search directions
    static final int SENSOR_GRANULARITY = 72;                   //How many angles to scan around
    static final int SOLDIER_CHASING_GRANULARITY = 24;          //Lessen bytecode usage when chasing enemies...
    static final float SENSOR_SMOOTHENING_THRESHOLD = 0.001f;   //Smoothens 'bumps' in the sensor data
    static float MOVEMENT_SCANNING_RADIUS = 1.1f;               //Radius of dot scanned to move in
    static float MOVEMENT_SCANNING_DISTANCE = 2.1f;             //Distance away from center to scan in
    static final float TARGET_RELEASE_DIST = 9f;    			//This is a square distance. Assume you have reached your destination if your target falls within this distance
    static final float STUCK_THRESHOLD = 0.2f;      			//Threshold to call if unit is stuck
    static final int STUCK_THRESHOLD_TURNS = 9;                 //Number of turns spent going back and forth being stuck...
    static final int GARDENER_MOVE_GRANULARITY = 12;			//Number of times gardener will try random directions

    //Game-changing macro controls
    static final int MAX_STOCKPILE_BULLET = 500;    			//Maximum bullets in stockpile before donation occurs
    static final int DONATION_AMT = 50;             			//Amount donated each time
    static final int GAME_INITIALIZE_ROUND = 2;					//How many rounds before game is initialized
    static final int EARLY_GAME = 124;              			//How early is counted as 'early game'
    static final int MID_GAME = 500;							//When mid-game starts
    static final int LATE_GAME = 1800;							//Late game case
    static int THE_FINAL_PROBLEM = 2998;						//Last ditch effort
    static final float FOREST_DENSITY_THRESHOLD = 0.05f;		//% of area covered by trees in sense range
    static final float OFFENSE_FACTOR = 3f; 					//Factor to prioritise offense / defense for scout micro [Higher => more offensive]
    static final int SOLDIER_ALARM_RELEVANCE = 5;   			//Relevance of a soldier alarm

    //Farmer control variables
    static float FARMER_ARCHON_DISTANCE = 5f;		            //Distance to be away from archon before starting farm
    static final int FARMER_MIN_SLOTS = 3;          			//Minimum number of trees that can be built before starting farm [1-6]
    static final int TREE_DELAY = 25;                           //Number of turns before able to build another tree
    static final float PLANT_TREE_CHANCE = 1.00f;               //Chance to actually plant a tree

    //Early game build order
    static final int MAX_BUILD_ORDER = 3;						//Reserve how many slots in build queue
    static final int BUILD_ORDER_QUEUE = 100;					//Start of channel for build order

    //Micro constants
    static final int BYTE_THRESHOLD = 9500;                     //Max number of bytecodes to be used...
    static final float SOLDIER_MICRO_DIST = 33f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float ARCHON_MICRO_DIST = 100.1f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float SCOUT_MICRO_DIST = 64.3f;				//Squared dist. Distance nearest enemy has to be to engage micro code
    static final float LUMBERJACK_AVOIDANCE_RANGE = 4f; 		//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_ARCHON = 8f; 	//How far to avoid lumberjacks
    static final float LUMBERJACK_AVOIDANCE_RANGE_SCOUT = 4f;	//How far to avoid lumberjacks
    static final float PENTAD_EFFECTIVE_DISTANCE = 7.596f;		//(1/tan(7.5deg)) Such that at least 2 shots will hit a target with radius 1
    static final float BULLET_SENSE_RANGE = 5f; 				//The range at which bullets are sensed
    static final float SOLDIER_COMBAT_SENSE_RADIUS = 5f;        //Distance to sense friendly units at (soldier)
    static final float SCOUT_COMBAT_SENSE_RADIUS = 5f;          //Distance to sense friendly units at (scout)
    static final float POINT_BLANK_RANGE = 0.15f; //Distance to just shoot an enemy even if friendly fire will happen

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

    static ArrayList<Direction> HEXAGONAL_PATTERN = new ArrayList<Direction>(Arrays.asList(new Direction(0f), new Direction(DEG_60), new Direction (DEG_60*2), new Direction(DEG_180),
            new Direction(DEG_60*4), new Direction(DEG_60*5)));
    static float[] SENSOR_ANGLES = new float[SENSOR_GRANULARITY];
    static float[] LIGHTWEIGHT_SENSOR_ANGLES = new float[SOLDIER_CHASING_GRANULARITY];

    //Robot specific variables
    static boolean isSet;                           //Whether important local variables are set
    static int timeLived;                           //Time since spawn
    static int patience;                            //For movement
    static ArrayList<MapLocation> visitedArr;       //For movement
    static MapLocation target;                      //This is the position the robot will move to
    static boolean inCombat;                        //Is this robot in combat?
    static int ROBOT_SPAWN_ROUND = 0;

    //Broadcast channel statics
    static final int BORDER_LEFT = 0;
    static final int BORDER_TOP = 1;
    static final int BORDER_RIGHT = 2;
    static final int BORDER_BOTTOM = 3;
    static final int SPOTTED_TURN = 10;
    static final int SPOTTED_LOCATION = 11;
    static final int SPOTTED_ENEMY_TYPE = 12;

    static final int CONTROL_ARCHON = 20;
    static final int CONTROL_ARCHON_HEARTBEAT = 21;

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
	 * [700]
	 *
	 * Reserved Channels for future use
	 * [800-999]
     *
     */

    /*
     * General TODO:
     * 1. Find a way to determine map density then choose the appropriate bot
     * 2. Improve scout micro to scout out broadcasting enemy robots (hunter killer behavior)
     * 3. OOP the code structure :D
     */

    public BasePlayer(RobotController control) throws GameActionException {
        BasePlayer.rc = control;
        ROBOT_SPAWN_ROUND = rc.getRoundNum();
        THE_FINAL_PROBLEM = rc.getRoundLimit()-3;
        ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
        MY_LOCATION = rc.getLocation();

        //Set up sensor angle array
        for (int i=0;i<SENSOR_GRANULARITY;i++){
            SENSOR_ANGLES[i] = i*(DEG_360/SENSOR_GRANULARITY);
        }
        for (int i=0;i<SOLDIER_CHASING_GRANULARITY;i++){
            LIGHTWEIGHT_SENSOR_ANGLES[i] = i*(DEG_360/SOLDIER_CHASING_GRANULARITY);
        }
        //Global Initialization
        System.out.println("Robot overhead cost: "+Integer.toString(Clock.getBytecodeNum()));
    }

    protected void startRound() throws GameActionException {
        //Always be on the lookout for neutral trees - might come in handy for pathing later on too...
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(3f, Team.NEUTRAL);
        MY_LOCATION = rc.getLocation();

        if (rc.getRoundNum() < EARLY_GAME) {
            for (RobotInfo r : SURROUNDING_ROBOTS_ENEMY) {
                if (r.type == RobotType.SCOUT){
                    System.out.println("I have detected a scout rushing us!!");
                    rc.broadcast(SCOUT_RUSH_DETECTED, 1);
                }
            }
        }
        return;
    }

    protected void endRound() throws GameActionException {
        //Shake trees in vicinity
        if (SURROUNDING_TREES_NEUTRAL.length > 0){
            if (rc.canShake(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.shake(SURROUNDING_TREES_NEUTRAL[0].ID);
        }

        //Donate
        donate();

        //Final few rounds, cash everything in!!!
        if (rc.getRoundNum() > THE_FINAL_PROBLEM){
            rc.donate((float)Math.floor(rc.getTeamBullets()/10)*10);
        }
    }

    //==========================================================================
    //<--------------------------- METRIC FUNCTIONS --------------------------->
    //==========================================================================
    //Metric to test if gardeners are spaced out enough :)
    //RETURNS  false if distance between farmers is < radius
    protected boolean isSpreadOut(MapLocation myLoc, float radius){
        RobotInfo[] farmers = rc.senseNearbyRobots(radius, rc.getTeam());
        float distance_to_archon = 9999f;

        //Avoid gardeners/archons
        for (RobotInfo f:farmers) if (f.type == RobotType.GARDENER || f.type == RobotType.ARCHON) return false;

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
    //<--------------------------- MICRO FUNCTIONS --------------------------->
    //==========================================================================
    //<-------------- THE ALMIGHTY PATHFINDING THINGUMMYWHAT ----------------->
    //Bug Pathing algo that reverses direction when unit is stuck
    protected void moveToTargetExperimental(MapLocation myLoc, int GRANULARITY, boolean rightBot, boolean stuck){
        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<GRANULARITY;i++){
            if (initDir.radians > SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (initDir.radians - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir++, GRANULARITY);

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
            if (rc.canMove(initDir)){
                rc.move(initDir);
                return;
            }
            else {
                if (rc.canSenseAllOfCircle(myLoc.add(initDir), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(initDir, rc.getType().bodyRadius)))
                    updateBorders(myLoc);
                //Try jiggling about the direction of movement
                if (!stuck) {
                    for (int i = 1; i < BASHING_GRANULARITY; i++) {
                        Direction tryDir = new Direction(absRad(initDir.radians + DEG_1 * i));
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                        tryDir = new Direction(absRad(initDir.radians - DEG_1 * i));
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }

                //Uncomplicated bug pathing code
                if (rightBot) {
                    for (int i = 1; i < GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir + i, GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                else {
                    for (int i = 1; i < GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir - i, GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
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
                rc.move(tryDir);
                return true;
            }
            tryDir = moveDir.rotateLeftRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (rc.canMove(tryDir)){
                rc.move(tryDir);
                return true;
            }
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

    //Better checking build slots for farmers functions :D
    protected ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
        ArrayList<Direction> available_slots = new ArrayList<Direction>();
        //Scan in a hexagonal pattern first
        for (int i=0;i<20;i++){
            if (available_slots.size() > MIN_SLOTS) return available_slots;
            else available_slots.clear();
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

    //Test whether grid search location has been reached
    protected boolean hasReachedGrid(MapLocation target_grid){
        return MY_LOCATION.distanceTo(target_grid) < TARGET_RELEASE_DIST;
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

    //==========================================================================
    //<------------------------ MAP UPDATE FUNCTIONS -------------------------->
    //==========================================================================
    protected void updateBorders(MapLocation myLoc){
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

    //Generate a new maplocation which is not yet scouted
    protected MapLocation newScoutingLocation(int max_attempts){
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

    protected void spotEnemy(RobotInfo enemy){
        if (enemy.getType() == RobotType.ARCHON){
            return;
        }
        try{
            rc.broadcast(SPOTTED_TURN, rc.getRoundNum());
            rc.broadcast(SPOTTED_LOCATION, mapLocationToInt(enemy.location));
            rc.broadcast(SPOTTED_ENEMY_TYPE, robotType(enemy.type));
        }
        catch (GameActionException e){
            e.printStackTrace();
        }
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

    static int robotType(RobotType type){
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
        if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET){
            try {
                //rc.donate((float)(Math.floor((rc.getTeamBullets() - MAX_STOCKPILE_BULLET) / 10.0) * 10.0));
                rc.donate(DONATION_AMT);
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
