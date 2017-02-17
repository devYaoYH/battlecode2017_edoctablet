package TemplatePlayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.MapLocation;

public class MovementController {

    private RobotController rc;

    static final float SCAN_ANGLE = (float)Math.PI; //Arc of movement to scan for - Robot will in SCAN_ANGLE radians arc centered on target direction
    static final int MOVEMENT_GRANULARITY = 24; //Number of directions to try
    static final float WANDER_DISTANCE = 5f; //How far away to set wandering target

    //Type-specific variables used to compute movement
    private RobotType rcType = null;
    private float rcStrideRadius = 0f;
    private float rcBodyRadius = 0f;
    private float rcSightRadius = 0f;

    //Stored movement information
    private float escapeRadius = 2.0f; //Within this radius around target, Robot will perceive it has reached intended target
    private MapLocation wanderLocation = null;
    private int wanderTimer = 0;

    /*
        Implementation of two basic movement functions covered during Battlecode 2017 lectures
        Why do we store rcType? (Hint: TANKS!)
    */
    public MovementController(RobotController robot){
        rc = robot;
        rcType = rc.getType();
        rcStrideRadius = rcType.strideRadius;
        rcBodyRadius = rcType.bodyRadius;
        rcSightRadius = rcType.sensorRadius;
    }

    //Random movement
    /*
        You'd be surprised how effective having a random movement function is.
        It adds randomity to your robot's behavior and increases its robustness in unforeseen situations.
        This particular implementation's recurrent behavior may be dangerous...but it's quick and dirty :)
    */
    public void wander() throws GameActionException {
        MapLocation rcLocation = rc.getLocation();
        if (wanderLocation != null){
            //Random location generated. Continue with travelling
            if (rcLocation.distanceTo(wanderLocation) < escapeRadius || rc.getRoundNum() > wanderTimer){ //Resets when timeout/target-reached
                wanderLocation = null;
                wander();
            }
            else basicMove(rcLocation.directionTo(wanderLocation));
        }
        else{
            wanderLocation = rcLocation.add(new Direction((float)(Math.random()*Math.PI*2)), (float)Math.random()*WANDER_DISTANCE);
            wanderTimer = Math.round(rcLocation.distanceTo(wanderLocation)/rcStrideRadius) + rc.getRoundNum(); //Sets a timeout (in case target location unreachable)
            wander();
        }
    }

    //Basic movement scanning
    /*
        Takes a targeted direction and finds the closest angle that can be traveled to in a
        SCAN_ANGLE arc centered on targetDir.
        Dist takes in a distance to be moved and tests whether a partial step can be taken
    */
    public Direction scanForDirection(Direction targetDir, float dist) throws GameActionException {
        if (rc.hasMoved()) return null;
        if (rc.canMove(targetDir, dist)){
            return targetDir;
        }
        Direction tryDir;
        for (int i=1;i<MOVEMENT_GRANULARITY/2;i++){
             tryDir = targetDir.rotateLeftRads(i*SCAN_ANGLE/MOVEMENT_GRANULARITY);
             if (rc.canMove(tryDir, dist)){
                 return tryDir;
             }
             tryDir = targetDir.rotateRightRads(i*SCAN_ANGLE/MOVEMENT_GRANULARITY);
             if (rc.canMove(tryDir, dist)){
                 return tryDir;
             }
        }
        return null;
    }

    //Examplefuncsplayer pathing algo
    /*
        Naive pathing algo, if targetDir not available, look for next closest direction to targetDir
    */
    public boolean basicMove(Direction targetDir) throws GameActionException {
        Direction moveDir = scanForDirection(targetDir, rcStrideRadius);
        if (moveDir == null) return false;
        rc.move(moveDir);
        return true;
    }

    //Lecture Slug-pathing algo
    /*
        Sacrifices speed for accuracy. Moves closer towards targetDir at the expense of distance-per-step
    */
    public boolean slugMove(Direction targetDir, int SLUG) throws GameActionException {
        Direction moveDir;
        for (int i=0;i<SLUG;i++){
            moveDir = scanForDirection(targetDir, rcStrideRadius/(float)Math.pow(2, i));
            if (moveDir != null){
                rc.move(moveDir);
                return true;
            }
        }
        return false;
    }

    //Precise movement
    /*
        If possible, positions robot exactly to targeted location
    */
    public boolean preciseMove(MapLocation targetLoc, int SLUG) throws GameActionException {
        if (rc.canMove(targetLoc)){
            rc.move(targetLoc);
            return true;
        }
        else{
            return slugMove(rc.getLocation().directionTo(targetLoc), SLUG);
        }
    }

}
