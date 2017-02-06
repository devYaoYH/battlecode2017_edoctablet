package tatooine;

import battlecode.common.*;

public class Message {

    static int msg = 0;

    //Digits of information
    static final int ENEMY_TYPE = 10000;
    static final int ENEMY_NUMBER = 1000;
    static final int ALERT_LEVEL = 100;

    public void Message(){
        //Ready to receive information and compress into message
        msg = 0;
    }

    public void Message(int message){
        msg = message;
    }

    public int getRawMsg(){
        return msg;
    }

    public void writeRawMsg(int message){
        msg = message;
    }

    public RobotType getEnemy(){
        int sensed_robot = msg/ENEMY_TYPE;
        if (sensed_robot == BasePlayer.robotType(RobotType.ARCHON)){
            return RobotType.ARCHON;
        }
        else if (sensed_robot == BasePlayer.robotType(RobotType.GARDENER)){
            return RobotType.GARDENER;
        }
        else if (sensed_robot == BasePlayer.robotType(RobotType.SCOUT)){
            return RobotType.SCOUT;
        }
        else if (sensed_robot == BasePlayer.robotType(RobotType.SOLDIER)){
            return RobotType.SOLDIER;
        }
        else if (sensed_robot == BasePlayer.robotType(RobotType.TANK)){
            return RobotType.TANK;
        }
        else return null;
    }

    public void writeEnemy(RobotType robot){
        switch(robot){
            case ARCHON:
                msg += BasePlayer.robotType(RobotType.ARCHON)*ENEMY_TYPE;
                break;
            case GARDENER:
                msg += BasePlayer.robotType(RobotType.GARDENER)*ENEMY_TYPE;
                break;
            case SCOUT:
                msg += BasePlayer.robotType(RobotType.SCOUT)*ENEMY_TYPE;
                break;
            case SOLDIER:
                msg += BasePlayer.robotType(RobotType.SOLDIER)*ENEMY_TYPE;
                break;
            case TANK:
                msg += BasePlayer.robotType(RobotType.TANK)*ENEMY_TYPE;
                break;
        }
        return;
    }

    public int getNumberOfEnemies(){
        return (msg - (msg/ENEMY_TYPE)*ENEMY_TYPE - (msg/ALERT_LEVEL)*ALERT_LEVEL)/ENEMY_NUMBER;
    }

    public void writeNumberOfEnemies(int num){
        msg += num*ENEMY_NUMBER;
    }

    public int getAlertLevel(){
        return (msg - (msg/ENEMY_TYPE)*ENEMY_TYPE - (msg/ENEMY_NUMBER)*ENEMY_NUMBER)/ALERT_LEVEL;
    }

    public void writeAlertLevel(int alert){
        msg += alert*ALERT_LEVEL;
    }

}
