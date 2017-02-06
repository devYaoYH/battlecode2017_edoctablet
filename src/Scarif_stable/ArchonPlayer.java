package Scarif_stable;

import battlecode.common.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

public class ArchonPlayer extends BasePlayer {

    //Behavioural switches
    private boolean hasControl = false;
    private boolean can_spawn_gardener;
    static boolean solo_gardener_start = true;
    private boolean enemyEngaged = false;
    static boolean underAttack = false;

    //Initial estimate of map size based on archon-archon distance
    static float est_size;

    //Controls for spawning gardeners
    private int gardenerTimer = 0;
    static final int GARDENER_DELAY = 17;           			//Number of turns to spawn a new gardener
    static final int MIN_GARDENERS = 2;                         //Min number of gardeners
    static float GARDENER_TO_TREE_RATIO = 0.43f;

    //Granularity - How fine a search to make around the robot
    static int SLUG = 3;                                        //Stepping for slug pathing
    static int ARCHON_MOVEMENT_GRANULARITY = 24;
    static float SPAWN_GRANULARITY = 24;

    //Keeping track of unit counts
    static int numberSmoothening = 30;                          //Takes a n-turn average count of the robots in case some of them fail to report
    static int number_gardeners = 0;
    static int number_gardeners_settled = 0;
    static float accum_gardeners = 0;
    static int number_lumberjacks = 0;
    static float accum_lumberjacks = 0;
    static int number_scouts = 0;
    static float accum_scouts = 0;
    static int number_soldiers = 0;
    static float accum_soldiers = 0;
    static int number_tanks = 0;
    static float accum_tanks = 0;
    private Queue<Integer> smoothened_gardener_count = new ArrayDeque<Integer>();
    private Queue<Integer> smoothened_settled_count = new ArrayDeque<Integer>();

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
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        gardenerTimer = 0;
        //todo I'm debugging this...
        debug = false;  //Turn this switch on for some nice lines and dots :)

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
            System.out.println("Surrounding foilage density: "+Float.toString(getDensity(MY_LOCATION, rc.getType().sensorRadius, TREES)));
            if (getDensity(MY_LOCATION, rc.getType().sensorRadius, TREES) > FOREST_DENSITY_THRESHOLD){
                System.out.println("DENSE MAP DETECTED");
                //todo stable: lum_sol_sol
                build_list = sol_lum_sol;
            }
            else{
                System.out.println("Clear Map detected");
                build_list = sol_sol_lum;
            }
            if (build_list!=null) {
                rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
                rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
                System.out.println("Build order: " + Integer.toString(build_list[0]) +" "+ Integer.toString(build_list[1]));
            }
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

        //Announces map size to other robots
        rc.broadcast(MAP_SIZE,floatToIntPrecise(est_size));

        //Estimate map size
        if (est_size < MAP_SMALL){
            System.out.println("Small Map");
        }
        else if (est_size < MAP_LARGE){
            System.out.println("Medium Map");
        }
        else {
            System.out.println("Large Map");
        }

