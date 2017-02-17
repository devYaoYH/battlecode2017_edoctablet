package TemplatePlayer;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class CombatPlayer extends BasePlayer {

    private FiringController fc;

    public CombatPlayer(RobotController robot) throws GameActionException {
        super(robot);
        fc = new FiringController(rc);
    }

    protected void startRound() throws GameActionException {
        super.startRound();
    }

    protected void endRound() throws GameActionException {
        super.endRound();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            fc.fire(enemies[0]);
        }
    }

}
