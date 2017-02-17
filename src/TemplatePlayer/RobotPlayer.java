package TemplatePlayer;

import battlecode.common.*;

public strictfp class RobotPlayer {

    static BasePlayer curPlayer = null;

    public static void run(RobotController rc) throws GameActionException {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        while(true) {
            try {
                //If exceptions were thrown, we reuse the already-created player object (complete with its initializations)
                //Might save that little bit more bytecode (re-initialize cost)
                if (curPlayer != null) curPlayer.runRobot();
                else {
                    switch (rc.getType()) {
                        case ARCHON:
                            curPlayer = new ArchonPlayer(rc);
                            break;
                        case GARDENER:
                            curPlayer = new GardenerPlayer(rc);
                            break;
                        case LUMBERJACK:
                            curPlayer = new LumberjackPlayer(rc);
                            break;
                        case SCOUT:
                            curPlayer = new ScoutPlayer(rc);
                            break;
                        case SOLDIER:
                            curPlayer = new SoldierPlayer(rc);
                            break;
                        case TANK:
                            curPlayer = new TankPlayer(rc);
                            break;
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}