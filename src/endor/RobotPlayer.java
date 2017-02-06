package endor;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public strictfp class RobotPlayer {

    static RobotController rc;
    static int id;

    //Signal Index Placeholder Consts
    static int NUM_ARC = 0;
    static int NUM_GAR = 1;
    static int NUM_SCO = 2;
    static int NUM_SOL = 3;
    static int NUM_TAN = 4;
    static int NUM_LUM = 5;

    //Control Constants
    static int LIM_GAR = 20;
    static int MAX_STACK = 3;

    //Precomp Degrees
    static float DEG_01 = (float) Math.PI/1800;
    static float DEG_05 = (float) Math.PI/360;
    static float DEG_1 = (float) Math.PI/180;
    static float DEG_5 = (float) Math.PI/36;
    static float DEG_15 = (float) Math.PI/12;
    static float DEG_45 = (float) Math.PI/4;
    static float DEG_90 = (float) Math.PI/2;
    static float DEG_180 = (float) Math.PI;
    static float DEG_360 = (float) Math.PI*2;

    //Shared Array SOP
    //[0-9] Roll Call
    //[100-109] Team Orders
    //[200-209] Team Order Data
    //[0-300] Team Robot Status

    //Squad Setup
    //[10-99] Team Members
    //[110-199] Member Data
    //[210-299] Member Keep Alive Signal

    //Ops Orders
    //[400-410] Reserved
    //[410] Spawn Scout
    //[411] Spawn Trees
    //[412] Spawn Soldiers
    //[413] Spawn Tanks
    //[414] Spawn Lumberjacks
    //[420] Offensive Posture
    //[430] Defensive Posture
    //[431] Early Soldier Spawn
    //[440] Farming mode
    //[441] Farmer Spawn Threshold

    //Archon Location
    //[501-502] Archon location
    //[500] Archon ID
    //[511-512] Archon location
    //[510] Archon ID
    //[521-522] Archon location
    //[520] Archon ID
    //[531-532] Archon location
    //[530] Archon ID
    //[541-542] Archon location
    //[540] Archon ID

    //Farmers
    //[551-552] Farmer location
    //[550] Farmer ID
    //[553] Farmer Keep Alive
    //... etc

    //Gardeners Counter
    //[700-799] Keep Alive heartbeat

    //Troops Counter
    //[800-899] Keep Alive heartbeat

    //Reserved Channels for future use
    //[900-999]

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        RobotPlayer.id = rc.getID();

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case SCOUT:
                runScout();
            case LUMBERJACK:
                runLumberjack();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        Random rand = new Random(id);

        while (true) {
            try {
                Direction dir = randomDirection();
                MapLocation myLocation = rc.getLocation();
                boolean hired_gardener = false;
                boolean can_spawn = false;
                boolean spawnGar = true;
                int cur_gardeners = 0;
                int cur_troops = 0;

                //Shake Neutral Trees
                TreeInfo[] neut_trees = rc.senseNearbyTrees(15,Team.NEUTRAL);
                for(TreeInfo n:neut_trees){
                    if(rc.canShake(n.ID)) rc.shake(n.ID);
                }

                //Disable early scout rush
                rc.broadcast(410, 1);

                //Enable early soldier rush
                //rc.broadcast(431, 0);

                //Count Number of gardeners
                for(int i=0;i<100;i++){
                    if(rc.readBroadcast(700+i)>rc.getRoundNum()-2){
                        cur_gardeners++;
                    }
                }

                //Count Number of troops
                for(int i=0;i<100;i++){
                    if(rc.readBroadcast(800+i)>rc.getRoundNum()-2){
                        cur_troops++;
                    }
                }

                //Stop excessive troop production
                if(cur_troops > 30) rc.broadcast(412,0);
                else rc.broadcast(412,1);

                if(cur_gardeners>5){
                    //Decrease amt farmers
                    System.out.println("Too many farmers!");
                    rc.broadcast(441,600);
                }
                else if(cur_gardeners<=5){
                    //Early game spawn at least 5 farmers
                    System.out.println("Still spawning farmers...");
                    rc.broadcast(441, 1000);
                }
                else{
                    //Scale with total number of gardeners
                    rc.broadcast(441,(600-cur_gardeners*2));
                }

                //Stop excessive gardener production
                if(cur_gardeners > 25) spawnGar = false;

                //Debug read threshold
                System.out.println("Current Threshold: "+Integer.toString(rc.readBroadcast(441)));
                System.out.println("Number of gardeners: "+Integer.toString(cur_gardeners));

                //Donate to VP excess bullets
                if(rc.getTeamBullets()>400){
                    rc.donate(100f);
                }

                //Check whether current position can spawn units, else move
                for(int i=0;i<72;i++) {
                    Direction cDir = new Direction(i * DEG_5);
                    if (rc.isCircleOccupied(myLocation.add(cDir,1.1f),1f)) {
                        can_spawn = true;
                    }
                }
                if(!can_spawn) {
                    if (!rc.hasMoved()) {
                        //Move Randomly to find spawn point
                        tryMove(randomDirection());
                    }
                }

                //Check Gardener Quota
                if (rc.isBuildReady() && spawnGar) {
                    if (rand.nextInt(1000)>cur_gardeners*20) {
                        //Search for location to build gardener
                        float offset = rand.nextInt(360)*DEG_360/360;
                        for(int i=0;i<72;i++) {
                            if (!hired_gardener) {
                                Direction cDir = new Direction(absRad(i * DEG_5+offset));
                                if (rc.canHireGardener(cDir)) {
                                    rc.hireGardener(cDir);
                                    hired_gardener = true;
                                }
                            }
                        }
                        if(!rc.hasMoved() && !hired_gardener){
                            //Move Randomly to find spawn point
                            tryMove(randomDirection());
                        }
                    }
                }

                //Initiate Round Roll Call
                if (rc.getRoundNum()%20 == 0) {
                    for (int i = 0; i < 6; ++i) rc.broadcast(i, 0);
                }

                //Dodge Incomming Bullets
                //TODO dodge();

                //Else Check for movement goal if any


                // Broadcast archon's location for other robots on the team to know
                for (int i=0;i<5;i++){
                    int curID = rc.readBroadcast(500+i*10);
                    if (curID == 0 || curID == id){
                        rc.broadcast(500+i*10+1,(int)myLocation.x);
                        rc.broadcast(500+i*10+2,(int)myLocation.y);
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener! :D");
        boolean initScout = false;
        boolean initSoldier = false;
        boolean initFarm = false;
        Random rand = new Random(id);

        //Decide whether this is a farmer type or builder type gardener
        int garType = rand.nextInt(1000);
        int threshold = rc.readBroadcast(441);
        boolean farmer = (garType<threshold);
        int farmSlot = 0;
        int counterSlot = 0;

        if(farmer){
            //Reserve Spot in farmer list
            for (int i = 0; i < 5; i++) {
                int curSpot = rc.readBroadcast(550 + i * 10);
                int kpSig = rc.readBroadcast(553 + i * 10);
                if (curSpot == 0 || kpSig < rc.getRoundNum() - 5) {
                    //Take this spot
                    farmSlot = 550 + i * 10;
                    rc.broadcast(farmSlot, id);
                    rc.broadcast(farmSlot + 3, rc.getRoundNum());
                }
            }
        }

        while (true){
            try{
                Team myTeam = rc.getTeam();
                MapLocation myLoc = rc.getLocation();

                threshold = rc.readBroadcast(441);
                farmer = (garType<threshold);

                boolean heartbeat = false;
                int conut = 0;
                int cur_round = rc.getRoundNum();
                //Announce Heartbeat
                while(!heartbeat){
                    if(rc.readBroadcast(700+conut)<cur_round-5){
                        rc.broadcast(700+conut,cur_round);
                        heartbeat = true;
                        break;
                    }
                    conut++;
                }

                //Shake Neutral Trees
                TreeInfo[] neut_trees = rc.senseNearbyTrees(15,Team.NEUTRAL);
                for(TreeInfo n:neut_trees){
                    if(rc.canShake(n.ID)) rc.shake(n.ID);
                }

                if (farmer) {
                    System.out.println("I'm a farmer!");

                    //Allow farmers to donate too
                    if(rc.getTeamBullets()>300) rc.donate(50f);

                    Direction[] hexPattern = new Direction[6];
                    TreeInfo[] curTrees;
                    for (int i = 0; i < 6; i++) {
                        hexPattern[i] = new Direction(0 + i * (DEG_45 + DEG_15));
                    }

                    //Get self location
                    myLoc = rc.getLocation();

                    //Keep Alive
                    if (farmSlot == 0) {
                        //Reserve Spot in farmer list
                        for (int i = 0; i < 5; i++) {
                            int curSpot = rc.readBroadcast(550 + i * 10);
                            int kpSig = rc.readBroadcast(553 + i * 10);
                            if (curSpot == 0 || kpSig < rc.getRoundNum() - 5) {
                                //Take this spot
                                farmSlot = 550 + i * 10;
                                rc.broadcast(farmSlot, id);
                                rc.broadcast(farmSlot + 3, rc.getRoundNum());
                            }
                        }
                    } else {
                        rc.broadcast(farmSlot + 3, rc.getRoundNum());
                    }

                    //Early Game Scout start
                    if (!initScout) {
                        if (rc.readBroadcast(410) == 1) {
                            initScout = true;
                        } else {
                            for (int i = 0; i < 72; i++) {
                                if (!initScout) {
                                    Direction cDir = new Direction(i * DEG_5);
                                    if (rc.canBuildRobot(RobotType.SCOUT, cDir)) {
                                        rc.buildRobot(RobotType.SCOUT, cDir);
                                        initScout = true;
                                        rc.broadcast(410, 1);
                                    }
                                }
                            }
                        }
                    }

                    //Early Game soldier rush
                    if (!initSoldier) {
                        if (rc.readBroadcast(431) == 1) {
                            initSoldier = true;
                        } else {
                            for (int i = 0; i < 72; i++) {
                                if (!initSoldier) {
                                    Direction cDir = new Direction(i * DEG_5);
                                    if (rc.canBuildRobot(RobotType.SOLDIER, cDir)) {
                                        rc.buildRobot(RobotType.SOLDIER, cDir);
                                        initSoldier = true;
                                        rc.broadcast(431, 1);
                                    }
                                }
                            }
                        }
                    }

                    //I am threatened!!
                    RobotInfo[] en_dep = rc.senseNearbyRobots(-1,rc.getTeam().opponent());
                    RobotInfo[] ops_plan = rc.senseNearbyRobots(-1,rc.getTeam());
                    int friendly_strength = 0;
                    //Count friendly soldiers
                    for(RobotInfo f:ops_plan){
                        if(f.type == RobotType.SOLDIER) friendly_strength++;
                    }
                    if(en_dep.length>0 && friendly_strength<3) {
                        if(rc.isBuildReady()) {
                            for (int i = 0; i < 6; i++) {
                                if (rc.canBuildRobot(RobotType.SOLDIER,hexPattern[i])) {
                                    rc.buildRobot(RobotType.SOLDIER,hexPattern[i]);
                                }
                            }
                        }
                    }

                    //Plant Trees
                    if (initFarm) {
                        //Starts to farm! :D
                        for (int i = 0; i < 6; i++) {
                            if (rc.canPlantTree(hexPattern[i])) {
                                rc.plantTree(hexPattern[i]);
                            }
                        }
                        //Update position
                        if (rc.hasMoved()) {
                            rc.broadcast(farmSlot + 1, (int) myLoc.x);
                            rc.broadcast(farmSlot + 2, (int) myLoc.y);
                        }
                    } else {
                        //Search for a good place to start farming
                        if (!rc.isCircleOccupiedExceptByThisRobot(rc.getLocation(), 3.05f)) {
                            //See if can move further away from archon
                            //Read Archon positions
                            float arcX = rc.readBroadcast(511);
                            float arcY = rc.readBroadcast(512);
                            MapLocation curArc = new MapLocation(arcX,arcY);
                            MapLocation curLoc = rc.getLocation();
                            Direction toArc = curLoc.directionTo(curArc);
                            for(int k=-15;k<16;k++) {
                                Direction tryDir = new Direction(absRad(toArc.opposite().radians + k * DEG_1));
                                if (!rc.isCircleOccupiedExceptByThisRobot(curLoc.add(tryDir, 1), 3.05f)) {
                                    if (rc.canMove(tryDir)) rc.move(tryDir);
                                }
                            }
                            if(rc.hasMoved()) {
                                initFarm = true;
                                //Set position
                                rc.broadcast(farmSlot + 1, (int) myLoc.x);
                                rc.broadcast(farmSlot + 2, (int) myLoc.y);
                            }
                        } else {
                            //Move around to find another spot for farming
                            TreeInfo[] surrTrees = rc.senseNearbyTrees();
                            RobotInfo[] surrRobots = rc.senseNearbyRobots();
                            MapLocation[] mapLoc = new MapLocation[surrTrees.length + surrRobots.length + 1];
                            float[] radii = new float[surrTrees.length + surrRobots.length + 1];
                            mapLoc[0] = myLoc;
                            radii[0] = 1;
                            for (int i = 0; i < surrTrees.length; i++) {
                                mapLoc[i + 1] = surrTrees[i].getLocation();
                                radii[i + 1] = surrTrees[i].getRadius();
                            }
                            for (int i = 0; i < surrRobots.length; i++) {
                                mapLoc[i + 1 + surrTrees.length] = surrRobots[i].getLocation();
                                radii[i + 1 + surrTrees.length] = surrRobots[i].getRadius();
                            }
                            Direction[] bubDat = bubbleSqueeze(mapLoc, radii);
                            if (rc.canMove(bubDat[0], bubDat[1].radians)) {
                                rc.move(bubDat[0], bubDat[1].radians);
                            } else {
                                Direction randDir = randomDirection();
                                if (rc.canMove(randDir)) rc.move(randDir);
                            }
                        }
                    }

                    //Manage Trees
                    curTrees = rc.senseNearbyTrees(2.1f, myTeam);
                    //Find the lowest hp tree
                    float lowHP = 99999;
                    int lowID = 0;
                    for (TreeInfo t : curTrees) {
                        int cT = t.ID;
                        float cHP = t.getHealth();
                        if(cHP<lowHP && rc.canWater(cT)){
                            lowHP = cHP;
                            lowID = cT;
                        }
                    }
                    if(lowID!=0) rc.water(lowID);
                    Clock.yield();
                }
                else {
                    System.out.println("I'm a builder!");

                    Team enemy = myTeam.opponent();
                    MapLocation[] archonLoc = new MapLocation[5];

                    // Listen for home archon's location
                    for (int i = 0; i < 5; i++) {
                        int xPos = rc.readBroadcast(501 + i * 10);
                        int yPos = rc.readBroadcast(502 + i * 10);
                        archonLoc[i] = new MapLocation(xPos, yPos);
                    }

                    //Early Game Scout start
                    if (!initScout) {
                        if (rc.readBroadcast(410) == 1) {
                            initScout = true;
                        } else {
                            for (int i = 0; i < 72; i++) {
                                if (!initScout) {
                                    Direction cDir = new Direction(i * DEG_5);
                                    if (rc.canBuildRobot(RobotType.SCOUT, cDir)) {
                                        rc.buildRobot(RobotType.SCOUT, cDir);
                                        initScout = true;
                                        rc.broadcast(410, 1);
                                    }
                                }
                            }
                        }
                    }

                    //Early Game soldier rush
                    if (!initSoldier) {
                        if (rc.readBroadcast(431) == 1) {
                            initSoldier = true;
                        } else {
                            for (int i = 0; i < 72; i++) {
                                if (!initSoldier) {
                                    Direction cDir = new Direction(i * DEG_5);
                                    if (rc.canBuildRobot(RobotType.SOLDIER, cDir)) {
                                        rc.buildRobot(RobotType.SOLDIER, cDir);
                                        initSoldier = true;
                                        rc.broadcast(431, 1);
                                    }
                                }
                            }
                        }
                    }

                    // Generate a random direction
                    Direction dir = randomDirection();

                    //Build soldiers for defence
                    if(rc.isBuildReady() && rc.readBroadcast(412)==1 && rand.nextInt(1000)<500){
                        //Build lumberjack
                        for (int i = 0; i < 72; i++) {
                            Direction cDir = new Direction(i * DEG_5);
                            if (rc.canBuildRobot(RobotType.SOLDIER, cDir)) {
                                rc.buildRobot(RobotType.SOLDIER, cDir);
                            }
                        }
                    }

                    // Move to spawn troops
                    if (rc.isCircleOccupiedExceptByThisRobot(rc.getLocation(), 3.05f)) {
                        //Move around to find another spot for farming
                        TreeInfo[] surrTrees = rc.senseNearbyTrees();
                        RobotInfo[] surrRobots = rc.senseNearbyRobots();
                        MapLocation[] mapLoc = new MapLocation[surrTrees.length + surrRobots.length + 1];
                        float[] radii = new float[surrTrees.length + surrRobots.length + 1];
                        mapLoc[0] = myLoc;
                        radii[0] = 1;
                        for (int i = 0; i < surrTrees.length; i++) {
                            mapLoc[i + 1] = surrTrees[i].getLocation();
                            radii[i + 1] = surrTrees[i].getRadius();
                        }
                        for (int i = 0; i < surrRobots.length; i++) {
                            mapLoc[i + 1 + surrTrees.length] = surrRobots[i].getLocation();
                            radii[i + 1 + surrTrees.length] = surrRobots[i].getRadius();
                        }
                        Direction[] bubDat = bubbleSqueeze(mapLoc, radii);
                        if (rc.canMove(bubDat[0], bubDat[1].radians)) {
                            rc.move(bubDat[0], bubDat[1].radians);
                        } else {
                            Direction randDir = randomDirection();
                            if (rc.canMove(randDir)) rc.move(randDir);
                        }
                    }

                    //Manage Trees
                    TreeInfo[] curTrees = rc.senseNearbyTrees(2.1f, myTeam);
                    //Find the lowest hp tree
                    float lowHP = 99999;
                    int lowID = 0;
                    for (TreeInfo t : curTrees) {
                        int cT = t.ID;
                        float cHP = t.getHealth();
                        if(cHP<lowHP && rc.canWater(cT)){
                            lowHP = cHP;
                            lowID = cT;
                        }
                    }
                    if(lowID!=0) rc.water(lowID);

                    // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                    Clock.yield();
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {

    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a Green Beret :)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                boolean heartbeat = false;
                int conut = 0;
                int cur_round = rc.getRoundNum();
                //Announce Heartbeat
                while(!heartbeat){
                    if(rc.readBroadcast(800+conut)<cur_round-5){
                        rc.broadcast(800+conut,cur_round);
                        heartbeat = true;
                        break;
                    }
                    conut++;
                }

                //Get current location
                MapLocation myLoc = rc.getLocation();

                //Shake Neutral Trees
                TreeInfo[] neut_trees = rc.senseNearbyTrees(15,Team.NEUTRAL);
                for(TreeInfo n:neut_trees){
                    if(rc.canShake(n.ID)) rc.shake(n.ID);
                }

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(3, enemy);
                RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
                boolean friendlyFire = false;
                boolean enemy_trees = true;

                //todo triad more effective?
                if(robots.length > 0 && !rc.hasAttacked() && rc.canFirePentadShot()) {
                    // Use Pentad Shot?
                    rc.firePentadShot(myLoc.directionTo(robots[0].location));
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    for(RobotInfo r:robots){
                        if(myLoc.distanceTo(r.location)<15){
                            if(!rc.hasAttacked() && rc.canFireSingleShot()){
                                //Check for friendly fire
                                for(RobotInfo f:friendly){
                                    if(intersectLineCircle(myLoc,f.location,myLoc.directionTo(r.location))){
                                        friendlyFire = true;
                                    }
                                }
                                if(!friendlyFire){
                                    //Don't waste shots on enemy trees if economy is bad
                                    if(rc.getTeamBullets()<200){
                                        TreeInfo[] en_trees = rc.senseNearbyTrees(5,rc.getTeam().opponent());
                                        for(TreeInfo t:en_trees){
                                            if(intersectLineCircle(myLoc,t.location,myLoc.directionTo(r.location))){
                                                enemy_trees = false;
                                            }
                                        }
                                    }
                                    if(enemy_trees) rc.fireSingleShot(myLoc.directionTo(r.location));
                                }
                            }
                        }
                    }

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        //Stick close to Archon
                        //todo if Archon is dead :/
                        MapLocation arcLoc = new MapLocation(rc.readBroadcast(511),rc.readBroadcast(512));
                        MapLocation myLocation = rc.getLocation();
                        if(myLocation.distanceTo(arcLoc)>50) tryMove(myLocation.directionTo(arcLoc));
                        else tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        //Stick close to Archon
                        MapLocation arcLoc = new MapLocation(rc.readBroadcast(511),rc.readBroadcast(512));
                        MapLocation myLocation = rc.getLocation();
                        if(myLocation.distanceTo(arcLoc)>5) tryMove(myLocation.directionTo(arcLoc));
                        else tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    //ABS func radians
    static float absRad(float r){
        if(r>DEG_360) return r-DEG_360;
        else if(r<0) return r+DEG_360;
        else return r;
    }

    //^0.5 Proportionality helper function
    static float proportion(float cur, float[] curList){
        float tot = 0f;

        for(float f:curList){
            tot+=Math.sqrt(f);
        }

        return (float)Math.sqrt(cur)/tot;
    }

    //static Direction[] bubbleSqueeze(MapLocation[] mapLoc, float[] radii, int recurse, Direction prevVec, float prevDist) throws GameActionException {
    static Direction[] bubbleSqueeze(MapLocation[] mapLoc, float[] radii) throws GameActionException {
        Direction[] retDat = new Direction[2];
        /*if(recurse > MAX_STACK) {
            retDat[0] = prevVec;
            retDat[1] = new Direction(prevDist);
            return retDat;
        }*/
        float eject = 0f;
        MapLocation self = mapLoc[0];
        float self_rad = radii[0];
        float tot_rad = 0f;
        float tot_dist = 0f;
        float[] intersectDir = new float[mapLoc.length-1];
        float[] intersectDist = new float[mapLoc.length-1];
        int conut = 0;

        for(int i=1;i<mapLoc.length;i++){
            float curDist = self.distanceTo(mapLoc[i]);
            float overlap = (self_rad+radii[i]-curDist);
            if(overlap>0){
                Direction curDir = self.directionTo(mapLoc[i]);
                intersectDir[conut] = curDir.radians;
                intersectDist[conut] = overlap;
                tot_rad += (float)Math.sqrt(overlap);
                conut++;
            }
        }

        for(int i=0;i<conut;i++){
            eject += intersectDir[i]*(Math.sqrt(intersectDist[i])/tot_rad);
            tot_dist += intersectDist[i]*(Math.sqrt(intersectDist[i])/tot_rad);
        }
        eject = eject/conut+DEG_180;

        if(eject>DEG_360){
            eject-=DEG_360;
        }

        retDat[0] = new Direction(eject);
        retDat[1] = new Direction(tot_dist);

        return retDat;

        //TODO Recursively find out better solutions
        /*if(recurse == 0){
            retDat[0] = new Direction(eject);
            retDat[1] = new Direction(tot_dist);
        }
        else{
            retDat[0] = new Direction((eject+prevVec.radians)/2);
            retDat[1] = new Direction((tot_dist+prevDist)/2);
        }

        if(rc.isCircleOccupiedExceptByThisRobot(mapLoc[0].add(retDat[0],tot_dist),radii[0])) return retDat;
        else{
            mapLoc[0] = mapLoc[0].add(retDat[0],tot_dist);
            return bubbleSqueeze(mapLoc, radii, recurse+1, retDat[0], retDat[1].radians);
        }*/
    }

    //Line intersection with circles
    static boolean intersectLineCircle(MapLocation origin, MapLocation cir, Direction path){
        return origin.distanceTo(cir)*Math.sin(path.radians-origin.directionTo(cir).radians)<1.1;
    }

}
