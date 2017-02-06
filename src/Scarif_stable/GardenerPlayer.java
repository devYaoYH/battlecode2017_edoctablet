package Scarif_stable;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;

public class GardenerPlayer extends BasePlayer {

    //Gardener specific variables
    private int treeTimer = 0;
    private int lumberjackTimer = 0;
    private int soldierTimer = 0;
    private int scoutTimer = 0;
    private int tankTimer = 0;
    static float FARMER_CLEARANCE_RADIUS = 8f;                  //How far apart should farmers be
    static final float TREE_RADIUS = 1f;                        //Radius of player trees
    private RobotType[] build_order;
    private boolean build_queue_empty = true;
    private boolean has_built_robot = false;
    private boolean enemyEngaged = false;

    private boolean CHEESE_SCOUT = true;

    //Farming stuff
    private MapLocation SPAWNING_ARCHON_LOCATION = null;
    private MapLocation LAST_KNOWN_GARDENER_LOCATION = null;
    private boolean settledDown = false;
    static HashSet<MapLocation> localBlacklist = new HashSet<>();
    static HashSet<MapLocation> globalBlacklist = new HashSet<>();
    static int globalBlacklist_index = 0;
    static final int LOCATION_TIMEOUT = 50;                     //Timeout to get to a new location
    static int settleTimer;
    static final int MAX_READ_SETTLE_PTS = 50;                  //Maximum number of settle pts read
    static int MAX_INIT_TREES = 2;                              //Max number of early game trees
    static float MIN_SEPARATION_DIST = 23f;                     //If target is further than nf, start new local farm
    private ArrayList<Direction> current_available_slots = new ArrayList<>();

    //Reads current unit composition
    static int number_gardeners = 0;
    static int number_lumberjacks = 0;
    private int surrounding_lumberjacks = 0;
    static int number_scouts = 0;
    static int number_soldiers = 0;
    static int number_tanks = 0;

    //Spawning constants (Macro control)
    private Direction SPAWN_DIR = null;                         //Reserved spawning slot once in position
    private float BULLET_OVERFLOW = 347;                        //Overflowing bullets, build something!
    static final int SOLDIER_DELAY = 17;          				//Number of turns to spawn a new soldier
    private float SPAWN_SOLDIER_CHANCE = 0.75f;
    private float SOLDIER_TO_TREE_RATIO = 1.23f;                //Max number of soldiers per tree
    static int MIN_SOLDIER = 5;                                 //Minimum number of soldiers
    static int MAX_SOLDIER = 10;                                //Early game max soldiers
    static final int SCOUT_DELAY = 100;   						//Number of turns to spawn a new scout
    private float SPAWN_SCOUT_CHANCE = 0.0f;
    private float SCOUT_TO_TREE_RATIO = 0.25f;                  //Max number of scouts per tree
    static final int TANK_DELAY = 13; 							//Number of turns to spawn a new tank
    private float SPAWN_TANK_CHANCE = 1f;
    private float TANK_TO_TREE_RATIO = 0.25f;                   //Max number of tanks per tree
    private float TANK_DENSITY_THRESHOLD = 0.15f;               //Surrounding density < this before tanks can be spawned
    private float LUMBERJACK_TO_NEUTRAL_RATIO = 0.43f;          //Number of lumbers per tree surrounding gardener
    private float LUMBERJACK_TO_SOLDIER_RATIO = 2f;             //Number of lumbers per soldier
    private final int LUMBERJACK_DELAY = 30;                    //Number of turns to spawn a new lumberjack
    private float NEUTRAL_TREE_HEALTH_THRESHOLD = 750f;         //Total accumulative health of surrounding trees | above which will spawn lumberjacks

    //Movement stuff
    static MapLocation prevLocation = null;                     //Previous location
    static int staying_conut = 0;                               //How many turns spent around there
    static int stuckRounds = 11;                                //How many rounds before I'm stuck
    static int cooldownRounds = 17;
    static int cooldownTimer = 0;                               //Turns to run slug path before reverting to direct pathing
    static float movement_stuck_radius = RobotType.GARDENER.strideRadius/8*stuckRounds;       //Stuck distance for 17 rounds
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean cooldown_active = false;                     //Cooldown period where direct pathing is disabled
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot
    private int timeToTarget = 0;
    private int targetTimer = THE_FINAL_PROBLEM;

    private int SLUG = 3;                                       //How many levels of slugging

    private Direction cooldownDir = null;                       //Try to escape local minimum

