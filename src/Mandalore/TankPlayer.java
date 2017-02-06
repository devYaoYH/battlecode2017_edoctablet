package Mandalore;

import battlecode.common.*;

import java.util.ArrayList;

public class TankPlayer extends BasePlayer {

    static float TANK_SHOOT_RANGE= 64f;   		        //Squared distance tanks engages at
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    static float movement_stuck_radius = RobotType.TANK.strideRadius - EPSILON;
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

    private int stuckRounds = 17;
    private int SLUG = 3;                                       //Granularity of slug pathing
    private int gridTimer = THE_FINAL_PROBLEM;
    private int timeToTarget = 0;                               //How long to target

    private float ARCHON_BONUS_SCORE = 5f;                      //Increasing this makes it more likely to go after archons
    private float GARDENER_BONUS_SCORE = 2f;                    //More likely to target gardeners
    private BulletInfo [] nearbyBullets = null;
    private int[] target_data = null;

    public TankPlayer(RobotController rc) throws GameActionException {
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

    private void initPlayer(){
        //Setup scanner range based on robot type
        MOVEMENT_SCANNING_DISTANCE = 4.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
        PENTAD_EFFECTIVE_DISTANCE = 6f;
        inCombat = false;
        target = null;
        //todo I'm debugging this...
        debug = false;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        rc.broadcast(SUM_TANKS,rc.readBroadcast(SUM_TANKS)+1);
        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    }

    private void run() {
        //Debug targeted location
        if (!inCombat && target != null){
            System.out.println(broadcaster.location_to_channel(target));
            System.out.println(broadcaster.printBoundaries());
            if (!broadcaster.isValidGrid(target)){
                System.out.println("Currently assigned grid is out of bounds");
                target = null;
            }
            else{
                //See whether someone else has already scouted this assigned position
                try {
                    target_data = broadcaster.readGrid(target);
                    if (target_data[0] == 1){
                        //Someone has recently scouted this location already!
                        target = null;
                    }
                    //todo timer for soldier to change target
                    if (rc.getRoundNum() > gridTimer){
                        sitRep(target);
                        target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (target == MY_LOCATION){
                            //No target found through bfs
                            target = ENEMY_ARCHON_LOCATION;
                        }
                        timeToTarget = (int)(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/2));
                        gridTimer = rc.getRoundNum() + timeToTarget;
                    }
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        //Spots enemies
        try {
            if (SURROUNDING_ROBOTS_ENEMY.length > 0) spotRep();
        } catch(Exception e){
            e.printStackTrace();
        }

        //Engage in combat if there are enemies present
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            inCombat = true;
            target = null;
        }

        //Respond to distress calls
        if (!inCombat){
            try {
                float max_rating = -10000f;
                MapLocation next_target = null;
                boolean active_alert = false;
                for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
                    int gridCoded = rc.readBroadcast(i);
                    if (gridCoded == 0) continue;
                    MapLocation targetLoc = broadcaster.channel_to_location(gridCoded);
                    Message curMsg = new Message(rc.readBroadcast(i + 1));
                    int heartbeat = rc.readBroadcast(i + 2);
                    if (heartbeat <= Math.max(0, rc.getRoundNum() - PING_LIFESPAN)) continue;
                    float cur_rating = getReportScore(targetLoc, curMsg);
                    if (cur_rating > max_rating) {
                        max_rating = cur_rating;
                        next_target = targetLoc;
                        active_alert = true;
                    }
                }
                if (active_alert){
                    if (target != null) {
                        int[] cur_target_data = broadcaster.readGrid(target);
                        if (cur_target_data[0] == 0){
                            //I'm just scouting...let's respond to an alert first
                            target = next_target;
                        }
                        else {
                            float cur_rating = getReportScore(target, new Message(cur_target_data[1]));
                            if (max_rating > cur_rating) {
                                target = next_target;
                            }
                        }
                    }
                    else{
                        target = next_target;
                    }
                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        else{
            //Snap out of combat
            if (SURROUNDING_ROBOTS_ENEMY.length == 0){
                inCombat = false;
                target = null;
            }
        }

        if (target != null) {
            System.out.println(broadcaster.location_to_channel(target));
        }

        //Movement
        //Engage enemy if in combat
        if (inCombat){
            RobotInfo targetEnemy = scoreTargetEnemy(SURROUNDING_ROBOTS_ENEMY);
            float nearestdist = MY_LOCATION.distanceSquaredTo(targetEnemy.location);
            if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0) {
                tankCombatMove(targetEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                System.out.println("After micro: " + Integer.toString(Clock.getBytecodeNum()));
            }
            else {
                //Move to nearest enemy's position
                target = targetEnemy.getLocation();
                if (am_stuck) {
                    search_dir = !search_dir;
                    am_stuck = false;
                    staying_conut = 0;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
                System.out.println("After moving: " + Integer.toString(Clock.getBytecodeNum()));
            }
        }
        else{
            //Go scouting at a new location
            if (target != null){
                if (hasReachedGrid(target)){
                    if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);
                    try {
                        //todo rushes towards enemy archon by default
                        int[] enemyArchonInfo = broadcaster.readGrid(ENEMY_ARCHON_LOCATION);
                        if (enemyArchonInfo[0] == 0){
                            target = ENEMY_ARCHON_LOCATION;
                        }
                        else target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        //target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (am_stuck){
                            search_dir = !search_dir;
                            am_stuck = false;
                            staying_conut = 0;
                        }
                        if (target == MY_LOCATION){
                            //No target found through bfs
                            target = ENEMY_ARCHON_LOCATION;
                        }
                        timeToTarget = (int)(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/2));
                        gridTimer = rc.getRoundNum() + timeToTarget;
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }
                else {
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
                    if (target != null){
                        if (debug) rc.setIndicatorDot(target, 255, 255, 255);
                        System.out.println(broadcaster.location_to_channel(target));
                    }
                }
            }
            else{
                try {
                    //todo rushes towards enemy archon by default
                    int[] enemyArchonInfo = broadcaster.readGrid(ENEMY_ARCHON_LOCATION);
                    if (enemyArchonInfo[0] == 0){
                        target = ENEMY_ARCHON_LOCATION;
                    }
                    else target = broadcaster.nextEmptyGrid(MY_LOCATION);
                    //target = broadcaster.nextEmptyGrid(MY_LOCATION);
                    if (am_stuck){
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
                    if (target == MY_LOCATION){
                        //No target found through bfs
                        target = ENEMY_ARCHON_LOCATION;
                    }
                    timeToTarget = (int)(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/2));
                    gridTimer = rc.getRoundNum() + timeToTarget;
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(broadcaster.location_to_channel(target));
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 0, 0);
        }
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
                }
                else{
                    am_stuck = false;
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

    //==========================================================================
    //<-------------- THE ALMIGHTY PATHFINDING THINGUMMYWHAT ----------------->
    //==========================================================================
    //Special case for tanks to avoid running over friendly trees...
    private Direction closest_direction_to_target(int GRANULARITY, int sensorDir, float distance, boolean rightBot, TreeInfo[] friendlyTrees, TreeInfo[] neutralTrees){
        if (rightBot) {
            for (int i = 1; i < GRANULARITY; i++) {
                Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir + i, GRANULARITY)]);
                if (canMoveTank(tryDir, distance, friendlyTrees, neutralTrees)) {
                    return tryDir;
                }
            }
        }
        else {
            for (int i = 1; i < GRANULARITY; i++) {
                Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir - i, GRANULARITY)]);
                if (canMoveTank(tryDir, distance, friendlyTrees, neutralTrees)) {
                    return tryDir;
                }
            }
        }
        return null;
    }

    //Bruteforcing way pathing
    protected boolean tryMove(Direction moveDir, TreeInfo[] friendlyTrees, TreeInfo[] neutralTrees) throws GameActionException {
        if (rc.hasMoved()) return false;
        if (canMoveTank(moveDir, rc.getType().strideRadius, friendlyTrees, neutralTrees)){
            rc.move(moveDir);
            return true;
        }
        for (int i=1;i<MOVEMENT_GRANULARITY;i++){
            Direction tryDir = moveDir.rotateRightRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (canMoveTank(tryDir, rc.getType().strideRadius, friendlyTrees, neutralTrees)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
            tryDir = moveDir.rotateLeftRads(MOVEMENT_SEARCH_ANGLE/MOVEMENT_GRANULARITY*i);
            if (canMoveTank(tryDir, rc.getType().strideRadius, friendlyTrees, neutralTrees)){
                if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 0, 255, 0);
                rc.move(tryDir);
                return true;
            }
            else if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, 2f), 255, 0, 0);
        }
        return false;
    }

    protected void moveToTargetExperimental(MapLocation myLoc, int GRANULARITY, boolean rightBot, boolean stuck, int SLUG_GRANULARITY){
        if (rc.hasMoved()) return;
        if (myLoc == target) return;
        //Scan for surrounding trees
        TreeInfo[] friendlyTrees = rc.senseNearbyTrees(rc.getType().strideRadius+rc.getType().bodyRadius+EPSILON, rc.getTeam());
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(rc.getType().strideRadius+rc.getType().bodyRadius+EPSILON, Team.NEUTRAL);

        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<GRANULARITY;i++){
            if (absRad(initDir.radians) > SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (absRad(initDir.radians) - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir++, GRANULARITY);

        if (debug) rc.setIndicatorLine(myLoc, myLoc.add(SENSOR_ANGLES[sensorDir], 2f), 0, 255, 0);

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
            broadcaster.updateBoundaries(myLoc);
            //todo DANGEROUS CODE...use tryMove unless stuck...
//            if (rc.canMove(initDir)){
//                rc.move(initDir);
//                return;
//            }
            if (!stuck){
                if (tryMove(initDir, friendlyTrees, neutralTrees)) return;
            }
            if (!rc.hasMoved()){
                //Slug Pathing
                Direction tryDir = null;
                Direction bestDir = null;
                float closestAngle = 99999f;
                float distanceMoved = 0f;
                for (int i=0;i<SLUG_GRANULARITY;i++){
                    tryDir = closest_direction_to_target(GRANULARITY, sensorDir, rc.getType().strideRadius/(float)Math.pow(2,i), rightBot, friendlyTrees, neutralTrees);
                    if (initDir.radiansBetween(tryDir) < closestAngle){
                        bestDir = tryDir;
                        closestAngle = initDir.radiansBetween(tryDir);
                        distanceMoved = rc.getType().strideRadius/(float)Math.pow(2,i);
                    }
                }
                if (bestDir != null){
                    rc.move(bestDir, distanceMoved);
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return;
    }

    private boolean canMoveTank(Direction tryDir, float dist, TreeInfo[] nearbyTrees, TreeInfo[] nearbyNeutrals){
        if (!rc.canMove(tryDir, dist)) return false;
        MapLocation tryLoc = MY_LOCATION.add(tryDir, dist);
        for (TreeInfo t:nearbyTrees){
            if (tryLoc.distanceTo(t.location) < RobotType.TANK.bodyRadius+t.radius) return false;
        }
        for (TreeInfo t:nearbyNeutrals){
            if (tryLoc.distanceTo(t.location) < RobotType.TANK.bodyRadius+t.radius) return false;
        }
        return true;
    }


    //<----------------------------- TANK MICRO ------------------------------>
    private float getTankScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
        float ans = 0;
        //Avoid lumberjacks
        for (RobotInfo lumberjack : nearbyLumberjacks){
            if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
                //System.out.println(lumberjack.getLocation().distanceTo(loc));
                ans -= rc.getType().attackPower * 100f;
            }
            else break;
        }

        //Avoid bullets [Heavy penalty]
        //Ignores bullets...will likely get hit anyway since it's so huge
//        for (BulletInfo bullet : nearbyBullets){
//            if (willCollideWithMe(loc, bullet)){
//                ans -= bullet.getDamage() * 1000f;
//            }
//        }

        //Get closer to gardeners
        if (loc.distanceTo(target) < MY_LOCATION.distanceTo(target)) ans += 2500;

        if (debug) rc.setIndicatorDot(loc, 255, Math.max(255-(int)(ans/-10), 0), 0);

        return ans;
    }

    private float getReportScore(MapLocation targetLoc, Message message){
        System.out.println("RAW MESSAGE: "+Integer.toString(message.getRawMsg()));
        System.out.println("Enemy: "+robotType(message.getEnemy()));
        System.out.println("Enemy Number: "+Integer.toString(message.getNumberOfEnemies()));
        System.out.println("Alert Level: "+Integer.toString(message.getAlertLevel()));
        System.out.println("Location of message: "+ broadcaster.location_to_channel(targetLoc));
        float score = 0f;
        float distance_metric = 10/(MY_LOCATION.distanceTo(targetLoc));
        int alert_level = message.getAlertLevel();
        boolean archon_present = (message.getArchonPresent() == 1);
        RobotType prevalent_enemy = message.getEnemy();
        int number_enemies = message.getNumberOfEnemies();
        score = alert_level + DANGER_RATINGS[robotType(prevalent_enemy)]*number_enemies/1000 + (archon_present?ARCHON_BONUS_SCORE:0f);
        System.out.println("Distance scaling: "+Float.toString(distance_metric)+" @ "+Float.toString(MY_LOCATION.distanceTo(targetLoc)));
        System.out.println("Score: "+Float.toString(score*distance_metric));
        System.out.println("=====================");
        return score*distance_metric;
    }

    private RobotInfo scoreTargetEnemy(RobotInfo[] surrEnemies){
        RobotInfo best_target = null;
        float best_score = 0f;
        float cur_score = 0f;

        for (RobotInfo r:surrEnemies){
            cur_score += 10*distanceScaling(MY_LOCATION.distanceSquaredTo(r.location));
            cur_score += DANGER_RATINGS[robotType(r.type)]/10;
            if (r.type == RobotType.GARDENER) cur_score += GARDENER_BONUS_SCORE;
            if (cur_score > best_score){
                best_score = cur_score;
                best_target = r;
            }
        }

        return best_target;
    }

    private void tankCombatMove(RobotInfo targetedEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
        MapLocation myLoc = rc.getLocation();
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
        boolean hasMoved = false; //Have I moved already?

        //Process nearby enemies
        for (RobotInfo enemy : nearbyEnemies){
            if (enemy.getType() == RobotType.LUMBERJACK){
                nearbyLumberjacks.add(enemy);
            }
            else if (enemy.getType() != RobotType.ARCHON){
                nearbyCombatUnits.add(enemy);
            }
        }

        //System.out.println(nearbyLumberjacks);

        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        System.out.println("How many frigging bullets are there?? "+Integer.toString(nearbyBullets.length));

        //Move in the 8 directions
        float maxScore = -1000000f;
        MapLocation prevtarget = target;
        if (targetedEnemy != null){
            target = targetedEnemy.getLocation();
        }
        else{
            //Is this a good idea?
            target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
        }

        if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);

        if (targetedEnemy.type == RobotType.GARDENER){
            //Just push forward to gardener's position
            if (am_stuck){
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
        }
        else {
            Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
            Direction finalDir = null; //Final move direction

            //Sometimes the best move is not to move
            float score = getTankScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
            if (score > maxScore) {
                maxScore = score;
                finalDir = null;
            }

            for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++) {
                if (Clock.getBytecodeNum() > BYTE_THRESHOLD) continue;
                Direction chosenDir; //Direction chosen by loop
                if (i % 2 == 0) {
                    chosenDir = new Direction((float) (initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
                }
                else {
                    chosenDir = new Direction((float) (initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
                }

                if (rc.canMove(chosenDir)) {
                    ////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                    score = getTankScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
                    //System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
                    //System.out.println("Score: "+score);
                    if (score > maxScore) {
                        maxScore = score;
                        finalDir = chosenDir;
                    }
                }
                else {
                    ////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
                }
            }

            if (finalDir != null) {
                target = MY_LOCATION.add(finalDir, 5f);
                if (am_stuck) {
                    search_dir = !search_dir;
                    am_stuck = false;
                    staying_conut = 0;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck, SLUG);
            }
        }

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SOLDIER_COMBAT_SENSE_RADIUS, rc.getTeam());
        //Should I use -1?

        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
        if (targetedEnemy != null && rc.getTreeCount() < SUSTAINABLE_TREE_THRESHOLD){
            if (targetedEnemy.type != RobotType.SOLDIER && targetedEnemy.type != RobotType.TANK) {
                if (myLoc.distanceTo(targetedEnemy.location) > PENTAD_EFFECTIVE_DISTANCE) {
                    should_fire_pentad = false;
                    if (targetedEnemy.type == RobotType.LUMBERJACK || targetedEnemy.type == RobotType.ARCHON || targetedEnemy.type == RobotType.SCOUT) {
                        should_fire_triad = false;
                    }
                }
            }
        }
        //Conduct attack
        //Avoid friendly fire
        if (rc.canFirePentadShot() && should_fire_pentad){
            MapLocation attackLoc = null;
            attackLoc = targetedEnemy.getLocation();
            if (attackLoc != null){
                boolean safeToShoot = true;
                for (int i = 0; i < 5; i++){
                    Direction shotDir = null;
                    if (i <= 1){
                        shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (2 - i));
                    }
                    else if (i == 2){
                        shotDir = myLoc.directionTo(attackLoc);
                    }
                    else{
                        shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (i - 2));
                    }
                    MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

                    for (RobotInfo ally : nearbyAllies){
                        if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
                            safeToShoot = false;
                            break;
                        }
                    }
                    if (!safeToShoot){
                        break;
                    }
                }
                if (safeToShoot){
                    try {
                        rc.firePentadShot(myLoc.directionTo(attackLoc));
                        if (debug) rc.setIndicatorLine(MY_LOCATION, attackLoc, 100, 0, 0);
                        hasFired = true;
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (!hasFired && rc.canFireTriadShot() && should_fire_triad){
            MapLocation attackLoc = null;
            attackLoc = targetedEnemy.getLocation();
            if (attackLoc != null){
                boolean safeToShoot = true;
                for (int i = 0; i < 3; i++){
                    Direction shotDir = null;
                    if (i == 0){
                        shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
                    }
                    else if (i == 1){
                        shotDir = myLoc.directionTo(attackLoc);
                    }
                    else{
                        shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
                    }
                    MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

                    for (RobotInfo ally : nearbyAllies){
                        if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
                            safeToShoot = false;
                            break;
                        }
                    }
                    if (!safeToShoot){
                        break;
                    }
                }
                if (safeToShoot){
                    try {
                        rc.fireTriadShot(myLoc.directionTo(attackLoc));
                        if (debug) rc.setIndicatorLine(MY_LOCATION, attackLoc, 200, 0, 0);
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (!hasFired && rc.canFireSingleShot()){
            MapLocation attackLoc = null;
            attackLoc = targetedEnemy.getLocation();
            if (attackLoc != null){
                boolean safeToShoot = true;
                Direction shotDir = myLoc.directionTo(attackLoc);
                MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

                for (RobotInfo ally : nearbyAllies){
                    if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
                        safeToShoot = false;
                        break;
                    }
                }
                if (safeToShoot){
                    try {
                        rc.fireSingleShot(myLoc.directionTo(attackLoc));
                        if (debug) rc.setIndicatorLine(MY_LOCATION, attackLoc, 255, 0, 0);
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return;
    }

}
