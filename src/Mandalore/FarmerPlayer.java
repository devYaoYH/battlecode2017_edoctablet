package Mandalore;

import battlecode.common.*;
import org.omg.CORBA.INTERNAL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class FarmerPlayer extends BasePlayer {

    //Gardener specific variables
    static float SIGHT_RADIUS = 7f;
    private int treeTimer = 0;
    private int lumberjackTimer = 0;
    private int soldierTimer = 0;
    private int scoutTimer = 0;
    private int tankTimer = 0;
    static float FARMER_CLEARANCE_RADIUS = 7f;      //How far apart should farmers be
    static float TREE_SCAN_RADIUS = 1.1f;           //How large a circle to scan for trees
    static float TREE_CLEARANCE_DISTANCE = 2.5f;	//Distance of gap between trees (>2f)
    static final float TREE_RADIUS = 1f;            //Radius of player trees
    private RobotType[] build_order;
    private boolean build_queue_empty = true;
    private boolean has_built_robot = false;

    private boolean CHEESE_SCOUT = true;

    private MapLocation SPAWNING_ARCHON_LOCATION = null;
    private float ATTRACTION_RADIUS = 11f;
    private MapLocation LAST_KNOWN_GARDENER_LOCATION = null;
    private boolean settledDown = false;

    private Random rand;

    static int number_gardeners = 0;
    static int number_lumberjacks = 0;
    private int surrounding_lumberjacks = 0;
    static boolean spawnedLumberjack = false;
    static int number_scouts = 0;
    static int number_soldiers = 0;
    static int number_tanks = 0;

    //Movement algo
    static MapLocation prevLocation = null;                     //Previous location
    static int staying_conut = 0;                               //How many turns spent around there
    //static float movement_stuck_radius = RobotType.SOLDIER.strideRadius - EPSILON;
    //todo n*(0.95/4)*0.5 = movement stuck radius
    static int stuckRounds = 17;                                //How many rounds before I'm stuck
    static int cooldownRounds = 25;
    static int cooldownTimer = 0;                              //Turns to run slug path before reverting to direct pathing
    static float movement_stuck_radius = RobotType.GARDENER.strideRadius/8*stuckRounds;       //Stuck distance for 17 rounds
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean cooldown_active = false;                     //Cooldown period where direct pathing is disabled
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

    private int SLUG = 3;                                       //How many levels of slugging

    //Settling algo
    static HashSet<MapLocation> localBlacklist;
    static final int LOCATION_TIMEOUT = 25;                     //Timeout to get to a new location
    static int settleTimer;
    static final Direction[] TREE_DIR = new Direction[]{new Direction(0f), new Direction((float)(Math.PI / 3)), new Direction((float)(2 * Math.PI / 3)), new Direction((float)(Math.PI)),
            new Direction((float)(4 * Math.PI / 3)), new Direction((float)(5 * Math.PI / 3))};
    static final int MAX_READ_SETTLE_PTS = 50;                  //Maximum number of settle pts read
    static final int MAX_GARDENER_TREES = 6;                    //Maximum number of trees a gardener can spawn
    static final int MIN_ACCEPTABLE_TREES = 4;                  //Min number of trees that gardener can plant
    static float MIN_GARDENER_SEPARATION = 5f;                  //Minimum distance between 2 gardeners
    static float GARDENER_SEPARATION_DIST = 8.5f;               //Separation distance between gardeners
    static float MAX_DISTANCE_TO_TARGET = 14f;                  //Maximum distance to target

    //Spawning constants (Macro control)
    private Direction SPAWN_DIR = null;                         //Reserved spawning slot once in position
    private float BULLET_OVERFLOW = 347;                        //Overflowing bullets, build something!
    static final int SOLDIER_DELAY = 10;          				//Number of turns to spawn a new soldier
    private float SPAWN_SOLDIER_CHANCE = 0.95f;
    private float SOLDIER_TO_TREE_RATIO = 1.23f;                //Max number of soldiers per tree
    static int MIN_SOLDIER = 5;                                 //Minimum number of soldiers
    static final int SCOUT_DELAY = 100;   						//Number of turns to spawn a new scout
    private float SPAWN_SCOUT_CHANCE = 0.0f;
    private float SCOUT_TO_TREE_RATIO = 0.25f;                  //Max number of scouts per tree
    static final int TANK_DELAY = 5; 							//Number of turns to spawn a new tank
    private float SPAWN_TANK_CHANCE = 1f;
    private float TANK_TO_TREE_RATIO = 0.25f;                   //Max number of tanks per tree
    private float TANK_DENSITY_THRESHOLD = 0.15f;               //Surrounding density < this before tanks can be spawned
    private float LUMBERJACK_TO_NEUTRAL_RATIO = 0.43f;          //Number of lumbers per tree surrounding gardener
    private final int LUMBERJACK_DELAY = 30;                    //Number of turns to spawn a new lumberjack
    private float NEUTRAL_TREE_HEALTH_THRESHOLD = 750f;         //Total accumulative health of surrounding trees | above which will spawn lumberjacks
    static final int GARDENER_STUCK_TIMER = 25;					//After n turn, just spawn a lumberjack already!

    private Direction moveDir = randomDirection();
    static ArrayList<Direction> current_available_slots = new ArrayList<>();

    public FarmerPlayer(RobotController rc) throws GameActionException {
        super(rc);
        try {
            initPlayer();
        } catch(Exception e){
            e.printStackTrace();
        }
        for (; ; Clock.yield()) {
            try {
                startRound();
                run();
                endRound();
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private void initPlayer() throws GameActionException {
        MOVEMENT_SCANNING_DISTANCE = 3.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
        SPHERE_REPULSION = 4.04f;
        SPHERE_ATTRACTION = 6.7f;
        rand = new Random(rc.getID());

        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(3f, rc.getTeam());
        for (RobotInfo r:SURROUNDING_ROBOTS_OWN){
            if (r.type == RobotType.ARCHON){
                SPAWNING_ARCHON_LOCATION = r.location;
                break;
            }
        }

        localBlacklist = new HashSet<MapLocation>(1000);
        settleTimer = 100000;
        //Get spawn direction

        float density = 999999f;
        int spawn_direction = -1;
        for (int i = 0; i < MAX_GARDENER_TREES; i++) {
            if (!rc.isCircleOccupied(MY_LOCATION.add(TREE_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS)) {
                System.out.println("Available build slot at: " + Float.toString(TREE_DIR[i].radians));
                float eval = getDensity(MY_LOCATION.add(TREE_DIR[i], MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, TREES);
                if (eval < density) {
                    density = eval;
                    spawn_direction = i;
                }
            }
        }
        if (spawn_direction == -1) {
            spawn_direction = 0;
        }
        SPAWN_DIR = TREE_DIR[spawn_direction];

        //todo I'm debugging this...
        debug = true;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(-1, rc.getTeam());
        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
        rc.broadcast(SUM_GARDENERS,rc.readBroadcast(SUM_GARDENERS)+1);
        build_order = getBuildOrder();
        build_queue_empty = true;
        has_built_robot = false;
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            spotRep();
        }
        number_gardeners = rc.readBroadcast(COUNT_GARDENERS);
        number_lumberjacks = rc.readBroadcast(COUNT_LUMBERJACKS);
        number_scouts = rc.readBroadcast(COUNT_SCOUTS);
        number_soldiers = rc.readBroadcast(COUNT_SOLDIERS);
        number_tanks = rc.readBroadcast(COUNT_TANKS);

        surrounding_lumberjacks = 0;
        for (RobotInfo r:SURROUNDING_ROBOTS_OWN){
            if (r.type == RobotType.LUMBERJACK) surrounding_lumberjacks++;
        }

        //todo check for last known gardener position (sticks close together :D)
        for (RobotInfo r:SURROUNDING_ROBOTS_OWN){
            if (r.type == RobotType.GARDENER){
                LAST_KNOWN_GARDENER_LOCATION = r.location;
                break;
            }
        }

        if (LAST_KNOWN_GARDENER_LOCATION != null) if (debug) rc.setIndicatorLine(MY_LOCATION, LAST_KNOWN_GARDENER_LOCATION, 0, 255, 0);

    }

    private void run() throws GameActionException {

        //Follow build order
        for (int i=0;i<MAX_BUILD_ORDER;i++){
            if (build_order[i]!=null){
                build_queue_empty = false;
                if (forceBuilding(build_order[i])){
                    rc.broadcast(BUILD_ORDER_QUEUE+i, 0);
                }
                break;
            }
        }

        //todo cheese dat scout out
        if (CHEESE_SCOUT && build_order[0] == null && build_order[1] == null){
            if (shouldCheeseScout() || rc.readBroadcast(FORCE_SCOUT_CHEESE) > 80) {
                CHEESE_SCOUT = (rc.readBroadcast(SCOUT_CHEESE_SIGNAL) == 0);
                if (CHEESE_SCOUT){
                    if(forceBuilding(RobotType.SCOUT)){
                        System.out.println("Scout cheeser sent!");
                        rc.broadcast(SCOUT_CHEESE_SIGNAL, 1);
                    }
                }
            }
            else CHEESE_SCOUT = false;
        }

        //Always try to force out a tank :)
        //Only if we've a force of soldiers tho || or a good economy
        //if (!has_built_robot && rc.isBuildReady() && (number_soldiers > MIN_SOLDIER || rc.getTreeCount() > SUSTAINABLE_ECONOMY) && getDensity(MY_LOCATION, SENSE_SURROUNDING_DENSITY_RADIUS, TREES) < TANK_DENSITY_THRESHOLD) forceBuilding(RobotType.TANK);

        //Plant trees
        //Debug settling down
        System.out.println("Have I settled? "+Boolean.toString(settledDown));
        System.out.println("Max slots around me: "+Integer.toString(Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1)));

        if (!settledDown){
            //Check validity of target
            if (target != null) {
                if (debug) rc.setIndicatorDot(target, 0, 0, 255);
                try {
                    //If unable to get to target in 20 turns => local blacklist then choose new location
                    if (rc.getRoundNum() >= settleTimer) {
                        settleTimer = 1000000;
                        localBlacklist.add(target);
                        target = null;
                    }
                    if (target != null && rc.canSenseAllOfCircle(target, rc.getType().bodyRadius)) {
                        //If target is not on the map
                        if (!rc.onTheMap(target, rc.getType().bodyRadius)) {
                            //Add this location to blacklist
                            int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                            rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(target.x));
                            rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(target.y));
                            num_blacklisted += 1;
                            rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 0, 0);
                            target = null;
                        }
                        //Occupied by gardener
                        if (target != null && rc.isLocationOccupiedByRobot(target)) {
                            RobotInfo robot = rc.senseRobotAtLocation(target);
                            if (robot.getType() == RobotType.GARDENER && robot.getID() != rc.getID()) {
                                //Add this location to blacklist
                                int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                                rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(target.x));
                                rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(target.y));
                                num_blacklisted += 1;
                                rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                                target = null;
                            }
                        }
                        //Occupied by local tree
                        if (target != null && rc.isLocationOccupiedByTree(target)) {
                            //Add this location to local blacklist
                            localBlacklist.add(target);
                            target = null;
                        }
                        //Otherwise occupied but not by robot
                        if (target != null && rc.isCircleOccupiedExceptByThisRobot(target, rc.getType().bodyRadius)) {
                            boolean canSkip = false;
                            if (rc.isLocationOccupiedByRobot(target) && rc.senseRobotAtLocation(target).getType() != RobotType.GARDENER && rc.senseRobotAtLocation(target).getType() != RobotType.ARCHON) {
                                canSkip = true;
                            }
                            if (!canSkip) {
                                //Add this location to blacklist
                                int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                                rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(target.x));
                                rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(target.y));
                                num_blacklisted += 1;
                                rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                                target = null;
                            }
                        }
                    }
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }

            //Acquire target if no target
            if (target == null){
                try{
					/*
					 * 500 => number of blacklisted gardener settling points
					 * 501 - 699 => actual blacklisted gardener settling points (2 numbers for each settling point)
					 */
                    //Fetch blacklist
                    HashSet<MapLocation> blacklist = new HashSet<MapLocation>(1000);
                    int blacklist_size = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                    System.out.println("Total Blacklisted locations: " + Integer.toString(blacklist_size));
                    for (int i = 0; i < blacklist_size; i++){
                        float locX = intToFloatPrecise(rc.readBroadcast(BLACKLISTED_LOCATIONS_X + i * 2));
                        float locY = intToFloatPrecise(rc.readBroadcast(BLACKLISTED_LOCATIONS_Y + i * 2));
                        blacklist.add(new MapLocation(locX, locY));
                        if (debug) rc.setIndicatorDot(new MapLocation(locX, locY), 255, 255, 0);
                    }

                    int num_settle_pts = Math.min(rc.readBroadcast(AVAILABLE_FARMING_LOCATIONS), MAX_READ_SETTLE_PTS);
                    float mindist = 10000000f;
                    MapLocation bestPt = null;
                    for (int i = 0; i < num_settle_pts; i++){
                        float locX = intToFloatPrecise(rc.readBroadcast(FARMING_LOCATIONS_X + i * 2));
                        float locY = intToFloatPrecise(rc.readBroadcast(FARMING_LOCATIONS_Y + i * 2));
                        MapLocation newPt = new MapLocation(locX, locY);
                        boolean isValid = true;
                        //ignore locations on blacklist or local blacklist
                        if (blacklist.contains(newPt) || localBlacklist.contains(newPt)){
                            isValid = false;
                        }
                        if (!isValid){
                            continue;
                        }
                        float dist = newPt.distanceTo(MY_LOCATION);
                        if (debug) rc.setIndicatorDot(newPt, 0, 255, 255);
                        if (dist < mindist){
                            mindist = dist;
                            bestPt = newPt;
                        }
                    }
                    target = bestPt;
                    if (target != null) settleTimer = rc.getRoundNum() + Math.round(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/4));
                    else settleTimer = rc.getRoundNum() + LOCATION_TIMEOUT;
                }
                catch (GameActionException e){
                    e.printStackTrace();
                }
            }

            //Target acquired, move to target
            if (target != null){
                if (MY_LOCATION.isWithinDistance(target, rc.getType().strideRadius)) {
                    try {
                        rc.move(target);
                        MY_LOCATION = rc.getLocation();


                        //Check if possible to settle
                        boolean canSettle = true;
                        /*for (int i = 0; i < MAX_GARDENER_TREES; i++){
                            if (!rc.onTheMap(myLoc.add(TREE_DIR[i], rc.getType().sensorRadius - 0.1f))){
                                canSettle = false;
                                break;
                            }
                        }*/

                        if (canSettle) {
                            //Force settle
                            System.out.println("Forced Settle");
                            int validCnt = 0;
                            float density = 999999f;
                            int spawn_direction = -1;
                            for (int i = 0; i < MAX_GARDENER_TREES; i++) {
                                if (!rc.isCircleOccupied(MY_LOCATION.add(TREE_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS)) {
                                    validCnt += 1;
                                    System.out.println("Available build slot at: " + Float.toString(TREE_DIR[i].radians));
                                    float eval = getDensity(MY_LOCATION.add(TREE_DIR[i], MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, TREES);
                                    if (eval < density) {
                                        density = eval;
                                        spawn_direction = i;
                                    }
                                }
                            }
                            System.out.println("validCnt: " + validCnt);
                            if (spawn_direction == -1) {
                                spawn_direction = 0;
                            }
                            SPAWN_DIR = TREE_DIR[spawn_direction];
                            gardenerHasSettled();
                            settledDown = true;
                        }
                        else {
                            //Add this location to blacklist
                            int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                            rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(target.x));
                            rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(target.y));
                            num_blacklisted += 1;
                            rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                            target = null;
                        }


                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                }
            }
            else{
                //Look for a space with a circle of radius 3
                gardenerDensityMove(GARDENER_MOVE_GRANULARITY);
                MY_LOCATION = rc.getLocation();
            }

            //TODO: Rotate trees to be in line with nearby trees
            RobotInfo[] nearbyFriendlyRobots = rc.senseNearbyRobots(MIN_GARDENER_SEPARATION, rc.getTeam());
            boolean hasNearbyGardener = false;
            for (RobotInfo ally : nearbyFriendlyRobots) {
                if (ally.getType() == RobotType.GARDENER) {
                    hasNearbyGardener = true;
                    break;
                }
            }
            int validCnt = 0;
            float density = 999999f;
            int spawn_direction = -1;
            for (int i = 0; i < MAX_GARDENER_TREES; i++) {
                if (!rc.isCircleOccupied(MY_LOCATION.add(TREE_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS)) {
                    validCnt += 1;
                    System.out.println("Available build slot at: " + Float.toString(TREE_DIR[i].radians));
                    float eval = getDensity(MY_LOCATION.add(TREE_DIR[i], MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, TREES);
                    if (eval < density) {
                        density = eval;
                        spawn_direction = i;
                    }
                }
            }
            System.out.println("validCnt: " + validCnt);
            if (spawn_direction == -1) {
                spawn_direction = 0;
            }
            if (validCnt >= MIN_ACCEPTABLE_TREES && !hasNearbyGardener) {
                SPAWN_DIR = TREE_DIR[spawn_direction];
                gardenerHasSettled();
                settledDown = true;
            }
        }
        else{
            //Broadcast new locations for gardeners to settle in
            try{
                int num_settle_pts = rc.readBroadcast(AVAILABLE_FARMING_LOCATIONS);
                for (int i = 0; i < MAX_GARDENER_TREES; i++){
                    MapLocation newGardenerLoc = rc.getLocation().add(TREE_DIR[i], GARDENER_SEPARATION_DIST);
                    if (broadcaster.isValidGrid(newGardenerLoc)){ //Imprecise but I doubt it matters
                        rc.setIndicatorDot(newGardenerLoc, 0, 255, 0); //For testing
                        rc.broadcast(FARMING_LOCATIONS_X + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.x));
                        rc.broadcast(FARMING_LOCATIONS_Y + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.y));
                        num_settle_pts += 1;
                    }
                }
                rc.broadcast(AVAILABLE_FARMING_LOCATIONS, num_settle_pts);
            }
            catch (GameActionException e){
                e.printStackTrace();
            }

            //Maintain a basic army before planting too many trees
            if (build_order[0] != null && build_order[1] != null && rc.getRoundNum() < EARLY_GAME){
                //Skip building trees till first 2 soldiers are out :)
            }
            else if (!spawnLumberjackControl() || !spawnSoldierControl() || rc.getTeamBullets() > BULLET_OVERFLOW) {
                //If I should spawn a lumberjack or soldier or if I've tons of bullets, go build these units first :)

                //Plant trees
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

            //Water
            TreeInfo lowestHealthTree = null;
            float lowestHealth = 1000000f;
            for (TreeInfo tree : SURROUNDING_TREES_OWN) {
                if (tree.getHealth() < lowestHealth) {
                    lowestHealth = tree.getHealth();
                    lowestHealthTree = tree;
                }
            }
            if (lowestHealthTree != null) {
                if (rc.canWater(lowestHealthTree.getID())) {
                    try {
                        rc.water(lowestHealthTree.getID());
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        //Build robots
        if (build_queue_empty){
            if (SURROUNDING_ROBOTS_ENEMY.length > 0){
                if (!has_built_robot) {
                    switch (SURROUNDING_ROBOTS_ENEMY[0].type) {
                        case ARCHON:
                            has_built_robot = forceBuilding(RobotType.SOLDIER);
                            break;
                        case LUMBERJACK:
                            has_built_robot = forceBuilding(RobotType.SOLDIER);
                            break;
                        case SCOUT:
                            has_built_robot = forceBuilding(RobotType.LUMBERJACK);
                            break;
                        case SOLDIER:
                            has_built_robot = forceBuilding(RobotType.SOLDIER);
                            break;
                        case GARDENER:
                            has_built_robot = forceBuilding(RobotType.LUMBERJACK);
                            break;
                        case TANK:
                            has_built_robot = forceBuilding(RobotType.TANK);
                            break;
                    }
                }
            }
            if (spawnLumberjackControl() && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.LUMBERJACK);
                if (has_built_robot) lumberjackTimer = rc.getRoundNum() + LUMBERJACK_DELAY;
            }
            if (spawnTankControl() && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.TANK);
                if (has_built_robot) tankTimer = rc.getRoundNum() + TANK_DELAY;
            }
            if (spawnSoldierControl() && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.SOLDIER);
                if (has_built_robot) soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
            }
            if (spawnScoutControl() && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.SCOUT);
                if (has_built_robot) scoutTimer = rc.getRoundNum() + SCOUT_DELAY;
            }
        }

        return;
    }

    public void endRound() throws GameActionException {
        super.endRound();
        System.out.println("Before computing stuck: "+Integer.toString(Clock.getBytecodeNum()));

        if (prevLocation == null){
            prevLocation = MY_LOCATION;
        }
        else{
            if (rc.getRoundNum() % stuckRounds == 0){
                //Check every stuckRounds rounds
                if (prevLocation.distanceTo(MY_LOCATION) < movement_stuck_radius){
                    //Oops, I'm stuck
                    System.out.println("Oh oops, I'm STUCK!!!");
                    am_stuck = true;
                    cooldown_active = true;
                    cooldownTimer = rc.getRoundNum() + cooldownRounds;
                }
                else{
                    am_stuck = false;
                    if (rc.getRoundNum() > cooldownTimer) cooldown_active = false;
                }
                prevLocation = MY_LOCATION;
            }
        }

        System.out.println("After computing stuck: "+Integer.toString(Clock.getBytecodeNum()));

        /*if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(Broadcaster.location_to_channel(target));
        }*/
    }

    //=========================================================================
    //<---------------------------- GARDENER MICRO --------------------------->
    //=========================================================================
    //<-------------------------- BUILDING/PLANTING -------------------------->
    private boolean shouldCheeseScout(){
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        float tot_bullets = 0;
        for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
            tot_bullets += t.containedBullets;
        }
        if (tot_bullets > 80f) return true;
        return false;
    }

    private boolean spawnLumberjackControl(){
        if (rc.getTeamBullets() < RobotType.LUMBERJACK.bulletCost) return false;
        if (SURROUNDING_TREES_NEUTRAL.length == 0) return false;
        else {
            float cur_ratio = (float) surrounding_lumberjacks / SURROUNDING_TREES_NEUTRAL.length;
            if (cur_ratio > LUMBERJACK_TO_NEUTRAL_RATIO){
                float tot_health_trees = 0f;
                for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
                    tot_health_trees += t.getHealth();
                }
                if (tot_health_trees > 2*NEUTRAL_TREE_HEALTH_THRESHOLD) return true;
                else if (tot_health_trees > NEUTRAL_TREE_HEALTH_THRESHOLD && rc.getRoundNum() > lumberjackTimer) return true;
            }
            else return true;
        }
        return false;
    }

    private boolean spawnSoldierControl(){
        if (rc.getTeamBullets() < RobotType.SOLDIER.bulletCost) return false;
        if (rc.getRoundNum() < soldierTimer){
            if (rc.getTeamBullets() > BULLET_OVERFLOW) return true;
            else return false;
        }
        else{
            float cur_ratio = (float)number_soldiers/Math.max(1,rc.getTreeCount());
            if (cur_ratio > SOLDIER_TO_TREE_RATIO){
                if (number_soldiers < MIN_SOLDIER) return true;
                else return false;
            }
            else{
                if (rand.nextFloat() > SPAWN_SOLDIER_CHANCE) return false;
            }
        }
        return true;
    }

    private boolean spawnTankControl(){
        if (rc.getTeamBullets() < RobotType.TANK.bulletCost) return false;
        if (rc.getTreeCount() < SUSTAINABLE_ECONOMY) return false;
        if (rc.getRoundNum() < tankTimer){
            if (rc.getTeamBullets() > BULLET_OVERFLOW) return true;
            else return false;
        }
        else{
            float cur_ratio = (float)number_tanks/Math.max(1,rc.getTreeCount());
            if (cur_ratio > TANK_TO_TREE_RATIO){
                if (rc.getTeamBullets() > 450) return true;
                else return false;
            }
            else{
                if (rand.nextFloat() > SPAWN_TANK_CHANCE) return false;
            }
        }
        return true;
    }

    private boolean spawnScoutControl(){
        if (rc.getTeamBullets() < RobotType.SCOUT.bulletCost) return false;
        if (rc.getRoundNum() < scoutTimer) return false;
        else{
            float cur_ratio = (float)number_scouts/Math.max(1,rc.getTreeCount());
            if (cur_ratio > SCOUT_TO_TREE_RATIO || rand.nextFloat() > SPAWN_SCOUT_CHANCE) return false;
        }
        return true;
    }

    private RobotType[] getBuildOrder() throws GameActionException {
        RobotType[] build_order = new RobotType[MAX_BUILD_ORDER];
        for (int i=0;i<MAX_BUILD_ORDER;i++){
            build_order[i] = robotType(rc.readBroadcast(BUILD_ORDER_QUEUE+i));
        }
        return build_order;
    }

    private boolean forceBuilding(RobotType robot) throws GameActionException {
        if (!rc.isBuildReady()) return false;
        Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION);
        if (debug) rc.setIndicatorLine(MY_LOCATION, ENEMY_ARCHON_LOCATION, 255, 255, 0);
        Direction tryDir = null;
        for (int i=0;i<SEARCH_GRANULARITY/2;i++){
            if (i%2==0){
                tryDir = initDir.rotateRightRads(i*DEG_360/SEARCH_GRANULARITY);
                if (rc.canBuildRobot(robot,tryDir)){
                    if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                    rc.buildRobot(robot,tryDir);
                    return true;
                }
                else{
                    if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
                }
            }
            else{
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
        }
        if (SPAWN_DIR != null){
            if (rc.canBuildRobot(robot, SPAWN_DIR)){
                rc.buildRobot(robot, SPAWN_DIR);
                return true;
            }
        }
        return false;
    }

    static void gardenerHasSettled(){
        try{
            int num_unit = rc.readBroadcast(SETTLED_FARMERS);
            rc.broadcast(SETTLED_FARMERS, num_unit + 1);
        }
        catch (GameActionException e){
            e.printStackTrace();
        }
    }

    private float gardenerSurroundingDensity(MapLocation travelLoc) throws GameActionException {
        float curDensity = 0;
        for (int i=0;i<6;i++){
            //Scan in a hexagonal pattern around the gardener
            MapLocation testLoc = travelLoc.add(new Direction(DEG_60*i), rc.getType().bodyRadius + 1f + EPSILON);
            if (!rc.onTheMap(testLoc, 1f)){
                curDensity += 1;
                continue;
            }
            curDensity += getDensity(testLoc, 1f, TREES);
        }
        return curDensity;
    }

    private boolean gardenerDensityMove(int GRANULARITY) throws GameActionException { //Bounces the gardener around
        if (LAST_KNOWN_GARDENER_LOCATION != null && MY_LOCATION.distanceTo(LAST_KNOWN_GARDENER_LOCATION) > ATTRACTION_RADIUS){
            return tryMove(MY_LOCATION.directionTo(SPAWNING_ARCHON_LOCATION));
        }
        Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION).opposite();
        if (SPAWNING_ARCHON_LOCATION != null) initDir = SPAWNING_ARCHON_LOCATION.directionTo(MY_LOCATION);
        float curScore = 0f;
        Direction tryDir = null;
        float bestScore = 99999f;
        Direction bestDir = null;
        for (int i=0;i<GRANULARITY/2;i++){
            if (i%2==0){
                tryDir = initDir.rotateRightRads(i*DEG_360/GRANULARITY);
                if (!rc.canMove(tryDir)) continue;
                curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, rc.getType().strideRadius));
                if (curScore == 0){
                    //Trivial case, it's entirely open there :O
                    rc.move(tryDir);
                    return true;
                }
                if (curScore < bestScore){
                    bestScore = curScore;
                    bestDir = tryDir;
                }
            }
            else{
                tryDir = initDir.rotateLeftRads(i*DEG_360/GRANULARITY);
                if (!rc.canMove(tryDir)) continue;
                curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, rc.getType().strideRadius));
                if (curScore == 0){
                    //Trivial case, it's entirely open there :O
                    rc.move(tryDir);
                    return true;
                }
                if (curScore < bestScore){
                    bestScore = curScore;
                    bestDir = tryDir;
                }
            }
        }
        if (bestDir != null){
            if (rc.canMove(bestDir)){
                rc.move(bestDir);
                return true;
            }
        }
        return false;
    }

    private void gardenerMove(int num_attempts) throws GameActionException { //Bounces the gardener around
        while (num_attempts > 0) {
            if (!rc.canMove(moveDir)) {
                moveDir = randomDirection();
            }
            else {
                try {
                    rc.move(moveDir);
                    break;
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }
            num_attempts--;
        }
        return;
    }

}