        //initializes blacklisted locations counter for gardener settling
        rc.broadcast(BLACKLISTED_FARMING_LOCATIONS, 0);

    }

    public void startRound() throws GameActionException {
        super.startRound();
        underAttack = false;

        if (rc.readBroadcast(CONTROL_ARCHON_HEARTBEAT) < rc.getRoundNum() - 3){ //If the heartbeat is outdated, replace with current robot (I have control)
            rc.broadcast(CONTROL_ARCHON, rc.getID());
            hasControl = true;
        }
        if (rc.readBroadcast(CONTROL_ARCHON) == rc.getID()) {   //Update counters being the control archon
            hasControl = true;
            number_gardeners = rc.readBroadcast(SUM_GARDENERS);
            rc.broadcast(COUNT_GARDENERS, number_gardeners);
            rc.broadcast(SUM_GARDENERS, 0);
            number_gardeners_settled = rc.readBroadcast(SUM_SETTLED);
            rc.broadcast(COUNT_SETTLED, number_gardeners_settled);
            rc.broadcast(SUM_SETTLED, 0);
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
        else {  //Simply read off what other archons have already computed
            hasControl = false;
            number_gardeners = rc.readBroadcast(COUNT_GARDENERS);
            number_gardeners_settled = rc.readBroadcast(COUNT_SETTLED);
            number_lumberjacks = rc.readBroadcast(COUNT_LUMBERJACKS);
            number_scouts = rc.readBroadcast(COUNT_SCOUTS);
            number_soldiers = rc.readBroadcast(COUNT_SOLDIERS);
            number_tanks = rc.readBroadcast(COUNT_TANKS);
        }

        /*
        As robots sometimes run over bytecode limit, they fail to report in their count before the turn ends
        This led to an over-producing of certain units (gardeners) and overcrowding which limited their capability
        to settle down and start farms (bad early game econs == gg)

        Intense hacking usually amounts to all best-practices being thrown out of the window
        This was a near-last minute fix so we didn't encapsulate the function (spaghetti yummy!)
        */
        //Get cumulative smoothened number of units
        if (smoothened_gardener_count.size() >= numberSmoothening){ //For those unsettled gardeners (still moving about)
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

        //Get cumulative smoothened number of units
        if (smoothened_settled_count.size() >= numberSmoothening){  //For settled gardeners (already starting a nice hexagonal farm :)
            //todo implement a proper queue now...
            smoothened_settled_count.poll();
            smoothened_settled_count.offer(number_gardeners_settled);
        }
        else{
            smoothened_settled_count.offer(number_gardeners_settled);
        }

        accum_gardeners = 0;
        for (int i:smoothened_settled_count){
            accum_gardeners += i;
        }

        number_gardeners_settled = (int)Math.ceil(accum_gardeners/smoothened_settled_count.size());

        System.out.println("Gardeners accum: "+Float.toString(accum_gardeners));
        System.out.println("Gardeners: "+Integer.toString(number_gardeners));
        System.out.println("Lumberjacks: "+Integer.toString(number_lumberjacks));
        System.out.println("Scouts: "+Integer.toString(number_scouts));
        System.out.println("Soldiers: "+Integer.toString(number_soldiers));
        System.out.println("Tanks: "+Integer.toString(number_tanks));

        /*
        Our farmers broadcast locations around itself for other farmers to quickly settle down and start farms
        We dubbed this version of farmers steroidal...
        */
        //Reset farmer settling counter
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

        /*
        Robots share a common messaging queue to transmit a variety of messages to others
        This section is simply for debugging:
        Green   - Valid message
        Red     - Message timed-out
        */
        //Debug status of broadcaster
        System.out.println(broadcaster.printBoundaries());
        for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
            int gridCoded = rc.readBroadcast(i);
            if (gridCoded == 0) continue;
            MapLocation targetLoc = broadcaster.channel_to_location(gridCoded);
            if (debug) rc.setIndicatorDot(targetLoc, 255, 0, 0);
            Message curMsg = new Message(rc.readBroadcast(i+1));
            //If gardeners are under attack
            if (curMsg.getMessageType() == Message.CONTACT_REP) underAttack = true;
            int heartbeat = rc.readBroadcast(i+2);
            if (heartbeat <= Math.max(0, rc.getRoundNum() - PING_LIFESPAN)) continue;
            if (debug) rc.setIndicatorDot(targetLoc, 0, 255, 0);
            System.out.println(curMsg.getRawMsg());
        }

        System.out.println("Overrun byte? "+Integer.toString(Clock.getBytecodeNum()));

        //Checks whether enemy has been engaged!
        if (!enemyEngaged) enemyEngaged = (rc.readBroadcast(ENEMY_ENGAGED) == 1);

        /*
        Mathematical hueristic: 1/5 (gardener:tree base rate) + 0.03 (allowance) + 2/num_trees (always have 2 excess gardeners for redundancy)
        */
        //Taper off gardener production as the game progresses
        GARDENER_TO_TREE_RATIO = 0.23f + (2f/(float)Math.max(1,rc.getTreeCount()));
        System.out.println("Current ratio: "+GARDENER_TO_TREE_RATIO);

        /*
        Towards the end of the competition as unit speeds were balanced ever slower, VP-wins were more tenable
        Thus last-minute hacked strategy:
            if map is so big that enemy soldiers can't traverse one end to the other before we can dump everything into VP
                stop unit production
                win! :)
        */
        //Test suitability for economic win!
        float numTurns = est_size/RobotType.SOLDIER.strideRadius;
        if (rc.getRoundNum() > MID_GAME && rc.getVictoryPointCost()*(1000-rc.getTeamVictoryPoints())/numTurns < rc.getTreeCount()){
            if (!underAttack) rc.broadcast(ECONOMIC_WIN, 1);
            else rc.broadcast(ECONOMIC_WIN, 0);
        }
        else{
            rc.broadcast(ECONOMIC_WIN, 0);
        }

    }

    private void run() throws GameActionException {
        can_spawn_gardener = true;
        try {
            //Monitor gardener to tree ratio
            System.out.println("Bullet Trees: "+Integer.toString(rc.getTreeCount()));
            System.out.println("Gardener to trees ratio: "+Float.toString(((float)number_gardeners/Math.max(1f,(float)rc.getTreeCount()))));
            can_spawn_gardener = spawnGardenerControl();    //Macro controls are abstracted from main logic for clarity

            System.out.println("Gardener Control: "+Boolean.toString(can_spawn_gardener));

            //Only spawn 1 gardener
            if (rc.getRoundNum() < EARLY_GAME && solo_gardener_start){
                System.out.println("Game initializing...");
                System.out.println("Current bullets left: "+Float.toString(rc.getTeamBullets()));
                if (rc.getTeamBullets() < INITIAL_BULLETS - 80f) can_spawn_gardener = false;
            }

            //Check whether gardener spawn timer has expired
            if (rc.getRoundNum() < gardenerTimer) can_spawn_gardener = false;

            //Spawn gardeners if there're no trees!!!
            if (rc.getTreeCount() == 0 && number_gardeners < 1 && rc.getRoundNum() > EARLY_GAME){
                can_spawn_gardener = true;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        /*
        We created a map with 50 soldiers on each side to test soldier micro strategies against each other :D
        */
        //todo micro testing ==> disable gardener spawning
        if (microTest) can_spawn_gardener = false;

        //Calls for help when enemies are nearby
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
            System.out.println("ENEMY AT THE GATES!!!");
            spotRep();  //Broadcaster system, refer to BasePlayer
        }

        //Look for nearby enemies or bullets and enter combat state
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
            if (rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, rc.getType().bodyRadius + 2f + EPSILON)){
                //todo use density-based movement
                System.out.println("Byte Usage prior to movement: "+Clock.getBytecodeNum());
                if (!archonDensityMove(ARCHON_MOVEMENT_GRANULARITY)) jiggleArchon();
                System.out.println("Byte Usage for movement: "+Clock.getBytecodeNum());
            }
            else jiggleArchon();
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Am I skipping turns? :O "+Integer.toString(Clock.getBytecodeNum()));

        System.out.println("Can I spawn a gardener? "+Boolean.toString(can_spawn_gardener));

        /*
        Try to spawn in a 90degree cone towards enemy (hopefully this avoids map corners)
        If not, force a spawn (better than not spawning)
        */
        //Spawn Code
        if (can_spawn_gardener){
            try {
                if (!spawnGardenerEnemyDirection()) forceBuilding(RobotType.GARDENER);
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    //<----------------------------- ARCHON MICRO ---------------------------->
    private boolean spawnGardenerControl(){
        //During MID_GAME, maintain a standing army before spawning too many gardeners
    	if (rc.getRoundNum() < 400 && rc.getRoundNum() > 100 && number_soldiers < GardenerPlayer.MIN_SOLDIER && number_gardeners != 0){
    		return false;
    	}
        //Go econ if we have a decent economy
        if (est_size < MAP_SMALL && rc.getTeamBullets() < 300){
            //On small maps, prioritize offense
            if (enemyEngaged && number_gardeners >= 1 && number_soldiers < 3){
                return false;
            }
        }
        //Upkeep minimal number of gardeners
        if ((number_gardeners_settled == 0 || rc.getTreeCount() == 0) && number_gardeners < MIN_GARDENERS) return true;
        else{
            //Maintain army
            if (enemyEngaged && number_gardeners >= 1 && number_soldiers < GardenerPlayer.MIN_SOLDIER) return false;
            float cur_gardener_to_tree_ratio = (number_gardeners+number_gardeners_settled) / Math.max(1f, rc.getTreeCount());
            if(cur_gardener_to_tree_ratio > GARDENER_TO_TREE_RATIO) return false;
        }
        return true;
    }

    private boolean spawnGardenerEnemyDirection() throws GameActionException {
        if (imminentEconomicWin) return false;
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

        if (closest_archon == null) closest_archon = ENEMY_ARCHON_LOCATION;

        //Direction to enemy (and hopefully away from map corners...
        /*
        Spawning behind the archon as it moves away from nearby farmers was a strategy we saw employed by many teams towards the end
        But we decided that our gardener spacing would avoid the issue of archon getting stuck and being unable to spawn gardeners
        so we decided not to put our archon at risk by moving it towards the enemy...
        */
        //todo spawn behind you as the archon moves?
        Direction toEnemy = MY_LOCATION.directionTo(closest_archon);

        //Try to spawn gardener in the least dense area in 90deg cone towards enemy
        ArrayList<Direction> build_locations = new ArrayList<Direction>();
        if (rc.canHireGardener(toEnemy)) build_locations.add(toEnemy);
        for (int i=1;i<SPAWN_GRANULARITY;i++){
            Direction curDir = toEnemy.rotateRightRads(i*DEG_90/SPAWN_GRANULARITY);
            if (rc.canHireGardener(curDir)) build_locations.add(curDir);
            curDir = toEnemy.rotateLeftRads(i*DEG_90/SPAWN_GRANULARITY);
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

    //Density of all robots
    public float density_robots(MapLocation center, float radius, Team SELECTOR){
        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, SELECTOR);
        float robots_area = 0;
        for (RobotInfo bot:robots){
            //calculates partial area inside scan radius
            float r = bot.getRadius();
            /*
            Archons treat the space around gardeners as occupied so as to avoid obstructing gardener unit production/farming
            */
            //Give gardeners a wide berth
            if (bot.type == RobotType.GARDENER) r = bot.getRadius()*3;  //Main line for overriding base function

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
                //todo gives double weighting to avoid gardeners!
                if (bot.type == RobotType.GARDENER) robots_area += intersectionArea;
            }
        }
        return robots_area / (radius*radius*DEG_180);
    }

    //Function gets the space occupied by objects around the final archon location given intended move direction
    private float archonSurroundingDensity(Direction tryDir) throws GameActionException {
        MapLocation testLoc = MY_LOCATION.add(tryDir, rc.getType().sensorRadius/2);
        if (!rc.onTheMap(testLoc, 1f)){
            return 1f;
        }
        return getDensity(testLoc, rc.getType().sensorRadius/2, OBJECTS);
    }

    //Evaluates the density around the archon's numerous possible movement locations and picks the least dense one
    private boolean archonDensityMove(int GRANULARITY) throws GameActionException {
        Direction initDir = MY_LOCATION.directionTo(ENEMY_ARCHON_LOCATION);
        float curScore = 0f;
        Direction tryDir = null;
        float bestScore = 99999f;
        Direction bestDir = null;
        for (int i=0;i<GRANULARITY/2;i++){
            //todo bytecode limit
            if (Clock.getBytecodeNum() > 15000) return false;
            tryDir = initDir.rotateRightRads(i*DEG_360/GRANULARITY);
            if (rc.canMove(tryDir)) {
                curScore = archonSurroundingDensity(tryDir);
                System.out.println(curScore + "@" + tryDir);
                if (debug)
                    rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(tryDir, rc.getType().strideRadius), (int) Math.min(255, curScore * 255), 255, (int) Math.min(255, curScore * 255));
                if (curScore < bestScore) {
                    bestScore = curScore;
                    bestDir = tryDir;
                }
            }
            tryDir = initDir.rotateLeftRads(i*DEG_360/GRANULARITY);
            if (rc.canMove(tryDir)) {
                curScore = archonSurroundingDensity(tryDir);
                System.out.println(curScore + "@" + tryDir);
                if (debug)
                    rc.setIndicatorLine(MY_LOCATION, MY_LOCATION.add(tryDir, rc.getType().strideRadius), (int) Math.min(255, curScore * 255), 255, (int) Math.min(255, curScore * 255));
                if (curScore < bestScore) {
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

    /*
    Through extensive testing, we found out that random motion is critical to game performance! :O
    Without this function, on tight maps, our archon couldn't spawn gardeners
    With the addition of a jiggling function, after a while, the archon would happen upon a location able to spawn gardeners
    */
    //Randomly jiggles the archon about
    private void jiggleArchon() throws GameActionException {
        Direction initDir = new Direction(rand.nextFloat()*DEG_360);
        float tryDist;
        for (int j=0;j<SLUG;j++) {
            tryDist = rc.getType().strideRadius/(float)Math.pow(2,SLUG);
            for (int i = 0; i < ARCHON_MOVEMENT_GRANULARITY; i++) {
                Direction tryDir;
                if (i%2 == 0) tryDir = initDir.rotateRightRads(i*DEG_360/ARCHON_MOVEMENT_GRANULARITY);
                else tryDir = initDir.rotateLeftRads(i*DEG_360/ARCHON_MOVEMENT_GRANULARITY);
                if (rc.canMove(tryDir, tryDist)){
                    rc.move(tryDir, tryDist);
                    return;
                }
            }
        }
    }

    //Scoring hueristic for archon's move locations whilst in combat mode
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

    //Archon's combat movement function (avoids enemies/bullets)
    private void archonCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets) {
        MapLocation myLoc = rc.getLocation();
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();

        //Process nearby enemies
        for (RobotInfo enemy : nearbyEnemies){
            if (enemy.getType() == RobotType.LUMBERJACK){
                nearbyLumberjacks.add(enemy);
            }
            else if (enemy.getType() != RobotType.ARCHON && enemy.getType() != RobotType.GARDENER){
                nearbyCombatUnits.add(enemy);
            }
        }

        /*
        Adds some randomity to the directions of travel
        Defaults direction towards enemy archon location if no nearby enemies
        */
        //Move in 8 directions
        float maxScore = -1000000f;
        if (nearestEnemy != null){
            target = nearestEnemy.getLocation();
        }
        else{
            target = ENEMY_ARCHON_LOCATION;
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
