package Mandalore;

import battlecode.common.*;

import java.util.ArrayList;

public class SoldierPlayer extends BasePlayer {

    //Soldier specific variables
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    //static float movement_stuck_radius = RobotType.SOLDIER.strideRadius - EPSILON;
    //todo n*(0.95/4)*0.5 = movement stuck radius
    static int stuckRounds = 10;                                //How many rounds before I'm stuck
    static int cooldownRounds = 25;
    static int cooldownTimer = 0;                               //Turns to run slug path before reverting to direct pathing
    static float movement_stuck_radius = RobotType.SOLDIER.strideRadius/8*stuckRounds;       //Stuck distance for n rounds
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean cooldown_active = false;                     //Cooldown period where direct pathing is disabled
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot
    private float prev_distance_to_target = 99999f;             //Used for determining when to resume direct pathing

    private int SLUG = 3;                                       //How many levels of slugging

    private int gridTimer = THE_FINAL_PROBLEM;
    private int timeToTarget = 0;                               //How long to target

    private float ARCHON_BONUS_SCORE = 3f;                      //Increasing this makes it more likely to go after archons
    private float ARCHON_PENALTY_SCORE = -2f;                   //Makes it less likely to attack archons when other enemies are nearby
    private float GARDENER_BONUS_SCORE = 2f;                    //More likely to target gardeners
    private float CLOSEST_BONUS_SCORE = 2f;                     //Tie breaker for closer enemy
    private float LUMBERJACK_BONUS_SCORE = 100f;                //Bonus score for lumberjacks within strike range of their attacks
    private float LUMBERJACK_STRIKE_RADIUS = 3f;                //Distance to activate lumberjack bonus (cuz they'll strike ya!)
    private BulletInfo [] nearbyBullets = null;
    private int[] target_data = null;

