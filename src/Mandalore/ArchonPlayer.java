package Mandalore;

import battlecode.common.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Random;

public class ArchonPlayer extends BasePlayer {

    //Archon specific variables
    static float ARCHON_SIGHT_AREA = 100*(float)Math.PI;
    private int gardenerTimer = 0;
    private boolean can_spawn_gardener;
    private Direction least_dense_direction;
    static float est_size;
    static boolean solo_gardener_start = true;
    static float GARDENER_TO_TREE_RATIO = 0.43f;
    static final int MIN_GARDENERS_EARLY = 2;                   //Min number of early game gardeners
    static final int MIN_GARDENERS = 3;                         //Min number of gardeners
    static float ARCHON_CLEARANCE_RADIUS = 4.05f;
    static float ARCHON_SIGHT_RADIUS = 10f;

    static float SPAWN_GRANULARITY = 24;
    static int ARCHON_BUILD_DIR = 6;

    static int ARCHON_MOVEMENT_GRANULARITY = 12;

    private boolean hasControl = false;
    static int numberSmoothening = 30;                          //Takes a n-turn average count of the robots in case some of them fail to report
    static int number_gardeners = 0;
    static float accum_gardeners = 0;
    static int number_lumberjacks = 0;
    static float accum_lumberjacks = 0;
    static int number_scouts = 0;
    static float accum_scouts = 0;
    static int number_soldiers = 0;
    static float accum_soldiers = 0;
    static int number_tanks = 0;
    static float accum_tanks = 0;

    static final int GARDENER_DELAY = 10;           			//Number of turns to spawn a new gardener
    static final float SPAWN_GARDENER_CHANCE = 1.1f;			//Chance to spawn a gardener
    static float SPAWN_GARDENER_DENSITY_THRESHOLD = 0.90f;		//% Area blocked around archon before it stops spawning gardeners

    private Queue<Integer> smoothened_gardener_count = new ArrayDeque<Integer>();

