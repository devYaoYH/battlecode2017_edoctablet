package TemplatePlayer;

import battlecode.common.*;

public class GardenerPlayer extends BasePlayer {

    private Controller spawnControl;

    //Planting trees!
    static final int PLANT_GRANULARITY = 24;
    private MapLocation farmLocation;
    private float farmRadius = 3.0f; //Clearance needed for a farm!

    public GardenerPlayer(RobotController robot) throws GameActionException {
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
        spawnControl = new Controller(rc);
    }

    public void startRound() throws GameActionException {
        //Handles typical code executed at the start of a round
        super.startRound();
    }

    private void run() throws GameActionException {
        //Main code block for Robot
        spawnControl.spawnRandom();
        TreeInfo[] surroundingTrees = rc.senseNearbyTrees(2f, rc.getTeam());
        if (farmLocation == null){
            farmLocation = seekFarmLocation();
        }
        else{
            mc.preciseMove(farmLocation, SLUG);
        }
        if (rc.getLocation() == farmLocation && surroundingTrees.length < 6){
            //starts to plant trees!
            plantTrees(surroundingTrees);
        }
        waterTrees(surroundingTrees);
    }

    public void endRound() throws GameActionException {
        //Handles typical code executed at the end of a round
        super.endRound();
    }

    //Locating suitable position to plant trees
    /*
        Here we implement a naive monte carlo (random polling) method to seek out possible locations to plant a hextet of trees
        Within a radius of (4f - based on variable values at time of writing) randomly picks locations to test 2 conditions:
            1) Free circle of radius 3f (1f body radius + 2f tree diameter)
            2) Circle of radius 3f is entirely on the map
        Once these two conditions are met, method returns the location as a possible location and robots starts moving towards location
    */
    private MapLocation seekFarmLocation() throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        //Keep a tab on bytecost
        while(Clock.getBytecodesLeft() > BYTE_LOW){
            MapLocation monteCarloLoc = currentLocation.add(new Direction((float)(Math.random()*2*Math.PI)), (float)Math.random()*(rcSightRadius-farmRadius));
            if (!rc.isCircleOccupiedExceptByThisRobot(monteCarloLoc, farmRadius) && rc.onTheMap(monteCarloLoc, farmRadius)){
                return monteCarloLoc;
            }
        }
        return null;
    }

    //Plants trees around self
    /*
        Naive hexagonal planting of trees by gardener that results in bullet tree clusters in vogue during the seeding/final tournaments
    */
    private void plantTrees(TreeInfo[] surroundingTrees) throws GameActionException {
        //We first sense if current gardener has already started a farm!
        if (surroundingTrees.length > 0){
            //Farm started
            TreeInfo referenceTree = surroundingTrees[0]; //We take a reference tree and form a hexagon around the gardener
            Direction initDir = rc.getLocation().directionTo(referenceTree.location);
            Direction plantDir = initDir.rotateRightDegrees(60); //Assuming best-case, full hexagon can be formed
            for (int i=0;i<PLANT_GRANULARITY;i++){
                if (rc.canPlantTree(plantDir)){
                    rc.plantTree(plantDir);
                    return;
                }
                plantDir = plantDir.rotateRightRads((float)Math.PI*2/PLANT_GRANULARITY);
            }
        }
        else{
            //Farm not yet started
            forcePlanting();
        }
    }

    //Seeks possible direction to start planting trees
    private void forcePlanting() throws GameActionException {
        for (int i=0;i<PLANT_GRANULARITY;i++){
            Direction plantDir = new Direction(i*(float)Math.PI*2/PLANT_GRANULARITY);
            if (rc.canPlantTree(plantDir)){
                rc.plantTree(plantDir);
                return;
            }
        }
    }

    //Importantly, waters trees surrounding it
    /*
        Trees are incredibly thirsty! One has to constantly water it. In this implementation, we find and water the thirstiest tree.
    */
    private void waterTrees(TreeInfo[] surroundingTrees) throws GameActionException {
        float minHealth = 999f;
        int minID = -1;
        for (TreeInfo t:surroundingTrees){
            if (t.health < minHealth && rc.canWater(t.ID)){
                minHealth = t.health;
                minID = t.ID;
            }
        }
        if (minID > 0){
            rc.water(minID);
        }
    }

}
