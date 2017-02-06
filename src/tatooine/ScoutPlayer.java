package tatooine;

import battlecode.common.*;

import java.util.ArrayList;

public class ScoutPlayer extends BasePlayer {

    public ScoutPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
            shakeTrees();
        }
    }

    private void initPlayer(){
        return;
    }

    private void run() {
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        //Broadcast whether scout is present
		/*try {
			rc.broadcast(21, rc.getRoundNum());
		} catch (GameActionException e) {
			e.printStackTrace();
		}*/
        //Set important variables
        if (!isSet){
            target = ENEMY_ARCHON_LOCATION;
            visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
            patience = 0;
            inCombat = false;
            isSet = true;
        }
        boolean lone_archon = (SURROUNDING_ROBOTS_ENEMY.length > 0 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON && SURROUNDING_ROBOTS_ENEMY.length == 1);

        //Look for nearby enemies
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            //Don't engage early game ARCHON
            inCombat = true;
            if (rc.getRoundNum() < MID_GAME && lone_archon){
                System.out.println("Only archon detected...");
                inCombat = false;
            }
            //Go into combat
            target = null;

            if (SURROUNDING_ROBOTS_ENEMY.length > 1 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                //If more than one enemy and enemy not an archon
                RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                spotEnemy(nearestEnemy);
            }
            else if (!lone_archon) {
                //Call all noncombat soldiers to enemy position
                RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
                spotEnemy(nearestEnemy);
            }
        }
        if (!inCombat){
            //If someone has spotted an enemy. Scouts not to be used to fight after early game
            try{
                if (rc.getRoundNum() <= MID_GAME) {
                    int turnSpotted = rc.readBroadcast(SPOTTED_TURN);
                    int locationSpotted = rc.readBroadcast(SPOTTED_LOCATION);
                    int enemySpotted = rc.readBroadcast(SPOTTED_ENEMY_TYPE);
                    if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE) {
                        target = intToMapLocation(locationSpotted);
                    }
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
            RobotInfo nearestEnemy = null;
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            //Don't engage early game ARCHON
            if (rc.getRoundNum() < MID_GAME){
                if (lone_archon){
                    //Snap out of combat
                    target = null;
                    inCombat = false;
                }
                else if (SURROUNDING_ROBOTS_ENEMY.length > 1 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    //If more than one enemy and enemy not an archon
                    nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                }
            }

            float nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);

            if (!lone_archon) {

                //Attack before moving
                //System.out.println("Nearest Enemy Dist: "+nearestdist);
                if (nearestdist <= SCOUT_MICRO_DIST || nearbyBullets.length > 0) {
                    //System.out.println("I'm making my COMBAT MOVE");
                    scoutCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                } else {
                    //Move to nearest enemy's position
                    target = nearestEnemy.getLocation();
                    moveToTarget(MY_LOCATION);
                }

                MY_LOCATION = rc.getLocation();
            }
            else{
                //Snap out of combat
                inCombat = false;
                if (target == null){
                    //A somewhat ballsy change
                    MY_LOCATION = rc.getLocation();
                    float furthestDist = -1000000f;
                    MapLocation furthestArchonLoc = null;
                    MapLocation [] initArchonLocs = rc.getInitialArchonLocations(rc.getTeam().opponent());
                    for (int i = 0; i < initArchonLocs.length; i++){
                        float dist = MY_LOCATION.distanceTo(initArchonLocs[i]);
                        if (dist > furthestDist){
                            furthestDist = dist;
                            furthestArchonLoc = initArchonLocs[i];
                        }
                    }
                    target = furthestArchonLoc;
                    //target = newScoutingLocation(20);
                }
                if (target != null){
                    moveToTarget(MY_LOCATION);
                    if (target != null){
                        if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
                            target = newScoutingLocation(20);
                        }
                    }
                }
            }

        }
        else{
            if (target == null){
                //A somewhat ballsy change
                MY_LOCATION = rc.getLocation();
                float furthestDist = -1000000f;
                MapLocation furthestArchonLoc = null;
                MapLocation [] initArchonLocs = rc.getInitialArchonLocations(rc.getTeam().opponent());
                for (int i = 0; i < initArchonLocs.length; i++){
                    float dist = MY_LOCATION.distanceTo(initArchonLocs[i]);
                    if (dist > furthestDist){
                        furthestDist = dist;
                        furthestArchonLoc = initArchonLocs[i];
                    }
                }
                target = furthestArchonLoc;
            }
            if (target != null){
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
                moveToTarget(MY_LOCATION);
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
