package Kamino;

import battlecode.common.*;

import java.util.ArrayList;

public class ScoutPlayer extends BasePlayer {

    //Navigational variables
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    static float movement_stuck_radius = RobotType.SCOUT.strideRadius/2 + EPSILON;
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

    private int SLUG = 1;

    private float ARCHON_BONUS_SCORE = 10f;                     //Increasing this makes scout go after archons more frequently
    private float GARDENER_BONUS_SCORE = 0.25f;                 //Makes scouts gravitate towards area with lots of gardeners
    private BulletInfo [] nearbyBullets = null;
    private int[] target_data = null;

    public ScoutPlayer(RobotController rc) throws GameActionException {
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
        target = ENEMY_ARCHON_LOCATION;
        inCombat = false;
        //todo I'm debugging this...
        debug = false;
        return;
    }

    public void startRound() throws GameActionException {
        super.startRound();
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        //Check for trees with bullets :O
        for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
            if (t.getContainedBullets() > 0){
                target = t.location;
                break;
            }
        }

        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
    }

    private void run() {
        //Debug targeted location
        if (target != null){
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
        if (SURROUNDING_ROBOTS_ENEMY.length > 0){
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
            RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            float nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);
            if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0) {
                scoutCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                System.out.println("After micro: " + Integer.toString(Clock.getBytecodeNum()));
            }
            else {
                //Move to nearest enemy's position
                target = nearestEnemy.getLocation();
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
                        target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (am_stuck){
                            search_dir = !search_dir;
                            am_stuck = false;
                            staying_conut = 0;
                        }
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
                    target = broadcaster.nextEmptyGrid(MY_LOCATION);
                    if (am_stuck){
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
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
            MY_LOCATION = rc.getLocation();
            staying_conut = 0;
            am_stuck = false;
        }
        else{
            if (debug) rc.setIndicatorDot(prevLocation, 255, 100, 100);
            if (debug) rc.setIndicatorDot(MY_LOCATION, 100,255, 125);
            centerPoint = MY_LOCATION.add(MY_LOCATION.directionTo(prevLocation), MY_LOCATION.distanceTo(prevLocation)/2);
            prevLocation = MY_LOCATION;
            MY_LOCATION = rc.getLocation();
            System.out.println("Evaluated previous distance: "+Float.toString(MY_LOCATION.distanceTo(centerPoint)));
            if (MY_LOCATION.distanceTo(centerPoint) < movement_stuck_radius && !inCombat){
                if (debug) rc.setIndicatorDot(centerPoint, 255,0, 0);
                staying_conut++;
            }
            else{
                staying_conut = 0;
                if (debug) rc.setIndicatorDot(centerPoint, 100, 100, 255);
            }
        }
        if (staying_conut > STUCK_THRESHOLD_TURNS){
            System.out.println("Oh oops, I'm STUCK!!!");
            am_stuck = true;
        }
        System.out.println("After computing stuck: "+Integer.toString(Clock.getBytecodeNum()));

    }

    //Not only shake the closest tree, shake the closest tree with bullets!
    private void shakeTrees() throws GameActionException {
        if (SURROUNDING_TREES_NEUTRAL.length > 0){
            for (TreeInfo t:SURROUNDING_TREES_NEUTRAL){
                if (t.getContainedBullets()>0){
                    if (rc.canShake(t.ID)){
                        rc.shake(t.ID);
                        return;
                    }
                }
            }
        }
    }

    //<----------------------------- SCOUT MICRO ----------------------------->
    private float getScoutScore(MapLocation loc, RobotInfo nearestGardener, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
        float ans = 0;
        //Avoid lumberjacks
        for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
            if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
                //System.out.println(lumberjack.getLocation().distanceTo(loc));
                ans -= RobotType.LUMBERJACK.attackPower * 1000f;
            }
        }

        //Avoid bullets [Heavy penalty]
        for (BulletInfo bullet : nearbyBullets){
            if (willCollideWithMe(loc, bullet)){
                ans -= bullet.getDamage() * 1000f;
            }
        }

        //Be as close as possible to the gardener
        if (nearestGardener != null){
            ans += distanceScaling(loc.distanceTo(nearestGardener.getLocation()));
        }

        //Be as far as possible from the soldiers
        for (RobotInfo robot : nearbyCombatRobots){
            ans -= distanceScaling(loc.distanceTo(robot.getLocation())) / OFFENSE_FACTOR;
        }

        return ans;
    }

    private float getReportScore(MapLocation targetLoc, Message message){
        float score = 0f;
        float distance_metric = 10/(MY_LOCATION.distanceTo(targetLoc));
        int alert_level = message.getAlertLevel();
        boolean archon_present = (message.getArchonPresent() == 1);
        RobotType prevalent_enemy = message.getEnemy();
        int number_enemies = message.getNumberOfEnemies();
        score = alert_level - DANGER_RATINGS[robotType(prevalent_enemy)]*number_enemies/10000 + (archon_present?ARCHON_BONUS_SCORE:0f);
        if (prevalent_enemy == RobotType.GARDENER) score += GARDENER_BONUS_SCORE*number_enemies;
        return score*distance_metric;
    }

    private void scoutCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
        MapLocation myLoc = rc.getLocation();
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        RobotInfo nearestGardener = null;
        float nearestGardenerDist = 1000000f;
        RobotInfo nearestLumberjack = null;
        float nearestLumberjackDist = 1000000f;
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
        boolean hasMoved = false; //Have I moved already?

        //Process nearby enemies
        for (RobotInfo enemy : nearbyEnemies){
            if (enemy.getType() == RobotType.LUMBERJACK){
                float dist = myLoc.distanceSquaredTo(enemy.getLocation());
                if (dist < nearestLumberjackDist){
                    nearestLumberjackDist = dist;
                    nearestLumberjack = enemy;
                }
                nearbyLumberjacks.add(enemy);
            }
            else if (enemy.getType() == RobotType.GARDENER){
                float dist = myLoc.distanceSquaredTo(enemy.getLocation());
                if (dist < nearestGardenerDist){
                    nearestGardenerDist = dist;
                    nearestGardener = enemy;
                }
            }
            else if (enemy.getType() != RobotType.ARCHON){
                nearbyCombatUnits.add(enemy);
            }
        }

        //System.out.println(nearbyLumberjacks);

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SCOUT_COMBAT_SENSE_RADIUS, rc.getTeam());

        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);


        float nearestDistToGardener = 10000000f;
        MapLocation bestMoveLoc = null;
        //See if there is any tree available
        for (TreeInfo tree : nearbyTrees){
            boolean canMove = true;
            for (RobotInfo lumberjack : nearbyLumberjacks){
                //System.out.println("Dist: "+tree.getLocation().distanceTo(lumberjack.getLocation()));
                if (tree.getLocation().distanceTo(lumberjack.getLocation()) <= LUMBERJACK_AVOIDANCE_RANGE_SCOUT){
                    canMove = false;
                    break;
                }
            }
            //Direct towards nearest gardener
            if (canMove && nearestGardener != null){
                MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestGardener.getLocation()), 0.01f);
                if (rc.canMove(moveLoc)){
                    float distToGardener = moveLoc.distanceTo(nearestGardener.getLocation());
                    if (distToGardener < nearestDistToGardener){
                        nearestDistToGardener = distToGardener;
                        bestMoveLoc = moveLoc;
                    }
                }
            }
            else if (canMove && nearestGardener == null){
                //Position away from any bullet
                if (nearbyBullets.length > 0){
                    MapLocation newLoc = tree.getLocation();
                    BulletInfo relBullet = null;
                    for (BulletInfo bullet : nearbyBullets){
                        if (willCollideWithMe(newLoc, bullet)){
                            relBullet = bullet;
                            break;
                        }
                    }
                    MapLocation moveLoc;
                    if (relBullet == null){
                        moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearbyBullets[0].getLocation()), 0.01f);
                    }
                    else{
                        moveLoc = tree.getLocation().add(relBullet.getDir(), 0.01f);
                    }
                    if (rc.canMove(moveLoc)  && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
                        try{
                            rc.move(moveLoc);
                            hasMoved = true;
                            return;
                        }
                        catch (GameActionException e){
                            e.printStackTrace();
                        }
                    }
                }
                else if (nearestEnemy != null){ //Position away from some enemy
                    MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestEnemy.getLocation()), 0.01f);
                    if (rc.canMove(moveLoc) && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
                        try{
                            rc.move(moveLoc);
                            hasMoved = true;
                            return;
                        }
                        catch (GameActionException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        if (!hasMoved && bestMoveLoc != null){
            try{
                if (rc.canMove(bestMoveLoc)){
                    if (bestMoveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
                        rc.move(bestMoveLoc);
                        hasMoved = true;
                        return;
                    }
                    else{
                        nearestGardener = new RobotInfo(nearestGardener.ID, nearestGardener.team, nearestGardener.type,
                                bestMoveLoc, nearestGardener.health, nearestGardener.attackCount, nearestGardener.moveCount);
                    }
                }
            }
            catch (GameActionException e){
                e.printStackTrace();
            }
        }
        //If no trees available then try the 8 directions

        if (hasMoved){
            return;
        }


        float maxScore = -1000000f;
        MapLocation prevtarget = target;
        if (nearestGardener != null){
            target = nearestGardener.getLocation();
        }
        else{
            //Is this a good idea?
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
                ////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                float score = getScoutScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
                //System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
                //System.out.println("Score: "+score);
                if (score > maxScore){
                    maxScore = score;
                    finalDir = chosenDir;
                }
            }
            else{
                ////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
            }
        }

        //Sometimes the best move is not to move
        float score = getScoutScore(myLoc, nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
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

        //Conduct attack
        if (rc.canFireSingleShot()){
            MapLocation attackLoc = null;
            if (nearestGardener != null){
                attackLoc = nearestGardener.getLocation();
            }
            else if (nearestLumberjack != null){
                attackLoc = nearestLumberjack.getLocation();
            }
            else{
                attackLoc = nearestEnemy.getLocation();
            }
            if (attackLoc != null){
                Direction shotDir = myLoc.directionTo(attackLoc);
                MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
                boolean safeToShoot = true;
                for (RobotInfo ally : nearbyAllies){
                    if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
                        safeToShoot = false;
                        break;
                    }
                }
                //Shoot if at point blank range
                if (!safeToShoot){
                    if (nearestEnemy.getLocation().distanceTo(MY_LOCATION) < rc.getType().bodyRadius + nearestEnemy.getRadius() + POINT_BLANK_RANGE){
                        safeToShoot = true;
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


        if (nearestGardener == null){
            return;
        }
        return;
    }

}
