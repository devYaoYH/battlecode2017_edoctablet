package tatooine;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public class FarmerPlayer extends BasePlayer {

    //Gardener specific variables
    static float GARDENER_SIGHT_AREA = 49*(float)Math.PI;
    static boolean spawnedLumberjack;
    static boolean settledDown;
    static int resetTimer;
    static int treeTimer;
    static int soldierTimer;
    static Direction SPAWN_DIR = null;              //Location to start spawning soldiers/troops
    static float FARMER_CLEARANCE_RADIUS = 4f;	    //Distance to be away from other farmers before starting farm
    static final int GARDENER_STUCK_TIMER = 17;					//After n turn, just spawn a lumberjack already!
    static ArrayList<Direction> current_available_slots = new ArrayList<>();

    static final int SCOUT_OBSOLETE_LIMIT = 2500;				//By which round the scout becomes obsolete
    static float SCOUT_SPAWN_CHANCE = 0.45f;
    static final int SOLDIER_DELAY = 50;          				//Number of turns to spawn a new soldier
    static final int SCOUT_DELAY = 150; 						//Number of turns to spawn a new scout

    private Direction moveDir = randomDirection();

    public FarmerPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer(){
        MOVEMENT_SCANNING_DISTANCE = 3.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
        SPHERE_REPULSION = 4.04f;
        SPHERE_ATTRACTION = 6.7f;
    }

    private void run(){
        Random rand = new Random(rc.getID());
        boolean emergency = false;

        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        //Macro game adaptations to perceived map size && round number
        if (rc.getRoundNum() > EARLY_GAME){
            //Decaying chance to spawn scouts ==> late game less scouts
            System.out.println("Scout spawn chance:");
            SCOUT_SPAWN_CHANCE *= (float)(SCOUT_OBSOLETE_LIMIT-rc.getRoundNum()+(0.95f*(rc.getRoundNum()-EARLY_GAME)))/(float)(SCOUT_OBSOLETE_LIMIT-EARLY_GAME);
            System.out.println(SCOUT_SPAWN_CHANCE);
        }

        //Debug settling down
        System.out.println("Have I settled? "+Boolean.toString(settledDown));
        System.out.println("Max slots around me: "+Integer.toString(Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1)));

        //Activate emergency mode when there's enemies about...
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
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
            treeTimer = 0;
            soldierTimer = 0;//When to spawn the next soldier
            settledDown = false; //Will be set to true if a suitable space can be found
        }
        else{
            timeLived += 1;
        }

        try {
            if (settledDown) current_available_slots = getBuildSlots(MY_LOCATION, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1));
            else{
                if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, 3.01f) && rc.onTheMap(MY_LOCATION, 3f)) current_available_slots = HEXAGONAL_PATTERN;
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
        } catch (Exception e) {
            System.out.println("Gardener fails to get surrounding build slots");
            e.printStackTrace();
        }

        //Debug for spawn direction
        if (SPAWN_DIR != null) System.out.println("SPAWN_DIR: "+Float.toString(SPAWN_DIR.radians));

        //Check for early game starts
        try {
            if (SPAWN_DIR != null) {
                //Follow build order
                for (int i=0;i<MAX_BUILD_ORDER;i++){
                    int cur_build = rc.readBroadcast(i+BUILD_ORDER_QUEUE);
                    System.out.println("Current requested robot: "+Integer.toString(cur_build));
                    RobotType requested_robot = robotType(cur_build);
                    if (requested_robot != null){
                        if (rc.canBuildRobot(requested_robot, SPAWN_DIR)){
                            rc.buildRobot(requested_robot, SPAWN_DIR);
                            rc.broadcast(i+BUILD_ORDER_QUEUE, 0);
                            System.out.println("Built robot: "+Integer.toString(cur_build)+" as part of build queue");
                        }
                    }
                    if (cur_build != 0) break;
                }
            }
            else {
                //Force out a spawn direction
                for (int i=0;i<SENSOR_GRANULARITY;i++){
                    Direction cDir = new Direction(i*DEG_360/SENSOR_GRANULARITY);
                    if (rc.canBuildRobot(RobotType.SCOUT, cDir)){
                        SPAWN_DIR = cDir;
                        break;
                    }
                }
                if (SPAWN_DIR != null) {
                    for (int i = 0; i < MAX_BUILD_ORDER; i++) {
                        int cur_build = rc.readBroadcast(BUILD_ORDER_QUEUE);
                        System.out.println("Current requested robot: "+Integer.toString(cur_build));
                        RobotType requested_robot = robotType(cur_build);
                        if (requested_robot != null) {
                            if (rc.canBuildRobot(requested_robot, SPAWN_DIR)) {
                                rc.buildRobot(requested_robot, SPAWN_DIR);
                                rc.broadcast(i + BUILD_ORDER_QUEUE, 0);
                                System.out.println("Built robot: "+Integer.toString(cur_build)+" as part of build queue");
                            }
                        }
                        if (cur_build != 0) break;
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
                else if (rc.getRoundNum() - GARDENER_STUCK_TIMER > ROBOT_SPAWN_ROUND && SURROUNDING_TREES_NEUTRAL.length > 0) {
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
                            if (rc.canPlantTree(cDir) && rc.getRoundNum() > treeTimer && rand.nextFloat() < PLANT_TREE_CHANCE){
                                System.out.println("Forcing planting of tree at: "+Float.toString(cDir.radians));
                                float angle_diff = absRad(cDir.radians - SPAWN_DIR.radians);
                                if (angle_diff > DEG_180) angle_diff = DEG_360-angle_diff;
                                if (angle_diff >= DEG_60){
                                    rc.plantTree(cDir);
                                    treeTimer = rc.getRoundNum() + TREE_DELAY;
                                }
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

            if (rc.getRoundNum() >= soldierTimer && SPAWN_DIR != null){
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

    //<---------------------------- GARDENER MICRO --------------------------->
    private void gardenerMove(int num_attempts){ //Bounces the gardener around
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
        return;
    }

}
