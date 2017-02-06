package Dagobah;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.Team;

public strictfp class RobotPlayer {

	static BasePlayer newPlayer;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        /*float archondist = rc.getInitialArchonLocations(rc.getTeam())[0].distanceTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
        if (archondist > 69 && rc.getInitialArchonLocations(rc.getTeam()).length >= 1 && rc.getTeam() == Team.A) {
            rc.setIndicatorDot(rc.getLocation(), 0, 255, 255);
            //large map
            GuanPlayer.run(rc);
        }*/
    	//Robot Specific Initializations
        while(true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:
                        newPlayer = new ArchonPlayer(rc);
                        break;
                    case GARDENER:
                        newPlayer = new GardenerPlayer(rc);
                        break;
                    case SOLDIER:
                        newPlayer = new SoldierPlayer(rc);
                        break;
                    case LUMBERJACK:
                        newPlayer = new LumberjackPlayer(rc);
                        break;
                    case SCOUT:
                        newPlayer = new ScoutShaker(rc);
                        break;
                    case TANK:
                        newPlayer = new TankPlayer(rc);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

	}

}