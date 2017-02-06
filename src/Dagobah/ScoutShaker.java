package Dagobah;

import battlecode.common.*;

import java.util.ArrayList;

public class ScoutShaker extends BasePlayer{

    private int SCOUT_HIDE_TURN = 400; //Turn for scout to go into hiding
    private float ENEMY_AVOID_DIST = 6f; //Distance to avoid enemies at
    static int spawnedTurn;
    static TreeInfo targetInfo;
    static boolean isInterruptable; //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise

    public ScoutShaker(RobotController robotController) throws GameActionException {
        super(robotController);
        try{
            initPlayer();
            for (; ; Clock.yield()) {
                startRound();
                runScout();
                endRound();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void initPlayer(){
        target = null;
        targetInfo = null;
        visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
        patience = 0;
        isInterruptable = false;
        isSet = true;
        inCombat = false;
        spawnedTurn = rc.getRoundNum();
    }

    private void runScout() {
        //Broadcast whether scout is present
		/*try {
			rc.broadcast(21, rc.getRoundNum());
		} catch (GameActionException e) {
			e.printStackTrace();
		}*/
        MapLocation myLoc = rc.getLocation();
        //Set important variables
        //Look for nearby enemies
        RobotInfo [] nearbyEnemies = rc.senseNearbyRobots(ENEMY_AVOID_DIST, rc.getTeam().opponent());
        RobotInfo [] allNearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
        if (!inCombat && nearbyEnemies.length > 0){
            //Go into combat
            inCombat = true;
            target = null;
        }
        if (inCombat && nearbyEnemies.length == 0){
            //Snap out of combat
            inCombat = false;
            target = null;
        }
        if (inCombat){
            //System.out.println("In Combat");
            //Avoid combat => run away from enemy
            //Find nearest enemy
            float nearestdist = 1000000f;
            RobotInfo nearestEnemy = null;
            for (RobotInfo enemy : nearbyEnemies){
                float dist = enemy.getLocation().distanceSquaredTo(myLoc);
                if (dist < nearestdist){
                    nearestdist = dist;
                    nearestEnemy = enemy;
                }
            }
            if (nearestEnemy.getLocation().distanceTo(myLoc) < SCOUT_MICRO_DIST || nearbyBullets.length > 0){
                scoutCombatMove(nearestEnemy, nearbyEnemies, nearbyBullets);
            }
            //Attack nearest enemy if possible
            //No micro here => He should micro to a position with the most enemies and least allies
            if (rc.canStrike()){
                try {
                    rc.strike();
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }
        }
        else{
            if (rc.getRoundNum() > spawnedTurn + SCOUT_HIDE_TURN){
                target = new MapLocation(0,0);
                try{
                    int to_donate = rc.readBroadcast(STAYING_ALIVE);
                    if (to_donate != 0){
                        scout_donate();
                    }
                }
                catch (GameActionException e){
                    e.printStackTrace();
                }
            }
            if (target != null){
                System.out.println("Target: "+target);
            }
            else{
                System.out.println("Target: null");
            }
            //Chop down trees if not in combat
            //System.out.println("Running this part");
            if (target == null || isInterruptable){ //Find a new target
                //System.out.println("Running this other part");
                TreeInfo [] nearbyTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                TreeInfo nearestTree = null;
                //Find nearest tree
                float minDist = 100000f;
                for (TreeInfo tree : nearbyTrees){
                    boolean isAccessible = true;
                    for (RobotInfo enemy : allNearbyEnemies){
                        if (tree.getLocation().isWithinDistance(enemy.getLocation(), ENEMY_AVOID_DIST + rc.getType().strideRadius)){
                            isAccessible = false;
                            break;
                        }
                    }
                    if (!isAccessible){
                        continue;
                    }
                    //System.out.println("Tree (ID: "+tree.getID()+") with "+tree.getContainedBullets()+" bullets spotted");
                    if (tree.getContainedBullets() == 0){
                        continue;
                    }
                    //System.out.println("Tree (ID: "+tree.getID()+") with "+tree.getContainedBullets()+" bullets eligible");
                    float dist = myLoc.distanceSquaredTo(tree.getLocation());
                    if (dist < minDist){
                        minDist = dist;
                        nearestTree = tree;
                    }
                }
                if (nearestTree != null){ //No more trees
                    System.out.println("Nearest Tree at: "+nearestTree.getLocation());
                    target = nearestTree.getLocation();
                    targetInfo = nearestTree;
                    isInterruptable = false;
                }
                else if (target == null){ //Don't replace with scouting if target is not null
                    //No more trees => TODO: Some scouting
                    try {
                        target = broadcaster.nextEmptyGrid(MY_LOCATION);
                        if (target == MY_LOCATION){
                            target = ENEMY_ARCHON_LOCATION;
                        }
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                    isInterruptable = true;
                }
            }
            if (target != null){
                //Check if has approached target
                boolean done = false;
                if (rc.canShake(target)){
                    try{
                        rc.shake(target);
                        target = null;
                        System.out.println("Target shaken reset");
                        done = true;
                    }
                    catch (GameActionException e){
                        e.printStackTrace();
                    }
                }

                //If target is gone
                if (target != null && myLoc.isWithinDistance(target, 1f)){
                    target = null;
                    System.out.println("Target close rest");
                }

                //Move toward target if not in range
                if (!done && target != null){
                    moveToTarget(myLoc);
                }
            }
        }
    }

    //A heuristic to help scouts move in combat
    private void scoutCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
        MapLocation myLoc = rc.getLocation();
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        float nearestLumberjackDist = 1000000f;
        ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
        ArrayList<RobotInfo> nearbyEnemyUnits = new ArrayList<RobotInfo>();
        boolean hasMoved = false; //Have I moved already?

        //Process nearby enemies
        for (RobotInfo enemy : nearbyEnemies){
            if (enemy.getType() == RobotType.LUMBERJACK){
                nearbyLumberjacks.add(enemy);
            }
            else{
                nearbyEnemyUnits.add(enemy);
            }
        }

        float maxScore = -100000f;
        target = rc.getInitialArchonLocations(rc.getTeam())[0];
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
                //rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
                float score = getScoutScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyEnemyUnits, nearbyBullets);
                //System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
                //System.out.println("Score: "+score);
                if (score > maxScore){
                    maxScore = score;
                    finalDir = chosenDir;
                }
            }
            else{
                //rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
            }
        }

        //Sometimes the best move is not to move
        float score = getScoutScore(myLoc, nearbyLumberjacks, nearbyEnemyUnits, nearbyBullets);
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
    }

    //Scout Score for micro
    private float getScoutScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyEnemyRobots, BulletInfo[] nearbyBullets){

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

        //Be as far as possible from the soldiers
        for (RobotInfo robot : nearbyEnemyRobots){
            ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
        }
        //System.out.println("Score: "+ans);
        return ans;
    }

    static void scout_donate(){
        //An endgame donate that uses up all your bullets except 50
        try {
            rc.donate((float)(Math.floor((rc.getTeamBullets() - 50) / rc.getVictoryPointCost()) * rc.getVictoryPointCost()));
        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

}
