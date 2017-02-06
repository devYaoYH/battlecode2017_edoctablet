package Kamino;

import battlecode.common.RobotType;

public class Message {

    static int msg = 0;

    //Digits of information
    static final int ENEMY_TYPE = 10000;
    static final int ENEMY_NUMBER = 1000;
    static final int ALERT_LEVEL = 100;
    static final int ARCHON_PRESENT = 10;
    static final int MESSAGE_TYPE = 1;

    //Types of message
    static final int SIT_REP = 0;
    static final int SPOT_REP = 1;
    static final int CONTACT_REP = 2;
    static final int TARGET_REP = 3;

    public Message(){
        //Ready to receive information and compress into message
        msg = 0;
    }

    public Message(int message){
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
        return (msg - (msg/ENEMY_TYPE)*ENEMY_TYPE)/ENEMY_NUMBER;
    }

    public void writeNumberOfEnemies(int num){
        msg += num*ENEMY_NUMBER;
    }

    public int getAlertLevel(){
        int digit_1 = (msg/ENEMY_TYPE);
        int digit_2 = (msg%ENEMY_TYPE)/ENEMY_NUMBER;
        int digit_3 = (msg%ENEMY_NUMBER)/ALERT_LEVEL;
        System.out.println(digit_1);
        System.out.println(digit_2);
        System.out.println(digit_3);
        return digit_3;
    }

    public void writeAlertLevel(int alert){
        msg += alert*ALERT_LEVEL;
    }

    public int getArchonPresent(){
        int digit_4 = (msg%ALERT_LEVEL)/ARCHON_PRESENT;
        return digit_4;
    }

    public void writeArchonPresent(int archon_present){
        msg += archon_present*ARCHON_PRESENT;
    }

    public int getMessageType(){
        int digit_5 = (msg%ARCHON_PRESENT)/MESSAGE_TYPE;
        return digit_5;
    }

    public void writeMessageType(int type){
        msg += type*MESSAGE_TYPE;
    }

}
