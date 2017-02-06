package Kamino;

import battlecode.common.*;

import java.util.ArrayList;

public class LumberjackPlayer extends BasePlayer {

    //Lumberjack specific variables
    private MapLocation prevLocation = null;                     //Previous location
    private int cooldownTimer = 0;
    private int stuckRounds = 10;
    private int cooldownRounds = 50;
    private float movement_stuck_radius = RobotType.LUMBERJACK.strideRadius/8*stuckRounds;
    private boolean am_stuck = false;                            //I am stuck where I am...
    private boolean search_dir = true;                           //Right/Left bot, true == Right bot
    private boolean cooldown_active = false;                     //Cooldown period where direct pathing is disabled

    private int SLUG = 2;
    private int gridTimer = THE_FINAL_PROBLEM;
    private int timeToTarget = 0;

    private TreeInfo targetInfo;
    private boolean isInterruptable = true;                      //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise
    private boolean isChopping = false;                          //Am I currently chopping a tree?
    static final float LUMBERJACK_MICRO_DIST = 64f;
    //private BulletInfo [] nearbyBullets;

    public LumberjackPlayer(RobotController rc) throws GameActionException {
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
        target = null;
        inCombat = false;
        isInterruptable = true;
        isChopping = false;
        gridTimer = rc.getRoundNum() + 15;         //Give it an initial 15 rounds to get to it's target
        //todo I'm debugging this...
        debug = false;
        return;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        rc.broadcast(SUM_LUMBERJACKS,rc.readBroadcast(SUM_LUMBERJACKS)+1);
        //todo lumberjacks ignores dodging bullets?
        //nearbyBullets = rc.senseNearbyBullets(-1);
    }

