package YavinIV;

import battlecode.common.*;

import java.util.ArrayList;

public class TankPlayer extends BasePlayer {

    static float TANK_SHOOT_RANGE= 64f;   		        //Squared distance tanks engages at
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    static float movement_stuck_radius = RobotType.TANK.strideRadius/2 + EPSILON;
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

    private float ARCHON_BONUS_SCORE = 5f;                      //Increasing this makes it more likely to go after archons
    private float GARDENER_BONUS_SCORE = 2f;                    //More likely to target gardeners
    private BulletInfo [] nearbyBullets = null;
    private int[] target_data = null;

    public TankPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer(){
        //Setup scanner range based on robot type
        MOVEMENT_SCANNING_DISTANCE = 4.1f;
        MOVEMENT_SCANNING_RADIUS = 2.1f;
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
            System.out.println(Broadcaster.location_to_channel(target));
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
                    MapLocation targetLoc = Broadcaster.channel_to_location(gridCoded);
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
            System.out.println(Broadcaster.location_to_channel(target));
        }

        //Movement
        //Engage enemy if in combat
        if (inCombat){
            RobotInfo targetEnemy = scoreTargetEnemy(SURROUNDING_ROBOTS_ENEMY);
            float nearestdist = MY_LOCATION.distanceSquaredTo(targetEnemy.location);
            if (nearestdist <= TANK_MICRO_DIST || nearbyBullets.length > 0) {
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
                moveToTargetExperimental(MY_LOCATION, SOLDIER_CHASING_GRANULARITY, search_dir, am_stuck);
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
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck);
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
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck);
                    if (target != null){
                        if (debug) rc.setIndicatorDot(target, 255, 255, 255);
                        System.out.println(Broadcaster.location_to_channel(target));
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
                    moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(Broadcaster.location_to_channel(target));
            //if (debug) rc.setIndicatorLine(MY_LOCATION, target, 255, 0, 0);
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

        if (target != null){
            if (debug) rc.setIndicatorDot(target, 255, 255, 255);
            System.out.println(Broadcaster.location_to_channel(target));
        }
    }

    //==========================================================================
    //<-------------- THE ALMIGHTY PATHFINDING THINGUMMYWHAT ----------------->
    //==========================================================================
    //Special case for tanks to avoid running over friendly trees...
    protected void moveToTargetExperimental(MapLocation myLoc, int GRANULARITY, boolean rightBot, boolean stuck){
        if (rc.hasMoved()) return;
        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<GRANULARITY;i++){
            if (absRad(initDir.radians) > SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (absRad(initDir.radians) - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir++, GRANULARITY);

        if (debug) rc.setIndicatorLine(myLoc, myLoc.add(SENSOR_ANGLES[sensorDir], 2f), 0, 255, 0);

        //Get a list of nearby friendly trees
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.TANK.bodyRadius+RobotType.TANK.strideRadius+EPSILON, rc.getTeam());

        //Show movement direction
//        Direction cDir = new Direction(SENSOR_ANGLES[0]);
//        MapLocation scanning_loc = null;
//        for (int i=0;i<GRANULARITY;i++){
//            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
//            if (canMoveTank(cDir)) rc.setIndicatorDot(scanning_loc, 0, 0, 255);
//            else rc.setIndicatorDot(scanning_loc, 255, 0, 0);
//            cDir = new Direction(SENSOR_ANGLES[i]);
//        }
//        rc.setIndicatorDot(MY_LOCATION.add(initDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 0);

        //Bug pathing algorithm!! :O
        //Right turning bot...

        //Try trivial case

        try {
            broadcaster.updateBoundaries(myLoc);
            if (canMoveTank(initDir, nearbyTrees)){
                rc.move(initDir);
                return;
            }
            else {
                //Uncomplicated bug pathing code
                if (rightBot) {
                    for (int i = 1; i < GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir + i, GRANULARITY)]);
                        if (canMoveTank(tryDir, nearbyTrees)) {
                            rc.move(tryDir);
                            if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                else {
                    for (int i = 1; i < GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir - i, GRANULARITY)]);
                        if (canMoveTank(tryDir, nearbyTrees)) {
                            rc.move(tryDir);
                            if (debug) rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return;
    }

    private boolean canMoveTank(Direction tryDir, TreeInfo[] nearbyTrees){
        MapLocation tryLoc = MY_LOCATION.add(tryDir, RobotType.TANK.strideRadius);
        for (TreeInfo t:nearbyTrees){
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
        System.out.println("Location of message: "+Broadcaster.location_to_channel(targetLoc));
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
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck);
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
                moveToTargetExperimental(MY_LOCATION, SOLDIER_CHASING_GRANULARITY, search_dir, am_stuck);
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
