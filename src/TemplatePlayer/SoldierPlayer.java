package TemplatePlayer;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.Clock;

public class SoldierPlayer extends CombatPlayer {

    public SoldierPlayer(RobotController robot) throws GameActionException {
        super(robot);
        runRobot();
    }

    public void runRobot() throws GameActionException {
        init();
        for (; ; Clock.yield()){ //Clock.yield() tells the game engine to end the robot's turn
            try{
                startRound();
                run();
                endRound();
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private void init() throws GameActionException {
        //Run once when Robot is first spawned
    }

    public void startRound() throws GameActionException {
        //Handles typical code executed at the start of a round
        super.startRound();
    }

    private void run() throws GameActionException {
        //Main code block for Robot
    }

    public void endRound() throws GameActionException {
        //Handles typical code executed at the end of a round
        super.endRound();
    }

}
