package Mandalore;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class GardenerPlayer extends BasePlayer {

    //Gardener specific variables
    static float SIGHT_RADIUS = 7f;
    private int treeTimer = 0;
    private int lumberjackTimer = 0;
    private int soldierTimer = 0;
    private int scoutTimer = 0;
    private int tankTimer = 0;
    static float FARMER_CLEARANCE_RADIUS = 8f;      //How far apart should farmers be
    static float TREE_SCAN_RADIUS = 1.1f;           //How large a circle to scan for trees
    static float TREE_CLEARANCE_DISTANCE = 2.5f;	//Distance of gap between trees (>2f)
    static final float TREE_RADIUS = 1f;            //Radius of player trees
    private RobotType[] build_order;
    private boolean build_queue_empty = true;
    private boolean has_built_robot = false;

    private boolean CHEESE_SCOUT = true;

    //Farming stuff
    private MapLocation SPAWNING_ARCHON_LOCATION = null;
    private float ATTRACTION_RADIUS = 11f;
    private MapLocation LAST_KNOWN_GARDENER_LOCATION = null;
    private boolean settledDown = false;
    static final Direction[] HEX_DIR = new Direction[]{new Direction(0f), new Direction((float)(Math.PI / 3)), new Direction((float)(2 * Math.PI / 3)), new Direction((float)(Math.PI)),
            new Direction((float)(4 * Math.PI / 3)), new Direction((float)(5 * Math.PI / 3))};
    static HashSet<MapLocation> localBlacklist = new HashSet<>();
    static final int LOCATION_TIMEOUT = 25;                     //Timeout to get to a new location
    static int settleTimer;
    static final int MAX_READ_SETTLE_PTS = 50;                  //Maximum number of settle pts read
    static int MAX_INIT_TREES = 2;                              //Max number of early game trees

    private Random rand;

    static int number_gardeners = 0;
    static int number_lumberjacks = 0;
    private int surrounding_lumberjacks = 0;
    static boolean spawnedLumberjack = false;
    static int number_scouts = 0;
    static int number_soldiers = 0;
    static int number_tanks = 0;

    //Spawning constants (Macro control)
    private Direction SPAWN_DIR = null;                         //Reserved spawning slot once in position
    private float BULLET_OVERFLOW = 347;                        //Overflowing bullets, build something!
    static final int SOLDIER_DELAY = 30;          				//Number of turns to spawn a new soldier
    private float SPAWN_SOLDIER_CHANCE = 0.75f;
    private float SOLDIER_TO_TREE_RATIO = 1.23f;                //Max number of soldiers per tree
    private int MIN_SOLDIER = 5;                                //Minimum number of soldiers
    private int MAX_SOLDIER = 10;                               //Early game max soldiers
    static final int SCOUT_DELAY = 100;   						//Number of turns to spawn a new scout
    private float SPAWN_SCOUT_CHANCE = 0.0f;
    private float SCOUT_TO_TREE_RATIO = 0.25f;                  //Max number of scouts per tree
    static final int TANK_DELAY = 15; 							//Number of turns to spawn a new tank
    private float SPAWN_TANK_CHANCE = 1f;
    private float TANK_TO_TREE_RATIO = 0.25f;                   //Max number of tanks per tree
    private float TANK_DENSITY_THRESHOLD = 0.15f;               //Surrounding density < this before tanks can be spawned
    private float LUMBERJACK_TO_NEUTRAL_RATIO = 0.43f;          //Number of lumbers per tree surrounding gardener
    private final int LUMBERJACK_DELAY = 30;                    //Number of turns to spawn a new lumberjack
    private float NEUTRAL_TREE_HEALTH_THRESHOLD = 750f;         //Total accumulative health of surrounding trees | above which will spawn lumberjacks
    static final int GARDENER_STUCK_TIMER = 25;					//After n turn, just spawn a lumberjack already!

    private Direction moveDir = randomDirection();
    private ArrayList<Direction> current_available_slots = new ArrayList<>();
    private ArrayList<MapLocation> occupied_settle_locations = new ArrayList<>();
    private int timeToTarget = 0;
    private int targetTimer = THE_FINAL_PROBLEM;

    //Movement stuff
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

    private Direction cooldownDir = null;                       //Try to escape local minimum

    public GardenerPlayer(RobotController rc) throws GameActionException {
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
        //todo I'm debugging this...
        debug = false;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(3f, rc.getTeam());
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

        if (rc.getRoundNum() > MID_GAME){
            SOLDIER_TO_TREE_RATIO = 0.50f;
        }

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

        if (settledDown) current_available_slots = getBuildSlots(MY_LOCATION, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1));
        else{
            if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, 3.01f) && rc.onTheMap(MY_LOCATION, 3f)) current_available_slots = HEXAGONAL_PATTERN;
            //todo Gardener starts to farm early
            else if (rc.getTreeCount() == 0) current_available_slots = getBuildSlots(MY_LOCATION, 2);
            else current_available_slots = getBuildSlots(MY_LOCATION, FARMER_MIN_SLOTS);
        }
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
                float eval = getDensity(MY_LOCATION.add(current_available_slots.get(i), MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, OBJECTS);
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

        //Debug for spawn direction
        if (SPAWN_DIR != null) System.out.println("SPAWN_DIR: "+Float.toString(SPAWN_DIR.radians));

        //todo since got new movement mechanism, ignore spreading out, should be emergent?
        if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS)) settledDown = true;
        //if (current_available_slots.size() > 0) settledDown = true;

        //Debug settling down
        System.out.println("Have I settled? "+Boolean.toString(settledDown));
        System.out.println("Max slots around me: "+Integer.toString(Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1)));

        if (!settledDown){
            //Try to spawn a lumberjack
            //Check if there are trees around
            if (rc.getRoundNum() > lumberjackTimer) {
                forceBuilding(RobotType.LUMBERJACK);
                lumberjackTimer = rc.getRoundNum() + LUMBERJACK_DELAY;
            }

            //Look for a space with a circle of radius 3
            //todo new gardener move algo
            //Vet target location
            if (target != null){
                if (!isSettleLocationValid(target)) target = null;
            }
            //Acquire new target if none currently
            if (target == null){
                target = acquireSettleLocation();
            }
            //Move to target location
            if (target == null){
                if (debug) rc.setIndicatorDot(MY_LOCATION, 255, 0, 0);
                gardenerDensityMove(GARDENER_MOVE_GRANULARITY);
            }
            else{
                if (debug) rc.setIndicatorDot(MY_LOCATION, 0, 255, 0);
                moveToSettleLocation();
            }

            /*if (rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, 3f)) {
                if (LAST_KNOWN_GARDENER_LOCATION == null) gardenerDensityMove(GARDENER_MOVE_GRANULARITY);
                else gardenerHexMove();
            }*/
            //Else in free space already, so don't even move...
        }
        else{
            //Announce locations around itself
            broadcastAvailableSettleLocations();

            //Should I plant trees?
            boolean should_plant_trees = true;

            //Maintain a basic army before planting too many trees
            if (build_order[0] != null && build_order[1] != null && rc.getRoundNum() < EARLY_GAME){
                //Skip building trees till first 2 soldiers are out :)
                should_plant_trees = false;
            }
            if (spawnSoldierControl()){
                //If I should spawn a lumberjack or soldier or if I've tons of bullets, go build these units first :)
                should_plant_trees = false;
            }
            if (rc.getRoundNum() < EARLY_GAME && SURROUNDING_TREES_OWN.length >= MAX_INIT_TREES){
                //Stop planting too many trees early game
                should_plant_trees = false;
            }
            //I have tons of bullets, might as well...
            if (rc.getTeamBullets() > BULLET_OVERFLOW) should_plant_trees = true;

            //Plant trees
            if (should_plant_trees) {
                //Plant trees
                for (Direction dir : current_available_slots) {
                    if (rc.canPlantTree(dir) && rc.getRoundNum() > treeTimer && rand.nextFloat() < PLANT_TREE_CHANCE) {
                        rc.plantTree(dir);
                        treeTimer = rc.getRoundNum() + TREE_DELAY;
                    }
                }
                //Try forcing 5 trees around gardener
                if (SURROUNDING_TREES_OWN.length < 5 && SURROUNDING_TREES_OWN.length > 0) {
                    if (debug && SPAWN_DIR != null)
                        rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(SPAWN_DIR, 3f), 0, 255, 255);
                    Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
                    for (int i = 0; i < 5; i++) {
                        cDir = cDir.rotateRightRads(DEG_60);
                        if (rc.canPlantTree(cDir) && rc.getRoundNum() > treeTimer && rand.nextFloat() < PLANT_TREE_CHANCE) {
                            System.out.println("Forcing planting of tree at: " + Float.toString(cDir.radians));
                            System.out.println("Radians between spawn_dir and cDir: "+Float.toString(SPAWN_DIR.radiansBetween(cDir)));
                        if (absRad(SPAWN_DIR.radiansBetween(cDir)) >= DEG_60){
                            rc.plantTree(cDir);
                            treeTimer = rc.getRoundNum() + TREE_DELAY;
                        }
//                                rc.plantTree(cDir);
//                                treeTimer = rc.getRoundNum() + TREE_DELAY;
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

        if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(broadcaster.location_to_channel(target));
        }
    }

    //=========================================================================
    //<---------------------------- GARDENER MICRO --------------------------->
    //=========================================================================
    //<--------------------------- SPAWNING METRICS -------------------------->
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
                if (rc.getRoundNum() < MID_GAME){
                    System.out.println("I'm in mid game with: "+Integer.toString(number_soldiers)+" soldiers");
                    if (rc.getTreeCount() > 13){    //todo magic numbers :O
                        if (rand.nextFloat() < SPAWN_SOLDIER_CHANCE) return true;
                    }
                    if (number_soldiers >= MAX_SOLDIER) return false;
                }
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

    //<-------------------------- BUILDING/PLANTING -------------------------->
    private RobotType[] getBuildOrder() throws GameActionException {
        boolean build_order_empty = true;
        RobotType[] build_order = new RobotType[MAX_BUILD_ORDER];
        for (int i=0;i<MAX_BUILD_ORDER;i++){
            build_order[i] = robotType(rc.readBroadcast(BUILD_ORDER_QUEUE+i));
            build_order_empty = false;
        }
        if (build_order_empty) return build_order;
        //Vets build order
        float est_size = ENEMY_ARCHON_LOCATION.distanceTo(MY_LOCATION);

        if (getDensity(MY_LOCATION, 4f, TREES) > 0){
            System.out.println("DENSE MAP DETECTED");
            //todo stable: lum_sol_sol
            if (est_size < MAP_SMALL){
                if (build_order[0] != null){
                    build_order[1] = RobotType.SOLDIER;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.LUMBERJACK;
                }
            }
            else if (est_size < MAP_LARGE){
                if (build_order[0] != null){
                    build_order[0] = RobotType.SOLDIER;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.LUMBERJACK;
                }
            }
            else {
                if (build_order[0] != null){
                    build_order[0] = RobotType.SOLDIER;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.LUMBERJACK;
                }
            }
        }
        else {
            System.out.println("Clear Map detected");
            if (build_order[0] != null){
                build_order[0] = RobotType.SOLDIER;
            }
            if (build_order[1] != null){
                build_order[1] = RobotType.SOLDIER;
            }
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
                tryDir = initDir.rotateRightRads(i*DEG_180/SEARCH_GRANULARITY);
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
                tryDir = initDir.rotateLeftRads(i*DEG_180/SEARCH_GRANULARITY);
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

    //<------------------------- TARGET ACQUISITION -------------------------->
    private boolean isSettleLocationValid(MapLocation loc) throws GameActionException {
        if (loc == null) return false;
        if (debug) rc.setIndicatorDot(loc, 0, 0, 255);
        //If unable to get to target in 20 turns => local blacklist then choose new location
        if (rc.getRoundNum() >= settleTimer) {
            settleTimer = 1000000;
            localBlacklist.add(loc);
            return false;
        }
        if (rc.canSenseAllOfCircle(loc, rc.getType().bodyRadius)) {
            //If target is not on the map
            if (!rc.onTheMap(loc, rc.getType().bodyRadius)) {
                //Add this location to blacklist
                int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(loc.x));
                rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(loc.y));
                num_blacklisted += 1;
                rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                if (debug) rc.setIndicatorLine(MY_LOCATION, loc, 255, 0, 0);
                localBlacklist.add(loc);
                return false;
            }
            //Occupied by gardener
            if (rc.isLocationOccupiedByRobot(loc)) {
                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot.getType() == RobotType.GARDENER && robot.getID() != rc.getID()) {
                    //Add this location to blacklist
                    int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                    rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(loc.x));
                    rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(loc.y));
                    num_blacklisted += 1;
                    rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                    localBlacklist.add(loc);
                    return false;
                }
            }
            //Occupied by local tree
            if (rc.isLocationOccupiedByTree(loc)) {
                //Add this location to local blacklist
                localBlacklist.add(loc);
                return false;
            }
            //Otherwise occupied but not by robot
            if (rc.isCircleOccupiedExceptByThisRobot(loc, rc.getType().bodyRadius)) {
                boolean canSkip = false;
                if (rc.isLocationOccupiedByRobot(loc) && rc.senseRobotAtLocation(loc).getType() != RobotType.GARDENER && rc.senseRobotAtLocation(loc).getType() != RobotType.ARCHON) {
                    canSkip = true;
                }
                if (!canSkip) {
                    //Add this location to blacklist
                    int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
                    rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(loc.x));
                    rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(loc.y));
                    num_blacklisted += 1;
                    rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);
                    localBlacklist.add(loc);
                    return false;
                }
            }
        }
        return true;
    }

    private MapLocation acquireSettleLocation() throws GameActionException {
        MapLocation targetLoc = null;
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
                targetLoc = newPt;
            }
        }
        if (targetLoc != null) settleTimer = rc.getRoundNum() + Math.max(LOCATION_TIMEOUT, Math.round(MY_LOCATION.distanceTo(targetLoc)/(rc.getType().strideRadius)/4));
        else settleTimer = rc.getRoundNum() + LOCATION_TIMEOUT;
        return targetLoc;
    }

    private void moveToSettleLocation() throws GameActionException {
        if (target == null) return;     //Error, target not set beforehand
        if (MY_LOCATION.isWithinDistance(target, rc.getType().strideRadius)) {
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);
            rc.move(target);
            MY_LOCATION = rc.getLocation();

            //Force settle
            System.out.println("Forced Settle");
            int validCnt = 0;
            float density = 999999f;
            int spawn_direction = -1;
            for (int i = 0; i < 6; i++) {
                if (!rc.isCircleOccupied(MY_LOCATION.add(HEX_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS)) {
                    validCnt += 1;
                    System.out.println("Available build slot at: " + Float.toString(HEX_DIR[i].radians));
                    float eval = getDensity(MY_LOCATION.add(HEX_DIR[i], MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, TREES);
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
            SPAWN_DIR = HEX_DIR[spawn_direction];
            settledDown = true;
            //Add this location to blacklist
            /*int num_blacklisted = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
            rc.broadcast(BLACKLISTED_LOCATIONS_X + num_blacklisted * 2, floatToIntPrecise(target.x));
            rc.broadcast(BLACKLISTED_LOCATIONS_Y + num_blacklisted * 2, floatToIntPrecise(target.y));
            num_blacklisted += 1;
            rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, num_blacklisted);*/
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 0, 0);
        }
        else {
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 255, 0);
            if (am_stuck) {
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
        }
    }

    private void broadcastAvailableSettleLocations() throws  GameActionException {
        int num_settle_pts = rc.readBroadcast(AVAILABLE_FARMING_LOCATIONS);
        for (int i = 0; i < 6; i++){
            MapLocation newGardenerLoc = rc.getLocation().add(HEX_DIR[i], FARMER_CLEARANCE_RADIUS + EPSILON);
            if (broadcaster.isValidGrid(newGardenerLoc)){ //Imprecise but I doubt it matters
                if (debug) rc.setIndicatorDot(newGardenerLoc, 0, 255, 0); //For testing
                rc.broadcast(FARMING_LOCATIONS_X + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.x));
                rc.broadcast(FARMING_LOCATIONS_Y + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.y));
                num_settle_pts += 1;
            }
        }
        rc.broadcast(AVAILABLE_FARMING_LOCATIONS, num_settle_pts);
    }

    //<------------------------- MOVEMENT ALGORITHMS ------------------------->
    private float gardenerSurroundingDensity(MapLocation travelLoc) throws GameActionException {
        //todo bytecode saving metric
        return getDensity(travelLoc, 3f, OBJECTS);
        //finer metric
//        float curDensity = 0;
//        for (int i=0;i<6;i++){
//            //Scan in a hexagonal pattern around the gardener
//            MapLocation testLoc = travelLoc.add(new Direction(DEG_60*i), 2.1f);
//            if (!rc.onTheMap(testLoc, 1f)){
//                curDensity += 1;
//                continue;
//            }
//            curDensity += getDensity(testLoc, 1f, OBJECTS);
//        }
//        return curDensity;
    }

    private boolean gardenerDensityMove(int GRANULARITY) throws GameActionException { //Bounces the gardener around
        /*if (LAST_KNOWN_GARDENER_LOCATION != null && MY_LOCATION.distanceTo(LAST_KNOWN_GARDENER_LOCATION) > ATTRACTION_RADIUS){
            return tryMove(MY_LOCATION.directionTo(SPAWNING_ARCHON_LOCATION));
        }*/
        if (cooldown_active) {
            if (cooldownDir == null) {
                //Look further away (long-range density) --> should escape local minimum
                Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION);
                float curScore = 0f;
                Direction tryDir = null;
                float bestScore = 99999f;
                Direction bestDir = null;
                MapLocation tryLoc = null;
                for (int i = 0; i < GRANULARITY / 2; i++) {
                    tryDir = initDir.rotateRightRads(i * DEG_360 / GRANULARITY);
                    tryLoc = MY_LOCATION.add(tryDir, 4f);
                    curScore = getDensity(tryLoc, 3f, OBJECTS);
                    if (curScore == 0) {
                        //Trivial case, it's entirely open there :O
                        cooldownDir = tryDir;
                        break;
                    }
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                    }
                    tryDir = initDir.rotateLeftRads(i * DEG_360 / GRANULARITY);
                    tryLoc = MY_LOCATION.add(tryDir, 4f);
                    curScore = getDensity(tryLoc, 3f, OBJECTS);
                    if (curScore == 0) {
                        //Trivial case, it's entirely open there :O
                        cooldownDir = tryDir;
                        break;
                    }
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                    }
                }
                if (bestDir != null){
                    cooldownDir = bestDir;
                }
                else{
                    cooldownDir = initDir;
                }
            }
            target = MY_LOCATION.add(cooldownDir, 5f);
            if (am_stuck) {
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
        }
        else{
            cooldownDir = null;
            //Look close to robot
            Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION).opposite();
            if (SPAWNING_ARCHON_LOCATION != null) initDir = SPAWNING_ARCHON_LOCATION.directionTo(MY_LOCATION);
            float curScore = 0f;
            Direction tryDir = null;
            float bestScore = 99999f;
            Direction bestDir = null;
            float bestDist = 0f;
            for (int j = 0; j < SLUG; j++) {
                float tryDist = (rc.getType().strideRadius) / (float) Math.pow(3, j);
                for (int i = 0; i < GRANULARITY / 2; i++) {
                    tryDir = initDir.rotateRightRads(i * DEG_360 / GRANULARITY);
                    if (!rc.canMove(tryDir, tryDist)) continue;
                    curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, tryDist));
                    if (curScore == 0) {
                        //Trivial case, it's entirely open there :O
                        rc.move(tryDir, tryDist);
                        return true;
                    }
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                        bestDist = tryDist;
                    }
                    tryDir = initDir.rotateLeftRads(i * DEG_360 / GRANULARITY);
                    if (!rc.canMove(tryDir, tryDist)) continue;
                    curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, tryDist));
                    if (curScore == 0) {
                        //Trivial case, it's entirely open there :O
                        rc.move(tryDir, tryDist);
                        return true;
                    }
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                        bestDist = tryDist;
                    }
                }
            }
            if (bestDir != null && bestDist != 0f) {
                if (rc.canMove(bestDir, bestDist)) {
                    rc.move(bestDir, bestDist);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyLoc(MapLocation loc) throws GameActionException {
        if (occupied_settle_locations.contains(loc)) return false;
        if (!rc.canSenseAllOfCircle(loc, 1f)){
            return true;
        }
        else{
            if (!rc.onTheMap(loc, 1f)){
                occupied_settle_locations.add(loc);
                return false;
            }
            else{
                if (rc.isCircleOccupied(loc, 1f)){
                    RobotInfo[] locRobots = rc.senseNearbyRobots(loc, 1f, rc.getTeam());
                    for (RobotInfo r:locRobots){
                        if(r.type == RobotType.GARDENER){
                            occupied_settle_locations.add(loc);
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private MapLocation getSettleTarget() throws GameActionException {
        /*Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION).opposite();
        if (SPAWNING_ARCHON_LOCATION != null) initDir = SPAWNING_ARCHON_LOCATION.directionTo(MY_LOCATION);
        float hexDir = absRad(initDir.radians);
        int hexIdx = Math.round(hexDir/DEG_60);*/
        Direction initDir = new Direction(DEG_60*rand.nextInt(6));
        for (int i=0;i<3;i++){
            Direction tryDir = initDir.rotateRightRads(DEG_60);
            MapLocation tryLoc = LAST_KNOWN_GARDENER_LOCATION.add(tryDir, FARMER_CLEARANCE_RADIUS+rc.getType().bodyRadius*2);
            if (verifyLoc(tryLoc)){
                if (debug) rc.setIndicatorDot(tryLoc, 0, 255, 0);
                timeToTarget = Math.round(MY_LOCATION.distanceTo(tryLoc)/(rc.getType().strideRadius/4));
                targetTimer = rc.getRoundNum() + timeToTarget;
                return tryLoc;
            }
            if (debug) rc.setIndicatorDot(tryLoc, 255, 0, 0);
            tryDir = initDir.rotateLeftRads(DEG_60);
            tryLoc = LAST_KNOWN_GARDENER_LOCATION.add(tryDir, FARMER_CLEARANCE_RADIUS+rc.getType().bodyRadius*2);
            if (verifyLoc(tryLoc)){
                if (debug) rc.setIndicatorDot(tryLoc, 0, 255, 0);
                timeToTarget = Math.round(MY_LOCATION.distanceTo(tryLoc)/(rc.getType().strideRadius/4));
                targetTimer = rc.getRoundNum() + timeToTarget;
                return tryLoc;
            }
            if (debug) rc.setIndicatorDot(tryLoc, 255, 0, 0);
        }
        return null;
    }

    private void gardenerHexMove() throws GameActionException {
        if (LAST_KNOWN_GARDENER_LOCATION == null){
            gardenerMove(GARDENER_MOVE_GRANULARITY);
            return;
        }
        else{
            //Generate the 6 hexagonal directions to move to
            if (rc.getRoundNum() > targetTimer){
                occupied_settle_locations.add(target);
                target = getSettleTarget();
            }
            else if (target == null || !verifyLoc(target)){
                target = getSettleTarget();
            }
            if (target == null) {
                gardenerMove(GARDENER_MOVE_GRANULARITY);
                return;
            }
            if (am_stuck){
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
        }
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