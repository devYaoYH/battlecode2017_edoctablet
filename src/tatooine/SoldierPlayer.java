package tatooine;

import battlecode.common.*;

import java.util.ArrayList;

public class SoldierPlayer extends BasePlayer {

    //Soldier specific variables
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    static float movement_stuck_radius = RobotType.SOLDIER.strideRadius/2 + EPSILON;
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

    public SoldierPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer(){//Set important variables
        target = ENEMY_ARCHON_LOCATION;
        visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
        patience = 0;
        inCombat = false;
        return;
    }

    private void run() {
        boolean lone_archon = false;

        //Look for nearby enemies
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            if (rc.getRoundNum() < EARLY_GAME && SURROUNDING_ROBOTS_ENEMY.length == 1){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Only archon detected...");
                    inCombat = false;
                    lone_archon = true;
                }
                else inCombat = true;
            }
            else inCombat = true;
            //Go into combat
            target = null;

            if(!lone_archon) {
                //Call all noncombat soldiers to enemy position
                RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
                spotEnemy(nearestEnemy);
            }
        }
        if (!inCombat){
            //If someone has spotted an enemy
            try{
                int turnSpotted = rc.readBroadcast(SPOTTED_TURN);
                int locationSpotted = rc.readBroadcast(SPOTTED_LOCATION);
                int enemySpotted = rc.readBroadcast(SPOTTED_ENEMY_TYPE);
                //Alarm all other soldiers
                if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE){
                    target = intToMapLocation(locationSpotted);
                }
            }
            catch (GameActionException e){
                e.printStackTrace();
            }
        }
        if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
            //Snap out of combat
            target = null;
            inCombat = false;
        }
        if (inCombat){
            //Find nearest enemy
            float nearestdist = 1000000f;
            RobotInfo nearestEnemy = null;
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[0].location);
            //Don't engage early game ARCHON
            if (rc.getRoundNum() < EARLY_GAME){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Ignoring early game archon");
                    if (SURROUNDING_ROBOTS_ENEMY.length == 1){
                        target = null;
                        inCombat = false;
                        lone_archon = true;
                    }
                    else {
                        nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                        nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[1].location);
                    }
                }
            }

            System.out.println("Right before moving: "+Integer.toString(Clock.getBytecodeNum()));

            if (!lone_archon) {
                //rc.setIndicatorDot(MY_LOCATION, 255, 0, 255);

                //Attack before moving
                //System.out.println("Nearest Enemy Dist: "+nearestdist);
                if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0) {
                    //System.out.println("I'm making my COMBAT MOVE");
                    soldierCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                    //rc.setIndicatorDot(rc.getLocation().add(new Direction(0f), 0.1f), 255, 100, 100);
                    System.out.println("After micro: " + Integer.toString(Clock.getBytecodeNum()));
                } else {
                    //Move to nearest enemy's position
                    target = nearestEnemy.getLocation();
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
                    moveToTargetExperimental(MY_LOCATION, SOLDIER_CHASING_GRANULARITY, search_dir, am_stuck);
                    //rc.setIndicatorDot(rc.getLocation().add(new Direction(0f), 0.1f), 100, 255, 100);
                    System.out.println("After moving: " + Integer.toString(Clock.getBytecodeNum()));
                }

                //MY_LOCATION = rc.getLocation();
            }

        }
        else{
            if (target == null){
                target = newScoutingLocation(20);
            }
            if (target != null){
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
                if (am_stuck){
                    search_dir = !search_dir;
                    am_stuck = false;
                    staying_conut = 0;
                }
                moveToTargetExperimental(MY_LOCATION, SENSOR_GRANULARITY, search_dir, am_stuck);
                if (target != null){
                    if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
                        target = newScoutingLocation(20);
                    }
                }
            }
        }
        if (target != null){
            //System.out.println("Target: "+target);
            //System.out.println("myPos: "+myLoc);
            //System.out.println("inCombat: "+inCombat);
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
            rc.setIndicatorDot(prevLocation, 255, 100, 100);
            rc.setIndicatorDot(MY_LOCATION, 100,255, 125);
            centerPoint = MY_LOCATION.add(MY_LOCATION.directionTo(prevLocation), MY_LOCATION.distanceTo(prevLocation)/2);
            prevLocation = MY_LOCATION;
            MY_LOCATION = rc.getLocation();
            System.out.println("Evaluated previous distance: "+Float.toString(MY_LOCATION.distanceTo(centerPoint)));
            if (MY_LOCATION.distanceTo(centerPoint) < movement_stuck_radius && !inCombat){
                rc.setIndicatorDot(centerPoint, 255,0, 0);
                staying_conut++;
            }
            else{
                staying_conut = 0;
                rc.setIndicatorDot(centerPoint, 100, 100, 255);
            }
        }
        if (staying_conut > STUCK_THRESHOLD_TURNS){
            System.out.println("Oh oops, I'm STUCK!!!");
            am_stuck = true;
        }
        System.out.println("After computing stuck: "+Integer.toString(Clock.getBytecodeNum()));

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
            if (willCollideWithMe(loc, bullet)){
                ans -= bullet.getDamage() * 1000f;
            }
        }

        //Be as far as possible from the soldiers
        for (RobotInfo robot : nearbyCombatRobots){
            ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
        }

        //Get closer to gardeners
        if (loc.distanceTo(target) <= MY_LOCATION.distanceTo(target)) ans += 50;

        rc.setIndicatorDot(loc, 255, Math.max(255-(int)(ans/-10), 0), 0);

        return ans;
    }

    private void soldierCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
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

        //Avoid friendly fire
        RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SOLDIER_COMBAT_SENSE_RADIUS, rc.getTeam());
        //Should I use -1?

        boolean hasFired = false;

        //Fire switches
        boolean should_fire_pentad = true;
        boolean should_fire_triad = true;
		/*if (nearestEnemy != null){
			if (myLoc.distanceTo(nearestEnemy.location) > PENTAD_EFFECTIVE_DISTANCE){
				should_fire_pentad = false;
				if (nearestEnemy.type == RobotType.LUMBERJACK || nearestEnemy.type == RobotType.ARCHON || nearestEnemy.type == RobotType.SCOUT ){
					should_fire_triad = false;
				}
			}
		}*/


        nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        System.out.println("How many frigging bullets are there?? "+Integer.toString(nearbyBullets.length));

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

        rc.setIndicatorLine(MY_LOCATION, target, 0, 255, 0);

        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        Direction finalDir = null; //Final move direction

        //Sometimes the best move is not to move
        float score = getSoldierScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
        if (score > maxScore){
            maxScore = score;
            finalDir = null;
        }

        for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
            if (Clock.getBytecodeNum() > BYTE_THRESHOLD) continue;
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
            }

            if (rc.canMove(chosenDir)){
                ////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
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

        if (finalDir != null){
            target = MY_LOCATION.add(finalDir, 5f);
            if (am_stuck){
                search_dir = !search_dir;
                am_stuck = false;
                staying_conut = 0;
            }
            moveToTargetExperimental(MY_LOCATION, SOLDIER_CHASING_GRANULARITY, search_dir, am_stuck);
        }

        //Conduct attack
        //Avoid friendly fire
        if (rc.canFirePentadShot() && should_fire_pentad){
            MapLocation attackLoc = null;
            attackLoc = nearestEnemy.getLocation();
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
            attackLoc = nearestEnemy.getLocation();
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
        if (!hasFired && rc.canFireSingleShot()){
            MapLocation attackLoc = null;
            attackLoc = nearestEnemy.getLocation();
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