    private void run(){
        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        //Look for nearby enemies
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            //Go into combat
            inCombat = true;
            isChopping = false;
        }
        if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
            //Snap out of combat
            inCombat = false;
            target = null;
        }
        if (inCombat){
            //Find nearest enemy
            RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            float nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);
            if (nearestdist <= LUMBERJACK_MICRO_DIST){
                //lumberjackCombatMove(nearestEnemy, nearbyBullets);
                lumberjackCombatMove(nearestEnemy);
            }
            else{
                //Move to nearest enemy's position
                target = nearestEnemy.getLocation();
                if (am_stuck) {
                    search_dir = !search_dir;
                    am_stuck = false;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
            }
        }
        else{
            //todo respond to contactReps
            MapLocation next_target = null;
            boolean active_alert = false;
            try {
                float max_rating = -0.1f;
                for (int i = ENEMY_PINGS; i < ENEMY_PINGS + MAX_ENEMY_PINGS * 3; i += 3) {
                    int gridCoded = rc.readBroadcast(i);
                    if (gridCoded == 0) continue;
                    MapLocation targetLoc = broadcaster.channel_to_location(gridCoded);
                    Message curMsg = new Message(rc.readBroadcast(i + 1));
                    int heartbeat = rc.readBroadcast(i + 2);
                    if (heartbeat <= Math.max(0, rc.getRoundNum() - PING_LIFESPAN)) continue;
                    if (curMsg.getMessageType() != Message.CONTACT_REP) continue;
                    if (curMsg.getEnemy() != RobotType.SCOUT) continue;
                    float cur_rating = getReportScore(targetLoc, curMsg);
                    if (cur_rating > max_rating) {
                        max_rating = cur_rating;
                        next_target = targetLoc;
                        active_alert = true;
                    }
                }
            } catch(Exception e){
                e.printStackTrace();
            }

            if (!active_alert) {

                //Check if tree has already been chopped down
                if (target != null && isChopping) {
                    boolean tree_exists = false;
                    for (TreeInfo t : SURROUNDING_TREES_NEUTRAL) {
                        if (t.location == target) {
                            //Target still exists :)
                            tree_exists = true;
                        }
                    }
                    if (!tree_exists) {
                        //Resets targeted tree as it no longer exists
                        target = null;
                        isChopping = false;
                    }
                }

                //Chop down trees if not in combat
                if (target == null || isInterruptable) {
                    //Find a new target
                    TreeInfo targetTree = null;
                    boolean robot_tree = false;
                    if (SURROUNDING_TREES_NEUTRAL.length > 0) {
                        for (TreeInfo t_info : SURROUNDING_TREES_NEUTRAL) {
                            if (t_info.containedRobot != null && rc.canChop(t_info.ID)) {
                                targetTree = t_info;
                                robot_tree = true;
                                break;
                            }
                        }
                        if (!robot_tree) targetTree = SURROUNDING_TREES_NEUTRAL[0];
                    }

                    if (targetTree != null) {
                        //Found a tree
                        target = targetTree.getLocation();
                        targetInfo = targetTree;
                        isInterruptable = false;
                        isChopping = true;
                    }
                    else if (target == null) {
                        //No more trees => TODO: Some scouting
                        try {
                            target = treeBroadcaster.nextOccupiedGrid(MY_LOCATION);
                            if (target == MY_LOCATION) {
                                //No target found through bfs
                                target = ENEMY_ARCHON_LOCATION;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        isInterruptable = true;
                    }

                    if (target != null) {
                        timeToTarget = (int) (MY_LOCATION.distanceTo(target) / (rc.getType().strideRadius / 2));
                        gridTimer = rc.getRoundNum() + timeToTarget;
                    }
                }
                if (target != null) {
                    //Check if has approached target
                    //todo timer for lumberjacks to change target
                    if (rc.getRoundNum() > gridTimer && !isChopping){
                        try {
                            target = treeBroadcaster.nextOccupiedGrid(MY_LOCATION);
                            if (target == MY_LOCATION) {
                                //No target found through bfs
                                target = ENEMY_ARCHON_LOCATION;
                            }
                            timeToTarget = (int) (MY_LOCATION.distanceTo(target) / (rc.getType().strideRadius / 2));
                            gridTimer = rc.getRoundNum() + timeToTarget;
                        } catch(Exception e){
                            e.printStackTrace();
                        }
                    }

                    boolean done = false;
                    //Find higher priority neutral tree that has robot! :D
                    if (SURROUNDING_TREES_NEUTRAL.length > 0) {
                        for (TreeInfo t_info : SURROUNDING_TREES_NEUTRAL) {
                            if (t_info.containedRobot != null && rc.canChop(t_info.ID)) {
                                target = t_info.location;
                                break;
                            }
                        }
                    }

                    if (rc.canChop(target)) {
                        try {
                            rc.chop(target);
                            done = true;
                        } catch (GameActionException e) {
                            e.printStackTrace();
                        }
                    }

                    //If target is gone
                    if (MY_LOCATION.isWithinDistance(target, 1)) {
                        target = null;
                    }
                    //Move toward target if not in range
                    if (!done && target != null) {
                        if (am_stuck) {
                            search_dir = !search_dir;
                            am_stuck = false;
                        }
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                    }
                }
            }
            else{
                if (next_target != null){
                    target = next_target;
                    timeToTarget = (int) (MY_LOCATION.distanceTo(target) / (rc.getType().strideRadius / 2));
                    gridTimer = rc.getRoundNum() + timeToTarget;
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
                    }
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
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
        //Chops if haven't attacked this turn
        if (!rc.hasAttacked()){
            if (SURROUNDING_TREES_NEUTRAL.length > 0){
                if (rc.canChop(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.chop(SURROUNDING_TREES_NEUTRAL[0].ID);
            }
        }

        //Computes average point of previous locations ==> metric to see if it's stuck
        System.out.println("Before computing stuck: "+Integer.toString(Clock.getBytecodeNum()));
        //todo Experimental code:
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

    //<-------------------------- LUMBERJACK MICRO --------------------------->
    private float getReportScore(MapLocation targetLoc, Message message){
        if (message.getEnemy() != RobotType.SCOUT) return -1;
        System.out.println("RAW MESSAGE: "+Integer.toString(message.getRawMsg()));
        System.out.println("Enemy: "+robotType(message.getEnemy()));
        System.out.println("Enemy Number: "+Integer.toString(message.getNumberOfEnemies()));
        System.out.println("Alert Level: "+Integer.toString(message.getAlertLevel()));
        System.out.println("Location of message: "+ broadcaster.location_to_channel(targetLoc));
        float score = 0f;
        float distance_metric = 10/(MY_LOCATION.distanceTo(targetLoc));
        int alert_level = message.getAlertLevel();
        RobotType prevalent_enemy = message.getEnemy();
        int number_enemies = message.getNumberOfEnemies();
        score = alert_level + DANGER_RATINGS[robotType(prevalent_enemy)]*number_enemies/1000;
        System.out.println("Distance scaling: "+Float.toString(distance_metric)+" @ "+Float.toString(MY_LOCATION.distanceTo(targetLoc)));
        System.out.println("Score: "+Float.toString(score*distance_metric));
        System.out.println("=====================");
        return score*distance_metric;
    }

    private float getLumberjackScore(MapLocation loc, BulletInfo[] nearbyBullets){
        float ans = 0;
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius + 1.0f, rc.getTeam());
        RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius + 1.0f, rc.getTeam());

        //Avoid bullets [Heavy penalty]
        for (BulletInfo bullet : nearbyBullets){
            if (willCollideWithMe(loc, bullet, 1)){
                ans -= bullet.getDamage() * 50f;
            }
        }

        //Avoid allied robots [Heavy penalty]
        for (RobotInfo ally : nearbyAllies){
            float dist = loc.distanceTo(ally.getLocation());
            if (dist <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
                ans -= RobotType.LUMBERJACK.attackPower * 1500f;
            }
            else{
                ans -= distanceScaling(dist);
            }
        }

        //Go close to enemy robots [Heavy reward if in range, slight reward if not in range]
        for (RobotInfo enemy : nearbyEnemies){
            float dist = loc.distanceTo(enemy.getLocation());
            if (dist <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
                ans += RobotType.LUMBERJACK.attackPower * 1000f;
            }
            else{
                ans += 100*distanceScaling(rc.getType().sensorRadius - dist);
            }
        }


        return ans;
    }

    private void lumberjackCombatMove(RobotInfo nearestEnemy){
        MapLocation myLoc = rc.getLocation();
        //Move then attack

        BulletInfo[] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        //Move in the 8 directions

        float maxScore = -1000000f;

        if (nearestEnemy != null){
            target = nearestEnemy.getLocation();
        }
        else{
            target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
        }

        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        Direction finalDir = null; //Final move direction
        for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
            }

            if (rc.canMove(chosenDir)){
                if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                float score = getLumberjackScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyBullets);
                if (score > maxScore){
                    maxScore = score;
                    finalDir = chosenDir;
                }
            }
            else{
                if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
            }
        }

        //Sometimes the best move is not to move
        float score = getLumberjackScore(myLoc, nearbyBullets);
        if (score > maxScore){
            maxScore = score;
            finalDir = null;
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

        //Carry out attack
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());

        if (nearbyAllies.length <= nearbyEnemies.length){
            if (rc.canStrike()){
                try {
                    rc.strike();
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
