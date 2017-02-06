package Scarif_stable;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public strictfp class RobotPlayer {

	static BasePlayer newPlayer;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
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
                        newPlayer = new ScoutShaker(rc);    //We pretty much neglected the development of scouts post sprint tourney
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