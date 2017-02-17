package TemplatePlayer;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;

public class BasePlayer {

    //Constants
    static final int BYTE_LOW = 1500;
    static final float BULLETS_MAX = 500f;

    //Controllers
    protected RobotController rc;
    protected MovementController mc;

    //Robot properties
    protected RobotType rcType = null;
    protected float rcStrideRadius = 0f;
    protected float rcBodyRadius = 0f;
    protected float rcSightRadius = 0f;

    //Robot-specific variables
    protected MapLocation target;
    protected int SLUG = 1;

    public BasePlayer(RobotController robot) throws GameActionException {
        rc = robot;
        mc = new MovementController(rc);
        rcType = rc.getType();
        rcStrideRadius = rcType.strideRadius;
        rcBodyRadius = rcType.bodyRadius;
        rcSightRadius = rcType.sensorRadius;
    }

    protected void startRound() throws GameActionException {

    }

    public void runRobot() throws GameActionException {

    }

    protected void endRound() throws GameActionException {
        //Good to keep movement wrapped up (at the end? - watch out for execution order of events in game engine)
        //May not be realistic to keep common movement code for all robots...
        if (target == null) mc.wander();
        else mc.slugMove(rc.getLocation().directionTo(target), SLUG);

        //Don't forget to donate! Many games are won by a scout hiding on a tree and donating bullets :)
        donate();
    }

    protected void donate() throws GameActionException {
        if (rc.getTeamBullets() > BULLETS_MAX){
            //Donates maximum amount of victory points above BULLETS_MAX bullet limit
            //donates when bullets are at least rc.getVictoryPointCost() more than BULLETS_MAX
            rc.donate(rc.getVictoryPointCost()*(float)Math.floor((rc.getTeamBullets()-BULLETS_MAX)/rc.getVictoryPointCost()));
        }
    }

}
