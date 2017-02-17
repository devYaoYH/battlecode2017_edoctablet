package TemplatePlayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Controller {

    //Constants
    static final int SPAWN_GRANULARITY = 24; //How many directions to search for possible building direction

    //Instance variables
    private RobotController rc;
    private RobotType rcType;

    //Spawning controls
    private int gardenerTimer = 0;
    private int lumberjackTimer = 0;
    private int soldierTimer = 0;
    private int tankTimer = 0;

    private int DELAY_GARDENER = 23;

    public Controller(RobotController robot){
        rc = robot;
        rcType = rc.getType();
    }

    //Common shared method to force a robot spawn (when you really need one)
    /*
        Naive method for spawning units, setting a direction may be preferred
        Static searching of possible directions --> leads to asymmetry when playing as TeamA/TeamB
    */
    private boolean forceSpawn(RobotType type) throws GameActionException{
        for (int i=0;i<SPAWN_GRANULARITY;i++){
            Direction buildDir = new Direction(i*(float)Math.PI*2/SPAWN_GRANULARITY);
            if (rc.canBuildRobot(type, buildDir)){
                rc.buildRobot(type, buildDir);
                return true;
            }
        }
        return false;
    }

    /*
        Archon method to spawn gardeners
    */
    public boolean spawnGardener() throws GameActionException {
        if (rcType!=RobotType.ARCHON) return false;
        if (rc.getTeamBullets() < RobotType.GARDENER.bulletCost) return false; //Example of a control
        //More conditions if necessary
        if (rc.getRoundNum() < gardenerTimer) return false; //For instance, we have a cooldown timer to prevent over-spawning gardeners
        boolean spawned = forceSpawn(RobotType.GARDENER); //Finally, after conditions have been met, we spawn gardener
        if (spawned) gardenerTimer = rc.getRoundNum() + DELAY_GARDENER;
        return spawned;
    }

    /*
        Gardener methods to spawn units
    */
    public boolean spawnLumberjack() throws GameActionException {
        if (rcType!=RobotType.GARDENER) return false;

        return forceSpawn(RobotType.LUMBERJACK);
    }

    public boolean spawnSoldier() throws GameActionException {
        if (rcType!=RobotType.GARDENER) return false;

        return forceSpawn(RobotType.SOLDIER);
    }

    public boolean spawnTank() throws GameActionException {
        if (rcType!=RobotType.GARDENER) return false;

        return forceSpawn(RobotType.TANK);
    }

    public boolean spawnRandom() throws GameActionException {
        int randInt = (int)Math.floor(Math.random()*3); //generates random integer from 0-2
        switch(randInt){
            case 0:
                return spawnLumberjack();
            case 1:
                return spawnSoldier();
            case 2:
                return spawnTank();
        }
        return false;
    }

}
