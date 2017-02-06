package YavinIV;

import battlecode.common.*;
import java.util.ArrayList;
import java.util.Random;

public class GardenerPlayer extends BasePlayer {

    //Gardener specific variables
    static float SIGHT_RADIUS = 7f;
    private int treeTimer = 0;
    private int soldierTimer = 0;
    private int scoutTimer = 0;
    private int tankTimer = 0;
    static float FARMER_CLEARANCE_RADIUS = 6f;      //How far apart should farmers be
    static float TREE_SCAN_RADIUS = 1.1f;           //How large a circle to scan for trees
    static float TREE_CLEARANCE_DISTANCE = 2.5f;	//Distance of gap between trees (>2f)
    static final float TREE_RADIUS = 1f;            //Radius of player trees
    private boolean watered_tree = false;
    private MapLocation next_tree = null;
    private MapLocation current_tree = null;
    private MapLocation prev_current_tree = null;
    private RobotType[] build_order;
    private boolean build_queue_empty = true;
    private boolean has_built_robot = false;
    private boolean reached_dying_tree = false;
    private boolean idle_clockwise = true;
    private boolean clockwise_blocked = false;
    private boolean anti_clockwise_blocked = false;

    private boolean settledDown = false;
    private boolean inPosition = false;
    private MapLocation possible_tree_pair = null;
    private MapLocation target_move_location = null;

    private Random rand;

    static int number_gardeners = 0;
    static int number_lumberjacks = 0;
    private int surrounding_lumberjacks = 0;
    static int number_scouts = 0;
    static int number_soldiers = 0;
    static int number_tanks = 0;

    //Spawning constants (Macro control)
    private Direction SPAWN_DIR = null;                         //Reserved spawning slot once in position
    static final int SOLDIER_DELAY = 10;          				//Number of turns to spawn a new soldier
    private float SPAWN_SOLDIER_CHANCE = 0.75f;
    private float SOLDIER_TO_TREE_RATIO = 2f;                   //Max number of soldiers per tree
    static final int SCOUT_DELAY = 100;   						//Number of turns to spawn a new scout
    private float SPAWN_SCOUT_CHANCE = 0.0f;
    private float SCOUT_TO_TREE_RATIO = 3f;                     //Max number of scouts per tree
    static final int TANK_DELAY = 15; 							//Number of turns to spawn a new tank
    private float SPAWN_TANK_CHANCE = 1f;
    private float TANK_TO_TREE_RATIO = 5f;                      //Max number of tanks per tree
    private float LUMBERJACK_TO_NEUTRAL_RATIO = 2f;             //Number of lumbers per tree surrounding gardener

    private Direction moveDir = randomDirection();

    public GardenerPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer() throws GameActionException {
        MOVEMENT_SCANNING_DISTANCE = 3.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
        SPHERE_REPULSION = 4.04f;
        SPHERE_ATTRACTION = 6.7f;
        rand = new Random(rc.getID());
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
        watered_tree = false;
        reached_dying_tree = false;
        has_built_robot = false;
        if (rc.getRoundNum() > EARLY_GAME){
            SPAWN_SCOUT_CHANCE *= (MID_GAME - rc.getRoundNum())/MID_GAME;
        }
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
    }

