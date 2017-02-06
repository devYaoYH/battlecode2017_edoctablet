package Dagobah;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public class GardenerPlayer extends BasePlayer {

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

    //Spawning constants (Macro control)
    private Direction SPAWN_DIR = null;                         //Reserved spawning slot once in position
    private float BULLET_OVERFLOW = 347;                        //Overflowing bullets, build something!
    static final int SOLDIER_DELAY = 30;          				//Number of turns to spawn a new soldier
    private float SPAWN_SOLDIER_CHANCE = 0.95f;
    private float SOLDIER_TO_TREE_RATIO = 1.23f;                //Max number of soldiers per tree
    private int MIN_SOLDIER = 10;                               //Minimum number of soldiers
    static final int SCOUT_DELAY = 100;   						//Number of turns to spawn a new scout
    private float SPAWN_SCOUT_CHANCE = 0.0f;
    private float SCOUT_TO_TREE_RATIO = 0.25f;                  //Max number of scouts per tree
    static final int TANK_DELAY = 15; 							//Number of turns to spawn a new tank
    private float SPAWN_TANK_CHANCE = 1f;
    private float TANK_TO_TREE_RATIO = 0.25f;                   //Max number of tanks per tree
    private float TANK_DENSITY_THRESHOLD = 0.15f;               //Surrounding density < this before tanks can be spawned
    private float LUMBERJACK_TO_NEUTRAL_RATIO = 0.43f;          //Number of lumbers per tree surrounding gardener
    private float NEUTRAL_TREE_HEALTH_THRESHOLD = 750f;         //Total accumulative health of surrounding trees | above which will spawn lumberjacks
    static final int GARDENER_STUCK_TIMER = 25;					//After n turn, just spawn a lumberjack already!

    private Direction moveDir = randomDirection();
    static ArrayList<Direction> current_available_slots = new ArrayList<>();

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
                float eval = getDensity(MY_LOCATION.add(current_available_slots.get(i), MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS, TREES);
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

        if (!settledDown){
            //Try to spawn a lumberjack
            //Check if there are trees around
            if (rc.getRoundNum() > lumberjackTimer) {
                forceBuilding(RobotType.LUMBERJACK);
                lumberjackTimer = rc.getRoundNum() + GARDENER_STUCK_TIMER;
            }
            //Look for a space with a circle of radius 3
            //todo new gardener move algo
            if (!isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS)) gardenerMove(GARDENER_MOVE_GRANULARITY);
            else gardenerDensityMove(GARDENER_MOVE_GRANULARITY);
        }
        else{
            //Maintain a basic army before planting too many trees
            if (build_order[0] != null && build_order[1] != null){
                //Skip building trees till first 2 soldiers are out :)
            }
            else if (!spawnLumberjackControl() || !spawnSoldierControl() || rc.getTeamBullets() > BULLET_OVERFLOW) {
                //If I should spawn a lumberjack or soldier or if I've tons of bullets, go build these units first :)
            //Plant trees
            for (Direction dir : current_available_slots) {
                if (rc.canPlantTree(dir) && rc.getRoundNum() > treeTimer && rand.nextFloat() < PLANT_TREE_CHANCE) {
                    rc.plantTree(dir);
                    treeTimer = rc.getRoundNum() + TREE_DELAY;
                }
            }
            //Try forcing 5 trees around gardener
            if (SURROUNDING_TREES_OWN.length < 5 && SURROUNDING_TREES_OWN.length > 0) {
                Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
                for (int i = 0; i < 5; i++) {
                    cDir = new Direction(absRad(cDir.radians + DEG_60));
                    if (rc.canPlantTree(cDir) && rc.getRoundNum() > treeTimer && rand.nextFloat() < PLANT_TREE_CHANCE) {
                        System.out.println("Forcing planting of tree at: " + Float.toString(cDir.radians));
                        float angle_diff = absRad(cDir.radians - SPAWN_DIR.radians);
                        if (angle_diff > DEG_180) angle_diff = DEG_360 - angle_diff;
                        if (angle_diff >= DEG_60) {
                            rc.plantTree(cDir);
                            treeTimer = rc.getRoundNum() + TREE_DELAY;
                        }
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
                if (has_built_robot) lumberjackTimer = rc.getRoundNum() + GARDENER_STUCK_TIMER;
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
                if (tot_health_trees > NEUTRAL_TREE_HEALTH_THRESHOLD) return true;
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

    private float gardenerSurroundingDensity(MapLocation travelLoc) throws GameActionException {
        float curDensity = 0;
        for (int i=0;i<6;i++){
            //Scan in a hexagonal pattern around the gardener
            MapLocation testLoc = travelLoc.add(new Direction(DEG_60*i), 2.1f);
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