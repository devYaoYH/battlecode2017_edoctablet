package tatooine;

import battlecode.common.*;

import java.util.ArrayList;

public class TankPlayer extends BasePlayer {

    static float TANK_SHOOT_RANGE= 64f;   		        //Squared distance tanks engages at

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
    }

    private void run() {
        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        //Set important variables
        if (!isSet){
            target = null;
            visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
            patience = 0;
            inCombat = false;

            isSet = true;
        }
        //Look for nearby enemies
        if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            //Go into combat
            inCombat = true;
            target = null;
            //Call all noncombat soldiers to enemy position
            float nearestdist = 1000000f;
            RobotInfo nearestEnemy = null;
            for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
                float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
                if (dist < nearestdist){
                    nearestdist = dist;
                    nearestEnemy = enemy;
                }
            }
            spotEnemy(nearestEnemy);
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
            //No micro here => Just shooting bullets at the nearest enemy
            if (MY_LOCATION.isWithinDistance(target, TANK_SHOOT_RANGE)){
                try {
                    if (rc.canFirePentadShot()){
                        //todo Check for friendly fire
						/*RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
						boolean friendlyFire = false;
						for(RobotInfo f:friendly){
							if(intersectLineCircle(myLoc,f.location,myLoc.directionTo(f.location))){
								friendlyFire = true;
							}
						}
						if(!friendlyFire) rc.firePentadShot(myLoc.directionTo(target));*/
                        rc.firePentadShot(MY_LOCATION.directionTo(target));
                    }
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }

        }
        else{
            //Look for trees to crush! hehehe :D
            TreeInfo closest_tree = null;
            if (SURROUNDING_TREES_ENEMY.length > 0) closest_tree = SURROUNDING_TREES_ENEMY[0];
            else if (SURROUNDING_TREES_NEUTRAL.length > 0) closest_tree = SURROUNDING_TREES_NEUTRAL[0];

            if (closest_tree != null){
                target = closest_tree.location;
            }

            if (target == null){
                target = newScoutingLocation(20);
            }
            else{
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
                moveToTarget(MY_LOCATION);
                if (target != null){
                    if (MY_LOCATION.distanceSquaredTo(target) <= TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
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

}
