package Scarif_stable;

import battlecode.common.*;

import java.util.ArrayList;

public class SoldierPlayer extends BasePlayer {

    //Soldier specific variables
    private MapLocation prevLocation = null;                    //Previous location
    static int stuckRounds = 10;                                //How many rounds before I'm stuck
    private int cooldownRounds = 15;
    private int cooldownTimer = 0;                              //Turns to run slug path before reverting to direct pathing
    static float movement_stuck_radius = RobotType.SOLDIER.strideRadius/8*stuckRounds;       //Stuck distance for n rounds
    private boolean am_stuck = false;                           //I am stuck where I am...
    private boolean cooldown_active = false;                    //Cooldown period where direct pathing is disabled
    private boolean search_dir = true;                          //Right/Left bot, true == Right bot
    private float prev_distance_to_target = 99999f;             //Used for determining when to resume direct pathing

    private int SLUG = 3;                                       //How many levels of slugging

    private int gridTimer = THE_FINAL_PROBLEM;
    private int timeToTarget = 0;                               //How long to target
    private float DISTANCE_COOLDOWN_ALLOWANCE = 2.7f;           //Movement must be close than nf before direct pathing kicks in again

    //Scoring weights (tunes soldier response to different targets)
    private float CONTACTREP_BONUS_SCORE = 3f;                  //Bonus towards going back to defend
    private float ARCHON_BONUS_SCORE = 0.7f;                    //Increasing this makes it more likely to go after archons
    private float ARCHON_PENALTY_SCORE = -7f;                   //Makes it less likely to attack archons when other enemies are nearby
    private float GARDENER_BONUS_SCORE = 2f;                    //More likely to target gardeners
    private float CLOSEST_BONUS_SCORE = 2f;                     //Tie breaker for closer enemy
    private float LUMBERJACK_BONUS_SCORE = 100f;                //Bonus score for lumberjacks within strike range of their attacks
    private float LUMBERJACK_STRIKE_RADIUS = 3f;                //Distance to activate lumberjack bonus (cuz they'll strike ya!)
    private BulletInfo [] nearbyBullets = null;
    private int[] target_data = null;

    //Fires an alternating spread of bullets in targeted direction (rather than constant direct firing)
    private float TRIAD_DEFLECTION = DEG_5+DEG_1*2;
    private float PENTAD_DEFLECTION = DEG_5;