    public void endRound() throws GameActionException {
        super.endRound();
        if (current_tree != null) prev_current_tree = current_tree;
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

        //Plant trees
        if (current_tree == null) {
            //Check for surrounding trees already planted
            //if (SURROUNDING_TREES_OWN.length > 0 && MY_LOCATION.distanceTo(SURROUNDING_TREES_OWN[0].location) < TREE_CLEARANCE_DISTANCE + 2f) {
            if (SURROUNDING_TREES_OWN.length > 0) {
                current_tree = SURROUNDING_TREES_OWN[0].location;
            }
            else {
                //Begin by planting a tree! :D
                current_tree = forcePlanting();
                treeTimer = rc.getRoundNum() + TREE_DELAY;
            }
        }
        else{
            if (MY_LOCATION.distanceTo(current_tree) > TREE_CLEARANCE_DISTANCE){
                if (SURROUNDING_TREES_OWN.length > 0){
                    current_tree = SURROUNDING_TREES_OWN[0].location;
                }
            }
            if (debug) rc.setIndicatorDot(current_tree, 255, 255, 255);
        }

        System.out.println("Settled down? "+Boolean.toString(settledDown));
        System.out.println("In position? "+Boolean.toString(inPosition));
        System.out.println("Clockwise rotation blocked? "+Boolean.toString(clockwise_blocked));

        //Settle down and increase density of trees
        if (settledDown){
            //In position, start to plant clusters of 6 trees to increase space efficiency (late-game)
            if (inPosition){
                //Reserve a slot for building units
                if (SPAWN_DIR == null) {
                    ArrayList<Direction> surr_built_slots = new ArrayList<Direction>();
                    for (int i = 1; i < 3; i++) {
                        surr_built_slots.add(new Direction(i * DEG_60));
                        surr_built_slots.add(new Direction(-i * DEG_60));
                    }
                    Direction least_dense_dir = getLeastDenseDirection(surr_built_slots);

                    if (least_dense_dir != null) {
                        SPAWN_DIR = least_dense_dir;
                    }
                }

                //In position to start tree cluster
                TreeInfo[] nearby_grove = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE, rc.getTeam());
                if (rc.getRoundNum() > treeTimer && rc.getTeamBullets() > 75 && nearby_grove.length < 5){
                    for (int i=1;i<3;i++){
                        Direction plantDir = new Direction(i*DEG_60);
                        if (plantDir != SPAWN_DIR && isPositionValid(MY_LOCATION.add(plantDir, TREE_CLEARANCE_DISTANCE))){
                            if (rc.canMove(plantDir, TREE_CLEARANCE_DISTANCE-2f) && !rc.hasMoved()){
                                rc.move(plantDir, TREE_CLEARANCE_DISTANCE-2f);
                                inPosition = false;
                                if (rc.canPlantTree(plantDir)){
                                    rc.plantTree(plantDir);
                                    break;
                                }
                            }
                        }
                        else {
                            plantDir = new Direction(-i * DEG_60);
                            if (plantDir != SPAWN_DIR && isPositionValid(MY_LOCATION.add(plantDir, TREE_CLEARANCE_DISTANCE))){
                                if (rc.canMove(plantDir, TREE_CLEARANCE_DISTANCE-2f) && !rc.hasMoved()){
                                    rc.move(plantDir, TREE_CLEARANCE_DISTANCE-2f);
                                    inPosition = false;
                                    if (rc.canPlantTree(plantDir)){
                                        rc.plantTree(plantDir);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else{
                //Find a spot in the forest to settle down
                //Check if spot has already been found...
                if (possible_tree_pair == null || target_move_location == null) {
                    MapLocation cur_possible_tree_pair = current_tree.add(new Direction(0f), 2f + TREE_CLEARANCE_DISTANCE);
                    MapLocation cur_target_move_location = current_tree.add(new Direction(0f), (2f + TREE_CLEARANCE_DISTANCE) / 2);
                    if (debug) rc.setIndicatorLine(MY_LOCATION, cur_possible_tree_pair, 0, 0, 255);
                    if (rc.canSenseAllOfCircle(cur_possible_tree_pair, TREE_SCAN_RADIUS)) {
                        if (!testGroveLocation(cur_possible_tree_pair, cur_target_move_location)) {
                            if (debug) rc.setIndicatorDot(cur_target_move_location, 150, 10, 0);
                            cur_possible_tree_pair = current_tree.add(new Direction(DEG_180), 2f + TREE_CLEARANCE_DISTANCE);
                            cur_target_move_location = current_tree.add(new Direction(DEG_180), (2f + TREE_CLEARANCE_DISTANCE) / 2);
                            if (debug) rc.setIndicatorLine(MY_LOCATION, cur_possible_tree_pair, 0, 0, 255);
                            if (debug) rc.setIndicatorDot(cur_target_move_location, 0, 255, 0);
                            if (rc.canSenseAllOfCircle(cur_possible_tree_pair, TREE_SCAN_RADIUS)) {
                                testGroveLocation(cur_possible_tree_pair, cur_target_move_location);
                            }
                        }
                    }
                    else {
                        if (debug) rc.setIndicatorDot(cur_target_move_location, 150, 10, 0);
                        cur_possible_tree_pair = current_tree.add(new Direction(DEG_180), 2f + TREE_CLEARANCE_DISTANCE);
                        cur_target_move_location = current_tree.add(new Direction(DEG_180), (2f + TREE_CLEARANCE_DISTANCE) / 2);
                        if (debug) rc.setIndicatorLine(MY_LOCATION, cur_possible_tree_pair, 0, 0, 255);
                        if (debug) rc.setIndicatorDot(cur_target_move_location, 0, 255, 0);
                        if (rc.canSenseAllOfCircle(cur_possible_tree_pair, TREE_SCAN_RADIUS)) {
                            testGroveLocation(cur_possible_tree_pair, cur_target_move_location);
                        }
                    }
                }
                else{
                    //Check if position is still valid
                    if (!rc.canSenseAllOfCircle(possible_tree_pair, TREE_SCAN_RADIUS) || rc.senseNearbyTrees(possible_tree_pair, TREE_SCAN_RADIUS, rc.getTeam()).length <= 0) {
                        //Bad location...
                        System.out.println("I'm in a bad position here...");
                        possible_tree_pair = null;
                        target_move_location = null;
                    }
                }

                if (possible_tree_pair != null && target_move_location != null){
                    if (debug) rc.setIndicatorDot(target_move_location, 0, 255, 0);
                    if (MY_LOCATION != target_move_location) {
                        if (rc.canMove(target_move_location) && !rc.hasMoved()) {
                            rc.move(target_move_location);
                            inPosition = true;
                        }
                        else orbitMove(current_tree, possible_tree_pair);
                    }
                }
            }

            //Water surrounding trees
            TreeInfo[] surrounding_trees = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE, rc.getTeam());
            float lowest_hp_tree = 9999f;
            MapLocation lowest_hp_tree_loc = null;
            for (TreeInfo t:surrounding_trees){
                if (t.health < lowest_hp_tree){
                    lowest_hp_tree = t.health;
                    lowest_hp_tree_loc = t.location;
                }
            }
            if (lowest_hp_tree_loc != null) {
                if (debug) rc.setIndicatorDot(lowest_hp_tree_loc, 255, 200, 100);
                if (rc.canWater(lowest_hp_tree_loc)) {
                    rc.water(lowest_hp_tree_loc);
                    reached_dying_tree = true;
                    if (debug) rc.setIndicatorDot(lowest_hp_tree_loc, 0, 255, 255);
                }
            }
        }
        else if (current_tree != null){
            //Gravitate towards outside edge of forest
            float lowest_density = 9999f;
            MapLocation lowest_density_tree = null;
            for (TreeInfo t : SURROUNDING_TREES_OWN) {
                //if (MY_LOCATION.distanceTo(t.location) > SIGHT_RADIUS) continue;
                if (MY_LOCATION.distanceTo(t.location) > TREE_CLEARANCE_DISTANCE + 2.5f) continue;
                float tree_surrounding_density = getDensity(t.location, 2.9f, OBJECTS);
                if (tree_surrounding_density < lowest_density) {
                    lowest_density = tree_surrounding_density;
                    lowest_density_tree = t.location;
                }
            }
            if (lowest_density_tree != null) current_tree = lowest_density_tree;

            //Find the next possible position to plant trees
            if (next_tree != null) {
                if (MY_LOCATION.distanceTo(next_tree) < SIGHT_RADIUS - 2f) {
                    if (!isPositionValid(next_tree)) next_tree = nearestTreeSlot(current_tree);
                }
                else if (prev_current_tree != current_tree){
                    //if current tree has moved, reset next tree target
                    next_tree = nearestTreeSlot(current_tree);
                }
            }
            else {
                next_tree = nearestTreeSlot(current_tree);
            }

            //Try to plant the tree, if not, move closer in order to plant it
            if (rc.getRoundNum() > treeTimer && rc.getTeamBullets() > 50 && next_tree != null) {
                //Look around to plant the next tree
                System.out.println("Next location direction relative to current tree: " + Float.toString(current_tree.directionTo(next_tree).radians));
                //Check if can move directly into position
                MapLocation direct_loc = current_tree.add(current_tree.directionTo(next_tree), TREE_CLEARANCE_DISTANCE);
                if (debug) rc.setIndicatorDot(direct_loc, 255, 0, 0);
                if (debug) rc.setIndicatorDot(next_tree, 0, 0, 255);
                if (rc.canMove(direct_loc) && MY_LOCATION.distanceTo(next_tree) < TREE_CLEARANCE_DISTANCE) {
                    rc.move(direct_loc);
                    //Try forcing a tank spawn (since we're at the edge of the forest)
                    has_built_robot = forceBuilding(RobotType.TANK);
                    if (has_built_robot){
                        tankTimer = rc.getRoundNum() + TANK_DELAY;
                    }
                    else if (rc.canPlantTree(current_tree.directionTo(next_tree)) && rc.getLocation().add(MY_LOCATION.directionTo(next_tree), 2f).distanceTo(next_tree) < EPSILON) {
                        rc.plantTree(current_tree.directionTo(next_tree));
                        treeTimer = rc.getRoundNum() + TREE_DELAY;
                        next_tree = null;
                    }
                }
                else {
                    //Orbit around to move into position
                    System.out.println("Moving into position to plant tree");
                    MapLocation try_tree = orbitMove(current_tree, next_tree);
                    if (try_tree != null) current_tree = try_tree;
                }
            }

            //Water trees
            float lowest_hp_tree = 9999f;
            MapLocation lowest_hp_tree_loc = null;
            for (TreeInfo t:SURROUNDING_TREES_OWN){
                if (t.health < lowest_hp_tree){
                    lowest_hp_tree = t.health;
                    lowest_hp_tree_loc = t.location;
                }
            }

            if (lowest_hp_tree_loc != null) {
                if (debug) rc.setIndicatorDot(lowest_hp_tree_loc, 255, 200, 100);
                if (rc.canWater(lowest_hp_tree_loc)) {
                    rc.water(lowest_hp_tree_loc);
                    reached_dying_tree = true;
                }
                else {
                    if (current_tree != null && !rc.hasMoved()) {
                        System.out.println("Moving to water tree");
                        MapLocation try_tree = orbitMove(current_tree, lowest_hp_tree_loc);
                        if (try_tree != null) current_tree = try_tree;
                        if (rc.canWater(lowest_hp_tree_loc)){
                            rc.water(lowest_hp_tree_loc);
                            if (debug) rc.setIndicatorDot(lowest_hp_tree_loc, 0, 255, 255);
                            watered_tree = true;
                        }
                    }
                }
                //If cannot reach lowest hp tree to water, at least water the next rechable tree
                //Still, move towards that particular tree :)
                if (!watered_tree){
                    if (current_tree != null){
                        orbitMove(current_tree, lowest_hp_tree_loc);
                    }
                    lowest_hp_tree = 9999f;
                    lowest_hp_tree_loc = null;
                    for (TreeInfo t : SURROUNDING_TREES_OWN) {
                        if (t.health < lowest_hp_tree && rc.canWater(t.ID)) {
                            lowest_hp_tree = t.health;
                            lowest_hp_tree_loc = t.location;
                        }
                    }
                    if (lowest_hp_tree_loc != null) {
                        //if (current_tree != null) orbitMove(current_tree, lowest_hp_tree_loc);
                        if (rc.canWater(lowest_hp_tree_loc)) rc.water(lowest_hp_tree_loc);
                        if (debug) rc.setIndicatorDot(lowest_hp_tree_loc, 0, 255, 255);
                    }
                }
            }

            //Test if conditions permit self to settle down
            settledDown = canSettleDown();
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
            if (SURROUNDING_TREES_NEUTRAL.length > 0 && ((float)surrounding_lumberjacks/SURROUNDING_TREES_NEUTRAL.length) < LUMBERJACK_TO_NEUTRAL_RATIO && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.LUMBERJACK);
            }
            if (rc.getRoundNum() > soldierTimer && ((float)number_soldiers/rc.getTreeCount()) < SOLDIER_TO_TREE_RATIO && rand.nextFloat() < SPAWN_SOLDIER_CHANCE - innerCodePenalty(current_tree, RobotType.SOLDIER) && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.SOLDIER);
                if (has_built_robot) soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
            }
            if (rc.getRoundNum() > tankTimer && ((float)number_tanks/rc.getTreeCount()) < TANK_TO_TREE_RATIO && rand.nextFloat() <  - innerCodePenalty(current_tree, RobotType.TANK) && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.TANK);
                if (has_built_robot) tankTimer = rc.getRoundNum() + TANK_DELAY;
            }
            if (rc.getRoundNum() > scoutTimer && ((float)number_scouts/rc.getTreeCount()) < SCOUT_TO_TREE_RATIO && rand.nextFloat() < SPAWN_SCOUT_CHANCE - innerCodePenalty(current_tree, RobotType.SCOUT) && !has_built_robot) {
                has_built_robot = forceBuilding(RobotType.SCOUT);
                if (has_built_robot) scoutTimer = rc.getRoundNum() + SCOUT_DELAY;
            }
        }

        if (!rc.hasMoved() && !settledDown){
            //Idly circling around the current tree
            if (current_tree != null) {
                if (!orbitMove(current_tree, idle_clockwise)){
                    idle_clockwise = !idle_clockwise;
                }
            }
            else{
                Direction randDir = randomDirection();
                if (rc.canMove(randDir)) rc.move(randDir);
            }
        }

        return;
    }
    //=========================================================================
    //<---------------------------- GARDENER MICRO --------------------------->
    //=========================================================================
    //<---------------------------- MOVEMENT ALGOS --------------------------->
    private MapLocation orbitMove(MapLocation currentTree, MapLocation targetTree) throws GameActionException {
        if (rc.hasMoved()) return null;
        if (tryMove(MY_LOCATION.directionTo(targetTree))) return null;
        //Determine if another tree in between should be used as the waypoint
        Direction from_current = currentTree.directionTo(MY_LOCATION);
        Direction from_target = targetTree.directionTo(MY_LOCATION);
        boolean clockwise = false;
        if (from_current.radiansBetween(from_target.opposite()) < DEG_60) {
            TreeInfo[] nextClosestTree = rc.senseNearbyTrees((float)0.9*(TREE_CLEARANCE_DISTANCE+2f)-1f, rc.getTeam());
            //TreeInfo[] nextClosestTree = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE - 2f, rc.getTeam());
            if (nextClosestTree.length > 1) {
                currentTree = nextClosestTree[1].location;
                from_current = currentTree.directionTo(MY_LOCATION);
                from_target = targetTree.directionTo(MY_LOCATION);
                if (debug) rc.setIndicatorLine(currentTree, MY_LOCATION, 0, 255, 0);
                if (debug) rc.setIndicatorLine(targetTree, MY_LOCATION, 0, 0, 255);
            }
            else{
                if (debug) rc.setIndicatorLine(currentTree, MY_LOCATION, 255, 0, 0);
                if (debug) rc.setIndicatorLine(targetTree, MY_LOCATION, 0, 0, 255);
            }
        }
        else{
            if (debug) rc.setIndicatorLine(currentTree, MY_LOCATION, 255, 0, 0);
            if (debug) rc.setIndicatorLine(targetTree, MY_LOCATION, 0, 0, 255);
        }

        //Move into orbiting position around central tree
        MapLocation optimalLocation = current_tree.add(current_tree.directionTo(MY_LOCATION), 2.15f);
        if (MY_LOCATION != optimalLocation) {
            if (MY_LOCATION.distanceTo(optimalLocation) <= RobotType.GARDENER.strideRadius && MY_LOCATION.distanceTo(optimalLocation) > EPSILON) {
                if (debug) rc.setIndicatorDot(optimalLocation, 0, 0, 0);
                if (rc.canMove(optimalLocation)){
                    rc.move(optimalLocation);
                    System.out.println("Spent turn relocating into orbit");
                    return currentTree;
                }
            }
            else{
                tryMove(from_target.opposite());
                return currentTree;
            }
        }

        if (currentTree == targetTree){
            tryMove(from_current.opposite());
        }
        else {
            if (rc.canMove(from_target.opposite())) {
                rc.move(from_target.opposite());
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(from_target.opposite(), 0.5f), 255, 255, 0);
            }
            else {

                //Orbit clockwise/anti-clockwise around the current tree to water target
                if (is_closest_rotation_clockwise(currentTree.directionTo(MY_LOCATION), currentTree.directionTo(targetTree))) {
                    //Rotate clockwise
                    if (!clockwise_blocked) clockwise = true;
                    else{
                        MapLocation tryLoc = null;
                        float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                        tryLoc = currentTree.add(from_current.rotateRightRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                        if (rc.canMove(tryLoc)){
                            clockwise = true;
                            clockwise_blocked = false;
                        }
                    }
                }
                else{
                    if (anti_clockwise_blocked) clockwise = true;
                    else{
                        MapLocation tryLoc = null;
                        float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                        tryLoc = currentTree.add(from_current.rotateLeftRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                        if (rc.canMove(tryLoc)){
                            clockwise = false;
                            anti_clockwise_blocked = true;
                        }
                    }
                }

                if (from_current.radiansBetween(from_target.opposite()) < DEG_60) {
                    TreeInfo[] nextClosestTree = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE - 2f, rc.getTeam());
                    if (nextClosestTree.length > 1) {
                        currentTree = nextClosestTree[1].location;
                    }
                }

                MapLocation tryLoc = null;
                if (clockwise) {
                    float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                    tryLoc = currentTree.add(from_current.rotateRightRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                }
                else {
                    float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                    tryLoc = currentTree.add(from_current.rotateLeftRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                }

                if (debug) rc.setIndicatorDot(tryLoc, 255, 255, 0);

                if (tryLoc != null) {
                    if (rc.canMove(tryLoc)) {
                        if (debug) rc.setIndicatorLine(MY_LOCATION, tryLoc, 0, 255, 0);
                        rc.move(tryLoc);
                        if (clockwise) clockwise_blocked = false;
                        else if (!clockwise) anti_clockwise_blocked = false;
                    }
                    else {
                        if (clockwise) clockwise_blocked = true;
                        else if (!clockwise) anti_clockwise_blocked = true;
                        clockwise = !clockwise;
                        if (clockwise) {
                            float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                            tryLoc = currentTree.add(from_current.rotateRightRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                        }
                        else {
                            float rotation_radians = 2 * (float) Math.asin(0.25f / currentTree.distanceTo(MY_LOCATION));
                            tryLoc = currentTree.add(from_current.rotateLeftRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
                        }
                        if (rc.canMove(tryLoc)){
                            if (debug) rc.setIndicatorLine(MY_LOCATION, tryLoc, 0, 255, 0);
                            rc.move(tryLoc);
                        }
                    }
                }
            }
        }

        return currentTree;
    }

    private boolean orbitMove(MapLocation currentTree, boolean clockwise) throws GameActionException {
        if (rc.hasMoved()) return false;
        //Orbit clockwise/anti-clockwise around the current tree to water target
        Direction from_current = currentTree.directionTo(MY_LOCATION);
        MapLocation tryLoc = null;
        if (clockwise){
            float rotation_radians = 2*(float) Math.asin(0.25f/currentTree.distanceTo(MY_LOCATION));
            tryLoc = currentTree.add(from_current.rotateRightRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
        }
        else {
            float rotation_radians = 2*(float) Math.asin(0.25f/currentTree.distanceTo(MY_LOCATION));
            tryLoc = currentTree.add(from_current.rotateLeftRads(rotation_radians), currentTree.distanceTo(MY_LOCATION));
        }

        if (tryLoc != null){
            if (rc.canMove(tryLoc)){
                rc.move(tryLoc);
                return true;
            }
        }

        return false;
    }

    private MapLocation nearestTreeSlot(MapLocation current_tree) throws GameActionException {
        Direction sector = current_tree.directionTo(MY_LOCATION);
        int init_search_index = 0;

        //Find which position on the tree grid is closest to gardener
        for (int i=0;i<6;i++){
            if (absRad(sector.radians) > absRad(i*DEG_60 - DEG_30) && absRad(sector.radians) < absRad(i*DEG_60 + DEG_30)){
                init_search_index = i;
                break;
            }
        }

        System.out.println("Section: "+Integer.toString(init_search_index));

        //Check free circle around location
        MapLocation cur_test_position;
        int curIdx = 0;
        Direction curDir = null;
        for (int i=0;i<4;i++){
            curIdx = init_search_index + i;
            curDir = new Direction(curIdx*DEG_60);
            cur_test_position = current_tree.add(curDir, TREE_CLEARANCE_DISTANCE+2f);
            if (isPositionValid(cur_test_position)) return cur_test_position;
            curIdx = init_search_index - i;
            curDir = new Direction(curIdx*DEG_60);
            cur_test_position = current_tree.add(curDir, TREE_CLEARANCE_DISTANCE+2f);
            if (isPositionValid(cur_test_position)) return cur_test_position;
        }

        return null;
    }

    //<-------------------------- BUILDING/PLANTING -------------------------->
    private RobotType[] getBuildOrder() throws GameActionException {
        RobotType[] build_order = new RobotType[MAX_BUILD_ORDER];
        for (int i=0;i<MAX_BUILD_ORDER;i++){
            build_order[i] = robotType(rc.readBroadcast(BUILD_ORDER_QUEUE+i));
        }
        return build_order;
    }

    private MapLocation forcePlanting() throws GameActionException {
        for (int i=0;i<SEARCH_GRANULARITY;i++){
            Direction tryDir = new Direction(i*DEG_360/SEARCH_GRANULARITY);
            if (rc.canPlantTree(tryDir) && rc.onTheMap(MY_LOCATION.add(tryDir, 2f), TREE_RADIUS + TREE_CLEARANCE_DISTANCE)){
                rc.plantTree(tryDir);
                return MY_LOCATION.add(tryDir, 2f);
            }
        }
        return null;
    }

    private boolean forceBuilding(RobotType robot) throws GameActionException {
        for (int i=0;i<SEARCH_GRANULARITY;i++){
            Direction tryDir = new Direction(i*DEG_360/SEARCH_GRANULARITY);
            if (rc.canBuildRobot(robot,tryDir)){
                rc.buildRobot(robot,tryDir);
                return true;
            }
        }
        return false;
    }

    //<--------------------------- METRIC FUNCTIONS -------------------------->
    private float innerCodePenalty(MapLocation centerTree, RobotType robot){
        if (centerTree == null) return 0f;
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(centerTree, 3.5f+TREE_CLEARANCE_DISTANCE, rc.getTeam());
        int excess = Math.max(0, nearbyTrees.length - 4);
        switch(robot){
            case SOLDIER:
                return excess*0.10f;
            case SCOUT:
                return excess*0.25f;
            case TANK:
                return excess*0.50f;
        }
        return 0f;
    }

    private boolean isPositionValid(MapLocation searchLoc) throws GameActionException {
        if (rc.canSenseAllOfCircle(searchLoc, TREE_RADIUS)){
            if (!rc.isCircleOccupiedExceptByThisRobot(searchLoc, TREE_RADIUS) && rc.onTheMap(searchLoc, TREE_RADIUS)){
                return true;
            }
        }
        return false;
    }

    private boolean testTreeExists(MapLocation treeLoc) throws GameActionException {
        if (!rc.canSenseAllOfCircle(treeLoc, TREE_SCAN_RADIUS)) return false;
        if (rc.onTheMap(treeLoc, TREE_SCAN_RADIUS) && rc.senseNearbyTrees(treeLoc, TREE_SCAN_RADIUS, rc.getTeam()).length > 0) {
            return true;
        }
        return false;
    }

    private boolean testGroveLocation(MapLocation cur_possible_tree_pair, MapLocation cur_target_move_location) throws GameActionException {
        if (rc.onTheMap(cur_possible_tree_pair, TREE_SCAN_RADIUS) && rc.senseNearbyTrees(cur_possible_tree_pair, TREE_SCAN_RADIUS, rc.getTeam()).length > 0) {
            if (debug) rc.setIndicatorDot(cur_target_move_location, 0, 255, 0);
            //Possible tree pair found, move towards target!
            possible_tree_pair = cur_possible_tree_pair;
            target_move_location = cur_target_move_location;
            return true;
        }
        return false;
    }

    private boolean canSettleDown() throws GameActionException {
        //Check if is orbiting current tree
        if ((MY_LOCATION.distanceTo(current_tree)- RobotType.GARDENER.bodyRadius - 1f) > TREE_CLEARANCE_DISTANCE) return false;

        //Check if area is completely on the map
        if (!rc.onTheMap(MY_LOCATION, TREE_CLEARANCE_DISTANCE/2 + 2f)) return false;

        //Check if area is free from neutral trees blocking
        TreeInfo[] neutral_trees = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE/2 + 2f, Team.NEUTRAL);
        if (neutral_trees.length > 0) return false;

        //Check if surrounding tree count is sufficient
        TreeInfo[] surrounding_trees = rc.senseNearbyTrees(TREE_CLEARANCE_DISTANCE, rc.getTeam());
        if (surrounding_trees.length < 2) return false;

        //Check if potential farmers are sufficiently spaced out
        RobotInfo[] surrounding_gardeners = rc.senseNearbyRobots(FARMER_CLEARANCE_RADIUS, rc.getTeam());
        for (RobotInfo r:surrounding_gardeners){
            if (r.type == RobotType.GARDENER) return false;
        }

        //Check if is positioned correctly between two trees
        MapLocation cur_possible_tree_pair = current_tree.add(new Direction(0f), 2f + TREE_CLEARANCE_DISTANCE);
        MapLocation alt_cur_possible_tree_pair = current_tree.add(new Direction(DEG_180), 2f + TREE_CLEARANCE_DISTANCE);
        if ((!testTreeExists(cur_possible_tree_pair) || MY_LOCATION.distanceTo(cur_possible_tree_pair) > TREE_CLEARANCE_DISTANCE) && (!testTreeExists(alt_cur_possible_tree_pair) || MY_LOCATION.distanceTo(alt_cur_possible_tree_pair) > TREE_CLEARANCE_DISTANCE)) return false;

        //Free space checks out :) starts hyper-farming
        return true;
    }

    //<------------------------------ MATH STUFFZ ---------------------------->
    private boolean is_closest_rotation_clockwise(Direction to_self, Direction to_target){
        System.out.println("to_self: "+Float.toString(to_self.radians));
        System.out.println("to_target: "+Float.toString(to_target.radians));
        float new_to_self = normalRad(to_self.radians - to_target.radians);
        return new_to_self > 0;
    }

    private float normalRad(float rad){
        if (rad > DEG_180) return -DEG_180 + (rad-DEG_180);
        return rad;
    }

}