    public SoldierPlayer(RobotController rc) throws GameActionException {
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

    private void initPlayer(){//Set important variables
        target = ENEMY_ARCHON_LOCATION;
        inCombat = false;
        //todo I'm debugging this...
        debug = false;
        return;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        rc.broadcast(SUM_SOLDIERS,rc.readBroadcast(SUM_SOLDIERS)+1);
        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    }

    private void run() {
        if (debug) rc.setIndicatorDot(MY_LOCATION, 0, 255, 255);
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
                soldierCombatMove(targetEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
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
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
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
                        if (enemyArchonInfo[0] == 0 && rc.readBroadcast(ENEMY_ARCHON_KILLED) == 0){
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
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
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
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
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
                    if (enemyArchonInfo[0] == 0 && rc.readBroadcast(ENEMY_ARCHON_KILLED) == 0){
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
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        //Scan again for enemies after movement
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (debug){
            for (RobotInfo r:SURROUNDING_ROBOTS_ENEMY){
                rc.setIndicatorDot(r.location, 255, 0, 0);
            }
        }

        System.out.println("Am I inCombat? "+Boolean.toString(inCombat));

        //Engage in combat if there are enemies present
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            inCombat = true;
            target = null;
            //I have most recently spotted an enemy, let's kick some ass
            RobotInfo targetEnemy = scoreTargetEnemy(SURROUNDING_ROBOTS_ENEMY);
            float nearestdist = MY_LOCATION.distanceSquaredTo(targetEnemy.location);
            System.out.println("Distance to enemy: "+Float.toString(nearestdist));
            if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0) {
                System.out.println("Going in for preemptive strike");
                soldierPreemptiveStrike(targetEnemy);
                System.out.println("After micro: " + Integer.toString(Clock.getBytecodeNum()));
            }
        }

        /*if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(Broadcaster.location_to_channel(target));
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 0, 0);
        }*/
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
                    //todo distance activation for direct pathing
                    prev_distance_to_target = MY_LOCATION.distanceTo(target);
                    //cooldownTimer = rc.getRoundNum() + cooldownRounds;
                }
                else{
                    am_stuck = false;
                    //todo distance activation for direct pathing
                    if (MY_LOCATION.distanceTo(target) < prev_distance_to_target){
                        prev_distance_to_target = 99999f;
                        cooldown_active = false;
                    }
                    //if (rc.getRoundNum() > cooldownTimer) cooldown_active = false;
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

    //<---------------------------- MOVEMENT ALGO ---------------------------->
    //Slug Pathing algo...
    protected void moveToTargetExperimental(MapLocation myLoc, int GRANULARITY, boolean rightBot, boolean stuck, int SLUG_GRANULARITY){
        if (rc.hasMoved()) return;
        if (myLoc == target) return;
        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //todo deflection based on distance to target as well as number of friendly troops ahead
        if (MY_LOCATION.distanceSquaredTo(target) > SOLDIER_MICRO_DIST && MY_LOCATION.distanceSquaredTo(target) < SOLDIER_MICRO_DIST*16){
            float density_ahead = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target), 4f), 3f, OBJECTS);
            float density_left = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target).rotateLeftRads(DEG_60), 4f), 3f, OBJECTS);
            float density_right = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target).rotateRightRads(DEG_60), 4f), 3f, OBJECTS);
            boolean deflect_right = false;
            if (density_left == density_right){
                deflect_right = (Math.random()<=0.5);
            }
            else if (density_left > density_right){
                deflect_right = true;
            }
            if (deflect_right){
                initDir = initDir.rotateRightRads(Math.min(DEG_45,DEG_90*density_ahead));
            }
            else{
                initDir = initDir.rotateLeftRads(Math.min(DEG_45,DEG_90*density_ahead));
            }
        }

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
                if (tryMove(initDir)) return;
            }
            if (!rc.hasMoved()){
                //Slug Pathing
                Direction tryDir = null;
                Direction bestDir = null;
                float closestAngle = 99999f;
                float distanceMoved = 0f;
                for (int i=0;i<SLUG_GRANULARITY;i++){
                    tryDir = closest_direction_to_target(GRANULARITY, sensorDir, rc.getType().strideRadius/(float)Math.pow(2,i), rightBot);
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

    //<---------------------------- SOLDIER MICRO ---------------------------->
    private float getSoldierScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
        float ans = 0;
        //Avoid lumberjacks
        for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
            if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
                //System.out.println(lumberjack.getLocation().distanceTo(loc));
                ans -= rc.getType().attackPower * 1000f;
            }
            else break;
        }

        //Avoid bullets [Heavy penalty]
        for (BulletInfo bullet : nearbyBullets){
            if (debug) rc.setIndicatorLine(bullet.location, bullet.location.add(bullet.dir, 1f), 255, 0, 0);
            if (willCollideWithMe(loc, bullet)){
                ans -= bullet.getDamage() * 1000f;
            }
        }

        //Be as far as possible from the soldiers
        for (RobotInfo robot : nearbyCombatRobots){
            ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
        }

        //Get closer to gardeners
        //if (loc.distanceTo(target) > MY_LOCATION.distanceTo(target)) ans -= 50;

        System.out.println("Score: "+Float.toString(ans));

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
        boolean closest_enemy = true;

        for (RobotInfo r:surrEnemies){
            cur_score = 0f;
            System.out.println("=================");
            System.out.println("Current enemy: "+Integer.toString(robotType(r.type)));
            if (closest_enemy){
                cur_score += CLOSEST_BONUS_SCORE;
                closest_enemy = false;
            }
            //todo increase for lumberjacks inrange of their strike attack
            if (r.type == RobotType.LUMBERJACK){
                if (MY_LOCATION.distanceTo(r.location) < LUMBERJACK_STRIKE_RADIUS){
                    cur_score += LUMBERJACK_BONUS_SCORE;
                }
            }
            System.out.println("Closest bonus: "+Float.toString(cur_score));
            cur_score += distanceScaling(MY_LOCATION.distanceTo(r.location));
            System.out.println("Distance scaling: "+Float.toString(cur_score));
            cur_score += DANGER_RATINGS[robotType(r.type)]/10;
            System.out.println("Danger rating: "+Float.toString(cur_score));
            if (r.type == RobotType.GARDENER) cur_score += GARDENER_BONUS_SCORE;
            if (r.type == RobotType.ARCHON) cur_score += ARCHON_PENALTY_SCORE;
            System.out.println("Gardener bonus: "+Float.toString(cur_score));
            if (cur_score > best_score){
                best_score = cur_score;
                best_target = r;
            }
            System.out.println("Current enemy: "+Integer.toString(robotType(r.type))+" | Score: "+Float.toString(cur_score));
        }

        return best_target;
    }

    private void soldierPreemptiveStrike(RobotInfo targetedEnemy){
        if (targetedEnemy == null) return;
        if (willCollideWithTree(MY_LOCATION.directionTo(target), rc.senseNearbyTrees(4f, null))) return;

        MapLocation myLoc = rc.getLocation();
        if (debug) rc.setIndicatorLine(myLoc, targetedEnemy.location, 255, 0, 255);
        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
        boolean should_fire = true;
        /*if (targetedEnemy != null && rc.getTreeCount() < SUSTAINABLE_TREE_THRESHOLD){
            if (targetedEnemy.type != RobotType.SOLDIER && targetedEnemy.type != RobotType.TANK) {
                if (myLoc.distanceTo(targetedEnemy.location) > PENTAD_EFFECTIVE_DISTANCE) {
                    should_fire_pentad = false;
                    if (targetedEnemy.type == RobotType.ARCHON || targetedEnemy.type == RobotType.GARDENER) {
                        should_fire_triad = false;
                    }
                }
            }
        }*/
        //When econs not good, don't spam Archon
        if (targetedEnemy.type == RobotType.ARCHON && rc.getRoundNum() < EARLY_GAME){
            should_fire_pentad = false;
            should_fire_triad = false;
            if (rc.getTeamBullets() < 53){
                should_fire = false;
            }
        }

        /*if (rc.getRoundNum() < MID_GAME){
            should_fire_pentad = false;
        }*/

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SOLDIER_COMBAT_SENSE_RADIUS, rc.getTeam());
        //Should I use -1?

        //Conduct attack
        //Avoid friendly fire
        if (!hasFired && rc.canFirePentadShot() && should_fire_pentad){
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
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (!hasFired && rc.canFireSingleShot() && should_fire){
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
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return;
    }

    private void soldierCombatMove(RobotInfo targetedEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
        if (targetedEnemy == null){
            target = ENEMY_ARCHON_LOCATION;
            if (am_stuck) {
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
            return;
        }
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

        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
        boolean should_fire = true;
        /*if (targetedEnemy != null && rc.getTreeCount() < SUSTAINABLE_TREE_THRESHOLD){
            if (targetedEnemy.type != RobotType.SOLDIER && targetedEnemy.type != RobotType.TANK) {
                if (myLoc.distanceTo(targetedEnemy.location) > PENTAD_EFFECTIVE_DISTANCE) {
                    should_fire_pentad = false;
                    if (targetedEnemy.type == RobotType.ARCHON || targetedEnemy.type == RobotType.GARDENER) {
                        should_fire_triad = false;
                    }
                }
            }
        }*/
        //When econs not good, don't spam Archon
        if (targetedEnemy.type == RobotType.ARCHON){
            if (rc.getRoundNum() < EARLY_GAME) {
                should_fire_pentad = false;
                should_fire_triad = false;
                if (rc.getTeamBullets() < 53) {
                    should_fire = false;
                }
            }
            else{
                if (rc.getTeamBullets() < 107) {
                    should_fire_pentad = false;
                    should_fire_triad = false;
                }
            }
        }

        /*if (rc.getRoundNum() < MID_GAME){
            should_fire_pentad = false;
        }*/

        //System.out.println(nearbyLumberjacks);
        System.out.println("How many frigging bullets are there?? "+Integer.toString(nearbyBullets.length));

        //Move in the 8 directions
        float maxScore = -1000000f;
        MapLocation prevtarget = target;
        target = targetedEnemy.getLocation();

        if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);

        if (targetedEnemy.type == RobotType.GARDENER || targetedEnemy.type == RobotType.ARCHON){
            //Just push forward to gardener's/archon's position
            //If soldier is not already beside the target
            switch(targetedEnemy.type){
                case GARDENER:
                    if (MY_LOCATION.distanceTo(targetedEnemy.location) > RobotType.SOLDIER.bodyRadius + RobotType.GARDENER.bodyRadius + 1f) {
                        if (am_stuck) {
                            search_dir = !search_dir;
                            am_stuck = false;
                            staying_conut = 0;
                        }
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                    }
                    break;
                case ARCHON:
                    if (MY_LOCATION.distanceTo(targetedEnemy.location) > RobotType.SOLDIER.bodyRadius + RobotType.ARCHON.bodyRadius + 1f) {
                        if (am_stuck) {
                            search_dir = !search_dir;
                            am_stuck = false;
                            staying_conut = 0;
                        }
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                    }
                    break;
            }
        }
        else if (willCollideWithTree(MY_LOCATION.directionTo(target), rc.senseNearbyTrees(4f, null))){
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 255);
            System.out.println("Direct path blocked...manouvring to new position");
            if (rc.getTeamBullets() < CONSERVE_BULLETS_LIMIT) hasFired = true;
            if (am_stuck) {
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, 1);
        }
        else {
            nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
            //Does combat directional scoring movement
            Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
            Direction finalDir = null; //Final move direction
            //Sometimes the best move is not to move
            float score = getSoldierScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
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
                    if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                    score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
                    //System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
                    //System.out.println("Score: "+score);
                    if (score > maxScore) {
                        maxScore = score;
                        finalDir = chosenDir;
                    }
                }
                else {
                    if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
                }
            }

            if (finalDir != null) {
                //todo Don't need such a heavy pathing algo? just tryMove
                try {
                    tryMove(finalDir);
                } catch(Exception e){
                    e.printStackTrace();
                }
                /*target = MY_LOCATION.add(finalDir, 5f);
                if (am_stuck) {
                    search_dir = !search_dir;
                    am_stuck = false;
                    staying_conut = 0;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, 1);*/
            }
        }

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SOLDIER_COMBAT_SENSE_RADIUS, rc.getTeam());
        //Should I use -1?

        //Conduct attack
        //Avoid friendly fire
        if (!hasFired && rc.canFirePentadShot() && should_fire_pentad){
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
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (!hasFired && rc.canFireSingleShot() && should_fire){
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
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return;
    }

}
