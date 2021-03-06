package tatooine;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public class ArchonPlayer extends BasePlayer {

    //Archon specific variables
    static float ARCHON_SIGHT_AREA = 100*(float)Math.PI;
    static int gardenerTimer;
    static boolean can_spawn_gardener;
    static Direction least_dense_direction;
    static float est_size;
    static boolean solo_gardener_start = false;
    static float GARDENER_TO_TREE_RATIO = 0.50f;
    static float ARCHON_CLEARANCE_RADIUS = 4.05f;
    static float ARCHON_SIGHT_RADIUS = 10f;
    static float TEST_ON_MAP_RADIUS = 0.25f;

    static int number_gardeners = 0;
    static int number_lumberjacks = 0;
    static int number_scouts = 0;
    static int number_soldiers = 0;
    static int number_tanks = 0;

    static final int GARDENER_DELAY = 20;           			//Number of turns to spawn a new gardener
    static final float SPAWN_GARDENER_CHANCE = 1.1f;			//Chance to spawn a gardener
    static float SPAWN_GARDENER_DENSITY_THRESHOLD = 0.90f;		//% Area blocked around archon before it stops spawning gardeners

    public ArchonPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer() throws GameActionException {
        //Setup scanner range based on robot type
        MOVEMENT_SCANNING_DISTANCE = 4.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;

        //Vie for control archon position
        if (rc.readBroadcast(CONTROL_ARCHON) == 0){
            rc.broadcast(CONTROL_ARCHON, rc.getID());
        }

        //Use archon positions to find rough size of map then set early-game strategy
        MY_LOCATION = rc.getLocation();
        est_size = ENEMY_ARCHON_LOCATION.distanceTo(MY_LOCATION);

        //Estimate which corner archon is in


        if (est_size < MAP_SMALL){
            rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
        }
        else if (est_size < MAP_LARGE){
            rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
            rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
        }
        else {
            rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
            rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
        }

        //Setup build order
        //Check forest density
        float area_trees = 0f;
        int[] build_list = null;
        int[] sco_sco_sco = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
        int[] sco_sco_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK)};
        int[] sco_lum_sco = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT)};
        int[] sco_lum_sol = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SOLDIER)};
        int[] sco_sol_sol = {robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER)};
        int[] sco_sol_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK)};
        int[] sol_sco_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
        int[] sol_lum_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT)};
        int[] sol_sol_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.SOLDIER),robotType(RobotType.SCOUT)};
        int[] lum_sco_sol = {robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER)};
        System.out.println("Surrounding foilage density: "+Float.toString(getDensity(MY_LOCATION, ARCHON_SIGHT_RADIUS, TREES)));
        if (getDensity(MY_LOCATION, ARCHON_SIGHT_RADIUS, TREES) > FOREST_DENSITY_THRESHOLD){
            System.out.println("DENSE MAP DETECTED");
            build_list = lum_sco_sol;
        }
        else{
            System.out.println("Clear Map detected");
            build_list = sol_sol_sco;
            if (est_size < MAP_SMALL){
                build_list = sol_sol_sco;
            }
            else if (est_size > MAP_LARGE){
                build_list = sco_sol_sol;
            }
        }
        if (build_list!=null) {
            rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
            rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
            rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);
        }
        System.out.println("Build order: " + Integer.toString(build_list[0]) +" "+ Integer.toString(build_list[1]) +" "+ Integer.toString(build_list[2]));

                    /*int[] build_list = {1,3,1};
                    rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
                    rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
                    rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);*/

    }

    public void startRound() throws GameActionException {
        super.startRound();
        if (rc.readBroadcast(CONTROL_ARCHON_HEARTBEAT) < rc.getRoundNum() - 3){
            rc.broadcast(CONTROL_ARCHON, rc.getID());
        }
        if (rc.readBroadcast(CONTROL_ARCHON) == rc.getID()) {
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
            number_gardeners = rc.readBroadcast(COUNT_GARDENERS);
            number_lumberjacks = rc.readBroadcast(COUNT_LUMBERJACKS);
            number_scouts = rc.readBroadcast(COUNT_SCOUTS);
            number_soldiers = rc.readBroadcast(COUNT_SOLDIERS);
            number_tanks = rc.readBroadcast(COUNT_TANKS);
        }
        System.out.println("Gardeners: "+Integer.toString(number_gardeners));
        System.out.println("Lumberjacks: "+Integer.toString(number_lumberjacks));
        System.out.println("Scouts: "+Integer.toString(number_scouts));
        System.out.println("Soldiers: "+Integer.toString(number_soldiers));
        System.out.println("Tanks: "+Integer.toString(number_tanks));
    }

    private void run() throws GameActionException {
        boolean can_spawn = false;
        Random rand = new Random(rc.getID());
        can_spawn_gardener = true;
        least_dense_direction = null;
        if (!isSet) {
            visitedArr = new ArrayList<MapLocation>();
            isSet = true;
            patience = 0;
        }

        try {
            //Only spawn 1 gardener
            if (rc.getRoundNum() < EARLY_GAME && solo_gardener_start){
                System.out.println("Game initializing...");
                System.out.println("Current bullets left: "+Float.toString(rc.getTeamBullets()));
                if (rc.getTeamBullets() < INITIAL_BULLETS-80) can_spawn_gardener = false;
            }
            //Check for scout rush in progress
            if (rc.getRoundNum() < EARLY_GAME) {
                if (rc.readBroadcast(SCOUT_RUSH_DETECTED) == 1) can_spawn_gardener = false;
                if (rc.getRoundNum() > GAME_INITIALIZE_ROUND) {
                    for (int i = 0; i < MAX_BUILD_ORDER; i++) {
                        if (rc.readBroadcast(i + BUILD_ORDER_QUEUE) != 0) can_spawn_gardener = false;
                    }
                }
            }

            //Check whether there's request to reserve bullets
            int gardener_need_bullet = rc.readBroadcast(RESERVE_BULLETS);
            int heartbeat = rc.readBroadcast(RESERVE_BULLETS+1);
            if (gardener_need_bullet == 1 && heartbeat > rc.getRoundNum()-3) {
                can_spawn_gardener = false;
            }

            //Check whether gardener spawn timer has expired
            if (rc.getRoundNum() < gardenerTimer) can_spawn_gardener = false;

            //Monitor gardener to tree ratio
            System.out.println("Bullet Trees: "+Integer.toString(rc.getTreeCount()));
            System.out.println("Gardener to trees ratio: "+Float.toString(((float)number_gardeners/(float)rc.getTreeCount())));
            if (rc.getTreeCount() > 0){
                if (((float)number_gardeners/(float)rc.getTreeCount())>GARDENER_TO_TREE_RATIO) can_spawn_gardener = false;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        if (!isSet){
            gardenerTimer = 0;
            isSet = true;
        }

        //Calls for help when enemies are nearby
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
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
            for (int i = 0; i < SEARCH_GRANULARITY; i++) {
                if (can_spawn) break;
                Direction cDir = new Direction(i * (DEG_360/SEARCH_GRANULARITY));
                if (rc.canBuildRobot(RobotType.GARDENER, cDir)) {
                    can_spawn = true;
                }
            }
            if (!can_spawn) {
                if (!rc.hasMoved()) {
                    //Get most spacious direction
                    float density = 999999f;
                    float offset = rand.nextInt(360)*DEG_1;
                    Direction least_dense_direction = null;
                    for(int i=0;i<SEARCH_GRANULARITY;i++) {
                        Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
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
                }
            }
            //todo experimental code!!! take out when submitting if it fails
            if(!isSpreadOut(MY_LOCATION, ARCHON_CLEARANCE_RADIUS)){
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
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Am I skipping turns? :O "+Integer.toString(Clock.getBytecodeNum()));

        //Spawn Code
        if (can_spawn_gardener){

            try {
                //Attempt to build gardener
                Direction[] build_locations = new Direction[SEARCH_GRANULARITY];
                int conut = 0;
                if (rc.isBuildReady()) {
                    //Search for location to build gardener
                    float offset = rand.nextInt(360)*DEG_1;
                    for(int i=0;i<SEARCH_GRANULARITY;i++) {
                        Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
                        if (rc.canHireGardener(cDir)) {
                            build_locations[conut] = cDir;
                            conut++;
                        }
                    }
                }

                //Get most spacious direction
                float density = 999999f;
                Direction spawn_direction = null;
                for(int i=0;i<conut;i++){
                    float eval = getDensity(MY_LOCATION.add(build_locations[i],MOVEMENT_SCANNING_DISTANCE),MOVEMENT_SCANNING_RADIUS, TREES);
                    if (!rc.onTheMap(MY_LOCATION.add(build_locations[i],MOVEMENT_SCANNING_DISTANCE), TEST_ON_MAP_RADIUS)) continue;
                    if(eval<density){
                        density = eval;
                        spawn_direction = build_locations[i];
                    }
                }
                if(spawn_direction != null) {
                    System.out.println("Hired Gardener in direction:");
                    System.out.println(spawn_direction.radians);
                    System.out.println(density);
                    rc.hireGardener(spawn_direction);
                    gardenerTimer = rc.getRoundNum() + GARDENER_DELAY;
                }

                if (rc.getRoundNum() <= 5){
                    //Update known map bounds
                    rc.broadcast(0, 0);
                    rc.broadcast(1,  0);
                    rc.broadcast(2, 600);
                    rc.broadcast(3, 600);
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    //<----------------------------- ARCHON MICRO ---------------------------->
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
