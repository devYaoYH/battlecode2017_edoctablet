package tatooine;

import battlecode.common.*;

import java.util.ArrayList;

public class LumberjackPlayer extends BasePlayer {

    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable;                 //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise

    public LumberjackPlayer(RobotController rc) throws GameActionException {
        super(rc);
        initPlayer();
        for(;;Clock.yield()){
            startRound();
            run();
            endRound();
        }
    }

    private void initPlayer(){
        return;
    }

    private void run(){
        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        //Set important variables
        if (!isSet){
            target = null;
            targetInfo = null;
            visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
            patience = 0;
            isInterruptable = false;
            isSet = true;
            inCombat = false;
        }
        //Look for nearby enemies
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            //Go into combat
            inCombat = true;
            spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
        }
        if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
            //Snap out of combat
            inCombat = false;
        }
        if (inCombat){
            //Find nearest enemy
            float nearestdist = 1000000f;
            RobotInfo nearestEnemy = null;
            for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
                float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
                if (dist < nearestdist){
                    nearestdist = dist;
                    nearestEnemy = enemy;
                }
            }
            //Move to nearest enemy's position
            target = nearestEnemy.getLocation();
            moveToTarget(MY_LOCATION);
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
            //Chop down trees if not in combat
            if (target == null || isInterruptable){ //Find a new target
                TreeInfo nearestTree = null;
                if (SURROUNDING_TREES_NEUTRAL.length>0) nearestTree = SURROUNDING_TREES_NEUTRAL[0];
                if (nearestTree != null){ //No more trees
                    target = nearestTree.getLocation();
                    targetInfo = nearestTree;
                    isInterruptable = false;
                }
                else if (target == null){ //Don't replace with scouting if target is not null
                    //No more trees => TODO: Some scouting
                    target = newScoutingLocation(20); //Go to a random scouting location with 20 attempts
                    isInterruptable = true;
                }
            }
            if (target != null){
                //Check if has approached target
                boolean done = false;
                if (rc.canChop(target)){
                    try{
                        //System.out.println("Chop chop");
                        rc.chop(target);
                        done = true;
                    }
                    catch (GameActionException e){
                        e.printStackTrace();
                    }
                }

                //If target is gone
                if (MY_LOCATION.isWithinDistance(target, 1)){
                    target = null;
                }
				/*if (target != null){
					System.out.println("Target: " + target);
				}
				else{
					System.out.println("Null Target");
				}*/
                //Move toward target if not in range
                if (!done && target != null){
                    moveToTarget(MY_LOCATION);
                }
            }
        }
    }

}