    /*
    GardenerPlayer is one of our most extensive pieces of code (2nd longest after BasePlayer) as it controls the critical macro strategy
    Basic logic structure of our gardeners:
        settleDown = can_settle_down();
        if (!settleDown){
            if (!is_target_valid(target)){
                acquire_target();
            }
            else{
                move_to_target();
            }
        }
        else{
            broadcast_locations_for_other_gardeners();
            reserve_slot_for_spawning();
            spam_trees(); //Max 5
        }
        spawnUnits();
    Ofc there's a bunch of additional checks and ad-hoc code in there, but that's about it for our gardeners.
    We experimented with a spade of different farming techniques for gardeners and in the end, settled on individual hexagonal
    flower design in a bigger hexagonal lattice. It provided the best balance between settling speed, space efficiency as well
    as gardener-to-tree ratio.

    Some points of interest in our gardener code:
        --> Short-range/Long-range min density-sensing movement
            --> Scans for local minimum density and moves towards a position that evaluates as such
            --> If stuck, try scanning further to escape local minima
        --> Build-order queue
            --> At high-level plays, mastering the initial build order is critical
            --> Games are frequently decided in the first 100 rounds
        --> Sensing valid building locations
            --> Finer sensing than simply a free circle of radius 3 around gardener

    If you're a participant, probably such considerations have already crossed your mind at some point during the competition.

    A stable gardener code is the single most critical piece of code in this year's tournament (well, aside from getting that
    gardener out from the archons). Personally, our team floundered at the Qualifying tournament due to having unstable gardener
    code >.< they failed to settle down fast enough and thus resulted in a delayed economic buildup.

    So to those teams out there (and future participants reading this), identify critical pieces of code early and stabilize them
    before moving on...
    */
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

        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(3f, rc.getTeam());
        for (RobotInfo r:SURROUNDING_ROBOTS_OWN){
            if (r.type == RobotType.ARCHON){
                SPAWNING_ARCHON_LOCATION = r.location;
                break;
            }
        }
        //todo I'm debugging this...
        debug = false;  //Turn this on for visualizations as to how our gardeners work
    }

    public void startRound() throws GameActionException {
        super.startRound();
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(3f, rc.getTeam());
        if (settledDown){
            rc.broadcast(SUM_SETTLED,rc.readBroadcast(SUM_SETTLED)+1);
        }
        else{
            rc.broadcast(SUM_GARDENERS,rc.readBroadcast(SUM_GARDENERS)+1);
        }
        build_order = getBuildOrder();
        build_queue_empty = true;
        has_built_robot = false;

        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            contactRep();
        }

        //Reset farmer settling counter - distributing this piece of control code provides redundancy (what if our archon was destroyed?)
        if (rc.readBroadcast(CONTROL_FARMER_HEARTBEAT) <= Math.max(0, rc.getRoundNum() - 3)){
            rc.broadcast(CONTROL_FARMER, rc.getID());
            rc.broadcast(AVAILABLE_FARMING_LOCATIONS, 0);
        }
        if (rc.readBroadcast(CONTROL_FARMER) == rc.getID()) {
            rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(new Direction(0f),2f), 0, 0, 0);
            rc.broadcast(AVAILABLE_FARMING_LOCATIONS, 0);
            rc.broadcast(CONTROL_FARMER_HEARTBEAT, rc.getRoundNum());
            if (rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS) > MAX_BLACKLISTED_LOCATIONS){
                rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, 0);
            }
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
        //We were thinking of some sort of flocking behavior for gardeners (not to stray too far away from others)
        for (RobotInfo r:SURROUNDING_ROBOTS_OWN){
            if (r.type == RobotType.GARDENER){
                LAST_KNOWN_GARDENER_LOCATION = r.location;
                break;
            }
        }

        if (LAST_KNOWN_GARDENER_LOCATION != null) if (debug) rc.setIndicatorLine(MY_LOCATION, LAST_KNOWN_GARDENER_LOCATION, 0, 255, 0);

        //Check whether enemy has been engaged!
        if (!enemyEngaged) enemyEngaged = (rc.readBroadcast(ENEMY_ENGAGED) == 1);

        //Read and store global blacklisted locations
        //Fetch global blacklist only once (and as blacklisted positions are only appended - previous entries immutable, we simply get updates)
        int blacklist_size = rc.readBroadcast(BLACKLISTED_FARMING_LOCATIONS);
        System.out.println("Total Blacklisted locations: " + Integer.toString(blacklist_size));
        for (int i = globalBlacklist_index; i < blacklist_size; i++){
            float locX = intToFloatPrecise(rc.readBroadcast(BLACKLISTED_LOCATIONS_X + i * 2));
            float locY = intToFloatPrecise(rc.readBroadcast(BLACKLISTED_LOCATIONS_Y + i * 2));
            globalBlacklist.add(new MapLocation(locX, locY));
            if (debug) rc.setIndicatorDot(new MapLocation(locX, locY), 255, 255, 0);
        }
        globalBlacklist_index = blacklist_size;
    }

    private void run() throws GameActionException {

        //Follow build order
        for (int i=0;i<MAX_BUILD_ORDER;i++){
            if (build_order[i]!=null){
                System.out.println("Building: "+build_order[i]);
                build_queue_empty = false;
                if (forceBuilding(build_order[i])){
                    rc.broadcast(BUILD_ORDER_QUEUE+i, 0);
                }
                break;
            }
        }

        //Cheese dat scout out
        if (CHEESE_SCOUT && build_order[0] == null && build_order[1] == null){
            if (shouldCheeseScout() || rc.readBroadcast(FORCE_SCOUT_CHEESE) > 80) {
                //Reads whether that single lone scout has been spawned to collect bullets from trees (thus we cap at 1 scout per game)
                CHEESE_SCOUT = (rc.readBroadcast(SCOUT_CHEESE_SIGNAL) == 0);
                if (CHEESE_SCOUT){
                    if(forceBuilding(RobotType.SCOUT)){
                        System.out.println("Scout cheeser sent!");
                        rc.broadcast(SCOUT_CHEESE_SIGNAL, 1);
                    }
                }
            }
        }

        //Plant trees
        if (!settledDown){
            if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, 3.01f) && rc.onTheMap(MY_LOCATION, 3f)) current_available_slots = HEXAGONAL_PATTERN;
            //Gardener starts to farm early
            //We relax conditions for first farm to start (expedites economic buildup)
            else if (rc.getTreeCount() == 0) current_available_slots = getBuildSlots(MY_LOCATION, 2);
            else current_available_slots = getBuildSlots(MY_LOCATION, FARMER_MIN_SLOTS);
            if (current_available_slots.size() > 0) {
                //Get most spacious direction - hopefully spawning in this direction will not get units stuck
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
                    SPAWN_DIR = current_available_slots.get(spawn_direction); //We reserve a direction for spawning units
                    current_available_slots.remove(spawn_direction);
                }
            }
        }
        else if (current_available_slots.size() < 1){ //Once settled down, hinge build_slots around spawn_direction (assume hextet of slots are free)
            //Using previously computed SPAWN_DIR to hinge the other 5 slots around reduces bytecode usage as we won't need to
            //test surroundings for build slots again.
            if (SPAWN_DIR != null){
                current_available_slots.clear();
                for (int i=1;i<6;i++){
                    current_available_slots.add(SPAWN_DIR.rotateRightRads(DEG_60*i));
                }
            }
            else { //If SPAWN_DIR is not set, search around for free slots assign SPAWN_DIR
                current_available_slots = getBuildSlots(MY_LOCATION, 2);
                if (current_available_slots.size() > 0){
                    SPAWN_DIR = current_available_slots.get(0);
                    current_available_slots.remove(0);
                }
            }
        }

        //Debug bytecode costs
        System.out.println("Bytes used to compute build locations: "+Integer.toString(Clock.getBytecodeNum()));

        //Debug for spawn direction
        if (SPAWN_DIR != null) System.out.println("SPAWN_DIR: "+Float.toString(SPAWN_DIR.radians));

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

            //Acquire new target if none is currently set
            if (target == null || !isSettleLocationValid(target)){
                System.out.println("Computed target validity: "+Clock.getBytecodeNum());
                target = acquireSettleLocation();
                System.out.println("Acquired target: "+Clock.getBytecodeNum());
            }
            //Move to target location
            if (target == null){
                if (debug) rc.setIndicatorDot(MY_LOCATION, 255, 0, 0);
                gardenerDensityMove(GARDENER_MOVE_GRANULARITY); //Initial movement algo for the first gardener (or if no nearby locations are available)
                System.out.println("Computed density move direction: "+Clock.getBytecodeNum());
            }
            else{
                if (debug) rc.setIndicatorDot(MY_LOCATION, 0, 255, 0);
                moveToSettleLocation(); //Navigate to nearest available location (on that global hexagonal lattice for tree clusters)
                System.out.println("Computed move to target: "+Clock.getBytecodeNum());
            }

            //todo don't settledown if entire grove of trees won't be on the map
            // && rc.onTheMap(MY_LOCATION, 3f)
            //We decided to relax the conditions as planting some trees is better off than delaying planting trees (huge economic falloff)
            if (target == null) {
                if (rc.getTreeCount() == 0) { //If you're the first gardener to start a cluster of trees, ignore spreading out from surrounding gardeners
                    if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS, false)) settledDown = true;
                }
                else if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS, true)) settledDown = true;
            }
        }
        else{
            //Announce locations around itself
            broadcastAvailableSettleLocations();

            //Should I plant trees?
            boolean should_plant_trees = (rc.getRoundNum() > treeTimer);

            //Maintain a basic army before planting too many trees
            if (build_order[0] != null && build_order[1] != null && rc.getRoundNum() < EARLY_GAME){
                //Skip building trees till first 2 soldiers are out :)
                should_plant_trees = false;
            }
            if (spawnSoldierControl()){
                //If I should spawn a lumberjack or soldier or if I've tons of bullets, go build these units first :)
                should_plant_trees = false;
            }
            if (rc.getRoundNum() < EARLY_GAME && SURROUNDING_TREES_OWN.length >= MAX_INIT_TREES && enemyEngaged){
                //Stop planting too many trees early game
                should_plant_trees = false;
            }
            //I have tons of bullets, might as well...
            if (rc.getTeamBullets() > BULLET_OVERFLOW) should_plant_trees = true;

            //Plant trees
            if (should_plant_trees) {
                //Plant trees
                for (Direction dir : current_available_slots) {
                    System.out.println("Build slot at: "+dir);
                    if (rc.canPlantTree(dir) && rand.nextFloat() < PLANT_TREE_CHANCE) {
                        rc.plantTree(dir);
                        treeTimer = rc.getRoundNum() + TREE_DELAY;
                    }
                }
                //Try forcing 5 trees around gardener if no explicit build slots are found
                if (rc.getRoundNum() > treeTimer && SURROUNDING_TREES_OWN.length < 5 && SURROUNDING_TREES_OWN.length > 0) {
                    if (debug && SPAWN_DIR != null)
                        rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(SPAWN_DIR, 3f), 0, 255, 255);
                    Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
                    for (int i = 0; i < 5; i++) {
                        cDir = cDir.rotateRightRads(DEG_60);
                        if (rand.nextFloat() < PLANT_TREE_CHANCE) {
                            System.out.println("Forcing planting of tree at: " + Float.toString(cDir.radians));
                            System.out.println("Radians between spawn_dir and cDir: "+Float.toString(SPAWN_DIR.radiansBetween(cDir)));
                            if (absRad(SPAWN_DIR.radiansBetween(cDir)) >= DEG_60 && rc.canPlantTree(cDir)){
                                rc.plantTree(cDir);
                                treeTimer = rc.getRoundNum() + TREE_DELAY;
                            }
                        }
                    }
                }
            }

            //Water surrounding trees
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
            if (SURROUNDING_ROBOTS_ENEMY.length > 0){   //Respond to counter enemy units
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
            //The ordering of spawning is important as those placed first has priority
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
            if (spawnScoutControl() && !has_built_robot) {  //If you look closely, we pretty much never spawn scouts (but...just in case)
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
        float tot_bullets = 0;
        for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
            tot_bullets += t.containedBullets;
        }
        if (tot_bullets > 40f) return true;
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
                if (rc.getRoundNum() < MID_GAME){
                    if (number_lumberjacks > LUMBERJACK_TO_SOLDIER_RATIO*Math.min(1,number_soldiers)) return false; //Cap lumberjacks below 2*soldier count up till mid game
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
        if (rc.getRoundNum() < MID_GAME &&  !enemyEngaged) return false;
        if (rc.getRoundNum() < soldierTimer){
            if (rc.getTeamBullets() > BULLET_OVERFLOW) return true;
            else return false;
        }
        else{
            if (number_soldiers < MIN_SOLDIER) return true;
            float cur_ratio = (float)number_soldiers/Math.max(1,rc.getTreeCount());
            if (cur_ratio > SOLDIER_TO_TREE_RATIO) return false;
            else{
                if (rc.getRoundNum() < MID_GAME){
                    System.out.println("I'm in mid game with: "+Integer.toString(number_soldiers)+" soldiers");
                    //We spawn more soldiers if the economy holds up (even above the set maximum)
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

    private boolean spawnTankControl(){ //We enforce a sustainable economy before spawning tanks (happens towards late-game)
        if (rc.getTeamBullets() < RobotType.TANK.bulletCost) return false;
        if (rc.getRoundNum() < MID_GAME*2 &&  !enemyEngaged) return false;
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

    private boolean spawnScoutControl(){ //Pretty much never happens cuz SPAWN_SCOUT_CHANCE is 1f :P (as per time of final submission)
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
            if (build_order[i] != null) build_order_empty = false;
        }

        if (build_order_empty) return build_order;

        //todo Vets build order
        //As the build order was evaluated by archons in round 1, we wanted gardeners themselves to further vet the build order
        //based on ground conditions after they were spawned.
        float est_size = ENEMY_ARCHON_LOCATION.distanceTo(MY_LOCATION);

        if (getDensity(MY_LOCATION, 5f, TREES) > FOREST_DENSITY_THRESHOLD){
            System.out.println("DENSE MAP DETECTED");
            //todo stable: lum_sol_sol
            //Left space for fine-tuning the starting build orders but never really gotten around to doing it
            //Extremely difficult to detect the correct build order for maps (limited sense range)
            if (est_size < MAP_SMALL){
                if (build_order[0] != null){
                    build_order[0] = RobotType.LUMBERJACK;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.SOLDIER;
                }
            }
            else if (est_size < MAP_LARGE){
                if (build_order[0] != null){
                    build_order[0] = RobotType.LUMBERJACK;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.SOLDIER;
                }
            }
            else {
                if (build_order[0] != null){
                    build_order[0] = RobotType.LUMBERJACK;
                }
                if (build_order[1] != null){
                    build_order[1] = RobotType.SOLDIER;
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

    public boolean forceBuilding(RobotType robot) throws GameActionException {
        //Customized function for spawning units as gardeners reserves a SPAWN_DIR (faster computation)
        if (SPAWN_DIR != null){
            if (rc.canBuildRobot(robot, SPAWN_DIR)){
                rc.buildRobot(robot, SPAWN_DIR);
                return true;
            }
        }
        return super.forceBuilding(robot);
    }

    //<------------------------- TARGET ACQUISITION -------------------------->
    private boolean isSettleLocationValid(MapLocation loc) throws GameActionException {
        if (loc == null) return false;
        if (debug) rc.setIndicatorDot(loc, 0, 0, 255);
        if (localBlacklist.contains(loc)) return false;
        if (Clock.getBytecodeNum() > BYTE_THRESHOLD) return true;   //Relaxed conditions if bytecode exceeded, better to move than freeze

        if (globalBlacklist.contains(loc)) return false;

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

        int num_settle_pts = Math.min(rc.readBroadcast(AVAILABLE_FARMING_LOCATIONS), MAX_READ_SETTLE_PTS);
        float mindist = 10000000f;
        for (int i = 0; i < num_settle_pts; i++){
            //todo bytecode escape
            //if (Clock.getBytecodeNum() > BYTE_THRESHOLD) continue;
            float locX = intToFloatPrecise(rc.readBroadcast(FARMING_LOCATIONS_X + i * 2));
            float locY = intToFloatPrecise(rc.readBroadcast(FARMING_LOCATIONS_Y + i * 2));
            MapLocation newPt = new MapLocation(locX, locY);
            //ignore locations on blacklist or local blacklist
            //if (blacklist.contains(newPt) || localBlacklist.contains(newPt)){
            if (localBlacklist.contains(newPt) || globalBlacklist.contains(newPt)) continue;
            float dist = newPt.distanceTo(MY_LOCATION);
            if (dist > MIN_SEPARATION_DIST) continue;
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
        if (rc.hasMoved()) return;      //Error, robot has already moved
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
                if (!rc.isCircleOccupied(MY_LOCATION.add(HEX_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS) && rc.onTheMap(MY_LOCATION.add(HEX_DIR[i], rc.getType().bodyRadius + TREE_RADIUS), TREE_RADIUS)) {
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
            if (globalBlacklist.contains(newGardenerLoc)) continue;   //Stop broadcasting if location has been blacklisted
            if (broadcaster.isValidGrid(newGardenerLoc)){ //Imprecise but shouldn't matter much
                if (debug) rc.setIndicatorDot(newGardenerLoc, 0, 255, 0);
                rc.broadcast(FARMING_LOCATIONS_X + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.x));
                rc.broadcast(FARMING_LOCATIONS_Y + 2 * num_settle_pts, floatToIntPrecise(newGardenerLoc.y));
                num_settle_pts += 1;
            }
        }
        rc.broadcast(AVAILABLE_FARMING_LOCATIONS, num_settle_pts);
    }

    //<------------------------- MOVEMENT ALGORITHMS ------------------------->
    private float gardenerSurroundingDensity(MapLocation travelLoc) throws GameActionException {
        float curDensity = 0;
        for (int i=0;i<6;i++){
            //Scan in a hexagonal pattern around the gardener
            MapLocation testLoc = travelLoc.add(new Direction(DEG_60*i), rc.getType().bodyRadius + 1f + EPSILON);
            if (!rc.onTheMap(testLoc.add(new Direction(DEG_60*i), 0.5f), 0.25f)){
                curDensity += 1f;
            }
            else curDensity += getDensity(testLoc, 1f, OBJECTS);
        }
        return curDensity;
    }

    //todo bytecode saving metric
    private float gardenerDensity(MapLocation travelLoc) throws GameActionException {
        return getDensity(travelLoc, 3f, OBJECTS);
    }

    private boolean gardenerDensityMove(int GRANULARITY) throws GameActionException {
        if (cooldown_active) {
            if (cooldownDir == null) {
                //Look further away (long-range density) --> should escape local minimum
                Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION).opposite();
                float curScore = 0f;
                Direction tryDir = null;
                float bestScore = 99999f;
                Direction bestDir = null;
                MapLocation tryLoc = null;
                tryDir = initDir;
                tryLoc = MY_LOCATION.add(tryDir, rc.getType().sensorRadius-2f);
                curScore = getDensity(tryLoc, 2f, OBJECTS);
                if (curScore < bestScore) {
                    bestScore = curScore;
                    bestDir = tryDir;
                }
                for (int i = 1; i < GRANULARITY / 2; i++) {
                    if (Clock.getBytecodeNum() > BYTE_THRESHOLD) continue;
                    tryDir = initDir.rotateRightRads(i * DEG_360 / GRANULARITY);
                    tryLoc = MY_LOCATION.add(tryDir, rc.getType().sensorRadius-2f);
                    curScore = getDensity(tryLoc, 2f, OBJECTS);
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                    }
                    tryDir = initDir.rotateLeftRads(i * DEG_360 / GRANULARITY);
                    tryLoc = MY_LOCATION.add(tryDir, rc.getType().sensorRadius-2f);
                    curScore = getDensity(tryLoc, 2f, OBJECTS);
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
            //if (SPAWNING_ARCHON_LOCATION != null) initDir = SPAWNING_ARCHON_LOCATION.directionTo(MY_LOCATION);
            Direction bestDir = null;
            float bestDist = 0f;
            for (int j = 0; j < 1; j++) {
                float curScore = 0f;
                Direction tryDir = null;
                float bestScore = 99999f;
                float tryDist = (rc.getType().strideRadius) / (float) Math.pow(2, j);
                tryDir = initDir;
                if (rc.canMove(tryDir, tryDist)) {
                    curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, tryDist));
                    if (curScore < bestScore) {
                        bestScore = curScore;
                        bestDir = tryDir;
                        bestDist = tryDist;
                        if (curScore == 0) break;   //Already minimum density direction :)
                    }
                }
                for (int i = 1; i < GRANULARITY / 2; i++) {
                    System.out.println("Each pass byte cost: "+Clock.getBytecodeNum());
                    //if (Clock.getBytecodeNum() > BYTE_THRESHOLD) continue;
                    tryDir = initDir.rotateRightRads(i * DEG_360 / GRANULARITY);
                    if (rc.canMove(tryDir, tryDist)) {
                        curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, tryDist));
                        if (curScore < bestScore) {
                            bestScore = curScore;
                            bestDir = tryDir;
                            bestDist = tryDist;
                            if (curScore == 0) break;   //Already minimum density direction :)
                        }
                    }
                    tryDir = initDir.rotateLeftRads(i * DEG_360 / GRANULARITY);
                    if (rc.canMove(tryDir, tryDist)) {
                        curScore = gardenerSurroundingDensity(MY_LOCATION.add(tryDir, tryDist));
                        if (curScore < bestScore) {
                            bestScore = curScore;
                            bestDir = tryDir;
                            bestDist = tryDist;
                            if (curScore == 0) break;   //Already minimum density direction :)
                        }
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

}