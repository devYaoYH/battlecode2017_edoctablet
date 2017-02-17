package TemplatePlayer;

import battlecode.common.*;

public class FiringController {

    private RobotController rc;

    //Static game constants
    static float TRIAD_ANGLE = GameConstants.TRIAD_SPREAD_DEGREES;
    static float PENTAD_ANGLE = GameConstants.PENTAD_SPREAD_DEGREES;

    public FiringController(RobotController robot) {
        rc = robot;
    }

    //Basic firing controls
    /*
        Exceptions (telling a robot to do something against the game rules) will incur a high bytecode penalty
        which is fatal in crucial fast-paced early game soldier v soldier micro. Thus, one cannot be too careful
        in checking for a robot's actions before committing to them.
    */
    public boolean fire(RobotInfo enemy) throws GameActionException {
        if (rc.getType() == RobotType.LUMBERJACK) return strike(enemy);
        if (rc.hasAttacked()) return false; //One such check (just in case)
        Direction toEnemy = rc.getLocation().directionTo(enemy.location);
        if (shouldFirePentad(enemy)){
            rc.firePentadShot(toEnemy);
        }
        else if(shouldFireTriad(enemy)){
            rc.fireTriadShot(toEnemy);
        }
        else{
            if (rc.canFireSingleShot()){
                rc.fireSingleShot(toEnemy);
            }
        }
        return false;
    }

    //Special strike method for lumberjacks
    private boolean strike(RobotInfo enemy) throws GameActionException {
        if (rc.getLocation().distanceTo(enemy.location) <= GameConstants.LUMBERJACK_STRIKE_RADIUS){
            rc.strike();
            return true;
        }
        return false;
    }

    private boolean shouldFirePentad(RobotInfo enemy){
        if (!rc.canFirePentadShot()) return false;
        //Some controls on when to fire pentads

        return true;
    }

    private boolean shouldFireTriad(RobotInfo enemy){
        if (!rc.canFireTriadShot()) return false;
        //Some controls on when to fire triads

        return true;
    }

}