    /*
    Our Soldiers/Tanks/Scouts all run the same basic structure of code. Had we built our bot up from the start using an OOP framework,
    there'd probably be a ton of common functions run by all 3 of these robots. As it is, hacking is messy >.< a lot of spaghetti ensues...
    */
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
        debug = true;   //Woot! enable for tiny soldier daggers :)
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
                    //Timer for soldier to change target
                    else if (rc.getRoundNum() > gridTimer){
                        sitRep(target);
                        MapLocation next_grid = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (next_grid == MY_LOCATION){
                            //No target found through bfs - default target
                            target = getDefaultLocation();
                        }
                        else{
                            target = next_grid;
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
            if (SURROUNDING_ROBOTS_ENEMY.length > 0){
                inCombat = true;
                target = null;
                spotRep();
            }
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
                        if (target != ENEMY_ARCHON_LOCATION || MY_LOCATION.distanceTo(next_target) < MY_LOCATION.distanceTo(target)) {
                            //I'll rather go disrupt enemy's farming than respond to contactrep/spotrep if I'm close to original target
                            if (cur_target_data[0] == 0) {
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
            //Hueristic for soldiers to pick highest-threat target and engage (in the case of multiple enemies in sight radius)
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
                        //Rushes towards enemy archon by default
                        int[] enemyArchonInfo = broadcaster.readGrid(ENEMY_ARCHON_LOCATION);
                        if (enemyArchonInfo[0] == 0 && rc.readBroadcast(ENEMY_ARCHON_KILLED) == 0){
                            target = ENEMY_ARCHON_LOCATION;
                        }
                        else{
                            MapLocation next_grid = broadcaster.nextEmptyGrid(MY_LOCATION);
                            if (next_grid == MY_LOCATION){
                                //No target found through bfs - default location
                                target = getDefaultLocation();
                            }
                            else{
                                target = next_grid;
                            }
                        }
                        //target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        timeToTarget = (int)(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/2));
                        gridTimer = rc.getRoundNum() + timeToTarget;
                        if (am_stuck){
                            search_dir = !search_dir;
                            am_stuck = false;
                        }
                        moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }
                else {
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
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
                    //Rushes towards enemy archon by default
                    int[] enemyArchonInfo = broadcaster.readGrid(ENEMY_ARCHON_LOCATION);
                    if (enemyArchonInfo[0] == 0 && rc.readBroadcast(ENEMY_ARCHON_KILLED) == 0){
                        target = ENEMY_ARCHON_LOCATION;
                    }
                    else{
                        MapLocation next_grid = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (next_grid == MY_LOCATION){
                            //No target found through bfs - default location
                            target = getDefaultLocation();
                        }
                        else{
                            target = next_grid;
                        }
                    }
                    //target = broadcaster.nextEmptyGrid(MY_LOCATION);
                    timeToTarget = (int)(MY_LOCATION.distanceTo(target)/(rc.getType().strideRadius/2));
                    gridTimer = rc.getRoundNum() + timeToTarget;
                    if (am_stuck){
                        search_dir = !search_dir;
                        am_stuck = false;
                    }
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

        //Indicators became quite messy after a while...
        //It is then my due moral responsibility to issue an epilepsy warning which follows the activation of these indicators
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
                    if (!am_stuck){
                        //just entering stuck mode
                        //todo distance activation for direct pathing
                        prev_distance_to_target = MY_LOCATION.distanceTo(target);
                        cooldownTimer = rc.getRoundNum() + cooldownRounds;
                    }
                    am_stuck = true;
                    cooldown_active = true;
                }
                else{
                    //todo distance activation for direct pathing
                    if (MY_LOCATION.distanceTo(target)+DISTANCE_COOLDOWN_ALLOWANCE < prev_distance_to_target && rc.getRoundNum() > cooldownTimer){
                        prev_distance_to_target = 99999f;
                        am_stuck = false;
                        cooldown_active = false;
                    }
                    //if (rc.getRoundNum() > cooldownTimer) cooldown_active = false;
                }
                prevLocation = MY_LOCATION;
            }
        }

        if (debug && target != null){
            if (cooldown_active) rc.setIndicatorDot(MY_LOCATION.add(MY_LOCATION.directionTo(target).opposite(), 1f), 255, 255, 0);
            else rc.setIndicatorDot(MY_LOCATION.add(MY_LOCATION.directionTo(target).opposite(), 1f), 0, 255, 255);
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
        //With this added piece of code, our soldier balls were much more effective in engaging the enemy
        //Those towards the back of the ball would spread out to the sides and surround the enemy's soldier ball in a major engagement
        //This dramatically increased the number of bullets per tick flying towards the enemy as more of our soldiers were able to shoot at the enemy :D
        //Sadly, we didn't face an all-out huge battle against anyone in this year's tournament :(
        if (MY_LOCATION.distanceSquaredTo(target) > SOLDIER_MICRO_DIST && MY_LOCATION.distanceSquaredTo(target) < SOLDIER_MICRO_DIST*16){
            float density_ahead = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target), 4f), 3f, ROBOTS);
            float density_left = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target).rotateLeftRads(DEG_60), 4f), 3f, ROBOTS);
            float density_right = getDensity(MY_LOCATION.add(MY_LOCATION.directionTo(target).rotateRightRads(DEG_60), 4f), 3f, ROBOTS);
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
        if (absRad(initDir.radians) - SENSOR_ANGLES[sensorDir] > (DEG_360/GRANULARITY/2)) sensorDir = absIdx(sensorDir + 1, GRANULARITY);

        if (debug) rc.setIndicatorLine(myLoc, myLoc.add(SENSOR_ANGLES[sensorDir], 2f), 0, 255, 0);

        try {
            broadcaster.updateBoundaries(myLoc);
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
                    if (tryDir != null && initDir.radiansBetween(tryDir) < closestAngle){
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

    //Default target for soldiers (in the case of everywhere being explored)
    private MapLocation getDefaultLocation(){
        //todo random directional target...(basically examplefuncsplayer)
        if (target != null && !hasReachedGrid(target)){
            return target;
        }
        return MY_LOCATION.add(new Direction((float)Math.random()*DEG_360), 25f);
    }

    //<---------------------------- SOLDIER MICRO ---------------------------->
    /*
    Towards the end of the competition, we focused majority of our efforts in improving our soldiers' micro
    As the meta moved to a more soldier-centric game, winning the initial soldier engagement usually decided the game
    Our soldiers have 2 main decision-making tools available to them:
        --> Report Scoring
            Once we had our Broadcaster/Message system in place, we could play around with how our units responded to such reports across the map
            So we tuned the soldier's response towards defending (if nearby) but mainly focused on converging upon bunches of enemy
            Our soldiers did pretty well in large engagements and we would see a 10v15 turn to our favor in scrims
        --> Enemy Scoring
            The naive method of simply targeting the nearest enemy doesn't really work out when there're multiple enemies in sight radius
            So we made a metric that scored each enemy based on their type, health, and distance to the targeting soldier
            We prioritize heavy hitting units (tanks>soldiers>lumberjacks) and give a bonus score to taking out gardeners
            Sacrificing that one soldier to take out a grove of 5/6 enemy bullet trees would set the enemy's economy back significantly
    */
    private float getReportScore(MapLocation targetLoc, Message message){
        System.out.println("RAW MESSAGE: "+Integer.toString(message.getRawMsg()));
        System.out.println("Enemy: "+robotType(message.getEnemy()));
        System.out.println("Enemy Number: "+Integer.toString(message.getNumberOfEnemies()));
        System.out.println("Alert Level: "+Integer.toString(message.getAlertLevel()));
        System.out.println("Location of message: "+ targetLoc);
        float score = 0f;
        float distance_metric = 100/(MY_LOCATION.distanceTo(targetLoc));
        int alert_level = message.getAlertLevel();
        boolean archon_present = (message.getArchonPresent() == 1);
        RobotType prevalent_enemy = message.getEnemy();
        int number_enemies = message.getNumberOfEnemies();
        //Respond to ContactRep
        int report_type = message.getMessageType();
        if (report_type == Message.CONTACT_REP){
            score += CONTACTREP_BONUS_SCORE;
        }
        score += alert_level + DANGER_RATINGS[robotType(prevalent_enemy)]*number_enemies/1000 + (archon_present?ARCHON_BONUS_SCORE:0f);
        System.out.println("Distance scaling: "+Float.toString(distance_metric)+" @ "+Float.toString(MY_LOCATION.distanceTo(targetLoc)));
        System.out.println("Score: "+Float.toString(score*distance_metric));
        System.out.println("=====================");
        //if (debug) rc.setIndicatorLine(MY_LOCATION, targetLoc, (int)Math.min(255, (score*distance_metric)*10+50), 50, 50);
        return score*distance_metric;
    }

    private RobotInfo scoreTargetEnemy(RobotInfo[] surrEnemies){
        RobotInfo best_target = null;
        float best_score = -999f;
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
            //cur_score += DANGER_RATINGS[robotType(r.type)]/10;
            //todo experimentally targeting low health enemies
            cur_score += DANGER_RATINGS[robotType(r.type)]/20;
            cur_score += DANGER_RATINGS[robotType(r.type)]/20*(r.type.maxHealth - r.health);    //Prioritize low-health targets
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

    private float getSoldierScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
        float ans = 0;
        //Avoid lumberjacks
        for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
            float dist = lumberjack.getLocation().distanceTo(loc);
            if (dist <= LUMBERJACK_AVOIDANCE_RANGE){
                //System.out.println(lumberjack.getLocation().distanceTo(loc));
                ans -= RobotType.LUMBERJACK.attackPower * 500f *  (LUMBERJACK_AVOIDANCE_RANGE - dist);
                //ans -= rc.getType().attackPower * 1000f;
            }
        }

        //Avoid bullets [Heavy penalty]
        for (BulletInfo bullet : nearbyBullets){
            //if (debug) rc.setIndicatorLine(bullet.location, bullet.location.add(bullet.dir, 1f), 255, 0, 0);
            if (willCollideWithMe(loc, bullet, 1)){
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

        //if (debug) rc.setIndicatorDot(loc, 255, Math.max(255-(int)(ans/-10), 0), 0);

        return ans;
    }

    /*
    Trigger-happy soldiers :P we immediately fire when we first see an enemy.
    After seeing many games with Oak's Disciples, we adopted their scattering of shots to make it harder for enemies to dodge
    So our shots go (left/center/right/center) and loops. The deflection is such that the concentration of firepower is
    still directed towards the center rather than evenly spread out (higher hit-probability since movement speeds were cut)
    */
    private void soldierPreemptiveStrike(RobotInfo targetedEnemy){
        if (targetedEnemy == null) return;
        //No point wasting bullets...if our shot will intersect with a tree, stop firing
        if (willCollideWithTree(MY_LOCATION.directionTo(targetedEnemy.location), rc.senseNearbyTrees(4f, null))) return;

        MapLocation myLoc = rc.getLocation();
        if (debug) rc.setIndicatorLine(myLoc, targetedEnemy.location, 255, 0, 255);
        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
        boolean should_fire = true;

        //When econs not good, don't spam Archon
        if (targetedEnemy.type == RobotType.ARCHON && rc.getRoundNum() < EARLY_GAME){
            should_fire_pentad = false;
            should_fire_triad = false;
            if (rc.getTeamBullets() < 53){
                should_fire = false;
            }
        }

        //todo disable early game pentads
        //This single if-statement improved our game performance significantly as it gave us a stronger starting economy
        if (rc.getRoundNum() < MID_GAME){
            should_fire_pentad = false;
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
                        //todo alternating shots
                        if ((rc.getRoundNum() % 4) % 2 == 0){
                            rc.firePentadShot(myLoc.directionTo(attackLoc));
                        }
                        else if (rc.getRoundNum() % 4 == 1){
                            //Left
                            rc.firePentadShot(myLoc.directionTo(attackLoc).rotateLeftRads(PENTAD_DEFLECTION));
                        }
                        else{
                            //Right
                            rc.firePentadShot(myLoc.directionTo(attackLoc).rotateRightRads(PENTAD_DEFLECTION));
                        }
                        //rc.firePentadShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
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
                        //todo alternating shots
                        if ((rc.getRoundNum() % 4) % 2 == 0){
                            rc.fireTriadShot(myLoc.directionTo(attackLoc));
                        }
                        else if (rc.getRoundNum() % 4 == 1){
                            //Left
                            rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateLeftRads(TRIAD_DEFLECTION));
                        }
                        else{
                            //Right
                            rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateRightRads(TRIAD_DEFLECTION));
                        }
                        //rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
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
                        rc.fireSingleShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
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
            }
            moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
            return;
        }
        MapLocation myLoc = rc.getLocation();
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();

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

        //When econs not good, don't spam Archon
        if (targetedEnemy.type == RobotType.ARCHON){
            if (rc.getRoundNum() < EARLY_GAME) {
                should_fire_pentad = false;
                should_fire_triad = false;
                if (rc.getTeamBullets() < 57) {
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

        //todo disable early game pentads
        if (rc.getRoundNum() < MID_GAME){
            should_fire_pentad = false;
        }

        //System.out.println(nearbyLumberjacks);
        System.out.println("How many frigging bullets are there?? "+Integer.toString(nearbyBullets.length));

        //Move in the 8 directions
        float maxScore = -1000000f;
        MapLocation prevtarget = target;
        target = targetedEnemy.getLocation();

        if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);

        if (targetedEnemy.type == RobotType.GARDENER || targetedEnemy.type == RobotType.ARCHON || targetedEnemy.type == RobotType.SCOUT){
            //Just push forward to gardener's/archon's position
            //If soldier is not already beside the target
            if (MY_LOCATION.distanceTo(targetedEnemy.location) > RobotType.SOLDIER.bodyRadius + targetedEnemy.type.bodyRadius + 1f) {
                if (am_stuck) {
                    search_dir = !search_dir;
                    am_stuck = false;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, cooldown_active, SLUG);
            }
            if (targetedEnemy.type == RobotType.SCOUT){
                //Senses whether to shoot
                if (willCollideWithTree(MY_LOCATION.directionTo(target), rc.senseNearbyTrees(4f, null))) return;
            }
        }
        else if (willCollideWithTree(MY_LOCATION.directionTo(target), rc.senseNearbyTrees(4f, null))){
            if (debug) rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 255);
            System.out.println("Direct path blocked...maneuvering to new position");
            if (rc.getTeamBullets() < CONSERVE_BULLETS_LIMIT) hasFired = true;
            if (am_stuck) {
                search_dir = !search_dir;
                am_stuck = false;
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
                    //if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                    score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
                    if (score > maxScore) {
                        maxScore = score;
                        finalDir = chosenDir;
                    }
                }
                else {
                    //if (debug) rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
                }
            }

            if (finalDir != null) {
                try {
                    tryMove(finalDir);
                } catch(Exception e){
                    e.printStackTrace();
                }
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
                        //todo alternating shots
                        if ((rc.getRoundNum() % 4) % 2 == 0){
                            rc.firePentadShot(myLoc.directionTo(attackLoc));
                        }
                        else if (rc.getRoundNum() % 4 == 1){
                            //Left
                            rc.firePentadShot(myLoc.directionTo(attackLoc).rotateLeftRads(PENTAD_DEFLECTION));
                        }
                        else{
                            //Right
                            rc.firePentadShot(myLoc.directionTo(attackLoc).rotateRightRads(PENTAD_DEFLECTION));
                        }
                        //rc.firePentadShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
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
                        //todo alternating shots
                        if ((rc.getRoundNum() % 4) % 2 == 0){
                            rc.fireTriadShot(myLoc.directionTo(attackLoc));
                        }
                        else if (rc.getRoundNum() % 4 == 1){
                            //Left
                            rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateLeftRads(TRIAD_DEFLECTION));
                        }
                        else{
                            //Right
                            rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateRightRads(TRIAD_DEFLECTION));
                        }
                        //rc.fireTriadShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
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
                        rc.fireSingleShot(myLoc.directionTo(attackLoc).rotateLeftRads(DEG_5).rotateRightRads(rand.nextFloat()*DEG_5*2));
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return;
    }

}