    public ArchonPlayer(RobotController rc) throws GameActionException {
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
        //Setup scanner range based on robot type
        MOVEMENT_SCANNING_DISTANCE = 4.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
        gardenerTimer = 0;
        //todo I'm debugging this...
        debug = false;

        //Vie for control archon position
        if (rc.readBroadcast(CONTROL_ARCHON) == 0){
            rc.broadcast(CONTROL_ARCHON, rc.getID());

            //Setup build order
            int[] build_list = null;
            int[] sco_sco_sco = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
            int[] sco_sco_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK)};
            int[] sco_sco_sol = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER)};
            int[] sco_lum_sco = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT)};
            int[] sco_lum_sol = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SOLDIER)};
            int[] sco_sol_sol = {robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER)};
            int[] sco_sol_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK)};
            int[] sol_sco_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
            int[] sol_lum_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT)};
            int[] sol_lum_sol = {robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK),robotType(RobotType.SOLDIER)};
            int[] sol_sol_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER),robotType(RobotType.SCOUT)};
            int[] sol_sol_lum = {robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK)};
            int[] lum_sco_sol = {robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER)};
            int[] lum_sol_sol = {robotType(RobotType.LUMBERJACK),robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER)};
            System.out.println("Surrounding foilage density: "+Float.toString(getDensity(MY_LOCATION, ARCHON_SIGHT_RADIUS, TREES)));
            if (getDensity(MY_LOCATION, ARCHON_SIGHT_RADIUS, TREES) > FOREST_DENSITY_THRESHOLD){
                System.out.println("DENSE MAP DETECTED");
                //todo stable: lum_sol_sol
                build_list = sol_lum_sol;
            }
            else{
                System.out.println("Clear Map detected");
                build_list = sol_sol_lum;
            /*if (est_size < MAP_SMALL){
                build_list = sol_sol_lum;
            }
            else if (est_size > MAP_LARGE){
                build_list = sco_sco_sol;
            }*/
            }
            //build_list = sol_sol_lum;
            if (build_list!=null) {
                rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
                rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
                //rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);
            }
            //System.out.println("Build order: " + Integer.toString(build_list[0]) +" "+ Integer.toString(build_list[1]) +" "+ Integer.toString(build_list[2]));
        }

        //Use archon positions to find rough size of map then set early-game strategy
        MY_LOCATION = rc.getLocation();
        MapLocation[] enemyArchonLocs = rc.getInitialArchonLocations(rc.getTeam().opponent());
        MapLocation[] friendlyArchonLocs = rc.getInitialArchonLocations(rc.getTeam());
        float longest_dist = 0f;
        for (int i=0;i<enemyArchonLocs.length;i++){
            for (int j=0;j<friendlyArchonLocs.length;j++){
                float cur_dist = enemyArchonLocs[i].distanceTo(friendlyArchonLocs[j]);
                if (cur_dist > longest_dist) longest_dist = cur_dist;
            }
        }
        est_size = longest_dist;

        //Estimate which corner archon is in
        if (est_size < MAP_SMALL){
            System.out.println("Small Map");
        }
        else if (est_size < MAP_LARGE){
            System.out.println("Medium Map");
        }
        else {
            System.out.println("Large Map");
        }
    }

    public void startRound() throws GameActionException {
        super.startRound();
        if (rc.readBroadcast(CONTROL_ARCHON_HEARTBEAT) < rc.getRoundNum() - 3){
            rc.broadcast(CONTROL_ARCHON, rc.getID());
            hasControl = true;
        }
        if (rc.readBroadcast(CONTROL_ARCHON) == rc.getID()) {
            hasControl = true;
            number_gardeners = rc.readBroadcast(SUM_GARDENERS);
            rc.broadcast(COUNT_GARDENERS, number_gardeners);
            rc.broadcast(SUM_GARDENERS, 0);
            number_lumberjacks = rc.readBroadcast(SUM_LUMBERJACKS);
            rc.broadcast(COUNT_LUMBERJACKS, number_lumberjacks);
            rc.broadcast(SUM_LUMBERJACKS, 0);
            number_scouts = rc.readBroadcast(SUM_SCOUTS);
            rc.broadcast(COUNT_SCOUTS, number_scouts);
            rc.broadcast(SUM_SCOUTS, 0);
            number_soldiers = rc.readBroadcast(SUM_SOLDIERS);
            rc.broadcast(COUNT_SOLDIERS, number_soldiers);
            rc.broadcast(SUM_SOLDIERS, 0);
            number_tanks = rc.readBroadcast(SUM_TANKS);
            rc.broadcast(COUNT_TANKS, number_tanks);
            rc.broadcast(SUM_TANKS, 0);
            rc.broadcast(CONTROL_ARCHON_HEARTBEAT, rc.getRoundNum());
        }
        else {
            hasControl = false;
            number_gardeners = rc.readBroadcast(COUNT_GARDENERS);
            number_lumberjacks = rc.readBroadcast(COUNT_LUMBERJACKS);
            number_scouts = rc.readBroadcast(COUNT_SCOUTS);
            number_soldiers = rc.readBroadcast(COUNT_SOLDIERS);
            number_tanks = rc.readBroadcast(COUNT_TANKS);
        }

        //Get cumulative smoothened number of units
        if (smoothened_gardener_count.size() >= numberSmoothening){
            //todo implement a proper queue now...
            smoothened_gardener_count.poll();
            smoothened_gardener_count.offer(number_gardeners);
        }
        else{
            smoothened_gardener_count.offer(number_gardeners);
        }

        accum_gardeners = 0;
        for (int i:smoothened_gardener_count){
            accum_gardeners += i;
        }

        number_gardeners = (int)Math.ceil(accum_gardeners/smoothened_gardener_count.size());

        System.out.println("Gardeners accum: "+Float.toString(accum_gardeners));
        System.out.println("Gardeners: "+Integer.toString(number_gardeners));
        System.out.println("Lumberjacks: "+Integer.toString(number_lumberjacks));
        System.out.println("Scouts: "+Integer.toString(number_scouts));
        System.out.println("Soldiers: "+Integer.toString(number_soldiers));
        System.out.println("Tanks: "+Integer.toString(number_tanks));

        //Reset farmer settling counter
        rc.broadcast(AVAILABLE_FARMING_LOCATIONS, 0);

        //Debug status of broadcaster
        System.out.println(broadcaster.printBoundaries());
        for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
            int gridCoded = rc.readBroadcast(i);
            if (gridCoded == 0) continue;
            MapLocation targetLoc = broadcaster.channel_to_location(gridCoded);
            if (debug) rc.setIndicatorDot(targetLoc, 255, 0, 0);
            Message curMsg = new Message(rc.readBroadcast(i+1));
            int heartbeat = rc.readBroadcast(i+2);
            if (heartbeat <= Math.max(0, rc.getRoundNum() - PING_LIFESPAN)) continue;
            if (debug) rc.setIndicatorDot(targetLoc, 0, 255, 0);
            System.out.println(curMsg.getRawMsg());
        }

        System.out.println("Overrun byte? "+Integer.toString(Clock.getBytecodeNum()));

        //Taper off gardener production as the game progresses
        GARDENER_TO_TREE_RATIO = 0.25f + (2f/rc.getTreeCount());
    }

    private void run() throws GameActionException {
        boolean can_spawn = false;
        Random rand = new Random(rc.getID());
        can_spawn_gardener = true;
        least_dense_direction = null;
        try {
            //Monitor gardener to tree ratio
            System.out.println("Bullet Trees: "+Integer.toString(rc.getTreeCount()));
            System.out.println("Gardener to trees ratio: "+Float.toString(((float)number_gardeners/Math.max(1f,(float)rc.getTreeCount()))));
            can_spawn_gardener = spawnGardenerControl();

            System.out.println("Gardener Control: "+Boolean.toString(can_spawn_gardener));

            //Only spawn 1 gardener
            if (rc.getRoundNum() < EARLY_GAME && solo_gardener_start){
                System.out.println("Game initializing...");
                System.out.println("Current bullets left: "+Float.toString(rc.getTeamBullets()));
                //if (!hasControl || rc.getTeamBullets() < INITIAL_BULLETS - 80f ) can_spawn_gardener = false;
                if (rc.getTeamBullets() < INITIAL_BULLETS - 80f) can_spawn_gardener = false;
            }

            //Check whether gardener spawn timer has expired
            if (rc.getRoundNum() < gardenerTimer) can_spawn_gardener = false;

            //todo Spawn gardeners if there're no trees!!!
            if (rc.getTreeCount() == 0 && number_gardeners < 1 && rc.getRoundNum() > EARLY_GAME){
                can_spawn_gardener = true;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        //todo micro testing ==> disable gardener spawning
        if (microTest) can_spawn_gardener = false;

        //Calls for help when enemies are nearby
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotRep();
        }

        //Look for nearby enemies or bullets
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
        float nearestdist = 1000000f;
        RobotInfo nearestEnemy = null;
        if (SURROUNDING_ROBOTS_ENEMY.length > 0) {
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);
            if (nearestdist <= ARCHON_MICRO_DIST || nearbyBullets.length > 0) {
                archonCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
            }
        }

        //Check whether current position can spawn units, else move
        try {
            /*for (int i = 0; i < ARCHON_BUILD_DIR; i++) {
                if (can_spawn) break;
                Direction cDir = new Direction(i * (DEG_360/ARCHON_BUILD_DIR));
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(cDir, RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius + EPSILON), 0, 0, 0);
                //if (rc.canBuildRobot(RobotType.GARDENER, cDir)) {
                if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION.add(cDir, RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius + EPSILON), RobotType.GARDENER.bodyRadius)){
                    can_spawn = true;
                    if (debug) rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(cDir, RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius + EPSILON), 0, 255, 0);
                }
                else if (debug) rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(cDir, RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius + EPSILON), 255, 0, 0);
            }*/
            if (getDensity(MY_LOCATION, rc.getType().bodyRadius + 2f + EPSILON, OBJECTS) > FOREST_DENSITY_THRESHOLD) {
                //todo use density-based movement
                System.out.println("Byte Usage prior to movement: "+Clock.getBytecodeNum());
                archonDensityMove(ARCHON_MOVEMENT_GRANULARITY);
                System.out.println("Byte Usage for movement: "+Clock.getBytecodeNum());
                /*if (!rc.hasMoved()) {
                    //Get most spacious direction
                    float density = 999999f;
                    float offset = rand.nextInt(360)*DEG_1;
                    Direction least_dense_direction = null;
                    for(int i=0;i<ARCHON_BUILD_DIR;i++) {
                        Direction cDir = new Direction(absRad(i * (DEG_360/ARCHON_BUILD_DIR)+offset));
                        float eval = getDensity(MY_LOCATION.add(cDir,MOVEMENT_SCANNING_DISTANCE),MOVEMENT_SCANNING_RADIUS, OBJECTS);
                        if (!rc.onTheMap(MY_LOCATION.add(cDir,MOVEMENT_SCANNING_DISTANCE), TEST_ON_MAP_RADIUS)) continue;
                        if(eval<density){
                            density = eval;
                            least_dense_direction = cDir;
                        }
                    }

                    if(least_dense_direction != null) {
                        if (rc.canMove(least_dense_direction)) rc.move(least_dense_direction);
                    }
                }*/
            }
            //todo experimental code!!! take out when submitting if it fails
            /*if(!isSpreadOut(MY_LOCATION, ARCHON_CLEARANCE_RADIUS)){
                RobotInfo[] nearby_farmers = rc.senseNearbyRobots(ARCHON_CLEARANCE_RADIUS, rc.getTeam());
                for (RobotInfo r:nearby_farmers){
                    if (!rc.hasMoved()) {
                        if (r.type == RobotType.GARDENER) {
                            Direction toFarmerOpposite = MY_LOCATION.directionTo(r.location).opposite();
                            for (int i = 0; i < MOVEMENT_GRANULARITY; i++) {
                                Direction tryDir = toFarmerOpposite.rotateRightRads(DEG_1 * 3 * i);
                                if (rc.canMove(tryDir)) {
                                    rc.move(tryDir);
                                    break;
                                }
                                else{
                                    tryDir = toFarmerOpposite.rotateLeftRads(DEG_1 * 3 * i);
                                    if (rc.canMove(tryDir)) {
                                        rc.move(tryDir);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }*/
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Am I skipping turns? :O "+Integer.toString(Clock.getBytecodeNum()));

        System.out.println("Can I spawn a gardener? "+Boolean.toString(can_spawn_gardener));

        //Spawn Code
        if (can_spawn_gardener){
            try {
                if (!spawnGardenerEnemyDirection()) spawnGardenerLeastDense();
                forceBuilding(RobotType.GARDENER);
                //spawnGardenerLeastDense();
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    //<----------------------------- ARCHON MICRO ---------------------------->
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
        return false;
    }

    private boolean spawnGardenerControl(){
        if (est_size < MAP_SMALL){
            //On small maps, prioritize offense
            if (number_gardeners >= 1 && number_soldiers < 3){
                return false;
            }
        }
        if (number_gardeners < MIN_GARDENERS) return true;
        else{
            //Maintain army
            if (number_gardeners >= 1 && number_soldiers < FarmerPlayer.MIN_SOLDIER) return false;
            float cur_gardener_to_tree_ratio = number_gardeners / Math.max(1f, rc.getTreeCount());
            if(cur_gardener_to_tree_ratio > GARDENER_TO_TREE_RATIO) return false;
        }
        return true;
    }

    private boolean spawnGardenerEnemyDirection() throws GameActionException {
        //Attempt to build gardener
        if (!rc.isBuildReady()) return false;

        //Find the closest enemy archon
        float cur_dist = 0f;
        float min_dist = 99999f;
        MapLocation closest_archon = null;
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        for (MapLocation en_loc:enemyArchons){
            cur_dist = en_loc.distanceTo(MY_LOCATION);
            if (cur_dist < min_dist){
                min_dist = cur_dist;
                closest_archon = en_loc;
            }
        }

        //Direction to enemy (and hopefully away from map corners...
        Direction toEnemy = MY_LOCATION.directionTo(closest_archon);

        //Try to spawn gardener in the least dense area in 90deg cone towards enemy
        ArrayList<Direction> build_locations = new ArrayList<Direction>();
        if (rc.canHireGardener(toEnemy)) build_locations.add(toEnemy);
        for (int i=1;i<SPAWN_GRANULARITY;i++){
            Direction curDir = toEnemy.rotateRightRads(DEG_90/SPAWN_GRANULARITY);
            if (rc.canHireGardener(curDir)) build_locations.add(curDir);
            curDir = toEnemy.rotateLeftRads(DEG_90/SPAWN_GRANULARITY);
            if (rc.canHireGardener(curDir)) build_locations.add(curDir);
        }

        Direction optimalDirection = getLeastDenseDirection(build_locations);

        if (optimalDirection != null) {
            System.out.println("Hired Gardener in direction:");
            System.out.println(optimalDirection.radians);
            rc.hireGardener(optimalDirection);
            gardenerTimer = rc.getRoundNum() + GARDENER_DELAY;
            return true;
        }
        return false;
    }

    private boolean spawnGardenerLeastDense() throws GameActionException {
        //Attempt to build gardener
        if (!rc.isBuildReady()) return false;

        //Search for location to build gardener
        ArrayList<Direction> build_locations = new ArrayList<Direction>();
        for(int i=0;i<SPAWN_GRANULARITY*2;i++) {
            Direction cDir = new Direction(absRad(i * (DEG_360/(SPAWN_GRANULARITY*2))));
            if (rc.canHireGardener(cDir)) build_locations.add(cDir);
        }

        Direction optimalDirection = getLeastDenseDirection(build_locations);

        if (optimalDirection != null) {
            System.out.println("Hired Gardener in direction:");
            System.out.println(optimalDirection.radians);
            rc.hireGardener(optimalDirection);
            gardenerTimer = rc.getRoundNum() + GARDENER_DELAY;
            return true;
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
            curDensity += getDensity(testLoc, 1f, OBJECTS);
        }
        return curDensity;
    }

    private float archonSurroundingDensity(MapLocation travelLoc) throws GameActionException {
        float curDensity = 0;
        for (int i=0;i<6;i++){
            //Scan in a hexagonal pattern around the gardener
            MapLocation testLoc = travelLoc.add(new Direction(DEG_60*i), rc.getType().bodyRadius + 1f + EPSILON);
            if (!rc.onTheMap(testLoc, 1f)){
                curDensity += 1;
                continue;
            }
            curDensity += getDensity(testLoc, 3f, OBJECTS);
            //curDensity += gardenerSurroundingDensity(testLoc);
        }
        return curDensity;
    }

    private boolean archonDensityMove(int GRANULARITY) throws GameActionException { //Bounces the gardener around
        Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION);
        float curScore = 0f;
        Direction tryDir = null;
        float bestScore = 99999f;
        Direction bestDir = null;
        for (int i=0;i<GRANULARITY/2;i++){
            tryDir = initDir.rotateRightRads(i*DEG_360/GRANULARITY);
            if (!rc.canMove(tryDir)) continue;
            curScore = archonSurroundingDensity(MY_LOCATION.add(tryDir, rc.getType().strideRadius));
            if (curScore == 0){
                //Trivial case, it's entirely open there :O
                rc.move(tryDir);
                return true;
            }
            if (curScore < bestScore){
                bestScore = curScore;
                bestDir = tryDir;
            }
            tryDir = initDir.rotateLeftRads(i*DEG_360/GRANULARITY);
            if (!rc.canMove(tryDir)) continue;
            curScore = archonSurroundingDensity(MY_LOCATION.add(tryDir, rc.getType().strideRadius));
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
        if (bestDir != null){
            if (rc.canMove(bestDir)){
                rc.move(bestDir);
                return true;
            }
        }
        return false;
    }

    private float getArchonScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
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

    private void archonCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets) {
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

}
